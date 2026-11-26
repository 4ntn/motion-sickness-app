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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.davidv.motionsickness.motion.CueMode
import dev.davidv.motionsickness.motion.DotStyle
import dev.davidv.motionsickness.motion.MotionCuesService
import dev.davidv.motionsickness.motion.draw
import dev.davidv.motionsickness.motion.gridPitch
import dev.davidv.motionsickness.motion.hueToColor
import dev.davidv.motionsickness.motion.sizeFactor
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

        Text(stringResource(R.string.main_layout), style = MaterialTheme.typography.bodyMedium)
        CueLayoutPicker(selected = appearance.mode, onSelect = cueSettings::setMode)

        Text(stringResource(R.string.main_style), style = MaterialTheme.typography.bodyMedium)
        DotStylePicker(
            selected = appearance.style,
            hue = appearance.hue,
            onSelect = cueSettings::setStyle,
        )

        if (appearance.style == DotStyle.Color) {
            Text(stringResource(R.string.main_hue), style = MaterialTheme.typography.bodyMedium)
            HueSlider(hue = appearance.hue, onHueChange = cueSettings::setHue)
        }

        Text(stringResource(R.string.main_size), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = appearance.size,
            onValueChange = cueSettings::setSize,
            valueRange = DotAppearance.MIN_SIZE..DotAppearance.MAX_SIZE,
        )

        Text(stringResource(R.string.main_opacity), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = appearance.opacity,
            onValueChange = cueSettings::setOpacity,
            valueRange = DotAppearance.MIN_OPACITY..1f,
        )
    }
}

/**
 * Row of miniature phone screens, one per [CueMode], showing where that layout puts the dots.
 * Dots are scaled with the same [sizeFactor] the overlay uses, so the preview matches it.
 */
@Composable
private fun CueLayoutPicker(
    selected: CueMode,
    onSelect: (CueMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (mode in CueMode.entries) {
            val isSelected = mode == selected
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier.selectable(selected = isSelected, role = Role.RadioButton) { onSelect(mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(
                    Modifier
                        .size(width = 56.dp, height = 96.dp)
                        .clip(shape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape,
                        ),
                ) {
                    val unit = size.minDimension / 2f
                    val pitch = mode.gridPitch * unit
                    val dotRadius = 2.dp.toPx()
                    // Hex grid centered on the tile, like the overlay's at rest.
                    val rows = (size.height / pitch).toInt() / 2 + 1
                    val cols = (size.width / pitch).toInt() / 2 + 1
                    for (row in -rows..rows) {
                        val y = center.y + row * pitch
                        val rowOffset = if (row % 2 != 0) pitch / 2f else 0f
                        for (col in -cols..cols) {
                            val x = center.x + col * pitch + rowOffset
                            val f = mode.sizeFactor(x, y, size.width, size.height, unit)
                            if (f > 0.01f) drawCircle(dotColor, dotRadius * f, Offset(x, y))
                        }
                    }
                }
                Text(stringResource(mode.label), style = MaterialTheme.typography.labelMedium)
            }
        }
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
    hue: Float,
    onSelect: (DotStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val color = remember(hue) { hueToColor(hue) }
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
                    style.draw(it.nativeCanvas, center.x, center.y, size.minDimension / 2f, paint, color)
                }
            }
        }
    }
}

/** Hue stops every 60° so the gradient passes through all primaries and secondaries. */
private val hueTrackColors = (0..6).map { Color(hueToColor(it * 60f)) }

/**
 * Slider for the [DotStyle.Color] hue. The track is a rainbow at the same fixed saturation and
 * brightness the dots use, and the thumb takes the selected color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = hue,
        onValueChange = onHueChange,
        modifier = modifier,
        valueRange = 0f..DotAppearance.MAX_HUE,
        colors = SliderDefaults.colors(thumbColor = Color(hueToColor(hue))),
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(hueTrackColors)),
            )
        },
    )
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
