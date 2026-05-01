# Consumer ProGuard rules for :core:data
# These rules are applied to any module that depends on :core:data.

# Keep all entity and model classes so Room and kotlinx.serialization work correctly
-keep class com.contextos.core.data.db.entity.** { *; }
-keep class com.contextos.core.data.model.** { *; }
