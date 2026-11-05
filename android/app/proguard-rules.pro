# 内容核心是 JNI 入口：R8 不能改这些原生方法名或删掉它们。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.alanmaster.xread.data.NativeCore { *; }

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Compose + lifecycle 的 CompositionLocal 提供链。
# 只开 minify 而不保留这些时，R8 会把 ProvideAndroidCompositionLocals 里的
# LocalLifecycleOwner 提供删掉，运行期直接
# "IllegalStateException: CompositionLocal LocalLifecycleOwner not present"。
-keep class androidx.lifecycle.compose.** { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.compose.runtime.CompositionLocalKt { *; }
-keepclassmembers class androidx.compose.runtime.** {
    public static ** Local*;
}
-keep class androidx.lifecycle.LifecycleOwner { *; }
-keep class androidx.lifecycle.LifecycleRegistry { *; }
-keep class androidx.lifecycle.ViewModelProvider$Factory { *; }

# Compose 运行时依赖的 lambda/快照机制
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.**
