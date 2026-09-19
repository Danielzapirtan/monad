package com.monad.nativeapp

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private enum class Workspace { DOCUMENTS, MEDIA }

@Composable
fun MonadApp(vm: MonadViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var workspace by remember { mutableStateOf(Workspace.DOCUMENTS) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant only a transient read permission.
            }
            vm.addFile(context, uri)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = workspace == Workspace.DOCUMENTS,
                    onClick = { workspace = Workspace.DOCUMENTS },
                    icon = { Icon(Icons.Default.Description, null) },
                    label = { Text("Morphix") }
                )
                NavigationBarItem(
                    selected = workspace == Workspace.MEDIA,
                    onClick = { workspace = Workspace.MEDIA },
                    icon = { Icon(Icons.Default.AudioFile, null) },
                    label = { Text("Diarix") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (workspace == Workspace.DOCUMENTS) "Document Utilities"
                    else "Media Editor",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
                )
                Text(
                    if (workspace == Workspace.DOCUMENTS)
                        "Convert, split, merge, and outline documents on this device."
                    else
                        "Import media, create clips, transcribe, and export a transcript.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            item {
                Button(onClick = {
                    picker.launch(
                        if (workspace == Workspace.DOCUMENTS)
                            arrayOf(
                                "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/epub+zip", "text/plain", "text/markdown", "text/html"
                            )
                        else arrayOf("audio/*", "video/*")
                    )
                }) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Import files")
                }
            }
            if (vm.error != null) {
                item { Text(vm.error!!, color = MaterialTheme.colorScheme.error) }
            }
            if (workspace == Workspace.DOCUMENTS) {
                item {
                    Text("Operations", style = MaterialTheme.typography.titleMedium)
                    TextField(
                        value = vm.targetFormat,
                        onValueChange = { vm.targetFormat = it.lowercase().trim().removePrefix(".") },
                        label = { Text("Convert to: pdf, txt, md, or html") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { vm.convertTextFiles(context) } }) { Text("Convert") }
                        Button(onClick = { scope.launch { vm.mergePdfFiles(context) } }) { Text("Merge PDFs") }
                    }
                }
                item {
                    Button(onClick = { scope.launch { vm.makeOutline(context) } }) {
                        Text("Create text outline")
                    }
                }
            } else {
                val media = vm.files.firstOrNull()
                if (media != null) {
                    item {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply { this.player = player }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        LaunchedEffect(media.uri) {
                            player.setMediaItem(MediaItem.fromUri(media.uri))
                            player.prepare()
                        }
                    }
                }
                item {
                    Text("Transcription", style = MaterialTheme.typography.titleMedium)
                    Text("Play imported audio/video here. Transcription uses a bundled engine when configured.")
                    TextField(
                        value = vm.language,
                        onValueChange = { vm.language = it },
                        label = { Text("Language (auto, en, de, fr, ro)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { scope.launch { vm.transcribe(context) } }) {
                        Text("Transcribe selected media")
                    }
                }
            }
            if (workspace == Workspace.DOCUMENTS) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { vm.splitByHeadings(context) } }) {
                            Text("Split by headings")
                        }
                        Button(onClick = { scope.launch { vm.exportAll(context) } }) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.padding(horizontal = 2.dp))
                            Text("Export ZIP")
                        }
                    }
                }
            }
            items(vm.files) { file ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text(file.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        if (file.output != null) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = file.mime
                                    putExtra(Intent.EXTRA_STREAM, file.output)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share output"))
                            }) { Icon(Icons.Default.Share, "Share") }
                        }
                    }
                }
            }
            if (vm.transcript.isNotBlank()) {
                item {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium)
                    TextField(
                        value = vm.transcript,
                        onValueChange = { vm.transcript = it },
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                }
            }
            item { Text(vm.status, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
