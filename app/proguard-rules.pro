# Morvo ProGuard rules
-keep class com.morvo.injector.InjectorBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
-renamesourcefileattribute SourceFile