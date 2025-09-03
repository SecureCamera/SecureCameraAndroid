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
import com.darkrockstudios.app.securecamera.preferences.AppSettingsDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * A ContentProvider that decrypts and streams images on-demand without writing decrypted data to disk.
 * This provider handles URIs in the format:
 * content://com.darkrockstudios.app.securecamera.decryptingprovider/photos/[photo_name]
 */
class DecryptingImageProvider : ContentProvider(), KoinComponent {

	private val imageManager: SecureImageRepository by inject()
	private val preferencesManager: AppSettingsDataSource by inject()

	@OptIn(ExperimentalUuidApi::class)
	private val uuid = Uuid.random()

	override fun onCreate(): Boolean {
		return true
	}

	override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String> {
		return arrayOf(MIME_TYPE)
	}

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
		if (mode != "r") return null

		val segments = uri.pathSegments
		if (segments.size < 2) return null
		val photoName = segments.last()
		val photoDef = imageManager.getPhotoByName(photoName) ?: return null
		val sanitizeMetadata = runBlocking { preferencesManager.sanitizeMetadata.first() }

		val ropc = ReadOnlyPhotoCallback(photoDef, sanitizeMetadata, imageManager)
		val storage = context!!.getSystemService(StorageManager::class.java)
		return storage.openProxyFileDescriptor(
			ParcelFileDescriptor.MODE_READ_ONLY,
			ropc,
			Handler(Looper.getMainLooper())
		)
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

		val photoName = segments.last()
		val photoDef = imageManager.getPhotoByName(photoName) ?: return null

		val sanitizeName = runBlocking { preferencesManager.sanitizeFileName.first() }
		val sanitizeMetadata = runBlocking { preferencesManager.sanitizeMetadata.first() }

		val size = runBlocking {
			if (sanitizeMetadata)
				stripMetadataInMemory(imageManager.decryptJpg(photoDef)).size
			else
				imageManager.decryptJpg(photoDef).size
		}

		val columnNames = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
		val row = arrayOfNulls<Any>(columnNames.size)
		for (i in columnNames.indices) {
			when (columnNames[i]) {
				OpenableColumns.DISPLAY_NAME -> {
					row[i] = getFileName(photoDef, sanitizeName)
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

	override fun getType(uri: Uri): String = MIME_TYPE

	override fun insert(uri: Uri, values: ContentValues?): Uri? = error("insert Unsupported")
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
		error("delete Unsupported")

	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
		error("update Unsupported")

	@OptIn(ExperimentalUuidApi::class)
	private fun getFileName(photoDef: PhotoDef, sanitizeName: Boolean): String {
		return if (sanitizeName) {
			"image_" + uuid.toHexString() + ".jpg"

		} else {
			photoDef.photoName
		}
	}

	companion object {
		private const val MIME_TYPE = "image/jpeg"
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