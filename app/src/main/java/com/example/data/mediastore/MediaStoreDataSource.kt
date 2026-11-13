package com.example.data.mediastore

import android.Manifest
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
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
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "MediaStoreDataSource"

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
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idCol == -1) return@use
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "IMG_$id.jpg"
                    val mimeType = (if (mimeCol != -1) cursor.getString(mimeCol) else null) ?: "image/jpeg"
                    val dateAddedSec = if (addedCol != -1) cursor.getLong(addedCol) else 0L
                    val dateModSec = if (modCol != -1) cursor.getLong(modCol) else dateAddedSec
                    val dateTakenMs = if (takenCol != -1) cursor.getLong(takenCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val bucketId = (if (bucketIdCol != -1) cursor.getString(bucketIdCol) else null) ?: "default"
                    val bucketName = (if (bucketNameCol != -1) cursor.getString(bucketNameCol) else null) ?: "Camera"
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
        }.onFailure { error ->
            Log.e(TAG, "Failed to query images from MediaStore", error)
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
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                if (idCol == -1) return@use
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val takenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Video.Media.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "VID_$id.mp4"
                    val mimeType = (if (mimeCol != -1) cursor.getString(mimeCol) else null) ?: "video/mp4"
                    val dateAddedSec = if (addedCol != -1) cursor.getLong(addedCol) else 0L
                    val dateModSec = if (modCol != -1) cursor.getLong(modCol) else dateAddedSec
                    val dateTakenMs = if (takenCol != -1) cursor.getLong(takenCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                    val bucketId = (if (bucketIdCol != -1) cursor.getString(bucketIdCol) else null) ?: "videos"
                    val bucketName = (if (bucketNameCol != -1) cursor.getString(bucketNameCol) else null) ?: "Movies"
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
        }.onFailure { error ->
            Log.e(TAG, "Failed to query videos from MediaStore", error)
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

    /**
     * Validates a target filename and preserves the original file extension.
     */
    fun validateAndSanitizeFilename(
        rawInput: String,
        originalDisplayName: String
    ): Result<String> {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isEmpty()) {
            return Result.failure(IllegalArgumentException("File name cannot be empty"))
        }

        val lastDot = originalDisplayName.lastIndexOf('.')
        val originalExtension = if (lastDot > 0 && lastDot < originalDisplayName.length - 1) {
            originalDisplayName.substring(lastDot + 1)
        } else {
            ""
        }

        val rawBaseName = if (originalExtension.isNotEmpty() &&
            trimmedInput.endsWith(".$originalExtension", ignoreCase = true)
        ) {
            trimmedInput.dropLast(originalExtension.length + 1).trim()
        } else {
            trimmedInput
        }

        if (rawBaseName.isEmpty()) {
            return Result.failure(IllegalArgumentException("File name cannot be empty"))
        }
        if (rawBaseName == "." || rawBaseName == "..") {
            return Result.failure(IllegalArgumentException("Invalid file name"))
        }
        if (rawBaseName.length > 200) {
            return Result.failure(IllegalArgumentException("File name is too long"))
        }

        val forbiddenChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        if (rawBaseName.any { ch -> ch in forbiddenChars || ch.code < 32 }) {
            return Result.failure(
                IllegalArgumentException("File name contains invalid characters (\\ / : * ? \" < > |)")
            )
        }

        val sanitizedFullName = if (originalExtension.isNotEmpty()) {
            "$rawBaseName.$originalExtension"
        } else {
            rawBaseName
        }

        return Result.success(sanitizedFullName)
    }

    private fun isPhotoPickerUri(uri: Uri): Boolean {
        val path = uri.path.orEmpty()
        val authority = uri.authority.orEmpty()
        return path.startsWith("/picker") ||
            path.contains("/picker/") ||
            authority.contains("photopicker", ignoreCase = true)
    }

    private fun hasDuplicateInSameDirectory(
        item: MediaItem,
        targetDisplayName: String
    ): Boolean {
        val uri = item.uri
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let { File(it) } ?: return false
            val parent = file.parentFile ?: return false
            val siblings = parent.listFiles() ?: return false
            return siblings.any { sibling ->
                sibling.name.equals(targetDisplayName, ignoreCase = true) &&
                    sibling.absolutePath != file.absolutePath
            }
        }

        if (!isStandardMediaStoreUri(uri)) {
            return false
        }

        val collection = if (item.isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        var currentBucketId: String? = item.bucketId.takeIf { it.isNotBlank() }
        var currentRelativePath: String? = null
        var currentDataPath: String? = null

        val itemProj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_ID
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()

        runCatching {
            contentResolver.query(uri, itemProj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val bIdx = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                    if (bIdx != -1) currentBucketId = c.getString(bIdx) ?: currentBucketId
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        if (rIdx != -1) currentRelativePath = c.getString(rIdx)
                    } else {
                        @Suppress("DEPRECATION")
                        val dIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dIdx != -1) currentDataPath = c.getString(dIdx)
                    }
                }
            }
        }

        if (!currentDataPath.isNullOrBlank()) {
            val oldFile = File(currentDataPath!!)
            val parent = oldFile.parentFile
            if (parent != null && parent.exists()) {
                val targetFile = File(parent, targetDisplayName)
                if (targetFile.exists() && !targetFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = false)) {
                    return true
                }
            }
        }

        val currentId = runCatching { ContentUris.parseId(uri) }.getOrDefault(item.id)
        val selectionBuilder = StringBuilder("${MediaStore.MediaColumns._ID} != ?")
        val args = mutableListOf(currentId.toString())

        if (!currentRelativePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionBuilder.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
            args.add(currentRelativePath!!)
        } else if (!currentBucketId.isNullOrBlank()) {
            selectionBuilder.append(" AND ${MediaStore.MediaColumns.BUCKET_ID} = ?")
            args.add(currentBucketId!!)
        }

        return runCatching {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selectionBuilder.toString(),
                args.toTypedArray(),
                null
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameCol == -1) return@use false
                while (cursor.moveToNext()) {
                    val existingName = cursor.getString(nameCol) ?: continue
                    if (existingName.equals(targetDisplayName, ignoreCase = true)) {
                        return@use true
                    }
                }
                false
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Safely renames a photo or video across Android 7–15+ without crashing or losing the original file.
     * - Validates filenames and preserves original file extensions.
     * - Prevents duplicate filenames in the same directory.
     * - Uses MediaStore APIs for Scoped Storage (Android 10+) and requests user consent when required.
     * - Handles Android <= 9 legacy storage safely with rollback if MediaStore update fails.
     */
    suspend fun renameMediaSafely(
        item: MediaItem,
        newName: String
    ): MediaStoreRenameResult = withContext(Dispatchers.IO) {
        val validation = validateAndSanitizeFilename(newName, item.displayName)
        val sanitizedName = validation.getOrElse { error ->
            return@withContext MediaStoreRenameResult.Failure(
                error.message ?: "Invalid file name"
            )
        }

        if (sanitizedName == item.displayName) {
            return@withContext MediaStoreRenameResult.Failure("Please enter a different file name")
        }

        val uri = item.uri
        if (isPhotoPickerUri(uri)) {
            return@withContext MediaStoreRenameResult.Failure(
                "Items added via Photo Picker are read-only and cannot be renamed"
            )
        }

        if (hasDuplicateInSameDirectory(item, sanitizedName)) {
            return@withContext MediaStoreRenameResult.Failure(
                "A file named \"$sanitizedName\" already exists in this folder"
            )
        }

        // Case 1: file:// URI
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val oldPath = uri.path
            if (oldPath.isNullOrBlank()) {
                return@withContext MediaStoreRenameResult.Failure("Invalid file path")
            }
            return@withContext try {
                val oldFile = File(oldPath)
                if (!oldFile.exists()) {
                    return@withContext MediaStoreRenameResult.Failure("Original file not found on storage")
                }
                val parent = oldFile.parentFile
                    ?: return@withContext MediaStoreRenameResult.Failure("Cannot resolve parent directory")
                val targetFile = File(parent, sanitizedName)
                if (targetFile.exists()) {
                    return@withContext MediaStoreRenameResult.Failure(
                        "A file named \"$sanitizedName\" already exists in this folder"
                    )
                }
                if (!oldFile.renameTo(targetFile)) {
                    return@withContext MediaStoreRenameResult.Failure(
                        "Permission denied or unable to rename file"
                    )
                }
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(oldFile.absolutePath, targetFile.absolutePath),
                        null,
                        null
                    )
                }
                MediaStoreRenameResult.Success(
                    newUri = Uri.fromFile(targetFile),
                    newDisplayName = sanitizedName,
                    newRelativePath = targetFile.absolutePath,
                    newModifiedMillis = System.currentTimeMillis()
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException renaming file:// URI: $uri", e)
                MediaStoreRenameResult.Failure("Permission denied to rename this file")
            } catch (e: Exception) {
                Log.e(TAG, "Failed renaming file:// URI: $uri", e)
                MediaStoreRenameResult.Failure("Failed to rename file: ${e.localizedMessage ?: "Unknown error"}")
            }
        }

        // Case 2: Non-MediaStore content:// URI (e.g., SAF DocumentProvider)
        if (!isStandardMediaStoreUri(uri)) {
            return@withContext try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val renamedUri = DocumentsContract.renameDocument(contentResolver, uri, sanitizedName)
                    if (renamedUri != null) {
                        MediaStoreRenameResult.Success(
                            newUri = renamedUri,
                            newDisplayName = sanitizedName,
                            newRelativePath = item.relativePath.substringBeforeLast('/') + "/$sanitizedName",
                            newModifiedMillis = System.currentTimeMillis()
                        )
                    } else {
                        MediaStoreRenameResult.Failure("This file provider did not allow renaming")
                    }
                } else {
                    MediaStoreRenameResult.Failure("External or read-only file cannot be renamed here")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException renaming external document URI: $uri", e)
                MediaStoreRenameResult.Failure("Permission denied to rename this external file")
            } catch (e: Exception) {
                Log.w(TAG, "Unsupported rename on external URI: $uri", e)
                MediaStoreRenameResult.Failure("This file cannot be renamed in its current location")
            }
        }

        // Case 3: Standard MediaStore URI
        if (checkMediaStoreExistence(uri) == MediaExistence.DEFINITELY_NOT_FOUND) {
            return@withContext MediaStoreRenameResult.Failure("File no longer exists on device")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) and Android 11+ (API 30-36) Scoped Storage
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                }
                val rowsUpdated = contentResolver.update(uri, values, null, null)
                if (rowsUpdated > 0) {
                    val updatedRelPath = buildUpdatedRelativePath(item.relativePath, sanitizedName)
                    return@withContext MediaStoreRenameResult.Success(
                        newUri = uri,
                        newDisplayName = sanitizedName,
                        newRelativePath = updatedRelPath,
                        newModifiedMillis = System.currentTimeMillis()
                    )
                } else {
                    return@withContext MediaStoreRenameResult.Failure("MediaStore could not rename this file")
                }
            } catch (securityException: SecurityException) {
                Log.w(TAG, "SecurityException renaming MediaStore URI on API ${Build.VERSION.SDK_INT}: $uri", securityException)
                val consentSender: IntentSender? = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        runCatching {
                            MediaStore.createWriteRequest(contentResolver, listOf(uri)).intentSender
                        }.getOrNull() ?: if (securityException is RecoverableSecurityException) {
                            securityException.userAction.actionIntent.intentSender
                        } else null
                    }
                    securityException is RecoverableSecurityException -> {
                        securityException.userAction.actionIntent.intentSender
                    }
                    else -> null
                }

                return@withContext if (consentSender != null) {
                    MediaStoreRenameResult.NeedsConsent(
                        intentSender = consentSender,
                        targetDisplayName = sanitizedName
                    )
                } else {
                    MediaStoreRenameResult.Failure("Write permission denied to rename this file")
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException renaming MediaStore URI: $uri", e)
                return@withContext MediaStoreRenameResult.Failure(
                    "Invalid file name or duplicate file in this folder"
                )
            } catch (e: SQLiteException) {
                Log.e(TAG, "SQLiteException renaming MediaStore URI: $uri", e)
                return@withContext MediaStoreRenameResult.Failure(
                    "A file with this name already exists in this folder"
                )
            } catch (e: IOException) {
                Log.e(TAG, "IOException renaming MediaStore URI: $uri", e)
                return@withContext MediaStoreRenameResult.Failure(
                    "Storage error while renaming file"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error renaming MediaStore URI: $uri", e)
                return@withContext MediaStoreRenameResult.Failure(
                    "Unable to rename file: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        } else {
            // Android 9 (API 28) and below
            val hasWritePerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasWritePerm) {
                return@withContext MediaStoreRenameResult.Failure(
                    "Storage write permission is required to rename files"
                )
            }

            return@withContext try {
                @Suppress("DEPRECATION")
                val dataColumn = MediaStore.MediaColumns.DATA
                val oldPath = contentResolver.query(uri, arrayOf(dataColumn), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(dataColumn)
                    if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
                }

                if (oldPath.isNullOrBlank()) {
                    return@withContext MediaStoreRenameResult.Failure("Unable to locate original file path")
                }

                val oldFile = File(oldPath)
                if (!oldFile.exists()) {
                    return@withContext MediaStoreRenameResult.Failure("Original file not found on storage")
                }

                val parentDir = oldFile.parentFile
                    ?: return@withContext MediaStoreRenameResult.Failure("Invalid parent folder")
                val targetFile = File(parentDir, sanitizedName)
                if (targetFile.exists()) {
                    return@withContext MediaStoreRenameResult.Failure(
                        "A file named \"$sanitizedName\" already exists in this folder"
                    )
                }

                if (!oldFile.renameTo(targetFile)) {
                    return@withContext MediaStoreRenameResult.Failure("Could not rename file on storage")
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
                }
                val rowsUpdated = try {
                    contentResolver.update(uri, values, null, null)
                } catch (updateError: Exception) {
                    Log.e(TAG, "MediaStore update failed after File.renameTo, rolling back", updateError)
                    targetFile.renameTo(oldFile)
                    return@withContext MediaStoreRenameResult.Failure(
                        "Failed to update MediaStore record; original file preserved"
                    )
                }

                if (rowsUpdated <= 0) {
                    targetFile.renameTo(oldFile)
                    return@withContext MediaStoreRenameResult.Failure(
                        "Failed to update MediaStore record; original file preserved"
                    )
                }

                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(item.mimeType),
                        null
                    )
                }

                MediaStoreRenameResult.Success(
                    newUri = uri,
                    newDisplayName = sanitizedName,
                    newRelativePath = buildUpdatedRelativePath(item.relativePath, sanitizedName),
                    newModifiedMillis = System.currentTimeMillis()
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException renaming legacy media URI: $uri", e)
                MediaStoreRenameResult.Failure("Permission denied to rename this file")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error renaming legacy media URI: $uri", e)
                MediaStoreRenameResult.Failure("Unable to rename file: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun buildUpdatedRelativePath(oldRelativePath: String, newDisplayName: String): String {
        val lastSlash = oldRelativePath.lastIndexOf('/')
        return if (lastSlash >= 0) {
            oldRelativePath.substring(0, lastSlash + 1) + newDisplayName
        } else {
            newDisplayName
        }
    }
}

data class MediaStoreDeleteResult(
    val deletedUris: Set<String> = emptySet(),
    val pendingConsentUris: List<Uri> = emptyList(),
    val failedUris: Set<String> = emptySet(),
    val intentSender: IntentSender? = null
)

sealed interface MediaStoreRenameResult {
    data class Success(
        val newUri: Uri,
        val newDisplayName: String,
        val newRelativePath: String,
        val newModifiedMillis: Long
    ) : MediaStoreRenameResult

    data class NeedsConsent(
        val intentSender: IntentSender,
        val targetDisplayName: String
    ) : MediaStoreRenameResult

    data class Failure(
        val errorMessage: String
    ) : MediaStoreRenameResult
}
