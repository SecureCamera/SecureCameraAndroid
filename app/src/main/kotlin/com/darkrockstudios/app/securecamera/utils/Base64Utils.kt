package com.darkrockstudios.app.securecamera.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.base64Encode(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): ByteArray = Base64.decode(this)

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.base64EncodeUrlSafe(): String = Base64.UrlSafe.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.base64DecodeUrlSafe(): ByteArray = Base64.UrlSafe.decode(this)