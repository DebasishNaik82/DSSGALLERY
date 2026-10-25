# Changelog

All notable changes to the **DSS Gallery** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
