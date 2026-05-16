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
-keep class kotlinx.coroutines.android.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Jetpack Compose
-keep class androidx.compose.runtime.ParcelableSnapshotMutationPolicy { *; }
-dontwarn androidx.compose.ui.platform.AndroidComposeView

# Zip4j
-keep class net.lingala.zip4j.** { *; }

# Exp4j
-keep class net.objecthunter.exp4j.** { *; }

# Serialization / Models
-keep class com.example.nozokima.data.local.entities.** { *; }
-keep class com.example.nozokima.model.** { *; }

# General
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
