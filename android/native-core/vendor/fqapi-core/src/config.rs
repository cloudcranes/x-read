//! Runtime configuration (mirrors config.json).

use serde::Deserialize;

#[derive(Debug, Clone, Deserialize, Default)]
pub struct AntiCrawler {
    #[serde(default)]
    pub enabled: bool,
    #[serde(default)]
    pub redirect_url: String,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Admin {
    #[serde(default)]
    pub token: String,
    #[serde(default)]
    pub update_server_url: String,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Config {
    #[serde(default)]
    pub algorithm_type: String,
    #[serde(default)]
    pub zwsm: String,
    #[serde(default)]
    pub port: u16,
    #[serde(default)]
    pub anti_crawler: AntiCrawler,
    #[serde(default)]
    pub admin: Admin,
    /// 上游访问代理，形如 `http://192.168.1.170:7890`。
    /// 模拟器/受限网络里系统 DNS 会把 `*.snssdk.com` 解析到 fake-IP，
    /// 没有代理接管时注册请求会一直挂到超时；由 Kotlin 侧把系统代理写进来。
    #[serde(default)]
    pub proxy: String,
}

impl Config {
    pub fn load(path: &str) -> Result<Self, String> {
        let data = std::fs::read(path).map_err(|e| format!("read config {path}: {e}"))?;
        let mut c: Config =
            serde_json::from_slice(&data).map_err(|e| format!("parse config {path}: {e}"))?;
        if c.port == 0 {
            c.port = 18921;
        }
        Ok(c)
    }
}
