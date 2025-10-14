package com.darkrockstudios.app.securecamera.utils

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

inline fun <T> Mutex.withLockBlocking(owner: Any? = null, crossinline action: () -> T): T {
	return runBlocking {
		withLock(owner, action)
	}
}