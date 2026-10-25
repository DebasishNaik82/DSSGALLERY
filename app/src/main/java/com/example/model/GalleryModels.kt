package com.example.model

import android.net.Uri
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAddedMillis: Long,
    val dateModifiedMillis: Long,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long = 0L,
    val bucketId: String = "",
    val bucketDisplayName: String = "Camera",
    val relativePath: String = "/Internal Storage/DCIM/Camera/",
    val isFavorite: Boolean = false,
    val caption: String = "",
    val tags: List<String> = emptyList(),
    val customAlbum: String? = null,
    val customAlbums: List<String> = emptyList()
) {
    val effectiveAlbumName: String
        get() = customAlbum?.takeIf { it.isNotBlank() }
            ?: customAlbums.firstOrNull()
            ?: bucketDisplayName

    val aspectRatio: Float
        get() = if (height > 0 && width > 0) width.toFloat() / height.toFloat() else 1f

    val megapixelsFormatted: String
        get() {
            if (width <= 0 || height <= 0) return "—"
            val mp = (width.toLong() * height.toLong()) / 1_000_000.0
            return if (mp >= 1.0) String.format(Locale.US, "%.1f MP", mp) else "< 1 MP"
        }

    val resolutionFormatted: String
        get() = if (width > 0 && height > 0) "${width} × ${height} px" else "Unknown resolution"

    val fileSizeFormatted: String
        get() {
            if (sizeBytes <= 0) return "Unknown"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.0f KB", kb)
            }
        }

    val durationFormatted: String
        get() {
            val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
}

data class ExifTelemetry(
    val cameraMake: String = "Unknown",
    val cameraModel: String = "Unknown",
    val lensModel: String = "Unknown",
    val aperture: String = "f/0.0",
    val shutterSpeed: String = "0s",
    val iso: String = "ISO 0",
    val focalLength: String = "0mm",
    val focalLength35mm: String = "0mm eq.",
    val exposureBias: String = "0.0 EV",
    val flashState: String = "Unknown",
    val whiteBalance: String = "Unknown",
    val meteringMode: String = "Unknown",
    val exposureProgram: String = "Unknown",
    val orientation: String = "Unknown",
    val software: String = "Unknown",
    val dateTimeOriginal: String = "",
    val colorSpace: String = "Display P3 / sRGB",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val locationName: String = "Local Device Storage",
    val formatBadge: String = "JPEG",
    val filmSimulation: String = "None",
    val histogramBins: List<Float> = listOf(
        0.08f, 0.12f, 0.18f, 0.25f, 0.34f, 0.42f, 0.51f, 0.64f,
        0.78f, 0.85f, 0.92f, 0.88f, 0.76f, 0.68f, 0.74f, 0.82f,
        0.95f, 1.00f, 0.89f, 0.77f, 0.62f, 0.54f, 0.48f, 0.41f,
        0.35f, 0.29f, 0.22f, 0.16f, 0.12f, 0.09f, 0.06f, 0.04f
    )
) {
    val summaryStrip: String
        get() = "$cameraMake $cameraModel  •  $focalLength  •  $aperture  •  $shutterSpeed  •  $iso"
}

enum class SystemAlbumType(val id: String, val title: String) {
    CAMERA("system_camera", "Camera"),
    SCREENSHOTS("system_screenshots", "Screenshots"),
    DOWNLOADS("system_downloads", "Downloads"),
    VIDEOS("system_videos", "Videos"),
    FAVORITES("system_favorites", "Favorites"),
    RECENTLY_ADDED("system_recent", "Recently Added"),
    USER_ALBUM("user_album", "Custom Album"),
    DEVICE_FOLDER("device_folder", "Device Folder")
}

data class AlbumItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: SystemAlbumType,
    val mediaCount: Int,
    val videoCount: Int = 0,
    val coverItem: MediaItem?,
    val items: List<MediaItem> = emptyList(),
    val isUserCreated: Boolean = false
)

data class TimelineDayGroup(
    val key: String,
    val dateHeader: String,
    val subtitle: String,
    val timestampMillis: Long,
    val items: List<MediaItem>
)

data class MemoryCollection(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val coverItem: MediaItem,
    val previewItems: List<MediaItem>,
    val allItems: List<MediaItem>
)

enum class ThemeOption(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Warm Porcelain (Light)"),
    DARK("Obsidian Coral (Dark)")
}

enum class GridDensity(val columns: Int, val label: String) {
    COMFORTABLE_2(2, "Comfortable (2 Columns)"),
    STANDARD_3(3, "Standard (3 Columns)"),
    COMPACT_4(4, "Compact (4 Columns)")
}

enum class SortOption(val label: String) {
    DATE_DESC("Date Taken (Newest First)"),
    DATE_ASC("Date Taken (Oldest First)"),
    NAME_ASC("Filename (A to Z)"),
    SIZE_DESC("File Size (Largest First)")
}

enum class MediaFilterType(val label: String) {
    ALL("All Media"),
    PHOTOS_ONLY("Photos"),
    VIDEOS_ONLY("Videos")
}

enum class DateRangeFilter(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_YEAR("This Year")
}

data class GalleryPreferences(
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val gridDensity: GridDensity = GridDensity.STANDARD_3,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val showVideosInTimeline: Boolean = true,
    val showVideoDurationBadges: Boolean = true,
    val showMemoriesSection: Boolean = true,
    val showPermissionDialog: Boolean = true
)

enum class PermissionAccessLevel {
    FULL_ACCESS,
    PARTIAL_ACCESS,
    DENIED
}
