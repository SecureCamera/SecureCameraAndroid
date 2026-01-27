package com.darkrockstudios.app.securecamera.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.darkrockstudios.app.securecamera.camera.MediaItem
import com.darkrockstudios.app.securecamera.camera.PhotoDef
import com.darkrockstudios.app.securecamera.camera.VideoDef

private const val DECRYPTING_PROVIDER_AUTHORITY = ".decryptingprovider"

/**
 * Creates a URI for a photo using the DecryptingMediaProvider
 */
private fun getDecryptingFileProviderUri(
    photo: PhotoDef,
    context: Context
): Uri {
    val authority = context.packageName + DECRYPTING_PROVIDER_AUTHORITY
    return Uri.Builder()
        .scheme("content")
        .authority(authority)
        .path("${DecryptingMediaProvider.PATH_PHOTOS}/${photo.photoName}")
        .build()
}

/**
 * Share a photo using DecryptingImageProvider (no temp files)
 */
fun sharePhotoWithProvider(
    photo: PhotoDef,
    context: Context
): Boolean {
    val uri = getDecryptingFileProviderUri(photo, context)

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/jpeg"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    return true
}

/**
 * Share multiple photos using DecryptingImageProvider (no temp files)
 */
fun sharePhotosWithProvider(
    photos: List<PhotoDef>,
    context: Context
): Boolean {
    if (photos.isEmpty()) {
        return false
    }

    val uris = photos.map { photo ->
        getDecryptingFileProviderUri(photo, context)
    }

    val shareIntent = if (uris.size == 1) {
        // Single photo share
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uris.first())
            type = "image/jpeg"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        // Multiple photo share
        Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "image/jpeg"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Photos"))
    return true
}

/**
 * Creates a URI for a video using the DecryptingMediaProvider
 */
private fun getDecryptingVideoProviderUri(
    video: VideoDef,
    context: Context
): Uri {
    val authority = context.packageName + DECRYPTING_PROVIDER_AUTHORITY
    return Uri.Builder()
        .scheme("content")
        .authority(authority)
        .path("${DecryptingMediaProvider.PATH_VIDEOS}/${video.videoName}")
        .build()
}

/**
 * Share a video using DecryptingMediaProvider (no temp files)
 */
fun shareVideoWithProvider(
    video: VideoDef,
    context: Context
): Boolean {
    val uri = getDecryptingVideoProviderUri(video, context)

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "video/mp4"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    return true
}

/**
 * Share multiple videos using DecryptingMediaProvider (no temp files)
 */
fun shareVideosWithProvider(
    videos: List<VideoDef>,
    context: Context
): Boolean {
    if (videos.isEmpty()) {
        return false
    }

    val uris = videos.map { video ->
        getDecryptingVideoProviderUri(video, context)
    }

    val shareIntent = if (uris.size == 1) {
        // Single video share
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uris.first())
            type = "video/mp4"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        // Multiple video share
        Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "video/mp4"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Videos"))
    return true
}

/**
 * Share mixed media (photos and videos) using DecryptingMediaProvider (no temp files)
 */
fun shareMediaWithProvider(
    mediaItems: List<MediaItem>,
    context: Context
): Boolean {
    if (mediaItems.isEmpty()) {
        return false
    }

    val photos = mediaItems.filterIsInstance<PhotoDef>()
    val videos = mediaItems.filterIsInstance<VideoDef>()

    // Pure photo or pure video - use specific MIME type
    if (videos.isEmpty()) return sharePhotosWithProvider(photos, context)
    if (photos.isEmpty()) return shareVideosWithProvider(videos, context)

    // Mixed media - use generic MIME type
    val photoUris = photos.map { getDecryptingFileProviderUri(it, context) }
    val videoUris = videos.map { getDecryptingVideoProviderUri(it, context) }
    val allUris = photoUris + videoUris

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(allUris))
        type = "*/*"  // Generic for mixed media
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
    return true
}
