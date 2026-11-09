package com.geotagcamera.geotagginglocationonphoto.util

import com.geotagcamera.geotagginglocationonphoto.support.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The app's error policy for background work, in one place:
 *
 * - Nothing a ViewModel starts may crash the app. A disk, database or
 *   settings failure (IOException, SQLiteException, a DataStore write that
 *   fails) is logged to [AppLog] and reported once to the user through
 *   [AppErrors], instead of taking the whole app down.
 * - Cancellation is never swallowed.
 * - Screens with their own, more specific messages still catch first; this is
 *   the net under them.
 */
object AppErrors {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short messages for the user; [com.geotagcamera.geotagginglocationonphoto.ui.nav.AppNavHost] shows them. */
    val events: SharedFlow<String> = _events

    fun report(message: String) { _events.tryEmit(message) }

    fun messageFor(e: Throwable): String = when (e) {
        is OutOfMemoryError -> "Not enough memory for that. Close other apps and try again."
        is android.database.sqlite.SQLiteFullException -> "Your phone's storage is full. Free up space and try again."
        is android.database.sqlite.SQLiteException -> "The app's photo list couldn't be updated. Try again."
        is java.io.IOException -> "Couldn't save that change. Check your phone's free storage and try again."
        is SecurityException -> "Android didn't allow that. Check the app's permissions."
        else -> "Something went wrong. Try again."
    }
}

/** [launch] that logs and reports failures instead of crashing. */
fun CoroutineScope.launchSafely(
    context: CoroutineContext = EmptyCoroutineContext,
    tag: String = "Task",
    onError: ((Throwable) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job = launch(context) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // A broken VM (StackOverflow etc.) is not recoverable; memory pressure is.
        if (e is VirtualMachineError && e !is OutOfMemoryError) throw e
        AppLog.e(tag, "background task failed", e)
        if (onError != null) onError(e) else AppErrors.report(AppErrors.messageFor(e))
    }
}

/** [stateIn] whose upstream failure (a database or settings read) leaves [initial] in place instead of crashing. */
fun <T> Flow<T>.safeStateIn(scope: CoroutineScope, started: SharingStarted, initial: T, tag: String = "Flow"): StateFlow<T> =
    catch { e ->
        AppLog.e(tag, "data stream failed", e)
        AppErrors.report(AppErrors.messageFor(e))
        emit(initial)
    }.stateIn(scope, started, initial)
