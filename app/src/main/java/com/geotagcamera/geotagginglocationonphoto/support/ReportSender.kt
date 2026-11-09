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
        attachments: List<ReportAttachments.Prepared> = emptyList()
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
        }.toString()
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
                code == 429 || code == 413 || code == 422 ->
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
