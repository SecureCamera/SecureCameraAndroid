package com.darkrockstudios.app.securecamera

import android.util.Log
import timber.log.Timber
import kotlin.math.min


class ReleaseLogTree : Timber.Tree() {
	override fun isLoggable(tag: String?, priority: Int): Boolean {
		// Only log ERROR and WTF in release builds
		return priority >= Log.ERROR
	}

	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		if (isLoggable(tag, priority)) {
			// Message is short enough, doesn't need to be broken into chunks
			if (message.length < MAX_LOG_LENGTH) {
				if (priority == Log.ASSERT) {
					Log.wtf(tag, message)
				} else {
					Log.println(priority, tag, message)
				}
				return
			}

			// Split by line, then ensure each line can fit into Log's max length
			var i = 0
			val length = message.length
			while (i < length) {
				var newline = message.indexOf('\n', i)
				newline = if (newline != -1) newline else length
				do {
					val end = min(newline, i + MAX_LOG_LENGTH)
					val part = message.substring(i, end)
					if (priority == Log.ASSERT) {
						Log.wtf(tag, part)
					} else {
						Log.println(priority, tag, part)
					}
					i = end
				} while (i < newline)
				i++
			}
		}
	}

	companion object {
		private const val MAX_LOG_LENGTH = 4000
	}
}