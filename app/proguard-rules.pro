# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfigs.release.proguardFiles and/or signingConfigs.debug.proguardFiles.

# If your project uses WebView with JS, uncomment the following and specify the fully qualified class name to the JavaScript interface class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep Android classes
-keep class android.** { *; }
-keep class androidx.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
