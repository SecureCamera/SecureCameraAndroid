package com.darkrockstudios.app.securecamera.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Minimal compatibility controller to keep most of the app code unchanged while using Navigation 3
 **/
interface NavController {
	fun navigate(key: NavKey, builder: (NavOptions.() -> Unit)? = null)
	fun navigate(key: NavKey)

	fun navigateUp(): Boolean
	fun popBackStack(): Boolean = navigateUp()
}

class NavOptions {
	var launchSingleTop: Boolean = false
}

class Nav3CompatController(
	private val backStack: NavBackStack<NavKey>
) : NavController {
	override fun navigate(key: NavKey, builder: (NavOptions.() -> Unit)?) {
		val opts = NavOptions().apply { builder?.invoke(this) }
		if (opts.launchSingleTop) {
			val current = backStack.lastOrNull()
			if (current != null && current == key) return
		}
		backStack.add(key)
	}

	override fun navigate(key: NavKey) {
		navigate(key, null)
	}

	override fun navigateUp(): Boolean {
		return backStack.removeLastOrNull() != null
	}

	/** Clears the entire back stack. */
	fun clearBackStack() {
		while (backStack.removeLastOrNull() != null) {
			// keep removing
		}
	}

	/** Ensures the given key is the base of the stack. Clears if different or empty. */
	fun ensureBase(base: NavKey) {
		val currentBase = backStack.firstOrNull()
		if (currentBase == base) return
		clearBackStack()
		backStack.add(base)
	}
}

fun NavController.navigateClearingBackStack(key: NavKey, launchSingleTop: Boolean = false) {
	when (this) {
		is Nav3CompatController -> {
			clearBackStack()
			navigate(key) { this.launchSingleTop = launchSingleTop }
		}
		else -> {
			navigate(key) { this.launchSingleTop = launchSingleTop }
		}
	}
}

/**
 * Puts `baseKey` at the bottom of the stack, and then navigates to targetKey
 */
fun NavController.navigateFromBase(baseKey: NavKey, targetKey: NavKey, launchSingleTop: Boolean = false) {
	when (this) {
		is Nav3CompatController -> {
			ensureBase(baseKey)
			navigate(targetKey) { this.launchSingleTop = launchSingleTop }
		}
		else -> {
			navigate(targetKey) { this.launchSingleTop = launchSingleTop }
		}
	}
}

fun NavController.popAndNavigate(popN: Int = 1, targetKey: NavKey, launchSingleTop: Boolean = false) {
	when (this) {
		is Nav3CompatController -> {
			repeat(popN) {
				popBackStack()
			}
			navigate(targetKey) { this.launchSingleTop = launchSingleTop }
		}

		else -> {
			navigate(targetKey) { this.launchSingleTop = launchSingleTop }
		}
	}
}