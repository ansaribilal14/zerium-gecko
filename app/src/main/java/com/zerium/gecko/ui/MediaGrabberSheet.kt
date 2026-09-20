package com.zerium.gecko.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zerium.gecko.R
import com.zerium.gecko.dl.DownloadEngine
import com.zerium.gecko.dl.MediaRegistry
import kotlinx.coroutines.delay

/**
 * Media grabber sheet: lists everything the scanner detected on the current
 * page (videos, audio, HLS playlists, blob-backed players, direct file
 * links) and hands each item to the download engine.
 */
class MediaGrabberSheet : BottomSheetDialogFragment() {

    interface Host {
        fun onMediaDownloadRequested(item: MediaRegistry.Item)
    }

    private var tabId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabId = arguments?.getLong("tabId") ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ZeriumTheme {
                    MediaList(tabId) { item ->
                        (activity as? Host)?.onMediaDownloadRequested(item)
                            ?: run {
                                Toast.makeText(requireContext(),
                                    R.string.download_started, Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        }
    }

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager, tabId: Long) {
            val f = MediaGrabberSheet()
            val args = Bundle()
            args.putLong("tabId", tabId)
            f.arguments = args
            f.show(fm, "media_grabber")
        }
    }
}

private fun tagLabel(tag: String): String = when (tag) {
    MediaRegistry.TAG_VIDEO -> "VIDEO"
    MediaRegistry.TAG_AUDIO -> "AUDIO"
    MediaRegistry.TAG_HLS -> "STREAM"
    MediaRegistry.TAG_BLOB -> "PLAYER"
    else -> "FILE"
}

private fun tagIcon(tag: String): Int = when (tag) {
    MediaRegistry.TAG_VIDEO, MediaRegistry.TAG_HLS, MediaRegistry.TAG_BLOB -> R.drawable.ic_video_c
    MediaRegistry.TAG_AUDIO -> R.drawable.ic_music_c
    MediaRegistry.TAG_FILE -> R.drawable.ic_download
    else -> R.drawable.ic_image_c
}

@Composable
fun MediaList(tabId: Long, onDownload: (MediaRegistry.Item) -> Unit) {
    val ctx = LocalContext.current
    val registry = remember { com.zerium.gecko.dl.MediaRegistryHolder.get(ctx) }
    val items = remember { mutableStateListOf<MediaRegistry.Item>() }
    androidx.compose.runtime.LaunchedEffect(tabId) {
        while (true) {
            items.clear()
            items.addAll(registry.items(tabId))
            delay(800)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.media_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                items.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.media_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(items, key = { it.url }) { item ->
                    MediaRow(item, onDownload)
                }
            }
        }
        Text(
            stringResource(R.string.media_scope_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MediaRow(item: MediaRegistry.Item, onDownload: (MediaRegistry.Item) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZIcon(tagIcon(item.tag), 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = { Text(tagLabel(item.tag), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(
                            item.label,
                            if (item.size > 0) human(item.size) else null
                        ).joinToString("  \u00b7  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onDownload(item) }) {
                ZIcon(R.drawable.ic_download_c, 22.dp, MaterialTheme.colorScheme.primary)
            }
        }
    }
}
