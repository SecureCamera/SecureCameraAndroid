package com.darkrockstudios.app.securecamera.obfuscation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.popAndNavigate
import com.darkrockstudios.app.securecamera.ui.HandleUiEvents
import com.darkrockstudios.app.securecamera.ui.PlayfulScaleVisibility
import kotlinx.coroutines.CoroutineScope
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.koin.androidx.compose.koinViewModel


@Composable
fun ObfuscatePhotoContent(
	photoName: String,
	navController: NavController,
	snackbarHostState: SnackbarHostState,
	outerScope: CoroutineScope,
	paddingValues: PaddingValues
) {
	val viewModel: ObfuscatePhotoViewModel = koinViewModel(key = photoName)
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(photoName) {
		if (uiState.imageBitmap == null) {
			viewModel.loadPhoto(photoName)
		}
	}

	// Determine if there are unsaved changes
	val hasUnsavedChanges = uiState.regions.isNotEmpty() ||
			uiState.obscuredBitmap != null ||
			uiState.isCreatingRegion ||
			uiState.currentRegion != null

	val showDiscardDialog = remember { mutableStateOf(false) }

	BackHandler(enabled = hasUnsavedChanges) {
		showDiscardDialog.value = true
	}

	// Discard confirmation dialog
	if (showDiscardDialog.value) {
		AlertDialog(
			onDismissRequest = { showDiscardDialog.value = false },
			title = { Text(stringResource(id = R.string.discard_changes_dialog_title)) },
			text = { Text(stringResource(id = R.string.discard_changes_dialog_message)) },
			confirmButton = {
				TextButton(
					onClick = {
						showDiscardDialog.value = false
						navController.navigateUp()
					}
				) {
					Text(stringResource(id = R.string.discard_button))
				}
			},
			dismissButton = {
				TextButton(
					onClick = { showDiscardDialog.value = false }
				) {
					Text(stringResource(id = R.string.cancel_button))
				}
			}
		)
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) {
		ObfuscatePhotoTopBar(
			onAddRegionClick = {
				viewModel.startRegionCreation()
			},
			isFindingFaces = uiState.isFindingFaces,
			isCreatingRegion = uiState.isCreatingRegion,
			onBackPressed = {
				if (hasUnsavedChanges) {
					showDiscardDialog.value = true
				} else {
					navController.navigateUp()
				}
			}
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.clipToBounds(),
			contentAlignment = Alignment.Center
		) {
			if (uiState.isLoading) {
				CircularProgressIndicator()
			} else if (uiState.photoDef == null) {
				Text(
					text = stringResource(id = R.string.photo_not_found),
					color = MaterialTheme.colorScheme.onBackground
				)
			} else {
				uiState.imageBitmap?.let {
					val zoomState = rememberZoomState()
					Box(
						modifier = Modifier
							.zoomable(
								zoomState = zoomState,
								zoomEnabled = uiState.isCreatingRegion.not()
							)
					) {
						var imageWidth by remember { mutableStateOf(0f) }
						var imageHeight by remember { mutableStateOf(0f) }
						var boxWidth by remember { mutableStateOf(0f) }
						var boxHeight by remember { mutableStateOf(0f) }
						var imageTopLeft by remember { mutableStateOf(Offset(0f, 0f)) }
						var dragStart by remember { mutableStateOf(Offset.Zero) }
						var dragEnd by remember { mutableStateOf(Offset.Zero) }

						val density = LocalDensity.current

						Image(
							bitmap = it,
							contentDescription = stringResource(id = R.string.photo_content_description),
							modifier = Modifier
								.fillMaxSize()
								.padding(16.dp)
								.onGloballyPositioned { coordinates ->
									// Get the size of the Box containing the image
									with(density) {
										boxWidth = coordinates.size.width.toFloat()
										boxHeight = coordinates.size.height.toFloat()
									}

									// Calculate the actual displayed image size (maintaining aspect ratio)
									val originalWidth = it.width.toFloat()
									val originalHeight = it.height.toFloat()
									val aspectRatio = originalWidth / originalHeight

									if (boxWidth / boxHeight > aspectRatio) {
										// Box is wider than image aspect ratio
										imageHeight = boxHeight
										imageWidth = imageHeight * aspectRatio
										imageTopLeft = Offset((boxWidth - imageWidth) / 2f, 0f)
									} else {
										// Box is taller than image aspect ratio
										imageWidth = boxWidth
										imageHeight = imageWidth / aspectRatio
										imageTopLeft = Offset(0f, (boxHeight - imageHeight) / 2f)
									}
								},
							contentScale = ContentScale.Fit
						)

						// Offset create box touch so it's not right under your finger
						val createTouchOffset = remember { Offset(10f, 10f) }
						// Draw rectangles around detected faces
						Canvas(
							modifier = Modifier
								.padding(16.dp)
								.fillMaxSize()
								.pointerInput(uiState.regions, uiState.isCreatingRegion) {
									if (uiState.isCreatingRegion) {
										// Handle drag for region creation
										detectDragGestures(
											onDragStart = { offset ->
												dragStart = offset - createTouchOffset
												dragEnd = offset - createTouchOffset
											},
											onDrag = { change, _ ->
												dragEnd = change.position - createTouchOffset

												// Convert screen coordinates to image coordinates
												if (imageWidth > 0 && imageHeight > 0) {
													val scaleX = it.width / imageWidth
													val scaleY = it.height / imageHeight

													val startX =
														((dragStart.x - imageTopLeft.x) * scaleX).toInt()
													val startY =
														((dragStart.y - imageTopLeft.y) * scaleY).toInt()
													val endX =
														((dragEnd.x - imageTopLeft.x) * scaleX).toInt()
													val endY =
														((dragEnd.y - imageTopLeft.y) * scaleY).toInt()

													// Update the region in the ViewModel
													viewModel.updateRegion(
														startX,
														startY,
														endX,
														endY
													)
												}
											}
										)
									} else {
										// Handle tap for toggling region obfuscation
										detectTapGestures { tapOffset ->
											if (uiState.regions.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
												val scaleX = imageWidth / it.width
												val scaleY = imageHeight / it.height

												// Check if tap is inside any region
												uiState.regions.forEachIndexed { index, region ->
													val boundingBox = region.rect

													// Scale the bounding box to match the displayed image size
													val scaledLeft =
														boundingBox.left * scaleX + imageTopLeft.x
													val scaledTop =
														boundingBox.top * scaleY + imageTopLeft.y
													val scaledRight =
														boundingBox.right * scaleX + imageTopLeft.x
													val scaledBottom =
														boundingBox.bottom * scaleY + imageTopLeft.y

													// Create a rectangle from the scaled coordinates
													val scaledRect = Rect(
														left = scaledLeft,
														top = scaledTop,
														right = scaledRight,
														bottom = scaledBottom
													)

													if (scaledRect.contains(tapOffset)) {
														viewModel.toggleRegionObfuscation(index)
														return@detectTapGestures
													}
												}
											}
										}
									}
								}
						) {
							val boxStrokeWidthPx = 2.dp.toPx()
							if (imageWidth > 0 && imageHeight > 0) {
								val scaleX = imageWidth / it.width
								val scaleY = imageHeight / it.height

								// Draw existing regions
								if (uiState.regions.isNotEmpty()) {
									uiState.regions.forEach { region ->
										val boundingBox = region.rect

										// Scale the bounding box to match the displayed image size
										val scaledLeft = boundingBox.left * scaleX + imageTopLeft.x
										val scaledTop = boundingBox.top * scaleY + imageTopLeft.y
										val scaledRight = boundingBox.right * scaleX + imageTopLeft.x
										val scaledBottom = boundingBox.bottom * scaleY + imageTopLeft.y

										val color = if (region is FaceRegion) {
											if (region.obfuscate) Color.Red else Color.Green
										} else {
											Color.Blue
										}
										drawRoundRect(
											color = color,
											topLeft = Offset(scaledLeft, scaledTop),
											size = Size(scaledRight - scaledLeft, scaledBottom - scaledTop),
											cornerRadius = CornerRadius(12f, 12f),
											style = Stroke(width = boxStrokeWidthPx)
										)
									}
								}

								// Draw the region being created
								if (uiState.isCreatingRegion && uiState.currentRegion != null) {
									val rect = uiState.currentRegion!!

									val scaledLeft = rect.left * scaleX + imageTopLeft.x
									val scaledTop = rect.top * scaleY + imageTopLeft.y
									val scaledRight = rect.right * scaleX + imageTopLeft.x
									val scaledBottom = rect.bottom * scaleY + imageTopLeft.y

									drawRect(
										color = Color.Blue,
										topLeft = Offset(scaledLeft, scaledTop),
										size = Size(scaledRight - scaledLeft, scaledBottom - scaledTop),
										style = Stroke(width = boxStrokeWidthPx)
									)
								}
							}
						}
					}
				}

				PlayfulScaleVisibility(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(
							bottom = paddingValues.calculateBottomPadding(),
							start = 16.dp,
							end = 16.dp
						),
					isVisible = (uiState.isCreatingRegion)
				) {
					Column(
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						FloatingActionButton(
							onClick = { viewModel.finishRegionCreation() },
							containerColor = MaterialTheme.colorScheme.primary,
							contentColor = MaterialTheme.colorScheme.onPrimary
						) {
							Icon(
								imageVector = Icons.Filled.Check,
								contentDescription = stringResource(id = R.string.obscure_action_button_finish_region)
							)
						}

						FloatingActionButton(
							onClick = { viewModel.cancelRegionCreation() },
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						) {
							Icon(
								imageVector = Icons.Filled.Close,
								contentDescription = stringResource(id = R.string.obscure_action_button_cancel_region)
							)
						}
					}
				}

				PlayfulScaleVisibility(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(
							bottom = paddingValues.calculateBottomPadding(),
							start = 16.dp,
							end = 16.dp
						),
					isVisible = (uiState.obscuredBitmap != null && uiState.isCreatingRegion.not())
				) {
					Column(
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						FloatingActionButton(
							onClick = { viewModel.clearObfuscation() },
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						) {
							Icon(
								imageVector = Icons.Filled.BlurOff,
								contentDescription = stringResource(id = R.string.obscure_action_button_clear)
							)
						}

						FloatingActionButton(
							onClick = { viewModel.showSaveDialog() },
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						) {
							Icon(
								imageVector = Icons.Filled.Save,
								contentDescription = stringResource(id = R.string.obscure_action_button_save)
							)
						}
					}
				}

				PlayfulScaleVisibility(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(
							bottom = paddingValues.calculateBottomPadding(),
							start = 16.dp,
							end = 16.dp
						),
					isVisible = (uiState.obscuredBitmap == null && uiState.regions.isNotEmpty() && uiState.isCreatingRegion.not())
				) {
					Column(
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						FloatingActionButton(
							onClick = { viewModel.obscureRegions() },
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						) {
							Icon(
								imageVector = Icons.Filled.BlurOn,
								contentDescription = stringResource(id = R.string.obscure_action_button_obfuscate)
							)
						}
					}
				}
			}
		}
	}

	if (uiState.showSaveDialog) {
		ConfirmSaveDialog(
			onDismiss = { viewModel.dismissSaveDialog() },
			saveAsCopy = {
				viewModel.saveAsCopy(
					onNavigate = { route ->
						// Pop the editor, and the original PhotoView
						navController.popAndNavigate(2, route)
					}
				)
			},
			overwriteOriginal = {
				viewModel.overwriteOriginal(
					onSuccess = {
						navController.popBackStack()
					}
				)
			},
		)
	}

	HandleUiEvents(viewModel.events, snackbarHostState, navController)
}

@Composable
private fun ConfirmSaveDialog(
	onDismiss: () -> Unit,
	saveAsCopy: () -> Unit,
	overwriteOriginal: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					overwriteOriginal()
					onDismiss()
				}
			) {
				Text(stringResource(id = R.string.overwrite_button))
			}
		},
		dismissButton = {
			TextButton(
				onClick = onDismiss
			) {
				Text(stringResource(id = R.string.cancel_button))
			}
		},
		title = { Text(stringResource(id = R.string.save_photo_dialog_title)) },
		text = {
			Column {
				Text(stringResource(id = R.string.save_photo_dialog_message))

				// Add a third button as part of the dialog content
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(
						onClick = {
							saveAsCopy()
							onDismiss()
						}
					) {
						Text(stringResource(id = R.string.save_copy_button))
					}
				}
			}
		},
		containerColor = MaterialTheme.colorScheme.surface,
		titleContentColor = MaterialTheme.colorScheme.onSurface,
		textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
	)
}
