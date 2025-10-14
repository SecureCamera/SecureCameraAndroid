package com.darkrockstudios.app.securecamera.navigation

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typed payload for importing photos. Used inside Nav3 typed keys only.
 */
@Serializable
@Parcelize
data class PhotoImportJob(
	val photos: List<@Serializable(with = UriAsStringSerializer::class) Uri>
) : Parcelable

/**
 * Serialize android.net.Uri as a String to avoid requiring a contextual module.
 */
object UriAsStringSerializer : KSerializer<Uri> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Uri) {
		encoder.encodeString(value.toString())
	}

	override fun deserialize(decoder: Decoder): Uri {
		return decoder.decodeString().toUri()
	}
}
