package com.darkrockstudios.app.securecamera.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface DestinationKey : NavKey

@Serializable
object Introduction : DestinationKey

@Serializable
object Camera : DestinationKey

@Serializable
object Gallery : DestinationKey

@Serializable
data class ViewPhoto(val photoName: String) : DestinationKey

@Serializable
data class ObfuscatePhoto(val photoName: String) : DestinationKey

@Serializable
object Settings : DestinationKey

@Serializable
object About : DestinationKey

@Serializable
data class PinVerification(val returnKey: DestinationKey) : DestinationKey

@Serializable
data class ImportPhotos(val job: PhotoImportJob) : DestinationKey