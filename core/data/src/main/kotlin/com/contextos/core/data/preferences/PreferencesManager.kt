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
}
