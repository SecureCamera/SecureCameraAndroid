package com.darkrockstudios.app.securecamera.encryption

import java.io.File

/**
 * Represents a video encryption job to be processed by the VideoEncryptionService.
 */
data class VideoEncryptionJob(
	val jobId: String,
	val tempFile: File,
	val outputFile: File,
	val createdAt: Long,
	val recordingTimestamp: Long,
	val status: JobStatus = JobStatus.Pending
)

/**
 * Represents the current status of an encryption job.
 */
sealed class JobStatus {
	data object Pending : JobStatus()
	data class InProgress(val progress: Float) : JobStatus()
	data object Completed : JobStatus()
	data class Error(val message: String) : JobStatus()
	data object Cancelled : JobStatus()
}
