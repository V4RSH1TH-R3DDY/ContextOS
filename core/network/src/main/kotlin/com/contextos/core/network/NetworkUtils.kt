package com.contextos.core.network

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException

private const val DEFAULT_TAG = "RetryPolicy"

/**
 * Executes [block] up to [maxAttempts] times, retrying on [IOException] with
 * exponential back-off. Any other exception type propagates immediately.
 *
 * @param maxAttempts   Total number of attempts (default 3).
 * @param initialDelayMs Delay before the second attempt in milliseconds (default 1 s).
 * @param maxDelayMs    Cap on the back-off delay (default 30 s).
 * @param factor        Multiplier applied to the delay after each failure (default 2×).
 * @param tag           Logcat tag used for retry warnings.
 * @param block         Suspend lambda that receives the zero-based attempt index.
 *
 * Usage:
 * ```
 * val events = retryWithBackoff(tag = "CalendarApiClient") { attempt ->
 *     service.events().list("primary").execute()
 * }
 * ```
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1_000L,
    maxDelayMs: Long = 30_000L,
    factor: Double = 2.0,
    tag: String = DEFAULT_TAG,
    block: suspend (attempt: Int) -> T,
): T {
    var currentDelay = initialDelayMs

    for (attempt in 0 until maxAttempts) {
        try {
            return block(attempt)
        } catch (e: IOException) {
            if (attempt == maxAttempts - 1) {
                Log.e(tag, "All $maxAttempts attempts failed — giving up", e)
                throw e
            }
            Log.w(
                tag,
                "Attempt ${attempt + 1}/$maxAttempts failed (${e.message}). " +
                    "Retrying in ${currentDelay}ms…",
            )
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }

    // unreachable — the loop either returns or throws
    error("retryWithBackoff: exited loop without result")
}
