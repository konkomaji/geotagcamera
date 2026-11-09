package com.geotagcamera.geotagginglocationonphoto.support

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone so the user can choose to send it.
 * Nothing leaves the device on its own: the next launch offers a report
 * (see [ReportProblemScreen]); declining deletes the file.
 *
 * Two sources: Kotlin/Java exceptions, caught as they happen, and (Android 11+)
 * what the system recorded about the last exit: native crashes (MapLibre,
 * camera drivers) and freezes ("app not responding"), which no in-app
 * handler can catch. See [collectSystemExits].
 */
object CrashCatcher {

    private const val FILE = "crash/last_crash.txt"
    private const val MAX_CHARS = 20_000
    private const val EXIT_MARKER = "crash/last_exit_check"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /**
     * Reads the system's record of recent exits once per launch and keeps the
     * newest native crash or freeze as a pending report. Blocking file I/O:
     * call off the main thread. The first run only sets the baseline, so
     * history from before this version is never offered.
     */
    fun collectSystemExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val marker = File(context.filesDir, EXIT_MARKER).apply { parentFile?.mkdirs() }
        val since = runCatching { marker.readText().trim().toLong() }.getOrNull()
        marker.writeText(System.currentTimeMillis().toString())
        if (since == null || pending(context) != null) return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val exit = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 5) }.getOrNull()
            ?.filter { it.timestamp > since }
            ?.firstOrNull { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_ANR }
            ?: return
        val anr = exit.reason == ApplicationExitInfo.REASON_ANR
        val text = buildString {
            appendLine("${if (anr) "Freeze (app not responding)" else "Native crash"} at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(exit.timestamp))}")
            appendLine(header(context))
            exit.description?.let { appendLine("System: $it") }
            appendLine("Memory: ${exit.pss / 1024} MB PSS")
            // ANR traces are plain-text thread dumps. Native tombstones are binary on Android 12+, so skipped.
            if (anr) {
                runCatching {
                    exit.traceInputStream?.bufferedReader()?.use { r ->
                        val buf = CharArray(12_000)
                        String(buf, 0, r.read(buf).coerceAtLeast(0))
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }?.let { appendLine(); append(it) }
            }
        }.take(MAX_CHARS)
        File(context.filesDir, FILE).apply { parentFile?.mkdirs() }.writeText(text)
    }

    private fun header(context: Context) =
        "App ${BuildConfig.VERSION_NAME} (${AppVersion.code(context)}, ${BuildConfig.FLAVOR}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("Crash at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine(header(context))
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }.take(MAX_CHARS)
        File(context.filesDir, FILE).apply { parentFile?.mkdirs() }.writeText(text)
    }
}
