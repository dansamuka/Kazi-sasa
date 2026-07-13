package com.kazisasa.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kazi_sasa_prefs")

/**
 * The one piece of cross-screen state that isn't in Room: which profile is
 * currently active (spec §7.1: "the user should be able to understand which
 * profile is currently active"). Screens observe [activeProfileId] rather than
 * being wired together directly, so switching profiles from anywhere updates
 * everywhere - the feed re-scores, triage re-filters, saved items re-badge.
 */
class ActiveProfileStore(private val context: Context) {

    private object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val NOTIFICATIONS_LAST_KNOWN_GRANTED = booleanPreferencesKey("notifications_last_known_granted")
        val DIGEST_ENABLED = booleanPreferencesKey("weekly_digest_enabled")
    }

    val activeProfileId: Flow<String?> =
        context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] }

    suspend fun setActiveProfileId(profileId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = profileId }
    }

    /** Tracked so the UI can be honest when permission is revoked after a reminder was set (spec §7.6). */
    val notificationsLastKnownGranted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS_LAST_KNOWN_GRANTED] ?: false }

    suspend fun setNotificationsLastKnownGranted(granted: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_LAST_KNOWN_GRANTED] = granted }
    }

    /** Weekly digest opt-in - the fallback when push notifications are denied (spec §7.10). */
    val weeklyDigestEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DIGEST_ENABLED] ?: true }

    suspend fun setWeeklyDigestEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIGEST_ENABLED] = enabled }
    }
}
