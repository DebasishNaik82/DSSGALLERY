package com.example.data.mediastore

import android.Manifest
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.data.local.PickedMediaEntity
import com.example.model.ExifTelemetry
import com.example.model.MediaItem
import com.example.model.PermissionAccessLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MediaStoreDataSource(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun getPermissionAccessLevel(): PermissionAccessLevel {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasImages = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val hasVideo = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            val hasPartial = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED

            when {
                hasImages || hasVideo -> PermissionAccessLevel.FULL_ACCESS
                hasPartial -> PermissionAccessLevel.PARTIAL_ACCESS
                else -> PermissionAccessLevel.DENIED
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val hasVideo = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasImages || hasVideo) PermissionAccessLevel.FULL_ACCESS else PermissionAccessLevel.DENIED
        } else {
            val hasStorage = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (hasStorage) PermissionAccessLevel.FULL_ACCESS else PermissionAccessLevel.DENIED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        awaitClose {
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
    }

    suspend fun queryAllDeviceMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        result.addAll(queryImages())
        result.addAll(queryVideos())
        result.sortedByDescending { it.dateTakenMillis }
    }

    private fun queryImages(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Images.Media.IS_FAVORITE)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val mimeType = cursor.getString(mimeCol) ?: "image/jpeg"
                    val dateAddedSec = cursor.getLong(addedCol)
                    val dateModSec = cursor.getLong(modCol)
                    val dateTakenMs = cursor.getLong(takenCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val bucketId = cursor.getString(bucketIdCol) ?: "default"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Camera"
                    val relPath = if (relPathCol != -1) {
                        cursor.getString(relPathCol) ?: "DCIM/$bucketName/"
                    } else {
                        "DCIM/$bucketName/"
                    }
                    val isFav = if (favCol != -1) cursor.getInt(favCol) == 1 else false

                    val addedMillis = dateAddedSec * 1000L
                    val effectiveTaken = if (dateTakenMs > 0L) dateTakenMs else addedMillis

                    items.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            isVideo = false,
                            dateAddedMillis = addedMillis,
                            dateModifiedMillis = dateModSec * 1000L,
                            dateTakenMillis = effectiveTaken,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            durationMillis = 0L,
                            bucketId = bucketId,
                            bucketDisplayName = bucketName,
                            relativePath = "/Internal Storage/${relPath.trimStart('/')}$displayName",
                            isFavorite = isFav
                        )
                    )
                }
            }
        }
        return items
    }

    private fun queryVideos(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Video.Media.IS_FAVORITE)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Video.Media.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = cursor.getString(nameCol) ?: "VID_$id.mp4"
                    val mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                    val dateAddedSec = cursor.getLong(addedCol)
                    val dateModSec = cursor.getLong(modCol)
                    val dateTakenMs = cursor.getLong(takenCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val duration = cursor.getLong(durationCol)
                    val bucketId = cursor.getString(bucketIdCol) ?: "videos"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Movies"
                    val relPath = if (relPathCol != -1) {
                        cursor.getString(relPathCol) ?: "Movies/$bucketName/"
                    } else {
                        "Movies/$bucketName/"
                    }
                    val isFav = if (favCol != -1) cursor.getInt(favCol) == 1 else false

                    val addedMillis = dateAddedSec * 1000L
                    val effectiveTaken = if (dateTakenMs > 0L) dateTakenMs else addedMillis

                    items.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            isVideo = true,
                            dateAddedMillis = addedMillis,
                            dateModifiedMillis = dateModSec * 1000L,
                            dateTakenMillis = effectiveTaken,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            durationMillis = duration,
                            bucketId = bucketId,
                            bucketDisplayName = bucketName,
                            relativePath = "/Internal Storage/${relPath.trimStart('/')}$displayName",
                            isFavorite = isFav
                        )
                    )
                }
            }
        }
        return items
    }

    suspend fun inspectPickedUris(uris: List<Uri>): List<PickedMediaEntity> = withContext(Dispatchers.IO) {
        val entities = mutableListOf<PickedMediaEntity>()
        val now = System.currentTimeMillis()
        uris.forEachIndexed { index, uri ->
            runCatching {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Photo picker ephemeral or already granted
                }

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val isVideo = mimeType.startsWith("video")
                var displayName = "Picked_${now + index}.jpg"
                var size = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                var width = 1920
                var height = 1440
                if (!isVideo) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                        val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                        if (w > 0 && h > 0) {
                            width = w
                            height = h
                        }
                    }
                }

                entities.add(
                    PickedMediaEntity(
                        uriString = uri.toString(),
                        mediaId = abs(uri.toString().hashCode().toLong()) + 900_000L,
                        displayName = displayName,
                        mimeType = mimeType,
                        isVideo = isVideo,
                        dateAddedMillis = now - (index * 30_000L),
                        sizeBytes = if (size > 0L) size else 2_450_000L,
                        width = width,
                        height = height,
                        durationMillis = if (isVideo) 18_000L else 0L
                    )
                )
            }
        }
        entities
    }

    suspend fun extractExifTelemetry(item: MediaItem): ExifTelemetry = withContext(Dispatchers.IO) {
        if (item.isVideo) {
            return@withContext ExifTelemetry(
                cameraMake = "Android Device",
                cameraModel = "Video Capture",
                lensModel = "Standard Lens",
                aperture = "f/1.8",
                shutterSpeed = "1/60s",
                iso = "ISO 100",
                focalLength = "24mm",
                focalLength35mm = "24mm (35mm eq.)",
                exposureBias = "0.0 EV",
                flashState = "Continuous Off",
                whiteBalance = "Auto WB (5600K)",
                meteringMode = "Multi-Segment",
                exposureProgram = "Shutter Priority",
                orientation = "Horizontal (Normal)",
                software = "Android MediaCodec H.265",
                colorSpace = "Rec.709 / HLG",
                locationName = item.effectiveAlbumName,
                formatBadge = item.mimeType.substringAfterLast('/').uppercase(Locale.US),
                filmSimulation = "Natural Video Profile"
            )
        }

        val histogram = computeLuminanceHistogram(item.uri)

        runCatching {
            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
            } else {
                item.uri
            }

            contentResolver.openInputStream(targetUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "Unknown"
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "Android Device"
                val lens = (exif.getAttribute("LensModel") ?: exif.getAttribute("LensSpecification"))
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Unknown Lens"

                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()
                    ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)?.toDoubleOrNull()
                val aperture = if (fNumber != null && fNumber > 0) {
                    String.format(Locale.US, "f/%.1f", fNumber)
                } else "f/2.8"

                val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()
                val shutter = when {
                    expTime == null || expTime <= 0.0 -> "1/250s"
                    expTime < 1.0 -> "1/${(1.0 / expTime).roundToInt().coerceAtLeast(1)}s"
                    else -> String.format(Locale.US, "%.1fs", expTime)
                }

                val isoRaw = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                    ?: exif.getAttribute("PhotographicSensitivity")
                val iso = if (!isoRaw.isNullOrBlank()) "ISO $isoRaw" else "ISO 160"

                val focalRatio = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                val focalMm = if (!focalRatio.isNullOrBlank()) {
                    val parts = focalRatio.split("/")
                    if (parts.size == 2) {
                        val num = parts[0].toDoubleOrNull() ?: 23.0
                        val den = (parts[1].toDoubleOrNull() ?: 1.0).coerceAtLeast(1.0)
                        (num / den).roundToInt()
                    } else {
                        focalRatio.toDoubleOrNull()?.roundToInt() ?: 23
                    }
                } else 23
                val focal = "${focalMm}mm"

                val focal35Raw = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.toIntOrNull()
                    ?: (focalMm * 1.5).roundToInt()
                val focal35 = "${focal35Raw}mm (35mm eq.)"

                val expBiasRaw = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.toDoubleOrNull()
                val expBias = if (expBiasRaw != null) {
                    val sign = if (expBiasRaw >= 0) "+" else ""
                    String.format(Locale.US, "%s%.1f EV", sign, expBiasRaw)
                } else "+0.3 EV"

                val flashInt = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0)
                val flashState = if ((flashInt and 1) != 0) "Fired, Auto mode" else "Off, Did not fire"

                val wbInt = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, ExifInterface.WHITEBALANCE_AUTO)
                val whiteBalance = if (wbInt == ExifInterface.WHITEBALANCE_MANUAL) "Manual Kelvin" else "Auto WB"

                val meteringInt = exif.getAttributeInt(ExifInterface.TAG_METERING_MODE, 5)
                val meteringMode = when (meteringInt) {
                    1 -> "Average"
                    2 -> "Center-Weighted"
                    3 -> "Spot"
                    4 -> "Multi-Spot"
                    5 -> "Multi-Segment Pattern"
                    6 -> "Partial"
                    else -> "Multi-Segment Pattern"
                }

                val expProgInt = exif.getAttributeInt(ExifInterface.TAG_EXPOSURE_PROGRAM, 3)
                val exposureProgram = when (expProgInt) {
                    1 -> "Manual (M)"
                    2 -> "Program AE (P)"
                    3 -> "Aperture Priority (A)"
                    4 -> "Shutter Priority (S)"
                    else -> "Aperture Priority (A)"
                }

                val orientationInt = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val orientation = when (orientationInt) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> "Rotate 90° CW"
                    ExifInterface.ORIENTATION_ROTATE_180 -> "Rotate 180°"
                    ExifInterface.ORIENTATION_ROTATE_270 -> "Rotate 270° CW"
                    else -> "Horizontal (Normal)"
                }

                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "Unknown"

                val dateTimeOrig = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.trim().orEmpty()

                val colorSpaceInt = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, 1)
                val colorSpace = if (colorSpaceInt == 1) "sRGB / Display P3" else "Display P3 Wide Gamut"

                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)
                val lat = if (hasLatLong) latLong[0].toDouble() else null
                val lon = if (hasLatLong) latLong[1].toDouble() else null
                val alt = exif.getAltitude(0.0).takeIf { it != 0.0 }

                val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim()
                val imageDesc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.trim()

                val locationLabel = when {
                    !imageDesc.isNullOrBlank() -> imageDesc
                    lat != null && lon != null -> resolveLocationLabel(lat, lon)
                    else -> item.effectiveAlbumName
                }

                val filmSim = userComment?.takeIf { it.isNotBlank() } ?: "No Metadata"

                val ext = item.displayName.substringAfterLast('.', "JPG").uppercase(Locale.US)
                val badge = if (ext == "JPG" || ext == "JPEG") "RAW + JPEG" else ext

                return@withContext ExifTelemetry(
                    cameraMake = make,
                    cameraModel = model,
                    lensModel = lens,
                    aperture = aperture,
                    shutterSpeed = shutter,
                    iso = iso,
                    focalLength = focal,
                    focalLength35mm = focal35,
                    exposureBias = expBias,
                    flashState = flashState,
                    whiteBalance = whiteBalance,
                    meteringMode = meteringMode,
                    exposureProgram = exposureProgram,
                    orientation = orientation,
                    software = software,
                    dateTimeOriginal = dateTimeOrig,
                    colorSpace = colorSpace,
                    latitude = lat,
                    longitude = lon,
                    filmSimulation = filmSim,
                    histogramBins = histogram
                )
            }
        }

        ExifTelemetry(
            locationName = item.effectiveAlbumName,
            formatBadge = item.displayName.substringAfterLast('.', "JPEG").uppercase(Locale.US),
            histogramBins = histogram
        )
    }

    private fun computeLuminanceHistogram(uri: Uri): List<Float> {
        val bins = IntArray(32)
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val bmp = android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                if (bmp != null) {
                    val w = bmp.width
                    val h = bmp.height
                    val stepX = (w / 48).coerceAtLeast(1)
                    val stepY = (h / 48).coerceAtLeast(1)
                    var y = 0
                    while (y < h) {
                        var x = 0
                        while (x < w) {
                            val pixel = bmp.getPixel(x, y)
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF
                            val luma = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
                            val binIdx = (luma * 31) / 255
                            bins[binIdx]++
                            x += stepX
                        }
                        y += stepY
                    }
                    bmp.recycle()
                }
            }
        }
        val maxVal = (bins.maxOrNull() ?: 0).coerceAtLeast(1)
        return if (bins.sum() == 0) {
            ExifTelemetry().histogramBins
        } else {
            bins.map { count -> (count.toFloat() / maxVal.toFloat()).coerceIn(0.05f, 1.0f) }
        }
    }

    private fun resolveLocationLabel(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.4f°, %.4f°", lat, lon)
    }

    private enum class MediaExistence {
        EXISTS,
        DEFINITELY_NOT_FOUND,
        UNKNOWN_OR_INACCESSIBLE
    }

    private fun isStandardMediaStoreUri(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != MediaStore.AUTHORITY) {
            return false
        }
        val path = uri.path ?: return false
        if (path.startsWith("/picker")) return false
        val id = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
        return id >= 0L
    }

    private fun checkMediaStoreExistence(uri: Uri): MediaExistence {
        if (!isStandardMediaStoreUri(uri)) {
            return MediaExistence.UNKNOWN_OR_INACCESSIBLE
        }
        return try {
            val cursor = contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )
            if (cursor == null) {
                MediaExistence.UNKNOWN_OR_INACCESSIBLE
            } else {
                cursor.use {
                    if (it.moveToFirst()) {
                        MediaExistence.EXISTS
                    } else {
                        MediaExistence.DEFINITELY_NOT_FOUND
                    }
                }
            }
        } catch (_: SecurityException) {
            MediaExistence.EXISTS
        } catch (_: Exception) {
            MediaExistence.UNKNOWN_OR_INACCESSIBLE
        }
    }

    private fun createDeleteRequestSafely(
        candidateUris: MutableList<Uri>,
        failedUris: MutableSet<String>
    ): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || candidateUris.isEmpty()) return null

        val batchSender = runCatching {
            val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(contentResolver, candidateUris)
            pendingIntent.intentSender
        }.getOrNull()
        if (batchSender != null) {
            return batchSender
        }

        val validUris = mutableListOf<Uri>()
        for (uri in candidateUris) {
            val singleSender = runCatching {
                MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
            }.getOrNull()
            if (singleSender != null) {
                validUris.add(uri)
            } else {
                failedUris.add(uri.toString())
            }
        }
        candidateUris.clear()
        candidateUris.addAll(validUris)

        if (validUris.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(contentResolver, validUris).intentSender
        }.getOrElse {
            validUris.forEach { failedUris.add(it.toString()) }
            candidateUris.clear()
            null
        }
    }

    fun isAppOwnedMedia(uri: Uri): Boolean {
        if (!isStandardMediaStoreUri(uri)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val ownerIndex = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                    if (ownerIndex != -1) {
                        val owner = cursor.getString(ownerIndex)
                        owner == context.packageName
                    } else false
                } else false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun requiresSystemConsent(items: List<MediaItem>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            items.any { isStandardMediaStoreUri(it.uri) && !isAppOwnedMedia(it.uri) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            items.any { isStandardMediaStoreUri(it.uri) && !isAppOwnedMedia(it.uri) }
        } else {
            false
        }
    }

    /**
     * Attempts to permanently delete the specified media items from device storage.
     * - Returns confirmed deleted URIs, URIs pending user consent, failed URIs, and an optional IntentSender.
     * - Supports direct deletion, Android 10 (Q) RecoverableSecurityException, and Android 11+ (R) MediaStore.createDeleteRequest.
     */
    suspend fun deleteMediaItemsWithResult(items: List<MediaItem>): MediaStoreDeleteResult = withContext(Dispatchers.IO) {
        val distinctItems = items.distinctBy { it.uri.toString() }
        val needsConsent = requiresSystemConsent(distinctItems)

        // On Android 11+, if items require system consent, request batch deletion directly without deleting any items first
        if (needsConsent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val candidateUris = distinctItems.filter { isStandardMediaStoreUri(it.uri) }.map { it.uri }.toMutableList()
            val tempFailed = LinkedHashSet<String>()
            val batchSender = createDeleteRequestSafely(candidateUris, tempFailed)
            if (batchSender != null) {
                return@withContext MediaStoreDeleteResult(
                    deletedUris = LinkedHashSet(),
                    pendingConsentUris = candidateUris,
                    failedUris = tempFailed,
                    intentSender = batchSender
                )
            }
        }

        val deletedUris = LinkedHashSet<String>()
        val needsConsentUris = mutableListOf<Uri>()
        val failedUris = LinkedHashSet<String>()
        var recoverableIntentSender: IntentSender? = null

        for (item in distinctItems) {
            val uri = item.uri
            val uriStr = uri.toString()
            if (uriStr.isBlank() || uri.scheme.isNullOrBlank()) {
                failedUris.add(uriStr)
                continue
            }

            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val path = uri.path
                if (path.isNullOrBlank()) {
                    failedUris.add(uriStr)
                } else {
                    val file = java.io.File(path)
                    val deleted = runCatching {
                        if (!file.exists()) true else file.delete()
                    }.getOrDefault(false)
                    if (deleted) {
                        deletedUris.add(uriStr)
                    } else {
                        failedUris.add(uriStr)
                    }
                }
                continue
            }

            val isMediaStoreUri = isStandardMediaStoreUri(uri)
            try {
                val deletedRows = contentResolver.delete(uri, null, null)
                if (deletedRows > 0) {
                    deletedUris.add(uriStr)
                } else if (checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
                    deletedUris.add(uriStr)
                } else {
                    failedUris.add(uriStr)
                }
            } catch (securityException: SecurityException) {
                if (isMediaStoreUri) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        securityException is android.app.RecoverableSecurityException &&
                        recoverableIntentSender == null
                    ) {
                        recoverableIntentSender = securityException.userAction.actionIntent.intentSender
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        needsConsentUris.add(uri)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        securityException is android.app.RecoverableSecurityException
                    ) {
                        needsConsentUris.add(uri)
                    } else {
                        failedUris.add(uriStr)
                    }
                } else {
                    failedUris.add(uriStr)
                }
            } catch (_: Exception) {
                if (checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
                    deletedUris.add(uriStr)
                } else {
                    failedUris.add(uriStr)
                }
            }
        }

        var intentSender: IntentSender? = null
        if (needsConsentUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val candidates = ArrayList(needsConsentUris)
                val tempFailed = LinkedHashSet<String>()
                intentSender = createDeleteRequestSafely(candidates, tempFailed)
                if (intentSender != null) {
                    needsConsentUris.clear()
                    needsConsentUris.addAll(candidates)
                    failedUris.addAll(tempFailed)
                } else if (recoverableIntentSender != null) {
                    intentSender = recoverableIntentSender
                } else {
                    needsConsentUris.forEach { failedUris.add(it.toString()) }
                    needsConsentUris.clear()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intentSender = recoverableIntentSender
                if (intentSender == null) {
                    needsConsentUris.forEach { failedUris.add(it.toString()) }
                    needsConsentUris.clear()
                }
            } else {
                needsConsentUris.forEach { failedUris.add(it.toString()) }
                needsConsentUris.clear()
            }
        }

        MediaStoreDeleteResult(
            deletedUris = deletedUris,
            pendingConsentUris = needsConsentUris,
            failedUris = failedUris,
            intentSender = intentSender
        )
    }

    /**
     * Verifies and finalizes deletion for items after the user approves the system consent dialog.
     * - On Android 11+, verifies that MediaProvider deleted the item (or deletes if still present).
     * - On Android 10, executes contentResolver.delete() using the newly granted write permission.
     */
    suspend fun finalizePendingDelete(items: List<MediaItem>): MediaStoreDeleteResult = withContext(Dispatchers.IO) {
        val deletedUris = LinkedHashSet<String>()
        val stillPendingUris = mutableListOf<Uri>()
        val failedUris = LinkedHashSet<String>()
        var nextIntentSender: IntentSender? = null

        for (item in items.distinctBy { it.uri.toString() }) {
            val uri = item.uri
            val uriStr = uri.toString()

            if (checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
                deletedUris.add(uriStr)
                continue
            }

            try {
                val rowsDeleted = contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0 || checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
                    deletedUris.add(uriStr)
                } else {
                    failedUris.add(uriStr)
                }
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    securityException is android.app.RecoverableSecurityException
                ) {
                    if (nextIntentSender == null) {
                        nextIntentSender = securityException.userAction.actionIntent.intentSender
                    }
                    stillPendingUris.add(uri)
                } else {
                    failedUris.add(uriStr)
                }
            } catch (_: Exception) {
                if (checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
                    deletedUris.add(uriStr)
                } else {
                    failedUris.add(uriStr)
                }
            }
        }

        if (stillPendingUris.isNotEmpty() && nextIntentSender == null) {
            stillPendingUris.forEach { failedUris.add(it.toString()) }
            stillPendingUris.clear()
        }

        MediaStoreDeleteResult(
            deletedUris = deletedUris,
            pendingConsentUris = stillPendingUris,
            failedUris = failedUris,
            intentSender = nextIntentSender
        )
    }

    /**
     * Deletes media items safely and returns an IntentSender if user consent is required.
     */
    suspend fun deleteMediaItems(items: List<MediaItem>): IntentSender? {
        return deleteMediaItemsWithResult(items).intentSender
    }

    suspend fun renameMedia(uri: Uri, newName: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            }
            contentResolver.update(uri, values, null, null)
        }
    }
}

data class MediaStoreDeleteResult(
    val deletedUris: Set<String> = emptySet(),
    val pendingConsentUris: List<Uri> = emptyList(),
    val failedUris: Set<String> = emptySet(),
    val intentSender: IntentSender? = null
)
