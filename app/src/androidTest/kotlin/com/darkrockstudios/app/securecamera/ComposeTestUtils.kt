package com.darkrockstudios.app.securecamera

import android.app.Application
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Waits for a node matching [matcher] to appear in either the unmerged or merged semantics tree.
 *
 * @return the matching SemanticsNodeInteraction (from the tree where it was found)
 */
fun ComposeContentTestRule.waitForEitherTree(
	matcher: SemanticsMatcher,
	timeout: Duration = 10.seconds,
	assert: Boolean = true,
	assertDisplayed: Boolean = true,
	preferUnmergedFirst: Boolean = true
): androidx.compose.ui.test.SemanticsNodeInteraction {

	fun hasAny(unmerged: Boolean) = onAllNodes(
		matcher,
		useUnmergedTree = unmerged
	).fetchSemanticsNodes().isNotEmpty()

	val order = if (preferUnmergedFirst) listOf(true, false) else listOf(false, true)

	waitUntil(timeout.inWholeMilliseconds) {
		hasAny(order[0]) || hasAny(order[1])
	}

	// Choose the tree that actually has it now
	val chosenUnmerged = if (hasAny(order[0])) order[0] else order[1]

	val node = onNode(
		matcher,
		useUnmergedTree = chosenUnmerged
	)

	if (assert) {
		if (assertDisplayed) node.assertIsDisplayed() else node.assertExists()
	}
	return node
}

fun ComposeContentTestRule.waitForTextEitherTree(
	text: String,
	timeout: Duration = 10.seconds,
	substring: Boolean = true,
	ignoreCase: Boolean = false,
	assert: Boolean = true,
	assertDisplayed: Boolean = true,
	preferUnmergedFirst: Boolean = true
) {
	fun hasAny(useUnmerged: Boolean) = onAllNodes(
		hasText(text, substring = substring, ignoreCase = ignoreCase),
		useUnmergedTree = useUnmerged
	).fetchSemanticsNodes().isNotEmpty()

	val order = if (preferUnmergedFirst) listOf(true, false) else listOf(false, true)

	waitUntil(timeout.inWholeMilliseconds) {
		hasAny(order[0]) || hasAny(order[1])
	}

	// Pick the tree that actually has the node
	val chosenUnmerged = if (hasAny(order[0])) order[0] else order[1]

	val node = onNode(
		hasText(text, substring = substring, ignoreCase = ignoreCase),
		useUnmergedTree = chosenUnmerged
	)
	if (assert) {
		if (assertDisplayed) node.assertIsDisplayed() else node.assertExists()
	}
}

fun ComposeContentTestRule.waitForTextEitherTree(
	@StringRes resId: Int,
	timeout: Duration = 10.seconds,
	substring: Boolean = true,
	ignoreCase: Boolean = false,
	assert: Boolean = true,
	assertDisplayed: Boolean = true,
	preferUnmergedFirst: Boolean = true
) {
	val str = androidx.test.platform.app.InstrumentationRegistry
		.getInstrumentation().targetContext.getString(resId)
	waitForTextEitherTree(
		text = str,
		timeout = timeout,
		substring = substring,
		ignoreCase = ignoreCase,
		assert = assert,
		assertDisplayed = assertDisplayed,
		preferUnmergedFirst = preferUnmergedFirst
	)
}

fun hasRole(role: Role): SemanticsMatcher =
	SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

fun str(@StringRes id: Int): String = r.getString(id)
private val r: Resources
	get() {
		val application = ApplicationProvider.getApplicationContext<Application>()
		return application.resources
	}

fun ComposeContentTestRule.setTextField(value: String, placeholder: Int) {
	onNode(
		hasSetTextAction() and hasTextExactly(
			str(placeholder),
			includeEditableText = false
		)
	).apply {
		performTextClearance()
		performTextInput(value)
	}
}

fun ComposeContentTestRule.waitUntilTextAppearsAtLeastOnce(
	text: String,
	timeout: Duration = 5.seconds,
	ignoreCase: Boolean = false
): Boolean {
	var seen = false
	waitUntil(timeout.inWholeMilliseconds) {
		val anyTree =
			onAllNodes(
				hasText(text, substring = true, ignoreCase = ignoreCase),
				useUnmergedTree = false
			)
				.fetchSemanticsNodes().isNotEmpty() ||
					onAllNodes(
						hasText(text, substring = true, ignoreCase = ignoreCase),
						useUnmergedTree = true
					)
						.fetchSemanticsNodes().isNotEmpty()
		if (anyTree) seen = true
		anyTree // stop waiting as soon as we see it
	}
	return seen
}