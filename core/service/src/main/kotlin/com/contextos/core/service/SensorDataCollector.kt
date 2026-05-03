package com.contextos.core.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager

import android.util.Log
import android.app.usage.UsageStatsManager
import com.contextos.core.data.model.AmbientAudioContext
import com.contextos.core.data.model.AppUsageEntry
import com.contextos.core.data.model.LatLng
import com.contextos.core.data.model.RawSensorData
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Collects all device sensor data in a single pass per agent cycle.
 *
 * All operations are non-blocking (run on [Dispatchers.IO]) and implement
 * graceful degradation — any individual sensor failure returns null / a safe
 * default rather than throwing an exception.
 *
 * Phase 1.2 deliverable: returns a fully populated [RawSensorData] within 2 s.
 */
@Singleton
class SensorDataCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun collect(): RawSensorData = withContext(Dispatchers.IO) {
        val timestampMs   = System.currentTimeMillis()
        val batteryInfo   = collectBattery()
        val locationInfo  = collectLocation()
        val appUsage      = collectAppUsage(timestampMs)
        val audioContext  = collectAmbientAudio()
        val connectivity  = collectConnectivity()

        RawSensorData(
            timestampMs             = timestampMs,
            batteryLevel            = batteryInfo.level,
            isCharging              = batteryInfo.isCharging,
            estimatedTimeToEmptyMs  = null,  // Best-effort; requires historical data
            location                = locationInfo?.let { LatLng(it.latitude, it.longitude) },
            locationAccuracyMeters  = locationInfo?.accuracy,
            dayOfWeek               = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
            timeSinceLastUnlockMs   = collectTimeSinceLastUnlock(timestampMs),
            foregroundApps          = appUsage,
            ambientAudioContext     = audioContext,
            wifiSsid                = connectivity.wifiSsid,
            isMobileDataConnected   = connectivity.isMobileDataConnected,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Battery
    // ─────────────────────────────────────────────────────────────────────────

    private data class BatteryInfo(val level: Int, val isCharging: Boolean)

    private fun collectBattery(): BatteryInfo {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct    = if (scale > 0) (level * 100 / scale) else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL
            BatteryInfo(pct.coerceIn(0, 100), charging)
        } catch (e: Exception) {
            Log.w(TAG, "Battery collection failed", e)
            BatteryInfo(-1, false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────────────────────

    private fun collectLocation(): Location? {
        val fusedLocation = try {
            @Suppress("MissingPermission")
            Tasks.await(
                LocationServices.getFusedLocationProviderClient(context).lastLocation,
                LOCATION_TIMEOUT_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission denied", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Fused location unavailable; trying platform fallback", e)
            null
        }
        if (fusedLocation != null) return fusedLocation

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val providers = listOf(
                LocationManager.FUSED_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            )
            providers.firstNotNullOfOrNull { provider ->
                try {
                    @Suppress("MissingPermission")
                    lm.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location collection failed", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // App usage
    // ─────────────────────────────────────────────────────────────────────────

    private fun collectAppUsage(nowMs: Long): List<AppUsageEntry> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                nowMs - USAGE_WINDOW_MS,
                nowMs,
            ) ?: return emptyList()

            val pm = context.packageManager
            stats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(5)
                .map { stat ->
                    val appName = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(stat.packageName, 0)
                        ).toString()
                    } catch (_: Exception) {
                        stat.packageName
                    }
                    AppUsageEntry(
                        packageName  = stat.packageName,
                        appName      = appName,
                        usageTimeMs  = stat.totalTimeInForeground,
                    )
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Usage stats permission denied", e)
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "App usage collection failed", e)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient audio
    // ─────────────────────────────────────────────────────────────────────────

    private fun collectAmbientAudio(): AmbientAudioContext {
        val sampleRate = 44100
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return AmbientAudioContext.UNKNOWN

        val bufferSize = minBufferSize.coerceAtLeast(sampleRate)

        var recorder: AudioRecord? = null
        return try {
            @Suppress("MissingPermission")
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return AmbientAudioContext.UNKNOWN
            }

            recorder.startRecording()
            val samples = ShortArray(sampleRate)
            var totalRead = 0
            val deadlineMs = System.currentTimeMillis() + AUDIO_SAMPLE_TIMEOUT_MS
            while (totalRead < samples.size && System.currentTimeMillis() < deadlineMs) {
                val read = recorder.read(samples, totalRead, samples.size - totalRead)
                if (read <= 0) break
                totalRead += read
            }

            if (totalRead <= 0) return AmbientAudioContext.UNKNOWN

            val sumSq = samples.take(totalRead).sumOf { (it * it).toLong() }
            val rms   = sqrt(sumSq.toDouble() / totalRead).toFloat()

            when {
                rms < 200f  -> AmbientAudioContext.SILENT
                rms < 1000f -> AmbientAudioContext.AMBIENT_NOISE
                rms < 3000f -> AmbientAudioContext.CONVERSATION
                else        -> AmbientAudioContext.AMBIENT_NOISE
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "RECORD_AUDIO permission denied", e)
            AmbientAudioContext.UNKNOWN
        } catch (e: Exception) {
            Log.w(TAG, "Ambient audio collection failed", e)
            AmbientAudioContext.UNKNOWN
        } finally {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time since last unlock
    // ─────────────────────────────────────────────────────────────────────────

    private fun collectTimeSinceLastUnlock(nowMs: Long): Long? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(nowMs - 24 * 3_600_000L, nowMs)
            var lastInteractiveMs = 0L
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE
                    || event.eventType == android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN) {
                    if (event.timeStamp > lastInteractiveMs) lastInteractiveMs = event.timeStamp
                }
            }
            if (lastInteractiveMs > 0) nowMs - lastInteractiveMs else null
        } catch (e: Exception) {
            Log.w(TAG, "Last-unlock time unavailable", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connectivity
    // ─────────────────────────────────────────────────────────────────────────

    private data class ConnectivityInfo(val wifiSsid: String?, val isMobileDataConnected: Boolean)

    @Suppress("DEPRECATION")
    private fun collectConnectivity(): ConnectivityInfo {
        var wifiSsid: String?         = null
        var isMobileDataConnected      = false

        try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wm.connectionInfo
            val rawSsid  = wifiInfo?.ssid
            // Android wraps SSID in quotes — strip them
            wifiSsid = rawSsid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        } catch (e: Exception) {
            Log.w(TAG, "WiFi SSID collection failed", e)
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps    = network?.let { cm.getNetworkCapabilities(it) }
            isMobileDataConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity collection failed", e)
        }

        return ConnectivityInfo(wifiSsid, isMobileDataConnected)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG            = "SensorDataCollector"
        private const val USAGE_WINDOW_MS = 30L * 60 * 1_000  // 30 minutes
        private const val LOCATION_TIMEOUT_MS = 750L
        private const val AUDIO_SAMPLE_TIMEOUT_MS = 1_100L
    }
}
