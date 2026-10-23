package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged

/** The accepted Swift component's ten-second preview never mutates committed order. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PaddockWidgetBento(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val quiet = rememberPaddockQuietMotion()
    val state = remember { PaddockWidgetBentoContentState() }
    val runtime = remember { PaddockWidgetBentoRuntime(autoPlay, quiet) }
    val scope = rememberCoroutineScope()
    val reaction = remember { PaddockWidgetBentoReaction(state, runtime, scope) }
    val assets = remember(context) { PaddockWidgetBentoAssets(context.applicationContext) }
    val fonts = remember(context) { PaddockFonts(context.applicationContext) }
    SideEffect { runtime.reducedMotion = quiet }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun present(destination: PaddockWidgetBentoDestination) {
        runtime.cancel()
        runtime.setVisible(false)
        state.destination = destination
        if (destination != PaddockWidgetBentoDestination.Reflex) reaction.cancel()
    }

    fun move(id: PaddockWidgetBentoCardID, offset: Int) {
        val now = paddockNowSeconds()
        runtime.controller.setEditing(true, now)
        if (runtime.controller.move(id, offset, now)) {
            view.announceForAccessibility("${id.cardName()} moved")
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        runtime.publish(now)
    }

    fun toggleEditing() {
        runtime.controller.setEditing(!runtime.controller.isEditing, paddockNowSeconds())
        runtime.publish()
    }

    fun restoreOrder() {
        runtime.restore(
            autoPlay = runtime.controller.autoPlay,
            quiet = runtime.controller.isQuiet,
            visible = runtime.controller.isVisible,
        )
    }

    fun action(destination: PaddockWidgetBentoDestination) {
        if (destination == PaddockWidgetBentoDestination.CloseGuide) {
            state.guideVisible = false
        } else {
            present(
                if (destination == PaddockWidgetBentoDestination.Banner) {
                    PaddockWidgetBentoDestination.WidgetStudio
                } else {
                    destination
                },
            )
        }
    }

    LaunchedEffect(runtime) { runtime.runClock() }
    LaunchedEffect(autoPlay) {
        runtime.controller.setAutoPlay(autoPlay, paddockNowSeconds())
        runtime.publish()
    }
    LaunchedEffect(quiet) {
        runtime.controller.setQuiet(quiet, paddockNowSeconds())
        runtime.publish()
    }
    DisposableEffect(lifecycleOwner, runtime) {
        runtime.setActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ ->
            val active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            runtime.setActive(active)
            if (!active) reaction.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runtime.setActive(false)
            reaction.cancel()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
            val scale = maxWidth.value / 402f
            val density = LocalDensity.current
            val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
            val feedLayer = rememberGraphicsLayer()
            val home = state.selectedTab == PaddockWidgetBentoDestination.Home
            key(state.selectedTab) {
                val scroll = rememberScrollState()
                val pull = remember(scroll, scale, runtime) {
                    PaddockPullConnection(scroll, scale, density.density, runtime) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }
                LaunchedEffect(scroll, scale, state.destination) {
                    snapshotFlow { scroll.value <= with(density) { (4.dp * scale).toPx() } }
                        .distinctUntilChanged()
                        .collect { atTop ->
                            runtime.setVisible(
                                state.selectedTab == PaddockWidgetBentoDestination.Home &&
                                    atTop && state.destination == null,
                            )
                        }
                }
                Column(
                    Modifier.fillMaxSize()
                        .drawWithContent {
                            feedLayer.record {
                                drawRect(Color.Black)
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(feedLayer)
                        }
                        .padding(top = if (home) 0.dp else safeTop)
                        .then(if (home) Modifier.nestedScroll(pull) else Modifier)
                        .verticalScroll(scroll),
                ) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = PaddockTheme.contentMinHeight.dp * scale)
                            .graphicsLayer { translationY = pull.pull },
                    ) {
                        if (home) {
                            PaddockHome(
                                scale, state, assets, fonts, runtime, ::action,
                                onTap = { present(it.cardDestination()) },
                                onMove = ::move,
                                onLift = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                                onCommit = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                            )
                        } else {
                            PaddockWidgetBentoSecondaryPage(
                                scale, state, runtime.order, runtime.isEditing,
                                ::move, ::toggleEditing, ::restoreOrder,
                            )
                        }
                    }
                    Spacer(Modifier.height(PaddockTheme.bottomClearance.dp * scale))
                }
            }
            PaddockDock(
                scale, state.selectedTab, feedLayer, maxHeight.value, quiet,
                Modifier.align(Alignment.BottomCenter).padding(bottom = PaddockTheme.dockBottom.dp * scale),
            ) { tab ->
                if (state.selectedTab != tab) {
                    runtime.cancel()
                    if (runtime.isEditing) runtime.done()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state.selectedTab = tab
                    runtime.setVisible(tab == PaddockWidgetBentoDestination.Home && state.destination == null)
                }
            }
        }

        state.destination?.let { destination ->
            ModalBottomSheet(
                onDismissRequest = {
                    state.destination = null
                    reaction.cancel()
                },
                sheetState = sheetState,
                containerColor = Color(0xFF171518),
                contentColor = Color.White,
            ) {
                Column(
                    Modifier.fillMaxSize().imePadding()
                        .semantics { paneTitle = paddockSheetTitle(destination) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(paddockSheetTitle(destination), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            state.destination = null
                            reaction.cancel()
                        }) {
                            Text("Done")
                        }
                    }
                    PaddockWidgetBentoDestinationContent(
                        destination, state, runtime.order, runtime.isEditing,
                        ::move, ::toggleEditing, ::restoreOrder, reaction::act,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Pull the feed past its top and let go: the pit crew rebuilds the bento (a nested-scroll pull, as Android has no overscroll). */
private class PaddockPullConnection(
    private val scroll: androidx.compose.foundation.ScrollState,
    private val scale: Float,
    private val density: Float,
    private val runtime: PaddockWidgetBentoRuntime,
    private val onPit: () -> Unit,
) : NestedScrollConnection {
    var pull by mutableFloatStateOf(0f)
        private set
    private var raw = 0f
    private var gesture = false
    private var fromTop = false

    private fun rubber(value: Float, limit: Float) = (1f - 1f / ((value * 0.55f) / limit + 1f)) * limit

    private fun begin() {
        if (gesture) return
        gesture = true
        fromTop = scroll.value <= 4f * scale * density && !runtime.peelHeld
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        begin()
        if (fromTop && raw > 0f && available.y < 0f) {
            val consumed = max(available.y, -raw)
            raw += consumed
            pull = rubber(raw, PaddockWidgetBentoMotion.Pull.rubber.toFloat() * scale * density)
            return Offset(0f, consumed)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        begin()
        if (fromTop && available.y > 0f && scroll.value == 0) {
            raw += available.y
            pull = rubber(raw, PaddockWidgetBentoMotion.Pull.rubber.toFloat() * scale * density)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulled = pull
        val far = pulled >= PaddockWidgetBentoMotion.Pull.trigger * scale * density
        val flick = available.y >= PaddockWidgetBentoMotion.Pull.flick * scale * density &&
            pulled >= PaddockWidgetBentoMotion.Pull.trigger * PaddockWidgetBentoMotion.Pull.flickShare * scale * density
        if (fromTop && (far || flick) && runtime.startPit()) onPit()
        gesture = false
        fromTop = false
        raw = 0f
        if (pull != 0f) {
            val stiffness = ((2 * PI / 0.4) * (2 * PI / 0.4)).toFloat()
            animate(pull, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = stiffness)) { value, _ -> pull = value }
        }
        return Velocity.Zero
    }
}

@Composable
private fun PaddockHome(
    scale: Float,
    state: PaddockWidgetBentoContentState,
    assets: PaddockWidgetBentoAssets,
    fonts: PaddockFonts,
    runtime: PaddockWidgetBentoRuntime,
    onAction: (PaddockWidgetBentoDestination) -> Unit,
    onTap: (PaddockWidgetBentoCardID) -> Unit,
    onMove: (PaddockWidgetBentoCardID, Int) -> Unit,
    onLift: () -> Unit,
    onCommit: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val t = PaddockTheme
    Box(Modifier.width(t.REFERENCE_WIDTH.dp * scale).height((t.contentHeight + state.pinnedWidgets.size * t.pinnedPitch).dp * scale)) {
        PaddockPitTrails(scale, runtime)
        PaddockWidgetBentoStaticGrid(
            scale, state.guideVisible, assets, fonts, runtime,
            onPeelEnd = {
                scope.launch {
                    delay((PaddockWidgetBentoMotion.Peel.release * 1000).toLong())
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            },
            onAction = onAction,
        )
        PaddockGooLayer(scale, runtime)
        PaddockWidgetBentoQuickCards(scale, fonts, runtime, onTap, onMove)
        Box(
            Modifier.offset(x = t.touchSurface.left.dp * scale, y = t.touchSurface.top.dp * scale)
                .size(t.touchSurface.width.dp * scale, t.touchSurface.height.dp * scale)
                .zIndex(7f)
                .paddockQuickCardGestures(scale, runtime, onTap, onLift, onCommit),
        )
        if (runtime.isEditing) {
            Box(
                Modifier.offset(x = (t.doneCenterX - t.doneWidth / 2).dp * scale, y = t.doneCenterY.dp * scale - 44.dp / 2)
                    .size(t.doneWidth.dp * scale, 44.dp)
                    .zIndex(8f)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = runtime::done)
                    .semantics { contentDescription = "Done. Finish arranging quick cards" },
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = Color.Black, fontSize = t.doneSize.sp * scale, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier.offset(x = t.screenInset.dp * scale, y = t.pinnedTop.dp * scale),
            verticalArrangement = Arrangement.spacedBy(t.pinnedGap.dp * scale),
        ) {
            state.pinnedWidgets.forEach { widget ->
                Row(
                    Modifier.size(382.dp * scale, t.pinnedHeight.dp * scale)
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(t.radiusPinned.dp * scale))
                        .padding(horizontal = t.pinnedInset.dp * scale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(paddockWidgetIcon(widget), null)
                    Text(widget, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Pinned", color = t.pinnedLabel)
                }
            }
        }
    }
}

private fun paddockSheetTitle(destination: PaddockWidgetBentoDestination): String {
    return when (destination) {
        PaddockWidgetBentoDestination.WidgetStudio, PaddockWidgetBentoDestination.Banner -> "Widget Studio"
        PaddockWidgetBentoDestination.HomeCards -> "Homecards"
        else -> destination.name
    }
}
