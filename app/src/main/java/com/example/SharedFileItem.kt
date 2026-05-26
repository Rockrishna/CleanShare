package com.example

data class SharedFileItem(
    val id: String,
    val originalUri: String, // string of content uri
    val originalName: String,
    val currentName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localCachedPath: String, // local temporary storage in cache Dir
    
    // EXIF indicators
    val hasExif: Boolean = false,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val dateTime: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val software: String? = null,
    val artist: String? = null,
    val userComment: String? = null,
    
    // Individual scrubbing options chosen by user
    val optionScrubGps: Boolean = false,
    val optionScrubCamera: Boolean = false,
    val optionScrubDateTime: Boolean = false,
    val optionScrubAll: Boolean = false,
    
    // Custom single file overrides
    val customArtist: String? = null,
    val customDescription: String? = null,
    val customDateTime: String? = null,
    val customCameraMake: String? = null,
    val customCameraModel: String? = null
) {
    val extension: String
        get() = originalName.substringAfterLast('.', "")

    val nameWithoutExtension: String
        get() = currentName.substringBeforeLast('.', currentName)
}
