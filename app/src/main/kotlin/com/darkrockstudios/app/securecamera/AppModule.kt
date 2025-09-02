package com.darkrockstudios.app.securecamera

import androidx.work.WorkManager
import com.darkrockstudios.app.securecamera.auth.AuthorizationRepository
import com.darkrockstudios.app.securecamera.auth.PinVerificationViewModel
import com.darkrockstudios.app.securecamera.camera.SecureImageRepository
import com.darkrockstudios.app.securecamera.camera.ThumbnailCache
import com.darkrockstudios.app.securecamera.gallery.GalleryViewModel
import com.darkrockstudios.app.securecamera.import.ImportPhotosViewModel
import com.darkrockstudios.app.securecamera.introduction.IntroductionViewModel
import com.darkrockstudios.app.securecamera.obfuscation.ObfuscatePhotoViewModel
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSource
import com.darkrockstudios.app.securecamera.preferences.AppPreferencesDataSourceImpl
import com.darkrockstudios.app.securecamera.security.DeviceInfoDataSource
import com.darkrockstudios.app.securecamera.security.SecurityLevel
import com.darkrockstudios.app.securecamera.security.SecurityLevelDetector
import com.darkrockstudios.app.securecamera.security.pin.PinCrypto
import com.darkrockstudios.app.securecamera.security.pin.PinRepository
import com.darkrockstudios.app.securecamera.security.pin.PinRepositoryHardware
import com.darkrockstudios.app.securecamera.security.pin.PinRepositorySoftware
import com.darkrockstudios.app.securecamera.security.schemes.EncryptionScheme
import com.darkrockstudios.app.securecamera.security.schemes.HardwareBackedEncryptionScheme
import com.darkrockstudios.app.securecamera.security.schemes.SoftwareEncryptionScheme
import com.darkrockstudios.app.securecamera.settings.SettingsViewModel
import com.darkrockstudios.app.securecamera.usecases.*
import com.darkrockstudios.app.securecamera.viewphoto.ViewPhotoViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val appModule = module {

	single { Clock.System } bind Clock::class
	singleOf(::SecureImageRepository)
	single<AppPreferencesDataSource> { AppPreferencesDataSourceImpl(context = get()) }
	single {
		AuthorizationRepository(
			preferences = get(),
			encryptionScheme = get(),
			context = get(),
			clock = get()
		)
	}
	singleOf(::LocationRepository)
	single<EncryptionScheme> {
		val detector = get<SecurityLevelDetector>()
		when (detector.detectSecurityLevel()) {
			SecurityLevel.SOFTWARE ->
				SoftwareEncryptionScheme(get())
			SecurityLevel.TEE, SecurityLevel.STRONGBOX -> {
				HardwareBackedEncryptionScheme(get(), get(), get())
			}
		}
	} bind EncryptionScheme::class
	single<PinRepository> {
		val detector = get<SecurityLevelDetector>()
		when (detector.detectSecurityLevel()) {
			SecurityLevel.SOFTWARE ->
				PinRepositorySoftware(get(), get(), get())

			SecurityLevel.TEE, SecurityLevel.STRONGBOX -> {
				PinRepositoryHardware(get(), get(), get(), get())
			}
		}
	} bind PinRepository::class
	singleOf(::SecurityLevelDetector)
	single<PinCrypto> { PinCrypto() }

	single { WorkManager.getInstance(get()) }

	factoryOf(::DeviceInfoDataSource)

	factoryOf(::ThumbnailCache)
	factoryOf(::SecurityResetUseCase)
	factoryOf(::PinStrengthCheckUseCase)
	factoryOf(::VerifyPinUseCase)
	factoryOf(::AuthorizePinUseCase)
	factoryOf(::CreatePinUseCase)
	factoryOf(::PinSizeUseCase)
	factoryOf(::RemovePoisonPillIUseCase)
	factoryOf(::MigratePinHash)
	factoryOf(::InvalidateSessionUseCase)
	factoryOf(::AddDecoyPhotoUseCase)

	viewModelOf(::ObfuscatePhotoViewModel)
	viewModelOf(::ViewPhotoViewModel)
	viewModelOf(::GalleryViewModel)
	viewModelOf(::SettingsViewModel)
	viewModelOf(::IntroductionViewModel)
	viewModelOf(::PinVerificationViewModel)
	viewModelOf(::ImportPhotosViewModel)
}
