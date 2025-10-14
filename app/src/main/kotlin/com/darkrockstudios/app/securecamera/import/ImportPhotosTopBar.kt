package com.darkrockstudios.app.securecamera.import

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPhotosTopBar(
	navController: NavController,
) {
	TopAppBar(
		title = {
			Text(
				stringResource(id = R.string.import_photos_title),
				color = MaterialTheme.colorScheme.onPrimaryContainer,
			)
		},
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
		),
		navigationIcon = {
			IconButton(
				onClick = { navController.navigateUp() },
				modifier = Modifier.padding(8.dp)
			) {
				Icon(
					imageVector = Icons.Filled.Close,
					contentDescription = stringResource(id = R.string.close_import_photos_content_description),
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}
		}
	)
}