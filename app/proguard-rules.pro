# Morvo ProGuard rules
# Keep Application & Activity classes (referenced in AndroidManifest)
-keep class com.morvo.MorvoApp { *; }
-keep class com.morvo.MainActivity { *; }

# Keep JNI bridge and native methods
-keep class com.morvo.injector.InjectorBridge { *; }
-keep class com.morvo.injector.ProcessManager { *; }
-keep class com.morvo.network.LicenseAPI { *; }

# Keep all native method names (JNI bridge)
-keepclasseswithmembernames class * { native <methods>; }

# Keep all classes in com.morvo package (safer)
-keep class com.morvo.** { *; }

-renamesourcefileattribute SourceFile
