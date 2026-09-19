package com.monad.nativeapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class DocumentResult(val uri: Uri, val mime: String, val description: String)

class DocumentProcessor {
    private val supported = setOf("txt", "md", "markdown", "html", "htm", "docx", "epub", "pdf")
    private val targets = setOf("txt", "md", "html", "pdf")

    fun convert(context: Context, source: Uri, requestedTarget: String? = null): DocumentResult {
        val name = displayName(context, source)
        val ext = name.substringAfterLast('.', "").lowercase()
        require(ext in supported) { "Unsupported format: .$ext" }
        val text = when (ext) {
            "docx" -> unzipXmlText(context, source, "word/document.xml")
            "epub" -> unzipEpubText(context, source)
            "html", "htm" -> stripHtml(readText(context, source))
            "pdf" -> null
            else -> readText(context, source)
        }
        val outputExt = requestedTarget?.lowercase()?.removePrefix(".")
            ?: if (ext == "html" || ext == "htm") "txt" else ext
        require(outputExt in targets) { "Output must be TXT, Markdown, HTML, or PDF." }
        val target = File(context.cacheDir, "${safeName(name)}.$outputExt")
        if (outputExt == "pdf") {
            if (ext == "pdf") {
                context.contentResolver.openInputStream(source)!!.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            } else {
                writePdf(target, text ?: "")
            }
        } else {
            val rendered = when (outputExt) {
                "html" -> "<!doctype html><html><body><pre>${escapeHtml(text ?: "")}</pre></body></html>"
                else -> text ?: ""
            }
            target.writeText(rendered)
        }
        return DocumentResult(
            Uri.fromFile(target),
            when (outputExt) {
                "pdf" -> "application/pdf"
                "html" -> "text/html"
                "md" -> "text/markdown"
                else -> "text/plain"
            },
            "Native .$ext -> .$outputExt"
        )
    }

    private fun escapeHtml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun mergePdfs(context: Context, sources: List<Uri>): Uri {
        val output = File(context.cacheDir, "merged-${System.currentTimeMillis()}.pdf")
        val document = PdfDocument()
        var pageNumber = 1
        sources.forEach { uri ->
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEach
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val width = page.width
                        val height = page.height
                        val info = PdfDocument.PageInfo.Builder(width, height, pageNumber++).create()
                        val outPage = document.startPage(info)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        bitmap.recycle()
                        document.finishPage(outPage)
                    }
                }
            }
            descriptor.close()
        }
        output.outputStream().use { document.writeTo(it) }
        document.close()
        return Uri.fromFile(output)
    }

    fun outline(context: Context, source: Uri): String {
        val name = displayName(context, source)
        val ext = name.substringAfterLast('.', "").lowercase()
        val text = when (ext) {
            "docx" -> unzipXmlText(context, source, "word/document.xml")
            "epub" -> unzipEpubText(context, source)
            "html", "htm" -> stripHtml(readText(context, source))
            "pdf" -> error("PDF outline extraction requires a text extraction library.")
            else -> readText(context, source)
        }
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.length <= 120 }
            .filter { it.startsWith("#") || it.matches(Regex("""^[A-Z][^.!?]{3,100}$""")) }
            .joinToString("\n") { "- $it" }
            .ifBlank { "- No headings detected in this document." }
    }

    fun splitByHeadings(context: Context, source: Uri): List<DocumentResult> {
        val name = displayName(context, source)
        val ext = name.substringAfterLast('.', "").lowercase()
        require(ext in setOf("txt", "md", "markdown", "html", "htm", "docx", "epub")) {
            "Heading split supports TXT, Markdown, HTML, DOCX, and EPUB."
        }
        val sourceText = when (ext) {
            "docx" -> unzipXmlText(context, source, "word/document.xml")
            "epub" -> unzipEpubText(context, source)
            "html", "htm" -> stripHtml(readText(context, source))
            else -> readText(context, source)
        }
        val sections = sourceText.split(Regex("(?m)(?=^#{1,6}\\s+|^\\s*[A-Z][^.!?\\n]{3,100}\\s*$)"))
            .map { it.trim() }.filter { it.isNotBlank() }
        require(sections.size > 1) { "No multiple headings were found to split." }
        return sections.mapIndexed { index, section ->
            val output = File(context.cacheDir, "${safeName(name)}-part-${index + 1}.txt")
            output.writeText(section)
            DocumentResult(Uri.fromFile(output), "text/plain", "Section ${index + 1}")
        }
    }

    fun zip(context: Context, files: List<Pair<String, Uri>>): Uri {
        require(files.isNotEmpty()) { "There are no outputs to export." }
        val output = File(context.cacheDir, "monad-export-${System.currentTimeMillis()}.zip")
        ZipOutputStream(output.outputStream().buffered()).use { archive ->
            files.forEach { (name, uri) ->
                archive.putNextEntry(ZipEntry(name))
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(archive) }
                    ?: File(uri.path ?: "").inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
        return Uri.fromFile(output)
    }

    private fun unzipXmlText(context: Context, source: Uri, entryName: String): String {
        val xml = context.contentResolver.openInputStream(source)!!.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name == entryName }
                    ?.let { zip.readBytes().toString(Charsets.UTF_8) }
            }
        } ?: error("DOCX document body not found")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream())
        return doc.getElementsByTagName("w:t").let { nodes ->
            (0 until nodes.length).joinToString("") { nodes.item(it).textContent }
        }
    }

    private fun unzipEpubText(context: Context, source: Uri): String {
        val result = StringBuilder()
        context.contentResolver.openInputStream(source)!!.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    if (entry.name.endsWith(".html") || entry.name.endsWith(".xhtml")) {
                        result.append(stripHtml(zip.readBytes().toString(Charsets.UTF_8))).append("\n\n")
                    }
                }
            }
        }
        return result.toString().trim()
    }

    private fun writePdf(file: File, text: String) {
        val document = PdfDocument()
        val lines = text.chunked(90)
        val pages = lines.chunked(45)
        pages.forEachIndexed { pageIndex, pageLines ->
            val info = PdfDocument.PageInfo.Builder(612, 792, pageIndex + 1).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            val paint = android.graphics.Paint().apply { textSize = 12f; color = Color.BLACK }
            pageLines.forEachIndexed { line, value -> canvas.drawText(value, 36f, 48f + line * 16f, paint) }
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun readText(context: Context, uri: Uri) =
        context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }

    private fun stripHtml(input: String) =
        input.replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), "")
            .replace(Regex("(?s)<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun displayName(context: Context, uri: Uri) =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else "document.txt" } ?: "document.txt"

    private fun safeName(name: String) = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
}
