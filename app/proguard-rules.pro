# Retrofit + kotlinx.serialization DTOs
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.budgetpulse.app.**$$serializer { *; }
-keepclassmembers class com.budgetpulse.app.** { *** Companion; }
-keepclasseswithmembers class com.budgetpulse.app.** { kotlinx.serialization.KSerializer serializer(...); }
# JavaMail
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.sasl.**
-dontwarn org.apache.harmony.**
