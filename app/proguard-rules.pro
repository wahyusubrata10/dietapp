# Model JSON (Gemini, remote config, backup) memakai kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class com.hawdietpro.app.** { *; }
-keepclassmembers class com.hawdietpro.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entity
-keep class com.hawdietpro.app.data.local.entity.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# security-crypto (Tink)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
