package com.geotagcamera.geotagginglocationonphoto.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

/** Gates whether Launch cross-fades straight into Capture or through the Permission Primer first. */
class OnboardingPreferences(private val context: Context) {

    private object Keys {
        val COMPLETED = booleanPreferencesKey("has_completed_onboarding")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[Keys.COMPLETED] ?: false
    }

    suspend fun setCompleted() {
        context.onboardingDataStore.edit { prefs -> prefs[Keys.COMPLETED] = true }
    }
}
