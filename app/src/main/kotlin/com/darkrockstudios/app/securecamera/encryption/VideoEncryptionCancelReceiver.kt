package com.darkrockstudios.app.securecamera.encryption

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * BroadcastReceiver for handling cancel actions from the encryption notification.
 */
class VideoEncryptionCancelReceiver : BroadcastReceiver() {

	companion object {
		const val ACTION_CANCEL_ENCRYPTION = "com.darkrockstudios.app.securecamera.action.CANCEL_ENCRYPTION"
		const val ACTION_CANCEL_ALL = "com.darkrockstudios.app.securecamera.action.CANCEL_ALL_ENCRYPTION"
	}

	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			ACTION_CANCEL_ENCRYPTION -> {
				Timber.d("Cancel current encryption job requested")
				VideoEncryptionService.cancelCurrentJob(context)
			}

			ACTION_CANCEL_ALL -> {
				Timber.d("Cancel all encryption jobs requested")
				VideoEncryptionService.cancelAllJobs(context)
			}
		}
	}
}
