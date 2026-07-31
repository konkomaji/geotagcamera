package com.geotagcamera.geotagginglocationonphoto.exif

import com.geotagcamera.geotagginglocationonphoto.security.IntegrityResult
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
 *   {"v":1,"alg":"SHA256withECDSA","h":<hex>,"sig":<b64>,"pk":<b64>,"t":<ISO8601>}
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
    val timestampIso: String
)

object UserCommentCodec {
    const val VERSION = 1
    const val ALG = "SHA256withECDSA"

    fun encode(result: IntegrityResult, capturedAtEpochMs: Long): String =
        JSONObject()
            .put("v", VERSION)
            .put("alg", ALG)
            .put("h", result.sha256Hex)
            .put("sig", result.signatureBase64)
            .put("pk", result.publicKeyBase64)
            .put("t", iso8601(capturedAtEpochMs))
            .toString()

    /** Parses a proof string; null if absent, malformed, or missing a required field. */
    fun decode(userComment: String?): SignedPayload? {
        if (userComment.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(userComment)
            val payload = SignedPayload(
                version = o.getInt("v"),
                alg = o.optString("alg", ALG),
                sha256Hex = o.getString("h"),
                signatureBase64 = o.getString("sig"),
                publicKeyBase64 = o.getString("pk"),
                timestampIso = o.optString("t", "")
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
