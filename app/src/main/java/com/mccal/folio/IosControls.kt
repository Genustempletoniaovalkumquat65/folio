package com.mccal.folio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style controls for Folio's settings (the sheet is always dark glass). */
private val IosGreen = Color(0xFF34C759)
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

/** iOS slider: thin white-filled track with a round white thumb. */
@Composable
internal fun IosSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    Slider(value, onValueChange, modifier, valueRange = valueRange, colors = SliderDefaults.colors(
        thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .22f),
        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
}
