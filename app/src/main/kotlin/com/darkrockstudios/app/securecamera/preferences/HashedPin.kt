package com.darkrockstudios.app.securecamera.preferences

import kotlinx.serialization.Serializable

@Serializable
data class HashedPin(val hash: String, val salt: String)