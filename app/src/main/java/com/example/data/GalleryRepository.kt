package com.example.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.example.data.local.AlbumMediaMappingEntity
import com.example.data.local.CustomAlbumEntity
import com.example.data.local.FavoriteMediaEntity
import com.example.data.local.GalleryDao
import com.example.data.local.MediaAnnotationEntity
import com.example.data.local.PickedMediaEntity
import com.example.data.local.RecentSearchEntity
import com.example.data.local.RecycleBinEntity
import com.example.data.mediastore.MediaStoreDataSource
import com.example.model.AlbumItem
import com.example.model.ExifTelemetry
import com.example.model.MediaItem
import com.example.model.MemoryCollection
import com.example.model.PermissionAccessLevel
import com.example.model.SortOption
import com.example.model.SystemAlbumType
import com.example.model.TimelineDayGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class GalleryRepository(
    private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val galleryDao: GalleryDao,
    externalScope: CoroutineScope
) {
    private val rawDeviceMediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    private val isLoadingFlow = MutableStateFlow(false)

    private val deleteMutex = Mutex()
    private val pendingDeleteItemsFlow = MutableStateFlow<List<RecycleBinEntity>>(emptyList())
    private val pendingDeleteIntentSenderFlow = MutableStateFlow<IntentSender?>(null)
    private val deleteStatusMessageFlow = MutableStateFlow<String?>(null)
    private val isConsentDialogLaunched = AtomicBoolean(false)

    val isLoading: Flow<Boolean> = isLoadingFlow
    val pendingDeleteIntentSender: StateFlow<IntentSender?> = pendingDeleteIntentSenderFlow.asStateFlow()
    val deleteStatusMessage: StateFlow<String?> = deleteStatusMessageFlow.asStateFlow()

    init {
        externalScope.launch(Dispatchers.IO) {
            mediaStoreDataSource.observeMediaStoreChanges().collect {
                refreshMediaStore()
            }
        }
        externalScope.launch(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            galleryDao.clearExpiredRecycleBinItems(thirtyDaysAgo)
        }
    }

    fun getPermissionAccessLevel(): PermissionAccessLevel =
        mediaStoreDataSource.getPermissionAccessLevel()

    fun getRequiredPermissions(): Array<String> =
        mediaStoreDataSource.getRequiredPermissions()

    suspend fun refreshMediaStore() {
        isLoadingFlow.value = true
        try {
            val deviceItems = mediaStoreDataSource.queryAllDeviceMedia()
            rawDeviceMediaFlow.value = deviceItems
        } finally {
            isLoadingFlow.value = false
        }
    }

    private data class RoomMetadataSnapshot(
        val pickedEntities: List<PickedMediaEntity>,
        val favoriteEntities: List<FavoriteMediaEntity>,
        val annotationEntities: List<MediaAnnotationEntity>,
        val albumMappings: List<AlbumMediaMappingEntity>,
        val recycleBinEntities: List<RecycleBinEntity>
    )

    private val roomMetadataFlow: Flow<RoomMetadataSnapshot> = combine(
        galleryDao.observePickedMedia(),
        galleryDao.observeFavorites(),
        galleryDao.observeAnnotations(),
        galleryDao.observeAlbumMediaMappings(),
        galleryDao.observeRecycleBin()
    ) { pickedEntities, favoriteEntities, annotationEntities, albumMappings, recycleBinEntities ->
        RoomMetadataSnapshot(
            pickedEntities = pickedEntities,
            favoriteEntities = favoriteEntities,
            annotationEntities = annotationEntities,
            albumMappings = albumMappings,
            recycleBinEntities = recycleBinEntities
        )
    }

    val allMediaFlow: Flow<List<MediaItem>> = combine(
        rawDeviceMediaFlow,
        roomMetadataFlow
    ) { deviceMedia, roomData ->
        val recycleBinUris = roomData.recycleBinEntities.map { it.uriString }.toSet()
        val recycleBinIds = roomData.recycleBinEntities.map { it.mediaId }.toSet()

        val favSet = roomData.favoriteEntities.map { it.uriString }.toSet()
        val annotationsMap = roomData.annotationEntities.associateBy { it.uriString }
        val mappingsByUri = roomData.albumMappings.groupBy({ it.uriString }, { it.albumName })

        val pickedItems = roomData.pickedEntities.map { entity ->
            MediaItem(
                id = entity.mediaId,
                uri = Uri.parse(entity.uriString),
                displayName = entity.displayName,
                mimeType = entity.mimeType,
                isVideo = entity.isVideo,
                dateAddedMillis = entity.dateAddedMillis,
                dateModifiedMillis = entity.dateAddedMillis,
                dateTakenMillis = entity.dateAddedMillis,
                sizeBytes = entity.sizeBytes,
                width = entity.width,
                height = entity.height,
                durationMillis = entity.durationMillis,
                bucketId = "picked_photos",
                bucketDisplayName = "Picked Photos",
                relativePath = "/Selected Photos/${entity.displayName}"
            )
        }

        val combinedMap = LinkedHashMap<String, MediaItem>()
        deviceMedia.forEach { item ->
            val uriStr = item.uri.toString()
            if (uriStr !in recycleBinUris && item.id !in recycleBinIds) {
                combinedMap[uriStr] = item
            }
        }
        pickedItems.forEach { item ->
            val uriStr = item.uri.toString()
            if (uriStr !in recycleBinUris && item.id !in recycleBinIds && !combinedMap.containsKey(uriStr)) {
                combinedMap[uriStr] = item
            }
        }

        combinedMap.values.map { item ->
            val uriStr = item.uri.toString()
            val annotation = annotationsMap[uriStr]
            val tags = annotation?.tagsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            val mappedAlbums = buildList {
                annotation?.customAlbumName?.takeIf { it.isNotBlank() }?.let { add(it) }
                mappingsByUri[uriStr]?.forEach { albumName ->
                    if (albumName.isNotBlank() && !contains(albumName)) {
                        add(albumName)
                    }
                }
            }

            item.copy(
                isFavorite = item.isFavorite || favSet.contains(uriStr),
                caption = annotation?.caption ?: "",
                tags = tags,
                customAlbum = mappedAlbums.firstOrNull(),
                customAlbums = mappedAlbums
            )
        }
    }

    val customAlbumsFlow: Flow<List<CustomAlbumEntity>> = galleryDao.observeCustomAlbums()

    val recentSearchesFlow: Flow<List<String>> = combine(
        galleryDao.observeRecentSearches()
    ) { array ->
        array[0].map { it.query }
    }

    suspend fun toggleFavorite(item: MediaItem) {
        val uriStr = item.uri.toString()
        if (item.isFavorite) {
            galleryDao.deleteFavorite(uriStr)
        } else {
            galleryDao.insertFavorite(
                FavoriteMediaEntity(
                    uriString = uriStr,
                    mediaId = item.id,
                    addedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun setFavoriteForItems(items: List<MediaItem>, favorite: Boolean) {
        items.forEach { item ->
            val uriStr = item.uri.toString()
            if (favorite) {
                galleryDao.insertFavorite(
                    FavoriteMediaEntity(
                        uriString = uriStr,
                        mediaId = item.id,
                        addedAtMillis = System.currentTimeMillis()
                    )
                )
            } else {
                galleryDao.deleteFavorite(uriStr)
            }
        }
    }

    suspend fun saveCaptionAndTags(
        item: MediaItem,
        caption: String,
        tags: List<String>,
        customAlbum: String? = item.customAlbum
    ) {
        val uriStr = item.uri.toString()
        val existing = galleryDao.getAnnotation(uriStr)
        val targetAlbum = customAlbum ?: existing?.customAlbumName
        galleryDao.upsertAnnotation(
            MediaAnnotationEntity(
                uriString = uriStr,
                mediaId = item.id,
                caption = caption.trim(),
                tagsCsv = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(","),
                customAlbumName = targetAlbum,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!targetAlbum.isNullOrBlank()) {
            galleryDao.insertCustomAlbum(CustomAlbumEntity(name = targetAlbum))
            galleryDao.insertAlbumMediaMappings(
                listOf(
                    AlbumMediaMappingEntity(
                        albumName = targetAlbum,
                        uriString = uriStr,
                        mediaId = item.id
                    )
                )
            )
        }
    }

    /**
     * Creates a custom album in Room and optionally moves or adds initial media items into it.
     */
    suspend fun createCustomAlbum(
        name: String,
        description: String = "Personal Collection",
        initialItems: List<MediaItem> = emptyList(),
        moveItems: Boolean = true
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        galleryDao.insertCustomAlbum(
            CustomAlbumEntity(
                name = cleanName,
                description = description.ifBlank { "Personal Collection" }
            )
        )
        if (initialItems.isNotEmpty()) {
            if (moveItems) {
                moveItemsToCustomAlbum(initialItems, cleanName)
            } else {
                addItemsToCustomAlbum(initialItems, cleanName)
            }
        }
    }

    /**
     * Moves the selected photos into [albumName] using Room database for local metadata mapping.
     * Clears any previous custom album mappings for these items so they are moved cleanly into [albumName].
     */
    suspend fun moveItemsToCustomAlbum(items: List<MediaItem>, albumName: String) {
        val cleanName = albumName.trim()
        if (cleanName.isEmpty() || items.isEmpty()) return

        galleryDao.insertCustomAlbum(CustomAlbumEntity(name = cleanName))
        val now = System.currentTimeMillis()
        val mappings = mutableListOf<AlbumMediaMappingEntity>()

        for (item in items) {
            val uriStr = item.uri.toString()
            galleryDao.deleteAllAlbumMappingsForUri(uriStr)
            mappings.add(
                AlbumMediaMappingEntity(
                    albumName = cleanName,
                    uriString = uriStr,
                    mediaId = item.id,
                    addedAtMillis = now
                )
            )
            val existing = galleryDao.getAnnotation(uriStr)
            galleryDao.upsertAnnotation(
                MediaAnnotationEntity(
                    uriString = uriStr,
                    mediaId = item.id,
                    caption = existing?.caption ?: item.caption,
                    tagsCsv = existing?.tagsCsv ?: item.tags.joinToString(","),
                    customAlbumName = cleanName,
                    updatedAtMillis = now
                )
            )
        }
        galleryDao.insertAlbumMediaMappings(mappings)
    }

    /**
     * Adds (copies mapping of) the selected photos into [albumName] in Room while preserving existing custom albums.
     */
    suspend fun addItemsToCustomAlbum(items: List<MediaItem>, albumName: String) {
        val cleanName = albumName.trim()
        if (cleanName.isEmpty() || items.isEmpty()) return

        galleryDao.insertCustomAlbum(CustomAlbumEntity(name = cleanName))
        val now = System.currentTimeMillis()
        val mappings = items.map { item ->
            AlbumMediaMappingEntity(
                albumName = cleanName,
                uriString = item.uri.toString(),
                mediaId = item.id,
                addedAtMillis = now
            )
        }
        galleryDao.insertAlbumMediaMappings(mappings)

        items.forEach { item ->
            val uriStr = item.uri.toString()
            val existing = galleryDao.getAnnotation(uriStr)
            if (existing?.customAlbumName.isNullOrBlank()) {
                galleryDao.upsertAnnotation(
                    MediaAnnotationEntity(
                        uriString = uriStr,
                        mediaId = item.id,
                        caption = existing?.caption ?: item.caption,
                        tagsCsv = existing?.tagsCsv ?: item.tags.joinToString(","),
                        customAlbumName = cleanName,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    suspend fun removeItemsFromCustomAlbum(items: List<MediaItem>, albumName: String) {
        val cleanName = albumName.trim()
        if (cleanName.isEmpty() || items.isEmpty()) return
        for (item in items) {
            val uriStr = item.uri.toString()
            galleryDao.deleteMediaFromAlbum(cleanName, uriStr)
            val existing = galleryDao.getAnnotation(uriStr)
            if (existing != null && existing.customAlbumName.equals(cleanName, ignoreCase = true)) {
                galleryDao.upsertAnnotation(existing.copy(customAlbumName = null))
            }
        }
    }

    suspend fun deleteCustomAlbum(albumName: String) {
        val cleanName = albumName.trim()
        if (cleanName.isEmpty()) return
        galleryDao.deleteAllMappingsForAlbum(cleanName)
        galleryDao.deleteCustomAlbum(cleanName)
    }

    suspend fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val entities = mediaStoreDataSource.inspectPickedUris(uris)
        galleryDao.insertPickedMedia(entities)
    }

    suspend fun recordSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            galleryDao.insertRecentSearch(RecentSearchEntity(query = trimmed))
        }
    }

    suspend fun removeRecentSearch(query: String) {
        galleryDao.deleteRecentSearch(query)
    }

    suspend fun clearRecentSearches() {
        galleryDao.clearAllRecentSearches()
    }

    suspend fun extractExif(item: MediaItem): ExifTelemetry {
        return mediaStoreDataSource.extractExifTelemetry(item)
    }

    suspend fun deleteMediaItems(items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        val success = moveToRecycleBin(items)
        if (success) {
            refreshMediaStore()
        }
        return success
    }



    fun sortMedia(items: List<MediaItem>, sortOption: SortOption): List<MediaItem> {
        return when (sortOption) {
            SortOption.DATE_DESC -> items.sortedByDescending { it.dateTakenMillis }
            SortOption.DATE_ASC -> items.sortedBy { it.dateTakenMillis }
            SortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.US) }
            SortOption.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
        }
    }

    fun groupMediaByDay(items: List<MediaItem>): List<TimelineDayGroup> {
        if (items.isEmpty()) return emptyList()
        val nowCal = Calendar.getInstance()
        val todayYear = nowCal.get(Calendar.YEAR)
        val todayDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayYear = yesterdayCal.get(Calendar.YEAR)
        val yesterdayDayOfYear = yesterdayCal.get(Calendar.DAY_OF_YEAR)

        val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sameYearFormatter = SimpleDateFormat("EEEE, MMM d", Locale.US)
        val fullDateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.US)

        val grouped = items.groupBy { item ->
            keyFormatter.format(Date(item.dateTakenMillis))
        }

        return grouped.map { (key, dayItems) ->
            val first = dayItems.first()
            val itemCal = Calendar.getInstance().apply { timeInMillis = first.dateTakenMillis }
            val year = itemCal.get(Calendar.YEAR)
            val dayOfYear = itemCal.get(Calendar.DAY_OF_YEAR)

            val header = when {
                year == todayYear && dayOfYear == todayDayOfYear -> "Today"
                year == yesterdayYear && dayOfYear == yesterdayDayOfYear -> "Yesterday"
                year == todayYear -> sameYearFormatter.format(Date(first.dateTakenMillis))
                else -> fullDateFormatter.format(Date(first.dateTakenMillis))
            }

            val subtitle = dayItems
                .map { it.effectiveAlbumName }
                .firstOrNull { it.isNotBlank() } ?: "DSS Gallery"

            TimelineDayGroup(
                key = key,
                dateHeader = header,
                subtitle = subtitle,
                timestampMillis = first.dateTakenMillis,
                items = dayItems
            )
        }
    }

    fun buildMemories(items: List<MediaItem>): List<MemoryCollection> {
        if (items.isEmpty()) return emptyList()
        val memories = mutableListOf<MemoryCollection>()

        val favorites = items.filter { it.isFavorite }
        if (favorites.isNotEmpty()) {
            memories.add(
                MemoryCollection(
                    id = "mem_favorites",
                    badge = "Memory Reel",
                    title = "Best of Favorites",
                    subtitle = "${favorites.size} curated highlights",
                    coverItem = favorites.first(),
                    previewItems = favorites.take(4),
                    allItems = favorites
                )
            )
        }

        val byBucket = items.groupBy { it.effectiveAlbumName }
        byBucket.forEach { (bucket, bucketItems) ->
            if (bucketItems.size >= 2) {
                memories.add(
                    MemoryCollection(
                        id = "mem_bucket_${bucket.lowercase(Locale.US)}",
                        badge = "Spotlight",
                        title = bucket,
                        subtitle = "${bucketItems.size} moments",
                        coverItem = bucketItems.first(),
                        previewItems = bucketItems.take(4),
                        allItems = bucketItems
                    )
                )
            }
        }

        if (memories.size < 2 && items.isNotEmpty()) {
            memories.add(
                MemoryCollection(
                    id = "mem_recent",
                    badge = "Recent Story",
                    title = "Recent Captures",
                    subtitle = "${items.size} items in library",
                    coverItem = items.first(),
                    previewItems = items.take(4),
                    allItems = items.take(12)
                )
            )
        }

        return memories.distinctBy { it.title }.take(5)
    }

    fun buildAlbums(
        items: List<MediaItem>,
        customAlbums: List<CustomAlbumEntity>
    ): List<AlbumItem> {
        val albums = mutableListOf<AlbumItem>()

        // 1. User-created custom albums (backed by Room local metadata mappings)
        val allCustomAlbumNames = LinkedHashSet<String>().apply {
            customAlbums.forEach { add(it.name) }
            items.forEach { media ->
                media.customAlbums.forEach { add(it) }
            }
        }

        allCustomAlbumNames.forEach { albumName ->
            val customEntity = customAlbums.find { it.name.equals(albumName, ignoreCase = true) }
            val customItems = items.filter { media ->
                media.customAlbum.equals(albumName, ignoreCase = true) ||
                    media.customAlbums.any { it.equals(albumName, ignoreCase = true) }
            }
            albums.add(
                AlbumItem(
                    id = "custom_$albumName",
                    title = albumName,
                    subtitle = customEntity?.description?.takeIf { it.isNotBlank() } ?: "Custom Album",
                    type = SystemAlbumType.USER_ALBUM,
                    mediaCount = customItems.size,
                    videoCount = customItems.count { it.isVideo },
                    coverItem = customItems.firstOrNull(),
                    items = customItems,
                    isUserCreated = true
                )
            )
        }

        // 2. Camera
        val cameraItems = items.filter {
            it.bucketDisplayName.equals("Camera", ignoreCase = true) ||
                it.relativePath.contains("/DCIM/Camera", ignoreCase = true)
        }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.CAMERA.id,
                title = SystemAlbumType.CAMERA.title,
                subtitle = "DCIM / Camera",
                type = SystemAlbumType.CAMERA,
                mediaCount = cameraItems.size,
                videoCount = cameraItems.count { it.isVideo },
                coverItem = cameraItems.firstOrNull() ?: items.firstOrNull(),
                items = cameraItems
            )
        )

        // 3. Favorites
        val favItems = items.filter { it.isFavorite }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.FAVORITES.id,
                title = SystemAlbumType.FAVORITES.title,
                subtitle = "Curated Collection",
                type = SystemAlbumType.FAVORITES,
                mediaCount = favItems.size,
                videoCount = favItems.count { it.isVideo },
                coverItem = favItems.firstOrNull(),
                items = favItems
            )
        )

        // 4. Screenshots
        val screenshotItems = items.filter {
            it.bucketDisplayName.contains("Screenshot", ignoreCase = true) ||
                it.displayName.contains("Screenshot", ignoreCase = true) ||
                it.relativePath.contains("Screenshots", ignoreCase = true)
        }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.SCREENSHOTS.id,
                title = SystemAlbumType.SCREENSHOTS.title,
                subtitle = "Screen Captures",
                type = SystemAlbumType.SCREENSHOTS,
                mediaCount = screenshotItems.size,
                videoCount = screenshotItems.count { it.isVideo },
                coverItem = screenshotItems.firstOrNull(),
                items = screenshotItems
            )
        )

        // 5. Videos
        val videoItems = items.filter { it.isVideo }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.VIDEOS.id,
                title = SystemAlbumType.VIDEOS.title,
                subtitle = "Motion & Clips",
                type = SystemAlbumType.VIDEOS,
                mediaCount = videoItems.size,
                videoCount = videoItems.size,
                coverItem = videoItems.firstOrNull(),
                items = videoItems
            )
        )

        // 6. Downloads
        val downloadItems = items.filter {
            it.bucketDisplayName.contains("Download", ignoreCase = true) ||
                it.relativePath.contains("/Download", ignoreCase = true)
        }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.DOWNLOADS.id,
                title = SystemAlbumType.DOWNLOADS.title,
                subtitle = "Saved Files",
                type = SystemAlbumType.DOWNLOADS,
                mediaCount = downloadItems.size,
                videoCount = downloadItems.count { it.isVideo },
                coverItem = downloadItems.firstOrNull(),
                items = downloadItems
            )
        )

        // 7. Recently Added
        val fourteenDaysAgo = System.currentTimeMillis() - (14L * 86_400_000L)
        val recentItems = items.filter { it.dateAddedMillis >= fourteenDaysAgo }
            .ifEmpty { items.take(20) }
        albums.add(
            AlbumItem(
                id = SystemAlbumType.RECENTLY_ADDED.id,
                title = SystemAlbumType.RECENTLY_ADDED.title,
                subtitle = "Latest Additions",
                type = SystemAlbumType.RECENTLY_ADDED,
                mediaCount = recentItems.size,
                videoCount = recentItems.count { it.isVideo },
                coverItem = recentItems.firstOrNull(),
                items = recentItems
            )
        )

        // 8. Additional Device Folder Buckets discovered in MediaStore
        val standardNames = setOf("camera", "screenshots", "download", "downloads", "movies", "videos")
        items.groupBy { it.bucketDisplayName }
            .filterKeys {
                it.isNotBlank() &&
                    it.lowercase(Locale.US) !in standardNames &&
                    allCustomAlbumNames.none { custom -> custom.equals(it, ignoreCase = true) }
            }
            .forEach { (bucketName, bucketItems) ->
                albums.add(
                    AlbumItem(
                        id = "folder_$bucketName",
                        title = bucketName,
                        subtitle = "Device Folder",
                        type = SystemAlbumType.DEVICE_FOLDER,
                        mediaCount = bucketItems.size,
                        videoCount = bucketItems.count { it.isVideo },
                        coverItem = bucketItems.firstOrNull(),
                        items = bucketItems
                    )
                )
            }

        return albums
    }

    fun getRecycleBinFlow(): Flow<List<RecycleBinEntity>> = galleryDao.observeRecycleBin()

    suspend fun moveToRecycleBin(items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        return try {
            items.forEach { item ->
                galleryDao.insertRecycleBin(
                    RecycleBinEntity(
                        uriString = item.uri.toString(),
                        mediaId = item.id,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        isVideo = item.isVideo
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromRecycleBin(items: List<RecycleBinEntity>) {
        items.forEach { item ->
            galleryDao.deleteFromRecycleBin(item.uriString)
        }
        refreshMediaStore()
    }

    private fun RecycleBinEntity.toMediaItem(): MediaItem = MediaItem(
        id = mediaId,
        uri = Uri.parse(uriString),
        displayName = displayName,
        mimeType = mimeType,
        isVideo = isVideo,
        dateAddedMillis = deletedAtMillis,
        dateModifiedMillis = deletedAtMillis,
        dateTakenMillis = deletedAtMillis,
        sizeBytes = 0,
        width = 0,
        height = 0,
        durationMillis = 0,
        bucketId = "",
        bucketDisplayName = "",
        relativePath = ""
    )

    private suspend fun removeConfirmedDeletedFromDatabase(entities: List<RecycleBinEntity>) {
        entities.forEach { item ->
            galleryDao.deleteFromRecycleBin(item.uriString)
            galleryDao.deleteFavorite(item.uriString)
            galleryDao.deleteAllAlbumMappingsForUri(item.uriString)
            galleryDao.deletePickedMedia(item.uriString)
        }
    }

    /**
     * Permanently deletes the specified items from device storage and the Recycle Bin.
     * - Only removes Recycle Bin database records AFTER confirming that the underlying media files were deleted.
     * - If Android requires user consent, stores the pending items and returns the system [IntentSender].
     */
    suspend fun permanentlyDeleteFromRecycleBin(items: List<RecycleBinEntity>): IntentSender? {
        if (items.isEmpty()) return null
        return deleteMutex.withLock {
            val existingSender = pendingDeleteIntentSenderFlow.value
            if (existingSender != null && pendingDeleteItemsFlow.value.isNotEmpty()) {
                return@withLock existingSender
            }

            val distinctItems = items.distinctBy { it.uriString }
            val mediaItems = distinctItems.map { it.toMediaItem() }
            val result = mediaStoreDataSource.deleteMediaItemsWithResult(mediaItems)

            if (result.deletedUris.isNotEmpty()) {
                val confirmedEntities = distinctItems.filter { it.uriString in result.deletedUris }
                removeConfirmedDeletedFromDatabase(confirmedEntities)
            }

            val pendingUriStrings = result.pendingConsentUris.map { it.toString() }.toSet()
            val pendingEntities = if (result.intentSender != null && pendingUriStrings.isNotEmpty()) {
                distinctItems.filter { it.uriString in pendingUriStrings }
            } else {
                emptyList()
            }

            if (result.intentSender != null && pendingEntities.isNotEmpty()) {
                isConsentDialogLaunched.set(false)
                pendingDeleteItemsFlow.value = pendingEntities
                pendingDeleteIntentSenderFlow.value = result.intentSender
            } else {
                isConsentDialogLaunched.set(false)
                pendingDeleteItemsFlow.value = emptyList()
                pendingDeleteIntentSenderFlow.value = null

                val deletedCount = result.deletedUris.size
                val failedCount = distinctItems.size - deletedCount
                deleteStatusMessageFlow.value = when {
                    deletedCount > 0 && failedCount == 0 ->
                        "Permanently deleted successfully"
                    else ->
                        "Failed to permanently delete"
                }
            }

            if (result.deletedUris.isNotEmpty()) {
                refreshMediaStore()
            }

            result.intentSender
        }
    }

    fun markConsentDialogLaunched(sender: IntentSender): Boolean {
        return pendingDeleteIntentSenderFlow.value === sender &&
            isConsentDialogLaunched.compareAndSet(false, true)
    }

    suspend fun requiresSystemConsent(items: List<RecycleBinEntity>): Boolean {
        if (items.isEmpty()) return false
        val mediaItems = items.map { it.toMediaItem() }
        return mediaStoreDataSource.requiresSystemConsent(mediaItems)
    }

    /**
     * Handles the result of the system delete-consent dialog.
     * - If approved, verifies actual deletion and removes only confirmed-deleted items from the Recycle Bin.
     * - If cancelled or failed, retains items in the Recycle Bin so the user can retry.
     */
    suspend fun handleDeleteConsentResult(approved: Boolean): Boolean {
        return deleteMutex.withLock {
            val pendingEntities = pendingDeleteItemsFlow.value
            pendingDeleteItemsFlow.value = emptyList()
            pendingDeleteIntentSenderFlow.value = null
            isConsentDialogLaunched.set(false)

            if (pendingEntities.isEmpty()) {
                if (approved) {
                    refreshMediaStore()
                }
                return@withLock approved
            }

            if (!approved) {
                deleteStatusMessageFlow.value = "Failed to permanently delete"
                return@withLock false
            }

            val mediaItems = pendingEntities.map { it.toMediaItem() }
            val finalizeResult = mediaStoreDataSource.finalizePendingDelete(mediaItems)

            if (finalizeResult.deletedUris.isNotEmpty()) {
                val confirmedEntities = pendingEntities.filter { it.uriString in finalizeResult.deletedUris }
                removeConfirmedDeletedFromDatabase(confirmedEntities)
            }

            val stillPendingUriStrings = finalizeResult.pendingConsentUris.map { it.toString() }.toSet()
            val stillPendingEntities = if (finalizeResult.intentSender != null && stillPendingUriStrings.isNotEmpty()) {
                pendingEntities.filter { it.uriString in stillPendingUriStrings }
            } else {
                emptyList()
            }

            if (finalizeResult.intentSender != null && stillPendingEntities.isNotEmpty()) {
                isConsentDialogLaunched.set(false)
                pendingDeleteItemsFlow.value = stillPendingEntities
                pendingDeleteIntentSenderFlow.value = finalizeResult.intentSender
                if (finalizeResult.deletedUris.isNotEmpty()) {
                    refreshMediaStore()
                }
                return@withLock finalizeResult.deletedUris.isNotEmpty()
            }

            refreshMediaStore()

            val deletedCount = finalizeResult.deletedUris.size
            val failedCount = pendingEntities.size - deletedCount
            deleteStatusMessageFlow.value = when {
                deletedCount > 0 && failedCount == 0 ->
                    "Permanently deleted successfully"
                else ->
                    "Failed to permanently delete"
            }

            deletedCount > 0 && failedCount == 0
        }
    }

    fun setDeleteStatusMessage(message: String) {
        deleteStatusMessageFlow.value = message
    }

    fun clearDeleteStatusMessage() {
        deleteStatusMessageFlow.value = null
    }

    suspend fun renameMedia(item: MediaItem, newName: String) {
        mediaStoreDataSource.renameMedia(item.uri, newName)
    }
}
