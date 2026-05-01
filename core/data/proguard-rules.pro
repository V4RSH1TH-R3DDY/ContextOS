# Add project-specific ProGuard rules here.
# By default, the flags in this file are applied to the release build only.
# https://developer.android.com/build/shrink-code

# Keep Room entity and DAO classes
-keep class com.contextos.core.data.db.** { *; }

# Keep data model classes used with kotlinx.serialization
-keep class com.contextos.core.data.model.** { *; }

# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
