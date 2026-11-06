//! In-process Android adapter. No HTTP listener and no NAS credentials.
//!
//! 不带 `android` feature 时，本 crate 仍然可以编译并通过 host 单测：
//! 只有 JNI 入口被条件编译掉，核心初始化/请求逻辑保持可测。
#[cfg(feature = "android")]
use jni::{objects::{JClass, JString}, sys::jstring, JNIEnv};
use rand::RngCore;
use serde_json::{json, Value};
use std::path::Path;
use std::sync::OnceLock;
#[cfg(feature = "android")]
use std::{
    panic::{catch_unwind, AssertUnwindSafe},
    sync::{Mutex, Once},
};

static RT: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
fn runtime() -> &'static tokio::runtime::Runtime {
    RT.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("tokio runtime")
    })
}

static CRASH_LOG: OnceLock<std::path::PathBuf> = OnceLock::new();

fn note_crash_target(dir: &str) {
    let _ = CRASH_LOG.set(Path::new(dir).join("native-panic.log"));
}

/// 同时写 stderr 与数据目录下的日志；Android 上 stderr 不进 logcat，只能靠落盘。
fn android_log(message: &str) {
    eprintln!("xread-native: {message}");
    if let Some(path) = CRASH_LOG.get() {
        use std::io::Write;
        if let Ok(mut f) = std::fs::OpenOptions::new().create(true).append(true).open(path) {
            let _ = writeln!(f, "{message}");
        }
    }
}

#[cfg(feature = "android")]
fn ok_body(body: Value) -> Value {
    json!({"status": 200, "body": body})
}

#[cfg(feature = "android")]
fn err(message: impl ToString) -> Value {
    json!({"status":500,"body":{"success":false,"error":message.to_string()}})
}

/// 最近一次 panic 的位置信息。
///
/// **只安装一次**全局 hook：旧实现在每个请求里 `take_hook`/`set_hook`，
/// 并发时两个线程会互相把对方的钩子当成「上一个」装回去，
/// 结果是进程级 hook 永久残留在临时钩子上、panic 文案还会跨请求串味。
#[cfg(feature = "android")]
static LAST_PANIC: Mutex<String> = Mutex::new(String::new());
#[cfg(feature = "android")]
static INSTALL_HOOK: Once = Once::new();

#[cfg(feature = "android")]
fn install_panic_hook() {
    INSTALL_HOOK.call_once(|| {
        let previous = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |info| {
            let location = info
                .location()
                .map(|l| format!("{}:{}", l.file(), l.line()))
                .unwrap_or_default();
            let msg = info.to_string();
            if let Ok(mut slot) = LAST_PANIC.lock() {
                *slot = if location.is_empty() {
                    msg
                } else {
                    format!("{msg} @ {location}")
                };
            }
            previous(info);
        }));
    });
}

#[cfg(feature = "android")]
fn take_panic_note() -> String {
    LAST_PANIC.lock().map(|mut s| std::mem::take(&mut *s)).unwrap_or_default()
}

/// 在 FFI 边界上执行并兜住 panic。**所有** JNI 入口都必须经过这里。
#[cfg(feature = "android")]
fn guarded(action: impl FnOnce() -> Result<Value, String> + std::panic::UnwindSafe) -> Value {
    install_panic_hook();
    // 清掉上一次留下的文案，避免把别的请求的 panic 当成这次的
    let _ = take_panic_note();
    match catch_unwind(AssertUnwindSafe(action)) {
        Ok(Ok(v)) => v,
        Ok(Err(e)) => err(e),
        Err(_) => {
            let detail = take_panic_note();
            android_log(&format!("panic: {detail}"));
            err(format!("native core panic: {detail}"))
        }
    }
}

#[cfg(feature = "android")]
fn err_value_msg(message: impl ToString) -> Value {
    err(message)
}

#[cfg(feature = "android")]
fn to_jstring(env: &mut JNIEnv, value: Value) -> jstring {
    match env.new_string(value.to_string()) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// 数据目录初始化：建目录、写默认配置、准备设备池密钥。
///
/// 与 JNI 无关，host 单测可直接调用。
pub fn prepare_data_dir(dir: &str) -> Result<(), String> {
    note_crash_target(dir);
    let root = Path::new(dir);
    std::fs::create_dir_all(root.join("config")).map_err(|e| e.to_string())?;
    let config_file = root.join("config/config.json");
    // 只在缺失时写；已存在但不合法时由 core 的 Config::load 报错，不在这里覆盖用户配置
    if !config_file.exists() {
        write_atomic(&config_file, br#"{"algorithm_type":"8404","port":0}"#)?;
    }
    let key = load_or_create_pool_key(&root.join("pool.key"))?;
    fqapi_core::runtime::set_device_pool_key(key)?;
    Ok(())
}

/// 原子写：先写临时文件再 rename，避免掉电/被杀留下截断文件。
fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let dir = path.parent().ok_or_else(|| "no parent".to_string())?;
    std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    let tmp = dir.join(format!(
        ".{}.tmp",
        path.file_name().and_then(|s| s.to_str()).unwrap_or("file")
    ));
    std::fs::write(&tmp, bytes).map_err(|e| e.to_string())?;
    if std::fs::rename(&tmp, path).is_err() {
        let _ = std::fs::remove_file(&tmp);
        // 退化为直接写；仍然失败才报错
        std::fs::write(path, bytes).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// 读取 32 字节设备池密钥；文件缺失、为空、非 32 字节或非法 hex 时**重新生成**。
///
/// 旧实现遇到空文件时 `hex::decode("")` 得到 0 字节，之后每次启动都在
/// `set_device_pool_key` 失败，只能清应用数据才能恢复。
fn load_or_create_pool_key(path: &Path) -> Result<Vec<u8>, String> {
    if let Ok(text) = std::fs::read_to_string(path) {
        if let Ok(bytes) = hex::decode(text.trim()) {
            if bytes.len() == 32 {
                return Ok(bytes);
            }
        }
        android_log(&format!(
            "pool.key 不可用（长度或格式错误），重新生成：{}",
            path.display()
        ));
    }
    let mut bytes = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut bytes);
    write_atomic(path, hex::encode(bytes).as_bytes())?;
    Ok(bytes.to_vec())
}

/// 发起一次内容请求并解析为 JSON。
///
/// `timeout_ms` 传核心层的上限。注意下载类端点（`cache=1`）自身还有更短的
/// 传输超时，见 `vendor/fqapi-core/src/endpoints/video.rs`。
pub fn dispatch(id: String, method: String, path: String, query: String, body: Vec<u8>) -> Result<Value, String> {
    // 这三个副本只为失败日志准备：request 拿走所有权，成功路径上不落盘。
    // 按字符截断 query——里面有 URL 编码，但仍不能假设切点一定落在 ASCII 边界。
    let log_method = method.clone();
    let log_path = path.clone();
    let log_query: String = query.chars().take(200).collect();
    let started = std::time::Instant::now();
    // 20 秒足够任何正常请求（章节正文实测 < 2s，首屏推荐 < 1s）。
    // 旧值 60 秒在「设备注册域名被网络拦截」时会让首屏干等一分钟；
    // 注册单步自己还有 12 秒上限，这里只是整体兜底。
    let outcome = runtime().block_on(fqapi_core::runtime::request(id, method, path, query, body, 20000));
    if let Err(e) = &outcome {
        // 只在失败时落盘：成功请求不写日志，避免每次翻页都碰磁盘。
        // 「这台设备为什么取不到内容」在 release 上没有任何其它可观测点，
        // rust.log 只记 init，业务请求的失败原因原本直接丢掉了。
        android_log(&format!(
            "request failed after {}ms: {log_method} /api/v1/{log_path}?{log_query} -> {e}",
            started.elapsed().as_millis()
        ));
    }
    let response = outcome?;
    let value: Value = serde_json::from_slice(&response.body)
        .map_err(|e| format!("non JSON response: {e}"))?;
    Ok(json!({"status": response.status, "body": value}))
}

#[cfg(feature = "android")]
fn jstring_of(env: &mut JNIEnv, s: &JString) -> Result<String, String> {
    env.get_string(s).map(Into::into).map_err(|e| e.to_string())
}

#[cfg(feature = "android")]
#[no_mangle]
pub extern "system" fn Java_com_alanmaster_xread_data_NativeCore_nativeInit(
    mut env: JNIEnv,
    _: JClass,
    dir: JString,
) -> jstring {
    let dir = match jstring_of(&mut env, &dir) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, err_value_msg(e)),
    };
    let value = guarded(move || {
        prepare_data_dir(&dir)?;
        runtime().block_on(async {
            fqapi_core::runtime::init_core(
                Path::new(&dir).join("config/config.json").to_string_lossy().into_owned(),
                Path::new(&dir).join("config/device_pool.json").to_string_lossy().into_owned(),
                Path::new(&dir).join("config/filter.json").to_string_lossy().into_owned(),
                dir.clone(),
                0,
                fqapi_core::listen::ListenMode::Disabled,
                None,
            )
            .await
        })?;
        Ok(ok_body(json!({"state":"ready","transport":"jni","listener":false})))
    });
    to_jstring(&mut env, value)
}

#[cfg(feature = "android")]
#[no_mangle]
pub extern "system" fn Java_com_alanmaster_xread_data_NativeCore_nativeRequest(
    mut env: JNIEnv,
    _: JClass,
    id: JString,
    path: JString,
    query: JString,
    method: JString,
    body: JString,
) -> jstring {
    let args = (|| -> Result<(String, String, String, String, Vec<u8>), String> {
        Ok((
            jstring_of(&mut env, &id)?,
            jstring_of(&mut env, &path)?,
            jstring_of(&mut env, &query)?,
            jstring_of(&mut env, &method)?,
            jstring_of(&mut env, &body)?.into_bytes(),
        ))
    })();
    let value = match args {
        Ok((id, path, query, method, body)) => guarded(move || dispatch(id, method, path, query, body)),
        Err(e) => err_value_msg(e),
    };
    to_jstring(&mut env, value)
}

/// 取消。旧实现完全裸调，panic 会越过 `extern "system"` 直接 abort 进程。
#[cfg(feature = "android")]
#[no_mangle]
pub extern "system" fn Java_com_alanmaster_xread_data_NativeCore_nativeCancel(
    mut env: JNIEnv,
    _: JClass,
    id: JString,
) {
    install_panic_hook();
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Ok(id) = jstring_of(&mut env, &id) {
            fqapi_core::runtime::cancel(id);
        }
    }));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pool_key_is_32_bytes_and_stable() {
        let dir = std::env::temp_dir().join(format!("xread-key-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("pool.key");
        let a = load_or_create_pool_key(&path).unwrap();
        assert_eq!(32, a.len());
        let b = load_or_create_pool_key(&path).unwrap();
        assert_eq!(a, b, "已存在的合法 key 不应被重写");
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn empty_pool_key_is_regenerated_instead_of_failing_forever() {
        let dir = std::env::temp_dir().join(format!("xread-key-empty-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("pool.key");
        std::fs::write(&path, b"").unwrap();
        let key = load_or_create_pool_key(&path).unwrap();
        assert_eq!(32, key.len(), "空 key 必须被重新生成");
        // 再读一次应拿到同一把（说明确实写回了磁盘）
        assert_eq!(key, load_or_create_pool_key(&path).unwrap());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn truncated_pool_key_is_regenerated() {
        let dir = std::env::temp_dir().join(format!("xread-key-short-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("pool.key");
        std::fs::write(&path, b"deadbeef").unwrap();
        assert_eq!(32, load_or_create_pool_key(&path).unwrap().len());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn non_hex_pool_key_is_regenerated() {
        let dir = std::env::temp_dir().join(format!("xread-key-bad-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("pool.key");
        std::fs::write(&path, b"zzzz").unwrap();
        assert_eq!(32, load_or_create_pool_key(&path).unwrap().len());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn prepare_data_dir_writes_default_config_once() {
        let dir = std::env::temp_dir().join(format!("xread-cfg-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        prepare_data_dir(dir.to_str().unwrap()).unwrap();
        let cfg = dir.join("config/config.json");
        assert!(cfg.exists());
        let first = std::fs::read_to_string(&cfg).unwrap();
        std::fs::write(&cfg, br#"{"algorithm_type":"1","port":0}"#).unwrap();
        // 再次准备不应覆盖用户已有配置
        let _ = prepare_data_dir(dir.to_str().unwrap());
        assert_eq!(
            r#"{"algorithm_type":"1","port":0}"#,
            std::fs::read_to_string(&cfg).unwrap()
        );
        assert_ne!(first, std::fs::read_to_string(&cfg).unwrap());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[tokio::test]
    async fn empty_pool_without_network_falls_back_to_local_cached_device() {
        let dir = std::env::temp_dir().join(format!("xread-dev-fallback-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let pool_path = dir.join("device_pool.json");

        // Real DevicePool over empty file without mock origin; registration fails or times out
        let pool = fqapi_core::device::DevicePool::new(&pool_path).await.unwrap();
        assert_eq!(pool.count().await, 0);

        // Fallback device fingerprint is self-contained and valid
        let fallback = fqapi_core::device::DevicePool::fallback_device();
        assert!(!fallback.device_id.is_empty());
        assert!(!fallback.secret_key.is_empty());
        assert_eq!(fallback.platform, "android");

        std::fs::remove_dir_all(&dir).ok();
    }
}
