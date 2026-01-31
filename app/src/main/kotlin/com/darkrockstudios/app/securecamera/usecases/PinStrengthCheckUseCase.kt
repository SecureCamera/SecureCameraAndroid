package com.darkrockstudios.app.securecamera.usecases

class PinStrengthCheckUseCase {
	fun isPinStrongEnough(pin: String, isAlphanumeric: Boolean = false): Boolean {
		return if (isAlphanumeric) {
			isAlphanumericPinStrong(pin)
		} else {
			isNumericPinStrong(pin)
		}
	}

	private fun isNumericPinStrong(pin: String): Boolean {
		// Check if PIN is at least 4 digits long and contains only digits
		if (pin.length < 4 || !pin.all { it.isDigit() }) {
			return false
		}

		// Check if all digits are the same (e.g., "1111")
		if (pin.all { it == pin[0] }) {
			return false
		}

		// Check if PIN is a sequence (ascending or descending)
		val isAscendingSequence = (0 until pin.length - 1).all {
			pin[it + 1].digitToInt() - pin[it].digitToInt() == 1
		}

		val isDescendingSequence = (0 until pin.length - 1).all {
			pin[it + 1].digitToInt() - pin[it].digitToInt() == -1
		}

		if (isAscendingSequence || isDescendingSequence) {
			return false
		}

		if (numericBlackList.contains(pin)) {
			return false
		}

		return true
	}

	private fun isAlphanumericPinStrong(pin: String): Boolean {
		// At least 4 characters
		if (pin.length < 4) return false

		// Must contain only letters and digits
		if (!pin.all { it.isLetterOrDigit() }) return false

		// All same character check (case-insensitive)
		if (pin.all { it.equals(pin[0], ignoreCase = true) }) return false

		// Check blacklist (case-insensitive)
		if (alphanumericBlackList.any { it.equals(pin, ignoreCase = true) }) return false

		return true
	}

	companion object {
		/**
		 * These are some of the most frequently chosen PINs in data leaks
		 * that are not already covered by our other heuristics.
		 */
		val numericBlackList = listOf(
			"1212",
			"6969",
		)

		/**
		 * Common weak passwords that should be rejected for alphanumeric PINs.
		 */
		val alphanumericBlackList = listOf(
			"password",
			"qwerty",
			"abc123",
			"letmein",
			"admin",
			"welcome",
			"monkey",
			"dragon",
			"master",
			"login",
		)
	}
}
