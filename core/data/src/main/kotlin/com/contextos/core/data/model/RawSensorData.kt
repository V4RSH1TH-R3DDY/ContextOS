package com.contextos.core.data.model

data class RawSensorData(
    val timestampMs: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val estimatedTimeToEmptyMs: Long?,
    val location: LatLng?,
    val locationAccuracyMeters: Float?,
    val dayOfWeek: Int,                     // Calendar.MONDAY … Calendar.SUNDAY
    val timeSinceLastUnlockMs: Long?,
    val foregroundApps: List<AppUsageEntry>,
    val ambientAudioContext: AmbientAudioContext,
    val wifiSsid: String?,
    val isMobileDataConnected: Boolean,
)
