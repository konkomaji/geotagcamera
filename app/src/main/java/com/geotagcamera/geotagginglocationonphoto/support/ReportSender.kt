package com.geotagcamera.geotagginglocationonphoto.support

import android.content.Context
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a report straight to the developer's inbox, through the small PHP
 * endpoint on the app's own website (site/api/report.php). Only ever called
 * when the user presses Send; the request carries exactly what the report
 * screen showed them, nothing else (no location, photos or identifiers).
 */
object ReportSender {

    const val ENDPOINT = "https://konkomaji.com/geotagcamera/api/report.php"

    /** Must match POW_BITS in site/api/report.php. */
    internal const val POW_BITS = 20

    /**
     * "ts:nonce" such that sha256("geotag-report-v1:ts:contentHash:nonce") starts
     * with [bits] zero bits. About a million hashes on average, roughly a second
     * on a phone: nothing for one report, expensive for a spam run. The hash
     * binds the report's exact content, so a proof can't be reused for another.
     */
    internal fun proofOfWork(ts: Long, contentHash: String, bits: Int = POW_BITS): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val prefix = "geotag-report-v1:$ts:$contentHash:".toByteArray(Charsets.UTF_8)
        var nonce = 0L
        while (true) {
            md.reset()
            md.update(prefix)
            md.update(nonce.toString().toByteArray(Charsets.UTF_8))
            if (hasLeadingZeroBits(md.digest(), bits)) return "$ts:$nonce"
            nonce++
        }
    }

    internal fun hasLeadingZeroBits(hash: ByteArray, bits: Int): Boolean {
        val full = bits / 8
        for (i in 0 until full) if (hash[i] != 0.toByte()) return false
        val rest = bits % 8
        return rest == 0 || (hash[full].toInt() and 0xFF) ushr (8 - rest) == 0
    }

    internal fun sha256Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    sealed interface Result {
        data class Sent(val id: String) : Result
        /** The server answered but refused (rate limit, too large). [message] is safe to show. */
        data class Rejected(val message: String) : Result
        /** No connection, timeout, server down. The email path still works. */
        data object Offline : Result
    }

    suspend fun send(
        context: Context,
        kind: ProblemReport.Kind,
        message: String,
        includeDevice: Boolean,
        crash: String?,
        log: File?,
        contact: String?,
        attachments: List<ReportAttachments.Prepared> = emptyList(),
        /** How long the form was open; part of the server's spam score. */
        elapsedMs: Long = -1
    ): Result = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("kind", kind.name.lowercase())
            put("message", message.trim())
            put("app", "${BuildConfig.VERSION_NAME} (${AppVersion.code(context)}, ${BuildConfig.FLAVOR})")
            if (includeDevice) put("device", ProblemReport.deviceInfo(context))
            crash?.let { put("crash", it) }
            log?.let { f -> runCatching { f.readText().takeLast(200_000) }.getOrNull()?.let { put("log", it) } }
            contact?.trim()?.takeIf { it.isNotEmpty() }?.let { put("contact", it) }
            put("website", "") // honeypot, always empty from the app
            if (elapsedMs >= 0) put("elapsed_ms", elapsedMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }.toString()
        val pow = withContext(Dispatchers.Default) { proofOfWork(System.currentTimeMillis() / 1000, sha256Hex(json)) }
        val boundary = "----GeoTagReport" + java.util.UUID.randomUUID().toString().replace("-", "")

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                // Uploading a 20 MB recording on a slow connection takes a while.
                readTimeout = 90_000
                doOutput = true
                setChunkedStreamingMode(64 * 1024)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "GeoTagCamera/${BuildConfig.VERSION_NAME}")
                // Forces a CORS preflight, so web pages can't post reports through visitors' browsers.
                setRequestProperty("X-GeoTag-Client", "1")
                setRequestProperty("X-GeoTag-PoW", pow)
            }
            conn.outputStream.buffered().use { out ->
                fun line(s: String = "") = out.write((s + "\r\n").toByteArray(Charsets.UTF_8))
                line("--$boundary")
                line("Content-Disposition: form-data; name=\"report\"")
                line("Content-Type: application/json; charset=utf-8")
                line()
                out.write(json.toByteArray(Charsets.UTF_8)); line()
                attachments.forEachIndexed { i, a ->
                    line("--$boundary")
                    line("Content-Disposition: form-data; name=\"attachment[]\"; filename=\"attachment-$i.${a.file.extension}\"")
                    line("Content-Type: ${a.mime}")
                    line()
                    a.file.inputStream().use { it.copyTo(out) }; line()
                }
                line("--$boundary--")
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val body = runCatching { JSONObject(text) }.getOrNull()
            when {
                code in 200..299 && body?.optBoolean("ok") == true -> Result.Sent(body.optString("id"))
                code == 429 || code == 413 || code == 422 || code == 400 || code == 403 || code == 503 ->
                    Result.Rejected(body?.optString("error")?.takeIf { it.isNotBlank() } ?: "The report was not accepted.")
                else -> Result.Offline
            }
        } catch (e: IOException) {
            Result.Offline
        } catch (e: SecurityException) {
            Result.Offline
        } finally {
            conn?.disconnect()
        }
    }
}
