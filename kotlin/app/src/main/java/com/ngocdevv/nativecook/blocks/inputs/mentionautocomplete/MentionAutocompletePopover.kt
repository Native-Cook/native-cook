package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.MAX_ROWS
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.PEOPLE
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteData.matchRange
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.POP_HEAD
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.POP_PAD
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.Pose
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.ROW_H
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.autoPose
import com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete.MentionAutocompleteMotion.clamp01
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.PI

/*
 MentionAutocompletePopover — the people list that grows out of the caret (a port of
 MentionPopover.tsx).

 It sits above the composer, anchored at its bottom edge; it scales from the caret's x ([anchor])
 and resizes to the rows it holds. Rows are placed by slot (0 = top) from the bottom edge, so the
 list can grow and shrink while every row slides to its new place. The loop places rows from its
 pose; a person's typing places them with springs.
*/

internal val POP_MAX_H = POP_HEAD + POP_PAD * 2 + MAX_ROWS * ROW_H

private val PopStyle = TextStyle(fontFamily = FontFamily.SansSerif, platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun MentionAutocompletePopover(
    pose: () -> Pose,
    query: String,
    /** Slot of each person for the current query, -1 when not listed. */
    slots: IntArray,
    open: Boolean,
    user: Boolean,
    /** Loop time the person took over at: rows start from where the loop had them. */
    takeT: Float,
    left: Float,
    bottom: Float,
    width: Float,
    /** Caret x inside the popover, the point it grows from. */
    anchor: Float,
    channel: String,
    accent: Color,
    onPick: (Int) -> Unit,
) {
    Box(
        Modifier.offset(left.dp, (bottom - POP_MAX_H).dp).size(width.dp, POP_MAX_H.dp)
            .graphicsLayer {
                val p = pose().pop
                val s = 0.3f + 0.7f * p
                alpha = clamp01(p * 2.2f)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin((anchor / width).coerceIn(0f, 1f), 1f)
            },
    ) {
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .layout { m, c ->
                    val h = (POP_HEAD + POP_PAD * 2 + max(0f, pose().count) * ROW_H) * density
                    val pl = m.measure(Constraints.fixed(c.maxWidth, h.toInt()))
                    layout(pl.width, pl.height) { pl.place(0, 0) }
                }
                .cssBoxShadow(Color(0x141E1A14), 2.dp, 4.dp, 18.dp)
                .cssBoxShadow(Color(0x291E1A14), 16.dp, 36.dp, 18.dp)
                .background(Color(0xFFFFFEFB), RoundedCornerShape(18.dp))
                .border(0.5.dp, Color(0x1F1E1C18), RoundedCornerShape(18.dp)),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp).fillMaxWidth().height(POP_HEAD.dp)
                .graphicsLayer { translationY = -(POP_PAD + max(0f, pose().count) * ROW_H) * density }
                // A closed popover is out of the semantics tree, as a discrete state (not by its alpha).
                .then(if (open) Modifier else Modifier.clearAndSetSemantics {}),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                buildAnnotatedString {
                    append("People in ")
                    withStyle(SpanStyle(color = Color(0xFF3F3C36), fontWeight = FontWeight.SemiBold)) { append(channel) }
                },
                Modifier.weight(1f),
                style = PopStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF7A766D)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText("return to pick", style = PopStyle.copy(fontSize = 12.sp, color = Color(0xFFA7A297)))
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp).fillMaxWidth().height(ROW_H.dp)
                .graphicsLayer {
                    val n = max(0f, pose().count)
                    alpha = clamp01(n)
                    translationY = -(POP_PAD + (n - 1) * ROW_H) * density
                }
                .background(accent.copy(alpha = 0x14 / 255f), RoundedCornerShape(12.dp)),
        )
        PEOPLE.forEachIndexed { i, person ->
            PopoverRow(i, person, query, slots[i], open, user, takeT, pose, onPick)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PopoverRow(
    index: Int,
    person: MentionAutocompletePerson,
    query: String,
    slot: Int,
    open: Boolean,
    user: Boolean,
    takeT: Float,
    pose: () -> Pose,
    onPick: (Int) -> Unit,
) {
    val y = remember { Animatable(0f) }
    val a = remember { Animatable(0f) }
    var userMode by remember { mutableStateOf(false) }
    val was = remember { booleanArrayOf(false) }

    LaunchedEffect(user, slot, open, takeT) {
        if (!user) {
            userMode = false
            was[0] = false
            return@LaunchedEffect
        }
        if (!was[0]) {
            val p = autoPose(takeT)
            y.snapTo(p.rowY[index])
            a.snapTo(p.rowA[index])
            was[0] = true
        }
        userMode = true
        val visible = open && slot >= 0
        launch { if (slot >= 0) y.animateTo(slot.toFloat(), springMs(380, 0.72f)) }
        launch { a.animateTo(if (visible) 1f else 0f, androidx.compose.animation.core.tween(if (visible) 220 else 150, easing = EaseInOutQuadPop)) }
    }

    val name = person.fullName
    val range = matchRange(person, query)
    val label = remember(name, range) {
        buildAnnotatedString {
            if (range == null) append(name)
            else {
                append(name.substring(0, range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1E1D1A))) { append(name.substring(range.first, range.last + 1)) }
                append(name.substring(range.last + 1))
            }
        }
    }
    val visible = open && slot >= 0
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp).fillMaxWidth().height(ROW_H.dp)
            .graphicsLayer {
                val p = pose()
                val yy = if (userMode) y.value else p.rowY.getOrElse(index) { 0f }
                val aa = clamp01(if (userMode) a.value else p.rowA.getOrElse(index) { 0f })
                val n = max(0f, p.count)
                alpha = aa
                translationY = (-(POP_PAD + (n - 1 - yy) * ROW_H) + 6 * (1 - aa)) * density
                scaleX = 0.96f + 0.04f * aa
                scaleY = 0.96f + 0.04f * aa
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Color(0x0D1E1C18) else Color.Transparent)
            .then(
                if (visible) Modifier.clickable(interaction, indication = null, role = Role.Button, onClickLabel = "Mention $name") { onPick(index) }
                // Rows of a closed popover, or filtered out, are not read.
                else Modifier.clearAndSetSemantics {},
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        MentionAutocompleteAvatar(person.initials, person.colors, 32f, online = person.online, ring = Color(0xFFFFFEFB))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(label, style = PopStyle.copy(fontSize = 16.sp, color = Color(0xFF57534B), letterSpacing = (-0.2).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(person.role + if (person.online) "" else " · away", style = PopStyle.copy(fontSize = 13.sp, color = Color(0xFF8A857B)), maxLines = 1)
        }
    }
}

/** A Reanimated duration-based spring, approximated: natural period = duration. */
internal fun springMs(ms: Int, ratio: Float): androidx.compose.animation.core.AnimationSpec<Float> {
    if (ms <= 0) return androidx.compose.animation.core.snap()
    val w = 2 * PI / (ms / 1000.0)
    return androidx.compose.animation.core.spring(dampingRatio = ratio, stiffness = (w * w).toFloat())
}

/** Reanimated's default withTiming curve. */
internal val EaseInOutQuadPop = androidx.compose.animation.core.Easing { if (it < 0.5f) 2 * it * it else 1 - (-2 * it + 2) * (-2 * it + 2) / 2 }
