package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_media")
data class FavoriteMediaEntity(
    @PrimaryKey val uriString: String,
    val mediaId: Long,
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_annotations")
data class MediaAnnotationEntity(
    @PrimaryKey val uriString: String,
    val mediaId: Long,
    val caption: String = "",
    val tagsCsv: String = "",
    val customAlbumName: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_albums")
data class CustomAlbumEntity(
    @PrimaryKey val name: String,
    val description: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "album_media_mappings",
    primaryKeys = ["albumName", "uriString"]
)
data class AlbumMediaMappingEntity(
    val albumName: String,
    val uriString: String,
    val mediaId: Long,
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "picked_media")
data class PickedMediaEntity(
    @PrimaryKey val uriString: String,
    val mediaId: Long,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long = 0L
)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey val uriString: String,
    val mediaId: Long,
    val deletedAtMillis: Long = System.currentTimeMillis(),
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean
)

@Dao
interface GalleryDao {
    // Favorites
    @Query("SELECT * FROM favorite_media ORDER BY addedAtMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorite_media WHERE uriString = :uriString")
    suspend fun deleteFavorite(uriString: String)

    // Annotations (Captions, Tags, Primary Custom Album)
    @Query("SELECT * FROM media_annotations")
    fun observeAnnotations(): Flow<List<MediaAnnotationEntity>>

    @Query("SELECT * FROM media_annotations WHERE uriString = :uriString LIMIT 1")
    suspend fun getAnnotation(uriString: String): MediaAnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(entity: MediaAnnotationEntity)

    // Custom User Albums
    @Query("SELECT * FROM custom_albums ORDER BY createdAtMillis DESC")
    fun observeCustomAlbums(): Flow<List<CustomAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomAlbum(album: CustomAlbumEntity)

    @Query("DELETE FROM custom_albums WHERE name = :albumName")
    suspend fun deleteCustomAlbum(albumName: String)

    // Album-to-Media Local Metadata Mappings
    @Query("SELECT * FROM album_media_mappings ORDER BY addedAtMillis DESC")
    fun observeAlbumMediaMappings(): Flow<List<AlbumMediaMappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumMediaMappings(mappings: List<AlbumMediaMappingEntity>)

    @Query("DELETE FROM album_media_mappings WHERE uriString = :uriString")
    suspend fun deleteAllAlbumMappingsForUri(uriString: String)

    @Query("DELETE FROM album_media_mappings WHERE albumName = :albumName AND uriString = :uriString")
    suspend fun deleteMediaFromAlbum(albumName: String, uriString: String)

    @Query("DELETE FROM album_media_mappings WHERE albumName = :albumName")
    suspend fun deleteAllMappingsForAlbum(albumName: String)

    // Recent Searches
    @Query("SELECT * FROM recent_searches ORDER BY searchedAtMillis DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllRecentSearches()

    // Picked Media (from Android Photo Picker)
    @Query("SELECT * FROM picked_media ORDER BY dateAddedMillis DESC")
    fun observePickedMedia(): Flow<List<PickedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPickedMedia(items: List<PickedMediaEntity>)

    @Query("DELETE FROM picked_media WHERE uriString = :uriString")
    suspend fun deletePickedMedia(uriString: String)

    // Recycle Bin
    @Query("SELECT * FROM recycle_bin ORDER BY deletedAtMillis DESC")
    fun observeRecycleBin(): Flow<List<RecycleBinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleBin(entity: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE uriString = :uriString")
    suspend fun deleteFromRecycleBin(uriString: String)

    @Query("DELETE FROM recycle_bin WHERE deletedAtMillis < :threshold")
    suspend fun clearExpiredRecycleBinItems(threshold: Long)
}

@Database(
    entities = [
        FavoriteMediaEntity::class,
        MediaAnnotationEntity::class,
        CustomAlbumEntity::class,
        AlbumMediaMappingEntity::class,
        RecentSearchEntity::class,
        PickedMediaEntity::class,
        RecycleBinEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "porcelain_gallery.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
