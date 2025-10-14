package com.darkrockstudios.app.securecamera.introduction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkrockstudios.app.securecamera.R
import com.darkrockstudios.app.securecamera.navigation.Camera
import com.darkrockstudios.app.securecamera.navigation.NavController
import com.darkrockstudios.app.securecamera.navigation.navigateClearingBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * Main content for the Introduction screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroductionContent(
	navController: NavController,
	modifier: Modifier = Modifier,
	paddingValues: PaddingValues
) {
	val viewModel: IntroductionViewModel = koinViewModel<IntroductionViewModelImpl>()
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val coroutineScope = rememberCoroutineScope()

	// Navigate to camera when PIN is created
	LaunchedEffect(uiState.pinCreated) {
		if (uiState.pinCreated) {
			navController.navigateClearingBackStack(Camera)
		}
	}

	val pagerState = rememberPagerState(pageCount = { uiState.slides.size + 2 })

	LaunchedEffect(Unit) {
		viewModel.skipToPage.collect { page ->
			withContext(Dispatchers.Main) {
				pagerState.animateScrollToPage(page)
			}
		}
	}

	// Update ViewModel when pager state changes
	LaunchedEffect(pagerState.currentPage) {
		if (uiState.currentPage != pagerState.currentPage) {
			viewModel.setPage(pagerState.currentPage)
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.padding(paddingValues),
	) {
		HorizontalPager(
			state = pagerState,
			modifier = Modifier
				.fillMaxHeight()
		) { page ->
			when {
				page < uiState.slides.size -> {
					IntroductionSlideContent(
						slide = uiState.slides[page],
						modifier = Modifier
							.fillMaxSize()
							.padding(16.dp)
					)
				}

				page == uiState.slides.size -> {
					SecurityContent(
						modifier = Modifier
							.fillMaxSize()
							.padding(16.dp),
						viewModel = viewModel,
					)
				}

				page == (uiState.slides.size + 1) -> {
					PinCreationContent(
						viewModel = viewModel,
						modifier = Modifier
							.fillMaxSize()
							.padding(16.dp)
					)
				}
			}
		}

		// Bottom navigation buttons
		Row(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			if (pagerState.currentPage < uiState.slides.size + 1) {
				// Skip button (only on intro slides)
				if (pagerState.currentPage < uiState.slides.size) {
					TextButton(
						onClick = {
							coroutineScope.launch {
								viewModel.navigateToSecurity()
							}
						}
					) {
						Text(stringResource(R.string.intro_skip))
					}
				} else {
					Spacer(Modifier.size(8.dp))
				}

				// Next button
				Button(
					onClick = {
						coroutineScope.launch {
							viewModel.navigateToNextPage()
						}
					}
				) {
					Text(stringResource(R.string.intro_next))
				}
			}
		}
	}
}

/**
 * Content for a single introduction slide
 */
@Composable
fun IntroductionSlideContent(
	slide: IntroductionSlide,
	modifier: Modifier = Modifier
) {
	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.widthIn(max = 512.dp)
				.align(Alignment.Center),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(
				modifier = Modifier
					.size(96.dp)
					.padding(16.dp),
				imageVector = slide.icon,
				contentDescription = stringResource(id = R.string.intro_slide_icon),
				tint = MaterialTheme.colorScheme.onBackground
			)

			Text(
				text = slide.title,
				style = MaterialTheme.typography.headlineMedium,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 16.dp)
			)

			Text(
				text = slide.description,
				style = MaterialTheme.typography.bodyLarge,
				textAlign = TextAlign.Center
			)
		}
	}
}
