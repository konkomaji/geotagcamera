package com.geotagcamera.geotagginglocationonphoto.support

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's one logger. Writes to logcat and to a small on-device ring file
 * (filesDir/logs/app.log), which a problem report attaches only when the user
 * ticks "Attach app log". Nothing is ever sent on its own.
 *
 * Rule for callers: never log coordinates, addresses, place names, file
 * names of photos or anything the user typed. Log what failed and why
 * (exception class and message), not where the user was.
 */
object AppLog {
    private const val MAX_BYTES = 256 * 1024L
    @Volatile private var file: File? = null
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, "logs/app.log").apply { parentFile?.mkdirs() }
    }

    fun i(tag: String, message: String) = write(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    /** The ring file's text, newest last, or null if there is none. */
    fun read(): String? = file?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear() { file?.delete() }

    private fun write(level: Int, tag: String, message: String, error: Throwable?) {
        val full = "GeoTag/$tag"
        when (level) {
            Log.ERROR -> Log.e(full, message, error)
            Log.WARN -> Log.w(full, message, error)
            else -> Log.i(full, message)
        }
        val f = file ?: return
        val levelChar = when (level) { Log.ERROR -> 'E'; Log.WARN -> 'W'; else -> 'I' }
        // Class and message only: a full trace here could hold user data from exception messages deep in a library.
        val cause = error?.let { " | ${it.javaClass.name}: ${it.message?.take(300)}" }.orEmpty()
        val line = synchronized(time) { time.format(Date()) } + " $levelChar $tag: $message$cause\n"
        runCatching {
            synchronized(this) {
                if (f.length() > MAX_BYTES) {
                    // Keep the newer half.
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2).substringAfter('\n'))
                }
                f.appendText(line)
            }
        }
    }
}
