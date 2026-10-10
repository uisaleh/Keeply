# Room and kotlinx.serialization ship their own consumer rules.
# Keep navigation route classes (type-safe navigation uses serialization).
-keep @kotlinx.serialization.Serializable class app.keeply.ui.navigation.** { *; }

# Strip verbose/debug/info/warn logging from shrunk builds so nothing about the vault reaches logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
# Serialized vault entities (cloud sync): keep their generated serializers.
-keep,includedescriptorclasses class app.keeply.**$$serializer { *; }
-keepclassmembers class app.keeply.** { *** Companion; }
-keepclasseswithmembers class app.keeply.** { kotlinx.serialization.KSerializer serializer(...); }
