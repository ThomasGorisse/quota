# kotlinx.serialization keeps generated serializers reachable through @Serializable classes.
-keepclassmembers class ** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
