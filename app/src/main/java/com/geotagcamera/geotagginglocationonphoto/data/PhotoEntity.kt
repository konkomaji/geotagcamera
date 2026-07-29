package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One captured, stamped photo. [sha256Hash] and [signatureBase64] back the
 * tamper-evident guarantee — recomputing the hash from the file bytes and
 * checking it against the stored, key-signed value is how "unedited since
 * capture" gets verified later.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val capturedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val address: String?,
    val addressFromCache: Boolean,
    val orgLabel: String?,
    val sha256Hash: String,
    val signatureBase64: String,
    val signingKeyAlias: String,
    val fieldWorkerSignature: Boolean
)
