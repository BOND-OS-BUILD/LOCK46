package com.lock46.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lock46.app.Graph
import com.lock46.app.enforce.Enforcer
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.util.InstalledApp
import com.lock46.app.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Essential-app picker.
 *
 * Selections are held in local state and only committed on Save, so backing out leaves the
 * enforced whitelist untouched — important when the user is editing mid-duty.
 */
@Composable
fun AppPickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var loadedWhitelist by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { InstalledApps.listLaunchable(context) }
        if (!loadedWhitelist) {
            selected.clear()
            selected.addAll(Graph.whitelist.get())
            loadedWhitelist = true
        }
        apps = loaded
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
            Text(
                "ESSENTIAL APPS",
                style = MaterialTheme.typography.titleLarge,
                color = Lock46Colors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Note(
                "Anything not switched on here is blocked during Duty Mode. " +
                    "Pinned entries stay available so the phone remains usable."
            )
        }

        HorizontalDivider(color = Lock46Colors.Line)

        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lock46Colors.Duty)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(list, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.alwaysAllowed || app.packageName in selected,
                    onCheckedChange = { checked ->
                        if (!app.alwaysAllowed) {
                            if (checked) {
                                selected.add(app.packageName)
                            } else {
                                selected.remove(app.packageName)
                            }
                        }
                    }
                )
                HorizontalDivider(color = Lock46Colors.Line.copy(alpha = 0.5f))
            }
        }

        HorizontalDivider(color = Lock46Colors.Line)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryAction("Cancel", onDone, modifier = Modifier.weight(1f))
            PrimaryAction(
                label = "Save",
                onClick = {
                    Graph.whitelist.save(selected.toSet())
                    Enforcer.refreshAlwaysAllowed()
                    // Re-evaluate now that the whitelist changed, rather than waiting for
                    // the next window-change event.
                    Enforcer.onForegroundPackage(context.packageName)
                    onDone()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.alwaysAllowed) { onCheckedChange(!checked) }
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Lock46Colors.PanelRaised, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Lock46Colors.TextSecondary
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Lock46Colors.TextPrimary
            )
            val subtitle = app.pinnedReason ?: app.packageName
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (app.alwaysAllowed) Lock46Colors.Ready else Lock46Colors.TextDim
            )
        }

        Spacer(Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !app.alwaysAllowed,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Lock46Colors.Ink,
                checkedTrackColor = Lock46Colors.Ready,
                uncheckedThumbColor = Lock46Colors.TextDim,
                uncheckedTrackColor = Lock46Colors.Panel,
                uncheckedBorderColor = Lock46Colors.Line,
                disabledCheckedTrackColor = Lock46Colors.Ready.copy(alpha = 0.5f),
                disabledCheckedThumbColor = Lock46Colors.Ink
            )
        )
    }
}

private const val ICON_PX = 96
