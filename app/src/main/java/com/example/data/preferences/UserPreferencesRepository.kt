package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GalleryPreferences
import com.example.model.GridDensity
import com.example.model.SortOption
import com.example.model.ThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_OPTION = stringPreferencesKey("theme_option")
        val GRID_DENSITY = stringPreferencesKey("grid_density")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SHOW_VIDEOS = booleanPreferencesKey("show_videos_in_timeline")
        val SHOW_VIDEO_DURATION = booleanPreferencesKey("show_video_duration_badges")
        val SHOW_MEMORIES = booleanPreferencesKey("show_memories_section")
        val SHOW_PERMISSION_DIALOG = booleanPreferencesKey("show_permission_dialog")
    }

    val preferencesFlow: Flow<GalleryPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_OPTION]?.let {
            runCatching { ThemeOption.valueOf(it) }.getOrNull()
        } ?: ThemeOption.LIGHT

        val density = prefs[Keys.GRID_DENSITY]?.let {
            runCatching { GridDensity.valueOf(it) }.getOrNull()
        } ?: GridDensity.STANDARD_3

        val sort = prefs[Keys.SORT_OPTION]?.let {
            runCatching { SortOption.valueOf(it) }.getOrNull()
        } ?: SortOption.DATE_DESC

        val showVideos = prefs[Keys.SHOW_VIDEOS] ?: true
        val showDuration = prefs[Keys.SHOW_VIDEO_DURATION] ?: true
        val showMemories = prefs[Keys.SHOW_MEMORIES] ?: true
        val showPermissionDialog = prefs[Keys.SHOW_PERMISSION_DIALOG] ?: true

        GalleryPreferences(
            themeOption = theme,
            gridDensity = density,
            sortOption = sort,
            showVideosInTimeline = showVideos,
            showVideoDurationBadges = showDuration,
            showMemoriesSection = showMemories,
            showPermissionDialog = showPermissionDialog
        )
    }

    suspend fun setShowPermissionDialog(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PERMISSION_DIALOG] = show }
    }

    suspend fun setThemeOption(option: ThemeOption) {
        context.dataStore.edit { it[Keys.THEME_OPTION] = option.name }
    }

    suspend fun setGridDensity(density: GridDensity) {
        context.dataStore.edit { it[Keys.GRID_DENSITY] = density.name }
    }

    suspend fun setSortOption(sort: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = sort.name }
    }

    suspend fun setShowVideosInTimeline(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_VIDEOS] = show }
    }

    suspend fun setShowVideoDurationBadges(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_VIDEO_DURATION] = show }
    }

    suspend fun setShowMemoriesSection(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_MEMORIES] = show }
    }
}
