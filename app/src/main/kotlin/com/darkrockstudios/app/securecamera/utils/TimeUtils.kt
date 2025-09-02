package com.darkrockstudios.app.securecamera.utils

import timber.log.Timber

/**
 * Measures the execution time of the provided block of code in milliseconds.
 * @param block The code block to measure.
 * @return A Pair containing the result of the block execution and the time taken in milliseconds.
 */
inline fun <T> measureTime(block: () -> T): Pair<T, Long> {
	val startTime = System.currentTimeMillis()
	val result = block()
	val endTime = System.currentTimeMillis()
	val executionTime = endTime - startTime
	return Pair(result, executionTime)
}

/**
 * Measures the execution time of the provided block of code, prints the time with a message,
 * and returns the result of the block execution.
 * @param message The message to print along with the measured time.
 * @param block The code block to measure.
 * @return The result of the block execution.
 */
inline fun <T> measureAndReport(message: String, block: () -> T): T {
	val (result, time) = measureTime(block)
	Timber.d("$message: $time ms")
	return result
}