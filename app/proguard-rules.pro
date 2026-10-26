# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve JavaScript interfaces and annotations for WebView
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve line numbers and source file for readable crash traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models used in serialization, JSON, and Firebase
-keep class com.example.ai.AiService$* { *; }
-keep class com.example.firebase.** { *; }
-keep class com.example.widget.** { *; }
-keep class com.example.reos.ReosAccountManager$* { *; }

# Keep App Widget Providers for Homescreen widgets
-keep class com.example.widget.WeatherGlanceWidget { *; }
-keep class com.example.widget.PortalQuickLaunchWidget { *; }

# Suppress harmless warnings from third-party libraries
-dontwarn okio.**
-dontwarn com.google.errorprone.annotations.**
