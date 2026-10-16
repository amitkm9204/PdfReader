package com.example.pdfreader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

class PdfReaderViewModel(application: Application) : AndroidViewModel(application) {
    var selectedUri by mutableStateOf<Uri?>(null)
        private set
    var fileName by mutableStateOf("No PDF Selected")
        private set
    var pageCount by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(0)
        private set
    var currentBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var extractedText by mutableStateOf("Please select a PDF file using the menu.")
        private set
    var readerMode by mutableStateOf(ReaderMode.VISUAL)
        private set
    var isAloudActive by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var textSize by mutableStateOf(16f)
        private set

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdDocument: PDDocument? = null
    private var tts: TextToSpeech? = null
    private var currentUtteranceDeferred: CompletableDeferred<Unit>? = null

    init {
        initTts(application)
    }

    private fun initTts(context: Application) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        currentUtteranceDeferred?.complete(Unit)
                    }

                    override fun onError(utteranceId: String?) {
                        currentUtteranceDeferred?.complete(Unit)
                    }
                })
            }
        }
    }

    fun openPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}

                try { pdfRenderer?.close() } catch (_: Exception) {}
                try { fileDescriptor?.close() } catch (_: Exception) {}
                try { pdDocument?.close() } catch (_: Exception) {}

                val contentResolver = getApplication<Application>().contentResolver
                val fd = contentResolver.openFileDescriptor(uri, "r")
                if (fd != null) {
                    fileDescriptor = fd
                    val renderer = PdfRenderer(fd)
                    pdfRenderer = renderer
                    val count = renderer.pageCount

                    var name = "Document.pdf"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                name = it.getString(nameIndex) ?: "Document.pdf"
                            }
                        }
                    }

                    var text = "No extractable text found."
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val doc = PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())
                        pdDocument = doc
                        val stripper = PDFTextStripper()
                        text = stripper.getText(doc).ifBlank { "No extractable text found." }
                    }

                    val bitmap = renderPageBitmap(renderer, 0)

                    withContext(Dispatchers.Main) {
                        selectedUri = uri
                        fileName = name
                        pageCount = count
                        currentPage = 0
                        currentBitmap = bitmap
                        extractedText = text
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Error loading PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderPageBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap? {
        return try {
            if (pageIndex in 0 until renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                // High-quality rendering factor (3x) for crystal clear visibility
                var width = page.width * 3
                var height = page.height * 3
                val maxDimension = 3000
                if (width > maxDimension || height > maxDimension) {
                    val scale = maxDimension.toFloat() / maxOf(width, height)
                    width = (width * scale).toInt()
                    height = (height * scale).toInt()
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun changePage(newIndex: Int) {
        if (newIndex in 0 until pageCount) {
            currentPage = newIndex
            viewModelScope.launch(Dispatchers.IO) {
                pdfRenderer?.let { renderer ->
                    val bmp = renderPageBitmap(renderer, newIndex)
                    withContext(Dispatchers.Main) {
                        currentBitmap = bmp
                    }
                }
            }
        }
    }

    fun toggleReaderMode(mode: ReaderMode) {
        readerMode = mode
    }

    fun toggleAloudActive(active: Boolean) {
        isAloudActive = active
        if (!active) {
            stopAloud()
        }
    }

    fun updateTextSize(size: Float) {
        textSize = size
    }

    fun startAloud() {
        if (isPlaying) return
        isPlaying = true

        viewModelScope.launch(Dispatchers.IO) {
            while (isPlaying && currentPage < pageCount) {
                currentUtteranceDeferred = CompletableDeferred()

                val textToRead = try {
                    pdDocument?.let { doc ->
                        val stripper = PDFTextStripper()
                        stripper.startPage = currentPage + 1
                        stripper.endPage = currentPage + 1
                        stripper.getText(doc).ifBlank { "Page ${currentPage + 1}" }
                    } ?: "Page ${currentPage + 1}"
                } catch (_: Exception) {
                    "Page ${currentPage + 1}"
                }

                if (textToRead.isNotBlank() && tts != null) {
                    val utteranceId = "utterance_$currentPage"
                    val params = Bundle()
                    tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

                    // Wait until TTS finishes reading the current page
                    currentUtteranceDeferred?.await()
                }

                if (!isPlaying) break

                // Advance to the next page if available
                if (currentPage < pageCount - 1) {
                    withContext(Dispatchers.Main) {
                        changePage(currentPage + 1)
                    }
                } else {
                    isPlaying = false
                    break
                }
            }
            isPlaying = false
        }
    }

    fun stopAloud() {
        isPlaying = false
        currentUtteranceDeferred?.complete(Unit)
        tts?.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopAloud()
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { fileDescriptor?.close() } catch (_: Exception) {}
        try { pdDocument?.close() } catch (_: Exception) {}
        tts?.shutdown()
    }
}
