-keep class com.quransunah.app.data.local.** { *; }
-keep class com.quransunah.app.data.catalog.** { *; }
-dontwarn javax.annotation.**

# Media3 / ExoPlayer / Android Auto — keep service surface only; allow R8 to shrink codecs.
-keep class com.quransunah.app.media.PlaybackService { *; }
-keep class com.quransunah.app.media.auto.** { *; }
-keep class com.quransunah.app.media.auto.AutoCarConnectionReceiver { *; }
-keepclassmembers class * implements androidx.media3.common.Player$Listener {
    public <methods>;
}
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    public <init>(...);
}
-dontwarn androidx.media3.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *;
}

# Strip verbose/debug/info logs from the release binary.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class com.quransunah.app.core.AppLog {
    public static void d(...);
    public static void i(...);
    public static void v(...);
    public static void w(...);
}
