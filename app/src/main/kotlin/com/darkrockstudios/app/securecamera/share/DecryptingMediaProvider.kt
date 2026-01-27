package com.darkrockstudios.app.securecamera.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.camera.VideoDef
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import com.darkrockstudios.app.securecamera.security.streaming.StreamingDecryptor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * A ContentProvider that decrypts and streams photos and videos on-demand without writing decrypted data to disk.
 * This provider handles URIs in the format:
 * - content://com.darkrockstudios.app.securecamera.decryptingprovider/photos/[photo_name]
 * - content://com.darkrockstudios.app.securecamera.decryptingprovider/videos/[video_name]
 */
class DecryptingMediaProvider : ContentProvider(), KoinComponent {

	private val imageManager: SecureImageRepository by inject()
	private val preferencesManager: AppSettingsDataSource by inject()

	@OptIn(ExperimentalUuidApi::class)
	private val uuid = Uuid.random()

	override fun onCreate(): Boolean {
		return true
	}

	companion object {
		const val PATH_PHOTOS = "photos"
		const val PATH_VIDEOS = "videos"
	}

	override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String> {
		val segments = uri.pathSegments
		if (segments.size < 2) return emptyArray()

		return when (segments[0]) {
			PATH_PHOTOS -> arrayOf("image/jpeg")
			PATH_VIDEOS -> arrayOf("video/mp4")
			else -> emptyArray()
		}
	}

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
		if (mode != "r") return null

		val segments = uri.pathSegments
		if (segments.size < 2) return null

		val mediaType = segments[0]  // "photos" or "videos"
		val mediaName = segments.last()

		val storage = context!!.getSystemService(StorageManager::class.java)

		return when (mediaType) {
			PATH_PHOTOS -> {
				val photoDef = imageManager.getPhotoByName(mediaName) ?: return null
				val sanitizeMetadata = runBlocking { preferencesManager.sanitizeMetadata.first() }
				val ropc = ReadOnlyPhotoCallback(photoDef, sanitizeMetadata, imageManager)
				storage.openProxyFileDescriptor(
					ParcelFileDescriptor.MODE_READ_ONLY,
					ropc,
					Handler(Looper.getMainLooper())
				)
			}
			PATH_VIDEOS -> {
				val videoDef = imageManager.getVideoByName(mediaName) ?: return null
				val rovc = ReadOnlyVideoCallback(videoDef, imageManager)
				storage.openProxyFileDescriptor(
					ParcelFileDescriptor.MODE_READ_ONLY,
					rovc,
					Handler(Looper.getMainLooper())
				)
			}
			else -> null
		}
	}

	/**
	 * Handles the query method to provide additional metadata about the file
	 * This allows us to set a sanitized filename when the file is shared
	 */
	@OptIn(ExperimentalStdlibApi::class)
	override fun query(
		uri: Uri,
		projection: Array<out String>?,
		selection: String?,
		selectionArgs: Array<out String>?,
		sortOrder: String?
	): Cursor? {
		val segments = uri.pathSegments
		if (segments.size < 2) return null

		val mediaType = segments[0]
		val mediaName = segments.last()

		val sanitizeName = runBlocking { preferencesManager.sanitizeFileName.first() }

		val (displayName, size) = when (mediaType) {
			PATH_PHOTOS -> {
				val photoDef = imageManager.getPhotoByName(mediaName) ?: return null
				val sanitizeMetadata = runBlocking { preferencesManager.sanitizeMetadata.first() }
				val photoSize = runBlocking {
					if (sanitizeMetadata)
						stripMetadataInMemory(imageManager.decryptJpg(photoDef)).size
					else
						imageManager.decryptJpg(photoDef).size
				}
				Pair(getPhotoFileName(photoDef, sanitizeName), photoSize)
			}
			PATH_VIDEOS -> {
				val videoDef = imageManager.getVideoByName(mediaName) ?: return null
				val videoSize = runBlocking {
					val scheme = imageManager.getStreamingEncryptionScheme()!!
					val decryptor = scheme.createStreamingDecryptor(videoDef.videoFile)
					val size = decryptor.totalSize
					decryptor.close()
					size
				}
				Pair(getVideoFileName(videoDef, sanitizeName), videoSize)
			}
			else -> return null
		}

		val columnNames = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
		val row = arrayOfNulls<Any>(columnNames.size)
		for (i in columnNames.indices) {
			when (columnNames[i]) {
				OpenableColumns.DISPLAY_NAME -> {
					row[i] = displayName
				}

				OpenableColumns.SIZE -> {
					row[i] = size
				}
			}
		}

		val cursor = MatrixCursor(columnNames, 1)
		cursor.addRow(row)
		return cursor
	}

	override fun getType(uri: Uri): String {
		val segments = uri.pathSegments
		if (segments.size < 2) return "application/octet-stream"

		return when (segments[0]) {
			PATH_PHOTOS -> "image/jpeg"
			PATH_VIDEOS -> "video/mp4"
			else -> "application/octet-stream"
		}
	}

	override fun insert(uri: Uri, values: ContentValues?): Uri? = error("insert Unsupported")
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
		error("delete Unsupported")

	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
		error("update Unsupported")

	@OptIn(ExperimentalUuidApi::class)
	private fun getPhotoFileName(photoDef: PhotoDef, sanitizeName: Boolean): String {
		return if (sanitizeName) {
			"image_" + uuid.toHexString() + ".jpg"
		} else {
			photoDef.photoName
		}
	}

	@OptIn(ExperimentalUuidApi::class)
	private fun getVideoFileName(videoDef: VideoDef, sanitizeName: Boolean): String {
		return if (sanitizeName) {
			"video_" + uuid.toHexString() + ".mp4"
		} else {
			videoDef.videoName
		}
	}
}

private class ReadOnlyPhotoCallback(
	private val photoDef: PhotoDef,
	private val sanitizeMetadata: Boolean,
	private val imageManager: SecureImageRepository,
) : ProxyFileDescriptorCallback() {

	private val decryptedBytes: ByteArray by lazy {
		if (sanitizeMetadata) {
			val bytes = runBlocking { imageManager.decryptJpg(photoDef) }
			stripMetadataInMemory(bytes)
		} else {
			runBlocking { imageManager.decryptJpg(photoDef) }
		}
	}

	override fun onGetSize(): Long = decryptedBytes.size.toLong()

	override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
		if (offset >= decryptedBytes.size) return 0
		val actually = minOf(size, decryptedBytes.size - offset.toInt(), data.size)
		System.arraycopy(decryptedBytes, offset.toInt(), data, 0, actually)
		return actually
	}

	override fun onRelease() {
		decryptedBytes.fill(0)
	}
}

private class ReadOnlyVideoCallback(
	private val videoDef: VideoDef,
	private val imageManager: SecureImageRepository,
) : ProxyFileDescriptorCallback() {

	private val decryptor: StreamingDecryptor = runBlocking {
		val scheme = imageManager.getStreamingEncryptionScheme()!!
		scheme.createStreamingDecryptor(videoDef.videoFile)
	}

	override fun onGetSize(): Long = decryptor.totalSize

	override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
		if (offset >= decryptor.totalSize) return 0
		return runBlocking {
			decryptor.read(offset, data, 0, size)
		}
	}

	override fun onRelease() {
		decryptor.close()
	}
}

private fun stripMetadataInMemory(imageData: ByteArray): ByteArray {
	val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
	return if (bitmap != null) {
		ByteArrayOutputStream().use { outputStream ->
			bitmap.compress(CompressFormat.JPEG, 90, outputStream)
			outputStream.toByteArray()
		}
	} else {
		error("Failed to strip metadata in memory")
	}
}