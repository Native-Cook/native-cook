package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The topic sheet (`Modal` with `animationType="slide"`, a fade under Reduce Motion): a backdrop, and on it
 * a sheet almost as tall as the screen with the topic chips, a count band and the Generate button. The whole
 * modal slides up, backdrop included. While the keyboard is open the sheet shrinks to fit above it.
 */
@Composable
internal fun FolioTopicsSheet(model: FolioFeatureLibraryModel, host: FolioScrollHost) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val open = model.topicsOpen
    FolioHitGate(interactive = open, hidden = !open) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = model.sheet.value(clock.frame.doubleValue)
                    if (model.reduced) {
                        alpha = progress
                    } else {
                        translationY = (1f - progress) * metrics.height * unit
                    }
                }
                .background(Color(0x38000000))
                .imePadding(),
        ) {
            // The dismiss area behind the sheet.
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        role = Role.Button
                        contentDescription = "Dismiss topic selection"
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { model.closeTopics() }) },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height((metrics.height - max(metrics.top + 3f, 40f)).dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(FolioSurfaceBg)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                FolioProgressiveBlurViewport(
                    enabled = open && !model.topicInputActive,
                    overflowing = { host.topics.maxValue > 4f * unit },
                    backgroundColor = FolioSurfaceBg,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(host.topics)
                            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
                    ) {
                        FolioText(
                            text = "Select Topics",
                            size = 20f,
                            color = FolioInk,
                            weight = FontWeight.SemiBold,
                            letterSpacing = -0.3f,
                            modifier = Modifier.semantics { heading() },
                        )
                        FolioText(
                            text = "Choose topics for your book summary, or select none for a random book",
                            size = 13f,
                            lineHeight = 17f,
                            color = FolioInkSoft,
                            modifier = Modifier.padding(top = 6.dp, bottom = 30.dp),
                        )
                        FolioTopicChips(model)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().background(Color(0xFFEFEDF5)).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val count = model.selected.size
                    FolioText(
                        text = if (count == 0) "Random book summary" else "$count ${if (count == 1) "topic" else "topics"} selected",
                        size = 13f,
                        color = Color(0xFF6A6A6A),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = (metrics.bottom + 10f).dp),
                ) {
                    FolioButton(
                        label = if (model.generating) "Generating…" else "Generate",
                        onClick = { model.generate() },
                        reduced = model.reduced,
                        busy = model.generating,
                        disabled = model.generating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolioTopicChips(model: FolioFeatureLibraryModel) {
    val clock = LocalFolioClock.current
    val list = model.topicList
    val all = list + model.selected.filter { it !in list }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        all.forEach { topic ->
            val isSelected = topic in model.selected
            val moreIndex = FolioMoreTopics.indexOf(topic)
            val chipModifier = if (moreIndex >= 0 && !model.reduced) {
                Modifier.graphicsLayer {
                    alpha = folioFadeIn(clock.frame.doubleValue, model.moreTopicsAt, moreIndex * 0.04, 0.26, false)
                }
            } else {
                Modifier
            }
            FolioPressable(
                onClick = { model.toggleTopic(topic) },
                scale = 1f,
                enabled = !model.generating,
                label = topic,
                modifier = chipModifier
                    .height(40.dp)
                    .folioShadow(if (isSelected) FolioShadowTopicSelected else FolioShadowTopic) { 20f }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) FolioBlue else Color(0xFFF2F2F4))
                    .then(
                        if (isSelected) {
                            Modifier
                        } else {
                            Modifier.folioInset(Color(1f, 1f, 1f, 0.9f), offsetY = 0f, spread = 1f, radius = 20f)
                        },
                    )
                    .padding(horizontal = 15.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FolioText(
                        text = topic,
                        size = 15f,
                        color = if (isSelected) FolioWhite else FolioInkBar,
                        weight = FontWeight.Medium,
                    )
                    if (isSelected) {
                        FolioZoomIn(enabled = !model.reduced, damping = 14f, stiffness = 320f) {
                            FolioIconView(FolioIcon.CheckmarkCircleOutline, 17f, FolioWhite)
                        }
                    }
                }
            }
        }
        FolioAddTopic(model)
    }
}

/** The dashed "Add yours" pill, which turns into a text field when tapped. */
@Composable
private fun FolioAddTopic(model: FolioFeatureLibraryModel) {
    val dashed = Modifier
        .height(40.dp)
        .drawBehind {
            val width = 1.5f * density
            val dash = 3f * width
            drawRoundRect(
                color = FolioBlue,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(20f * density),
                style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f)),
            )
        }
    if (model.addingTopic) {
        val focus = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(dashed.widthIn(min = 140.dp).padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = model.customTopic,
                onValueChange = { if (it.length <= 32) model.customTopic = it },
                enabled = !model.generating,
                singleLine = true,
                textStyle = folioTextStyle(15f, FolioBlue, null, null, 0f, TextAlign.Unspecified, null, false, null, true),
                cursorBrush = SolidColor(FolioBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { model.commitCustomTopic() }),
                modifier = Modifier
                    .widthIn(min = 110.dp)
                    .focusRequester(focus)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                            model.topicInputFocused()
                        } else if (hadFocus) {
                            hadFocus = false
                            model.topicInputBlurred()
                        }
                    }
                    .semantics { contentDescription = "Add your own topic" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (model.customTopic.isEmpty()) {
                            FolioText(text = "Your topic", size = 15f, color = Color(0xFF7DA3E8))
                        }
                        inner()
                    }
                },
            )
        }
    } else {
        FolioPressable(
            onClick = { model.startAddingTopic() },
            scale = 1f,
            enabled = !model.generating,
            label = "Add your own topic",
            modifier = dashed.padding(horizontal = 15.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolioIconView(FolioIcon.Add, 17f, FolioBlue)
                FolioText(text = "Add yours", size = 15f, color = FolioBlue, weight = FontWeight.Medium)
            }
        }
    }
}
