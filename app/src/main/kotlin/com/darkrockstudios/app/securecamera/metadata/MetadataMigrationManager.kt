package com.darkrockstudios.app.securecamera.metadata

import android.content.Context
import com.darkrockstudios.app.securecamera.camera.MediaType
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository.Companion.generateRandomFilename
import com.darkrockstudios.app.securecamera.security.FileTimestampObfuscator
import com.darkrockstudios.app.securecamera.security.streaming.SecvFileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles one-time migration from timestamp-based filenames to random UUID filenames.
 *
 * Migration process:
 * 1. Create migration marker file
 * 2. For each legacy-named file:
 *    a. Parse timestamp from filename
 *    b. Generate new random filename
 *    c. Add entry to sidecar
 *    d. Rename file
 *    e. Rename thumbnail if exists
 * 3. Delete migration marker
 *
 * Crash recovery: If marker exists on startup, scan for remaining legacy files and continue.
 */
class MetadataMigrationManager(
	private val appContext: Context,
	private val metadataManager: MetadataManager,
	private val imageRepository: SecureImageRepository,
	private val fileTimestampObfuscator: FileTimestampObfuscator,
) {
	private val photoDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SS", Locale.US)
	private val videoDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

	/**
	 * Returns the migration marker file.
	 */
	private fun getMigrationMarker(): File {
		return File(metadataManager.getMetadataDirectory(), SecmFileFormat.MIGRATION_MARKER_FILENAME)
	}

	/**
	 * Checks if migration is needed.
	 * Returns true if:
	 * - Migration marker exists (crashed during previous migration)
	 * - Any legacy-named photos exist (photo_*.jpg)
	 * - Any legacy-named videos exist (video_*.secv or video_*.mp4)
	 */
	suspend fun needsMigration(): Boolean {
		if (getMigrationMarker().exists()) {
			Timber.d("Migration marker exists - migration incomplete")
			return true
		}

		val photosDir = imageRepository.getGalleryDirectory()
		val videosDir = imageRepository.getVideosDirectory()

		val hasLegacyPhotos = photosDir.listFiles()?.any { file ->
			file.isFile && file.name.startsWith("photo_") && file.name.endsWith(".jpg")
		} ?: false

		val hasLegacyVideos = videosDir.listFiles()?.any { file ->
			file.isFile && file.name.startsWith("video_") &&
					(file.name.endsWith(".${SecvFileFormat.FILE_EXTENSION}") || file.name.endsWith(".mp4"))
		} ?: false

		return hasLegacyPhotos || hasLegacyVideos
	}

	/**
	 * Counts the total number of files that need migration.
	 */
	suspend fun countFilesToMigrate(): Int {
		val photosDir = imageRepository.getGalleryDirectory()
		val videosDir = imageRepository.getVideosDirectory()

		val legacyPhotos = photosDir.listFiles()?.count { file ->
			file.isFile && file.name.startsWith("photo_") && file.name.endsWith(".jpg")
		} ?: 0

		val legacyVideos = videosDir.listFiles()?.count { file ->
			file.isFile && file.name.startsWith("video_") &&
					(file.name.endsWith(".${SecvFileFormat.FILE_EXTENSION}") || file.name.endsWith(".mp4"))
		} ?: 0

		return legacyPhotos + legacyVideos
	}

	/**
	 * Executes the migration process with progress reporting.
	 *
	 * @param onProgress Callback for progress updates (current, total)
	 */
	suspend fun executeMigration(onProgress: suspend (current: Int, total: Int) -> Unit) {
		withContext(Dispatchers.IO) {
			Timber.i("Starting metadata migration")

			// Create marker file
			val marker = getMigrationMarker()
			marker.parentFile?.mkdirs()
			marker.createNewFile()

			// Ensure metadata index is loaded
			metadataManager.loadIndex()

			val photosDir = imageRepository.getGalleryDirectory()
			val videosDir = imageRepository.getVideosDirectory()
			val thumbnailsDir = File(appContext.cacheDir, SecureImageRepository.THUMBNAILS_DIR)

			// Collect all files to migrate
			val legacyPhotos = photosDir.listFiles()?.filter { file ->
				file.isFile && file.name.startsWith("photo_") && file.name.endsWith(".jpg")
			} ?: emptyList()

			val legacyVideos = videosDir.listFiles()?.filter { file ->
				file.isFile && file.name.startsWith("video_") &&
						(file.name.endsWith(".${SecvFileFormat.FILE_EXTENSION}") || file.name.endsWith(".mp4"))
			} ?: emptyList()

			val total = legacyPhotos.size + legacyVideos.size
			var current = 0

			Timber.i("Migrating $total files (${legacyPhotos.size} photos, ${legacyVideos.size} videos)")

			// Migrate photos
			for (file in legacyPhotos) {
				try {
					migratePhoto(file, photosDir, thumbnailsDir)
				} catch (e: Exception) {
					Timber.e(e, "Failed to migrate photo: ${file.name}")
				}
				current++
				onProgress(current, total)
			}

			// Migrate videos
			for (file in legacyVideos) {
				try {
					migrateVideo(file, videosDir, thumbnailsDir)
				} catch (e: Exception) {
					Timber.e(e, "Failed to migrate video: ${file.name}")
				}
				current++
				onProgress(current, total)
			}

			// Delete marker to indicate completion
			marker.delete()

			Timber.i("Migration complete: $current files migrated")
		}
	}

	/**
	 * Migrates a single photo file.
	 */
	private suspend fun migratePhoto(file: File, photosDir: File, thumbnailsDir: File) {
		val oldName = file.name
		val extension = oldName.substringAfterLast('.', "jpg")

		val timestamp = parsePhotoTimestamp(oldName)

		val newName = generateRandomFilename(MediaType.PHOTO, extension)
		val newFile = File(photosDir, newName)

		// Add metadata entry first (atomic write)
		val entry = MediaMetadataEntry(
			filename = newName,
			originalTimestamp = timestamp,
			mediaType = MediaType.PHOTO,
			originalFilename = oldName,
			fileSize = file.length()
		)
		metadataManager.addEntry(entry)

		// Rename the file
		if (!file.renameTo(newFile)) {
			metadataManager.removeEntry(newName)
			error("Failed to rename $oldName to $newName")
		}
		fileTimestampObfuscator.obfuscate(newFile)

		// Rename thumbnail if it exists
		val oldThumbnail = File(thumbnailsDir, oldName)
		if (oldThumbnail.exists()) {
			val newThumbnail = File(thumbnailsDir, newName)
			oldThumbnail.renameTo(newThumbnail)
			fileTimestampObfuscator.obfuscate(newThumbnail)
		}

		Timber.d("Migrated photo: $oldName -> $newName")
	}

	/**
	 * Migrates a single video file.
	 */
	private suspend fun migrateVideo(file: File, videosDir: File, thumbnailsDir: File) {
		val oldName = file.name
		val extension = oldName.substringAfterLast('.', SecvFileFormat.FILE_EXTENSION)

		val timestamp = parseVideoTimestamp(oldName)

		val newName = generateRandomFilename(MediaType.VIDEO, extension)
		val newFile = File(videosDir, newName)

		// Add metadata entry first (atomic write)
		val entry = MediaMetadataEntry(
			filename = newName,
			originalTimestamp = timestamp,
			mediaType = MediaType.VIDEO,
			originalFilename = oldName,
			fileSize = file.length()
		)
		metadataManager.addEntry(entry)

		// Rename the file
		if (!file.renameTo(newFile)) {
			metadataManager.removeEntry(newName)
			error("Failed to rename $oldName to $newName")
		}
		fileTimestampObfuscator.obfuscate(newFile)

		// Rename thumbnail if it exists
		val oldThumbnail = File(thumbnailsDir, "$oldName.thumb")
		if (oldThumbnail.exists()) {
			val newThumbnail = File(thumbnailsDir, "$newName.thumb")
			oldThumbnail.renameTo(newThumbnail)
			fileTimestampObfuscator.obfuscate(newThumbnail)
		}

		Timber.d("Migrated video: $oldName -> $newName")
	}

	/**
	 * Parses timestamp from a legacy photo filename.
	 * Format: photo_yyyyMMdd_HHmmss_SS.jpg
	 */
	private fun parsePhotoTimestamp(filename: String): Long {
		return try {
			val dateString = filename.removePrefix("photo_").removeSuffix(".jpg")
			photoDateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
		} catch (e: ParseException) {
			Timber.w(e, "Failed to parse photo timestamp from: $filename")
			System.currentTimeMillis()
		}
	}

	/**
	 * Parses timestamp from a legacy video filename.
	 * Format: video_yyyyMMdd_HHmmss.secv or video_yyyyMMdd_HHmmss.mp4
	 */
	private fun parseVideoTimestamp(filename: String): Long {
		return try {
			val extension = filename.substringAfterLast('.')
			val dateString = filename.removePrefix("video_").removeSuffix(".$extension")
			videoDateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
		} catch (e: ParseException) {
			Timber.w(e, "Failed to parse video timestamp from: $filename")
			System.currentTimeMillis()
		}
	}
}
