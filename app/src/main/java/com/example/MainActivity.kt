@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example

import java.io.File
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("root_scaffold")
                ) { innerPadding ->
                    MainContentScreen(
                        viewModel = fileViewModel,
                        contentPadding = innerPadding
                    )
                }
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
                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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
                val streamUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
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
    viewModel: FileViewModel,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val filesList by viewModel.files.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()

    // File picker launcher for fallback manual selection
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadUris(context, uris)
        }
    }

    var selectedFileIndex by remember { mutableStateOf(0) }
    var selectedFileForMetadataEdit by remember { mutableStateOf<SharedFileItem?>(null) }
    var activeTab by remember { mutableStateOf("rename") } // "rename" or "scrub"
    var currentScreenModeTab by remember { mutableStateOf("single") } // "single" or "batch"

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
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // Elegant modern header action row (no bulky icon/paragraphs card)
            MinimalTopBar(
                filesCount = filesList.size,
                activeItem = activeItem,
                showThumbnail = showThumbnailInTopBar && (currentScreenModeTab == "single"),
                onClearClick = { viewModel.clearFiles(context) },
                onAddMoreClick = { pickerLauncher.launch("*/*") }
            )

            if (filesList.isEmpty()) {
                // Fullscreen border-free Material 3 expressive empty state
                EmptyStateView(
                    onSelectFilesClick = { pickerLauncher.launch("*/*") }
                )
            } else {
                val realActiveItem = filesList[currentIndex]
                val isFoldedFlip = boxHeight < 560.dp && boxWidth < 500.dp

                // High-fidelity tab selector to avoid messy UI clutter
                if (filesList.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicBorder.copy(alpha = 0.3f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("single" to "Clean Selected File", "batch" to "Clean All (${filesList.size})").forEach { (tabId, label) ->
                            val isSelected = currentScreenModeTab == tabId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CosmicCyanAccent else Color.Transparent)
                                    .clickable { currentScreenModeTab = tabId }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CosmicSlateBg else CosmicWhiteText.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

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
                            // Column 1: Info and Load status summary
                            Column(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StatusSummaryHeader(filesList = filesList)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Batch settings apply simultaneously across all imported media assets securely.",
                                    fontSize = 13.sp,
                                    color = CosmicWhiteText.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            // Column 2: Batch processing panel
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                BatchControlBoard(
                                    activeTab = activeTab,
                                    onTabChange = { activeTab = it },
                                    viewModel = viewModel
                                )
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
                                val activeImageHeight = (220.dp - scrollOffsetDp).coerceAtLeast(44.dp)
                                val activeImageFraction = ((220.dp - scrollOffsetDp) / 220.dp).coerceIn(0.1f, 1.0f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(activeImageHeight)
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
                        TextButton(onClick = { viewModel.processAndPrepareShare(context) }) {
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
                        onClick = { viewModel.processAndPrepareShare(context) },
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
    }
}

@Composable
fun MinimalTopBar(
    filesCount: Int,
    activeItem: SharedFileItem? = null,
    showThumbnail: Boolean = false,
    onClearClick: () -> Unit,
    onAddMoreClick: () -> Unit
) {
    val context = LocalContext.current
    if (filesCount > 0) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (showThumbnail && activeItem != null) {
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
                        text = if (showThumbnail && activeItem != null) {
                            val name = activeItem.originalName
                            if (name.length > 14) name.take(12) + "..." else name
                        } else {
                            "CleanShare"
                        },
                        fontSize = if (showThumbnail && activeItem != null) 14.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CosmicWhiteText,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                text = "Clean, Rename & Share Files",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CosmicWhiteText,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Rename files and scrub sensitive metadata (like location pins, camera specifications, and timestamps) before you send them to other people.",
                fontSize = 13.sp,
                color = CosmicGrayMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

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

    Surface(
        color = CosmicSlateBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicCardSurface, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, CosmicBorder), RoundedCornerShape(12.dp))
                .padding(14.dp),
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
                text = "Total Payload: $formattedSize",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CosmicGrayMuted
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
                                text = item.extension.uppercase().ifEmpty { "FILE" },
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
                        text = "NEW FILENAME",
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
                            text = ext.uppercase(),
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
    // Collect specific text updates
    val findText by viewModel.findText.collectAsStateWithLifecycle()
    val replaceWithText by viewModel.replaceWithText.collectAsStateWithLifecycle()
    val batchPrefix by viewModel.batchPrefix.collectAsStateWithLifecycle()
    val batchSuffix by viewModel.batchSuffix.collectAsStateWithLifecycle()
    val batchBaseName by viewModel.batchBaseName.collectAsStateWithLifecycle()
    val startNumber by viewModel.startNumber.collectAsStateWithLifecycle()

    var showBatchRenameOptions by remember { mutableStateOf(true) }

    Surface(
        color = CosmicCardSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBatchRenameOptions = !showBatchRenameOptions },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Batch tools icon",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bulk Optimization Controls",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicWhiteText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showBatchRenameOptions) "Hide" else "Show Options",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicCyanAccent
                    )
                    Icon(
                        imageVector = if (showBatchRenameOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle batch options",
                        tint = CosmicCyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showBatchRenameOptions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Small tab controls: Rename Panel vs Metadata Scrub Panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicSlateBg)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == "rename") CosmicCardSurface else Color.Transparent)
                                .clickable { onTabChange("rename") }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bulk Rename Settings",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == "rename") CosmicCyanAccent else CosmicGrayMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == "scrub") CosmicCardSurface else Color.Transparent)
                                .clickable { onTabChange("scrub") }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bulk Privacy Settings",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == "scrub") CosmicCyanAccent else CosmicGrayMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeTab == "rename") {
                        // RENAME TOOL OPTIONS
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Section 1: Find & Replace
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSlateBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Find & Replace",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicWhiteText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = findText,
                                        onValueChange = { viewModel.findText.value = it },
                                        placeholder = { Text("Find e.g. DSC_", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    OutlinedTextField(
                                        value = replaceWithText,
                                        onValueChange = { viewModel.replaceWithText.value = it },
                                        placeholder = { Text("Replace with", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    IconButton(
                                        onClick = { viewModel.applyBatchFindAndReplace() },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(CosmicCyanDark, RoundedCornerShape(8.dp))
                                            .testTag("apply_find_replace_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Search and replace",
                                            tint = CosmicWhiteText
                                        )
                                    }
                                }
                            }

                            // Section 2: Prefix & Suffix additions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSlateBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Prefix & Suffix Adds",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicWhiteText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = batchPrefix,
                                        onValueChange = { viewModel.batchPrefix.value = it },
                                        placeholder = { Text("Prefix e.g. Vacay_", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    OutlinedTextField(
                                        value = batchSuffix,
                                        onValueChange = { viewModel.batchSuffix.value = it },
                                        placeholder = { Text("Suffix e.g. _v2", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    IconButton(
                                        onClick = { viewModel.applyBatchPrefixSuffix() },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(CosmicCyanDark, RoundedCornerShape(8.dp))
                                            .testTag("apply_prefix_suffix_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Apply Prefix Suffix",
                                            tint = CosmicWhiteText
                                        )
                                    }
                                }
                            }

                            // Section 3: Pattern sequential rename
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSlateBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Sequence / Number Auto-renaming (e.g. Doc_01, Image_02)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicWhiteText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = batchBaseName,
                                        onValueChange = { viewModel.batchBaseName.value = it },
                                        placeholder = { Text("Base Name e.g. Paris", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(1.3f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    OutlinedTextField(
                                        value = startNumber,
                                        onValueChange = { viewModel.startNumber.value = it },
                                        placeholder = { Text("Start e.g. 1", fontSize = 12.sp, color = CosmicGrayMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicCyanAccent,
                                            unfocusedBorderColor = CosmicBorder,
                                            focusedTextColor = CosmicWhiteText,
                                            unfocusedTextColor = CosmicWhiteText
                                        ),
                                        modifier = Modifier.weight(0.7f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    IconButton(
                                        onClick = { viewModel.applyBatchSequentialRenaming() },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(CosmicCyanDark, RoundedCornerShape(8.dp))
                                            .testTag("apply_batch_sequence_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Sequential automatic serial rename",
                                            tint = CosmicWhiteText
                                        )
                                    }
                                }
                            }

                            // Auto-Date utility renaming
                            Button(
                                onClick = { viewModel.applyBatchDateRenaming() },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicCyanDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_date_rename_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Date rename icon",
                                    tint = CosmicCyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AUTO-NAMING BY DATE (e.g., 20260526_01.png)",
                                    fontSize = 11.sp,
                                    color = CosmicCyanAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Dynamic convert utilities (Caps toggle)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.applyBatchCaseConversion(allUppercase = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_uppercase_button")
                                ) {
                                    Text("MASS CAPS", fontSize = 11.sp, color = CosmicWhiteText, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.applyBatchCaseConversion(allUppercase = false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_lowercase_button")
                                ) {
                                    Text("mass lowercase", fontSize = 11.sp, color = CosmicWhiteText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // METADATA SCRUB OPTIONS
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Perform a quick batch cleanse setting across all eligible loaded files and photos:",
                                fontSize = 12.sp,
                                color = CosmicWhiteText.copy(alpha = 0.8f)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BatchActionCard(
                                    title = "Scrub GPS & Geotags",
                                    desc = "Removes spatial latitude coordinates & elevations.",
                                    icon = Icons.Default.LocationOff,
                                    onClick = { viewModel.applyBatchScrubAction(scrubGps = true, scrubCamera = false, scrubDate = false, scrubAll = false) }
                                )

                                BatchActionCard(
                                    title = "Scrub Device Info",
                                    desc = "Strips Camera Make/Model details & editing software tags.",
                                    icon = Icons.Default.DeviceUnknown,
                                    onClick = { viewModel.applyBatchScrubAction(scrubGps = false, scrubCamera = true, scrubDate = false, scrubAll = false) }
                                )

                                BatchActionCard(
                                    title = "Scrub Timestamp Records",
                                    desc = "Removes original creation timestamps & digitized dates.",
                                    icon = Icons.Default.CalendarToday,
                                    onClick = { viewModel.applyBatchScrubAction(scrubGps = false, scrubCamera = false, scrubDate = true, scrubAll = false) }
                                )

                                BatchActionCard(
                                    title = "Full Metadata Purge (Purge All EXIF)",
                                    desc = "Wipes out all common metadata properties fully.",
                                    icon = Icons.Default.NoAccounts,
                                    tint = CosmicAmberWarning,
                                    onClick = { viewModel.applyBatchScrubAction(scrubGps = false, scrubCamera = false, scrubDate = false, scrubAll = true) }
                                )
                            }
                        }
                    }
                }
            }
        }
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
            imageVector = Icons.Default.ArrowRightAlt,
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
                                    text = item.extension.uppercase().ifEmpty { "FILE" },
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
        else -> Icons.Default.InsertDriveFile
    }
}
