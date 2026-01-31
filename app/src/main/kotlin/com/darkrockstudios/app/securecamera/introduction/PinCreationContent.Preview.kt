package com.darkrockstudios.app.securecamera.introduction

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.app.securecamera.security.SecurityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

private class DummyIntroductionViewModel(initial: IntroductionUiState) : IntroductionViewModel {
	private val _state = MutableStateFlow(initial)
	override val uiState: StateFlow<IntroductionUiState> = _state
	override val skipToPage: SharedFlow<Int> = MutableStateFlow(0)
	override fun setPage(page: Int) {}
	override suspend fun navigateToNextPage() {}
	override suspend fun navigateToSecurity() {}
	override fun createPin(pin: String, confirmPin: String) {}
	override fun toggleBiometricsRequired() {}
	override fun toggleEphemeralKey() {}
	override fun toggleAlphanumericPin() {}
	override fun setShowAlphanumericHelpDialog(show: Boolean) {}
}

@Preview(name = "Pin Creation", showBackground = true)
@Composable
private fun PinCreationContentPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		val vm = DummyIntroductionViewModel(
			IntroductionUiState(
				slides = emptyList(),
				errorMessage = null,
				pinCreated = false,
				securityLevel = SecurityLevel.SOFTWARE,
				requireBiometrics = false,
				ephemeralKey = false,
				currentPage = 0,
				isCreatingPin = false,
				pinSize = 6..16,
			)
		)
		PinCreationContent(
			viewModel = vm,
			modifier = Modifier
		)
	}
}

@Preview(name = "Pin Creation - Error", showBackground = true)
@Composable
private fun PinCreationContentErrorPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		val vm = DummyIntroductionViewModel(
			IntroductionUiState(
				slides = emptyList(),
				errorMessage = "Pins do not match",
				pinCreated = false,
				securityLevel = SecurityLevel.SOFTWARE,
				requireBiometrics = false,
				ephemeralKey = false,
				currentPage = 0,
				isCreatingPin = false,
				pinSize = 6..16,
			)
		)
		PinCreationContent(
			viewModel = vm,
			modifier = Modifier
		)
	}
}

@Preview(name = "Pin Creation - Loading", showBackground = true)
@Composable
private fun PinCreationContentLoadingPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		val vm = DummyIntroductionViewModel(
			IntroductionUiState(
				slides = emptyList(),
				errorMessage = null,
				pinCreated = false,
				securityLevel = SecurityLevel.SOFTWARE,
				requireBiometrics = false,
				ephemeralKey = false,
				currentPage = 0,
				isCreatingPin = true,
				pinSize = 6..16,
			)
		)
		PinCreationContent(
			viewModel = vm,
			modifier = Modifier
		)
	}
}
