package com.darkrockstudios.app.securecamera.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.darkrockstudios.app.securecamera.navigation.NavController

private class DummyNavController : NavController {
	override fun navigate(
		key: NavKey,
		builder: (com.darkrockstudios.app.securecamera.navigation.NavOptions.() -> Unit)?
	) {
	}

	override fun navigate(key: NavKey) {}
	override fun navigateUp(): Boolean = true
}

@Preview(name = "About", showBackground = true)
@Composable
private fun AboutContentPreview() {
	Surface(color = MaterialTheme.colorScheme.background) {
		val dummyVm = object : AboutViewModel {
			private val _state = kotlinx.coroutines.flow.MutableStateFlow(AboutUiState(versionName = "1.2.3"))
			override val uiState: kotlinx.coroutines.flow.StateFlow<AboutUiState> = _state
		}
		AboutContent(
			navController = DummyNavController(),
			modifier = Modifier,
			paddingValues = PaddingValues(0.dp),
			viewModel = dummyVm
		)
	}
}

