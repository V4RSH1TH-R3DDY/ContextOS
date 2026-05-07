package com.contextos.core.service.privacy

import android.util.Log
import com.contextos.core.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single gatekeeper class that all LLM inference calls must pass through.
 *
 * Routes requests to either on-device inference or cloud (OpenClaw) based on:
 *   - The [InferenceRequestType] of the request
 *   - Whether the user has opted in to cloud inference for drafting
 *
 * Phase 12.1 — On-Device Reasoning Enforcement
 */
@Singleton
class InferenceRouter @Inject constructor(
    private val preferencesManager: PreferencesManager,
) {

    /**
     * Determines the appropriate inference target for a given request type.
     *
     * On-device capable: situation classification, routine detection, confidence scoring.
     * Off-device (opt-in only): message drafting with personalised tone.
     */
    suspend fun route(requestType: InferenceRequestType): InferenceTarget {
        return when (requestType) {
            InferenceRequestType.SITUATION_ANALYSIS -> {
                Log.d(TAG, "Routing SITUATION_ANALYSIS → ON_DEVICE")
                InferenceTarget.ON_DEVICE
            }
            InferenceRequestType.ROUTINE_DETECTION -> {
                Log.d(TAG, "Routing ROUTINE_DETECTION → ON_DEVICE")
                InferenceTarget.ON_DEVICE
            }
            InferenceRequestType.MESSAGE_DRAFTING -> {
                val userConsented = preferencesManager.cloudInferenceConsented.first()
                if (userConsented) {
                    Log.d(TAG, "Routing MESSAGE_DRAFTING → CLOUD (user consented)")
                    InferenceTarget.CLOUD
                } else {
                    Log.d(TAG, "Routing MESSAGE_DRAFTING → ON_DEVICE_FALLBACK (no consent)")
                    InferenceTarget.ON_DEVICE_FALLBACK
                }
            }
        }
    }

    companion object {
        private const val TAG = "InferenceRouter"
    }
}

/**
 * Types of inference requests the system can make.
 */
enum class InferenceRequestType {
    /** Context classification and skill ranking — always on-device. */
    SITUATION_ANALYSIS,

    /** Pattern detection and confidence scoring — always on-device. */
    ROUTINE_DETECTION,

    /** Message drafting with personalised tone — cloud opt-in only. */
    MESSAGE_DRAFTING,
}

/**
 * Where an inference request should be executed.
 */
enum class InferenceTarget {
    /** Execute on-device using rule-based or quantised model inference. */
    ON_DEVICE,

    /** Execute via cloud LLM (OpenClaw/Gemini) — requires user consent. */
    CLOUD,

    /** On-device fallback when cloud is available but user hasn't consented. */
    ON_DEVICE_FALLBACK,
}
