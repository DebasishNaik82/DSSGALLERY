package com.example

import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.GalleryRepository
import com.example.data.local.GalleryDatabase
import com.example.data.local.PickedMediaEntity
import com.example.data.mediastore.MediaStoreDataSource
import com.example.model.MediaItem
import com.example.viewmodel.RecycleBinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: GalleryDatabase
    private lateinit var repository: GalleryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        FakeConsentMediaProvider.reset()
        Robolectric.setupContentProvider(FakeConsentMediaProvider::class.java, MediaStore.AUTHORITY)
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val mediaStoreDataSource = MediaStoreDataSource(context)
        repository = GalleryRepository(
            context = context,
            mediaStoreDataSource = mediaStoreDataSource,
            galleryDao = db.galleryDao(),
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @After
    fun tearDown() {
        FakeConsentMediaProvider.reset()
        db.close()
    }

    @Test
    fun testAppName() {
        val appName = context.getString(R.string.app_name)
        assertEquals("DSS Gallery", appName)
    }

    @Test
    fun testAppVersionV1_1() {
        assertEquals("1.1", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)
    }

    @Test
    fun testMediaItemFormatting() {
        val item = MediaItem(
            id = 101L,
            uri = Uri.parse("content://media/external/images/media/101"),
            displayName = "DSCF0842.RAF",
            mimeType = "image/jpeg",
            isVideo = true,
            dateAddedMillis = 1729770000000L,
            dateModifiedMillis = 1729770000000L,
            dateTakenMillis = 1729770000000L,
            sizeBytes = 36_490_444L,
            width = 7728,
            height = 5152,
            durationMillis = 24_000L,
            bucketDisplayName = "Camera",
            isFavorite = true
        )

        assertEquals("39.8 MP", item.megapixelsFormatted)
        assertEquals("7728 × 5152 px", item.resolutionFormatted)
        assertEquals("34.8 MB", item.fileSizeFormatted)
        assertEquals("0:24", item.durationFormatted)
        assertTrue(item.isFavorite)
    }

    @Test
    fun testDeleteFromGalleryMovesToRecycleBinAndRestoreReturnsToGallery(): Unit = runBlocking {
        val tempFile = File.createTempFile("gallery_photo_", ".jpg", context.cacheDir).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val uri = Uri.fromFile(tempFile)
        db.galleryDao().insertPickedMedia(
            listOf(
                PickedMediaEntity(
                    uriString = uri.toString(),
                    mediaId = 501L,
                    displayName = tempFile.name,
                    mimeType = "image/jpeg",
                    isVideo = false,
                    dateAddedMillis = 1000L,
                    sizeBytes = 4L,
                    width = 100,
                    height = 100,
                    durationMillis = 0L
                )
            )
        )

        val initialMedia = repository.allMediaFlow.first()
        assertEquals(1, initialMedia.size)

        // 1. Delete from Gallery -> moves to Recycle Bin without deleting underlying file
        repository.deleteMediaItems(initialMedia)

        assertTrue("Original file must still exist after moving to Recycle Bin", tempFile.exists())
        val afterMoveMedia = repository.allMediaFlow.first()
        assertTrue("Item in Recycle Bin must be hidden from Gallery", afterMoveMedia.isEmpty())

        val recycleBinItems = repository.getRecycleBinFlow().first()
        assertEquals(1, recycleBinItems.size)
        assertEquals(uri.toString(), recycleBinItems.first().uriString)

        // 7. Restore from Recycle Bin -> removes from Recycle Bin and restores to Gallery
        repository.restoreFromRecycleBin(recycleBinItems)

        assertTrue(repository.getRecycleBinFlow().first().isEmpty())
        val restoredMedia = repository.allMediaFlow.first()
        assertEquals(1, restoredMedia.size)
        assertEquals(uri.toString(), restoredMedia.first().uri.toString())

        tempFile.delete()
    }

    @Test
    fun testPermanentDeleteRemovesFileAndDatabaseEntryOnlyOnSuccessAndHandlesPartialFailure(): Unit = runBlocking {
        val deletableFile = File.createTempFile("deletable_", ".jpg", context.cacheDir).apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }
        val deletableUri = Uri.fromFile(deletableFile)
        val failingUri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/999")
        FakeConsentMediaProvider.existingUris.add(failingUri)
        FakeConsentMediaProvider.failingDeleteUris.add(failingUri)

        val itemsToMove = listOf(
            MediaItem(
                id = 601L,
                uri = deletableUri,
                displayName = deletableFile.name,
                mimeType = "image/jpeg",
                isVideo = false,
                dateAddedMillis = 1000L,
                dateModifiedMillis = 1000L,
                dateTakenMillis = 1000L,
                sizeBytes = 3L,
                width = 10,
                height = 10
            ),
            MediaItem(
                id = 602L,
                uri = failingUri,
                displayName = "failed.jpg",
                mimeType = "image/jpeg",
                isVideo = false,
                dateAddedMillis = 1000L,
                dateModifiedMillis = 1000L,
                dateTakenMillis = 1000L,
                sizeBytes = 3L,
                width = 10,
                height = 10
            )
        )

        repository.moveToRecycleBin(itemsToMove)
        val binBefore = repository.getRecycleBinFlow().first()
        assertEquals(2, binBefore.size)

        // Multi-select permanent delete where one succeeds and one fails
        val sender = repository.permanentlyDeleteFromRecycleBin(binBefore)
        assertNull(sender)

        assertFalse("Deletable file should be removed from disk", deletableFile.exists())
        val binAfter = repository.getRecycleBinFlow().first()
        assertEquals("Failed item must remain in Recycle Bin for retry", 1, binAfter.size)
        assertEquals(failingUri.toString(), binAfter.first().uriString)
        assertEquals("Failed to permanently delete", repository.deleteStatusMessage.value)

        // Now allow the failed item to be deleted on retry
        FakeConsentMediaProvider.failingDeleteUris.remove(failingUri)
        val retrySender = repository.permanentlyDeleteFromRecycleBin(binAfter)
        assertNull(retrySender)
        assertTrue("Recycle Bin must be empty after retry succeeds", repository.getRecycleBinFlow().first().isEmpty())
        assertEquals("Permanently deleted successfully", repository.deleteStatusMessage.value)
    }

    @Test
    @Config(sdk = [29])
    fun testConsentFlowCancelKeepsItemAndApproveDeletesItem(): Unit = runBlocking {
        val uri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/777")
        FakeConsentMediaProvider.existingUris.add(uri)
        FakeConsentMediaProvider.requireConsentUris.add(uri)

        val mediaItem = MediaItem(
            id = 777L,
            uri = uri,
            displayName = "consent_photo.jpg",
            mimeType = "image/jpeg",
            isVideo = false,
            dateAddedMillis = 1000L,
            dateModifiedMillis = 1000L,
            dateTakenMillis = 1000L,
            sizeBytes = 1024L,
            width = 100,
            height = 100
        )

        repository.moveToRecycleBin(listOf(mediaItem))
        val binItems = repository.getRecycleBinFlow().first()
        assertEquals(1, binItems.size)

        // Attempt permanent delete -> requires user consent on Android 10 via RecoverableSecurityException
        val intentSender = repository.permanentlyDeleteFromRecycleBin(binItems)
        assertNotNull("IntentSender must be returned when consent is required", intentSender)
        assertNotNull(repository.pendingDeleteIntentSender.value)
        assertEquals("Item must remain in Recycle Bin while awaiting consent", 1, repository.getRecycleBinFlow().first().size)

        // User cancels confirmation -> item stays in Recycle Bin
        val cancelledResult = repository.handleDeleteConsentResult(approved = false)
        assertFalse(cancelledResult)
        assertNull(repository.pendingDeleteIntentSender.value)
        assertEquals("Cancelled deletion must keep item in Recycle Bin", 1, repository.getRecycleBinFlow().first().size)
        assertEquals("Failed to permanently delete", repository.deleteStatusMessage.value)

        // Retry permanent delete and approve confirmation
        val retrySender = repository.permanentlyDeleteFromRecycleBin(repository.getRecycleBinFlow().first())
        assertNotNull(retrySender)
        assertTrue(repository.markConsentDialogLaunched(retrySender!!))
        assertFalse("Duplicate dialog launch for same sender must be prevented", repository.markConsentDialogLaunched(retrySender))

        // Once user approves, provider allows deletion
        FakeConsentMediaProvider.requireConsentUris.remove(uri)
        val approvedResult = repository.handleDeleteConsentResult(approved = true)
        assertTrue(approvedResult)
        assertTrue("Recycle Bin must be empty after approved deletion", repository.getRecycleBinFlow().first().isEmpty())
        assertFalse("Underlying MediaStore row must be deleted", FakeConsentMediaProvider.existingUris.contains(uri))
        assertEquals("Permanently deleted successfully", repository.deleteStatusMessage.value)
    }

    @Test
    @Config(sdk = [29])
    fun testRequiresSystemConsentForNonOwnedMedia(): Unit = runBlocking {
        val uri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/888")
        FakeConsentMediaProvider.existingUris.add(uri)
        FakeConsentMediaProvider.requireConsentUris.add(uri)

        val mediaItem = MediaItem(
            id = 888L,
            uri = uri,
            displayName = "non_owned_photo.jpg",
            mimeType = "image/jpeg",
            isVideo = false,
            dateAddedMillis = 1000L,
            dateModifiedMillis = 1000L,
            dateTakenMillis = 1000L,
            sizeBytes = 1024L,
            width = 100,
            height = 100
        )

        repository.moveToRecycleBin(listOf(mediaItem))
        val binItems = repository.getRecycleBinFlow().first()

        val requiresConsent = repository.requiresSystemConsent(binItems)
        assertTrue("Non-owned MediaStore item must require system consent", requiresConsent)
    }

    class FakeConsentMediaProvider : ContentProvider() {
        companion object {
            val existingUris = mutableSetOf<Uri>()
            val requireConsentUris = mutableSetOf<Uri>()
            val failingDeleteUris = mutableSetOf<Uri>()

            fun reset() {
                existingUris.clear()
                requireConsentUris.clear()
                failingDeleteUris.clear()
            }
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns._ID))
            if (existingUris.contains(uri)) {
                cursor.addRow(arrayOf(777L))
            }
            return cursor
        }

        override fun getType(uri: Uri): String = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            if (failingDeleteUris.contains(uri)) {
                return 0
            }
            if (requireConsentUris.contains(uri)) {
                val context = context ?: ApplicationProvider.getApplicationContext<Context>()
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    777,
                    Intent("com.example.ACTION_CONFIRM_DELETE"),
                    PendingIntent.FLAG_IMMUTABLE
                )
                throw android.app.RecoverableSecurityException(
                    SecurityException("User consent required for $uri"),
                    "Allow DSS Gallery to delete this item?",
                    android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_delete),
                        "Delete",
                        "Delete media",
                        pendingIntent
                    )
                )
            }
            return if (existingUris.remove(uri)) 1 else 0
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}

