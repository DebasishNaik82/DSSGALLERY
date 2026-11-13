package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.MediaItem

enum class MediaType {
    IMAGE,
    VIDEO
}

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["mediaType"]),
        Index(value = ["albumName"]),
        Index(value = ["displayName"]),
        Index(value = ["dateTakenMillis"]),
        Index(value = ["dateModifiedMillis"])
    ]
)
data class MediaEntity(
    @PrimaryKey
    val uriString: String,
    val mediaId: Long,
    val displayName: String,
    val filePath: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val mimeType: String,
    val dateAddedMillis: Long,
    val dateModifiedMillis: Long,
    val dateTakenMillis: Long,
    val albumName: String,
    val bucketId: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long = 0L,
    val isFavorite: Boolean = false
)

fun MediaEntity.toMediaItem(): MediaItem = MediaItem(
    id = mediaId,
    uri = Uri.parse(uriString),
    displayName = displayName,
    mimeType = mimeType,
    isVideo = mediaType == MediaType.VIDEO,
    dateAddedMillis = dateAddedMillis,
    dateModifiedMillis = dateModifiedMillis,
    dateTakenMillis = dateTakenMillis,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    bucketId = bucketId,
    bucketDisplayName = albumName,
    relativePath = relativePath.ifBlank { filePath },
    isFavorite = isFavorite
)

fun MediaItem.toMediaEntity(): MediaEntity = MediaEntity(
    uriString = uri.toString(),
    mediaId = id,
    displayName = displayName,
    filePath = relativePath,
    mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    dateAddedMillis = dateAddedMillis,
    dateModifiedMillis = dateModifiedMillis,
    dateTakenMillis = dateTakenMillis,
    albumName = bucketDisplayName,
    bucketId = bucketId,
    relativePath = relativePath,
    width = width,
    height = height,
    durationMillis = durationMillis,
    isFavorite = isFavorite
)
