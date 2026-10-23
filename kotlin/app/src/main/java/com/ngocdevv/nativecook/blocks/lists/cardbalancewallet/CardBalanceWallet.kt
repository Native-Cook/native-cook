package com.ngocdevv.nativecook.blocks.lists.cardbalancewallet

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CardBalanceWalletAction {
    Notifications, AddMoney, Transfer, ScanToPay, AddCard, Overview, Insights, Messages,
}

private fun String.toCardBalanceWalletAction(): CardBalanceWalletAction = when (this) {
    "bell" -> CardBalanceWalletAction.Notifications
    "addMoney" -> CardBalanceWalletAction.AddMoney
    "transfer" -> CardBalanceWalletAction.Transfer
    "scan" -> CardBalanceWalletAction.ScanToPay
    "addCard" -> CardBalanceWalletAction.AddCard
    "overview" -> CardBalanceWalletAction.Overview
    "insights" -> CardBalanceWalletAction.Insights
    else -> CardBalanceWalletAction.Messages
}

private val CardBalanceWalletPoppins = FontFamily(
    Font(R.font.cardbalancewallet_poppins_regular, FontWeight.Normal),
    Font(R.font.cardbalancewallet_poppins_medium, FontWeight.Medium),
    Font(R.font.cardbalancewallet_poppins_semibold, FontWeight.SemiBold),
)

private val CardBalanceWalletNoPadding = PlatformTextStyle(includeFontPadding = false)

private object CardBalanceWalletSpec {
    const val SafeTop = 62f
    const val Inset = 15.8f
    const val HeaderY = 109.2f
    const val Avatar = 47f
    const val NameX = 74.5f
    const val Bell = 24f
    const val CardTop = 166.3f
    const val CardHeight = 239.7f
    const val CardRadius = 24f
    const val CardPadX = 17f
    const val CardLabelY = 25.8f
    const val BadgeRight = 15.9f
    const val BadgeTop = 45.7f
    const val BadgeWidth = 33.8f
    const val BadgeHeight = 23.8f
    const val BadgeRadius = 5f
    const val ActionsTop = 419f
    const val ActionsHeight = 82.4f
    const val ActionRadius = 24f
    const val ActionGap = 12.9f
    const val ActionPadX = 17f
    const val DotsX = 183.7f
    const val DotsY = 528f
    const val ActiveDot = 22f
    const val Dot = 3.4f
    const val DotGap = 2.6f
    const val DotHeight = 3.6f
    const val TilesTop = 563.3f
    const val TileSize = 88.4f
    const val TileGap = 5f
    const val TileRadius = 20f
    const val SpentTop = 685.3f
    const val SpentRadius = 24f
    const val SpentTitleY = 28.8f
    const val BarY = 82.5f
    const val BarHeight = 16.3f
    const val BarGap = 3f
    const val TabBand = 780f
    const val TabTrackTop = 785.2f
    const val TabTrackHeight = 62.6f
    const val TabTrackX = 32.8f
    const val TabPillWidth = 117f
    const val TabPillHeight = 57f
    const val TabCircle = 57.3f
    val TabCircleXs = listOf(195.6f, 266f, 335.7f)

    val Screen = Color(0xFF030303)
    val White = Color.White
    val Ink = Color(0xFF0B0B0C)
    val Tile = Color(0xFF1F1F1F)
    val Muted = Color(0xFF8B8B8F)
    val Accent = Color(0xFF6E5BFF)
    val DotColor = Color(0xFF1F1F21)
    val TabBandColor = Color(0xFF1E1D1D)
    val TabTrack = Color(0xFF2B2B2D)
    val TabCircleColor = Color(0xFF8E8E93)
    val TabIcon = Color(0xFF2A2A2C)
}

@Composable
fun CardBalanceWallet(
    autoPlay: Boolean = false,
    onAction: ((CardBalanceWalletAction) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val canAutoPlay by rememberUpdatedState(autoPlay && !reduced)
    val motion = remember { CardBalanceWalletMotion.AnimatedValues() }
    val scope = rememberCoroutineScope()
    var scriptGeneration by remember { mutableIntStateOf(0) }
    var interrupted by remember { mutableStateOf(false) }
    var pressedTarget by remember { mutableStateOf<String?>(null) }
    var pickupJob by remember { mutableStateOf<Job?>(null) }
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldLightStatus = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            pickupJob?.cancel()
            releaseJob?.cancel()
            resumeJob?.cancel()
            if (oldLightStatus != null) controller?.isAppearanceLightStatusBars = oldLightStatus
        }
    }

    LaunchedEffect(autoPlay, reduced) {
        pickupJob?.cancel()
        releaseJob?.cancel()
        resumeJob?.cancel()
        interrupted = false
        motion.reset()
    }

    LaunchedEffect(autoPlay, reduced, scriptGeneration) {
        if (!autoPlay || reduced || interrupted) return@LaunchedEffect
        motion.reset()
        while (isActive) {
            val restIn = if (reduced) 1_000L else 600L
            val active = if (reduced) 2_600L else 5_600L
            val restOut = if (reduced) 3_400L else 800L
            delay(restIn)
            val start = withFrameNanos { it } - restIn * 1_000_000L
            var elapsed = restIn.toFloat()
            while (isActive && elapsed < restIn + active) {
                withFrameNanos { frame -> elapsed = (frame - start) / 1_000_000f }
                motion.set(CardBalanceWalletMotion.pose(elapsed, reduced))
            }
            motion.reset()
            delay(restOut)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CardBalanceWalletSpec.Screen),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val safeTop = WindowInsets.statusBars.getTop(density) / density.density
        val offsetY = if (safeTop > 0f) safeTop - CardBalanceWalletSpec.SafeTop else -40f
        val cardWidth = width - CardBalanceWalletSpec.Inset * 2f

        fun stopScript() {
            interrupted = true
            scriptGeneration += 1
            resumeJob?.cancel()
            pickupJob?.cancel()
            releaseJob?.cancel()
        }

        fun scheduleResume() {
            if (!canAutoPlay) return
            resumeJob?.cancel()
            resumeJob = scope.launch {
                delay(CardBalanceWalletMotion.ResumeAfterMs)
                if (!canAutoPlay) return@launch
                releaseJob?.cancel()
                motion.reset()
                interrupted = false
                scriptGeneration += 1
            }
        }

        CardBalanceWalletSurface(
            onAction = onAction,
            width = width,
            height = height,
            offsetY = offsetY,
            motion = motion,
            reduced = reduced,
            pressedTarget = pressedTarget,
            modifier = Modifier.pointerInput(autoPlay, reduced, width, offsetY, onAction) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val down = downEvent.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                        stopScript()
                        pressedTarget = if (onAction == null) null else cardBalanceWalletHitTarget(
                            x = down.position.x / density.density,
                            y = down.position.y / density.density,
                            width = width,
                            offsetY = offsetY,
                        )
                        val activePressTarget = pressedTarget
                        val localX = down.position.x / density.density
                        val localY = down.position.y / density.density
                        val onCard = localX in CardBalanceWalletSpec.Inset..(CardBalanceWalletSpec.Inset + cardWidth)
                            && localY in (offsetY + CardBalanceWalletSpec.CardTop)..(offsetY + CardBalanceWalletSpec.CardTop + CardBalanceWalletSpec.CardHeight)
                        val initial = motion.pose()
                        var targetRX = initial.rx
                        var targetRY = initial.ry
                        var moved = false
                        if (onCard && !reduced) {
                            val normalizedX = ((localX - CardBalanceWalletSpec.Inset) / cardWidth).coerceIn(0f, 1f) - 0.5f
                            val normalizedY = ((localY - offsetY - CardBalanceWalletSpec.CardTop) / CardBalanceWalletSpec.CardHeight).coerceIn(0f, 1f) - 0.5f
                            val tilt = CardBalanceWalletMotion.tilt(normalizedX, normalizedY)
                            targetRX = tilt.first
                            targetRY = tilt.second
                            pickupJob = scope.launch {
                                val started = withFrameNanos { it }
                                while (isActive) {
                                    var elapsed = 0f
                                    withFrameNanos { elapsed = (it - started) / 1_000_000f }
                                    if (!moved) {
                                        val follow = CardBalanceWalletMotion.easeOut(
                                            (elapsed / CardBalanceWalletMotion.FollowInMs).coerceIn(0f, 1f),
                                        )
                                        motion.rx.floatValue = initial.rx + (targetRX - initial.rx) * follow
                                        motion.ry.floatValue = initial.ry + (targetRY - initial.ry) * follow
                                    }
                                    motion.lift.floatValue = CardBalanceWalletMotion.pickupLift(elapsed, initial.lift)
                                    motion.glow.floatValue = 0f
                                }
                            }
                        } else if (reduced) {
                            motion.reset()
                        }

                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var change = down
                        var endedByUp = false
                        while (change.pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) endedByUp = true
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (activePressTarget != null && pressedTarget != null) {
                                val px = change.position.x / density.density
                                val py = change.position.y / density.density
                                if (!cardBalanceWalletTargetContains(activePressTarget, px, py, width, offsetY, 16f)) {
                                    pressedTarget = null
                                }
                            }
                            if (onCard && !reduced && change.pressed) {
                                moved = true
                                val x = ((change.position.x / density.density - CardBalanceWalletSpec.Inset) / cardWidth).coerceIn(0f, 1f) - 0.5f
                                val y = ((change.position.y / density.density - offsetY - CardBalanceWalletSpec.CardTop) / CardBalanceWalletSpec.CardHeight).coerceIn(0f, 1f) - 0.5f
                                val tilt = CardBalanceWalletMotion.tilt(x, y)
                                motion.rx.floatValue = tilt.first
                                motion.ry.floatValue = tilt.second
                            }
                        }

                        pickupJob?.cancel()
                        pickupJob = null
                        if (endedByUp && activePressTarget != null && pressedTarget != null && onAction != null) {
                            onAction(activePressTarget.toCardBalanceWalletAction())
                        }
                        pressedTarget = null
                        if (onCard && !reduced) {
                            val velocity = tracker.calculateVelocity()
                            val released = motion.pose()
                            releaseJob = scope.launch {
                                val started = withFrameNanos { it }
                                var elapsed = 0f
                                while (isActive && elapsed < 1_500f) {
                                    withFrameNanos { elapsed = (it - started) / 1_000_000f }
                                    motion.set(
                                        CardBalanceWalletMotion.releasedPose(
                                            milliseconds = elapsed,
                                            initial = released,
                                            velocityX = -velocity.y / density.density / CardBalanceWalletSpec.CardHeight * 2f * CardBalanceWalletMotion.TiltMax,
                                            velocityY = velocity.x / density.density / cardWidth * 2f * CardBalanceWalletMotion.TiltMax,
                                        ),
                                    )
                                }
                                motion.reset()
                            }
                        }
                        scheduleResume()
                    }
                }
            },
        )
    }
}

@Composable
private fun CardBalanceWalletSurface(
    onAction: ((CardBalanceWalletAction) -> Unit)?,
    width: Float,
    height: Float,
    offsetY: Float,
    motion: CardBalanceWalletMotion.AnimatedValues,
    reduced: Boolean,
    pressedTarget: String?,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(CardBalanceWalletSpec.Screen)) {
        CardBalanceWalletHeader(width, offsetY, pressedTarget, onAction)
        CardBalanceWalletCard(width, offsetY, motion, reduced)
        CardBalanceWalletActions(width, offsetY, pressedTarget, onAction)
        CardBalanceWalletCardShadow(width, offsetY, motion)
        CardBalanceWalletDots(offsetY, motion)
        CardBalanceWalletTiles(offsetY, pressedTarget, onAction)
        CardBalanceWalletSpent(width, offsetY)
        CardBalanceWalletTabBar(width, height, offsetY, pressedTarget, onAction)
    }
}

@Composable
private fun BoxScope.CardBalanceWalletHeader(
    width: Float, offsetY: Float, pressedTarget: String?, onAction: ((CardBalanceWalletAction) -> Unit)?,
) {
    Image(
        painter = painterResource(R.drawable.cardbalancewallet_carmen),
        contentDescription = "Carmen",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.HeaderY - CardBalanceWalletSpec.Avatar / 2f).dp)
            .size(CardBalanceWalletSpec.Avatar.dp)
            .clip(CircleShape)
            .background(Color(0xFFDEDEDE)),
    )
    androidx.compose.material3.Text(
        text = "Carmen",
        color = CardBalanceWalletSpec.White,
        fontFamily = CardBalanceWalletPoppins,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 20.sp,
        style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
        modifier = Modifier.offset(CardBalanceWalletSpec.NameX.dp, (offsetY + CardBalanceWalletSpec.HeaderY - 10f).dp),
    )
    Box(
        modifier = Modifier
            .offset((width - 28.8f - 22f).dp, (offsetY + CardBalanceWalletSpec.HeaderY - 22f).dp)
            .size(44.dp)
            .graphicsLayer {
                val scale = if (pressedTarget == "bell") CardBalanceWalletMotion.PressScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                role = Role.Button
                contentDescription = "Notifications, 1 new"
                onClick {
                    val callback = onAction ?: return@onClick false
                    callback(CardBalanceWalletAction.Notifications)
                    true
                }
                if (onAction == null) disabled()
            },
    ) {
        CardBalanceWalletGlyph(
            CardBalanceWalletGlyphKind.Bell,
            CardBalanceWalletSpec.White,
            CardBalanceWalletSpec.Bell + 2f,
            Modifier.align(Alignment.Center),
        )
        Box(
            Modifier
                .offset(27.dp, 7.dp)
                .size(8.dp)
                .background(CardBalanceWalletSpec.Accent, CircleShape),
        )
    }
}

@Composable
private fun BoxScope.CardBalanceWalletCard(
    width: Float,
    offsetY: Float,
    motion: CardBalanceWalletMotion.AnimatedValues,
    reduced: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardWidth = width - CardBalanceWalletSpec.Inset * 2f
    val bitmap = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.cardbalancewallet_card_field) }
    val shader = remember(bitmap, density.density) { CardBalanceWalletCardShader(bitmap, density.density) }
    val balanceTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.cardbalancewallet_interdisplay_bold)
    }
    val balancePaint = remember(balanceTypeface, density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 63.5f * density.density
            typeface = balanceTypeface
        }
    }
    Box(
        modifier = Modifier
            .offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.CardTop).dp)
            .size(cardWidth.dp, CardBalanceWalletSpec.CardHeight.dp)
            .graphicsLayer {
                val lift = motion.lift.floatValue
                rotationX = if (reduced) 0f else motion.rx.floatValue
                rotationY = if (reduced) 0f else motion.ry.floatValue
                cameraDistance = 900f * density.density
                val scale = if (reduced) 1f else 1f + CardBalanceWalletMotion.LiftScale * lift
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(CardBalanceWalletSpec.CardRadius.dp)
                clip = true
            }
            .semantics {
                contentDescription = "Card ending 6745, balance \$12,567"
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ry = if (reduced) 0f else motion.ry.floatValue
            val lift = motion.lift.floatValue
            val glow = motion.glow.floatValue
            val shift = if (reduced) 0f else ry / CardBalanceWalletMotion.TiltMax * CardBalanceWalletMotion.Refraction
            val spec = if (reduced) glow else lift.coerceIn(0f, 1f) * CardBalanceWalletMotion.Specular + glow
            val light = if (reduced) 0.5f else 0.5f - ry / CardBalanceWalletMotion.TiltMax * CardBalanceWalletMotion.SpecularTravel
            drawIntoCanvas { shader.draw(it.nativeCanvas, size.width, size.height, shift, spec, light) }
        }
        androidx.compose.material3.Text(
            text = "card balance",
            color = Color.White,
            fontFamily = CardBalanceWalletPoppins,
            fontWeight = FontWeight.Normal,
            fontSize = 15.6.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
            modifier = Modifier.offset(CardBalanceWalletSpec.CardPadX.dp, (CardBalanceWalletSpec.CardLabelY - 12f).dp),
        )
        androidx.compose.material3.Text(
            text = "*6745",
            color = Color.White,
            fontFamily = CardBalanceWalletPoppins,
            fontWeight = FontWeight.Normal,
            fontSize = 15.6.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = CardBalanceWalletSpec.CardPadX.dp)
                .offset(y = (CardBalanceWalletSpec.CardLabelY - 12f).dp),
        )
        Box(
            modifier = Modifier
                .offset(
                    (cardWidth - CardBalanceWalletSpec.BadgeRight - CardBalanceWalletSpec.BadgeWidth).dp,
                    CardBalanceWalletSpec.BadgeTop.dp,
                )
                .size(CardBalanceWalletSpec.BadgeWidth.dp, CardBalanceWalletSpec.BadgeHeight.dp)
                .background(Color.White, RoundedCornerShape(CardBalanceWalletSpec.BadgeRadius.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CardBalanceWalletGlyph(CardBalanceWalletGlyphKind.Network, Color.Transparent, 26f)
        }
        Canvas(Modifier.fillMaxSize()) {
            val inkLeft = listOf(18.5f, 57.2f, 88.9f, 130.5f, 150.5f, 191.5f, 233.9f)
            val bearing = listOf(2.16f, 1.27f, 2.29f, 1.52f, 1.78f, 1.91f, 1.4f)
            "\$12,567".forEachIndexed { index, character ->
                drawContext.canvas.nativeCanvas.drawText(
                    character.toString(),
                    (inkLeft[index] - bearing[index]) * density.density,
                    207.4f * density.density,
                    balancePaint,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CardBalanceWalletActions(
    width: Float, offsetY: Float, pressedTarget: String?, onAction: ((CardBalanceWalletAction) -> Unit)?,
) {
    val total = width - CardBalanceWalletSpec.Inset * 2f
    val itemWidth = (total - CardBalanceWalletSpec.ActionGap) / 2f
    CardBalanceWalletAction(
        onAction = onAction,
        title = "add money",
        label = "Add money",
        glyph = CardBalanceWalletGlyphKind.Plus,
        target = "addMoney",
        pressedTarget = pressedTarget,
        width = itemWidth,
        modifier = Modifier.offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.ActionsTop).dp),
    )
    CardBalanceWalletAction(
        onAction = onAction,
        title = "transfer",
        label = "Transfer",
        glyph = CardBalanceWalletGlyphKind.Arrow,
        target = "transfer",
        pressedTarget = pressedTarget,
        width = itemWidth,
        modifier = Modifier.offset(
            (CardBalanceWalletSpec.Inset + itemWidth + CardBalanceWalletSpec.ActionGap).dp,
            (offsetY + CardBalanceWalletSpec.ActionsTop).dp,
        ),
    )
}

@Composable
private fun CardBalanceWalletAction(
    onAction: ((CardBalanceWalletAction) -> Unit)?,
    title: String,
    label: String,
    glyph: CardBalanceWalletGlyphKind,
    target: String,
    pressedTarget: String?,
    width: Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(width.dp, CardBalanceWalletSpec.ActionsHeight.dp)
            .graphicsLayer {
                val scale = if (pressedTarget == target) CardBalanceWalletMotion.PressScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .background(Color.White, RoundedCornerShape(CardBalanceWalletSpec.ActionRadius.dp))
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    val callback = onAction ?: return@onClick false
                    callback(target.toCardBalanceWalletAction())
                    true
                }
                if (onAction == null) disabled()
            },
    ) {
        androidx.compose.material3.Text(
            text = title,
            color = CardBalanceWalletSpec.Ink,
            fontFamily = CardBalanceWalletPoppins,
            fontWeight = FontWeight.Medium,
            fontSize = 16.4.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp,
            style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = CardBalanceWalletSpec.ActionPadX.dp)
                .offset(y = (-3).dp),
        )
        CardBalanceWalletGlyph(
            glyph,
            CardBalanceWalletSpec.Ink,
            24f,
            Modifier.offset(
                (width - CardBalanceWalletSpec.ActionPadX - 24f).dp,
                ((CardBalanceWalletSpec.ActionsHeight - 24f) / 2f).dp,
            ),
        )
    }
}

@Composable
private fun BoxScope.CardBalanceWalletCardShadow(
    width: Float,
    offsetY: Float,
    motion: CardBalanceWalletMotion.AnimatedValues,
) {
    Box(
        modifier = Modifier
            .offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.ActionsTop).dp)
            .size((width - CardBalanceWalletSpec.Inset * 2f).dp, 34.dp)
            .graphicsLayer {
                val progress = motion.lift.floatValue.coerceIn(0f, 1f)
                alpha = CardBalanceWalletMotion.Shadow * progress
                translationY = CardBalanceWalletMotion.ShadowDrop * progress * density
            }
            .clip(RoundedCornerShape(
                topStart = CardBalanceWalletSpec.ActionRadius.dp,
                topEnd = CardBalanceWalletSpec.ActionRadius.dp,
            ))
            .background(Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))),
    )
}

@Composable
private fun BoxScope.CardBalanceWalletDots(
    offsetY: Float,
    motion: CardBalanceWalletMotion.AnimatedValues,
) {
    Canvas(
        modifier = Modifier
            .offset(CardBalanceWalletSpec.DotsX.dp, (offsetY + CardBalanceWalletSpec.DotsY - CardBalanceWalletSpec.DotHeight / 2f).dp)
            .size(60.dp, CardBalanceWalletSpec.DotHeight.dp)
            .semantics { contentDescription = "Card 1 of 3" },
    ) {
        val ry = motion.ry.floatValue
        val extra = CardBalanceWalletMotion.DotStretch * min(1f, abs(ry) / CardBalanceWalletMotion.TiltMax)
        val scale = this.density
        val activeX = if (ry < 0f) -extra else 0f
        drawRoundRect(
            Color.White,
            Offset(activeX * scale, 0f),
            Size((CardBalanceWalletSpec.ActiveDot + extra) * scale, size.height),
            androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            CardBalanceWalletSpec.DotColor,
            Offset((CardBalanceWalletSpec.ActiveDot + CardBalanceWalletSpec.DotGap) * scale, 0f),
            Size(CardBalanceWalletSpec.Dot * scale, size.height),
            androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            CardBalanceWalletSpec.DotColor,
            Offset((CardBalanceWalletSpec.ActiveDot + CardBalanceWalletSpec.Dot + CardBalanceWalletSpec.DotGap * 2f) * scale, 0f),
            Size(CardBalanceWalletSpec.Dot * scale, size.height),
            androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}

@Composable
private fun BoxScope.CardBalanceWalletTiles(
    offsetY: Float, pressedTarget: String?, onAction: ((CardBalanceWalletAction) -> Unit)?,
) {
    CardBalanceWalletTile(
        onAction = onAction,
        glyph = CardBalanceWalletGlyphKind.Scan,
        glyphSize = 28f,
        label = "Scan to pay",
        target = "scan",
        pressedTarget = pressedTarget,
        modifier = Modifier.offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.TilesTop).dp),
    )
    CardBalanceWalletTile(
        onAction = onAction,
        glyph = CardBalanceWalletGlyphKind.Plus,
        glyphSize = 24f,
        label = "Add card",
        target = "addCard",
        pressedTarget = pressedTarget,
        modifier = Modifier.offset(
            (CardBalanceWalletSpec.Inset + CardBalanceWalletSpec.TileSize + CardBalanceWalletSpec.TileGap).dp,
            (offsetY + CardBalanceWalletSpec.TilesTop).dp,
        ),
    )
}

@Composable
private fun CardBalanceWalletTile(
    onAction: ((CardBalanceWalletAction) -> Unit)?,
    glyph: CardBalanceWalletGlyphKind,
    glyphSize: Float,
    label: String,
    target: String,
    pressedTarget: String?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(CardBalanceWalletSpec.TileSize.dp)
            .graphicsLayer {
                val scale = if (pressedTarget == target) CardBalanceWalletMotion.PressScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .background(CardBalanceWalletSpec.Tile, RoundedCornerShape(CardBalanceWalletSpec.TileRadius.dp))
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    val callback = onAction ?: return@onClick false
                    callback(target.toCardBalanceWalletAction())
                    true
                }
                if (onAction == null) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        CardBalanceWalletGlyph(glyph, Color.White, glyphSize)
    }
}

@Composable
private fun BoxScope.CardBalanceWalletSpent(width: Float, offsetY: Float) {
    val spentWidth = width - CardBalanceWalletSpec.Inset * 2f
    val barWidth = spentWidth - CardBalanceWalletSpec.CardPadX * 2f
    val room = barWidth - CardBalanceWalletSpec.BarGap * 3f
    val split = listOf(0.3f, 0.15f, 0.22f, 0.33f)
    val colors = listOf(Color(0xFFEAB4BB), Color(0xFFB4E2EC), Color(0xFFC9B6EF), Color(0xFFE8EAB3))
    Box(
        modifier = Modifier
            .offset(CardBalanceWalletSpec.Inset.dp, (offsetY + CardBalanceWalletSpec.SpentTop).dp)
            .size(spentWidth.dp, 140.dp)
            .background(CardBalanceWalletSpec.Tile, RoundedCornerShape(CardBalanceWalletSpec.SpentRadius.dp))
            .semantics { contentDescription = "Spent in May, \$1,789" },
    ) {
        val title = buildAnnotatedString {
            withStyle(SpanStyle(color = CardBalanceWalletSpec.Muted)) { append("Spent in ") }
            withStyle(SpanStyle(color = Color.White)) { append("May") }
        }
        androidx.compose.material3.Text(
            text = title,
            fontFamily = CardBalanceWalletPoppins,
            fontWeight = FontWeight.Medium,
            fontSize = 16.8.sp,
            lineHeight = 24.sp,
            style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
            modifier = Modifier.offset(CardBalanceWalletSpec.CardPadX.dp, (CardBalanceWalletSpec.SpentTitleY - 12f).dp),
        )
        androidx.compose.material3.Text(
            text = "\$1,789",
            color = Color.White,
            fontFamily = CardBalanceWalletPoppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp,
            style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = CardBalanceWalletSpec.CardPadX.dp)
                .offset(y = (CardBalanceWalletSpec.SpentTitleY - 15f).dp),
        )
        var x = CardBalanceWalletSpec.CardPadX
        split.forEachIndexed { index, share ->
            Box(
                Modifier
                    .offset(x.dp, (CardBalanceWalletSpec.BarY - CardBalanceWalletSpec.BarHeight / 2f).dp)
                    .size((share * room).dp, CardBalanceWalletSpec.BarHeight.dp)
                    .background(colors[index], RoundedCornerShape(CardBalanceWalletSpec.BarHeight.dp / 2f)),
            )
            x += share * room + CardBalanceWalletSpec.BarGap
        }
    }
}

@Composable
private fun BoxScope.CardBalanceWalletTabBar(
    width: Float,
    height: Float,
    offsetY: Float,
    pressedTarget: String?,
    onAction: ((CardBalanceWalletAction) -> Unit)?,
) {
    val bandTop = offsetY + CardBalanceWalletSpec.TabBand
    Box(
        modifier = Modifier
            .offset(y = bandTop.dp)
            .size(width.dp, max(0f, height - bandTop).dp)
            .background(
                Brush.verticalGradient(
                    0f to CardBalanceWalletSpec.TabBandColor.copy(alpha = 0f),
                    0.12f to CardBalanceWalletSpec.TabBandColor.copy(alpha = 0.92f),
                    1f to CardBalanceWalletSpec.TabBandColor.copy(alpha = 0.92f),
                ),
            )
            .semantics { contentDescription = "Tab bar" },
    ) {
        Box(
            modifier = Modifier
                .offset(
                    CardBalanceWalletSpec.TabTrackX.dp,
                    (CardBalanceWalletSpec.TabTrackTop - CardBalanceWalletSpec.TabBand).dp,
                )
                .size((width - CardBalanceWalletSpec.TabTrackX * 2f).dp, CardBalanceWalletSpec.TabTrackHeight.dp)
                .background(CardBalanceWalletSpec.TabTrack, RoundedCornerShape(CardBalanceWalletSpec.TabTrackHeight.dp / 2f)),
        ) {
            Row(
                modifier = Modifier
                    .offset(5.dp, ((CardBalanceWalletSpec.TabTrackHeight - CardBalanceWalletSpec.TabPillHeight) / 2f).dp)
                    .size(CardBalanceWalletSpec.TabPillWidth.dp, CardBalanceWalletSpec.TabPillHeight.dp)
                    .background(Color.White, RoundedCornerShape(CardBalanceWalletSpec.TabPillHeight.dp / 2f))
                    .semantics {
                        role = Role.Tab
                        selected = true
                        contentDescription = "All cards"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CardBalanceWalletGlyph(CardBalanceWalletGlyphKind.Wallet, CardBalanceWalletSpec.Accent, 26f)
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Text(
                    text = "All cards",
                    color = CardBalanceWalletSpec.Accent,
                    fontFamily = CardBalanceWalletPoppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    style = androidx.compose.ui.text.TextStyle(platformStyle = CardBalanceWalletNoPadding),
                )
            }
            listOf(
                Triple(CardBalanceWalletGlyphKind.Grid, "Overview", "overview"),
                Triple(CardBalanceWalletGlyphKind.Chart, "Insights", "insights"),
                Triple(CardBalanceWalletGlyphKind.Chat, "Messages", "messages"),
            ).forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .offset(
                            (CardBalanceWalletSpec.TabCircleXs[index] - CardBalanceWalletSpec.TabTrackX - CardBalanceWalletSpec.TabCircle / 2f).dp,
                            ((CardBalanceWalletSpec.TabTrackHeight - CardBalanceWalletSpec.TabCircle) / 2f).dp,
                        )
                        .size(CardBalanceWalletSpec.TabCircle.dp)
                        .graphicsLayer {
                            val scale = if (pressedTarget == item.third) CardBalanceWalletMotion.PressScale else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(CardBalanceWalletSpec.TabCircleColor, CircleShape)
                        .semantics {
                            role = Role.Tab
                            selected = false
                            contentDescription = item.second
                            onClick {
                                val callback = onAction ?: return@onClick false
                                callback(item.third.toCardBalanceWalletAction())
                                true
                            }
                            if (onAction == null) disabled()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CardBalanceWalletGlyph(item.first, CardBalanceWalletSpec.TabIcon, 26f)
                }
            }
        }
    }
}

private fun cardBalanceWalletHitTarget(
    x: Float,
    y: Float,
    width: Float,
    offsetY: Float,
): String? {
    return listOf("bell", "addMoney", "transfer", "scan", "addCard", "overview", "insights", "messages")
        .firstOrNull { cardBalanceWalletTargetContains(it, x, y, width, offsetY, 4f) }
}

private fun cardBalanceWalletTargetContains(
    target: String,
    x: Float,
    y: Float,
    width: Float,
    offsetY: Float,
    extra: Float,
): Boolean {
    val actionWidth = (width - CardBalanceWalletSpec.Inset * 2f - CardBalanceWalletSpec.ActionGap) / 2f
    val frame = when (target) {
        "bell" -> floatArrayOf(width - 28.8f - 22f, offsetY + CardBalanceWalletSpec.HeaderY - 22f, 44f, 44f)
        "addMoney" -> floatArrayOf(CardBalanceWalletSpec.Inset, offsetY + CardBalanceWalletSpec.ActionsTop, actionWidth, CardBalanceWalletSpec.ActionsHeight)
        "transfer" -> floatArrayOf(CardBalanceWalletSpec.Inset + actionWidth + CardBalanceWalletSpec.ActionGap, offsetY + CardBalanceWalletSpec.ActionsTop, actionWidth, CardBalanceWalletSpec.ActionsHeight)
        "scan" -> floatArrayOf(CardBalanceWalletSpec.Inset, offsetY + CardBalanceWalletSpec.TilesTop, CardBalanceWalletSpec.TileSize, CardBalanceWalletSpec.TileSize)
        "addCard" -> floatArrayOf(CardBalanceWalletSpec.Inset + CardBalanceWalletSpec.TileSize + CardBalanceWalletSpec.TileGap, offsetY + CardBalanceWalletSpec.TilesTop, CardBalanceWalletSpec.TileSize, CardBalanceWalletSpec.TileSize)
        "overview", "insights", "messages" -> {
            val index = listOf("overview", "insights", "messages").indexOf(target)
            val center = CardBalanceWalletSpec.TabCircleXs[index]
            val top = offsetY + CardBalanceWalletSpec.TabTrackTop + (CardBalanceWalletSpec.TabTrackHeight - CardBalanceWalletSpec.TabCircle) / 2f
            floatArrayOf(center - CardBalanceWalletSpec.TabCircle / 2f, top, CardBalanceWalletSpec.TabCircle, CardBalanceWalletSpec.TabCircle)
        }
        else -> return false
    }
    return x in (frame[0] - extra)..(frame[0] + frame[2] + extra)
        && y in (frame[1] - extra)..(frame[1] + frame[3] + extra)
}
