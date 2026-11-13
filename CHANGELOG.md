# Changelog

All notable changes to the **DSS Gallery** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.1] - 2026-11-13

### Fixed
- **Bottom Navigation Bar Button Synchronization (`GalleryApp`, `PorcelainFloatingBottomBar`):**
  - Fixed a `LaunchedEffect` state-sync race condition where observing `pagerState.currentPage` with `currentDestination` as a restart key immediately reverted `currentDestination` back to the prior tab before `animateScrollToPage` started, preventing navigation bar taps, Explore shortcuts, and tab back-navigation from switching screens.
  - Synchronized tab transitions cleanly using `settledPage` and `pendingTabNavigation` tracking so rapid repeated taps, manual pager swipes, and system back navigation stay in sync with the active tab highlight.
  - Enforced minimum `48.dp` interactive touch height and `Role.Tab` selection semantics on all navigation bar items (`Photos`, `Albums`, `Favorites`, `Explore`).
- **Photo & Video Renaming Crash Prevention (`MediaStoreDataSource`, `GalleryRepository`, `RenameDialog`):**
  - Replaced unguarded `contentResolver.update` calls with version-aware safe renaming (`renameMediaSafely`) handling Android 11+ `MediaStore.createWriteRequest` user consent, Android 10 `RecoverableSecurityException` recovery, Android <= 9 `File.renameTo` with rollback on `MediaStore` failure, `file://` URIs, SAF `DocumentsContract`, and explicit read-only handling for Photo Picker URIs.
  - Added filename validation (rejecting empty names, `.` / `..`, and illegal filesystem characters `\ / : * ? " < > |`), automatic extension preservation, and same-directory duplicate checks.
  - Synchronized Room metadata (`MediaEntity`, `PickedMediaEntity`, `RecycleBinEntity`, favorites, annotations, and album mappings) immediately after successful renames.
- **Full-Screen Viewer Left/Right Swipe Navigation (`ZoomableMediaPage`, `PhotoViewerScreen`, `ExoVideoPlayer`):**
  - Replaced `detectTransformGestures` with a custom `awaitEachGesture` handler that leaves unzoomed single-finger drags unconsumed so `HorizontalPager` swipes left (next) and right (previous) reliably across photos, SVGs, and videos while preserving pinch-to-zoom, zoomed panning, double-tap zoom lock, and single-tap chrome toggling.
  - Guarded `SvgImage` against zero-dimension SVG bounds and keyed `ExoVideoPlayer` instances by `uri` with lifecycle/page-based pause and controller visibility sync.

### Added
- **Local Room Media Cache (`MediaEntity`, `MediaDao`, `GalleryDatabase` v4):**
  - Added `MediaEntity` (`media_items` table with indexes on `mediaId`, `mediaType`, `albumName`, `displayName`, `dateTakenMillis`, and `dateModifiedMillis`) storing URIs, filenames, paths, `MediaType` (`IMAGE`/`VIDEO`), sizes, MIME types, timestamps, album buckets, dimensions, and video durations.
  - Added `MediaDao` with single/batch inserts, updates, rename updates, chunked deletions, reactive `Flow` queries (all media, images, videos, by album, filename search), and deduplicating transactional sync (`syncMediaItems`), wired into `GalleryRepository` with `350ms` debounced `MediaStore` change observation.
- **Automated Robolectric & Compose UI Test Coverage (`GalleryFeaturesRobolectricTest`):**
  - Added tests covering `MediaDao` CRUD/filter/search/sync, filename validation, `file://` photo/video/duplicate/missing/picker renames, `RenameDialog` validation UI, mixed photo/video viewer swipe boundaries and zoom lock, and bottom navigation bar tab switching.

### Changed
- **ProGuard / R8 Configuration & Build Setup (`app/proguard-rules.pro`, `app/build.gradle.kts`):**
  - Added targeted `proguard-rules.pro` keep rules for Room (`RoomDatabase`, `*Entity`, `*_Impl`, `MediaType`), Kotlin metadata/signatures, Coil decoders and service loaders (`SvgDecoder`, `VideoFrameDecoder`, `GifDecoder`, `ImageDecoderDecoder`), and `AndroidSVG`, and enabled `proguard-rules.pro` in both `release` and `debug` build types.

---

## [2.0.0]

### Added
- SVG Viewer support using AndroidSVG.
- Video Gallery integration with AndroidX Media3 ExoPlayer.
- Integrated Video Player with play/pause, seekbar, and volume/brightness gestures.
- Automatic video thumbnails in the gallery.
- Android "Open With" integration (ACTION_VIEW, ACTION_SEND, ACTION_SEND_MULTIPLE).
- Registration as a system media handler for image/*, video/*, and image/svg+xml.
- Support for additional media formats: HEIC, MKV, MOV, AVI, WEBM.
- Added the option to rename a selected photo from the More menu.

### Improved
- Improved photo navigation using left and right swipes in both grid and full-screen view.
- Improved video thumbnail display for better visibility in the gallery.

### Fixed
- Fixed issues with large image loading and potential memory leaks.
- Resolved permission handling bugs for Android 11+ scoped storage.
- Fixed the issue preventing swipe navigation from working.
- Fixed the issue where video thumbnails were not appearing correctly.

## [v1.0]

Initial production release of **DSS Gallery** — a privacy-first, offline Android photo and video gallery application built with Kotlin, Jetpack Compose, Material Design 3, Android MediaStore, Room, and DataStore.

### Added
- **Custom Theme-Matched Confirmation Dialogs:**
  - Integrated `MoveToBinConfirmationDialog` replacing default system prompts when moving photos/videos to the Recycle Bin.
  - Integrated `PermanentlyDeleteConfirmationDialog` with prominent destructive warnings and safety locks when permanently deleting items or emptying the Recycle Bin.
  - Utilized DSS Gallery Material 3 dynamic color scheme, typography, `RoundedCornerShape(28.dp)`, spacing, and button styles.
  - Provided clearly labeled Cancel and Confirm buttons with multi-click prevention (`isProcessing`) to guard against duplicate operations.
- **Success and Error Toast Notifications:**
  - Implemented custom theme-matched `GalleryToastNotification` floating pill component with contextual icons, elevations, and dynamic light/dark theme styling.
  - Standardized feedback notifications for Move to Bin:
    - Success: *"Moved to Bin successfully"*
    - Failure: *"Failed to move to Bin"*
  - Standardized feedback notifications for Permanently Delete:
    - Success: *"Permanently deleted successfully"*
    - Failure: *"Failed to permanently delete"*
- **Photos Timeline & Gallery Home:**
  - Date-grouped timeline automatically organizing media by day (*Today*, *Yesterday*, and localized date headers).
  - Responsive multi-column grid layout supporting customizable grid densities (2, 3, or 4 columns).
  - Highlighting header displaying total live photos and videos count with quick search navigation.
  - Interactive **Memories Carousel** featuring automatic highlight collections, spotlight moments, and full-screen reel slideshows.
  - Multi-select action bar for batch operations: sharing, favorite toggling, moving/copying into custom albums, and soft deletion.
- **Albums & Smart Collections:**
  - Built-in smart collections for **Camera**, **Favorites**, **Screenshots**, **Videos**, **Downloads**, and **Recently Added**.
  - Dynamic discovery of physical device folder buckets.
  - Custom user-created albums with metadata-backed photo/video assignment and organization.
  - Album details view with dedicated slideshow playback and batch album management.
- **Curated Favorites Hub:**
  - Dedicated Favorites screen with media type filtering (*All*, *Photos*, *Videos*) and date range filtering (*All Time*, *Today*, *Last 7 Days*, *Last 30 Days*, *This Year*).
  - Integrated full-screen slideshow reel and system sharing/printing export actions.
- **Full-Screen Media Viewer & EXIF Telemetry:**
  - Edge-to-edge swipeable media viewer with native video playback controls.
  - Interactive zoomable viewport supporting pinch-to-zoom (up to 4.5x) and double-tap zoom gestures.
  - In-depth **Photo Info & EXIF Telemetry Sheet** displaying camera make, model, lens profile, optical parameters (aperture, shutter speed, ISO, focal length, 35mm equivalent, EV bias, flash state, white balance, metering mode), color space, resolution, file size, relative file path, and luminance histogram.
  - Shot location label parsing with coordinate display when GPS metadata is available.
  - Photo notes and comma-separated tag editor persisted directly in local storage.
  - Actions for setting wallpapers via system intent, media renaming, sharing, and external editor invocation.
- **Built-In Media Editors:**
  - **Photo Editor:** Crop, rotation (90° increments), aspect ratio presets, brightness/contrast adjustments, visual filter presets, stickers, text overlays, and freehand drawing canvas.
  - **Video Editor:** Timeline trimming with start/end markers, audio mute toggle, playback preview, and video export.
- **Offline Search & Discovery:**
  - Instant offline search across filenames, folder bucket names, custom album names, user captions, and tags.
  - Search filter chips for media types and date ranges.
  - Persistent recent search query history with individual deletion and clear-all capabilities.
- **Recycle Bin & Data Safety:**
  - Dedicated Recycle Bin view preventing accidental file loss.
  - Non-destructive soft-delete moving items to the Recycle Bin while retaining original media files on disk.
  - Full restoration capability returning deleted items to their original albums and timeline positions.
  - Batch permanent deletion with confirmation dialogs and empty-bin actions.
- **Application Preferences & Customization:**
  - Material 3 theme switching supporting System Default, Light Mode, and Dark Mode.
  - Timeline display preferences for toggling video visibility and video duration badges.
  - Toggle for the Memories section visibility.
  - Configurable default sort orders: Date Descending, Date Ascending, Name Ascending, and File Size Descending.
- **Privacy & Security Architecture:**
  - Zero `INTERNET` permission declared in `AndroidManifest.xml` — fully offline with zero telemetry or cloud tracking.
  - Compliance with modern Android storage permissions: granular Android 13+ permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`), Android 14+ partial visual user selection (`READ_MEDIA_VISUAL_USER_SELECTED`), and Android 10+ media location access (`ACCESS_MEDIA_LOCATION`).
  - Integration with the zero-permission Android Photo Picker (`PickMultipleVisualMedia`) for importing external media safely.

### Implemented
- **Robust Media Deletion Flow:**
  - Integrated `MediaStore.createDeleteRequest` (Android 11+) and `RecoverableSecurityException` (Android 10) user-consent flows so system permission dialogs are handled safely via `ActivityResultContracts.StartIntentSenderForResult`.
  - Added atomic dialog launch locking (`markConsentDialogLaunched`) to eliminate duplicate permission dialog triggers and race conditions during deletion requests.
  - Implemented safe URI existence verification to ensure MediaStore records are validated before deletion requests.
  - Added logic to retain failed or cancelled items in the Recycle Bin for user retry, supporting partial batch success during multi-item deletions.
- **Data Architecture & Persistence:**
  - Room SQLite database (`GalleryDatabase`) for metadata persistence: custom albums, album mappings, user annotations (captions & tags), favorites, recent searches, picked media, and recycle bin items.
  - Jetpack DataStore (`UserPreferencesRepository`) for preferences storage (theming, grid density, sort options, display toggles).
  - Reactive repository pattern (`GalleryRepository`) combining real-time MediaStore queries via `ContentObserver` with Room metadata flows.
  - State management using Jetpack `ViewModel` and Kotlin `StateFlow`.
- **Automated Testing Suite:**
  - Robolectric test suite validating unit and component behaviors including app branding, media item telemetry formatting, soft deletion, library restoration, permanent deletion, partial batch failure handling, and deletion consent cancellation/approval flows.

### Enhanced
- **Confirmation, Feedback, and Theme Compatibility:**
  - Enhanced user feedback and error prevention during single-photo viewer deletions, multi-selection batch deletions, and Recycle Bin management.
  - Ensured Cancel actions safely dismiss dialogs without modifying any files or database records.
  - Optimized toast notification system to remove duplicate triggers, ensuring exactly one message per action.
  - Maintained full Light and Dark theme compatibility across confirmation dialogs, toast notifications, and interactive chrome using the centralized theme system.
