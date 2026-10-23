package com.ngocdevv.nativecook.blocks.sheets.morphingmodesheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

data class Mode(
    val label: String,
    val caption: String,
    val icon: ImageVector,
    val pro: Boolean = false,
)

enum class Tag { New, Max }

data class Model(
    val name: String,
    val caption: String,
    /** A small badge after the name. */
    val tag: Tag? = null,
    /** A group heading shown above this row. */
    val heading: String? = null,
)

internal val MODE_HEIGHT = 66.dp
internal val MODE_GAP = 8.dp
internal val MODEL_HEIGHT = 56.dp
internal val MODEL_GAP = 4.dp
internal val HEADING_HEIGHT = 30.dp

internal val DEFAULT_MODES = listOf(
    Mode("Search", "Fast answers to everyday questions", Icons.Outlined.Search, pro = true),
    Mode("Research", "Deep research on any topic", Icons.Outlined.Layers, pro = true),
    Mode("Labs", "Create projects from scratch", Icons.Outlined.Science, pro = true),
)

internal val DEFAULT_MODELS = listOf(
    Model("Best", "Picks the right model for each question"),
    Model("Pulse", "Our fastest model for quick lookups"),
    Model("Orbit", "Careful, balanced and clear"),
    Model("Vega", "The newest model from Vega Labs", Tag.New),
    Model("Atlas", "Broad knowledge, plain writing"),
    Model("Vega Thinking", "Vega with extended reasoning", Tag.New, heading = "Reasoning"),
    Model("Atlas Thinking", "Atlas working through hard problems"),
    Model("Nova Max", "The most capable reasoning model", Tag.Max),
    Model("Pulse Thinking", "Pulse with a short reasoning pass"),
)

private val TEAL = Color(0xFF1E8F95)
private val TEAL_SOFT = Color(0xFFCFEBEC)
private val SELECTED_FILL = Color(0xFFE4F2F2)
private val SELECTED_BORDER = Color(0xFF4CB8BD)
private val ROW_FILL = Color(0xFFF5F5F2)
private val INK = Color(0xFF1C1C1C)
private val MUTE = Color(0xFF8A8A8A)
private val SWITCH_OFF = Color(0xFFD8D8D8)
private val SWITCH_ON = Color(0xFF1FA37A)
private val TAG_BLUE = Color(0xFF3B82F6)
private val TAG_GOLD = Color(0xFFD99A1C)
private const val ROW_FADE_MS = 200

private fun Modifier.press(onClick: () -> Unit) = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    role = Role.Button,
    onClick = onClick,
)

/** The white disc an icon of the compact sheet sits on. */
@Composable
private fun IconDisc(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(38.dp)
            .dropShadow(CircleShape, Shadow(radius = 3.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.06f))
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** A name with a small badge after it, on the rows of both sheets. */
@Composable
private fun Badge(text: String, ink: Color, fill: Color, tracking: TextUnit = 0.sp) {
    Box(Modifier.height(16.dp).background(fill, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ink, fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = tracking)
    }
}

@Composable
private fun RowName(text: String, size: TextUnit, lineHeight: TextUnit) {
    Text(text, color = INK, fontSize = size, lineHeight = lineHeight, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
}

/** A row of the compact sheet; the selected one takes a teal fill and border. */
@Composable
internal fun ModeRow(
    mode: Mode,
    selected: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val fill by animateColorAsState(if (selected) SELECTED_FILL else ROW_FILL, tween(ROW_FADE_MS), label = "fill")
    val border by animateColorAsState(if (selected) SELECTED_BORDER else Color.Transparent, tween(ROW_FADE_MS), label = "border")
    Row(
        modifier
            .fillMaxWidth()
            .height(MODE_HEIGHT)
            .background(fill, RoundedCornerShape(16.dp))
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .semantics { this.selected = selected }
            .press(onPress)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(mode.icon, if (selected) TEAL else INK)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RowName(mode.label, 15.5.sp, 20.sp)
                if (mode.pro) Badge("PRO", TEAL, TEAL_SOFT, 0.4.sp)
            }
            Text(mode.caption, color = MUTE, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** The incognito row: a switch that slides its knob while the track changes colour. */
@Composable
internal fun SwitchRow(label: String, caption: String, on: Boolean, onPress: () -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (on) SWITCH_ON else SWITCH_OFF, tween(ROW_FADE_MS), label = "track")
    val knob by animateDpAsState(if (on) 20.dp else 0.dp, tween(ROW_FADE_MS), label = "knob")
    Row(
        modifier
            .fillMaxWidth()
            .height(MODE_HEIGHT)
            .background(ROW_FILL, RoundedCornerShape(16.dp))
            .border(1.5.dp, Color.Transparent, RoundedCornerShape(16.dp))
            .press(onPress)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(Icons.Outlined.Shield, INK)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowName(label, 15.5.sp, 20.sp)
            Text(caption, color = MUTE, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(width = 50.dp, height = 30.dp).background(track, RoundedCornerShape(15.dp)).padding(2.dp)) {
            Box(
                Modifier
                    .offset(x = knob)
                    .size(26.dp)
                    .dropShadow(CircleShape, Shadow(radius = 3.dp, color = Color.Black, offset = DpOffset(0.dp, 1.5.dp), alpha = 0.2f))
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/**
 * A row of the tall sheet. It slides up 12 dp and fades in as the sheet grows, a little after the
 * row above. [grow] is read while drawing, 0 compact and 1 tall.
 */
@Composable
internal fun ModelRow(model: Model, index: Int, selected: Boolean, grow: () -> Float, onPress: () -> Unit) {
    val mark by animateFloatAsState(if (selected) 1f else 0f, tween(ROW_FADE_MS), label = "mark")
    val scale by animateFloatAsState(if (selected) 1f else 0.4f, tween(ROW_FADE_MS), label = "markScale")
    Column(
        Modifier.graphicsLayer {
            val from = 0.4f + index * 0.02f
            val raw = min(1f, max(0f, (grow() - from) / (0.85f + index * 0.02f - from)))
            val t = raw * raw * (3f - 2f * raw)
            alpha = t
            translationY = 12f * (1f - t) * density
        },
    ) {
        if (model.heading != null) {
            Text(
                model.heading.uppercase(),
                modifier = Modifier.fillMaxWidth().heightIn(min = HEADING_HEIGHT).padding(top = 12.dp, start = 20.dp, end = 20.dp),
                color = MUTE,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = MODEL_GAP)
                .fillMaxWidth()
                .height(MODEL_HEIGHT)
                .background(ROW_FILL, RoundedCornerShape(14.dp))
                .semantics { this.selected = selected }
                .press(onPress)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RowName(model.name, 15.sp, 19.sp)
                    if (model.tag != null) {
                        Badge(if (model.tag == Tag.New) "New" else "Max", Color.White, if (model.tag == Tag.New) TAG_BLUE else TAG_GOLD)
                    }
                }
                Text(model.caption, color = MUTE, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = mark
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(TEAL, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
