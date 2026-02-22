package com.darkrockstudios.app.securecamera.auth

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
import com.darkrockstudios.app.securecamera.usecases.InvalidateSessionUseCase
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Service that monitors session validity and shows an ongoing notification
 * while the session is active. If the session becomes invalid, it will
 * call InvalidateSessionUseCase and terminate itself.
 *
 * Uses onTaskRemoved to clear the notification when the app is swiped away.
 */
class SessionService : Service() {

	private val authRepository: AuthorizationRepository by inject()
	private val invalidateSessionUseCase: InvalidateSessionUseCase by inject()

	private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private var sessionMonitorJob: Job? = null

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "session_status_channel"
		const val NOTIFICATION_ID = 2
		private val CHECK_INTERVAL = 30.seconds
		private const val ACTION_CLOSE_SESSION = "com.darkrockstudios.app.securecamera.action.CLOSE_SESSION"

		fun startService(context: Context) {
			val intent = Intent(context, SessionService::class.java)
			context.startService(intent)
		}

		fun stopService(context: Context) {
			val intent = Intent(context, SessionService::class.java)
			context.stopService(intent)
		}
	}

	override fun onCreate() {
		super.onCreate()
		Timber.d("SessionService created")
		createNotificationChannel()
		showNotification()
		startSessionMonitoring()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Timber.d("SessionService started")

		if (intent?.action == ACTION_CLOSE_SESSION) {
			Timber.d("Close action received, invalidating session")
			invalidateSessionUseCase.invalidateSession()
			stopSelf()
			return START_NOT_STICKY
		} else {
			return START_STICKY
		}
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onDestroy() {
		Timber.d("SessionService destroyed")
		dismissNotification()
		serviceScope.cancel()
		super.onDestroy()
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		Timber.d("App task removed, clearing notification and stopping service")
		invalidateSessionUseCase.invalidateSession()
		dismissNotification()
		stopSelf()
		super.onTaskRemoved(rootIntent)
	}

	private fun startSessionMonitoring() {
		sessionMonitorJob = serviceScope.launch {
			try {
				while (isActive) {
					if (!authRepository.checkSessionValidity()) {
						Timber.d("Session is no longer valid, invalidating session")
						invalidateSessionUseCase.invalidateSession()
						dismissNotification()
						stopSelf()
						break
					}
					delay(CHECK_INTERVAL)
				}
			} catch (e: Exception) {
				Timber.e(e, "Error in SessionService")
			} finally {
				dismissNotification()
				stopSelf()
			}
		}
	}

	private fun createNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
		.setSmallIcon(android.R.drawable.ic_lock_lock)
		.setContentTitle(getString(R.string.session_worker_notification_title))
		.setContentText(getString(R.string.session_worker_notification_content))
		.setOngoing(true)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.setContentIntent(createPendingIntent())
		.addAction(
			android.R.drawable.ic_menu_close_clear_cancel,
			getString(R.string.session_worker_notification_close),
			createClosePendingIntent()
		)
		.build()

	private fun createPendingIntent() = PendingIntent.getActivity(
		this,
		0,
		Intent(this, MainActivity::class.java),
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
	)

	private fun createClosePendingIntent() = PendingIntent.getService(
		this,
		1,
		Intent(this, SessionService::class.java).apply {
			action = ACTION_CLOSE_SESSION
		},
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
	)

	private fun createNotificationChannel() {
		val name = getString(R.string.session_worker_channel_name)
		val description = getString(R.string.session_worker_channel_description)
		val importance = NotificationManager.IMPORTANCE_LOW
		val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
			this.description = description
		}

		val notificationManager =
			getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.createNotificationChannel(channel)
	}

	private fun showNotification() {
		val notificationManager =
			getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.notify(NOTIFICATION_ID, createNotification())
	}

	private fun dismissNotification() {
		val notificationManager =
			getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.cancel(NOTIFICATION_ID)
	}
}
