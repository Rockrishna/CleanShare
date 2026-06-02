package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.DecimalFormat

sealed class LoadingState {
    object Idle : LoadingState()
    object Loading : LoadingState()
    data class Success(val count: Int) : LoadingState()
    data class Error(val message: String) : LoadingState()
}

sealed class ShareState {
    object Idle : ShareState()
    object Processing : ShareState()
    data class Prepared(val intent: Intent, val fileUris: List<Uri>) : ShareState()
    data class Error(val message: String) : ShareState()
}

class FileViewModel : ViewModel() {

    private val _files = MutableStateFlow<List<SharedFileItem>>(emptyList())
    val files: StateFlow<List<SharedFileItem>> = _files.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

    // Batch renaming configuration states
    val findText = MutableStateFlow("")
    val replaceWithText = MutableStateFlow("")
    val batchPrefix = MutableStateFlow("")
    val batchSuffix = MutableStateFlow("")
    val batchBaseName = MutableStateFlow("")
    val startNumber = MutableStateFlow("1")

    // Global App Preferences / Settings States
    val useDynamicTheming = MutableStateFlow(true)
    val defaultScrubGps = MutableStateFlow(true)
    val defaultScrubCamera = MutableStateFlow(true)
    val defaultScrubDateTime = MutableStateFlow(true)
    val defaultScrubAll = MutableStateFlow(true)
    val autoRenameSafeHashes = MutableStateFlow(false)
    val lowercaseExtensions = MutableStateFlow(true)

    fun clearFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingState.value = LoadingState.Loading
            // Clean local cache files we created
            val list = _files.value
            for (item in list) {
                try {
                    val file = File(item.localCachedPath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("FileViewModel", "Error deleting cache file: ${e.message}")
                }
            }
            _files.value = emptyList()
            _loadingState.value = LoadingState.Idle
            _shareState.value = ShareState.Idle
        }
    }

    fun removeFileItem(context: Context, id: String) {
        val currentList = _files.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = currentList[index]
            try {
                val file = File(item.localCachedPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("FileViewModel", "Error deleting item: ${e.message}")
            }
            currentList.removeAt(index)
            _files.value = currentList
        }
    }

    fun resetShareState() {
        _shareState.value = ShareState.Idle
    }

    fun loadUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _loadingState.value = LoadingState.Loading
            val loadedItems = mutableListOf<SharedFileItem>()

            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    try {
                        var originalName = getFileNameFromUri(context, uri) ?: "shared_file_${System.currentTimeMillis()}_$index"
                        if (lowercaseExtensions.value) {
                            val ext = originalName.substringAfterLast('.', "")
                            if (ext.isNotEmpty()) {
                                originalName = originalName.substringBeforeLast('.', originalName) + "." + ext.lowercase()
                            }
                        }
                        val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(originalName)
                        val size = getFileSizeFromUri(context, uri)

                        // Copy to app's secure sandbox cache directory
                        val tempDir = File(context.cacheDir, "shared_temp")
                        if (!tempDir.exists()) {
                            tempDir.mkdirs()
                        }
                        
                        // Use a safe random hash prefix to avoid filename collisions while maintaining the original name
                        val randomPrefix = SecureRandom().nextInt(1000000).toString()
                        val cacheFile = File(tempDir, "${randomPrefix}_$originalName")
                        
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            FileOutputStream(cacheFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        if (cacheFile.exists()) {
                            // Extract metadata
                            val isSupportedImage = mimeType.contains("image", ignoreCase = true) && 
                                (mimeType.contains("jpeg", ignoreCase = true) || 
                                 mimeType.contains("jpg", ignoreCase = true) || 
                                 mimeType.contains("png", ignoreCase = true) || 
                                 mimeType.contains("webp", ignoreCase = true) || 
                                 mimeType.contains("heic", ignoreCase = true) || 
                                 mimeType.contains("heif", ignoreCase = true))

                            var hasExif = false
                            var gpsLat: Double? = null
                            var gpsLong: Double? = null
                            var dt: String? = null
                            var make: String? = null
                            var model: String? = null
                            var sw: String? = null
                            var art: String? = null
                            var comment: String? = null

                            if (isSupportedImage) {
                                try {
                                    val exif = ExifInterface(cacheFile.absolutePath)
                                    hasExif = true
                                    
                                    val latLong = exif.latLong
                                    if (latLong != null && latLong.size >= 2) {
                                        gpsLat = latLong[0]
                                        gpsLong = latLong[1]
                                    }
                                    
                                    dt = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: 
                                         exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                                    make = exif.getAttribute(ExifInterface.TAG_MAKE)
                                    model = exif.getAttribute(ExifInterface.TAG_MODEL)
                                    sw = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                                    art = exif.getAttribute(ExifInterface.TAG_ARTIST)
                                    comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                                } catch (e: Exception) {
                                    Log.e("FileViewModel", "ExifInterface error: ${e.message}")
                                }
                            }

                            loadedItems.add(
                                SharedFileItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    originalUri = uri.toString(),
                                    originalName = originalName,
                                    currentName = originalName,
                                    sizeBytes = size,
                                    mimeType = mimeType,
                                    localCachedPath = cacheFile.absolutePath,
                                    hasExif = hasExif,
                                    gpsLatitude = gpsLat,
                                    gpsLongitude = gpsLong,
                                    dateTime = dt,
                                    cameraMake = make,
                                    cameraModel = model,
                                    software = sw,
                                    artist = art,
                                    userComment = comment,
                                    optionScrubGps = defaultScrubGps.value,
                                    optionScrubCamera = defaultScrubCamera.value,
                                    optionScrubDateTime = defaultScrubDateTime.value,
                                    optionScrubAll = defaultScrubAll.value
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("FileViewModel", "Error loading uri: ${e.message}")
                    }
                }
            }

            val currentList = _files.value.toMutableList()
            currentList.addAll(loadedItems)
            _files.value = currentList
            _loadingState.value = LoadingState.Success(loadedItems.size)
        }
    }

    // Interactive operations: update single item filename
    fun updateFilename(id: String, newName: String) {
        val list = _files.value.map { item ->
            if (item.id == id) {
                // Ensure proper extension is retained or appended if not present
                val currentExt = item.extension
                var cleanName = newName.trim()
                if (currentExt.isNotEmpty() && !cleanName.endsWith(".$currentExt", ignoreCase = true)) {
                    // Try to clear any wrong extension and add correct one
                    val editedExt = cleanName.substringAfterLast('.', "")
                    if (editedExt != currentExt) {
                        cleanName = "$cleanName.$currentExt"
                    }
                }
                item.copy(currentName = cleanName)
            } else {
                item
            }
        }
        _files.value = list
    }

    // Toggle specific metadata scrubbing variables for an individual file
    fun toggleScrubGps(id: String, value: Boolean) {
        _files.value = _files.value.map { if (it.id == id) it.copy(optionScrubGps = value) else it }
    }

    fun toggleScrubCamera(id: String, value: Boolean) {
        _files.value = _files.value.map { if (it.id == id) it.copy(optionScrubCamera = value) else it }
    }

    fun toggleScrubDateTime(id: String, value: Boolean) {
        _files.value = _files.value.map { if (it.id == id) it.copy(optionScrubDateTime = value) else it }
    }

    fun toggleScrubAll(id: String, value: Boolean) {
        _files.value = _files.value.map { if (it.id == id) it.copy(optionScrubAll = value) else it }
    }

    fun toggleBatchScrubGps(value: Boolean) {
        defaultScrubGps.value = value
        _files.value = _files.value.map { if (it.hasExif) it.copy(optionScrubGps = value) else it }
    }

    fun toggleBatchScrubCamera(value: Boolean) {
        defaultScrubCamera.value = value
        _files.value = _files.value.map { if (it.hasExif) it.copy(optionScrubCamera = value) else it }
    }

    fun toggleBatchScrubDateTime(value: Boolean) {
        defaultScrubDateTime.value = value
        _files.value = _files.value.map { if (it.hasExif) it.copy(optionScrubDateTime = value) else it }
    }

    fun toggleBatchScrubAll(value: Boolean) {
        defaultScrubAll.value = value
        _files.value = _files.value.map { if (it.hasExif) it.copy(optionScrubAll = value) else it }
    }

    fun resetBatchFilenames() {
        _files.value = _files.value.map { it.copy(currentName = it.originalName) }
    }

    fun updateCustomArtist(id: String, artist: String?) {
        _files.value = _files.value.map { if (it.id == id) it.copy(customArtist = artist) else it }
    }

    fun updateCustomDescription(id: String, desc: String?) {
        _files.value = _files.value.map { if (it.id == id) it.copy(customDescription = desc) else it }
    }

    fun updateCustomDateTime(id: String, dateTime: String?) {
        _files.value = _files.value.map { if (it.id == id) it.copy(customDateTime = dateTime) else it }
    }

    fun updateCustomCameraMake(id: String, make: String?) {
        _files.value = _files.value.map { if (it.id == id) it.copy(customCameraMake = make) else it }
    }

    fun updateCustomCameraModel(id: String, model: String?) {
        _files.value = _files.value.map { if (it.id == id) it.copy(customCameraModel = model) else it }
    }

    // BATCH RENAMING SCHEMES
    fun applyBatchFindAndReplace() {
        val find = findText.value
        val replace = replaceWithText.value
        if (find.isEmpty()) return

        _files.value = _files.value.map { item ->
            val ext = item.extension
            val baseName = item.nameWithoutExtension
            val newBaseName = baseName.replace(find, replace)
            val finalName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName
            item.copy(currentName = finalName)
        }
    }

    fun applyBatchPrefixSuffix() {
        val prefix = batchPrefix.value.trim()
        val suffix = batchSuffix.value.trim()
        if (prefix.isEmpty() && suffix.isEmpty()) return

        _files.value = _files.value.map { item ->
            val ext = item.extension
            val baseName = item.nameWithoutExtension
            val newBaseName = "$prefix$baseName$suffix"
            val finalName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName
            item.copy(currentName = finalName)
        }
    }

    fun applyBatchSequentialRenaming() {
        val base = batchBaseName.value.trim()
        if (base.isEmpty()) return
        
        val startVal = startNumber.value.toIntOrNull() ?: 1

        _files.value = _files.value.mapIndexed { index, item ->
            val ext = item.extension
            val currentSeq = startVal + index
            
            // Format number gracefully: e.g., 001, 002, or simple digits depending on count size
            val formatter = DecimalFormat("000")
            val seqString = if (_files.value.size >= 100) formatter.format(currentSeq) else String.format("%02d", currentSeq)
            
            val finalName = if (ext.isNotEmpty()) "${base}_$seqString.$ext" else "${base}_$seqString"
            item.copy(currentName = finalName)
        }
    }

    fun applyBatchDateRenaming() {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val currentDateStr = sdf.format(java.util.Date())

        _files.value = _files.value.mapIndexed { index, item ->
            val ext = item.extension
            // If the item has EXIF timestamp format "yyyy:MM:dd HH:mm:ss", filter digits and take first 8 ("yyyyMMdd")
            val rawTime = item.customDateTime ?: item.dateTime
            val fileDate = if (!rawTime.isNullOrBlank()) {
                val digits = rawTime.filter { it.isDigit() }
                if (digits.length >= 8) digits.take(8) else currentDateStr
            } else {
                currentDateStr
            }
            val seqString = String.format("%02d", index + 1)
            val finalName = if (ext.isNotEmpty()) "${fileDate}_$seqString.$ext" else "${fileDate}_$seqString"
            item.copy(currentName = finalName)
        }
    }

    fun applyBatchCaseConversion(allUppercase: Boolean) {
        _files.value = _files.value.map { item ->
            val ext = item.extension
            val baseName = item.nameWithoutExtension
            val newBase = if (allUppercase) baseName.uppercase() else baseName.lowercase()
            val finalName = if (ext.isNotEmpty()) "$newBase.$ext" else newBase
            item.copy(currentName = finalName)
        }
    }

    // BATCH METADATA SCRUBBING SCHEMES
    fun applyBatchScrubAction(scrubGps: Boolean, scrubCamera: Boolean, scrubDate: Boolean, scrubAll: Boolean) {
        _files.value = _files.value.map { item ->
            if (item.hasExif) {
                item.copy(
                    optionScrubGps = scrubGps,
                    optionScrubCamera = scrubCamera,
                    optionScrubDateTime = scrubDate,
                    optionScrubAll = scrubAll
                )
            } else {
                item
            }
        }
    }

    // FINAL OUTPUT GENERATION & SHARE TRIGGERING
    fun processAndPrepareShare(context: Context) {
        if (_files.value.isEmpty()) return

        // Auto-apply active bulk renaming parameters if configure fields are not empty
        if (findText.value.isNotEmpty()) {
            applyBatchFindAndReplace()
        }
        val prefix = batchPrefix.value.trim()
        val suffix = batchSuffix.value.trim()
        if (prefix.isNotEmpty() || suffix.isNotEmpty()) {
            applyBatchPrefixSuffix()
        }
        if (batchBaseName.value.trim().isNotEmpty()) {
            applyBatchSequentialRenaming()
        }

        _shareState.value = ShareState.Processing

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Create a dedicated directory under internal cache folder for processed files
                    val processedDir = File(context.cacheDir, "processed_items")
                    if (processedDir.exists()) {
                        processedDir.deleteRecursively()
                    }
                    processedDir.mkdirs()

                    val preparedUris = mutableListOf<Uri>()
                    var mimeTypeSet = mutableSetOf<String>()

                    _files.value.forEach { item ->
                        val originalFile = File(item.localCachedPath)
                        if (originalFile.exists()) {
                            // Target location using the user's custom currentName or safe random hash
                            val finalShareName = if (autoRenameSafeHashes.value) {
                                val ext = item.extension
                                val randomHash = "clean_" + java.util.UUID.randomUUID().toString().take(8)
                                if (ext.isNotEmpty()) "$randomHash.$ext" else randomHash
                            } else {
                                item.currentName
                            }
                            val targetFile = File(processedDir, finalShareName)
                            
                            // Copy bytes to target destination
                            originalFile.inputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            // Perform EXIF modifications on the target copy if supported or enabled
                            if (item.hasExif) {
                                try {
                                    val exif = ExifInterface(targetFile.absolutePath)
                                    var updated = false

                                    if (item.optionScrubAll) {
                                        val commonTags = listOf(
                                            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
                                            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
                                            ExifInterface.TAG_GPS_PROCESSING_METHOD,
                                            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST, ExifInterface.TAG_USER_COMMENT,
                                            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
                                            ExifInterface.TAG_EXIF_VERSION, ExifInterface.TAG_FLASH, ExifInterface.TAG_FOCAL_LENGTH,
                                            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_COPYRIGHT
                                        )
                                        for (tag in commonTags) {
                                            exif.setAttribute(tag, null)
                                        }
                                        updated = true
                                    } else {
                                        if (item.optionScrubGps) {
                                            val gpsTags = listOf(
                                                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
                                                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
                                                ExifInterface.TAG_GPS_PROCESSING_METHOD
                                            )
                                            for (tag in gpsTags) {
                                                exif.setAttribute(tag, null)
                                            }
                                            updated = true
                                        }
                                        if (item.optionScrubCamera) {
                                            val hardwareTags = listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE)
                                            for (tag in hardwareTags) {
                                                exif.setAttribute(tag, null)
                                            }
                                            updated = true
                                        }
                                        if (item.optionScrubDateTime) {
                                            val dateTags = listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED)
                                            for (tag in dateTags) {
                                                exif.setAttribute(tag, null)
                                            }
                                            updated = true
                                        }
                                    }

                                    // Apply custom tag overrides (Artist, Description, DateTime, Camera specs) if configured
                                    if (!item.optionScrubAll) {
                                        if (item.customArtist != null) {
                                            exif.setAttribute(ExifInterface.TAG_ARTIST, item.customArtist)
                                            updated = true
                                        }
                                        if (item.customDescription != null) {
                                            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, item.customDescription)
                                            updated = true
                                        }
                                        if (item.customDateTime != null) {
                                            exif.setAttribute(ExifInterface.TAG_DATETIME, item.customDateTime)
                                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, item.customDateTime)
                                            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, item.customDateTime)
                                            updated = true
                                        }
                                        if (item.customCameraMake != null) {
                                            exif.setAttribute(ExifInterface.TAG_MAKE, item.customCameraMake)
                                            updated = true
                                        }
                                        if (item.customCameraModel != null) {
                                            exif.setAttribute(ExifInterface.TAG_MODEL, item.customCameraModel)
                                            updated = true
                                        }
                                    }

                                    if (updated) {
                                        exif.saveAttributes()
                                        Log.d("FileViewModel", "Successfully saved EXIF updates on: ${item.currentName}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("FileViewModel", "Error editing EXIF attributes: ${e.message}")
                                }
                            }

                            // Share using content:// authority generated via our declared FileProvider
                            val authority = "com.aistudio.sharecleaner.qxwdzs.fileprovider"
                            val contentUri = FileProvider.getUriForFile(context, authority, targetFile)
                            preparedUris.add(contentUri)
                            mimeTypeSet.add(item.mimeType)
                        }
                    }

                    if (preparedUris.isNotEmpty()) {
                        val shareIntent = Intent().apply {
                            if (preparedUris.size == 1) {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, preparedUris[0])
                                type = mimeTypeSet.firstOrNull() ?: "*/*"
                            } else {
                                action = Intent.ACTION_SEND_MULTIPLE
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(preparedUris))
                                // If multiple mime types, use wildcard, otherwise specific
                                type = if (mimeTypeSet.size == 1) mimeTypeSet.first() else "*/*"
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        _shareState.value = ShareState.Prepared(shareIntent, preparedUris)
                    } else {
                        _shareState.value = ShareState.Error("No files could be processed successfully.")
                    }

                } catch (e: Exception) {
                    Log.e("FileViewModel", "Error preparing files for sharing: ${e.message}")
                    _shareState.value = ShareState.Error("Failed to bundle files: ${e.localizedMessage}")
                }
            }
        }
    }

    // UTILITY METHODS
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            name = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileViewModel", "Error querying filename: ${e.message}")
            }
        }
        if (name == null) {
            val path = uri.path
            val cut = path?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = path?.substring(cut + 1)
            }
        }
        return name
    }

    private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var size: Long = 0
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (colIndex != -1) {
                            size = cursor.getLong(colIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileViewModel", "Error querying file size: ${e.message}")
            }
        }
        if (size == 0L) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                size = pfd?.statSize ?: 0L
                pfd?.close()
            } catch (e: Exception) {
                Log.e("FileViewModel", "Error reading file descriptor size: ${e.message}")
            }
        }
        return size
    }

    private fun getMimeTypeFromExtension(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }
}
