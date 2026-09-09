# kotlinx.serialization
#
# R8 rewrites and strips aggressively; the serialization plugin generates serializers that
# are only ever reached reflectively, through the `Companion` or the synthetic
# `$serializer` class. Without these, `Status`, `Pairing` and friends decode to a
# NoSuchMethodError at runtime in release only — the exact failure a minified build is
# most likely to hide until it is on the store.
# Source: the rules shipped in the kotlinx.serialization README.

-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault

# Keep the `serializer()` accessor on anything that has one.
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the Companion of every @Serializable class, and its serializer() accessor.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the synthetic $serializer objects and their `descriptor`/`childSerializers`.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$$serializer {
    *** INSTANCE;
    kotlinx.serialization.descriptors.SerialDescriptor descriptor;
}
# The serialization runtime itself.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# Our own model classes, named explicitly so a refactor that drops @Serializable is loud.
-keep,includedescriptorclasses class com.gorisse.thomas.quota.data.**$$serializer { *; }
-keepclassmembers class com.gorisse.thomas.quota.data.** {
    *** Companion;
    <fields>;
}
-keep @kotlinx.serialization.Serializable class com.gorisse.thomas.quota.data.** { *; }

# Enum entries survive obfuscation: ProviderId / Severity are decoded by name from JSON.
-keepclassmembers enum com.gorisse.thomas.quota.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AndroidX Glance / WorkManager instantiate these by reflection from the manifest.
-keep class com.gorisse.thomas.quota.widget.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Line numbers in a Play Console crash need the source file attribute kept and renamed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
