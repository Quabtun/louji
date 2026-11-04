# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.quabtun.louji.**$$serializer { *; }
-keepclassmembers class com.quabtun.louji.** {
    *** Companion;
}
-keepclasseswithmembers class com.quabtun.louji.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# zip4j (AES-256 加密备份)
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**
