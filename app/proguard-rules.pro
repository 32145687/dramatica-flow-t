# Proguard rules
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.dramatica.flow.**$$serializer { *; }
-keepclassmembers class com.dramatica.flow.** { *** Companion; }
-keepclasseswithmembers class com.dramatica.flow.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.dramatica.flow.data.** { *; }
