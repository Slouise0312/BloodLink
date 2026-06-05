# ── BloodLink ProGuard Rules ─────────────────────────────────────────────────
# These rules ensure R8/ProGuard doesn't break Firebase, TFLite, ML Kit,
# or Compose while still obfuscating all other code.

# ── General ──────────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# ── Firebase ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Firebase Firestore — keeps model serialization working
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }

# Firebase Messaging (FCM)
-keep class com.google.firebase.messaging.** { *; }

# ── Your data models (Firestore serialization) ──────────────────────────────
# Keep all fields in your Models.kt classes so Firestore can read/write them
-keep class com.example.bloodlink.AppUser { *; }
-keep class com.example.bloodlink.Screening { *; }
-keep class com.example.bloodlink.Event { *; }
-keep class com.example.bloodlink.Alert { *; }
-keep class com.example.bloodlink.UserRole { *; }
-keepclassmembers class com.example.bloodlink.** {
    <fields>;
}

# ── TensorFlow Lite ──────────────────────────────────────────────────────────
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# ── ML Kit (Face Detection + Barcode) ────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── ZXing (QR code generation) ───────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ── CameraX ──────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Jetpack Compose ──────────────────────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── Strip debug logs from release builds ─────────────────────────────────────
# Removes Log.d() and Log.v() calls entirely from the APK — no UIDs,
# screening data, or TFLite scores visible in Logcat on release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── Prevent R8 from removing classes used via reflection ─────────────────────
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}