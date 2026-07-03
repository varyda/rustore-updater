# Keep kotlinx serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.app.rustoreupdater.**$$serializer { *; }
-keepclassmembers class ru.app.rustoreupdater.** {
    *** Companion;
}
-keepclasseswithmembers class ru.app.rustoreupdater.** {
    kotlinx.serialization.KSerializer serializer(...);
}
