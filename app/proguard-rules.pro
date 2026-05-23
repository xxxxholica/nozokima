# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# ML Kit (Bundled rules should handle most cases, keeping only what's necessary for reflection if any)
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# Jetpack Compose (Bundled rules should handle this)
-dontwarn androidx.compose.ui.platform.AndroidComposeView

# Zip4j
-keep class net.lingala.zip4j.model.** { *; }
-keep class net.lingala.zip4j.exception.** { *; }

# Serialization / Models
-keep class com.example.nozokima.data.local.entities.** { *; }
-keep class com.example.nozokima.model.** { *; }

# General
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
