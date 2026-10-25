package com.example.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GalleryRepository
import com.example.data.local.GalleryDatabase
import com.example.data.mediastore.MediaStoreDataSource
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.AlbumItem
import com.example.model.DateRangeFilter
import com.example.model.ExifTelemetry
import com.example.model.GalleryPreferences
import com.example.model.GridDensity
import com.example.model.MediaFilterType
import com.example.model.MediaItem
import com.example.model.MemoryCollection
import com.example.model.PermissionAccessLevel
import com.example.model.SortOption
import com.example.model.ThemeOption
import com.example.model.TimelineDayGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class SearchFilterState(
    val query: String = "",
    val mediaFilter: MediaFilterType = MediaFilterType.ALL,
    val dateFilter: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val albumFilter: String? = null
)

data class FavoritesFilterState(
    val mediaFilter: MediaFilterType = MediaFilterType.ALL,
    val dateFilter: DateRangeFilter = DateRangeFilter.ALL_TIME
)

data class SelectionState(
    val isSelectionMode: Boolean = false,
    val selectedUris: Set<String> = emptySet()
)

data class GalleryUiState(
    val isLoading: Boolean = true,
    val permissionAccessLevel: PermissionAccessLevel = PermissionAccessLevel.DENIED,
    val preferences: GalleryPreferences = GalleryPreferences(),
    val allMedia: List<MediaItem> = emptyList(),
    val timelineGroups: List<TimelineDayGroup> = emptyList(),
    val memories: List<MemoryCollection> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val filteredFavorites: List<MediaItem> = emptyList(),
    val favoriteGroups: List<TimelineDayGroup> = emptyList(),
    val favoritesFilter: FavoritesFilterState = FavoritesFilterState(),
    val selection: SelectionState = SelectionState(),
    val searchFilter: SearchFilterState = SearchFilterState(),
    val searchResults: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val activeExifTelemetry: ExifTelemetry? = null,
    val statusMessage: String? = null,
    val pendingDeleteIntentSender: IntentSender? = null
)

class GalleryViewModel(
    application: Application,
    private val repository: GalleryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val permissionFlow = MutableStateFlow(repository.getPermissionAccessLevel())
    private val selectionFlow = MutableStateFlow(SelectionState())
    private val searchFilterFlow = MutableStateFlow(SearchFilterState())
    private val favoritesFilterFlow = MutableStateFlow(FavoritesFilterState())
    private val activeExifFlow = MutableStateFlow<ExifTelemetry?>(null)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val pendingDeleteFlow = MutableStateFlow<IntentSender?>(null)
    private var hasCheckedInitialSeed = false

    val requiredPermissions: Array<String>
        get() = repository.getRequiredPermissions()

    val galleryRepository: GalleryRepository
        get() = repository

    private val coreDataFlow = combine(
        repository.allMediaFlow,
        repository.customAlbumsFlow,
        preferencesRepository.preferencesFlow,
        repository.isLoading,
        permissionFlow
    ) { allMedia, customAlbums, prefs, isLoading, perm ->
        CoreDataBundle(allMedia, customAlbums, prefs, isLoading, perm)
    }

    private val uiControlsFlow = combine(
        selectionFlow,
        searchFilterFlow,
        favoritesFilterFlow,
        repository.recentSearchesFlow,
        activeExifFlow
    ) { selection, searchFilter, favFilter, recentSearches, exif ->
        UiControlsBundle(selection, searchFilter, favFilter, recentSearches, exif)
    }

    private val combinedStatusMessageFlow = combine(
        statusMessageFlow,
        repository.deleteStatusMessage
    ) { localMsg, repoDeleteMsg ->
        repoDeleteMsg ?: localMsg
    }

    private val combinedPendingDeleteFlow = combine(
        pendingDeleteFlow,
        repository.pendingDeleteIntentSender
    ) { localSender, repoSender ->
        repoSender ?: localSender
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        coreDataFlow,
        uiControlsFlow,
        combinedStatusMessageFlow,
        combinedPendingDeleteFlow
    ) { core, controls, statusMsg, pendingDelete ->
        val visibleMedia = if (core.prefs.showVideosInTimeline) {
            core.allMedia
        } else {
            core.allMedia.filter { !it.isVideo }
        }

        val sortedMedia = repository.sortMedia(visibleMedia, core.prefs.sortOption)
        val timelineGroups = repository.groupMediaByDay(sortedMedia)
        val memories = repository.buildMemories(sortedMedia)
        val albums = repository.buildAlbums(core.allMedia, core.customAlbums)

        val favorites = sortedMedia.filter { it.isFavorite }
        val filteredFavs = filterMediaList(
            items = favorites,
            query = "",
            mediaFilter = controls.favFilter.mediaFilter,
            dateFilter = controls.favFilter.dateFilter,
            albumFilter = null
        )
        val favGroups = repository.groupMediaByDay(filteredFavs)

        val searchResults = filterMediaList(
            items = sortedMedia,
            query = controls.searchFilter.query,
            mediaFilter = controls.searchFilter.mediaFilter,
            dateFilter = controls.searchFilter.dateFilter,
            albumFilter = controls.searchFilter.albumFilter
        )

        GalleryUiState(
            isLoading = core.isLoading,
            permissionAccessLevel = core.perm,
            preferences = core.prefs,
            allMedia = sortedMedia,
            timelineGroups = timelineGroups,
            memories = memories,
            albums = albums,
            favorites = favorites,
            filteredFavorites = filteredFavs,
            favoriteGroups = favGroups,
            favoritesFilter = controls.favFilter,
            selection = controls.selection,
            searchFilter = controls.searchFilter,
            searchResults = searchResults,
            recentSearches = controls.recentSearches,
            activeExifTelemetry = controls.exif,
            statusMessage = statusMsg,
            pendingDeleteIntentSender = pendingDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    init {
        viewModelScope.launch {
            permissionFlow.value = repository.getPermissionAccessLevel()
            repository.refreshMediaStore()
        }
    }

    fun ensureInitialLibraryPopulatedIfEmpty() {
        viewModelScope.launch {
            repository.refreshMediaStore()
        }
    }

    fun updatePermissionStatus() {
        viewModelScope.launch {
            permissionFlow.value = repository.getPermissionAccessLevel()
            repository.refreshMediaStore()
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            permissionFlow.value = repository.getPermissionAccessLevel()
            repository.refreshMediaStore()
        }
    }



    fun toggleSelection(uriString: String) {
        selectionFlow.update { current ->
            val updated = current.selectedUris.toMutableSet()
            if (updated.contains(uriString)) {
                updated.remove(uriString)
            } else {
                updated.add(uriString)
            }
            SelectionState(
                isSelectionMode = updated.isNotEmpty(),
                selectedUris = updated
            )
        }
    }

    fun enterSelectionMode(initialUri: String? = null) {
        selectionFlow.update { current ->
            val set = if (initialUri != null) setOf(initialUri) else current.selectedUris
            SelectionState(isSelectionMode = true, selectedUris = set)
        }
    }

    fun clearSelection() {
        selectionFlow.value = SelectionState(isSelectionMode = false, selectedUris = emptySet())
    }

    fun selectAll(items: List<MediaItem>) {
        selectionFlow.value = SelectionState(
            isSelectionMode = items.isNotEmpty(),
            selectedUris = items.map { it.uri.toString() }.toSet()
        )
    }

    fun selectItems(items: List<MediaItem>, select: Boolean) {
        selectionFlow.update { current ->
            val updated = current.selectedUris.toMutableSet()
            items.forEach { item ->
                val uriStr = item.uri.toString()
                if (select) updated.add(uriStr) else updated.remove(uriStr)
            }
            SelectionState(
                isSelectionMode = updated.isNotEmpty(),
                selectedUris = updated
            )
        }
    }

    fun getSelectedMediaItems(): List<MediaItem> {
        val selectedSet = selectionFlow.value.selectedUris
        return uiState.value.allMedia.filter { selectedSet.contains(it.uri.toString()) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
            val msg = if (item.isFavorite) "Removed from Favorites" else "Added to Favorites"
            showStatusMessage(msg)
        }
    }

    fun favoriteSelectedItems(favorite: Boolean = true) {
        val selected = getSelectedMediaItems()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.setFavoriteForItems(selected, favorite)
            clearSelection()
            showStatusMessage(
                if (favorite) "Added ${selected.size} items to Favorites"
                else "Removed ${selected.size} items from Favorites"
            )
        }
    }

    fun deleteItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                val success = repository.deleteMediaItems(items)
                if (success) {
                    clearSelection()
                    showStatusMessage("Moved to Bin successfully")
                } else {
                    showStatusMessage("Failed to move to Bin")
                }
            } catch (_: Exception) {
                showStatusMessage("Failed to move to Bin")
            }
        }
    }

    fun deleteSelectedItems() {
        val selected = getSelectedMediaItems()
        deleteItems(selected)
    }

    fun renameMedia(item: MediaItem, newName: String) {
        viewModelScope.launch {
            repository.renameMedia(item, newName)
            refreshLibrary()
        }
    }

    fun markConsentDialogLaunched(sender: IntentSender): Boolean {
        return repository.markConsentDialogLaunched(sender)
    }

    fun clearPendingDeleteRequest(completed: Boolean) {
        pendingDeleteFlow.value = null
        viewModelScope.launch {
            val deleted = repository.handleDeleteConsentResult(completed)
            if (deleted) {
                clearSelection()
            }
        }
    }

    fun loadExifFor(item: MediaItem) {
        viewModelScope.launch {
            activeExifFlow.value = repository.extractExif(item)
        }
    }

    fun savePhotoDetails(
        item: MediaItem,
        caption: String,
        tags: List<String>,
        customAlbum: String? = item.customAlbum
    ) {
        viewModelScope.launch {
            repository.saveCaptionAndTags(item, caption, tags, customAlbum)
            showStatusMessage("Saved notes & tags to device")
        }
    }

    fun createCustomAlbum(
        name: String,
        description: String = "Personal Collection",
        assignSelected: Boolean = false,
        moveItems: Boolean = true
    ) {
        viewModelScope.launch {
            val selected = if (assignSelected) getSelectedMediaItems() else emptyList()
            repository.createCustomAlbum(
                name = name,
                description = description,
                initialItems = selected,
                moveItems = moveItems
            )
            if (assignSelected) clearSelection()
            val suffix = if (selected.isNotEmpty()) {
                " and moved ${selected.size} photo(s) into it"
            } else ""
            showStatusMessage("Created custom album \"$name\"$suffix")
        }
    }

    fun assignItemsToAlbum(
        items: List<MediaItem>,
        albumName: String,
        moveItems: Boolean = true
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (moveItems) {
                repository.moveItemsToCustomAlbum(items, albumName)
                clearSelection()
                showStatusMessage("Moved ${items.size} photo(s) to \"$albumName\"")
            } else {
                repository.addItemsToCustomAlbum(items, albumName)
                clearSelection()
                showStatusMessage("Added ${items.size} photo(s) to \"$albumName\"")
            }
        }
    }

    fun removeItemsFromCustomAlbum(items: List<MediaItem>, albumName: String) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.removeItemsFromCustomAlbum(items, albumName)
            clearSelection()
            showStatusMessage("Removed ${items.size} photo(s) from \"$albumName\"")
        }
    }

    fun deleteCustomAlbum(albumName: String) {
        viewModelScope.launch {
            repository.deleteCustomAlbum(albumName)
            showStatusMessage("Deleted custom album \"$albumName\"")
        }
    }

    fun onPickedMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            repository.addPickedUris(uris)
            showStatusMessage("Imported ${uris.size} photo(s) from Android Photo Picker")
        }
    }



    // Search & Explore controls
    fun setSearchQuery(query: String) {
        searchFilterFlow.update { it.copy(query = query) }
    }

    fun submitSearchQuery(query: String) {
        searchFilterFlow.update { it.copy(query = query) }
        viewModelScope.launch {
            repository.recordSearchQuery(query)
        }
    }

    fun setSearchMediaFilter(filter: MediaFilterType) {
        searchFilterFlow.update { it.copy(mediaFilter = filter) }
    }

    fun setSearchDateFilter(filter: DateRangeFilter) {
        searchFilterFlow.update { it.copy(dateFilter = filter) }
    }

    fun setSearchAlbumFilter(albumTitle: String?) {
        searchFilterFlow.update { it.copy(albumFilter = albumTitle) }
    }

    fun clearSearchFilters() {
        searchFilterFlow.value = SearchFilterState()
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            repository.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    // Favorites filters
    fun setFavoritesMediaFilter(filter: MediaFilterType) {
        favoritesFilterFlow.update { it.copy(mediaFilter = filter) }
    }

    fun setFavoritesDateFilter(filter: DateRangeFilter) {
        favoritesFilterFlow.update { it.copy(dateFilter = filter) }
    }

    // Preferences
    fun setThemeOption(option: ThemeOption) {
        viewModelScope.launch {
            preferencesRepository.setThemeOption(option)
        }
    }

    fun setGridDensity(density: GridDensity) {
        viewModelScope.launch {
            preferencesRepository.setGridDensity(density)
        }
    }

    fun setSortOption(sort: SortOption) {
        viewModelScope.launch {
            preferencesRepository.setSortOption(sort)
        }
    }

    fun setShowVideosInTimeline(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowVideosInTimeline(show)
        }
    }

    fun setShowVideoDurationBadges(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowVideoDurationBadges(show)
        }
    }

    fun setShowMemoriesSection(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowMemoriesSection(show)
        }
    }

    fun setShowPermissionDialog(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowPermissionDialog(show)
        }
    }

    fun showStatusMessage(message: String) {
        statusMessageFlow.value = message
    }

    fun dismissStatusMessage() {
        statusMessageFlow.value = null
        repository.clearDeleteStatusMessage()
    }

    private fun filterMediaList(
        items: List<MediaItem>,
        query: String,
        mediaFilter: MediaFilterType,
        dateFilter: DateRangeFilter,
        albumFilter: String?
    ): List<MediaItem> {
        val cleanQuery = query.trim().lowercase(Locale.US)
        val now = System.currentTimeMillis()
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        return items.filter { item ->
            val matchesQuery = if (cleanQuery.isEmpty()) {
                true
            } else {
                item.displayName.lowercase(Locale.US).contains(cleanQuery) ||
                    item.bucketDisplayName.lowercase(Locale.US).contains(cleanQuery) ||
                    item.caption.lowercase(Locale.US).contains(cleanQuery) ||
                    item.tags.any { it.lowercase(Locale.US).contains(cleanQuery) } ||
                    (item.customAlbum?.lowercase(Locale.US)?.contains(cleanQuery) == true)
            }

            val matchesType = when (mediaFilter) {
                MediaFilterType.ALL -> true
                MediaFilterType.PHOTOS_ONLY -> !item.isVideo
                MediaFilterType.VIDEOS_ONLY -> item.isVideo
            }

            val matchesDate = when (dateFilter) {
                DateRangeFilter.ALL_TIME -> true
                DateRangeFilter.TODAY -> (now - item.dateTakenMillis) <= 86_400_000L
                DateRangeFilter.LAST_7_DAYS -> (now - item.dateTakenMillis) <= 7L * 86_400_000L
                DateRangeFilter.LAST_30_DAYS -> (now - item.dateTakenMillis) <= 30L * 86_400_000L
                DateRangeFilter.THIS_YEAR -> item.dateTakenMillis >= startOfYear
            }

            val matchesAlbum = if (albumFilter.isNullOrBlank()) {
                true
            } else {
                item.bucketDisplayName.equals(albumFilter, ignoreCase = true) ||
                    item.customAlbum.equals(albumFilter, ignoreCase = true)
            }

            matchesQuery && matchesType && matchesDate && matchesAlbum
        }
    }

    private data class CoreDataBundle(
        val allMedia: List<MediaItem>,
        val customAlbums: List<com.example.data.local.CustomAlbumEntity>,
        val prefs: GalleryPreferences,
        val isLoading: Boolean,
        val perm: PermissionAccessLevel
    )

    private data class UiControlsBundle(
        val selection: SelectionState,
        val searchFilter: SearchFilterState,
        val favFilter: FavoritesFilterState,
        val recentSearches: List<String>,
        val exif: ExifTelemetry?
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = GalleryDatabase.getInstance(application)
            val mediaStoreDataSource = MediaStoreDataSource(application)
            val prefsRepo = UserPreferencesRepository(application)
            val repo = GalleryRepository(
                context = application,
                mediaStoreDataSource = mediaStoreDataSource,
                galleryDao = db.galleryDao(),
                externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            )
            return GalleryViewModel(application, repo, prefsRepo) as T
        }
    }
}
