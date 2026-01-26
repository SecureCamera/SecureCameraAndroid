package com.darkrockstudios.app.securecamera.encryption

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.darkrockstudios.app.securecamera.MainActivity
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import com.darkrockstudios.app.securecamera.security.streaming.SecvFileFormat
import com.darkrockstudios.app.securecamera.security.streaming.VideoEncryptionHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ForegroundService that handles video encryption in the background.
 * Processes encryption jobs sequentially with progress notifications.
 */
class VideoEncryptionService : Service() {

	private val encryptionScheme: EncryptionScheme by inject()

	private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private var encryptionJob: Job? = null
	private var videoEncryptionHelper: VideoEncryptionHelper? = null

	private val jobQueue = ConcurrentLinkedQueue<VideoEncryptionJob>()
	private var currentJob: VideoEncryptionJob? = null
	private val isCancelled = AtomicBoolean(false)
	private val isAllCancelled = AtomicBoolean(false)

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "video_encryption_channel"
		const val NOTIFICATION_ID = 3

		private const val ACTION_ENQUEUE = "com.darkrockstudios.app.securecamera.action.ENQUEUE_ENCRYPTION"
		private const val ACTION_CANCEL_CURRENT = "com.darkrockstudios.app.securecamera.action.CANCEL_CURRENT"
		private const val ACTION_CANCEL_ALL = "com.darkrockstudios.app.securecamera.action.CANCEL_ALL"

		private const val EXTRA_TEMP_FILE = "extra_temp_file"
		private const val EXTRA_OUTPUT_FILE = "extra_output_file"
		private const val EXTRA_JOB_ID = "extra_job_id"

		/**
		 * Observable state of all encryption jobs.
		 * Map of output file name to progress (0.0 to 1.0, or null if queued/starting)
		 */
		private val _encryptionState = MutableStateFlow<Map<String, Float?>>(emptyMap())
		val encryptionState: StateFlow<Map<String, Float?>> = _encryptionState.asStateFlow()

		/**
		 * Check if a specific file is currently being encrypted.
		 */
		fun isEncrypting(fileName: String): Boolean = _encryptionState.value.containsKey(fileName)

		/**
		 * Get the encryption progress for a specific file (0.0 to 1.0, null if queued).
		 */
		fun getProgress(fileName: String): Float? = _encryptionState.value[fileName]

		private fun updateProgress(fileName: String, progress: Float?) {
			_encryptionState.value = _encryptionState.value.toMutableMap().apply {
				put(fileName, progress)
			}
		}

		private fun removeFromState(fileName: String) {
			_encryptionState.value = _encryptionState.value.toMutableMap().apply {
				remove(fileName)
			}
		}

		private fun clearState() {
			_encryptionState.value = emptyMap()
		}

		/**
		 * Enqueue a video file for encryption.
		 */
		fun enqueueEncryption(context: Context, tempFile: File, outputFile: File) {
			val jobId = UUID.randomUUID().toString()
			// Add to state immediately as "queued" (null progress)
			updateProgress(outputFile.name, null)
			val intent = Intent(context, VideoEncryptionService::class.java).apply {
				action = ACTION_ENQUEUE
				putExtra(EXTRA_TEMP_FILE, tempFile.absolutePath)
				putExtra(EXTRA_OUTPUT_FILE, outputFile.absolutePath)
				putExtra(EXTRA_JOB_ID, jobId)
			}
			context.startService(intent)
		}

		/**
		 * Cancel the current encryption job.
		 */
		fun cancelCurrentJob(context: Context) {
			val intent = Intent(context, VideoEncryptionService::class.java).apply {
				action = ACTION_CANCEL_CURRENT
			}
			context.startService(intent)
		}

		/**
		 * Cancel all encryption jobs (current and queued).
		 */
		fun cancelAllJobs(context: Context) {
			val intent = Intent(context, VideoEncryptionService::class.java).apply {
				action = ACTION_CANCEL_ALL
			}
			context.startService(intent)
		}

		/**
		 * Recover stranded temp files that were left behind due to a crash.
		 * This should be called after user authentication to ensure any unencrypted
		 * video files are immediately encrypted.
		 *
		 * Handles:
		 * - temp_*.mp4 files (unencrypted recordings)
		 * - *.secv.encrypting files (partial encryptions - deleted)
		 */
		fun recoverStrandedFiles(context: Context) {
			val videosDir = File(context.filesDir, "videos")
			if (!videosDir.exists()) {
				Timber.d("Videos directory doesn't exist, nothing to recover")
				return
			}

			// Find and delete any partial .encrypting files
			val encryptingFiles = videosDir.listFiles { file ->
				file.name.endsWith(VideoEncryptionHelper.ENCRYPTING_SUFFIX)
			} ?: emptyArray()

			encryptingFiles.forEach { file ->
				Timber.w("Deleting partial encryption file: ${file.name}")
				file.delete()
			}

			// Find stranded temp files
			val tempFiles = videosDir.listFiles { file ->
				file.name.startsWith("temp_") && file.name.endsWith(".mp4")
			} ?: emptyArray()

			if (tempFiles.isEmpty()) {
				Timber.d("No stranded temp files found")
				return
			}

			Timber.w("Found ${tempFiles.size} stranded temp file(s), recovering...")

			// Get set of files currently being processed
			val currentlyProcessing = _encryptionState.value.keys

			tempFiles.forEach { tempFile ->
				// Derive the expected output file name
				val outputName = tempFile.name
					.replace("temp_", "video_")
					.replace(".mp4", ".${SecvFileFormat.FILE_EXTENSION}")
				val outputFile = File(videosDir, outputName)

				// Skip if already being processed
				if (currentlyProcessing.contains(outputName)) {
					Timber.d("Skipping ${tempFile.name} - already being processed")
					return@forEach
				}

				// Delete any existing partial output (shouldn't exist if .encrypting is used, but safety check)
				if (outputFile.exists()) {
					Timber.w("Deleting existing partial output: ${outputFile.name}")
					outputFile.delete()
				}

				Timber.i("Recovering stranded video: ${tempFile.name} -> ${outputFile.name}")
				enqueueEncryption(context, tempFile, outputFile)
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		Timber.d("VideoEncryptionService created")
		createNotificationChannel()
		initializeEncryptionHelper()
	}

	private fun initializeEncryptionHelper() {
		val streamingScheme = encryptionScheme.getStreamingCapability()
		if (streamingScheme != null) {
			videoEncryptionHelper = VideoEncryptionHelper(streamingScheme)
		} else {
			Timber.e("Streaming encryption not available")
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Timber.d("VideoEncryptionService onStartCommand: ${intent?.action}")

		when (intent?.action) {
			ACTION_ENQUEUE -> {
				val tempFilePath = intent.getStringExtra(EXTRA_TEMP_FILE)
				val outputFilePath = intent.getStringExtra(EXTRA_OUTPUT_FILE)
				val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString()

				if (tempFilePath != null && outputFilePath != null) {
					val job = VideoEncryptionJob(
						jobId = jobId,
						tempFile = File(tempFilePath),
						outputFile = File(outputFilePath),
						createdAt = System.currentTimeMillis()
					)
					enqueueJob(job)
				}
			}

			ACTION_CANCEL_CURRENT -> {
				cancelCurrentEncryption()
			}

			ACTION_CANCEL_ALL -> {
				cancelAllEncryption()
			}
		}

		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onDestroy() {
		Timber.d("VideoEncryptionService destroyed")
		serviceScope.cancel()
		dismissNotification()
		super.onDestroy()
	}

	private fun enqueueJob(job: VideoEncryptionJob) {
		jobQueue.offer(job)
		Timber.d("Job enqueued: ${job.jobId}, queue size: ${jobQueue.size}")

		// Start foreground immediately
		startForeground(NOTIFICATION_ID, createStartingNotification())

		// Start processing if not already running
		if (encryptionJob == null || encryptionJob?.isActive != true) {
			processNextJob()
		} else {
			// Update notification to show queue count
			updateNotificationForQueue()
		}
	}

	private fun processNextJob() {
		val job = jobQueue.poll()
		if (job == null) {
			Timber.d("No more jobs in queue, stopping service")
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
			return
		}

		currentJob = job
		isCancelled.set(false)

		encryptionJob = serviceScope.launch {
			try {
				processEncryptionJob(job)
			} catch (e: CancellationException) {
				Timber.d("Encryption job cancelled: ${job.jobId}")
				handleJobCancelled(job)
			} catch (e: Exception) {
				Timber.e(e, "Error processing encryption job: ${job.jobId}")
				handleJobError(job, e.message ?: "Unknown error")
			} finally {
				currentJob = null
				// Process next job unless all cancelled
				if (!isAllCancelled.get()) {
					processNextJob()
				} else {
					isAllCancelled.set(false)
					clearQueueAndStop()
				}
			}
		}
	}

	private suspend fun processEncryptionJob(job: VideoEncryptionJob) {
		val helper = videoEncryptionHelper
		if (helper == null) {
			Timber.e("Video encryption helper not available")
			handleJobError(job, "Encryption not available")
			return
		}

		Timber.i("Starting encryption for job: ${job.jobId}")
		updateNotificationProgress(0f)
		updateProgress(job.outputFile.name, 0f)

		// Flag to prevent progress updates after encryption completes
		val isComplete = AtomicBoolean(false)

		// Launch a coroutine to observe progress and update notification + state
		val progressObserver = serviceScope.launch {
			helper.encryptionProgress.collectLatest { progress ->
				// Check completion flag to prevent race condition where a buffered
				// progress update could re-add the file to state after removal
				if (isComplete.get()) return@collectLatest

				when (progress) {
					is VideoEncryptionHelper.EncryptionProgress.InProgress -> {
						updateProgress(job.outputFile.name, progress.progress)
						withContext(Dispatchers.Main) {
							updateNotificationProgress(progress.progress)
						}
					}

					else -> { /* Handled below */
					}
				}
			}
		}

		try {
			val success = helper.encryptVideoFile(
				tempFile = job.tempFile,
				outputFile = job.outputFile,
				isCancelled = { isCancelled.get() }
			)

			// Set completion flag BEFORE handling completion to prevent race condition
			isComplete.set(true)
			progressObserver.cancel()

			if (isCancelled.get()) {
				handleJobCancelled(job)
			} else if (success) {
				handleJobCompleted(job)
			} else {
				handleJobError(job, "Encryption failed")
			}
		} finally {
			isComplete.set(true)
			progressObserver.cancel()
			helper.resetProgress()
		}
	}

	private fun handleJobCompleted(job: VideoEncryptionJob) {
		Timber.i("Encryption completed for job: ${job.jobId}")
		// Remove from encryption state
		removeFromState(job.outputFile.name)
		// Delete temp file
		if (job.tempFile.exists()) {
			job.tempFile.delete()
		}
	}

	private fun handleJobCancelled(job: VideoEncryptionJob) {
		Timber.d("Handling cancelled job: ${job.jobId}")
		// Remove from encryption state
		removeFromState(job.outputFile.name)
		// Delete partial output file
		if (job.outputFile.exists()) {
			job.outputFile.delete()
		}
		// Delete temp file
		if (job.tempFile.exists()) {
			job.tempFile.delete()
		}
		showCancelledNotification()
	}

	private fun handleJobError(job: VideoEncryptionJob, message: String) {
		Timber.e("Encryption error for job ${job.jobId}: $message")
		// Remove from encryption state
		removeFromState(job.outputFile.name)
		// Delete partial output file
		if (job.outputFile.exists()) {
			job.outputFile.delete()
		}
		// Keep temp file for potential retry
		showErrorNotification(message)
	}

	private fun cancelCurrentEncryption() {
		Timber.d("Cancelling current encryption")
		isCancelled.set(true)
		encryptionJob?.cancel()
	}

	private fun cancelAllEncryption() {
		Timber.d("Cancelling all encryption jobs")
		isAllCancelled.set(true)
		isCancelled.set(true)
		encryptionJob?.cancel()
	}

	private fun clearQueueAndStop() {
		// Clean up all queued jobs
		while (jobQueue.isNotEmpty()) {
			val job = jobQueue.poll()
			job?.let {
				removeFromState(it.outputFile.name)
				if (it.tempFile.exists()) {
					it.tempFile.delete()
				}
			}
		}
		// Clear any remaining state
		clearState()
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	// region Notifications

	private fun createNotificationChannel() {
		val name = getString(R.string.encryption_channel_name)
		val description = getString(R.string.encryption_channel_description)
		val importance = NotificationManager.IMPORTANCE_LOW
		val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
			this.description = description
		}

		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.createNotificationChannel(channel)
	}

	private fun createStartingNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
		.setSmallIcon(android.R.drawable.ic_menu_upload)
		.setContentTitle(getString(R.string.encryption_notification_title))
		.setContentText(getString(R.string.encryption_notification_preparing))
		.setProgress(100, 0, true)
		.setOngoing(true)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.setContentIntent(createContentPendingIntent())
		.addAction(createCancelAction())
		.build()

	private fun updateNotificationProgress(progress: Float) {
		val queueSize = jobQueue.size + 1 // +1 for current job
		val progressPercent = (progress * 100).toInt()

		val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_menu_upload)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setContentIntent(createContentPendingIntent())

		if (queueSize > 1) {
			val currentIndex = queueSize - jobQueue.size
			builder.setContentTitle(getString(R.string.encryption_notification_title_plural))
			builder.setContentText(
				getString(
					R.string.encryption_notification_queue,
					currentIndex,
					queueSize,
					progressPercent
				)
			)
			builder.addAction(createCancelAllAction())
		} else {
			builder.setContentTitle(getString(R.string.encryption_notification_title))
			builder.setContentText(getString(R.string.encryption_notification_progress, progressPercent))
			builder.addAction(createCancelAction())
		}

		builder.setProgress(100, progressPercent, false)

		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(NOTIFICATION_ID, builder.build())
	}

	private fun updateNotificationForQueue() {
		val queueSize = jobQueue.size + 1 // +1 for current job
		if (queueSize > 1) {
			val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(android.R.drawable.ic_menu_upload)
				.setContentTitle(getString(R.string.encryption_notification_title_plural))
				.setContentText(getString(R.string.encryption_notification_queue, 1, queueSize, 0))
				.setProgress(100, 0, true)
				.setOngoing(true)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setContentIntent(createContentPendingIntent())
				.addAction(createCancelAllAction())

			val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
			notificationManager.notify(NOTIFICATION_ID, builder.build())
		}
	}

	private fun showErrorNotification(message: String) {
		val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_dialog_alert)
			.setContentTitle(getString(R.string.encryption_error_title))
			.setContentText(message)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setAutoCancel(true)
			.setContentIntent(createContentPendingIntent())

		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(NOTIFICATION_ID + 101, builder.build())
	}

	private fun showCancelledNotification() {
		val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
			.setContentTitle(getString(R.string.encryption_cancelled_title))
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setAutoCancel(true)
			.setContentIntent(createContentPendingIntent())

		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(NOTIFICATION_ID + 102, builder.build())
	}

	private fun dismissNotification() {
		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel(NOTIFICATION_ID)
	}

	private fun createContentPendingIntent() = PendingIntent.getActivity(
		this,
		0,
		Intent(this, MainActivity::class.java),
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
	)

	private fun createCancelAction(): NotificationCompat.Action {
		val cancelIntent = Intent(this, VideoEncryptionCancelReceiver::class.java).apply {
			action = VideoEncryptionCancelReceiver.ACTION_CANCEL_ENCRYPTION
		}
		val pendingIntent = PendingIntent.getBroadcast(
			this,
			0,
			cancelIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		return NotificationCompat.Action.Builder(
			android.R.drawable.ic_menu_close_clear_cancel,
			getString(R.string.encryption_action_cancel),
			pendingIntent
		).build()
	}

	private fun createCancelAllAction(): NotificationCompat.Action {
		val cancelIntent = Intent(this, VideoEncryptionCancelReceiver::class.java).apply {
			action = VideoEncryptionCancelReceiver.ACTION_CANCEL_ALL
		}
		val pendingIntent = PendingIntent.getBroadcast(
			this,
			1,
			cancelIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		return NotificationCompat.Action.Builder(
			android.R.drawable.ic_menu_close_clear_cancel,
			getString(R.string.encryption_action_cancel_all),
			pendingIntent
		).build()
	}

	// endregion
}
