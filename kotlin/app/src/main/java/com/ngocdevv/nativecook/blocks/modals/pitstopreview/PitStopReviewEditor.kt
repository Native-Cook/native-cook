package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

@Composable
internal fun PitStopReviewEditor(
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    visible: Boolean,
    quiet: Boolean,
    scale: Float,
    viewportHeight: Float,
    safeTop: Float,
    safeBottom: Float,
    imeHeight: Float,
    rating: Int,
    note: String,
    shareBusy: Boolean,
    starSlots: (List<Offset>) -> Unit,
    onRating: (Int) -> Unit,
    onNote: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val theme = PitStopReviewTheme
    val density = LocalDensity.current
    val panelHeight = minOf(422f * scale, viewportHeight - imeHeight - safeTop - 32f)
    val panelBottom = 16f * scale + if (imeHeight > 0f) 0f else safeBottom
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .semantics { if (!visible) invisibleToUser() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = snapshot.value.panel.toFloat() }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.34f to Color.Black.copy(alpha = 0.12f),
                        0.6f to Color.Black.copy(alpha = 0.62f),
                    ),
                )
                .clickable(enabled = visible, onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-panelBottom).dp)
                .width((370f * scale).dp)
                .height(panelHeight.dp)
                .graphicsLayer {
                    val panel = snapshot.value.panel.toFloat()
                    alpha = panel
                    translationX = if (visible) 0f else 100_000f
                    if (!snapshot.value.quiet) {
                        translationY = (1f - panel) * PitStopReviewMotion.M.panelRise.toFloat() * scale * density.density
                        scaleX = PitStopReviewMotion.M.panelScale.toFloat() +
                            (1f - PitStopReviewMotion.M.panelScale.toFloat()) * panel
                        scaleY = scaleX
                    }
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape((28f * scale).dp))
                .background(theme.panel)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape((28f * scale).dp))
                .pointerInput(visible) { if (visible) detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding((24f * scale).dp)
                .semantics { paneTitle = "Review editor" },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Your race-day take",
                        color = theme.ink,
                        fontSize = pitStopReviewSp(24f, scale),
                        fontWeight = FontWeight.Bold,
                        style = pitStopReviewTextStyle(),
                    )
                    Text(
                        "What has your experience been like?",
                        color = theme.muted,
                        fontSize = pitStopReviewSp(15f, scale),
                        modifier = Modifier.padding(top = (6f * scale).dp),
                        style = pitStopReviewTextStyle(),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.requiredSize(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close review", tint = theme.ink)
                }
            }
            fun selectAt(x: Float) {
                val rowPx = with(density) { (PitStopReviewTheme.Stars.row * scale).dp.toPx() }
                onRating(ceil(x / rowPx * 5f).toInt().coerceIn(1, 5))
            }
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = (12f * scale).dp)
                    .requiredSize((PitStopReviewTheme.Stars.row * scale).dp, (PitStopReviewTheme.Stars.height * scale).dp)
                    .onGloballyPositioned { coordinates ->
                        val origin = coordinates.positionInRoot()
                        val cell = coordinates.size.width / 5f
                        val centerY = origin.y + coordinates.size.height / 2f
                        starSlots(List(5) { index -> Offset(origin.x + (index + 0.5f) * cell, centerY) })
                    }
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { point -> selectAt(point.x) },
                            onDrag = { change, _ ->
                                selectAt(change.position.x)
                                change.consume()
                            },
                        )
                    },
            ) {
                repeat(5) { index ->
                    val value = index + 1
                    Box(
                        Modifier
                            .requiredSize((PitStopReviewTheme.Stars.cell * scale).dp, (PitStopReviewTheme.Stars.height * scale).dp)
                            .semantics {
                                contentDescription = if (value == 1) "1 star" else "$value stars"
                                role = Role.RadioButton
                                selected = rating == value
                            }
                            .clickable { onRating(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        PitStopReviewStar(
                            index,
                            rating > 0 || quiet,
                            rating > 0 && value <= rating,
                            snapshot,
                            scale,
                        )
                    }
                }
            }
            BasicTextField(
                value = note,
                onValueChange = onNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((110f * scale).dp)
                    .padding(top = (10f * scale).dp)
                    .clip(RoundedCornerShape((16f * scale).dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape((16f * scale).dp))
                    .onFocusChanged { state -> onFocus(state.isFocused) }
                    .semantics { contentDescription = "Review note" },
                textStyle = TextStyle(
                    color = theme.ink,
                    fontSize = pitStopReviewSp(15f, scale),
                    platformStyle = pitStopReviewTextStyle().platformStyle,
                ),
                maxLines = 5,
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize().padding((14f * scale).dp)) {
                        if (note.isEmpty()) {
                            Text(
                                "Add a note (optional)",
                                color = theme.muted,
                                fontSize = pitStopReviewSp(15f, scale),
                                style = pitStopReviewTextStyle(),
                            )
                        }
                        field()
                    }
                },
            )
            Text(
                "${pitStopReviewGraphemeCount(note)}/280",
                color = theme.muted,
                fontSize = pitStopReviewSp(11f, scale),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = (4f * scale).dp),
                style = pitStopReviewTextStyle(),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = (12f * scale).dp)
                    .height((48f * scale).dp)
                    .clip(RoundedCornerShape((24f * scale).dp))
                    .background(theme.ink.copy(alpha = if (rating > 0) 1f else 0.55f))
                    .clickable(enabled = rating > 0 && !shareBusy, role = Role.Button, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (shareBusy) "Sharing…" else "Share review",
                    color = if (rating > 0) Color(0xFF080708) else theme.muted,
                    fontSize = pitStopReviewSp(16f, scale),
                    fontWeight = FontWeight.Bold,
                    style = pitStopReviewTextStyle(),
                )
            }
            Box(
                Modifier
                    .height((44f * scale).dp)
                    .clickable { onRating(0) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Reset rating",
                    color = theme.muted,
                    fontSize = pitStopReviewSp(14f, scale),
                    style = pitStopReviewTextStyle(),
                )
            }
        }
    }
}

@Composable
private fun PitStopReviewStar(
    index: Int,
    shown: Boolean,
    selected: Boolean,
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    scale: Float,
) {
    Canvas(
        Modifier
            .requiredSize((PitStopReviewTheme.Stars.box * scale).dp)
            .graphicsLayer {
                val scene = snapshot.value.scene
                alpha = if (shown) 1f else PitStopReviewMotion.starLanded(scene, index).toFloat()
                val starScale = if (shown) 1f else PitStopReviewMotion.starScale(scene, index).toFloat()
                scaleX = starScale
                scaleY = starScale
            },
    ) {
        val path = Path()
        repeat(10) { pointIndex ->
            val angle = -Math.PI / 2.0 + pointIndex * Math.PI / 5.0
            val radius = if (pointIndex % 2 == 0) {
                PitStopReviewTheme.Stars.radius
            } else {
                PitStopReviewTheme.Stars.radius * 0.46f
            } * scale * density
            val point = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * radius,
                center.y + kotlin.math.sin(angle).toFloat() * radius,
            )
            if (pointIndex == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        if (selected) drawPath(path, PitStopReviewTheme.yellow)
        drawPath(
            path,
            if (selected) Color.Black.copy(alpha = 0.35f) else Color.White,
            style = Stroke(2f * scale * density, join = StrokeJoin.Round),
        )
    }
}
