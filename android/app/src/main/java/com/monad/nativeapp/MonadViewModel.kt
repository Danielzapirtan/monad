package com.monad.nativeapp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val detail: String,
    val output: Uri? = null
)

class MonadViewModel : ViewModel() {
    val files = androidx.compose.runtime.mutableStateListOf<ImportedFile>()
    var status by androidx.compose.runtime.mutableStateOf("Ready")
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    var transcript by androidx.compose.runtime.mutableStateOf("")
    var language by androidx.compose.runtime.mutableStateOf("auto")
    var targetFormat by androidx.compose.runtime.mutableStateOf("pdf")
    private val processor = DocumentProcessor()
    private val transcriber: TranscriptionEngine = AndroidSpeechTranscriptionEngine()

    fun addFile(context: Context, uri: Uri) {
        val name = context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment
        } ?: uri.lastPathSegment ?: "Selected file"
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        files.add(ImportedFile(uri, name, mime, mime))
        error = null
        status = "${files.size} file(s) selected"
    }

    fun convertTextFiles(context: Context) {
        runOperation("Converting documents") {
            files.toList().forEachIndexed { index, file ->
                val result = processor.convert(context, file.uri, targetFormat)
                files[index] = file.copy(
                    detail = result.description,
                    output = result.uri.toShareUri(context),
                    mime = result.mime
                )
            }
            "Conversion complete"
        }
    }

    fun mergePdfFiles(context: Context) {
        runOperation("Merging PDFs") {
            val pdfs = files.filter { it.mime == "application/pdf" || it.name.endsWith(".pdf", true) }
            require(pdfs.size >= 2) { "Select at least two PDF files to merge." }
            val output = processor.mergePdfs(context, pdfs.map { it.uri })
            files.add(ImportedFile(output, "merged.pdf", "application/pdf", "Merged PDF", output.toShareUri(context)))
            "Merged PDF is ready to share"
        }
    }

    fun makeOutline(context: Context) {
        runOperation("Building outline") {
            val file = files.firstOrNull() ?: error("Import a text document first.")
            transcript = processor.outline(context, file.uri)
            "Outline ready"
        }
    }

    fun splitByHeadings(context: Context) {
            runOperation("Splitting document") {
                val file = files.firstOrNull() ?: error("Import a document first.")
                val results = processor.splitByHeadings(context, file.uri)
                results.forEach { result ->
                    files.add(
                        ImportedFile(
                            result.uri,
                            result.uri.lastPathSegment ?: "section.txt",
                            result.mime,
                            result.description,
                            result.uri.toShareUri(context)
                        )
                    )
                }
                "${results.size} sections created"
            }
    }

    fun exportAll(context: Context) {
            runOperation("Creating ZIP export") {
                val outputs = files.mapNotNull { file ->
                    file.output?.let { file.name to it }
                }
                require(outputs.isNotEmpty()) { "Run an operation before exporting." }
                val archive = processor.zip(context, outputs)
                files.add(
                    ImportedFile(
                        archive,
                        "monad-export.zip",
                        "application/zip",
                        "Complete export",
                        archive.toShareUri(context)
                    )
                )
                "ZIP export is ready to share"
            }
    }

    fun transcribe(context: Context) {
        runOperation("Transcribing media") {
            val media = files.firstOrNull() ?: error("Import an audio or video file first.")
            transcript = transcriber.transcribe(context, media.uri, language)
            "Transcription ready"
        }
    }

    private fun runOperation(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            status = label
            error = null
            try {
                status = withContext(Dispatchers.IO) { block() }
            } catch (t: Throwable) {
                error = t.message ?: "Operation failed"
                status = "Operation failed"
            }
        }
    }

    private fun Uri.toShareUri(context: Context): Uri =
        if (scheme == "file") FileProvider.getUriForFile(
            context, "${context.packageName}.files", java.io.File(path!!)
        ) else this
}
