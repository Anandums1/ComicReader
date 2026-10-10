# Add project specific ProGuard rules here.

# Strip all logging and debugging statements for production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Preserve Room models, Hilt dependencies and other reflective dependencies
-keepclassmembers class * {
    @androidx.room.* *;
}

# Ignore missing SLF4J bindings (common in libraries like Commons Compress)
-dontwarn org.slf4j.**
