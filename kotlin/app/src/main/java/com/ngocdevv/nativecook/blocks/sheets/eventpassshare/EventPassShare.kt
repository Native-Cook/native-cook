package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min

private val StrongOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val SheetReferenceTop = 62f
private const val SheetOverdraw = 120f
private val ActionColor = Color.White.copy(alpha = 0.50f)

private enum class EventPassShareAction {
    Copy,
    Share,
    Export,
}

@Composable
fun EventPassShare(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val initialReducedMotion = rememberReducedMotion()
    var reducedMotion by remember { mutableStateOf(initialReducedMotion) }
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    var touchExploration by remember {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    val quiet = reducedMotion || touchExploration
    val assets = remember(context.applicationContext) {
        EventPassShareAssets(context.applicationContext)
    }
    val ambientPainter = remember { EventPassShareAmbientPainter() }
    val runtime = remember(ambientPainter.isDynamic) {
        EventPassShareRuntime(EventPassShareController(), ambientPainter.isDynamic)
    }
    val sheetOffset = remember { mutableFloatStateOf(0f) }
    val sheetAnimation = remember { AtomicReference<Job?>(null) }
    var sheetOpen by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<EventPassShareAction?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        busy = null
        runtime.setSharePresented(false)
    }

    fun launchAction(action: EventPassShareAction) {
        if (busy != null) return
        error = null
        if (action == EventPassShareAction.Copy) {
            busy = action
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Event details", EventPassShareContent.Details),
                )
            }.onSuccess {
                runtime.confirmCopy()
                eventPassShareSuccessHaptic(view)
            }.onFailure {
                error = "Couldn’t copy event details. Try again."
            }
            busy = null
            return
        }

        busy = action
        runtime.setSharePresented(true)
        eventPassShareLightHaptic(view)
        coroutineScope.launch {
            runCatching {
                when (action) {
                    EventPassShareAction.Share -> createEventPassTextShareIntent(context)
                    EventPassShareAction.Export -> createEventPassImageShareIntent(context, assets)
                    EventPassShareAction.Copy -> error("Copy is handled synchronously")
                }
            }.onSuccess { chooser ->
                runCatching { shareLauncher.launch(chooser) }
                    .onFailure {
                        error = if (action == EventPassShareAction.Share) {
                            "Couldn’t share this event. Try again."
                        } else {
                            "Couldn’t export the event image. Try again."
                        }
                        busy = null
                        runtime.setSharePresented(false)
                    }
            }.onFailure {
                error = if (action == EventPassShareAction.Share) {
                    "Couldn’t share this event. Try again."
                } else {
                    "Couldn’t export the event image. Try again."
                }
                busy = null
                runtime.setSharePresented(false)
            }
        }
    }

    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = enabled
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager?.removeTouchExplorationStateChangeListener(listener) }
    }

    DisposableEffect(view) {
        val activity = generateSequence(context) { candidate ->
            (candidate as? android.content.ContextWrapper)?.baseContext
        }.filterIsInstance<android.app.Activity>().firstOrNull()
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = bars?.isAppearanceLightStatusBars
        val oldNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (oldStatus != null) bars.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) bars.isAppearanceLightNavigationBars = oldNavigation
        }
    }

    DisposableEffect(lifecycleOwner, runtime) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    reducedMotion = Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ) == 0f
                    runtime.setForeground(true)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> runtime.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        runtime.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runtime.cancelInteraction()
        }
    }

    LaunchedEffect(runtime) { runtime.runClock() }
    LaunchedEffect(runtime, autoPlay, quiet) {
        runtime.configure(autoPlay, quiet, nowSeconds())
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF0B1518))) {
        val pixelScale = constraints.maxWidth / 402f
        val layoutScale = pixelScale / density.density
        fun point(value: Float): Dp = (value * layoutScale).dp
        val safeTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val safeTop = with(density) { safeTopPx.toDp() }
        val sheetViewportHeight = (maxHeight - safeTop).coerceAtLeast(point(1f))
        val contentHeight = maxOf(sheetViewportHeight + point(1f), point(790f))
        val screenHeightPx = constraints.maxHeight.toFloat()
        val hiddenOffsetPx = screenHeightPx + EventPassSheetDismiss.Overshoot.toFloat() * pixelScale

        fun closeSheet() {
            if (!sheetOpen) return
            sheetOpen = false
            runtime.setVisible(false)
            sheetAnimation.getAndSet(null)?.cancel()
            sheetAnimation.set(coroutineScope.launch {
                if (quiet) {
                    sheetOffset.floatValue = hiddenOffsetPx
                } else {
                    animate(
                        sheetOffset.floatValue,
                        hiddenOffsetPx,
                        animationSpec = tween(250, easing = StrongOut),
                    ) { value, _ -> sheetOffset.floatValue = value }
                }
            })
        }

        fun reopenSheet() {
            if (sheetOpen) return
            sheetOpen = true
            runtime.setVisible(true)
            sheetAnimation.getAndSet(null)?.cancel()
            sheetAnimation.set(coroutineScope.launch {
                if (quiet) {
                    sheetOffset.floatValue = 0f
                } else {
                    animate(
                        sheetOffset.floatValue,
                        0f,
                        animationSpec = tween(300, easing = StrongOut),
                    ) { value, _ -> sheetOffset.floatValue = value }
                }
            })
        }

        Canvas(Modifier.fillMaxSize()) {
            if (ambientPainter.isDynamic) runtime.ambientRevision.intValue
            ambientPainter.drawPage(
                canvas = drawContext.canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                scale = pixelScale,
                referenceY = SheetReferenceTop - safeTopPx / pixelScale,
                sheetOffset = sheetOffset.floatValue / pixelScale,
                ambient = runtime.ambientFrame.get(),
            )
        }

        EventPage(
            assets = assets,
            width = maxWidth,
            height = maxHeight,
            hidden = sheetOpen,
            onReopen = ::reopenSheet,
        )

        Box(
            Modifier
                .offset(y = safeTop - point(1f))
                .fillMaxWidth()
                .height(sheetViewportHeight + point(1f))
                .graphicsLayer { translationY = sheetOffset.floatValue }
                .then(
                    if (sheetOpen) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                StaticSheet(
                    runtime = runtime,
                    assets = assets,
                    ambientPainter = ambientPainter,
                    layoutScale = layoutScale,
                    pixelScale = pixelScale,
                    contentHeight = contentHeight,
                    sheetOffset = sheetOffset,
                    sheetAnimation = sheetAnimation,
                    animationScope = coroutineScope,
                    screenHeightPx = screenHeightPx,
                    quiet = quiet,
                    busy = busy,
                    error = error,
                    copyDetails = { launchAction(EventPassShareAction.Copy) },
                    exportImage = { launchAction(EventPassShareAction.Export) },
                    shareEvent = { launchAction(EventPassShareAction.Share) },
                    closeSheet = ::closeSheet,
                    commitDragClose = {
                        sheetOpen = false
                        runtime.setVisible(false)
                        eventPassShareLightHaptic(view)
                    },
                    flipHaptic = { eventPassShareLightHaptic(view) },
                )
            }
        }
    }
}

@Composable
private fun EventPage(
    assets: EventPassShareAssets,
    width: Dp,
    height: Dp,
    hidden: Boolean,
    onReopen: () -> Unit,
) {
    val density = LocalDensity.current
    fun fixedSp(value: Float) = (value / density.fontScale).sp
    val posterSize = min(251f, (width.value - 96f).coerceAtLeast(112.95f))
    val posterScale = posterSize / 251f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, height)
            .then(if (hidden) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EventPassSharePoster(assets, posterScale, accessible = !hidden)
            Spacer(Modifier.height(22.dp))
            Text(
                EventPassShareContent.Title,
                color = Color.White,
                fontSize = fixedSp(24f),
                lineHeight = fixedSp(30f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Saturday, July 18 · Los Angeles",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = fixedSp(15f),
                lineHeight = fixedSp(20f),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(minOf(260.dp, width - 64.dp))
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(26.dp))
                    .clickable(enabled = !hidden, role = Role.Button, onClick = onReopen),
            ) {
                Text(
                    "Share Event",
                    color = Color.Black,
                    fontSize = fixedSp(17f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StaticSheet(
    runtime: EventPassShareRuntime,
    assets: EventPassShareAssets,
    ambientPainter: EventPassShareAmbientPainter,
    layoutScale: Float,
    pixelScale: Float,
    contentHeight: Dp,
    sheetOffset: MutableFloatState,
    sheetAnimation: AtomicReference<Job?>,
    animationScope: CoroutineScope,
    screenHeightPx: Float,
    quiet: Boolean,
    busy: EventPassShareAction?,
    error: String?,
    copyDetails: () -> Unit,
    exportImage: () -> Unit,
    shareEvent: () -> Unit,
    closeSheet: () -> Unit,
    commitDragClose: () -> Unit,
    flipHaptic: () -> Unit,
) {
    val sheet = EventPassShareTheme.sheet
    val link = EventPassShareTheme.link
    val stage = EventPassShareTheme.stage
    val pass = EventPassShareTheme.pass
    val shareButton = EventPassShareTheme.shareButton
    val density = LocalDensity.current
    fun dp(value: Float) = (value * layoutScale).dp
    fun sp(value: Float) = (value * layoutScale / density.fontScale).sp
    fun localY(reference: Float) = dp(reference - sheet.top)
    val copied = runtime.copyConfirmed.value
    val spill = remember { FloatArray(3) }
    val sheetShape = RoundedCornerShape(topStart = dp(sheet.radius), topEnd = dp(sheet.radius))

    Box(Modifier.fillMaxWidth().height(contentHeight)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(contentHeight + dp(SheetOverdraw))
                .clip(sheetShape),
        ) {
            if (ambientPainter.isDynamic) {
                runtime.ambientRevision.intValue
                runtime.drawRevision.intValue
            }
            val frame = runtime.frame.get()
            eventPassShareSpillUniform(
                frame.tiltX,
                frame.tiltY,
                if (frame.quietCrossfade) 0.0 else frame.flipAngle,
                spill,
            )
            ambientPainter.drawSheet(
                canvas = drawContext.canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                scale = pixelScale,
                referenceY = sheet.top,
                sheetOffset = sheetOffset.floatValue / pixelScale,
                ambient = runtime.ambientFrame.get(),
                cardSpill = spill,
            )
        }

        Box(
            Modifier
                .offset(dp(sheet.handle.left), localY(sheet.handle.top))
                .size(dp(sheet.handle.width), dp(sheet.handle.height))
                .background(
                    Color.White.copy(alpha = 0.13f),
                    RoundedCornerShape(dp(sheet.handle.height / 2f)),
                ),
        )
        Text(
            "Share Event",
            color = Color.White,
            fontSize = sp(sheet.headerFontSize),
            lineHeight = sp(sheet.close.size),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(dp(111f), localY(sheet.headerTop))
                .size(dp(180f), dp(sheet.close.size)),
        )

        Box(
            Modifier
                .offset(y = localY(sheet.top))
                .fillMaxWidth()
                .height(dp(link.top - sheet.top))
                .sheetDismissGesture(
                    sheetOffset = sheetOffset,
                    sheetAnimation = sheetAnimation,
                    animationScope = animationScope,
                    pixelScale = pixelScale,
                    screenHeightPx = screenHeightPx,
                    quiet = quiet,
                    onClosed = commitDragClose,
                ),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(dp(sheet.close.left), localY(sheet.headerTop))
                .size(dp(sheet.close.size))
                .clickable(role = Role.Button, onClick = closeSheet)
                .semantics { contentDescription = "Close sharing sheet" },
        ) {
            EventPassShareGlassSurface(
                kind = EventPassShareGlassKind.Clear,
                radius = dp(sheet.close.size / 2f),
                layoutScale = layoutScale,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(dp(sheet.close.glyph)),
            )
        }

        CopyPill(
            copied = copied,
            busy = busy == EventPassShareAction.Copy,
            layoutScale = layoutScale,
            enabled = busy == null,
            onCopy = copyDetails,
        )

        EventPassSharePass(
            runtime = runtime,
            assets = assets,
            layoutScale = layoutScale,
            pixelScale = pixelScale,
            onFlipCommit = flipHaptic,
            modifier = Modifier
                .offset(dp(pass.left), localY(pass.top))
                .passGesture(runtime, pixelScale, flipHaptic),
        )

        Text(
            "common✦",
            color = Color.White.copy(alpha = 0.31f),
            fontSize = sp(20f),
            lineHeight = sp(28f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(dp(136f), localY(stage.wordmarkTop))
                .size(dp(130f), dp(28f)),
        )

        StageAction(
            kind = EventPassShareStageIcon.Copy,
            copied = copied,
            busy = busy == EventPassShareAction.Copy,
            label = if (copied) "Event details copied" else "Copy event details",
            layoutScale = layoutScale,
            enabled = busy == null,
            modifier = Modifier.offset(
                dp(stage.copyCenterX - stage.actionHit / 2f),
                localY(stage.actionCenterY - stage.actionHit / 2f),
            ),
            onClick = copyDetails,
        )
        StageAction(
            kind = EventPassShareStageIcon.Download,
            copied = false,
            busy = busy == EventPassShareAction.Export,
            label = "Export event image",
            layoutScale = layoutScale,
            enabled = busy == null,
            modifier = Modifier.offset(
                dp(stage.downloadCenterX - stage.actionHit / 2f),
                localY(stage.actionCenterY - stage.actionHit / 2f),
            ),
            onClick = exportImage,
        )

        if (error != null) {
            Text(
                error,
                color = Color(0xFFFFB4AB),
                fontSize = sp(12f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(dp(30f), localY(746f))
                    .width(dp(342f))
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(dp(shareButton.left), localY(shareButton.top))
                .size(dp(shareButton.width), dp(shareButton.height))
                .clickable(
                    enabled = busy == null,
                    role = Role.Button,
                    onClick = shareEvent,
                ),
        ) {
            EventPassShareGlassSurface(
                kind = EventPassShareGlassKind.Prominent,
                radius = dp(shareButton.height / 2f),
                layoutScale = layoutScale,
            )
            Text(
                if (busy == EventPassShareAction.Share) "Sharing…" else "Share Event",
                color = Color.Black.copy(
                    alpha = if (busy != null && busy != EventPassShareAction.Share) 0.5f else 1f,
                ),
                fontSize = sp(shareButton.fontSize),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CopyPill(
    copied: Boolean,
    busy: Boolean,
    layoutScale: Float,
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    val link = EventPassShareTheme.link
    val density = LocalDensity.current
    fun dp(value: Float) = (value * layoutScale).dp
    fun sp(value: Float) = (value * layoutScale / density.fontScale).sp
    val textMeasurer = rememberTextMeasurer()
    val label = when {
        copied -> "Copied"
        busy -> "Copying…"
        else -> "Event info · Jul 18"
    }
    val baseStyle = TextStyle(fontSize = sp(link.fontSize), fontWeight = FontWeight.Normal)
    val measuredWidth = textMeasurer.measure(label, baseStyle, maxLines = 1).size.width
    val availableWidth = with(density) {
        dp(link.width - link.padding * 2f - link.gap - link.icon).toPx()
    }
    val fittedScale = if (measuredWidth > 0) {
        (availableWidth / measuredWidth).coerceIn(0.78f, 1f)
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(
                dp(201f - link.width / 2f),
                dp(link.top - EventPassShareTheme.sheet.top),
            )
            .size(dp(link.width), dp(link.hitHeight))
            .clickable(enabled = enabled, role = Role.Button, onClick = onCopy)
            .semantics {
                contentDescription = if (copied) "Event details copied" else "Copy event details"
            },
    ) {
        Box(
            Modifier.size(dp(link.width), dp(link.height)),
        ) {
            EventPassShareGlassSurface(
                kind = EventPassShareGlassKind.Clear,
                radius = dp(link.height / 2f),
                layoutScale = layoutScale,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dp(link.gap)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dp(link.padding)),
            ) {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = sp(link.fontSize * fittedScale),
                    lineHeight = sp(20f * fittedScale),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (copied) {
                        Icons.Default.Check
                    } else if (busy) {
                        Icons.Outlined.HourglassEmpty
                    } else {
                        Icons.Outlined.ContentCopy
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.76f),
                    modifier = Modifier.size(dp(link.icon)),
                )
            }
        }
    }
}

private enum class EventPassShareStageIcon {
    Copy,
    Download,
}

@Composable
private fun StageAction(
    kind: EventPassShareStageIcon,
    copied: Boolean,
    busy: Boolean,
    label: String,
    layoutScale: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val stage = EventPassShareTheme.stage
    fun dp(value: Float) = (value * layoutScale).dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(dp(stage.actionHit))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (copied || busy) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = ActionColor,
                modifier = Modifier.size(dp(stage.actionIcon)),
            )
        } else {
            EventPassShareStageGlyph(kind, dp(stage.actionIcon))
        }
    }
}

@Composable
private fun EventPassShareStageGlyph(kind: EventPassShareStageIcon, size: Dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.width / 15f
        val stroke = Stroke(1.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (kind == EventPassShareStageIcon.Copy) {
            val rear = Path().apply {
                moveTo(9.6f * unit, 3.4f * unit)
                lineTo(9.6f * unit, 3.2f * unit)
                cubicTo(9.6f * unit, 2.26f * unit, 8.84f * unit, 1.5f * unit, 7.9f * unit, 1.5f * unit)
                lineTo(3.2f * unit, 1.5f * unit)
                cubicTo(2.26f * unit, 1.5f * unit, 1.5f * unit, 2.26f * unit, 1.5f * unit, 3.2f * unit)
                lineTo(1.5f * unit, 7.9f * unit)
                cubicTo(1.5f * unit, 8.84f * unit, 2.26f * unit, 9.6f * unit, 3.2f * unit, 9.6f * unit)
                lineTo(3.4f * unit, 9.6f * unit)
            }
            val front = Path().apply {
                moveTo(7.6f * unit, 5.4f * unit)
                lineTo(11.4f * unit, 5.4f * unit)
                cubicTo(12.62f * unit, 5.4f * unit, 13.6f * unit, 6.38f * unit, 13.6f * unit, 7.6f * unit)
                lineTo(13.6f * unit, 11.4f * unit)
                cubicTo(13.6f * unit, 12.62f * unit, 12.62f * unit, 13.6f * unit, 11.4f * unit, 13.6f * unit)
                lineTo(7.6f * unit, 13.6f * unit)
                cubicTo(6.38f * unit, 13.6f * unit, 5.4f * unit, 12.62f * unit, 5.4f * unit, 11.4f * unit)
                lineTo(5.4f * unit, 7.6f * unit)
                cubicTo(5.4f * unit, 6.38f * unit, 6.38f * unit, 5.4f * unit, 7.6f * unit, 5.4f * unit)
                close()
            }
            drawPath(rear, ActionColor, style = stroke)
            drawPath(front, ActionColor, style = stroke)
        } else {
            drawLine(
                ActionColor,
                Offset(7.5f * unit, 1.6f * unit),
                Offset(7.5f * unit, 9.2f * unit),
                strokeWidth = 1.4f * unit,
                cap = StrokeCap.Round,
            )
            drawLine(
                ActionColor,
                Offset(4.6f * unit, 6.4f * unit),
                Offset(7.5f * unit, 9.3f * unit),
                strokeWidth = 1.4f * unit,
                cap = StrokeCap.Round,
            )
            drawLine(
                ActionColor,
                Offset(7.5f * unit, 9.3f * unit),
                Offset(10.4f * unit, 6.4f * unit),
                strokeWidth = 1.4f * unit,
                cap = StrokeCap.Round,
            )
            val tray = Path().apply {
                moveTo(1.5f * unit, 9.6f * unit)
                lineTo(1.5f * unit, 10.5f * unit)
                cubicTo(1.5f * unit, 11.66f * unit, 2.44f * unit, 12.6f * unit, 3.6f * unit, 12.6f * unit)
                lineTo(11.4f * unit, 12.6f * unit)
                cubicTo(12.56f * unit, 12.6f * unit, 13.5f * unit, 11.66f * unit, 13.5f * unit, 10.5f * unit)
                lineTo(13.5f * unit, 9.6f * unit)
            }
            drawPath(tray, ActionColor, style = stroke)
        }
    }
}

private fun Modifier.passGesture(
    runtime: EventPassShareRuntime,
    pixelScale: Float,
    onFlipCommit: () -> Unit,
): Modifier = pointerInput(runtime, pixelScale) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!runtime.beginInteraction()) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var translation = Offset.Zero
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                translation = change.position - down.position
                runtime.updateInteraction(
                    (translation.x / pixelScale).toDouble(),
                    (translation.y / pixelScale).toDouble(),
                )
                if (!change.pressed) {
                    val velocity = tracker.calculateVelocity()
                    val committedFlip = runtime.endInteraction(
                        (translation.x / pixelScale).toDouble(),
                        (translation.y / pixelScale).toDouble(),
                        ((translation.x + velocity.x * 0.20f) / pixelScale).toDouble(),
                        ((translation.y + velocity.y * 0.20f) / pixelScale).toDouble(),
                    )
                    if (committedFlip) onFlipCommit()
                    ended = true
                    break
                }
            }
        } finally {
            if (!ended) runtime.cancelInteraction()
        }
    }
}

private fun Modifier.sheetDismissGesture(
    sheetOffset: MutableFloatState,
    sheetAnimation: AtomicReference<Job?>,
    animationScope: CoroutineScope,
    pixelScale: Float,
    screenHeightPx: Float,
    quiet: Boolean,
    onClosed: () -> Unit,
): Modifier = pointerInput(sheetOffset, pixelScale, screenHeightPx, quiet, animationScope, onClosed) {
    awaitEachGesture {
        sheetAnimation.getAndSet(null)?.cancel()
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var translationY = 0f
        var active = false
        var released = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            translationY = change.position.y - down.position.y
            if (!active && abs(translationY) >= EventPassSheetDismiss.ActiveOffset * pixelScale) {
                active = true
            }
            if (active) {
                change.consume()
                val offset = if (translationY >= 0f) {
                    translationY
                } else {
                    -eventPassShareRubberBand((-translationY / pixelScale).toDouble()).toFloat() * pixelScale
                }
                sheetOffset.floatValue = offset
            }
            if (!change.pressed) {
                released = true
                val velocityY = tracker.calculateVelocity().y
                val close = translationY > screenHeightPx * EventPassSheetDismiss.CommitShare ||
                    velocityY > EventPassSheetDismiss.CommitVelocity * pixelScale
                if (close) {
                    val target = screenHeightPx + EventPassSheetDismiss.Overshoot.toFloat() * pixelScale
                    onClosed()
                    if (quiet) {
                        sheetOffset.floatValue = target
                    } else {
                        sheetAnimation.set(animationScope.launch {
                            animate(
                                sheetOffset.floatValue,
                                target,
                                initialVelocity = velocityY,
                                animationSpec = spring(
                                    dampingRatio = EventPassSheetDismiss.CloseDampingRatio.toFloat(),
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            ) { value, _ -> sheetOffset.floatValue = value }
                        })
                    }
                } else if (active) {
                    if (quiet) {
                        sheetOffset.floatValue = 0f
                    } else {
                        sheetAnimation.set(animationScope.launch {
                            animate(
                                sheetOffset.floatValue,
                                0f,
                                initialVelocity = velocityY,
                                animationSpec = spring(
                                    dampingRatio = EventPassSheetDismiss.BackDampingRatio.toFloat(),
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { value, _ -> sheetOffset.floatValue = value }
                        })
                    }
                }
                break
            }
        }
        if (!released && active) sheetOffset.floatValue = 0f
    }
}

private fun eventPassShareLightHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

private fun eventPassShareSuccessHaptic(view: View) {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(feedback)
}
