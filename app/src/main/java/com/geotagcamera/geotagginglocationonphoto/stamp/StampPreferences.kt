package com.geotagcamera.geotagginglocationonphoto.stamp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stampDataStore by preferencesDataStore(name = "stamp_prefs")

/**
 * No cloud sync, no account required — preferences live only on this device.
 *
 * Migration note: every key string that existed before the 1.1.0 redesign
 * (COORDINATES, ADDRESS, TIMESTAMP, ALTITUDE, ACCURACY, BEARING, ORG_LABEL,
 * REQUIRE_SIGNATURE) is kept byte-for-byte unchanged below, so an existing
 * install's choices load exactly as before. Everything new is a fresh key
 * with its own default — that's the entire migration strategy, no
 * DataStore.Migration object needed for a preferences store this simple.
 */
class StampPreferences(private val context: Context) {

    private object Keys {
        val TEMPLATE = stringPreferencesKey("template")
        val POSITION = stringPreferencesKey("position")
        val MAP = booleanPreferencesKey("show_map")
        val COUNTRY = booleanPreferencesKey("show_country")
        val COORDINATES = booleanPreferencesKey("show_coordinates")
        val ADDRESS = booleanPreferencesKey("show_address")
        val TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val GMT_OFFSET = booleanPreferencesKey("show_gmt_offset")
        val ALTITUDE = booleanPreferencesKey("show_altitude")
        val ACCURACY = booleanPreferencesKey("show_accuracy")
        val BEARING = booleanPreferencesKey("show_bearing")
        val WEATHER = booleanPreferencesKey("show_weather")
        val ORG_LABEL_TOGGLE = booleanPreferencesKey("show_org_label_toggle")
        val ORG_LABEL = stringPreferencesKey("org_label")
        val ORG_LOGO_TOGGLE = booleanPreferencesKey("show_org_logo")
        val ORG_LOGO_URI = stringPreferencesKey("org_logo_uri")
        val SIGNATURE_FIELD = booleanPreferencesKey("show_signature_field")
        val REQUIRE_SIGNATURE = booleanPreferencesKey("require_signature")
        val BRAND_MARK = booleanPreferencesKey("show_brand_mark")
    }

    val fields: Flow<StampFields> = context.stampDataStore.data.map { prefs ->
        StampFields(
            template = prefs[Keys.TEMPLATE]?.let { runCatching { StampTemplate.valueOf(it) }.getOrNull() }
                ?: StampTemplate.CARD,
            position = prefs[Keys.POSITION]?.let { runCatching { StampAnchor.valueOf(it) }.getOrNull() }
                ?: StampAnchor.BOTTOM_LEFT,
            showMap = prefs[Keys.MAP] ?: true,
            showCountry = prefs[Keys.COUNTRY] ?: true,
            showAddress = prefs[Keys.ADDRESS] ?: true,
            showCoordinates = prefs[Keys.COORDINATES] ?: true,
            showTimestamp = prefs[Keys.TIMESTAMP] ?: true,
            showGmtOffset = prefs[Keys.GMT_OFFSET] ?: true,
            showAltitude = prefs[Keys.ALTITUDE] ?: false,
            showAccuracy = prefs[Keys.ACCURACY] ?: true,
            showBearing = prefs[Keys.BEARING] ?: false,
            showWeather = prefs[Keys.WEATHER] ?: false,
            showOrgLabelToggle = prefs[Keys.ORG_LABEL_TOGGLE] ?: true,
            orgLabel = prefs[Keys.ORG_LABEL] ?: "",
            showOrgLogo = prefs[Keys.ORG_LOGO_TOGGLE] ?: true,
            orgLogoUri = prefs[Keys.ORG_LOGO_URI],
            showSignatureField = prefs[Keys.SIGNATURE_FIELD] ?: true,
            requireSignature = prefs[Keys.REQUIRE_SIGNATURE] ?: false,
            showBrandMark = prefs[Keys.BRAND_MARK] ?: true
        )
    }

    suspend fun update(fields: StampFields) {
        context.stampDataStore.edit { prefs ->
            prefs[Keys.TEMPLATE] = fields.template.name
            prefs[Keys.POSITION] = fields.position.name
            prefs[Keys.MAP] = fields.showMap
            prefs[Keys.COUNTRY] = fields.showCountry
            prefs[Keys.COORDINATES] = fields.showCoordinates
            prefs[Keys.ADDRESS] = fields.showAddress
            prefs[Keys.TIMESTAMP] = fields.showTimestamp
            prefs[Keys.GMT_OFFSET] = fields.showGmtOffset
            prefs[Keys.ALTITUDE] = fields.showAltitude
            prefs[Keys.ACCURACY] = fields.showAccuracy
            prefs[Keys.BEARING] = fields.showBearing
            prefs[Keys.WEATHER] = fields.showWeather
            prefs[Keys.ORG_LABEL_TOGGLE] = fields.showOrgLabelToggle
            prefs[Keys.ORG_LABEL] = fields.orgLabel
            prefs[Keys.ORG_LOGO_TOGGLE] = fields.showOrgLogo
            fields.orgLogoUri?.let { prefs[Keys.ORG_LOGO_URI] = it }
            prefs[Keys.SIGNATURE_FIELD] = fields.showSignatureField
            prefs[Keys.REQUIRE_SIGNATURE] = fields.requireSignature
            prefs[Keys.BRAND_MARK] = fields.showBrandMark
        }
    }
}
