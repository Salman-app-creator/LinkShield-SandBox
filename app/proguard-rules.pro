# ═══════════════════════════════════════════════════════════════════════
# LinkShield Sandbox — ProGuard Rules
# ═══════════════════════════════════════════════════════════════════════
#
# Yeh rules release build mein code ko obfuscate karte hain aur APK
# size kam karte hain. Debug build par koi asar nahi padta.
#
# ⚠️ IMPORTANT: Agar aap naya library add karein, toh yahan uske rules
# bhi add karein — warna woh library kaam karna band kar sakti hai.
# ═══════════════════════════════════════════════════════════════════════

# ── Kotlin ────────────────────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── AndroidX ──────────────────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**

# ── Jetpack Compose ───────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# ── OkHttp ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ── JSON (org.json) ───────────────────────────────────────────────────
-keep class org.json.** { *; }

# ── Coil (Image Loading) ──────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ── App Models (Serialization ke liye) ────────────────────────────────
-keep class com.linkshield.sandbox.api.** { *; }
-keep class com.linkshield.sandbox.ui.grabber.** { *; }
-keep class com.linkshield.sandbox.update.** { *; }
-keep class com.linkshield.sandbox.license.** { *; }
-keep class com.linkshield.sandbox.vpn.** { *; }
-keep class com.linkshield.sandbox.dns.** { *; }
-keep class com.linkshield.sandbox.adblock.** { *; }

# ── BuildConfig (API keys ke liye) ────────────────────────────────────
-keep class com.linkshield.sandbox.BuildConfig { *; }

# ── WebView (agar JavaScript interface use ho) ────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Native Methods ────────────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── View Constructors (XML se inflate hone wale views) ────────────────
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ── Parcelable ────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── Serializable ──────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Enums ─────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Reflection (avoids stripping of classes accessed via reflection) ──
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

# ── Debug Info (line numbers for crash reports) ───────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── R8 Full Mode (Android Gradle Plugin 8+) ───────────────────────────
# Agar Android Gradle Plugin 8.x use kar rahe hain, toh:
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
