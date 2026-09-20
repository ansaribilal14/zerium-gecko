package com.zerium.gecko.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zerium.gecko.R
import com.zerium.gecko.dl.CategoryResolver
import com.zerium.gecko.dl.DownloadEngine
import com.zerium.gecko.dl.DownloadTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Download manager UI (Jetpack Compose, Material 3): live progress, speed,
 * pause/resume/cancel/retry, category folders, open/share/delete.
 */
class DownloadsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeriumTheme {
                DownloadsScreen(
                    onBack = { finish() },
                    openFile = { t -> openTask(t) }
                )
            }
        }
    }

    private fun openTask(t: DownloadTask) {
        try {
            val uri: Uri = if (t.finalUri != null && t.finalUri.startsWith("content:")) {
                Uri.parse(t.finalUri)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    this, applicationContext.packageName + ".fileprovider",
                    File(t.finalUri!!))
            }
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, if (t.mime.isNullOrEmpty()) "*/*" else t.mime)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

private enum class Filter(val labelRes: Int) {
    ALL(R.string.dl_filter_all),
    ACTIVE(R.string.dl_filter_active),
    DONE(R.string.dl_filter_done)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, openFile: (DownloadTask) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { DownloadEngine.get(ctx) }
    val tasks = remember { mutableStateListOf<DownloadTask>() }
    var filter by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val snap = engine.snapshot()
            tasks.clear()
            tasks.addAll(snap)
            delay(500)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        ZIcon(R.drawable.ic_back_c)
                    }
                    Text(
                        stringResource(R.string.menu_downloads),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { engine.clearFinished() }) {
                        ZIcon(R.drawable.ic_trash_c)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Filter.values().size) { i ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = {
                            Text(stringResource(Filter.values()[i].labelRes))
                        }
                    )
                }
            }
            val active = Filter.values()[filter]
            val visible = tasks.filter { t ->
                when (active) {
                    Filter.ALL -> true
                    Filter.ACTIVE -> t.status == DownloadTask.Status.RUNNING
                            || t.status == DownloadTask.Status.QUEUED
                            || t.status == DownloadTask.Status.PAUSED
                    Filter.DONE -> t.status == DownloadTask.Status.COMPLETED
                            || t.status == DownloadTask.Status.FAILED
                }
            }
            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.dl_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { t ->
                        TaskCard(task = t, engine = engine, onOpen = { openFile(t) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(task: DownloadTask, engine: DownloadEngine, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (task.status == DownloadTask.Status.COMPLETED) onOpen() },
            onLongClick = {}),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(task.category)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.filename,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (task.status) {
                    DownloadTask.Status.RUNNING,
                    DownloadTask.Status.QUEUED -> IconBtn(R.drawable.ic_pause) {
                        engine.pause(task.id)
                    }
                    DownloadTask.Status.PAUSED,
                    DownloadTask.Status.FAILED -> IconBtn(R.drawable.ic_play) {
                        engine.resume(task.id)
                    }
                    DownloadTask.Status.COMPLETED -> IconBtn(R.drawable.ic_check) { onOpen() }
                }
                IconBtn(
                    if (task.status == DownloadTask.Status.COMPLETED) R.drawable.ic_trash
                    else R.drawable.ic_close
                ) {
                    engine.cancel(task.id)
                }
            }
            if (task.status == DownloadTask.Status.RUNNING
                || task.status == DownloadTask.Status.PAUSED
                || task.status == DownloadTask.Status.QUEUED
            ) {
                Spacer(Modifier.height(10.dp))
                if (task.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { task.progressFraction() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (task.status == DownloadTask.Status.FAILED && !task.error.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun CategoryIcon(category: Int) {
    val res = when (category) {
        CategoryResolver.VIDEO -> R.drawable.ic_video_c
        CategoryResolver.AUDIO -> R.drawable.ic_music_c
        CategoryResolver.IMAGE -> R.drawable.ic_image_c
        CategoryResolver.DOC -> R.drawable.ic_pdf_c
        CategoryResolver.ARCHIVE -> R.drawable.ic_archive_c
        CategoryResolver.APK -> R.drawable.ic_apk_c
        else -> R.drawable.ic_download_c
    }
    ZIcon(res, 30.dp)
}

@Composable
private fun IconBtn(res: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        ZIcon(res, 20.dp)
    }
}

@Composable
private fun subtitle(t: DownloadTask): String {
    return when (t.status) {
        DownloadTask.Status.COMPLETED -> {
            val size = if (t.totalBytes > 0) " \u00b7 " + human(t.totalBytes) else ""
            stringResource(R.string.dl_status_completed) + size
        }
        DownloadTask.Status.FAILED -> stringResource(R.string.dl_status_failed)
        DownloadTask.Status.PAUSED -> {
            val pct = if (t.totalBytes > 0) {
                " \u00b7 " + (t.progressFraction() * 100).toInt() + "%"
            } else ""
            stringResource(R.string.dl_status_paused) + pct
        }
        DownloadTask.Status.QUEUED -> stringResource(R.string.dl_status_queued)
        DownloadTask.Status.RUNNING -> {
            val sb = StringBuilder()
            if (t.hls && t.totalBytes > 0) {
                sb.append(stringResource(R.string.dl_hls_progress, t.doneBytes, t.totalBytes))
            } else if (t.totalBytes > 0) {
                sb.append(human(t.doneBytes)).append(" / ").append(human(t.totalBytes))
            } else {
                sb.append(human(t.doneBytes))
            }
            if (t.speedBps > 0) sb.append(" \u00b7 ").append(human(t.speedBps)).append("/s")
            sb.toString()
        }
    }
}

internal fun human(b: Long): String {
    if (b < 1024) return "$b B"
    if (b < 1048576) return String.format(java.util.Locale.US, "%.1f KB", b / 1024f)
    if (b < 1073741824L) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576f)
    return String.format(java.util.Locale.US, "%.2f GB", b / 1073741824f)
}
