# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# OkHttp + Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

# ML Kit text recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-dontwarn org.json.**

# BlurView
-dontwarn com.shockwave.**

# BuildConfig — needed to read API keys at runtime
-keep class com.noscroll.BuildConfig { *; }

# System-bound components — must survive obfuscation
-keep class com.noscroll.NoScrollAccessibilityService { *; }
-keep class com.noscroll.OverlayService { *; }

# Compose
-dontwarn androidx.compose.**
