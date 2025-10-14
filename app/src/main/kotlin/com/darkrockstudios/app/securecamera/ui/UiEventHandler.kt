package com.darkrockstudios.app.securecamera.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.app.securecamera.navigation.NavController
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun HandleUiEvents(events: SharedFlow<UiEvent>, snackbarHostState: SnackbarHostState, navController: NavController) {
	LaunchedEffect(Unit) {
		events.collect { e ->
			when (e) {
				is UiEvent.ShowSnack -> {
					val result = snackbarHostState.showSnackbar(
						message = e.text,
						actionLabel = e.action
					)
					// handle SnackbarResult if you need to react to “Undo”, etc.
				}
			}
		}
	}
}