package com.darkrockstudios.app.securecamera.about

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutViewModelImplTest {
	@Test
	fun `uiState exposes app version`() {
		val pm = mockk<PackageManager>()
		val ctx = mockk<Context>()
		every { ctx.packageManager } returns pm
		every { ctx.packageName } returns "com.example.app"

		val pkgInfo = PackageInfo()
		pkgInfo.versionName = "9.9.9"
		every { pm.getPackageInfo("com.example.app", 0) } returns pkgInfo

		val vm = AboutViewModelImpl(ctx)
		val state = vm.uiState.value
		assertEquals("9.9.9", state.versionName)
	}
}
