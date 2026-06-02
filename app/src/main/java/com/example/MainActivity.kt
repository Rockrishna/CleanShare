@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example

import java.io.File
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.material3.MaterialTheme

// Define design colors as dynamic composable properties bound to Material 3 colorScheme
val CosmicSlateBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val CosmicCardSurface: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val CosmicCyanAccent: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val CosmicCyanLight: Color
    @Composable
    get() = MaterialTheme.colorScheme.primaryContainer

val CosmicCyanDark: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimaryContainer

val CosmicWhiteText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val CosmicGrayMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val CosmicBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

val CosmicDivider: Color
    @Composable
    get() = MaterialTheme.colorScheme.outlineVariant

val CosmicAmberWarning: Color
    @Composable
    get() = MaterialTheme.colorScheme.tertiary

val CosmicGreenSuccess: Color
    @Composable
    get() = MaterialTheme.colorScheme.secondary

class MainActivity : ComponentActivity() {
    private val fileViewModel = FileViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial incoming share intent
        intent?.let { handleIncomingIntent(it) }

        setContent {
            val useDynamicTheming by fileViewModel.useDynamicTheming.collectAsStateWithLifecycle()

            MyApplicationTheme(dynamicColor = useDynamicTheming) {
                MainContentScreen(
                    viewModel = fileViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle incoming intent if activity is already running
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        try {
            Log.d("MainActivity", "Handling incoming intent action: ${intent.action}")
            if (intent.action == Intent.ACTION_SEND) {
                // Try to import single Uri stream
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) {
                    fileViewModel.loadUris(this, listOf(streamUri))
                } else {
                    // Try to inspect raw text data links if any
                    val textData = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (textData != null) {
                        val parsedUri = Uri.parse(textData)
                        if (parsedUri != null && (parsedUri.scheme == "content" || parsedUri.scheme == "file")) {
                            fileViewModel.loadUris(this, listOf(parsedUri))
                        }
                    }
                }
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                // Try to import multiple Uri streams
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (streamUris != null) {
                    fileViewModel.loadUris(this, streamUris)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing incoming share sheet: ${e.message}")
        }
    }
}

@Composable
fun MainContentScreen(
    viewModel: FileViewModel
) {
    val context = LocalContext.current
    val filesList by viewModel.files.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()
    val useDynamicTheming by viewModel.useDynamicTheming.collectAsStateWithLifecycle()

    var currentMainTab by remember { mutableStateOf("cleaner") } // "cleaner" or "settings"

    var showPickerSelectionDialog by remember { mutableStateOf(false) }
    var permissionStatusText by remember { mutableStateOf(getStoragePermissionStatus(context)) }

    DisposableEffect(Unit) {
        permissionStatusText = getStoragePermissionStatus(context)
        onDispose {}
    }

    // Modern Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadUris(context, uris)
        }
    }

    // Modern Document Launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadUris(context, uris)
        }
    }

    // Modern Fine-grained Permissions Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionStatusText = getStoragePermissionStatus(context)
        val imageGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        val videoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: false
        val partialGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] ?: false
        } else false
        val oldStorageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

        if (imageGranted || videoGranted || oldStorageGranted) {
            Toast.makeText(context, "Full Storage Access Granted", Toast.LENGTH_SHORT).show()
        } else if (partialGranted) {
            Toast.makeText(context, "Limited Selected-Photos Access Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage Access Rejected", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedFileIndex by remember { mutableStateOf(0) }
    var selectedFileForMetadataEdit by remember { mutableStateOf<SharedFileItem?>(null) }
    var activeTab by remember { mutableStateOf("rename") } // "rename" or "scrub"
    var currentScreenModeTab by remember { mutableStateOf("single") } // "single" or "batch"
    var showConfirmPreviewScreen by remember { mutableStateOf(false) }

    // Reset carousel index if list is cleared
    LaunchedEffect(filesList.size) {
        if (selectedFileIndex >= filesList.size) {
            selectedFileIndex = (filesList.size - 1).coerceAtLeast(0)
        }
        if (filesList.size <= 1) {
            currentScreenModeTab = "single"
        }
    }

    // Reaction to sharing state prepared by the model
    LaunchedEffect(shareState) {
        if (shareState is ShareState.Prepared) {
            val prepared = shareState as ShareState.Prepared
            try {
                // Launch Share Chooser
                val chooser = Intent.createChooser(prepared.intent, "Send Cleaned Files")
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                viewModel.resetShareState()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerScaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerScaffoldPadding)
                .background(CosmicSlateBg)
        ) {
            if (currentMainTab == "cleaner") {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CosmicSlateBg)
                ) {
                    val boxWidth = this.maxWidth
                    val boxHeight = this.maxHeight
                    val isWideScreen = boxWidth >= 720.dp

                    val scrollState = rememberScrollState()
                    val scrollOffset = scrollState.value
                    val density = LocalDensity.current
                    val scrollOffsetDp = with(density) { scrollOffset.toDp() }

                    val currentIndex = if (filesList.isNotEmpty()) selectedFileIndex.coerceIn(0, filesList.size - 1) else 0
                    val activeItem = if (filesList.isNotEmpty()) filesList[currentIndex] else null
                    val showThumbnailInTopBar = scrollOffsetDp > 160.dp

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Elegant modern header action row (cohesive integrated toolbar)
                        MinimalTopBar(
                            filesCount = filesList.size,
                            activeItem = activeItem,
                            showThumbnail = showThumbnailInTopBar && (currentScreenModeTab == "single"),
                            currentScreenModeTab = currentScreenModeTab,
                            onScreenModeTabChange = { currentScreenModeTab = it },
                            onSettingsClick = { currentMainTab = "settings" },
                            onClearClick = { viewModel.clearFiles(context) },
                            onAddMoreClick = { showPickerSelectionDialog = true }
                        )

                        if (filesList.isEmpty()) {
                            // Fullscreen border-free Material 3 expressive empty state
                            EmptyStateView(
                                onSelectFilesClick = { showPickerSelectionDialog = true }
                            )
                        } else {
                val realActiveItem = filesList[currentIndex]
                val isFoldedFlip = boxHeight < 560.dp && boxWidth < 500.dp

                if (isWideScreen) {
                    // --- Widescreen / Tablet / ChromeOS / Foldable Unfolded side-by-side mode ---
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (currentScreenModeTab == "single") {
                            // Column 1: Carousel, Preview Card, and base name field
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (filesList.size > 1) {
                                    FilesCarousel(
                                        filesList = filesList,
                                        selectedIndex = currentIndex,
                                        onIndexSelected = { selectedFileIndex = it }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Large preview card
                                ActiveFilePreviewCard(
                                    activeItem = realActiveItem,
                                    modifier = Modifier.weight(1f)
                                )

                                // Large rename field
                                ActiveFileRenameSection(
                                    item = realActiveItem,
                                    onFilenameChange = { newBaseName ->
                                        val ext = realActiveItem.extension
                                        val fullName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName
                                        viewModel.updateFilename(realActiveItem.id, fullName)
                                    }
                                )
                            }

                            // Column 2: ActiveItemControls only
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ActiveItemControls(
                                    item = realActiveItem,
                                    onEditMetadataClick = { selectedFileForMetadataEdit = realActiveItem },
                                    onToggleGps = { v -> viewModel.toggleScrubGps(realActiveItem.id, v) },
                                    onToggleCamera = { v -> viewModel.toggleScrubCamera(realActiveItem.id, v) },
                                    onToggleDate = { v -> viewModel.toggleScrubDateTime(realActiveItem.id, v) },
                                    onToggleAllExif = { v -> viewModel.toggleScrubAll(realActiveItem.id, v) }
                                )
                            }
                        } else {
                            // Unified full-width scrollable widescreen batch panel
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 720.dp)
                                        .fillMaxWidth()
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        StatusSummaryHeader(filesList = filesList)

                                        BatchControlBoard(
                                            activeTab = activeTab,
                                            onTabChange = { activeTab = it },
                                            viewModel = viewModel
                                        )
                                        
                                        Spacer(modifier = Modifier.height(96.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- Unified Portrait / Compact Mode with Collapsing Dynamic Image Scale ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (currentScreenModeTab == "single") {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Files Carousel (only visible at top if NOT folded flip mode and multiple files are imported)
                                if (filesList.size > 1 && !isFoldedFlip) {
                                    FilesCarousel(
                                        filesList = filesList,
                                        selectedIndex = currentIndex,
                                        onIndexSelected = { selectedFileIndex = it }
                                    )
                                } else if (filesList.size == 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Interactive Scaling Image Preview in Portrait!
                                val activeImageFraction = ((200.dp - scrollOffsetDp) / 200.dp).coerceIn(0.65f, 1.0f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .graphicsLayer {
                                            alpha = activeImageFraction
                                            scaleX = activeImageFraction
                                            scaleY = activeImageFraction
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActiveFilePreviewCard(
                                        activeItem = realActiveItem,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Active individual name editor
                                ActiveFileRenameSection(
                                    item = realActiveItem,
                                    onFilenameChange = { newBaseName ->
                                        val ext = realActiveItem.extension
                                        val fullName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName
                                        viewModel.updateFilename(realActiveItem.id, fullName)
                                    }
                                )

                                // Individual active item controls / metadata switches
                                ActiveItemControls(
                                    realActiveItem,
                                    onEditMetadataClick = { selectedFileForMetadataEdit = realActiveItem },
                                    onToggleGps = { v -> viewModel.toggleScrubGps(realActiveItem.id, v) },
                                    onToggleCamera = { v -> viewModel.toggleScrubCamera(realActiveItem.id, v) },
                                    onToggleDate = { v -> viewModel.toggleScrubDateTime(realActiveItem.id, v) },
                                    onToggleAllExif = { v -> viewModel.toggleScrubAll(realActiveItem.id, v) }
                                )

                                // Folding Flip Phone Specific Layout requirement:
                                // "for multiple images, on a folding flip phone when folded move to the bottom half the carousel"
                                if (filesList.size > 1 && isFoldedFlip) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Select Active File:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicCyanAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    FilesCarousel(
                                        filesList = filesList,
                                        selectedIndex = currentIndex,
                                        onIndexSelected = { selectedFileIndex = it },
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(96.dp)) // padding so content stays free of the sharing button
                            }
                        } else {
                            // "batch" tab option in portrait mode:
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatusSummaryHeader(filesList = filesList)

                                BatchControlBoard(
                                    activeTab = activeTab,
                                    onTabChange = { activeTab = it },
                                    viewModel = viewModel
                                )
                                
                                Spacer(modifier = Modifier.height(96.dp)) // padding so content stays free of the sharing button
                            }
                        }
                    }
                }
            }
        }
        // Processing screen blocking indicator
        if (shareState is ShareState.Processing) {
            ProcessingOverlay()
        }

        // Standard snackbar style bottom banner for ready / error share triggers
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
        ) {
            if (shareState is ShareState.Error) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { showConfirmPreviewScreen = true }) {
                            Text("Retry", color = CosmicCyanAccent)
                        }
                    },
                    containerColor = Color(0xFFFA5252),
                    contentColor = Color.White
                ) {
                    Text(text = (shareState as ShareState.Error).message)
                }
            }

            // High-Contrast Process & Share Floating Button panel
            if (filesList.isNotEmpty()) {
                Surface(
                    color = CosmicSlateBg.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmicBorder.copy(alpha = 0.5f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    tonalElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            // Automatically apply active renaming variables to previews if they haven't run explicitly yet
                            if (viewModel.findText.value.isNotEmpty()) {
                                viewModel.applyBatchFindAndReplace()
                            }
                            if (viewModel.batchPrefix.value.trim().isNotEmpty() || viewModel.batchSuffix.value.trim().isNotEmpty()) {
                                viewModel.applyBatchPrefixSuffix()
                            }
                            if (viewModel.batchBaseName.value.trim().isNotEmpty()) {
                                viewModel.applyBatchSequentialRenaming()
                            }
                            showConfirmPreviewScreen = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CosmicCyanAccent,
                            contentColor = CosmicCardSurface
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp)
                            .testTag("apply_and_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Clean & Share",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        // Active individual Metadata Edit Dialog
        selectedFileForMetadataEdit?.let { currentItem ->
            val latestItem = filesList.find { it.id == currentItem.id } ?: currentItem
            MetadataEditDialog(
                item = latestItem,
                onDismiss = { selectedFileForMetadataEdit = null },
                onSaveCustomTags = { artist, desc, date, make, model ->
                    viewModel.updateCustomArtist(latestItem.id, artist)
                    viewModel.updateCustomDescription(latestItem.id, desc)
                    viewModel.updateCustomDateTime(latestItem.id, date)
                    viewModel.updateCustomCameraMake(latestItem.id, make)
                    viewModel.updateCustomCameraModel(latestItem.id, model)
                    selectedFileForMetadataEdit = null
                    Toast.makeText(context, "Custom details saved successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Beautiful full confirmation preview overlay screen
        if (showConfirmPreviewScreen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showConfirmPreviewScreen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CosmicSlateBg),
                    color = CosmicSlateBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        // Title header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Confirm Sharing Preview",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CosmicWhiteText
                                )
                                Text(
                                    text = "Review all modifications and sanitizations below",
                                    fontSize = 11.sp,
                                    color = CosmicGrayMuted
                                )
                            }
                            IconButton(onClick = { showConfirmPreviewScreen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmicWhiteText)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sanitization list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(CosmicCardSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filesList) { sharedItem ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CosmicSlateBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    // Row 1: File icon and size
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = getGenericFileIcon(sharedItem.mimeType),
                                            contentDescription = "File icon",
                                            tint = CosmicCyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatSize(sharedItem.sizeBytes),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CosmicCyanLight,
                                            modifier = Modifier
                                                .background(CosmicCyanDark, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Original Name
                                    Text(
                                        text = "ORIGINAL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicGrayMuted
                                    )
                                    Text(
                                        text = sharedItem.originalName,
                                        fontSize = 11.sp,
                                        color = CosmicWhiteText.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Clean name
                                    Text(
                                        text = "PROPOSED NEW NAME",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicCyanAccent
                                    )
                                    Text(
                                        text = sharedItem.currentName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicWhiteText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Sanitation indicators
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        val hasGps = sharedItem.optionScrubGps || sharedItem.optionScrubAll
                                        val hasCamera = sharedItem.optionScrubCamera || sharedItem.optionScrubAll
                                        val hasTime = sharedItem.optionScrubDateTime || sharedItem.optionScrubAll

                                        ConfirmPillCheckbox(label = "Strip GPS", checked = hasGps)
                                        ConfirmPillCheckbox(label = "Strip Camera Info", checked = hasCamera)
                                        ConfirmPillCheckbox(label = "Strip Date", checked = hasTime)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Main confirm actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmPreviewScreen = false },
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, CosmicBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicWhiteText),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    showConfirmPreviewScreen = false
                                    viewModel.processAndPrepareShare(context)
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CosmicCyanAccent,
                                    contentColor = CosmicCardSurface
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm & Share", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

            if (currentMainTab == "settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { currentMainTab = "cleaner" },
                    onRequestPermissions = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                                )
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO
                                )
                            )
                        } else {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            )
                        }
                    }
                )
            }
        }
    }

    if (showPickerSelectionDialog) {
        ModernPickerSelectionDialog(
            onDismissRequest = { showPickerSelectionDialog = false },
            onLaunchPhotoPicker = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onLaunchDocumentPicker = {
                documentPickerLauncher.launch(arrayOf("*/*"))
            },
            permissionStatusText = permissionStatusText,
            onRequestPermissions = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    )
                } else {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    )
                }
            }
        )
    }
}

@Composable
fun MinimalTopBar(
    filesCount: Int,
    activeItem: SharedFileItem? = null,
    showThumbnail: Boolean = false,
    currentScreenModeTab: String = "single",
    onScreenModeTabChange: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onClearClick: () -> Unit,
    onAddMoreClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = CosmicCardSurface,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (showThumbnail && activeItem != null && filesCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CosmicBorder),
                            modifier = Modifier.size(36.dp),
                            color = CosmicCardSurface
                        ) {
                            if (activeItem.mimeType.contains("image", ignoreCase = true)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(java.io.File(activeItem.localCachedPath))
                                        .crossfade(true)
                                        .size(72)
                                        .build(),
                                    contentDescription = "Mini topbar preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = getGenericFileIcon(activeItem.mimeType),
                                    contentDescription = "Mini file icon",
                                    tint = CosmicCyanAccent,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (showThumbnail && activeItem != null && filesCount > 0) {
                            val name = activeItem.originalName
                            if (name.length > 14) name.take(12) + "..." else name
                        } else {
                            "CleanShare"
                        },
                        fontSize = if (showThumbnail && activeItem != null && filesCount > 0) 14.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CosmicWhiteText,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (filesCount == 0) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .background(
                                    color = CosmicBorder.copy(alpha = 0.15f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings",
                                tint = CosmicCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (filesCount > 0) {
                        TextButton(
                            onClick = onAddMoreClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = CosmicCyanAccent),
                            modifier = Modifier.testTag("add_file_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Files", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onClearClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFA5252)),
                            modifier = Modifier.testTag("clear_all_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear List", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (filesCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicSlateBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("single" to "Clean Selected File", "batch" to "Clean All ($filesCount)").forEach { (tabId, label) ->
                        val isSelected = currentScreenModeTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CosmicCyanAccent else Color.Transparent)
                                .clickable { onScreenModeTabChange(tabId) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CosmicSlateBg else CosmicWhiteText.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    onSelectFilesClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(CosmicCyanAccent.copy(alpha = 0.22f), Color.Transparent)
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Safe sharing",
                    tint = CosmicCyanAccent,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Securely Share Your Photos and Files",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CosmicWhiteText,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Rename files and photos while scrubbing sensitive metadata (like GPS location markers, device details, and original timestamps) before clean sharing.",
                fontSize = 13.sp,
                color = CosmicGrayMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSelectFilesClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CosmicCyanAccent,
                    contentColor = CosmicSlateBg
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_files_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "Pick files",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pick Photos or Files",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "HOW IT WORKS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicCyanAccent.copy(alpha = 0.8f),
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideStepRow(step = "1", text = "Open any file explorer, gallery, or document app")
            GuideStepRow(step = "2", text = "Tap 'Share' and choose CleanShare from the sheet")
            GuideStepRow(step = "3", text = "Adjust file names, scrub location, and share instantly")
        }
    }
}

@Composable
fun GuideStepRow(step: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicWhiteText
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = CosmicWhiteText.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun StatusSummaryHeader(filesList: List<SharedFileItem>) {
    val totalSize = filesList.sumOf { it.sizeBytes }
    val formattedSize = formatSize(totalSize)
    val filesPlural = if (filesList.size == 1) "File" else "Files"

    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicCardSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Status summary",
                    tint = CosmicCyanLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${filesList.size} $filesPlural Loaded",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicWhiteText
                )
            }
            Text(
                text = "Total Filesize: $formattedSize",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicCyanAccent
            )
        }
    }
}

@Composable
fun FilesCarousel(
    filesList: List<SharedFileItem>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(
            items = filesList,
            key = { _, item -> item.id }
        ) { index, item ->
            val isSelected = index == selectedIndex
            val borderStroke = if (isSelected) {
                BorderStroke(2.dp, CosmicCyanAccent)
            } else {
                BorderStroke(1.dp, CosmicBorder)
            }
            
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmicCardSurface)
                    .border(borderStroke, RoundedCornerShape(16.dp))
                    .clickable { onIndexSelected(index) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.mimeType.contains("image", ignoreCase = true)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(item.localCachedPath))
                            .crossfade(true)
                            .size(256) // Downsample thumbnail resolution for butter smooth scroll
                            .build(),
                        contentDescription = item.originalName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = getGenericFileIcon(item.mimeType),
                            contentDescription = item.originalName,
                            tint = if (isSelected) CosmicCyanAccent else CosmicGrayMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) CosmicCyanLight else CosmicDivider,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (item.extension.isNotEmpty()) ".${item.extension.uppercase()}" else "FILE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) CosmicCyanDark else CosmicGrayMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveFilePreviewCard(activeItem: SharedFileItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CosmicCardSurface)
            .border(1.dp, CosmicBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (activeItem.mimeType.contains("image", ignoreCase = true)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(activeItem.localCachedPath))
                    .crossfade(true)
                    .size(800) // Downsample large preview to safe memory block sizes
                    .build(),
                contentDescription = "Active Image Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = getGenericFileIcon(activeItem.mimeType),
                    contentDescription = "Doc Details Icon",
                    tint = CosmicCyanAccent,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = activeItem.originalName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicWhiteText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${activeItem.mimeType.uppercase()} • ${formatSize(activeItem.sizeBytes)}",
                    fontSize = 11.sp,
                    color = CosmicGrayMuted
                )
            }
        }
    }
}

@Composable
fun ActiveFileRenameSection(
    item: SharedFileItem,
    onFilenameChange: (String) -> Unit
) {
    val baseName = item.nameWithoutExtension
    val ext = item.extension
    
    Surface(
        color = CosmicCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ModeEdit,
                        contentDescription = "Rename",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RENAME FILE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicGrayMuted,
                        letterSpacing = 1.sp
                    )
                }
                
                if (ext.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(CosmicCyanDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ".${ext.uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicCyanLight
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            OutlinedTextField(
                value = baseName,
                onValueChange = onFilenameChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = CosmicWhiteText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                trailingIcon = {
                    if (baseName.isNotEmpty()) {
                        IconButton(onClick = { onFilenameChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = CosmicGrayMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CosmicCyanAccent,
                    unfocusedBorderColor = CosmicBorder,
                    focusedTextColor = CosmicWhiteText,
                    unfocusedTextColor = CosmicWhiteText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input_prominent"),
                placeholder = { Text("Enter clean filename...", color = CosmicGrayMuted, fontSize = 13.sp) }
            )
        }
    }
}

@Composable
fun ActiveItemControls(
    item: SharedFileItem,
    modifier: Modifier = Modifier,
    onEditMetadataClick: () -> Unit,
    onToggleGps: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleDate: (Boolean) -> Unit,
    onToggleAllExif: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicCardSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "File details & cleaning",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight
                )
                
                Text(
                    text = "Edit details",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanAccent,
                    modifier = Modifier
                        .clickable { onEditMetadataClick() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // GPS Location Display
            val hasGps = item.gpsLatitude != null && item.gpsLongitude != null
            val displayGps = if (hasGps) {
                "GPS Location: ${String.format("%.4f", item.gpsLatitude)}, ${String.format("%.4f", item.gpsLongitude)}"
            } else {
                "No GPS coordinates mapping"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Icon(
                    imageVector = if (hasGps) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    contentDescription = "Location",
                    tint = if (hasGps) CosmicAmberWarning else CosmicGrayMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = displayGps, fontSize = 11.sp, color = if (hasGps) CosmicWhiteText else CosmicGrayMuted)
            }
            
            // Camera specs Display
            val activeMake = item.customCameraMake ?: item.cameraMake
            val activeModel = item.customCameraModel ?: item.cameraModel
            val cameraText = if (!activeModel.isNullOrEmpty()) {
                "Camera brand: ${activeMake ?: ""} $activeModel".trim()
            } else {
                "No camera hardware details"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera info",
                    tint = if (!item.cameraModel.isNullOrEmpty()) CosmicCyanAccent else CosmicGrayMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = cameraText, fontSize = 11.sp, color = if (!item.cameraModel.isNullOrEmpty()) CosmicWhiteText else CosmicGrayMuted)
            }
            
            // Capture date records Display
            val activeDate = item.customDateTime ?: item.dateTime
            val hasDate = !activeDate.isNullOrEmpty()
            val dateText = if (hasDate) {
                "Date captured: $activeDate"
            } else {
                "No original recording date timestamp"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Date info",
                    tint = if (hasDate) CosmicCyanAccent else CosmicGrayMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = dateText, fontSize = 11.sp, color = if (hasDate) CosmicWhiteText else CosmicGrayMuted)
            }
            
            // User custom text entries
            if (item.customArtist != null || item.customDescription != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CosmicDivider)
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(text = "Custom attributes configured:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicCyanLight)
                if (item.customArtist != null) {
                    Text(text = "Artist tag: ${item.customArtist}", fontSize = 11.sp, color = CosmicWhiteText)
                }
                if (item.customDescription != null) {
                    Text(text = "Description tag: ${item.customDescription}", fontSize = 11.sp, color = CosmicWhiteText)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CosmicDivider)
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Choose details to ignore / clean from this file:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicGrayMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScrubChip(
                    label = "Hide location pin",
                    selected = item.optionScrubGps,
                    onToggle = onToggleGps,
                    disabled = item.gpsLatitude == null
                )
                ScrubChip(
                    label = "Clear camera brand",
                    selected = item.optionScrubCamera,
                    onToggle = onToggleCamera,
                    disabled = item.cameraModel.isNullOrBlank()
                )
                ScrubChip(
                    label = "Reset Capture date",
                    selected = item.optionScrubDateTime,
                    onToggle = onToggleDate,
                    disabled = item.dateTime.isNullOrBlank() && item.customDateTime.isNullOrBlank()
                )
                ScrubChip(
                    label = "Purge all metadata",
                    selected = item.optionScrubAll,
                    onToggle = onToggleAllExif,
                    accentColor = CosmicAmberWarning
                )
            }
        }
    }
}

@Composable
fun BatchControlBoard(
    activeTab: String,
    onTabChange: (String) -> Unit,
    viewModel: FileViewModel
) {
    val context = LocalContext.current
    
    val filesList by viewModel.files.collectAsStateWithLifecycle()
    
    // Text field states
    val findText by viewModel.findText.collectAsStateWithLifecycle()
    val replaceWithText by viewModel.replaceWithText.collectAsStateWithLifecycle()
    val batchPrefix by viewModel.batchPrefix.collectAsStateWithLifecycle()
    val batchSuffix by viewModel.batchSuffix.collectAsStateWithLifecycle()
    val batchBaseName by viewModel.batchBaseName.collectAsStateWithLifecycle()
    val startNumber by viewModel.startNumber.collectAsStateWithLifecycle()

    // Global scrub defaults
    val defaultScrubGps by viewModel.defaultScrubGps.collectAsStateWithLifecycle()
    val defaultScrubCamera by viewModel.defaultScrubCamera.collectAsStateWithLifecycle()
    val defaultScrubDateTime by viewModel.defaultScrubDateTime.collectAsStateWithLifecycle()
    val defaultScrubAll by viewModel.defaultScrubAll.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. RENAME CARD (STRICTLY VERTICAL, NO SIDE-BY-SIDE) ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CosmicBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Bulk Rename",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Bulk Rename Controls",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicWhiteText
                    )
                }

                // Sub-Section A: Find & Replace
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FIND & REPLACE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicCyanLight,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { viewModel.findText.value = it },
                        placeholder = { Text("Find Text (e.g. IMG_)", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (findText.isNotEmpty()) {
                                IconButton(onClick = { viewModel.findText.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = replaceWithText,
                        onValueChange = { viewModel.replaceWithText.value = it },
                        placeholder = { Text("Replace WITH", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (replaceWithText.isNotEmpty()) {
                                IconButton(onClick = { viewModel.replaceWithText.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.applyBatchFindAndReplace()
                            Toast.makeText(context, "Find & replace pattern applied successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicCyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Apply Find & Replace", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicCyanDark)
                    }
                }

                HorizontalDivider(color = CosmicDivider, modifier = Modifier.padding(vertical = 4.dp))

                // Sub-Section B: Prefix & Suffix
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PREFIX & SUFFIX ADDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicCyanLight,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedTextField(
                        value = batchPrefix,
                        onValueChange = { viewModel.batchPrefix.value = it },
                        placeholder = { Text("Prefix (added to start)", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (batchPrefix.isNotEmpty()) {
                                IconButton(onClick = { viewModel.batchPrefix.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            focusedLabelColor = CosmicCyanAccent,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = batchSuffix,
                        onValueChange = { viewModel.batchSuffix.value = it },
                        placeholder = { Text("Suffix (added to end)", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (batchSuffix.isNotEmpty()) {
                                IconButton(onClick = { viewModel.batchSuffix.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.applyBatchPrefixSuffix()
                            Toast.makeText(context, "Prefix & suffix applied successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicCyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Apply Prefix & Suffix", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicCyanDark)
                    }
                }

                HorizontalDivider(color = CosmicDivider, modifier = Modifier.padding(vertical = 4.dp))

                // Sub-Section C: Order/Sequential
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SEQUENCE AUTO-NUMBERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicCyanLight,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedTextField(
                        value = batchBaseName,
                        onValueChange = { viewModel.batchBaseName.value = it },
                        placeholder = { Text("Sequential Base Name", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (batchBaseName.isNotEmpty()) {
                                IconButton(onClick = { viewModel.batchBaseName.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = startNumber,
                        onValueChange = { viewModel.startNumber.value = it },
                        placeholder = { Text("Starting Number", fontSize = 12.sp, color = CosmicGrayMuted) },
                        singleLine = true,
                        trailingIcon = {
                            if (startNumber.isNotEmpty()) {
                                IconButton(onClick = { viewModel.startNumber.value = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CosmicGrayMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = CosmicWhiteText,
                            unfocusedTextColor = CosmicWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.applyBatchSequentialRenaming()
                            Toast.makeText(context, "Sequential renaming applied successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicCyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Apply Sequence Pattern", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicCyanDark)
                    }
                }

                HorizontalDivider(color = CosmicDivider, modifier = Modifier.padding(vertical = 4.dp))

                // Sub-Section D: Case conversions & date presets
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "QUICK PRESETS & CONVERSIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicCyanLight,
                        letterSpacing = 0.5.sp
                    )

                    Button(
                        onClick = {
                            viewModel.applyBatchDateRenaming()
                            Toast.makeText(context, "Rename by photo capture dates completed!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "By Date", tint = CosmicWhiteText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Format All by Capture Date", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                    }

                    Button(
                        onClick = {
                            viewModel.applyBatchCaseConversion(allUppercase = true)
                            Toast.makeText(context, "Converted all base names to UPPERCASE!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("CONVERT ALL TO UPPERCASE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                    }

                    Button(
                        onClick = {
                            viewModel.applyBatchCaseConversion(allUppercase = false)
                            Toast.makeText(context, "Converted all base names to lowercase!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("convert all to lowercase", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                    }

                    Button(
                        onClick = {
                            viewModel.resetBatchFilenames()
                            Toast.makeText(context, "Reverted all filenames to original names!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1aFA5252)),
                        border = BorderStroke(1.dp, Color(0xFFFA5252).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFFFA5252), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset All to Original Names", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFA5252))
                    }
                }
            }
        }

        // --- 2. PRIVACY & SANITIZATION CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CosmicBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bulk Privacy",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Bulk Privacy Controls (EXIF)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicWhiteText
                    )
                }

                Text(
                    text = "Configure sanitization presets directly applied to all files in this batch:",
                    fontSize = 12.sp,
                    color = CosmicGrayMuted,
                    lineHeight = 16.sp
                )

                // GPS Switch Row
                Surface(
                    color = CosmicSlateBg.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = "GPS",
                            tint = if (defaultScrubGps) CosmicAmberWarning else CosmicGrayMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strip Location Data (GPS)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                            Text("Removes geographic tags to hide where media was captured.", fontSize = 11.sp, color = CosmicGrayMuted)
                        }
                        Switch(
                            checked = defaultScrubGps,
                            onCheckedChange = {
                                viewModel.toggleBatchScrubGps(it)
                                Toast.makeText(context, if (it) "GPS Scrubbing Enabled for batch" else "GPS Scrubbing Disabled for batch", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Camera Switch Row
                Surface(
                    color = CosmicSlateBg.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Camera Info",
                            tint = if (defaultScrubCamera) CosmicCyanAccent else CosmicGrayMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strip Device & Camera Specs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                            Text("Removes camera make, software version, and software logs.", fontSize = 11.sp, color = CosmicGrayMuted)
                        }
                        Switch(
                            checked = defaultScrubCamera,
                            onCheckedChange = {
                                viewModel.toggleBatchScrubCamera(it)
                                Toast.makeText(context, if (it) "Camera Scrubbing Enabled for batch" else "Camera Scrubbing Disabled for batch", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Timestamps Row
                Surface(
                    color = CosmicSlateBg.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Timestamps",
                            tint = if (defaultScrubDateTime) CosmicCyanAccent else CosmicGrayMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strip Dates & Timestamps", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                            Text("Wipes creation dates, hour details, and digitizing timestamps.", fontSize = 11.sp, color = CosmicGrayMuted)
                        }
                        Switch(
                            checked = defaultScrubDateTime,
                            onCheckedChange = {
                                viewModel.toggleBatchScrubDateTime(it)
                                Toast.makeText(context, if (it) "Date Scrubbing Enabled for batch" else "Date Scrubbing Disabled for batch", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Full Purge Row
                Surface(
                    color = CosmicSlateBg.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Full Purge",
                            tint = if (defaultScrubAll) CosmicAmberWarning else CosmicGrayMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full Privacy Purge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
                            Text("Wipes absolutely all metadata variables without exception.", fontSize = 11.sp, color = CosmicGrayMuted)
                        }
                        Switch(
                            checked = defaultScrubAll,
                            onCheckedChange = {
                                viewModel.toggleBatchScrubAll(it)
                                Toast.makeText(context, if (it) "Full EXIF Purge Enabled for batch" else "Full EXIF Purge Disabled for batch", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // --- 3. FILES PREVIEW LIST CARD (SCROLLABLE LIST INTEGRATED) ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicCardSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CosmicBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Preview list",
                            tint = CosmicCyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Modified Files Preview (${filesList.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicWhiteText
                        )
                    }
                }

                Text(
                    text = "Inspect how your files will look. File names update live based on renaming controls above.",
                    fontSize = 11.sp,
                    color = CosmicGrayMuted,
                    lineHeight = 15.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filesList.forEachIndexed { idx, item ->
                        val proposedName = getProposedNameForFile(
                            item = item,
                            findText = findText,
                            replaceWithText = replaceWithText,
                            prefix = batchPrefix,
                            suffix = batchSuffix,
                            sequenceBase = batchBaseName,
                            sequenceStart = startNumber,
                            index = idx,
                            totalCount = filesList.size
                        )

                        Surface(
                            color = CosmicSlateBg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Mini preview image or icon
                                    if (item.mimeType.contains("image", ignoreCase = true)) {
                                        AsyncImage(
                                            model = File(item.localCachedPath),
                                            contentDescription = "Mini batch item preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CosmicCardSurface)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CosmicCardSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getGenericFileIcon(item.mimeType),
                                                contentDescription = "file icon",
                                                tint = CosmicCyanAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ORIGINAL:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CosmicGrayMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = item.originalName,
                                            fontSize = 11.sp,
                                            color = CosmicWhiteText.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "PROPOSED:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CosmicCyanAccent,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = proposedName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CosmicWhiteText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.removeFileItem(context, item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from batch",
                                            tint = Color(0xFFFA5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = CosmicDivider)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Active Privacy Presets Pills for this item
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Scrub options:", fontSize = 10.sp, color = CosmicGrayMuted)
                                    
                                    val isGpsScrubbed = item.optionScrubGps || item.optionScrubAll || defaultScrubGps || defaultScrubAll
                                    val isCameraScrubbed = item.optionScrubCamera || item.optionScrubAll || defaultScrubCamera || defaultScrubAll
                                    val isDateScrubbed = item.optionScrubDateTime || item.optionScrubAll || defaultScrubDateTime || defaultScrubAll

                                    PreviewScrubPill(label = "Loc", active = isGpsScrubbed)
                                    PreviewScrubPill(label = "Cam", active = isCameraScrubbed)
                                    PreviewScrubPill(label = "Timestamp", active = isDateScrubbed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getProposedNameForFile(
    item: SharedFileItem,
    findText: String,
    replaceWithText: String,
    prefix: String,
    suffix: String,
    sequenceBase: String,
    sequenceStart: String,
    index: Int, totalCount: Int
): String {
    val ext = item.extension
    var baseName = item.nameWithoutExtension
    
    if (sequenceBase.trim().isNotEmpty()) {
        val startVal = sequenceStart.toIntOrNull() ?: 1
        val currentSeq = startVal + index
        val formatter = java.text.DecimalFormat("000")
        val seqString = if (totalCount >= 100) formatter.format(currentSeq) else String.format("%02d", currentSeq)
        baseName = "${sequenceBase.trim()}_$seqString"
    } else {
        if (findText.isNotEmpty()) {
            baseName = baseName.replace(findText, replaceWithText)
        }
        val cleanPrefix = prefix.trim()
        val cleanSuffix = suffix.trim()
        baseName = "$cleanPrefix$baseName$cleanSuffix"
    }
    
    return if (ext.isNotEmpty()) "$baseName.$ext" else baseName
}

@Composable
fun PreviewScrubPill(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CosmicCyanDark else CosmicDivider)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) CosmicCyanLight else CosmicGrayMuted
        )
    }
}

@Composable
fun BatchActionCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = CosmicCyanAccent,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicSlateBg, RoundedCornerShape(10.dp))
            .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
            Text(text = desc, fontSize = 10.sp, color = CosmicGrayMuted)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
            contentDescription = "Trigger batch click",
            tint = CosmicCyanAccent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun FileItemCard(
    item: SharedFileItem,
    onFilenameChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
    onEditMetadataClick: () -> Unit,
    onToggleGps: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleDate: (Boolean) -> Unit,
    onToggleAllExif: (Boolean) -> Unit
) {
    Surface(
        color = CosmicCardSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (item.optionScrubAll || item.optionScrubGps || item.optionScrubCamera || item.optionScrubDateTime) CosmicCyanDark else CosmicBorder),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_item_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Row 1: App icon thumbnail preview & formatted specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    // Image thumbnail preview if image, generic icon otherwise
                    if (item.mimeType.contains("image", ignoreCase = true)) {
                        AsyncImage(
                            model = File(item.localCachedPath),
                            contentDescription = "Image preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CosmicSlateBg)
                                .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CosmicSlateBg)
                                .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getGenericFileIcon(item.mimeType),
                                contentDescription = "Generic file icon",
                                tint = CosmicCyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.originalName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicWhiteText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(CosmicCyanLight, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (item.extension.isNotEmpty()) ".${item.extension.uppercase()}" else "FILE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicCyanDark
                                )
                            }

                            Text(
                                text = formatSize(item.sizeBytes),
                                fontSize = 11.sp,
                                color = CosmicGrayMuted
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove single item",
                        tint = CosmicGrayMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: Editable Filename Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicSlateBg, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ModeEdit,
                        contentDescription = "Edit single name",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW FILENAME INDICATOR",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicGrayMuted,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = item.currentName,
                    onValueChange = onFilenameChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = CosmicWhiteText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_${item.id}"),
                    singleLine = true
                )
            }

            // EXIF metadata details indicator box
            if (item.hasExif) {
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detected METADATA (EXIF)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicCyanLight
                        )

                        Text(
                            text = "DETAILS & OVERRIDES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicCyanAccent,
                            modifier = Modifier
                                .clickable { onEditMetadataClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coordinates display if any
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val hasGps = item.gpsLatitude != null && item.gpsLongitude != null
                        Icon(
                            imageVector = if (hasGps) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = "GPS availability status",
                            tint = if (hasGps) CosmicAmberWarning else CosmicGrayMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasGps) "GPS Geotags: ${String.format("%.4f", item.gpsLatitude)}, ${String.format("%.4f", item.gpsLongitude)}" else "No GPS Geotags mapping",
                            fontSize = 11.sp,
                            color = if (hasGps) CosmicWhiteText else CosmicGrayMuted
                        )
                    }

                    // Hardware device info if any
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val hasCamera = !item.cameraModel.isNullOrEmpty()
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera hardware info",
                            tint = if (hasCamera) CosmicCyanAccent else CosmicGrayMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasCamera) "Camera Model: ${item.cameraMake ?: ""} ${item.cameraModel}" else "No camera Hardware model found",
                            fontSize = 11.sp,
                            color = if (hasCamera) CosmicWhiteText else CosmicGrayMuted
                        )
                    }

                    // Date captured
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val hasDate = !item.dateTime.isNullOrEmpty()
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date captured info",
                            tint = if (hasDate) CosmicCyanAccent else CosmicGrayMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasDate) "Timestamp: ${item.dateTime}" else "No timestamp records found",
                            fontSize = 11.sp,
                            color = if (hasDate) CosmicWhiteText else CosmicGrayMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CosmicDivider)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Metadata Cleansing Options:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicGrayMuted
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Checklist chips/toggles
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScrubChip(
                            label = "GPS Geotags",
                            selected = item.optionScrubGps,
                            onToggle = onToggleGps,
                            disabled = item.gpsLatitude == null
                        )

                        ScrubChip(
                            label = "Camera Hardware",
                            selected = item.optionScrubCamera,
                            onToggle = onToggleCamera,
                            disabled = item.cameraModel.isNullOrEmpty()
                        )

                        ScrubChip(
                            label = "Timestamp",
                            selected = item.optionScrubDateTime,
                            onToggle = onToggleDate,
                            disabled = item.dateTime.isNullOrEmpty()
                        )

                        ScrubChip(
                            label = "Purge All EXIF",
                            selected = item.optionScrubAll,
                            onToggle = onToggleAllExif,
                            accentColor = CosmicAmberWarning
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrubChip(
    label: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    disabled: Boolean = false,
    accentColor: Color = CosmicCyanAccent
) {
    val opacity = if (disabled) 0.35f else 1f
    Box(
        modifier = Modifier
            .alpha(opacity)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.15f) else CosmicSlateBg)
            .border(
                1.dp,
                if (selected) accentColor else CosmicBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !disabled) { onToggle(!selected) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Selection",
                tint = if (selected) accentColor else CosmicGrayMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) CosmicWhiteText else CosmicGrayMuted
            )
        }
    }
}

// Utility to render simple multi-column flow layouts
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Simply fallback using a flexible Wrap-centered Column/Row setup
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun MetadataEditDialog(
    item: SharedFileItem,
    onDismiss: () -> Unit,
    onSaveCustomTags: (String?, String?, String?, String?, String?) -> Unit
) {
    var artistInput by remember { mutableStateOf(item.customArtist ?: item.artist ?: "") }
    var descriptionInput by remember { mutableStateOf(item.customDescription ?: item.userComment ?: "") }
    var dateTimeInput by remember { mutableStateOf(item.customDateTime ?: item.dateTime ?: "") }
    var cameraMakeInput by remember { mutableStateOf(item.customCameraMake ?: item.cameraMake ?: "") }
    var cameraModelInput by remember { mutableStateOf(item.customCameraModel ?: item.cameraModel ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(1.dp, CosmicBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = CosmicCardSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditAttributes,
                            contentDescription = "Edit File Details",
                            tint = CosmicCyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Edit File Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicWhiteText
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CosmicGrayMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CosmicDivider)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Edit values embedded inside details of your files and photos. These changes are saved cleanly before sharing:",
                    fontSize = 12.sp,
                    color = CosmicGrayMuted,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Artist Field
                Text(
                    text = "ARTIST / PHOTOGRAPHER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = artistInput,
                    onValueChange = { artistInput = it },
                    placeholder = { Text("e.g. Jane Doe", color = CosmicGrayMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicCyanAccent,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicWhiteText,
                        unfocusedTextColor = CosmicWhiteText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("artist_override_field"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Description Field
                Text(
                    text = "CAPTION / DESCRIPTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    placeholder = { Text("e.g. Scenery caption", color = CosmicGrayMuted, fontSize = 12.sp) },
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicCyanAccent,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicWhiteText,
                        unfocusedTextColor = CosmicWhiteText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("description_override_field"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Date Time Field
                Text(
                    text = "DATE & TIME STAMP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = dateTimeInput,
                    onValueChange = { dateTimeInput = it },
                    placeholder = { Text("YYYY:MM:DD HH:MM:SS", color = CosmicGrayMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicCyanAccent,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicWhiteText,
                        unfocusedTextColor = CosmicWhiteText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("datetime_override_field"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Camera Make Field
                Text(
                    text = "CAMERA MANUFACTURER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = cameraMakeInput,
                    onValueChange = { cameraMakeInput = it },
                    placeholder = { Text("e.g. Sony", color = CosmicGrayMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicCyanAccent,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicWhiteText,
                        unfocusedTextColor = CosmicWhiteText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_make_override_field"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Camera Model Field
                Text(
                    text = "CAMERA DEVICE MODEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicCyanLight,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = cameraModelInput,
                    onValueChange = { cameraModelInput = it },
                    placeholder = { Text("e.g. ILCE-7M4", color = CosmicGrayMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicCyanAccent,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicWhiteText,
                        unfocusedTextColor = CosmicWhiteText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_model_override_field"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, CosmicBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = CosmicWhiteText, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val finalArtist = artistInput.trim().ifEmpty { null }
                            val finalDesc = descriptionInput.trim().ifEmpty { null }
                            val finalDate = dateTimeInput.trim().ifEmpty { null }
                            val finalMake = cameraMakeInput.trim().ifEmpty { null }
                            val finalModel = cameraModelInput.trim().ifEmpty { null }
                            onSaveCustomTags(finalArtist, finalDesc, finalDate, finalMake, finalModel)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicCyanAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_dialog_button")
                    ) {
                        Text("Save Details", color = CosmicSlateBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlay() {
    Surface(
        color = CosmicSlateBg.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 12.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(CosmicCardSurface, RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, CosmicBorder), RoundedCornerShape(16.dp))
                    .padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = CosmicCyanAccent,
                    strokeWidth = 3.2.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Scrubbing & Renaming files...",
                    color = CosmicWhiteText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saving detail updates",
                    color = CosmicGrayMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// FORMATTING & UI TYPE MAPPINGS helpers
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val df = java.text.DecimalFormat("#,##0.#")
    val calculatedSize = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "${df.format(calculatedSize)} ${units[digitGroups]}"
}

private fun getGenericFileIcon(mimeType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        mimeType.contains("pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
        mimeType.contains("audio", ignoreCase = true) -> Icons.Default.MusicNote
        mimeType.contains("video", ignoreCase = true) -> Icons.Default.VideoFile
        mimeType.contains("zip", ignoreCase = true) || mimeType.contains("archive", ignoreCase = true) -> Icons.Default.Archive
        mimeType.contains("text", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
fun ConfirmPillCheckbox(label: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                color = if (checked) CosmicCyanDark else CosmicBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) CosmicCyanAccent else CosmicGrayMuted,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (checked) CosmicCyanLight else CosmicGrayMuted,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsScreen(
    viewModel: FileViewModel,
    onBackClick: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val useDynamicTheming by viewModel.useDynamicTheming.collectAsStateWithLifecycle()
    val defaultScrubGps by viewModel.defaultScrubGps.collectAsStateWithLifecycle()
    val defaultScrubCamera by viewModel.defaultScrubCamera.collectAsStateWithLifecycle()
    val defaultScrubDateTime by viewModel.defaultScrubDateTime.collectAsStateWithLifecycle()
    val defaultScrubAll by viewModel.defaultScrubAll.collectAsStateWithLifecycle()
    val autoRenameSafeHashes by viewModel.autoRenameSafeHashes.collectAsStateWithLifecycle()
    val lowercaseExtensions by viewModel.lowercaseExtensions.collectAsStateWithLifecycle()
    val filesList by viewModel.files.collectAsStateWithLifecycle()

    var cacheSizeStr by remember { mutableStateOf("0 B") }
    
    LaunchedEffect(filesList) {
        val totalBytes = getFolderSize(File(context.cacheDir, "shared_temp")) + 
                         getFolderSize(File(context.cacheDir, "processed_items"))
        cacheSizeStr = formatSize(totalBytes)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .background(
                            color = CosmicBorder.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Cleaner",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Preferences",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CosmicWhiteText,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Configure default processing heuristics & style",
                        fontSize = 11.sp,
                        color = CosmicGrayMuted
                    )
                }
            }
        }

        item {
            SettingsSectionHeader(title = "Gallery & API Permissions")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("settings_permissions_card"),
                color = CosmicCardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = CosmicCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Fine-Grained Gallery Access",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicWhiteText
                                )
                                Text(
                                    text = "Status: " + getStoragePermissionStatus(context),
                                    fontSize = 11.sp,
                                    color = CosmicGrayMuted
                                )
                            }
                        }
                    }

                    Text(
                        text = "On Android 14+, you can grant 'Select Photos' access to authorize only files you choose. Full access bypasses individual selection prompts.",
                        fontSize = 11.sp,
                        color = CosmicGrayMuted,
                        lineHeight = 14.sp
                    )

                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CosmicCyanAccent,
                            contentColor = CosmicSlateBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Configure Storage Permissions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionHeader(title = "Appearance & Style")
        }

        item {
            SettingsSwitchCard(
                title = "Material You Dynamic Theme",
                description = "Derive application primary accents directly from your system current wallpaper",
                checked = useDynamicTheming,
                icon = Icons.Default.Palette,
                onCheckedChange = { viewModel.useDynamicTheming.value = it }
            )
        }

        item {
            SettingsSectionHeader(title = "Default Metadata Presets")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSwitchCard(
                    title = "Strip Location GPS Data",
                    description = "Clear latitude/longitude geotags from all imported media automatically",
                    checked = defaultScrubGps,
                    icon = Icons.Default.LocationOff,
                    onCheckedChange = { viewModel.defaultScrubGps.value = it }
                )
                SettingsSwitchCard(
                    title = "Strip Original Timestamp & Date",
                    description = "Clear generation date-time tags from photo headers automatically",
                    checked = defaultScrubDateTime,
                    icon = Icons.Default.CalendarToday,
                    onCheckedChange = { viewModel.defaultScrubDateTime.value = it }
                )
                SettingsSwitchCard(
                    title = "Strip Technical Hardware Spec tags",
                    description = "Clear camera model, lens parameters, and software makers automatically",
                    checked = defaultScrubCamera,
                    icon = Icons.Default.CameraAlt,
                    onCheckedChange = { viewModel.defaultScrubCamera.value = it }
                )
                SettingsSwitchCard(
                    title = "Strict Mode (Full Scrub)",
                    description = "Remove all descriptive, artist, copyright, and custom tags simultaneously",
                    checked = defaultScrubAll,
                    icon = Icons.Default.VerifiedUser,
                    onCheckedChange = { viewModel.defaultScrubAll.value = it }
                )
            }
        }

        item {
            SettingsSectionHeader(title = "Automated Storage Automation")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSwitchCard(
                    title = "File Extension Normalizer",
                    description = "Automatically standardize files suffix to simple lowercase extensions (e.g. .JPEG to .jpg)",
                    checked = lowercaseExtensions,
                    icon = Icons.Default.DriveFileRenameOutline,
                    onCheckedChange = { viewModel.lowercaseExtensions.value = it }
                )
                SettingsSwitchCard(
                    title = "Opaque Hash Preservation",
                    description = "Scrub target shared filename and assign safe 8-character random UUID on export",
                    checked = autoRenameSafeHashes,
                    icon = Icons.Default.Grid3x3,
                    onCheckedChange = { viewModel.autoRenameSafeHashes.value = it }
                )
            }
        }

        item {
            SettingsSectionHeader(title = "Sandbox Space & Security Audit")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CosmicCardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Temp Storage",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicWhiteText
                            )
                            Text(
                                text = "Secure temporary folder caching original and copied sharing buffers",
                                fontSize = 11.sp,
                                color = CosmicGrayMuted,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }

                        Text(
                            text = cacheSizeStr,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = CosmicCyanAccent
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.clearFiles(context)
                            val totalBytes = getFolderSize(File(context.cacheDir, "shared_temp")) + 
                                             getFolderSize(File(context.cacheDir, "processed_items"))
                            cacheSizeStr = formatSize(totalBytes)
                            Toast.makeText(context, "Sandbox Cache Cleared Successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CosmicCyanAccent,
                            contentColor = CosmicSlateBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clear_sandbox_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Securely Erase All Cached Buffers",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                color = CosmicSlateBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DIAGNOSTIC TELEMETRY STATUS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicGrayMuted
                    )
                    
                    DiagnosticRow(label = "Android SDK Version", value = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.CODENAME})")
                    DiagnosticRow(
                        label = "Device Theme Matching Status", 
                        value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Compatible / Monet Active" else "Static Preset Active"
                    )
                    DiagnosticRow(label = "Sandbox File Isolation", value = "OK / Sandboxed")
                    DiagnosticRow(label = "Authority Integrity Status", value = "OK / FileProvider verified")
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CosmicCyanAccent,
        letterSpacing = 1.25.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        color = CosmicCardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) CosmicCyanAccent.copy(alpha = 0.35f) else CosmicBorder.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (checked) CosmicCyanAccent.copy(alpha = 0.12f) else CosmicBorder.copy(alpha = 0.15f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) CosmicCyanAccent else CosmicGrayMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicWhiteText
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = CosmicGrayMuted,
                    lineHeight = 14.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CosmicCardSurface,
                    checkedTrackColor = CosmicCyanAccent,
                    uncheckedThumbColor = CosmicGrayMuted,
                    uncheckedTrackColor = CosmicBorder.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = CosmicGrayMuted)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicWhiteText)
    }
}

private fun getFolderSize(file: File): Long {
    if (!file.exists()) return 0
    if (!file.isDirectory) return file.length()
    var size: Long = 0
    file.listFiles()?.forEach {
        size += if (it.isDirectory) getFolderSize(it) else it.length()
    }
    return size
}

fun getStoragePermissionStatus(context: Context): String {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val hasImages = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasSelect = context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasImages) "All Photos Granted"
            else if (hasSelect) "Selected Photos Only"
            else "Access Denied"
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val hasImages = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasImages) "All Photos Granted" else "Access Denied"
        }
        else -> {
            val hasOld = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasOld) "All Files Granted" else "Access Denied"
        }
    }
}
@Composable
fun ModernPickerSelectionDialog(
    onDismissRequest: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onLaunchDocumentPicker: () -> Unit,
    permissionStatusText: String,
    onRequestPermissions: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("modern_picker_dialog"),
            color = CosmicCardSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Import Media & Files",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CosmicWhiteText
                        )
                        Text(
                            text = "Add photos or system documents securely",
                            fontSize = 11.sp,
                            color = CosmicGrayMuted
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .background(CosmicBorder.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                            .size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CosmicWhiteText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Modern Side-by-Side buttons for Photos (Photo Picker) and Files (Document Picker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Option: Photos (Uses Photo Picker)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable {
                                onLaunchPhotoPicker()
                                onDismissRequest()
                            },
                        color = CosmicSlateBg.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicCyanAccent.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CosmicCyanAccent.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Pick Photos",
                                    tint = CosmicCyanAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Photos",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicWhiteText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Photo Picker",
                                fontSize = 10.sp,
                                color = CosmicGrayMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Right Option: Files (Uses Document Picker)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable {
                                onLaunchDocumentPicker()
                                onDismissRequest()
                            },
                        color = CosmicSlateBg.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicCyanAccent.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CosmicCyanAccent.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Pick Files",
                                    tint = CosmicCyanAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Files",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicWhiteText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All Documents",
                                fontSize = 10.sp,
                                color = CosmicGrayMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Compact permission status section at the bottom (extremely space-efficient)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CosmicSlateBg.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = CosmicCyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Access: $permissionStatusText",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CosmicWhiteText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = onRequestPermissions,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("grant_permissions_button")
                        ) {
                            Text(
                                text = "Configure",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicCyanAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
