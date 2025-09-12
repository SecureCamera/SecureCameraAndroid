package com.darkrockstudios.app.securecamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.util.Size
import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.convertToPhotoMetadata
import com.ashampoo.kim.model.GpsCoordinates
import com.ashampoo.kim.model.MetadataUpdate
import com.ashampoo.kim.model.TiffOrientation
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.toJavaInstant


class SecureImageRepository(
	private val appContext: Context,
	internal val thumbnailCache: ThumbnailCache,
	private val encryptionScheme: EncryptionScheme,
) {
	fun getGalleryDirectory(): File = File(appContext.filesDir, PHOTOS_DIR)

	fun getDecoyDirectory(): File {
		return File(appContext.filesDir, DECOYS_DIR)
	}

	fun evictKey() = encryptionScheme.evictKey()

	/**
	 * Resets all security-related data when a security failure occurs.
	 * Deletes all images and thumbnails and evicts all in-memory data.
	 */
	fun securityFailureReset() {
		deleteAllImages()
		clearAllThumbnails()
		evictKey()
	}

	/**
	 * Deleted all images that haven't been flagged as benign
	 */
	fun activatePoisonPill() {
		deleteNonDecoyImages()
		clearAllThumbnails()
		evictKey()
	}

	private fun clearAllThumbnails() {
		val thumbnailsDir = getThumbnailsDir()
		if (thumbnailsDir.exists()) {
			thumbnailsDir.deleteRecursively()
		}
		thumbnailCache.clear()
	}

	/**
	 * Derives the encryption key from the user's PIN, then encrypted the plainText bytes and writes it to targetFile
	 */
	internal suspend fun encryptToFile(plain: ByteArray, targetFile: File) {
		encryptionScheme.encryptToFile(plain, targetFile)
	}

	/**
	 * Derives the encryption key from the user's PIN, then decrypts encryptedFile and returns the plainText bytes
	 */
	private suspend fun decryptFile(encryptedFile: File): ByteArray {
		return encryptionScheme.decryptFile(encryptedFile)
	}

	/**
	 * Compresses a bitmap to JPEG format with the specified quality
	 */
	private fun compressBitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
		return ByteArrayOutputStream().use { outputStream ->
			bitmap.compress(CompressFormat.JPEG, quality, outputStream)
			outputStream.toByteArray()
		}
	}

	/**
	 * Encrypts and saves image data to a file, then renames it to the target file
	 */
	private suspend fun encryptAndSaveImage(imageBytes: ByteArray, tempFile: File, targetFile: File) {
		tempFile.delete()
		targetFile.delete()

		encryptionScheme.encryptToFile(
			plain = imageBytes,
			targetFile = tempFile,
		)

		tempFile.renameTo(targetFile)
	}

	/**
	 * Processes an image with metadata and prepares it for saving
	 */
	private fun processImageWithMetadata(
		bitmap: Bitmap,
		sourceJpgBytes: ByteArray,
		quality: Int
	): ByteArray {
		val newJpgBytes = compressBitmapToJpeg(bitmap, quality)
		var updatedBytes = newJpgBytes

		Kim.readMetadata(sourceJpgBytes)?.convertToPhotoMetadata()?.let { photoMetadata ->
			if (photoMetadata.takenDate != null) {
				updatedBytes = Kim.update(
					bytes = updatedBytes,
					MetadataUpdate.TakenDate(photoMetadata.takenDate!!)
				)
			}

			if (photoMetadata.orientation != null) {
				updatedBytes =
					Kim.update(
						bytes = updatedBytes,
						MetadataUpdate.Orientation(photoMetadata.orientation!!)
					)
			}

			if (photoMetadata.gpsCoordinates != null) {
				updatedBytes =
					Kim.update(
						bytes = updatedBytes,
						MetadataUpdate.GpsCoordinates(photoMetadata.gpsCoordinates!!)
					)
			}
		}

		return updatedBytes
	}

	/**
	 * Applies specific metadata to an image for the saveImage function
	 */
	private fun applyImageMetadata(
		imageBytes: ByteArray,
		latLng: GpsCoordinates?,
		applyRotation: Boolean,
		rotationDegrees: Int
	): ByteArray {
		val dateUpdate: MetadataUpdate = MetadataUpdate.TakenDate(System.currentTimeMillis())
		var updatedBytes = Kim.update(bytes = imageBytes, dateUpdate)

		if (applyRotation) {
			updatedBytes = Kim.update(bytes = updatedBytes, MetadataUpdate.Orientation(TiffOrientation.STANDARD))
		} else {
			val tiffOrientation = calculateTiffOrientation(rotationDegrees)
			val orientationUpdate: MetadataUpdate = MetadataUpdate.Orientation(tiffOrientation)
			updatedBytes = Kim.update(bytes = updatedBytes, orientationUpdate)
		}

		if (latLng != null) {
			val gpsUpdate: MetadataUpdate = MetadataUpdate.GpsCoordinates(latLng)
			updatedBytes = Kim.update(bytes = updatedBytes, gpsUpdate)
		}

		return updatedBytes
	}

	suspend fun saveImage(
		image: CapturedImage,
		latLng: GpsCoordinates?,
		applyRotation: Boolean,
		quality: Int = 90,
	): File {
		val dir = getGalleryDirectory()

		if (!dir.exists()) {
			dir.mkdirs()
		}

		val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SS", Locale.US)
		val finalImageName: String = "photo_" + dateFormat.format(Date.from(image.timestamp.toJavaInstant())) + ".jpg"

		val photoFile = File(dir, finalImageName)
		val tempFile = File(dir, "$finalImageName.tmp")

		var rawSensorBitmap = image.sensorBitmap
		if (applyRotation) {
			rawSensorBitmap = rawSensorBitmap.rotate(image.rotationDegrees)
		}

		val jpgBytes = compressBitmapToJpeg(rawSensorBitmap, quality)
		val updatedBytes =
			applyImageMetadata(jpgBytes, latLng, applyRotation, image.rotationDegrees)
		encryptAndSaveImage(updatedBytes, tempFile, photoFile)

		return photoFile
	}

	suspend fun updateImage(
		bitmap: Bitmap,
		photoDef: PhotoDef,
		quality: Int = 90
	): PhotoDef {
		val jpgBytes = decryptJpg(photoDef)
		val updatedBytes = processImageWithMetadata(bitmap, jpgBytes, quality)

		val dir = getGalleryDirectory()
		val tempFile = File(dir, "${photoDef.photoName}.tmp")

		encryptAndSaveImage(updatedBytes, tempFile, photoDef.photoFile)

		thumbnailCache.evictThumbnail(photoDef)
		getThumbnail(photoDef).delete()

		return photoDef
	}

	suspend fun saveImageCopy(
		bitmap: Bitmap,
		photoDef: PhotoDef,
		quality: Int = 90
	): PhotoDef {
		val jpgBytes = decryptJpg(photoDef)
		val updatedBytes = processImageWithMetadata(bitmap, jpgBytes, quality)

		val dir = getGalleryDirectory()
		val newImageName = generateCopyName(dir, photoDef.photoName)
		val newPhotoFile = File(dir, newImageName)
		val tempFile = File(dir, "$newImageName.tmp")

		encryptAndSaveImage(updatedBytes, tempFile, newPhotoFile)

		val newPhotoDef = PhotoDef(
			photoName = newImageName,
			photoFormat = "jpg",
			photoFile = newPhotoFile
		)

		return newPhotoDef
	}

	suspend fun readImage(photo: PhotoDef): Bitmap {
		val plainBytes = decryptFile(
			encryptedFile = photo.photoFile,
		)
		return BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
	}

	suspend fun decryptJpg(
		photo: PhotoDef,
	): ByteArray {
		val plainBytes = decryptFile(
			encryptedFile = photo.photoFile,
		)
		return plainBytes
	}

	private fun getThumbnailsDir(): File {
		val thumbnailsDir = File(appContext.cacheDir, THUMBNAILS_DIR)
		thumbnailsDir.mkdirs()
		return thumbnailsDir
	}

	internal fun getThumbnail(photoDef: PhotoDef): File {
		val dir = getThumbnailsDir()
		return File(dir, photoDef.photoName)
	}

	suspend fun readThumbnail(photo: PhotoDef): Bitmap? {
		thumbnailCache.getThumbnail(photo)?.let { return it }

		val thumbFile = getThumbnail(photo)

		val thumbnailBitmap = if (thumbFile.exists()) {
			// Decrypt the thumbnail file, not the full image
			val plainBytes = decryptFile(
				encryptedFile = thumbFile,
			)
			BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
		} else if (photo.photoFile.exists().not()) {
			Timber.w("Photo no longer exists! ${photo.photoName}")
			null
		} else {
			// Create thumbnail from the full image
			val plainBytes = decryptFile(
				encryptedFile = photo.photoFile,
			)

			val options = BitmapFactory.Options().apply {
				inSampleSize = 4
			}

			val thumbnailBitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size, options)
			val thumbnailBytes = thumbnailBitmap.let { bitmap ->
				ByteArrayOutputStream().use { outputStream ->
					bitmap.compress(CompressFormat.JPEG, 75, outputStream)
					outputStream.toByteArray()
				}
			}
			encryptionScheme.encryptToFile(
				plain = thumbnailBytes,
				targetFile = thumbFile,
			)

			thumbnailBitmap
		}

		if (thumbnailBitmap != null) {
			thumbnailCache.putThumbnail(photo, thumbnailBitmap)
		}

		return thumbnailBitmap
	}

	fun getPhotos(): List<PhotoDef> {
		val dir = getGalleryDirectory()
		if (!dir.exists()) {
			return emptyList()
		}

		return dir.listFiles()
			?.filter { it.isFile }
			?.map { file ->
				val name = file.name
				val format = name.substringAfterLast('.', "jpg")
				PhotoDef(
					photoName = name,
					photoFormat = format,
					photoFile = file
				)
			} ?: emptyList()
	}

	fun deleteImage(photoDef: PhotoDef, deleteDecoy: Boolean = true): Boolean {
		thumbnailCache.evictThumbnail(photoDef)
		if (deleteDecoy && isDecoyPhoto(photoDef)) {
			getDecoyFile(photoDef).delete()
		}

		return if (photoDef.photoFile.exists()) {
			getThumbnail(photoDef).delete()
			photoDef.photoFile.delete()
		} else {
			false
		}
	}

	fun deleteImages(photos: List<PhotoDef>, deleteDecoy: Boolean = true): Boolean {
		return photos.map { deleteImage(it, deleteDecoy) }.all { it }
	}

	fun deleteAllImages(deleteDecoy: Boolean = true) {
		val photos = getPhotos()
		deleteImages(photos, deleteDecoy)
	}

	fun deleteNonDecoyImages() {
		val galleryDir = getGalleryDirectory()
		val thumbnailsDir = getThumbnailsDir()

		galleryDir.deleteRecursively()
		thumbnailsDir.deleteRecursively()

		galleryDir.mkdirs()
		getThumbnailsDir().mkdirs()

		getDecoyFiles().forEach { file ->
			val targetFile = File(galleryDir, file.name)
			file.renameTo(targetFile)
		}
		getDecoyDirectory().deleteRecursively()
	}

	fun getPhotoByName(photoName: String): PhotoDef? {
		val dir = getGalleryDirectory()
		if (!dir.exists()) {
			return null
		}

		val photoFile = File(dir, photoName)
		if (!photoFile.exists() || !photoFile.isFile) {
			return null
		}

		val format = photoName.substringAfterLast('.', "jpg")
		return PhotoDef(
			photoName = photoName,
			photoFormat = format,
			photoFile = photoFile
		)
	}

	suspend fun getPhotoMetaData(photoDef: PhotoDef): PhotoMetaData {
		val name = photoDef.photoName
		val dateTaken = photoDef.dateTaken()

		var orientation: TiffOrientation? = null
		var coords: GpsCoordinates? = null
		var size = Size(0, 0)

		val jpgBytes = decryptJpg(photoDef)
		Kim.readMetadata(jpgBytes)?.convertToPhotoMetadata()?.let { imageMetadata ->
			orientation = imageMetadata.orientation
			coords = imageMetadata.gpsCoordinates
			size = Size(
				imageMetadata.widthPx ?: 0,
				imageMetadata.heightPx ?: 0,
			)
		}

		return PhotoMetaData(
			name = name,
			resolution = size,
			dateTaken = dateTaken,
			location = coords,
			orientation = orientation,
		)
	}

	fun isDecoyPhoto(photoDef: PhotoDef): Boolean = getDecoyFile(photoDef).exists()

	internal fun getDecoyFile(photoDef: PhotoDef): File {
		return File(getDecoyDirectory(), photoDef.photoName)
	}

	private fun getDecoyFiles(): List<File> {
		val dir = getDecoyDirectory()
		if (!dir.exists()) {
			return emptyList()
		}

		return dir.listFiles()?.filter { it.isFile && it.name.endsWith("jpg") } ?: emptyList()
	}

	fun numDecoys(): Int = getDecoyFiles().count()

	suspend fun addDecoyPhotoWithKey(photoDef: PhotoDef, keyBytes: ByteArray): Boolean {
		return if (numDecoys() < MAX_DECOY_PHOTOS) {
			val jpgBytes = decryptJpg(photoDef)
			getDecoyDirectory().mkdirs()
			val decoyFile = getDecoyFile(photoDef)

			encryptionScheme.encryptToFile(
				plain = jpgBytes,
				keyBytes = keyBytes,
				targetFile = decoyFile
			)

			true
		} else {
			false
		}
	}

	suspend fun removeDecoyPhoto(photoDef: PhotoDef): Boolean {
		return getDecoyFile(photoDef).delete()
	}

	suspend fun removeAllDecoyPhotos() {
		getDecoyFiles().forEach { file ->
			file.delete()
		}
	}

	companion object {
		const val PHOTOS_DIR = "photos"
		const val DECOYS_DIR = "decoys"
		const val THUMBNAILS_DIR = ".thumbnails"
		const val MAX_DECOY_PHOTOS = 10

        internal fun generateCopyName(dir: File, originalName: String): String {
            val base = originalName.substringBeforeLast(".")
            val ext = originalName.substringAfterLast('.', "jpg")
            var candidate = "${base}_cp.${ext}"
            var i = 1
            while (File(dir, candidate).exists()) {
                candidate = "${base}_cp${i}.${ext}"
                i++
            }
            return candidate
        }
	}
}
