package com.darkrockstudios.app.securecamera

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test


class SmokeTestUiTest {

	@get:Rule
	val permissionsRule: GrantPermissionRule =
		if (Build.VERSION.SDK_INT >= 33) {
			GrantPermissionRule.grant(
				Manifest.permission.CAMERA,
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.POST_NOTIFICATIONS,
			)
		} else {
			GrantPermissionRule.grant(
				Manifest.permission.CAMERA,
				Manifest.permission.ACCESS_FINE_LOCATION,
			)
		}

	@get:Rule
	val composeTestRule = createAndroidComposeRule<MainActivity>()

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun smokeTest() {
		composeTestRule.apply {
			onNodeWithText(str(R.string.intro_next)).performClick()
			onNodeWithText(str(R.string.intro_slide1_title)).assertIsDisplayed()

			onNodeWithText(str(R.string.intro_next)).performClick()
			onNodeWithText(str(R.string.intro_slide2_title)).assertIsDisplayed()

			onNodeWithText(str(R.string.intro_skip)).performClick()
			onNodeWithText(str(R.string.security_intro_supported_security_label)).assertIsDisplayed()

			onNodeWithText(str(R.string.intro_next)).performClick()
			onNodeWithText(str(R.string.pin_creation_title)).assertIsDisplayed()

			setPinFields("3133734", "313373")
			waitForIdle()
			onNodeWithText(str(R.string.pin_creation_button)).performClick()
			waitForIdle()
			waitUntilAtLeastOneExists(hasTestTag("pin-error"))

			setPinFields("123456", "123456")
			onNodeWithText(str(R.string.pin_creation_button)).performClick()
			waitForIdle()
			waitUntilExactlyOneExists(hasText(str(R.string.pin_creation_error_weak_pin)))

			setPinFields("313373", "313373")
			onNodeWithText(str(R.string.pin_creation_button)).performClick()
			waitForIdle()
			waitUntilExactlyOneExists(hasText(str(R.string.pin_creating_vault)))

			waitForEitherTree(hasRole(Role.Button) and hasContentDescription(str(R.string.camera_shutter_button_desc)))
				.performClick()

			waitForEitherTree(hasContentDescription(str(R.string.camera_more_options_content_description)))
				.performClick()

			waitForEitherTree(hasTestTag("flash-switch"))
				.performClick()

			waitForEitherTree(hasRole(Role.Button) and hasContentDescription(str(R.string.camera_close_controls_content_description)))
				.performClick()
		}
	}

	private fun ComposeContentTestRule.setPinFields(primary: String, confirm: String) {
		setTextField(
			placeholder = R.string.pin_creation_hint,
			value = primary,
		)

		setTextField(
			placeholder = R.string.pin_creation_confirm_hint,
			value = confirm,
		)
	}
}