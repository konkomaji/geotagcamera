package com.geotagcamera.geotagginglocationonphoto.stamp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stampDataStore by preferencesDataStore(name = "stamp_prefs")

/** No cloud sync, no account required — preferences live only on this device. */
class StampPreferences(private val context: Context) {

    private object Keys {
        val COORDINATES = booleanPreferencesKey("show_coordinates")
        val ADDRESS = booleanPreferencesKey("show_address")
        val TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val ALTITUDE = booleanPreferencesKey("show_altitude")
        val ACCURACY = booleanPreferencesKey("show_accuracy")
        val BEARING = booleanPreferencesKey("show_bearing")
        val ORG_LABEL = stringPreferencesKey("org_label")
        val REQUIRE_SIGNATURE = booleanPreferencesKey("require_signature")
    }

    val fields: Flow<StampFields> = context.stampDataStore.data.map { prefs ->
        StampFields(
            showCoordinates = prefs[Keys.COORDINATES] ?: true,
            showAddress = prefs[Keys.ADDRESS] ?: true,
            showTimestamp = prefs[Keys.TIMESTAMP] ?: true,
            showAltitude = prefs[Keys.ALTITUDE] ?: false,
            showAccuracy = prefs[Keys.ACCURACY] ?: false,
            showBearing = prefs[Keys.BEARING] ?: false,
            orgLabel = prefs[Keys.ORG_LABEL] ?: "",
            requireSignature = prefs[Keys.REQUIRE_SIGNATURE] ?: false
        )
    }

    suspend fun update(fields: StampFields) {
        context.stampDataStore.edit { prefs ->
            prefs[Keys.COORDINATES] = fields.showCoordinates
            prefs[Keys.ADDRESS] = fields.showAddress
            prefs[Keys.TIMESTAMP] = fields.showTimestamp
            prefs[Keys.ALTITUDE] = fields.showAltitude
            prefs[Keys.ACCURACY] = fields.showAccuracy
            prefs[Keys.BEARING] = fields.showBearing
            prefs[Keys.ORG_LABEL] = fields.orgLabel
            prefs[Keys.REQUIRE_SIGNATURE] = fields.requireSignature
        }
    }
}
