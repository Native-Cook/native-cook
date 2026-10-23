package com.ngocdevv.nativecook.blocks.lists.sendmoneycards

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.hypot

@Composable
fun SendMoneyCards(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val engine = remember(autoPlay) { SendMoneyCardsEngine(autoPlay) }
    val painter = remember(context, localDensity.density) {
        SendMoneyCardsPainter(context, localDensity.density)
    }
    var width by remember { mutableStateOf(SendMoneyCardsSpec.ScreenWidth) }
    var height by remember { mutableStateOf(SendMoneyCardsSpec.ScreenHeight) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var open by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(autoPlay) }
    var reduced by remember {
        mutableStateOf(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f,
        )
    }
    val safeTop = WindowInsets.statusBars.getTop(localDensity) / localDensity.density
    val offsetY = SendMoneyCardsSpec.islandOffset(safeTop)

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    engine.width = width
    engine.height = height
    engine.offsetY = offsetY
    engine.reduced = reduced

    LaunchedEffect(engine, reduced, running) {
        engine.reduced = reduced
        if (!running && !engine.needsFrames) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { nanos ->
                val now = SystemClock.uptimeMillis()
                engine.update(now)
                frameTick = nanos
                val nextOpen = engine.isOpen
                if (nextOpen != open) open = nextOpen
            }
            if (!engine.needsFrames) {
                running = false
                break
            }
        }
    }

    fun haptic(kind: SendMoneyCardsEngine.Haptic) {
        val constant = when (kind) {
            SendMoneyCardsEngine.Haptic.Light -> HapticFeedbackConstants.CLOCK_TICK
            SendMoneyCardsEngine.Haptic.Medium -> HapticFeedbackConstants.CONTEXT_CLICK
            SendMoneyCardsEngine.Haptic.Soft -> HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            SendMoneyCardsEngine.Haptic.None -> return
        }
        view.performHapticFeedback(constant)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                width = size.width / localDensity.density
                height = size.height / localDensity.density
            }
            .pointerInput(engine, width, height, offsetY, reduced) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val now = down.uptimeMillis
                    val point = Offset(
                        down.position.x / localDensity.density,
                        down.position.y / localDensity.density,
                    )
                    running = true
                    engine.interrupt(now)
                    val pose = engine.pose(now)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val start = point
                    var last = down
                    var mode = "passive"
                    var target: String? = null
                    if (engine.isOpen) {
                        val closeX = width - SendMoneyCardsSpec.SheetCloseX
                        val closeY = offsetY + SendMoneyCardsSpec.SheetCloseY
                        target = when {
                            hypot(point.x - closeX, point.y - closeY) <= 30f -> "close"
                            point.y in (offsetY + SendMoneyCardsSpec.SheetButtonTop)..
                                (offsetY + SendMoneyCardsSpec.SheetButtonTop + SendMoneyCardsSpec.SheetButtonHeight) -> "next"
                            point.x in (SendMoneyCardsSpec.SheetAddNewX - 40f)..
                                (SendMoneyCardsSpec.SheetAddNewX + 40f) &&
                                point.y in (offsetY + SendMoneyCardsSpec.SheetAvatarsY - 24f)..
                                (offsetY + SendMoneyCardsSpec.SheetAvatarsY + 42f) -> "add"
                            else -> SendMoneyCardsSpec.Contacts.indices.firstOrNull { index ->
                                val centerY = offsetY + SendMoneyCardsSpec.SheetContactsTop +
                                    index * SendMoneyCardsSpec.SheetContactPitch
                                point.y in (centerY - 22f)..(centerY + 22f)
                            }?.let { "contact-$it" }
                        }
                        mode = "sheet"
                        engine.press(target)
                    } else {
                        val cardTop = offsetY + SendMoneyCardsSpec.CardTop + pose.lift
                        if (point.x in SendMoneyCardsSpec.CardInset..(width - SendMoneyCardsSpec.CardInset) &&
                            point.y in cardTop..(cardTop + SendMoneyCardsSpec.CardHeight)) {
                            mode = "card"
                            haptic(engine.beginCard(now))
                        }
                    }
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        last = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(last.uptimeMillis, last.position)
                        val translationY = (last.position.y - down.position.y) / localDensity.density
                        if (mode == "card") engine.dragCard(last.uptimeMillis, translationY)
                        last.consume()
                    } while (last.pressed)
                    val translationX = (last.position.x - down.position.x) / localDensity.density
                    val translationY = (last.position.y - down.position.y) / localDensity.density
                    val velocityY = tracker.calculateVelocity().y / localDensity.density
                    val end = last.uptimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
                    when (mode) {
                        "card" -> haptic(engine.endCard(end, translationY, velocityY))
                        "sheet" -> {
                            val isTap = hypot(translationX, translationY) < 16f
                            if (target == "close" && isTap) haptic(engine.closeButton(end))
                            else haptic(engine.endSheet(end, translationY, velocityY))
                            engine.press(null)
                        }
                        else -> engine.endPassiveTouch(end)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val drawTick = frameTick
            val now = SystemClock.uptimeMillis()
            val pose = engine.pose(now)
            painter.draw(
                canvas = drawContext.canvas.nativeCanvas,
                width = size.width / localDensity.density,
                height = size.height / localDensity.density,
                offsetY = offsetY,
                pose = pose,
                reduced = reduced,
                pressedTarget = engine.pressedTarget,
            )
        }
        SendMoneyCardsSemantics(
            open = open,
            width = width,
            offsetY = offsetY,
            onSend = {
                running = true
                haptic(engine.accessibilitySend(SystemClock.uptimeMillis()))
                true
            },
            onClose = {
                running = true
                haptic(engine.closeButton(SystemClock.uptimeMillis()))
                true
            },
        )
    }
}

@Composable
private fun SendMoneyCardsSemantics(
    open: Boolean,
    width: Float,
    offsetY: Float,
    onSend: () -> Boolean,
    onClose: () -> Boolean,
) {
    if (!open) {
        Box(
            Modifier
                .offset(SendMoneyCardsSpec.CardInset.dp, (offsetY + SendMoneyCardsSpec.CardTop).dp)
                .size((width - SendMoneyCardsSpec.CardInset * 2f).dp, SendMoneyCardsSpec.CardHeight.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Card ending ${SendMoneyCardsSpec.Last4}, balance ${SendMoneyCardsSpec.Balance.replace(" ", "")}. Drag up to the top to send money"
                    onClick(label = "Send money", action = onSend)
                },
        )
    } else {
        Box(
            Modifier
                .offset(
                    (width - SendMoneyCardsSpec.SheetCloseX - 22f).dp,
                    (offsetY + SendMoneyCardsSpec.SheetCloseY - 22f).dp,
                )
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Close"
                    onClick(action = onClose)
                },
        )
        SendMoneyCardsSpec.Favourites.forEachIndexed { index, person ->
            Box(
                Modifier
                    .offset(
                        (SendMoneyCardsSpec.SheetAvatarX - 40f +
                            index * SendMoneyCardsSpec.SheetAvatarStep).dp,
                        (offsetY + SendMoneyCardsSpec.SheetAvatarsY -
                            SendMoneyCardsSpec.SheetAvatar / 2f).dp,
                    )
                    .size(80.dp, 60.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Send to ${person.name}"
                    },
            )
        }
        Box(
            Modifier
                .offset(
                    (SendMoneyCardsSpec.SheetAddNewX - 40f).dp,
                    (offsetY + SendMoneyCardsSpec.SheetAvatarsY -
                        SendMoneyCardsSpec.SheetAvatar / 2f).dp,
                )
                .size(80.dp, 60.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Add new recipient"
                },
        )
        SendMoneyCardsSpec.Contacts.forEachIndexed { index, person ->
            Box(
                Modifier
                    .offset(
                        (SendMoneyCardsSpec.SheetContactX - SendMoneyCardsSpec.SheetAvatar / 2f).dp,
                        (offsetY + SendMoneyCardsSpec.SheetContactsTop +
                            index * SendMoneyCardsSpec.SheetContactPitch - 22f).dp,
                    )
                    .size(230.dp, 44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Send to ${person.name}"
                    },
            )
        }
        Box(
            Modifier
                .offset(SendMoneyCardsSpec.SheetButtonX.dp,
                    (offsetY + SendMoneyCardsSpec.SheetButtonTop).dp)
                .size(SendMoneyCardsSpec.SheetButtonWidth.dp, SendMoneyCardsSpec.SheetButtonHeight.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Next"
                },
        )
    }
}
