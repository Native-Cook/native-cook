package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import android.app.Activity
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The 7.8-second invite / find-friends loop with touch-driven manual handoff. */
@Composable
fun FindFriendsInvite(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberLiveReducedMotion()
    val screenReader = rememberLiveScreenReader()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val assets = remember(context) { FindFriendsInviteAssets(context) }
    val overlay = remember { FindFriendsInviteOverlay() }
    val controller = remember(autoPlay) { FindFriendsInviteController(autoPlay) }
    var clocks by remember(controller) { mutableStateOf(controller.snapshot()) }
    var wakeGeneration by remember(controller) { mutableIntStateOf(0) }

    // Haptics are best effort: a device without a vibrator or a failing call must not stop the screen.
    fun performHaptic(haptic: FindFriendsInviteHaptic) {
        runCatching {
            when (haptic) {
                FindFriendsInviteHaptic.LIGHT -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                FindFriendsInviteHaptic.SUCCESS -> {
                    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.CONTEXT_CLICK
                    }
                    view.performHapticFeedback(constant)
                }
            }
        }
    }

    fun wake(haptic: FindFriendsInviteHaptic? = null) {
        clocks = controller.snapshot()
        haptic?.let(::performHaptic)
        wakeGeneration++
    }

    // Reduce Motion and the screen reader are followed live: either stops the loop.
    LaunchedEffect(controller, reducedMotion, screenReader) {
        controller.update(reducedMotion, reducedMotion || screenReader)
        wake()
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = true
        onDispose {
            if (oldStatus != null) {
                bars.isAppearanceLightStatusBars = oldStatus
            }
        }
    }

    LaunchedEffect(controller, wakeGeneration) {
        while (isActive && controller.shouldTick()) {
            // The loop's rest stretch draws nothing: one rest frame, then no frames until it ends.
            val rest = controller.restSeconds()
            if (rest > 0.0) {
                clocks = controller.snapshot()
                delay((rest * 1000.0).toLong())
                controller.skipRest()
                continue
            }
            val haptics = withFrameNanos { now ->
                controller.tick(now)
            }
            clocks = controller.snapshot()
            haptics.forEach(::performHaptic)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val topInsetDp = WindowInsets.statusBars.getTop(density) / density.density
        val geometry = remember(maxWidth, maxHeight, topInsetDp, density) {
            FindFriendsInviteGeometry(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
                topInsetDp = topInsetDp,
                density = density.density,
                scaledDensity = density.fontScale * density.density,
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            drawFindFriendsInvite(clocks, geometry, assets, overlay)
        }

        FindFriendsInvitePressTarget(
            label = FindFriendsInviteTheme.Copy.invite,
            x = geometry.inviteLeft,
            y = geometry.inviteTop,
            width = geometry.inviteWidth,
            height = FindFriendsInviteTheme.Box.invite.height,
            onDown = {
                controller.inviteDown(System.nanoTime())
                wake()
            },
            onUp = {
                controller.inviteUp()
                wake()
            },
            onAccessibilityClick = {
                val now = System.nanoTime()
                controller.inviteDown(now)
                controller.inviteUp()
                wake()
            },
        )

        val cup = FindFriendsInviteTheme.Box.cup
        FindFriendsInviteTapTarget(
            label = FindFriendsInviteTheme.Copy.cupAccessibility,
            x = cup.centerX + geometry.dx - cup.width / 2f,
            y = cup.centerY + geometry.dy - cup.height / 2f,
            width = cup.width,
            height = cup.height,
            onTap = {
                wake(controller.cupTap(System.nanoTime()))
            },
        )

        FindFriendsInvitePressTarget(
            label = FindFriendsInviteTheme.Copy.check,
            x = geometry.checkLeft,
            y = geometry.checkTop,
            width = geometry.checkWidth,
            height = FindFriendsInviteTheme.Box.check.height,
            onDown = {
                controller.scanDown(System.nanoTime())
                wake()
            },
            onUp = {
                controller.scanUp()
                wake()
            },
            onAccessibilityClick = {
                val now = System.nanoTime()
                controller.scanDown(now)
                controller.scanUp()
                wake()
            },
        )
    }
}

@Composable
private fun FindFriendsInvitePressTarget(
    label: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onAccessibilityClick: () -> Unit,
) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(width.dp, height.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    onAccessibilityClick()
                    true
                }
            }
            .pointerInput(onDown, onUp) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDown()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onUp()
                    }
                }
            },
    )
}

@Composable
private fun FindFriendsInviteTapTarget(
    label: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(width.dp, height.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    onTap()
                    true
                }
            }
            .pointerInput(onTap) {
                detectTapGestures(onTap = { onTap() })
            },
    )
}

/** Reduce Motion (animations switched off), followed live. */
@Composable
private fun rememberLiveReducedMotion(): Boolean {
    val context = LocalContext.current
    fun read() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** A screen reader (TalkBack's touch exploration), followed live. */
@Composable
private fun rememberLiveScreenReader(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
