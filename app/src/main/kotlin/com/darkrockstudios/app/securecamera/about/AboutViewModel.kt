package com.darkrockstudios.app.securecamera.about

import kotlinx.coroutines.flow.StateFlow

interface AboutViewModel {
	val uiState: StateFlow<AboutUiState>
}

data class AboutUiState(
	val versionName: String = "---",
)
