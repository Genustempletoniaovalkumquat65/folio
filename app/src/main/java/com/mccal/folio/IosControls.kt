package com.mccal.folio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style controls for Folio's settings (the sheet is always dark glass). */
private val IosGreen = Color(0xFF34C759)
private val IosBlue = Color(0xFF0A84FF)
private val IosTrackOff = Color(0xFF39393D)

/** iOS switch: 51×31 green track with a white thumb that springs across. */
@Composable
internal fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) IosGreen else IosTrackOff, label = "switch track")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMedium), label = "switch thumb")
    Box(modifier.minimumInteractiveComponentSize()
        .toggleable(checked, role = Role.Switch, onValueChange = {
            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff); onCheckedChange(it)
        }), contentAlignment = Alignment.Center) {
        Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track).padding(2.dp)) {
            Box(Modifier.offset(x = offset).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}

/** iOS-style choice pill (a drop-in for the settings' FilterChips): white when selected, frosted when not. */
@Composable
internal fun IosChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = .1f), label = "chip")
    Box(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(background)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.ProvideTextStyle(TextStyle(color = if (selected) Color.Black else Color.White, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)) { label() }
    }
}

/** iOS slider: thin track filled in system blue with a round white thumb. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun IosSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Slider(value, onValueChange, modifier, valueRange = valueRange, interactionSource = interaction,
        thumb = { Box(Modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)) },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(IosBlue))
            }
        })
}

/** iOS search field: frosted rounded capsule, magnifier, placeholder and a clear button. */
@Composable
internal fun IosSearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier, ink: Color = Color.White, onSearch: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .12f))
        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = ink.copy(alpha = .55f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) androidx.compose.material3.Text(placeholder, color = ink.copy(alpha = .55f), fontSize = 17.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(query, onQuery, fieldModifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 17.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch?.invoke() }))
        }
        if (query.isNotEmpty()) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Cancel, "Clear search", tint = ink.copy(alpha = .5f),
            modifier = Modifier.minimumInteractiveComponentSize().size(20.dp).clickable { onQuery("") })
    }
}

/** iOS blue (or red) text action row inside a grouped list. */
@Composable
internal fun IosActionRow(text: String, tag: String? = null, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (destructive) Color(0xFFFF453A) else Color(0xFF0A84FF)
    androidx.compose.material3.Text(text, color = if (enabled) color else Color.White.copy(alpha = .3f), fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier))
}

/** Used when haptic feedback is turned off in settings. */
internal object NoHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Android's "Remove animations" (animator duration scale 0), treated like iOS Reduce Motion: no wiggling,
 * no sliding pages, no fold blur. Read once per composition of the provider.
 */
internal val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

internal fun reduceMotionEnabled(context: android.content.Context): Boolean =
    runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        .getOrDefault(1f) == 0f
