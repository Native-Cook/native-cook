@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 Folio Feature Library: an app-library home for invented apps, with a Featured carousel of glass orbs, an
 Invites card, Popular apps under category chips and a Recently added list, under a frosted Home and Explore
 pill. Opening a feature fills the screen through a loading lens into a library; Generate New opens a topic
 sheet, a book is created, and it opens into a readable detail.

 autoPlay (FOLIO_FEATURE_LIBRARY_LOOP = 39 s), as in the React Native block: the carousel turns at 0.5, 1.5
 and 2.5 · the page scrolls down 3.5 to 6.6 and back at 8 · the first card opens at 9.75 (loading lens, 2.75 s)
 · the library at 12.5, its header card contracting 13.5 to 14.5 · topics open at 16, Productivity is picked at
 17.25, creation 18.25 to 22.5, the cover at 24 · the detail pushes in at 26.5, its sections fill from 28.5, the
 author at 32.9, the reading pass 29.8 to 34.6 · back to the library at 35.5 and home at 36.75.
 A touch (or a drag) takes over; 2 s after the last one, if nothing but the home was used, the loop restarts.
 Once a library was used the loop stays stopped so created books and favourites are kept.
 previewTime freezes the script at that second and switches touch off.
 Reduced motion: screens change with fades, no loop.

 The code mirrors the React Native component: FolioFeatureLibraryModel holds its states and handlers, the
 clock and scheduler replace setTimeout and withTiming, so every frame is a function of the loop time.
*/

/** Loop period of `autoPlay`, seconds (`LOOP` in the React Native block). */
const val FOLIO_FEATURE_LIBRARY_LOOP = 39.0

/**
 * The whole block, filling the screen under a visible, dark-icon status bar.
 *
 * [onBookCreated] receives the picked topics when a book has been created (not in the autoplay script).
 */
@Composable
fun FolioFeatureLibrary(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onBookCreated: ((List<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    FolioStatusBar()

    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(readReducedMotion(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = readReducedMotion(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    var assets by remember { mutableStateOf<FolioFeatureLibraryAssets?>(null) }
    LaunchedEffect(Unit) {
        assets = withContext(Dispatchers.Default) { FolioFeatureLibraryAssets.load(context.applicationContext) }
    }

    // `useSafeAreaInsets`: the status bar or cutout on top, the navigation bar at the bottom.
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(FolioScreenBg)) {
        val loaded = assets
        if (loaded != null) {
            val metrics = FolioMetrics(maxWidth.value, maxHeight.value, top, bottom, density.fontScale)
            // React Native remounts the screen when the system font scale changes (`key={type-fontScale}`).
            key(metrics.fontScale) {
                FolioFeatureLibraryContent(
                    autoPlay = autoPlay,
                    previewTime = previewTime,
                    onBookCreated = onBookCreated,
                    reduced = reduced,
                    foreground = foreground,
                    metrics = metrics,
                    assets = loaded,
                )
            }
        }
    }
}

private fun readReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** `<StatusBar style="dark" />`: dark icons while the block is open. */
@Composable
private fun FolioStatusBar() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
}

@Composable
private fun FolioFeatureLibraryContent(
    autoPlay: Boolean,
    previewTime: Double?,
    onBookCreated: ((List<String>) -> Unit)?,
    reduced: Boolean,
    foreground: Boolean,
    metrics: FolioMetrics,
    assets: FolioFeatureLibraryAssets,
) {
    val density = LocalDensity.current
    val frozenAt = previewTime?.rem(FOLIO_FEATURE_LIBRARY_LOOP)
    val clock = remember(frozenAt) { FolioFeatureLibraryClock(frozenAt) }
    val model = remember(clock) { FolioFeatureLibraryModel(clock, metrics) }
    val frames = remember { FolioFrames() }
    val outerScroll = rememberScrollState()
    val carouselState = rememberLazyListState()
    val libraryScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val topicScroll = rememberScrollState()
    val host = remember(outerScroll, carouselState, libraryScroll, detailScroll, topicScroll) {
        FolioScrollHost(outerScroll, carouselState, libraryScroll, detailScroll, topicScroll)
    }
    val exploreLayer = rememberGraphicsLayer()
    val blurSource = remember(exploreLayer) { FolioBlurSource(exploreLayer) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Inputs are handed to the model while composing, so every screen below reads the current values.
    model.autoPlay = autoPlay
    model.reduced = reduced
    model.metrics = metrics
    model.onBookCreated = onBookCreated
    model.haptic = rememberFolioHaptic()
    model.dismissKeyboard = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    model.frameOf = { origin -> folioMeasureFrame(origin, frames, host, model.metrics, density.density) }
    model.attach(host, density.density)

    LaunchedEffect(model) { model.pump() }
    LaunchedEffect(foreground) {
        if (model.foreground != foreground) model.applyForeground(foreground)
    }
    DisposableEffect(model) { onDispose { model.dispose() } }

    // The effect that runs `cycle()`: it restarts the script whenever one of these changes.
    LaunchedEffect(model, autoPlay, model.manual, reduced, foreground, model.notice != null) {
        if (!autoPlay || model.manual || reduced || !foreground || model.notice != null || model.userOwned) {
            return@LaunchedEffect
        }
        model.startTimeline()
        try {
            awaitCancellation()
        } finally {
            model.stopTimeline()
        }
    }
    // Two seconds after a person stops, the loop may take over again.
    DisposableEffect(model, model.manual, autoPlay, foreground, model.notice, reduced, model.screen, model.topicsOpen) {
        model.resumeConditionsChanged()
        onDispose { model.cancelResume() }
    }

    val touchModifier = if (clock.frozen) {
        Modifier
    } else {
        Modifier.pointerInput(model) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    var began = false
                    var ended = false
                    for (change in event.changes) {
                        if (change.pressed && !change.previousPressed) began = true
                        if (!change.pressed && change.previousPressed) ended = true
                    }
                    if (began) model.touchBegin()
                    if (ended) model.touchEnd(event.changes.any { it.pressed })
                }
            }
        }
    }

    CompositionLocalProvider(LocalFolioClock provides clock, LocalFolioIonicons provides assets.ionicons) {
        Box(
            Modifier
                .fillMaxSize()
                .then(touchModifier)
                .onGloballyPositioned { frames.root = it },
        ) {
            FolioExplore(model, assets, host, frames, blurSource)
            FolioAppSurface(model, assets, host)
            FolioCardBridge(model, assets)
        }
        FolioTopicsSheet(model, host)
        FolioNotice(model, assets)
    }
}

/**
 * `measureInWindow` of the tapped card relative to the root, in dp. A feature card is worked out from the
 * carousel's position and scroll offset (so it also works while the card is scrolled out of the list).
 */
private fun folioMeasureFrame(
    origin: FolioReturnOrigin,
    frames: FolioFrames,
    host: FolioScrollHost,
    metrics: FolioMetrics,
    unit: Float,
): FolioFrame? {
    val root = frames.root ?: return null
    if (!root.isAttached) return null
    fun boundsOf(target: LayoutCoordinates?): FolioFrame? {
        if (target == null || !target.isAttached) return null
        val rect = root.localBoundingBoxOf(target, false)
        return FolioFrame(rect.left / unit, rect.top / unit, rect.width / unit, rect.height / unit)
    }
    return when (origin) {
        FolioReturnOrigin.Recent -> boundsOf(frames.recent)
        FolioReturnOrigin.Plus -> boundsOf(frames.plus)
        is FolioReturnOrigin.Feature -> {
            val row = frames.carousel
            if (row == null || !row.isAttached) return null
            val rect = root.localBoundingBoxOf(row, false)
            val cardPx = (metrics.cardWidth * unit).roundToInt()
            val step = cardPx + (14f * unit).roundToInt()
            val scrolled = host.carousel.firstVisibleItemIndex * step + host.carousel.firstVisibleItemScrollOffset
            val left = rect.left + 19f * unit + origin.index * step - scrolled
            FolioFrame(left / unit, rect.top / unit, cardPx / unit, 330f)
        }
    }
}
