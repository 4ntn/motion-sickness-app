// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation3.runtime.NavKey
import dev.davidv.motionsickness.R
import dev.davidv.motionsickness.data.CueSettings
import dev.davidv.motionsickness.data.DotAppearance
import dev.davidv.motionsickness.motion.DotStyle
import dev.davidv.motionsickness.motion.MotionCuesService
import dev.davidv.motionsickness.motion.draw
import dev.davidv.motionsickness.theme.MyApplicationTheme

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val cuesRunning by MotionCuesService.isRunning.collectAsStateWithLifecycle()
    val cueSettings = remember(context) { CueSettings.get(context) }
    val appearance by cueSettings.appearance.collectAsStateWithLifecycle()

    // Permissions can change while we're backgrounded (user toggled them in Settings).
    // Re-query on resume so the UI stays honest.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                notifGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.main_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.main_subtitle), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))

        if (!overlayGranted) {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    overlayLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.main_grant_overlay)) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
            Button(
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) { Text("Allow notifications") }
        }

        Spacer(Modifier.height(8.dp))

        if (!cuesRunning) {
            Button(
                onClick = { MotionCuesService.start(context) },
                enabled = overlayGranted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.main_start)) }
        } else {
            Button(
                onClick = { MotionCuesService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.main_stop)) }
        }

        Spacer(Modifier.height(8.dp))

        DotStylePicker(selected = appearance.style, onSelect = cueSettings::setStyle)

        Text(stringResource(R.string.main_opacity), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = appearance.opacity,
            onValueChange = cueSettings::setOpacity,
            valueRange = DotAppearance.MIN_OPACITY..1f,
        )
    }
}

/**
 * Row of round swatches, one per [DotStyle], each drawn with the same code as the overlay.
 * The selected one gets a ring in the theme's primary color; the others a faint outline so
 * the white swatch stays visible on a light background.
 */
@Composable
private fun DotStylePicker(
    selected: DotStyle,
    onSelect: (DotStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (style in DotStyle.entries) {
            val isSelected = style == selected
            val description = stringResource(style.label)
            Canvas(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                    .semantics { contentDescription = description }
                    .padding(6.dp),
            ) {
                drawIntoCanvas {
                    style.draw(it.nativeCanvas, center.x, center.y, size.minDimension / 2f, paint)
                }
            }
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme { MainScreen(onItemClick = {}) }
}
