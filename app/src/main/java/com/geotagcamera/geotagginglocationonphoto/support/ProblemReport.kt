package com.geotagcamera.geotagginglocationonphoto.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.export.ExportFiles
import java.io.File

/** Builds the report for the user's email app or GitHub. Direct sending is [ReportSender]. */
object ProblemReport {

    const val EMAIL = "hello@konkomaji.com"
    const val ISSUES_URL = "https://github.com/konkomaji/geotagcamera/issues/new"
    /** Where this build came from, for the bug form's version answer. */
    private val SOURCE = if (BuildConfig.FLAVOR == "play") "Google Play" else "GitHub or F-Droid"

    enum class Kind(val label: String) { PROBLEM("Problem"), IDEA("Idea"), QUESTION("Question") }

    /** Facts that help reproduce a problem. Never location, photos or captures. */
    fun deviceInfo(context: Context): String = buildString {
        appendLine("App: GeoTag Camera ${BuildConfig.VERSION_NAME} (${AppVersion.code(context)}, ${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("CPU: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
        val dm = Resources.getSystem().displayMetrics
        append("Screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi")
    }

    fun subject(kind: Kind, message: String): String =
        "[GeoTag Camera ${BuildConfig.VERSION_NAME}] ${kind.label}: " + message.lineSequence().first().take(60)

    fun body(context: Context, message: String, includeDevice: Boolean, crash: String?): String = buildString {
        appendLine(message.trim())
        if (includeDevice) { appendLine(); appendLine("--"); appendLine(deviceInfo(context)) }
        if (crash != null) { appendLine(); appendLine("-- Crash details --"); appendLine(crash) }
    }

    /**
     * The app's own recent log lines, written to a shareable file. Lines naming
     * map tiles are dropped: tile numbers reveal roughly where you are.
     */
    fun captureLog(context: Context): File? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "800", "--pid", android.os.Process.myPid().toString()))
        // Only our own lines and crash traces: library logs may name map tiles, which reveal a rough location.
        val lines = process.inputStream.bufferedReader().readLines()
            .filter { it.contains("GeoTag/") || it.contains("AndroidRuntime") }
            .filterNot { it.contains("openfreemap", ignoreCase = true) || it.contains(".pbf") }
        val own = AppLog.read()?.let { "--- App log (this phone only) ---\n$it\n\n--- System log ---\n" }.orEmpty()
        ExportFiles.newFile(context, "geotag-app-log", "txt").apply { writeText(own + lines.joinToString("\n")) }
    }.getOrNull()

    /** Opens an email app. Returns false when the phone has none. */
    fun send(context: Context, subject: String, body: String, attachment: File?): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attachment != null) {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, ExportFiles.uriFor(context, attachment))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "message/rfc822"
            // Only email apps, not every app that accepts text.
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        return try {
            context.startActivity(Intent.createChooser(send, "Send report with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * A GitHub issue form, prefilled with what the user wrote and (if they
     * allowed it) the device details. Public: GitHub shows it to everyone,
     * so crash details and logs are never put in. Nothing is posted until
     * the user submits the form there.
     */
    fun issueUrl(kind: Kind, message: String, includeDevice: Boolean, context: Context): String {
        val m = message.trim().take(1500)
        val title = m.lineSequence().firstOrNull().orEmpty().take(80)
        val params = buildList {
            when (kind) {
                Kind.PROBLEM -> {
                    add("template" to "bug_report.yml")
                    add("what-happened" to m)
                    add("app-version" to "${BuildConfig.VERSION_NAME} (${AppVersion.code(context)}, ${BuildConfig.FLAVOR}, from $SOURCE)")
                    if (includeDevice) add("device" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
                Kind.IDEA -> { add("template" to "feature_request.yml"); add("problem" to m) }
                Kind.QUESTION -> {
                    add("template" to "question.yml"); add("question" to m)
                    if (includeDevice) add("context" to deviceInfo(context))
                }
            }
            if (title.isNotBlank()) add("title" to title)
        }
        return ISSUES_URL + "?" + params.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
    }

    fun openIssues(context: Context, url: String = ISSUES_URL) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** For phones with no email app: the whole report, ready to paste anywhere. */
    fun copyToClipboard(context: Context, subject: String, body: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("GeoTag Camera report", "To: $EMAIL\nSubject: $subject\n\n$body")
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        cm.setPrimaryClip(clip)
    }
}
