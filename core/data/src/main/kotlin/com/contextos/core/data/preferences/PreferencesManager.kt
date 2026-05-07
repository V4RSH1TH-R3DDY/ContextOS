package com.contextos.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "contextos_prefs")

class PreferencesManager(
    private val context: Context,
) {

    // Keys
    private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    private val KEY_EMERGENCY_CONTACT_NAME = stringPreferencesKey("emergency_contact_name")
    private val KEY_EMERGENCY_CONTACT_PHONE = stringPreferencesKey("emergency_contact_phone")
    private val KEY_EMERGENCY_CONTACT_RELATIONSHIP = stringPreferencesKey("emergency_contact_relationship")
    private val KEY_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
    private val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
    private val KEY_CLOUD_INFERENCE_CONSENTED = booleanPreferencesKey("cloud_inference_consented")
    private val KEY_DATA_RETENTION_DAYS = stringPreferencesKey("data_retention_days")
    private val KEY_FIRST_INSTALL_MS = stringPreferencesKey("first_install_ms")
    private val KEY_SMARTTHINGS_CONNECTED = booleanPreferencesKey("smartthings_connected")
    private val KEY_SMARTTHINGS_LOCATION_NAME = stringPreferencesKey("smartthings_location_name")
    private val KEY_SUPPRESSED_ROUTINE_TYPES = stringPreferencesKey("suppressed_routine_types")

    // Onboarding
    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = completed }
    }

    // Emergency contact
    data class EmergencyContact(
        val name: String,
        val phone: String,
        val relationship: String,
    )

    val emergencyContact: Flow<EmergencyContact?> = context.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_EMERGENCY_CONTACT_NAME]
            val phone = prefs[KEY_EMERGENCY_CONTACT_PHONE]
            val relationship = prefs[KEY_EMERGENCY_CONTACT_RELATIONSHIP]
            if (name != null && phone != null) {
                EmergencyContact(name, phone, relationship ?: "Family")
            } else {
                null
            }
        }

    suspend fun saveEmergencyContact(contact: EmergencyContact) {
        context.dataStore.edit {
            it[KEY_EMERGENCY_CONTACT_NAME] = contact.name
            it[KEY_EMERGENCY_CONTACT_PHONE] = contact.phone
            it[KEY_EMERGENCY_CONTACT_RELATIONSHIP] = contact.relationship
        }
    }

    suspend fun clearEmergencyContact() {
        context.dataStore.edit {
            it.remove(KEY_EMERGENCY_CONTACT_NAME)
            it.remove(KEY_EMERGENCY_CONTACT_PHONE)
            it.remove(KEY_EMERGENCY_CONTACT_RELATIONSHIP)
        }
    }

    // Google account
    val googleAccountEmail: Flow<String?> = context.dataStore.data
        .map { it[KEY_GOOGLE_ACCOUNT_EMAIL] }

    suspend fun setGoogleAccountEmail(email: String?) {
        context.dataStore.edit {
            if (email != null) {
                it[KEY_GOOGLE_ACCOUNT_EMAIL] = email
            } else {
                it.remove(KEY_GOOGLE_ACCOUNT_EMAIL)
            }
        }
    }

    // Demo mode
    val isDemoMode: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_DEMO_MODE] ?: false }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEMO_MODE] = enabled }
    }

    // Cloud inference consent (Phase 12.1)
    val cloudInferenceConsented: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_CLOUD_INFERENCE_CONSENTED] ?: false }

    suspend fun setCloudInferenceConsented(consented: Boolean) {
        context.dataStore.edit { it[KEY_CLOUD_INFERENCE_CONSENTED] = consented }
    }

    // Data retention policy (Phase 12.2) — days to keep action log entries
    val dataRetentionDays: Flow<Int> = context.dataStore.data
        .map { (it[KEY_DATA_RETENTION_DAYS] ?: "90").toIntOrNull() ?: 90 }

    suspend fun setDataRetentionDays(days: Int) {
        context.dataStore.edit { it[KEY_DATA_RETENTION_DAYS] = days.toString() }
    }

    // First install timestamp (Phase 10.3 — learning UI visibility)
    val firstInstallMs: Flow<Long> = context.dataStore.data
        .map { (it[KEY_FIRST_INSTALL_MS] ?: "0").toLongOrNull() ?: 0L }

    suspend fun recordFirstInstallIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_FIRST_INSTALL_MS] == null) {
                prefs[KEY_FIRST_INSTALL_MS] = System.currentTimeMillis().toString()
            }
        }
    }

    // SmartThings connection (Phase 11.3)
    val isSmartThingsConnected: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SMARTTHINGS_CONNECTED] ?: false }

    val smartThingsLocationName: Flow<String?> = context.dataStore.data
        .map { it[KEY_SMARTTHINGS_LOCATION_NAME] }

    suspend fun setSmartThingsConnection(connected: Boolean, locationName: String? = null) {
        context.dataStore.edit {
            it[KEY_SMARTTHINGS_CONNECTED] = connected
            if (locationName != null) {
                it[KEY_SMARTTHINGS_LOCATION_NAME] = locationName
            } else {
                it.remove(KEY_SMARTTHINGS_LOCATION_NAME)
            }
        }
    }

    // Suppressed routine types (Phase 10.2 — "Stop suggesting this")
    val suppressedRoutineTypes: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_SUPPRESSED_ROUTINE_TYPES] ?: ""
            if (raw.isBlank()) emptySet() else raw.split(",").toSet()
        }

    suspend fun suppressRoutineType(routineType: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SUPPRESSED_ROUTINE_TYPES] ?: ""
            val types = if (existing.isBlank()) mutableSetOf() else existing.split(",").toMutableSet()
            types.add(routineType)
            prefs[KEY_SUPPRESSED_ROUTINE_TYPES] = types.joinToString(",")
        }
    }

    suspend fun unsuppressRoutineType(routineType: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SUPPRESSED_ROUTINE_TYPES] ?: ""
            val types = existing.split(",").toMutableSet()
            types.remove(routineType)
            prefs[KEY_SUPPRESSED_ROUTINE_TYPES] = types.joinToString(",")
        }
    }
}

