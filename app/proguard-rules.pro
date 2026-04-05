# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Glance AppWidget — keep all widget-related classes
-keep class com.southsouthwest.framelog.ui.widget.** { *; }

# Keep GlanceAppWidgetReceiver subclasses
-keep public class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Keep GlanceAppWidget subclasses
-keep public class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# Keep ActionCallback implementations
-keep public class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# Keep Glance itself from being stripped
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# WorkManager — used internally by Glance for widget update scheduling.
# InputMerger subclasses are instantiated reflectively; R8 strips the no-arg
# constructor without this rule, causing Glance's update pipeline to fail silently.
-keep class * extends androidx.work.InputMerger { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**