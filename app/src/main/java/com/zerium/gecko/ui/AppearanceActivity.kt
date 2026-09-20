package com.zerium.gecko.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zerium.gecko.Prefs
import com.zerium.gecko.R

/**
 * Appearance settings in Compose: app theme mode, Material You dynamic
 * color, accent color. Applies instantly; the non-Compose (View) surfaces
 * pick the accent up from the shared M3 theme on next launch, which is
 * stated honestly on the screen.
 */
class AppearanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeriumTheme {
                AppearanceScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var theme by remember { mutableIntStateOf(prefs.appTheme()) }
    var dynamic by remember { mutableStateOf(prefs.dynamicColor()) }
    var accent by remember { mutableIntStateOf(prefs.accentColor()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                ZIcon(R.drawable.ic_back_c)
            }
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.appearance_theme),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        0 to R.string.appearance_system,
                        1 to R.string.appearance_light,
                        2 to R.string.appearance_dark
                    )
                    options.forEach { (value, label) ->
                        FilterChip(
                            selected = theme == value,
                            onClick = {
                                theme = value
                                prefs.appTheme(value)
                                applyNightMode(value)
                            },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.appearance_dynamic),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.appearance_dynamic_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamic && Build.VERSION.SDK_INT >= 31,
                        onCheckedChange = {
                            dynamic = it
                            prefs.dynamicColor(it)
                        },
                        enabled = Build.VERSION.SDK_INT >= 31,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Text(
                    stringResource(R.string.appearance_accent),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentChoices.forEach { (name, argb) ->
                        val selected = accent == argb
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .padding(3.dp)
                                .clickable {
                                    accent = argb
                                    prefs.accentColor(argb)
                                }
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(argb), CircleShape)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.appearance_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun applyNightMode(mode: Int) {
    AppCompatDelegate.setDefaultNightMode(
        when (mode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}
