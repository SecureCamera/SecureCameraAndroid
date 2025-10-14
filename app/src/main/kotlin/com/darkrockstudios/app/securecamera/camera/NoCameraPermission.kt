package com.darkrockstudios.app.securecamera.camera

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NoCameraPermission(
	navController: NavController,
	permissionsState: MultiplePermissionsState,
) {
	val context = LocalContext.current

	Box(
		modifier = Modifier
			.fillMaxSize()
	) {
		Card(modifier = Modifier.align(Alignment.Center)) {
			Column(
				modifier = Modifier.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Text(
					stringResource(R.string.camera_permissions_required),
					modifier = Modifier.padding(16.dp)
				)

				if (permissionsState.revokedPermissions.isNotEmpty()) {
					Button(onClick = {
						val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
							data = Uri.fromParts("package", context.packageName, null)
						}
						context.startActivity(intent)
					}) {
						Text(text = stringResource(R.string.camera_open_settings))
					}
				}
			}
		}

		BottomCameraControls(
			modifier = Modifier.align(Alignment.BottomCenter),
			navController = navController,
			onCapture = null,
			isLoading = false,
		)
	}
}
