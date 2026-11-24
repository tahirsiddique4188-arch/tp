# ProGuard & R8 Optimization Rules for Titan IDE
# Strict tree-shaking and aggressive dead-code elimination in R8 full mode

-allowaccessmodification

# Preserve line numbers for stack traces while stripping source files
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Jetpack Compose UI optimization
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.local.db.** { *; }
-dontwarn androidx.room.paging.**

# Jsoup HTML Engine
-keep public class org.jsoup.** { public *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil Image Loading
-dontwarn coil.**

# Domain Data Models and API Key Entities
-keep class com.example.data.model.** { *; }
-keep class com.titan.ide.** { *; }
-keep class com.example.util.TitanApiKey { *; }

# Strip verbose and debug logs in release to prevent log sniffing by attackers
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}


# Titan IDE Release Hardening — Prevent R8 from breaking Room, OkHttp, JSON & Accessibility
-keep class com.titan.ide.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.services.** { *; }
-keep class com.example.voice.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
