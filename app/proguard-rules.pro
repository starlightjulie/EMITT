# kotlinx.serialization keeps its own generated serializers; these keep the @Serializable models intact.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.emitt.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.emitt.** {
    kotlinx.serialization.KSerializer serializer(...);
}
