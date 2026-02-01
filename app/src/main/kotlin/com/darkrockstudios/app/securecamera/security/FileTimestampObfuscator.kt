package com.darkrockstudios.app.securecamera.security

import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime


/**
 * Utility class for obfuscating filesystem timestamps on created files.
 * This prevents metadata leakage about when photos/videos were actually taken.
 * The real timestamps are stored securely in encrypted sidecar metadata.
 */
class FileTimestampObfuscator {
	/**
	 * Sets the file's various timestamps to a fixed date
	 * @param file The file to obfuscate
	 * @return true if the timestamp was successfully set, false otherwise
	 */
	fun obfuscate(file: File): Boolean {
		return updateFileMetadata(file, OBFUSCATED_TIMESTAMP)
	}

	private fun updateFileMetadata(file: File, creationMillis: Long): Boolean {
		return try {
			val path: Path = file.toPath()
			val view: BasicFileAttributeView = Files.getFileAttributeView(path, BasicFileAttributeView::class.java)
			val creationTime = FileTime.fromMillis(creationMillis)
			view.setTimes(creationTime, creationTime, creationTime)
			true
		} catch (e: IOException) {
			file.setLastModified(OBFUSCATED_TIMESTAMP)
			Timber.e(e, "Failed to update file creation time")
			false
		}
	}

	companion object {
		private const val OBFUSCATED_TIMESTAMP = 973382400000L
	}
}
