package com.example.pdfreader

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdfreader.ui.theme.PdfReaderTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PdfReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfReaderMainScreen()
                }
            }
        }
    }
}

enum class ReaderMode {
    VISUAL,
    TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderMainScreen(viewModel: PdfReaderViewModel = viewModel()) {
    // File picker launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.openPdf(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = viewModel.fileName, maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (viewModel.pageCount > 0) {
                            Text(text = "Page ${viewModel.currentPage + 1} of ${viewModel.pageCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Open File / Menu Button
                    IconButton(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open PDF from Filesystem")
                    }
                    // Mode Switcher (Visual vs Text)
                    IconButton(onClick = {
                        val newMode = if (viewModel.readerMode == ReaderMode.VISUAL) ReaderMode.TEXT else ReaderMode.VISUAL
                        viewModel.toggleReaderMode(newMode)
                    }) {
                        Icon(
                            imageVector = if (viewModel.readerMode == ReaderMode.VISUAL) Icons.Default.TextSnippet else Icons.Default.PictureAsPdf,
                            contentDescription = "Toggle Reader Mode"
                        )
                    }
                    // Aloud Mode Toggle Button
                    IconButton(onClick = {
                        viewModel.toggleAloudActive(!viewModel.isAloudActive)
                    }) {
                        Icon(
                            imageVector = if (viewModel.isAloudActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Aloud Mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                // Aloud Mode Control Bar if active
                if (viewModel.isAloudActive) {
                    Surface(
                        tonalElevation = 8.dp,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (viewModel.isPlaying) "Reading Aloud (Page ${viewModel.currentPage + 1})..." else "Aloud Mode Ready",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row {
                                if (viewModel.isPlaying) {
                                    Button(onClick = { viewModel.stopAloud() }) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop")
                                    }
                                } else {
                                    Button(onClick = { viewModel.startAloud() }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Read Aloud")
                                    }
                                }
                            }
                        }
                    }
                }

                // Page Navigation Bar (Previous / Next page buttons for Visual mode)
                if (viewModel.pageCount > 0 && viewModel.readerMode == ReaderMode.VISUAL) {
                    Surface(
                        tonalElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.changePage(viewModel.currentPage - 1) },
                                enabled = viewModel.currentPage > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = null)
                                Text("Previous")
                            }

                            Text(
                                text = "${viewModel.currentPage + 1} / ${viewModel.pageCount}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { viewModel.changePage(viewModel.currentPage + 1) },
                                enabled = viewModel.currentPage < viewModel.pageCount - 1
                            ) {
                                Text("Next")
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (viewModel.selectedUri == null) {
                // Empty State
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No PDF Opened",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Browse your filesystem to select and read PDF documents one page at a time with zoom and continuous voice reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open PDF File")
                    }
                }
            } else {
                when (viewModel.readerMode) {
                    ReaderMode.VISUAL -> {
                        // Visual Mode: One page at a time with Zoom & Pan
                        viewModel.currentBitmap?.let { bitmap ->
                            ZoomablePdfPage(bitmap = bitmap, pageNumber = viewModel.currentPage + 1)
                        } ?: CircularProgressIndicator()
                    }
                    ReaderMode.TEXT -> {
                        // Clean Text Reader Mode with live Aloud text highlighting
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Text size controller
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Text Size: ${viewModel.textSize.toInt()}sp", fontSize = 12.sp)
                                IconButton(onClick = { if (viewModel.textSize > 10f) viewModel.updateTextSize(viewModel.textSize - 2f) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size")
                                }
                                IconButton(onClick = { if (viewModel.textSize < 32f) viewModel.updateTextSize(viewModel.textSize + 2f) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Font Size")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val scrollState = rememberScrollState()

                            val highlightColor = MaterialTheme.colorScheme.primaryContainer
                            val annotatedText = buildAnnotatedString {
                                viewModel.pageTexts.forEachIndexed { index, pageText ->
                                    val isCurrentReading = viewModel.isAloudActive && viewModel.isPlaying && index == viewModel.currentPage
                                    if (isCurrentReading) {
                                        pushStyle(SpanStyle(background = highlightColor))
                                        append("▶ [Page ${index + 1} - Reading Aloud]\n")
                                        append(pageText)
                                        pop()
                                    } else {
                                        append("[Page ${index + 1}]\n")
                                        append(pageText)
                                    }
                                    append("\n\n")
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .verticalScroll(scrollState)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = annotatedText,
                                    fontSize = viewModel.textSize.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = (viewModel.textSize * 1.4f).sp
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
fun ZoomablePdfPage(bitmap: Bitmap, pageNumber: Int) {
    var scale by remember(pageNumber) { mutableStateOf(1f) }
    var offsetX by remember(pageNumber) { mutableStateOf(0f) }
    var offsetY by remember(pageNumber) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(pageNumber) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Page $pageNumber",
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
