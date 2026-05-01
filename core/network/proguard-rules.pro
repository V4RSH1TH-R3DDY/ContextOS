# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools. For more details, see
# http://developer.android.com/guide/developing/tools/proguard.html

# Retain Retrofit service interface method and field names
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
