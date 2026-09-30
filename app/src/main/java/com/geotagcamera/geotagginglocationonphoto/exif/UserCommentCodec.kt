package com.geotagcamera.geotagginglocationonphoto.exif

import com.geotagcamera.geotagginglocationonphoto.security.IntegrityResult
import com.geotagcamera.geotagginglocationonphoto.security.ProofClaims
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The self-describing integrity proof carried inside every signed photo,
 * mirrored in EXIF UserComment and XMP so any device with the app can verify
 * a file it never captured.
 *
 * Wire form is compact JSON (via [JSONObject], which ships in the Android SDK
 * — zero new dependency, and a real parser for adversarial/foreign files):
 *
 *   {"v":2,"alg":"SHA256withECDSA","h":<hex>,"sig":<b64>,"pk":<b64>,"t":<ISO8601>,
 *    "lat":<deg>,"lon":<deg>,"ms":<epoch ms>}   (lat/lon/ms signed; v1 omits them)
 *
 * With NIST P-256 keys every field is small — SHA-256 hex is 64 chars, the
 * DER signature base64 ~96, the X.509 public key base64 ~124 — so the whole
 * payload lands comfortably under a few hundred bytes, well within EXIF
 * UserComment's practical budget (asserted by test, not assumed).
 */
data class SignedPayload(
    val version: Int,
    val alg: String,
    val sha256Hex: String,
    val signatureBase64: String,
    val publicKeyBase64: String,
    val timestampIso: String,
    /** Signed location/time claims; non-null iff proof version >= 2. */
    val claims: ProofClaims? = null
)

object UserCommentCodec {
    /** v1: signature over the image hash only. v2: also commits to lat/lon/time. */
    const val VERSION = 2
    const val ALG = "SHA256withECDSA"

    fun encode(result: IntegrityResult, capturedAtEpochMs: Long): String =
        JSONObject()
            .put("v", if (result.claims != null) VERSION else 1)
            .put("alg", ALG)
            .put("h", result.sha256Hex)
            .put("sig", result.signatureBase64)
            .put("pk", result.publicKeyBase64)
            .put("t", iso8601(capturedAtEpochMs))
            .apply {
                result.claims?.let {
                    put("lat", it.latitude)
                    put("lon", it.longitude)
                    put("ms", it.epochMs)
                }
            }
            .toString()

    /** Parses a proof string; null if absent, malformed, or missing a required field. */
    fun decode(userComment: String?): SignedPayload? {
        if (userComment.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(userComment)
            val version = o.getInt("v")
            val claims = if (version >= 2) {
                ProofClaims(o.getDouble("lat"), o.getDouble("lon"), o.getLong("ms"))
            } else null
            val payload = SignedPayload(
                version = version,
                alg = o.optString("alg", ALG),
                sha256Hex = o.getString("h"),
                signatureBase64 = o.getString("sig"),
                publicKeyBase64 = o.getString("pk"),
                timestampIso = o.optString("t", ""),
                claims = claims
            )
            if (payload.sha256Hex.isBlank() ||
                payload.signatureBase64.isBlank() ||
                payload.publicKeyBase64.isBlank()
            ) return null
            payload
        }.getOrNull()
    }

    private fun iso8601(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))
}
