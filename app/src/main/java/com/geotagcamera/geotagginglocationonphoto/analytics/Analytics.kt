package com.geotagcamera.geotagginglocationonphoto.analytics

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Usage statistics, Google Play build only.
 *
 * The "foss" flavor (GitHub, F-Droid) ships a no-op [AnalyticsBackend] with
 * no analytics library at all; [isAvailable] is false there and nothing below
 * does anything. The "play" flavor's backend is Google Analytics for Firebase.
 *
 * Nothing is collected until the user chooses, in the consent dialog:
 * - [Consent.usage]: anonymous usage statistics (screens, feature use).
 * - [Consent.demographics]: approximate age range, gender and interests,
 *   which Google derives using the Android advertising ID.
 * Both default to off and can be changed any time in Settings.
 *
 * Never sent, by design of every call site: coordinates, addresses, place
 * names, photo contents or file names, notes, anything the user typed.
 */
object Analytics {

    data class Consent(val asked: Boolean, val usage: Boolean, val demographics: Boolean)

    private const val PREFS = "analytics_consent"
    private val _consent = MutableStateFlow(Consent(asked = false, usage = false, demographics = false))
    val consent: StateFlow<Consent> = _consent.asStateFlow()

    val isAvailable: Boolean get() = AnalyticsBackend.isAvailable

    fun init(context: Context) {
        if (!isAvailable) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _consent.value = Consent(p.getBoolean("asked", false), p.getBoolean("usage", false), p.getBoolean("demographics", false))
        AnalyticsBackend.init(context.applicationContext)
        AnalyticsBackend.applyConsent(_consent.value)
    }

    fun setConsent(context: Context, usage: Boolean, demographics: Boolean) {
        if (!isAvailable) return
        // Demographics are derived from usage data, so they need usage too.
        val c = Consent(asked = true, usage = usage, demographics = usage && demographics)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean("asked", true); putBoolean("usage", c.usage); putBoolean("demographics", c.demographics)
        }
        _consent.value = c
        AnalyticsBackend.applyConsent(c)
    }

    /** A navigation destination, by route pattern only (no arguments: they can hold ids). */
    fun screen(route: String?) {
        if (!isAvailable || !_consent.value.usage || route == null) return
        AnalyticsBackend.screen(route.substringBefore('?').substringBefore('/'))
    }

    /** A feature used. [params] must be coarse facts (a template name, a count), never user data. */
    fun event(name: String, params: Map<String, Any> = emptyMap()) {
        if (!isAvailable || !_consent.value.usage) return
        AnalyticsBackend.event(name, params)
    }
}
