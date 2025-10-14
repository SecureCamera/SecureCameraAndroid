package com.darkrockstudios.app.securecamera.about

import android.content.Context
import com.darkrockstudios.app.securecamera.BaseViewModel

class AboutViewModelImpl(
	private val appContext: Context,
) : BaseViewModel<AboutUiState>(), AboutViewModel {

	override fun createState(): AboutUiState {
		val pm = appContext.packageManager
		val pkg = appContext.packageName
		val version = try {
			val info = pm.getPackageInfo(pkg, 0)
			info.versionName ?: "---"
		} catch (e: Exception) {
			"---"
		}
		return AboutUiState(versionName = version)
	}
}
