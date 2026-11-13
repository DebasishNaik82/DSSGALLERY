package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedia(items: List<MediaEntity>)

    @Update
    suspend fun updateMedia(item: MediaEntity)

    @Query(
        "UPDATE media_items SET displayName = :newName, filePath = :newPath, relativePath = :newRelativePath, dateModifiedMillis = :modifiedMillis WHERE uriString = :uriString"
    )
    suspend fun updateMediaRename(
        uriString: String,
        newName: String,
        newPath: String,
        newRelativePath: String,
        modifiedMillis: Long
    )

    @Delete
    suspend fun deleteMedia(item: MediaEntity)

    @Query("DELETE FROM media_items WHERE uriString = :uriString")
    suspend fun deleteMediaByUri(uriString: String)

    @Query("DELETE FROM media_items WHERE uriString IN (:uriStrings)")
    suspend fun deleteMediaByUris(uriStrings: List<String>)

    @Query("DELETE FROM media_items")
    suspend fun clearAllMedia()

    @Query("SELECT * FROM media_items ORDER BY dateTakenMillis DESC")
    fun observeAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateTakenMillis DESC")
    suspend fun getAllMediaSnapshot(): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE uriString = :uriString LIMIT 1")
    suspend fun getMediaByUri(uriString: String): MediaEntity?

    @Query("SELECT * FROM media_items WHERE mediaType = 'IMAGE' ORDER BY dateTakenMillis DESC")
    fun observeImages(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = 'VIDEO' ORDER BY dateTakenMillis DESC")
    fun observeVideos(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE albumName = :albumName COLLATE NOCASE ORDER BY dateTakenMillis DESC")
    fun observeMediaByAlbum(albumName: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE displayName LIKE '%' || :query || '%' ORDER BY dateTakenMillis DESC")
    fun searchMediaByFilename(query: String): Flow<List<MediaEntity>>

    @Transaction
    suspend fun syncMediaItems(items: List<MediaEntity>) {
        val existingMap = getAllMediaSnapshot().associateBy { it.uriString }
        val incomingMap = items.associateBy { it.uriString }

        val removedUris = (existingMap.keys - incomingMap.keys).toList()
        if (removedUris.isNotEmpty()) {
            removedUris.chunked(500).forEach { chunk ->
                deleteMediaByUris(chunk)
            }
        }

        val changedOrNew = items.filter { item ->
            existingMap[item.uriString] != item
        }
        if (changedOrNew.isNotEmpty()) {
            changedOrNew.chunked(500).forEach { chunk ->
                insertAllMedia(chunk)
            }
        }
    }
}
