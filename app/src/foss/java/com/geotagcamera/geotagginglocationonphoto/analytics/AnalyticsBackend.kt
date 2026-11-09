package com.geotagcamera.geotagginglocationonphoto.analytics

import android.content.Context

/**
 * "foss" flavor (GitHub, F-Droid): no analytics. No library, no network
 * call, no stored state. Every call is a no-op and [isAvailable] is false,
 * so no consent dialog or setting is ever shown.
 */
internal object AnalyticsBackend {
    const val isAvailable = false
    fun init(context: Context) = Unit
    fun applyConsent(consent: Analytics.Consent) = Unit
    fun screen(name: String) = Unit
    fun event(name: String, params: Map<String, Any>) = Unit
}
