# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.chronos.app.data.model.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.chronos.** {
    *** Companion;
}
-keepclasseswithmembers class com.chronos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep alarm-related classes
-keep class com.chronos.app.alarm.** { *; }
-keep class com.chronos.app.notifications.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
