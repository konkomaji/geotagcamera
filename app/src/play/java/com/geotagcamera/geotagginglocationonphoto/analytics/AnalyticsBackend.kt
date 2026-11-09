package com.geotagcamera.geotagginglocationonphoto.analytics

import android.content.Context
import android.os.Bundle
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.support.AppLog
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * "play" flavor: Google Analytics for Firebase.
 *
 * Firebase is set up by hand from BuildConfig (no google-services plugin and
 * no google-services.json in the repository): the values come from the
 * gitignored firebase.properties or CI secrets. Without them this build has
 * analytics turned off, like the foss build.
 *
 * Collection is off in the manifest until [applyConsent] turns it on, and
 * Google consent mode starts with every storage type denied
 * (src/play/AndroidManifest.xml), so nothing is sent before the user agrees.
 */
internal object AnalyticsBackend {
    val isAvailable: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (!isAvailable || fa != null) return
        fa = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build()
                )
            }
            FirebaseAnalytics.getInstance(context)
        }.onFailure { AppLog.w("Analytics", "Firebase setup failed, analytics off", it) }.getOrNull()
    }

    fun applyConsent(consent: Analytics.Consent) {
        val a = fa ?: return
        val usage = if (consent.usage) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        val demo = if (consent.demographics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        a.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to usage,
                FirebaseAnalytics.ConsentType.AD_STORAGE to demo,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to demo,
                // Never used for ads: this app shows none.
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
            )
        )
        a.setAnalyticsCollectionEnabled(consent.usage)
    }

    fun screen(name: String) {
        fa?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
        })
    }

    fun event(name: String, params: Map<String, Any>) {
        fa?.logEvent(name, Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is Int -> putLong(k, v.toLong())
                    is Long -> putLong(k, v)
                    is Boolean -> putString(k, v.toString())
                    else -> putString(k, v.toString().take(100))
                }
            }
        })
    }
}
