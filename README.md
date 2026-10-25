# Gallery — Porcelain Edition

A complete, offline-first Android photo and video gallery application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Android MediaStore**, **Room**, and **DataStore**. Inspired by the tactile warmth and fluidity of OPPO ColorOS and archival Porcelain aesthetics.

## Key Features

1. **Photos Home Screen**
   - Large editorial **Gallery** header with live photo and video counts.
   - Rounded search bar with instant offline access.
   - **Memories Carousel**: Horizontal highlight cards with stacked thumbnail previews and an interactive full-screen **Memory Reel Slideshow**.
   - **Date-Grouped Timeline**: Groups media into readable headings (*Today*, *Yesterday*, and formatted dates) with responsive 2-, 3-, or 4-column grids, video duration chips, favorite badges, and multi-select mode.
2. **Albums & Collections**
   - Built-in smart collections: **Camera**, **Favorites**, **Screenshots**, **Videos**, **Downloads**, and **Recently Added**.
   - Discovers device folder buckets automatically and supports creating **Custom Personal Albums**.
3. **Curated Favorites & Batch Studio**
   - Dedicated Favorites timeline with date & media-type filters, **Annual Recap Memory Reel**, and **Quick Batch Actions** (*Share*, *Porcelain Collage Generator* saving directly to `MediaStore`, and *Print Sheet*).
4. **Recycle Bin & Data Management**
   - Securely move deleted items to a dedicated Recycle Bin instead of permanent deletion.
   - Easy restoration of deleted photos and videos.
5. **Full-Screen Viewer & EXIF Telemetry Inspector**
   - Edge-to-edge swipeable `HorizontalPager` with pinch-to-zoom (`1x–4.5x`), double-tap zoom, and native video playback.
   - **Photo Info (EXIF & Telemetry)**: Displays camera make/model, lens specification, 6-cell optical telemetry grid (Aperture, Shutter, ISO, Focal Length, Exposure Compensation, Flash), file size, resolution, color space, interactive file path copy, shot location map card, and Room-persisted tags & personal journal notes.
6. **Explore & Search**
   - Instant offline search across filenames, album names, personal captions, and tags.
   - Filter by media type (*Photos*, *Videos*), date ranges, and albums, with persisted recent searches.
7. **Strict Offline & Privacy-First Architecture**
   - Zero `INTERNET` permission in `AndroidManifest.xml`.
   - Zero cloud SDKs, analytics, or external servers.
   - Supports Android 13+ granular media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`), Android 14+ Partial Photo Access (`READ_MEDIA_VISUAL_USER_SELECTED`), and the zero-permission **Android Photo Picker**.

## Architecture

- **`model/`**: Immutable domain models (`MediaItem`, `ExifTelemetry`, `AlbumItem`, `TimelineDayGroup`, `MemoryCollection`, `GalleryPreferences`).
- **`data/mediastore/`**: `MediaStoreDataSource` (real `ContentResolver` queries, `ContentObserver`, `ExifInterface` parser, `MediaStore.createDeleteRequest` consent handler) and `StudioSampleGenerator` (writes real high-res JPEGs with EXIF tags and collages to `MediaStore`).
- **`data/local/`**: `GalleryDatabase` & `GalleryDao` (Room SQLite persistence for favorites, captions, tags, custom albums, recent searches, and photo picker URIs).
- **`data/preferences/`**: `UserPreferencesRepository` (Jetpack DataStore for theme, grid density, sorting, and video visibility).
- **`viewmodel/`**: `GalleryViewModel` reactive `StateFlow` state holder.
- **`ui/`**: Modular Jetpack Compose screens and reusable Porcelain theme components.

## Setup & Build Instructions

1. Open the project in Android Studio (Ladybug or newer recommended) with JDK 11+ and Android SDK 36.
2. Build the debug and release APKs/AABs via Gradle:
   ```bash
   gradle assembleDebug bundleDebug assembleRelease bundleRelease
   ```
3. Run local JVM & Robolectric unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

## Outputs

- Debug builds: `output/debug/`
- Release builds: `output/release/`
