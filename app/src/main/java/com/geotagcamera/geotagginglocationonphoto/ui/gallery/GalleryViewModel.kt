package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import com.geotagcamera.geotagginglocationonphoto.util.launchSafely
import com.geotagcamera.geotagginglocationonphoto.util.safeStateIn
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.security.PhotoVerification
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which photos the gallery shows. */
sealed interface GalleryFilter {
    data object All : GalleryFilter
    data object Unfiled : GalleryFilter
    data class Project(val id: Long) : GalleryFilter
}

/** A long-running export or report, surfaced as a dialog. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Working(val label: String, val done: Int = 0, val total: Int = 0) : ExportState
    data class Ready(val file: java.io.File, val mimeType: String, val title: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Per-tile trust badge. UNSIGNED/UNKNOWN draw nothing — only real signals are surfaced. */
enum class TileStatus { VERIFIED, TAMPERED, UNSIGNED, UNKNOWN }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    // First: visiblePhotos below reads it during construction.
    private val _filter = MutableStateFlow<GalleryFilter>(GalleryFilter.All)
    val filter: StateFlow<GalleryFilter> = _filter.asStateFlow()
    private val photoDao = AppDatabase.get(application).photoDao()
    private val projectDao = AppDatabase.get(application).projectDao()

    val projects: StateFlow<List<ProjectWithCount>> = projectDao.observeAll()
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: GalleryFilter) { _filter.value = filter }

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()
    private var exportJob: kotlinx.coroutines.Job? = null

    fun dismissExport() { exportJob?.cancel(); _export.value = ExportState.Idle }

    /** What the current filter is called, for file names and report titles. */
    fun scopeTitle(): String = when (val f = _filter.value) {
        GalleryFilter.All -> "All photos"
        GalleryFilter.Unfiled -> "Photos without a project"
        is GalleryFilter.Project -> projects.value.firstOrNull { it.id == f.id }?.name ?: "Project"
    }

    fun exportLocations(format: com.geotagcamera.geotagginglocationonphoto.export.ExportFormat, photos: List<PhotoEntity>, title: String) {
        if (photos.isEmpty()) return
        exportJob?.cancel()
        _export.value = ExportState.Working("Exporting ${photos.size} location" + if (photos.size == 1) "" else "s")
        exportJob = viewModelScope.launchSafely(tag = "Gallery") {
            _export.value = runCatching {
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val names = projects.value.associate { it.id to it.name }
                    val text = com.geotagcamera.geotagginglocationonphoto.export.PhotoExport.render(format, photos.sortedBy { it.capturedAtEpochMs }, names, title)
                    val file = com.geotagcamera.geotagginglocationonphoto.export.ExportFiles.newFile(context, title, format.extension)
                    file.writeText(text, Charsets.UTF_8)
                    ExportState.Ready(file, format.mimeType, "$title (${format.label})")
                }
            }.getOrElse { ExportState.Failed("Couldn't write the export. Check free storage and try again.") }
        }
    }

    fun buildReport(photos: List<PhotoEntity>, title: String, organisation: String?) {
        if (photos.isEmpty()) return
        exportJob?.cancel()
        _export.value = ExportState.Working("Building report", 0, photos.size)
        exportJob = viewModelScope.launchSafely(tag = "Gallery") {
            _export.value = runCatching {
                val context = getApplication<Application>()
                val file = com.geotagcamera.geotagginglocationonphoto.export.ExportFiles.newFile(context, title, "pdf")
                com.geotagcamera.geotagginglocationonphoto.export.ProjectReport(context).build(
                    com.geotagcamera.geotagginglocationonphoto.export.ProjectReport.Input(
                        title = title, subtitle = "Site photo report", organisation = organisation, photos = photos
                    ),
                    file
                ) { done, total -> _export.value = ExportState.Working("Building report", done, total) }
                ExportState.Ready(file, "application/pdf", "$title (PDF report)")
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is OutOfMemoryError) ExportState.Failed("Not enough memory for this report. Try fewer photos.")
                else ExportState.Failed("Couldn't build the report. Check free storage and try again.")
            }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    /** Adds picked GeoTag photos to the list, into the filtered project if one is shown. */
    fun importPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launchSafely(tag = "Gallery") {
            val project = (_filter.value as? GalleryFilter.Project)?.id
            val result = withContext(Dispatchers.IO) {
                runCatching { com.geotagcamera.geotagginglocationonphoto.data.PhotoImporter(getApplication()).import(uris, project) }.getOrNull()
            }
            _message.value = result?.summary() ?: "Couldn't import those photos."
        }
    }

    fun moveToProject(ids: Collection<Long>, projectId: Long?) {
        viewModelScope.launchSafely(tag = "Gallery") { photoDao.moveToProject(ids.toList(), projectId) }
    }

    /** Creates a project and moves [ids] into it. Returns an error message or null. */
    suspend fun createProjectAndMove(name: String, ids: Collection<Long>): String? {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return "Give the project a name."
        if (projectDao.countWithName(clean) > 0) return "A project with that name already exists."
        val id = projectDao.insert(com.geotagcamera.geotagginglocationonphoto.data.ProjectEntity(name = clean, createdAtEpochMs = System.currentTimeMillis()))
        photoDao.moveToProject(ids.toList(), id)
        return null
    }

    fun renameProject(id: Long, name: String) {
        val clean = name.trim().take(80)
        if (clean.isNotEmpty()) viewModelScope.launchSafely(tag = "Gallery") { projectDao.rename(id, clean) }
    }

    fun setArchived(id: Long, archived: Boolean) { viewModelScope.launchSafely(tag = "Gallery") { projectDao.setArchived(id, archived) } }

    fun deleteProject(id: Long) {
        viewModelScope.launchSafely(tag = "Gallery") {
            projectDao.delete(id)
            if ((_filter.value as? GalleryFilter.Project)?.id == id) _filter.value = GalleryFilter.All
        }
    }

    /** Null until the first database emission, so the screen can tell "loading" from "empty". */
    val photos: StateFlow<List<PhotoEntity>?> = photoDao.observeAll()
        .safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [photos] narrowed to the current filter. */
    val visiblePhotos: StateFlow<List<PhotoEntity>?> = kotlinx.coroutines.flow.combine(photos, _filter) { list, f ->
        list?.filter { p ->
            when (f) {
                GalleryFilter.All -> true
                GalleryFilter.Unfiled -> p.projectId == null
                is GalleryFilter.Project -> p.projectId == f.id
            }
        }
    }.safeStateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tileStatus = MutableStateFlow<Map<Long, TileStatus>>(emptyMap())
    val tileStatus: StateFlow<Map<Long, TileStatus>> = _tileStatus.asStateFlow()

    /** A system "allow deleting these photos" prompt the screen must launch. */
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent: StateFlow<IntentSender?> = _deleteConsent.asStateFlow()
    private var idsAwaitingConsent: List<Long> = emptyList()

    init {
        // One verification pass at a time: a new list cancels the old pass,
        // and results are cached so each photo is only read once per session.
        viewModelScope.launchSafely(tag = "Gallery") {
            photoDao.observeAll().collectLatest { list -> verifyMissing(list) }
        }
    }

    private suspend fun verifyMissing(list: List<PhotoEntity>) = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val gone = mutableListOf<Long>()
        for (photo in list) {
            if (_tileStatus.value.containsKey(photo.id)) continue
            val uri = Uri.parse(photo.filePath)
            if (!exists(uri)) {
                // Deleted from the system gallery: drop the stale index row.
                gone += photo.id
                continue
            }
            val status = when (PhotoVerification.verifyUri(context, uri)) {
                is VerificationOutcome.Untampered -> TileStatus.VERIFIED
                is VerificationOutcome.Edited -> TileStatus.TAMPERED
                is VerificationOutcome.MetadataUnavailable -> TileStatus.UNKNOWN
                is VerificationOutcome.NoProof -> TileStatus.UNSIGNED
                is VerificationOutcome.Unreadable -> TileStatus.UNKNOWN
            }
            _tileStatus.update { it + (photo.id to status) }
        }
        if (gone.isNotEmpty()) photoDao.deleteByIds(gone)
    }

    /**
     * False only when the file is definitely gone. Any other error (no storage
     * permission on Android 8/9, a busy provider) keeps the row: deleting a
     * photo's index entry, with its hash and signature, on a transient error
     * would be unrecoverable.
     */
    private fun exists(uri: Uri): Boolean = try {
        getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: java.io.FileNotFoundException) {
        false
    } catch (e: Exception) {
        true
    }

    /** Deletes the photos from the device gallery and the index. May ask for consent first. */
    fun delete(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        viewModelScope.launchSafely(tag = "Gallery") {
            val byUri = photos.associateBy { it.filePath }
            val outcome = withContext(Dispatchers.IO) {
                MediaStoreImageSaver.delete(getApplication(), photos.map { Uri.parse(it.filePath) })
            }
            // Only rows whose file is really gone; the rest wait for consent or stay.
            val deletedIds = outcome.deleted.mapNotNull { byUri[it.toString()]?.id }
            if (deletedIds.isNotEmpty()) photoDao.deleteByIds(deletedIds)
            _tileStatus.update { it - deletedIds.toSet() }
            if (outcome.consent != null) {
                idsAwaitingConsent = outcome.awaitingConsent.mapNotNull { byUri[it.toString()]?.id }
                _deleteConsent.value = outcome.consent
            }
        }
    }

    /** Called with the outcome of the system consent prompt. */
    fun onDeleteConsentResult(granted: Boolean) {
        _deleteConsent.value = null
        val ids = idsAwaitingConsent
        idsAwaitingConsent = emptyList()
        if (granted && ids.isNotEmpty()) viewModelScope.launchSafely(tag = "Gallery") { photoDao.deleteByIds(ids) }
    }
}
