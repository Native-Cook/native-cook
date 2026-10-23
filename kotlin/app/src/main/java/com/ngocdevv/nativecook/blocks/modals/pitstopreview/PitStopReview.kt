package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.accessibility.AccessibilityManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PitStopReview(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val accessibility = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val reducedMotion = rememberReducedMotion()
    var touchExploration by remember {
        mutableStateOf(accessibility?.let(AccessibilityManagerCompat::isTouchExplorationEnabled) == true)
    }
    val quiet = reducedMotion || touchExploration
    val controller = remember {
        PitStopReviewController(autoPlay, nowSeconds()).apply {
            setForeground(false, nowSeconds())
        }
    }
    val runtime = remember(controller) { PitStopReviewRuntime(controller) }
    val assets = remember(context) { PitStopReviewAssets(context.applicationContext) }
    val fonts = remember(context) { PitStopReviewFonts(context.applicationContext) }
    val scroll = rememberScrollState()
    val starSlots = remember { mutableStateOf<List<Offset>>(emptyList()) }
    var panelVisible by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var shareBusy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(PitStopReviewTab.Home) }
    var destination by remember { mutableStateOf<PitStopReviewDestination?>(null) }
    var savedWeekends by remember { mutableStateOf(setOf<String>()) }
    var passSaved by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf(listOf(true, true, true)) }
    var lapCount by remember { mutableStateOf(3) }
    var displayName by remember { mutableStateOf("Alex Vale") }
    var selectedCircuit by remember { mutableStateOf("Harbor") }
    var reactionText by remember { mutableStateOf("Ready") }
    var reactionStart by remember { mutableStateOf<Double?>(null) }
    var reactionJob by remember { mutableStateOf<Job?>(null) }
    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(density).toDp().value }
    val safeBottom = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp().value }
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp().value }
    val imeVisible = imeHeight > 0f

    fun refreshDraft() {
        val now = nowSeconds()
        rating = controller.displayRating(now)
        note = controller.displayNote(now)
    }

    fun publish() {
        runtime.publish()
    }

    fun releaseKeyboard() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        controller.setKeyboardPresented(false, nowSeconds())
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        shareBusy = false
        controller.setSharing(false, nowSeconds())
        publish()
    }

    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = enabled
        }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val active = event == Lifecycle.Event.ON_RESUME
            if (active || event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (active) controller.setSharing(false, nowSeconds())
                controller.setForeground(active, nowSeconds())
                if (!active) {
                    reactionJob?.cancel()
                    reactionJob = null
                    reactionStart = null
                    reactionText = "Ready"
                }
                publish()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            reactionJob?.cancel()
        }
    }
    LaunchedEffect(runtime) { runtime.runClock() }
    LaunchedEffect(lifecycleOwner) {
        controller.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            nowSeconds(),
        )
        publish()
    }
    LaunchedEffect(autoPlay) {
        controller.setAutoPlay(autoPlay, nowSeconds())
        publish()
    }
    LaunchedEffect(quiet) {
        controller.setQuiet(quiet, nowSeconds())
        publish()
    }
    LaunchedEffect(imeVisible) {
        controller.setKeyboardPresented(imeVisible, nowSeconds())
        publish()
    }
    LaunchedEffect(tab) {
        controller.setHomeSelected(tab == PitStopReviewTab.Home, nowSeconds())
        controller.setFeedAtTop(tab == PitStopReviewTab.Home, nowSeconds())
        scroll.scrollTo(0)
        publish()
    }
    LaunchedEffect(destination) {
        controller.setDestinationOwned(destination != null, nowSeconds())
        publish()
    }
    LaunchedEffect(scroll, tab) {
        snapshotFlow { scroll.value <= with(density) { 4.dp.roundToPx() } }
            .distinctUntilChanged()
            .collect { top ->
                controller.setFeedAtTop(tab == PitStopReviewTab.Home && top, nowSeconds())
                publish()
            }
    }
    LaunchedEffect(runtime) {
        snapshotFlow { runtime.snapshot.value.panelVisible }
            .distinctUntilChanged()
            .collect { visible ->
                panelVisible = visible
                if (visible) refreshDraft()
            }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PitStopReviewTheme.background)) {
        val scale = maxWidth.value / PitStopReviewTheme.REFERENCE_WIDTH
        val viewportHeight = maxHeight.value
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = !panelVisible)
                .padding(top = safeTop.dp, bottom = (120f * scale).dp)
                .semantics { if (panelVisible) hideFromAccessibility() },
        ) {
            PitStopReviewHome(
                runtime.snapshot,
                assets,
                fonts,
                scale,
                displayName,
                cards,
                onWrite = {
                    controller.openEditor(nowSeconds())
                    refreshDraft()
                    publish()
                },
                onDestination = { next ->
                    controller.setDestinationOwned(true, nowSeconds())
                    destination = next
                    publish()
                },
                modifier = Modifier
                    .graphicsLayer { translationX = if (tab == PitStopReviewTab.Home) 0f else -100_000f }
                    .semantics { if (tab != PitStopReviewTab.Home) hideFromAccessibility() },
            )
            PitStopReviewTab.entries.filter { page -> page != PitStopReviewTab.Home }.forEach { page ->
                val active = tab == page
                PitStopReviewTabPage(
                    page,
                    scale,
                    savedWeekends,
                    { savedWeekends = it },
                    cards,
                    { cards = it },
                    selectedCircuit,
                    { selectedCircuit = it },
                    Modifier
                        .height((1150f * scale).dp)
                        .zIndex(if (active) 1f else 0f)
                        .graphicsLayer {
                            alpha = if (active) 1f else 0f
                            translationX = if (active) 0f else 100_000f
                        }
                        .semantics { if (!active) hideFromAccessibility() },
                )
            }
        }
        PitStopReviewDock(
            tab,
            scale,
            panelVisible,
            quiet,
            onSelect = { selected ->
                val home = selected == PitStopReviewTab.Home
                controller.setHomeSelected(home, nowSeconds())
                if (home) controller.setFeedAtTop(true, nowSeconds())
                tab = selected
                publish()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (PitStopReviewTheme.TabBar.bottom * scale).dp)
                .semantics { if (panelVisible) hideFromAccessibility() },
        )
        PitStopReviewEditor(
            runtime.snapshot,
            panelVisible,
            quiet,
            scale,
            viewportHeight,
            safeTop,
            safeBottom,
            imeHeight,
            rating,
            note,
            shareBusy,
            starSlots = { slots -> starSlots.value = slots },
            onRating = { value ->
                val changed = !controller.editorOwned || value != controller.rating
                controller.setRating(value, nowSeconds())
                refreshDraft()
                publish()
                if (changed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            },
            onNote = { value ->
                controller.setNote(value, nowSeconds())
                refreshDraft()
                publish()
            },
            onFocus = { focused ->
                controller.setKeyboardPresented(focused, nowSeconds())
                publish()
            },
            onClose = {
                releaseKeyboard()
                controller.closeEditor(nowSeconds())
                publish()
            },
            onShare = {
                releaseKeyboard()
                if (controller.beginShare(nowSeconds())) {
                    shareBusy = true
                    shareLauncher.launch(pitStopReviewShareIntent(controller.rating, controller.note))
                    publish()
                }
            },
        )
        PitStopReviewLights(
            runtime.snapshot,
            starSlots,
            scale,
            safeTop,
            Modifier.fillMaxSize(),
        )

        destination?.let { current ->
            ModalBottomSheet(
                onDismissRequest = {
                    controller.setDestinationOwned(false, nowSeconds())
                    destination = null
                    if (current == PitStopReviewDestination.Reflex) {
                        reactionJob?.cancel()
                        reactionText = "Ready"
                    }
                    publish()
                },
                containerColor = Color(0xFF171518),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pitStopReviewSheetTitle(current), color = PitStopReviewTheme.ink)
                    TextButton(
                        onClick = {
                            controller.setDestinationOwned(false, nowSeconds())
                            destination = null
                            if (current == PitStopReviewDestination.Reflex) {
                                reactionJob?.cancel()
                                reactionText = "Ready"
                            }
                            publish()
                        },
                    ) { Text("Done") }
                }
                PitStopReviewDestinationContent(
                    current,
                    passSaved,
                    { passSaved = it },
                    displayName,
                    { displayName = it },
                    savedWeekends,
                    { savedWeekends = it },
                    cards,
                    { cards = it },
                    lapCount,
                    { lapCount = it },
                    reactionText,
                    reactionAction = {
                        when {
                            reactionText.endsWith(" ms") -> {
                                reactionText = "Ready"
                                reactionStart = null
                            }
                            reactionText == "Ready" || reactionText.startsWith("Too soon") -> {
                                reactionText = "Get ready…"
                                reactionJob?.cancel()
                                reactionJob = scope.launch {
                                    delay(3_000)
                                    if (destination == PitStopReviewDestination.Reflex) {
                                        reactionStart = nowSeconds()
                                        reactionText = "GO"
                                    }
                                }
                            }
                            reactionText == "GO" -> {
                                reactionText = "${((nowSeconds() - (reactionStart ?: nowSeconds())) * 1_000).toInt()} ms"
                                reactionStart = null
                            }
                            else -> {
                                reactionJob?.cancel()
                                reactionText = "Too soon. Try again"
                            }
                        }
                    },
                    selectedCircuit,
                    { selectedCircuit = it },
                )
            }
        }
    }
}

private fun pitStopReviewShareIntent(rating: Int, note: String): Intent {
    val trimmed = note.trim()
    val text = "Turn Nine — $rating/5" + if (trimmed.isNotEmpty()) "\n$trimmed" else ""
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return Intent.createChooser(send, "Share review")
}
