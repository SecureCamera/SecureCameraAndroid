package com.darkrockstudios.app.securecamera.utils

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Extension function to convert a ByteArray to an ImageBitmap.
 * This is useful for displaying images captured from the camera in Jetpack Compose UI.
 *
 * @return ImageBitmap created from the ByteArray
 */
fun ByteArray.decodeToImageBitmap(): ImageBitmap {
    // Convert ByteArray to Android Bitmap first
    val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size)
    
    // Then convert Android Bitmap to Compose's ImageBitmap
    return bitmap.asImageBitmap()
}