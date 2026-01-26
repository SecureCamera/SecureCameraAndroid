package com.darkrockstudios.app.securecamera.camera

import android.graphics.Bitmap
import android.util.LruCache
import com.darkrockstudios.app.securecamera.utils.withLockBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThumbnailCache {
	private val maxMemory = Runtime.getRuntime().maxMemory() / 1024
	private val cacheSize = (maxMemory / 8).toInt()

	private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
		override fun sizeOf(key: String, bitmap: Bitmap): Int {
			// The cache size will be measured in kilobytes
			return bitmap.byteCount / 1024
		}
	}
	private val cacheMutex = Mutex()

	suspend fun getThumbnail(mediaItem: MediaItem): Bitmap? {
		return cacheMutex.withLock {
			thumbnailCache.get(mediaItem.mediaName)
		}
	}

	fun evictThumbnail(mediaItem: MediaItem) {
		cacheMutex.withLockBlocking {
			thumbnailCache.remove(mediaItem.mediaName)
		}
	}

	fun clear() {
		cacheMutex.withLockBlocking {
			thumbnailCache.evictAll()
		}
	}

	suspend fun putThumbnail(mediaItem: MediaItem, thumbnailBitmap: Bitmap) {
		cacheMutex.withLock {
			thumbnailCache.put(mediaItem.mediaName, thumbnailBitmap)
		}
	}
}