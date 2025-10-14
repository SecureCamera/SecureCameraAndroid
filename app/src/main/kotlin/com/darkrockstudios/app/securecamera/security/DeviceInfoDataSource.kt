package com.darkrockstudios.app.securecamera.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.operations.Hasher
import kotlin.io.encoding.ExperimentalEncodingApi

class DeviceInfoDataSource(private val appContext: Context) {
	private val hasher: Hasher = CryptographyProvider.Default.get(SHA512).hasher()

	@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
	@SuppressLint("HardwareIds")
	suspend fun getDeviceIdentifier(): ByteArray {
		val androidId = Settings.Secure.getString(
			appContext.contentResolver,
			Settings.Secure.ANDROID_ID
		)
		val id = androidId + android.os.Build.MANUFACTURER + android.os.Build.MODEL
		val hashed = hasher.hash(id.toByteArray())
		return hashed
	}
}