# LOCK46 — release shrinking rules.
# Components referenced only from AndroidManifest.xml must survive shrinking.
-keep class com.lock46.app.enforce.Lock46AccessibilityService { *; }
-keep class com.lock46.app.enforce.DutyService { *; }
-keep class com.lock46.app.enforce.BootReceiver { *; }
-keep class com.lock46.app.enforce.DutyExpiryReceiver { *; }
-keep class com.lock46.app.ui.MainActivity { *; }

# Never keep source file names / line numbers that could leak internals.
-renamesourcefileattribute SourceFile
