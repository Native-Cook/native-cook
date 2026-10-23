package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.hypot

/**
 * Mood Orb Jar, full screen: a glass jar with a metal lid, a five-mood picker and the day's entries. Flick an orb up from
 * the picker and it flies into the jar on an arc solved for its mouth; swipe the newest entry to undo it.
 * Ported from the React Native block `mood-orb-jar`; the simulation is `MoodOrbJarSim.kt`.
 */
@Composable
fun MoodOrbJar(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reduced = rememberReducedMotion()
    val safeTop = WindowInsets.safeDrawing.getTop(localDensity) / localDensity.density
    val art = rememberMoodOrbJarArt()
    val pens = rememberMoodOrbJarScenePens()
    val roll = rememberMoodOrbJarRoll(enabled = !reduced)

    // The jar sits on a light page: dark status bar icons.
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MoodOrbJarColors.bg)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val geometry = remember(width, height, safeTop) { makeGeometry(width, height, safeTop.toDouble()) }
        val currentGeometry = rememberUpdatedState(geometry)
        val currentReduced = rememberUpdatedState(reduced)

        // Settling the pile takes a few hundred steps: once, not on every composition.
        val sim = remember { initialSim(geometry, reduced, autoPlay) }
        val frame = remember { MoodOrbJarFrame(initialCount = (0 until POOL).count { sim.state[it] == MoodOrbJarOrb.PILE }) }
        var entries by remember { mutableStateOf(MoodOrbJarStartEntries) }
        val nextId = remember { mutableIntStateOf(1) }

        // The new entry's avatar: the first card's orb, where the shared element lands.
        fun avatarX(g: MoodOrbJarGeometry): Double {
            return MoodOrbJarLayout.SIDE * g.s + MoodOrbJarSpace.CARD_PAD + MoodOrbJarLayout.ENTRY_ORB
        }

        fun avatarY(g: MoodOrbJarGeometry): Double {
            return g.rowY + MoodOrbJarSpace.AVATAR_TOP + MoodOrbJarLayout.ENTRY_ORB
        }

        fun onEvent(event: Int) {
            if (event >= MoodOrbJarEvent.REMOVED) {
                entries = if (entries.firstOrNull()?.fresh == true) entries.drop(1) else entries
            } else if (event >= MoodOrbJarEvent.ADDED) {
                val mood = event - MoodOrbJarEvent.ADDED
                val id = nextId.intValue++
                val added = MoodOrbJarEntry(
                    id = id,
                    mood = mood,
                    tags = if (MoodOrbJarTheme.REPLICA) {
                        MoodOrbJarTokens.Copy.replicaEntry.tags
                    } else {
                        MoodOrbJarMoods[mood].tags
                    },
                    time = if (MoodOrbJarTheme.REPLICA) {
                        MoodOrbJarTokens.Copy.replicaEntry.time
                    } else {
                        MoodOrbJarCopy.NOW
                    },
                    fresh = true,
                )
                entries = listOf(added) + entries.map { it.copy(fresh = false) }
            } else {
                view.performMoodOrbJarHaptic(event)
            }
        }

        LaunchedEffect(sim, autoPlay) {
            var previousNanos = withFrameNanos { it }
            while (isActive) {
                val nanos = withFrameNanos { it }
                val elapsed = ((nanos - previousNanos) / 1_000_000_000.0)
                    .coerceAtMost(MoodOrbJarMotion.MAX_FRAME_DELTA)
                previousNanos = nanos
                val g = currentGeometry.value
                sim.reduced = currentReduced.value
                sim.tilt = if (sim.reduced) 0.0 else roll.value.toDouble().coerceIn(-1.0, 1.0)
                if (MoodOrbJarMotion.REPLICA_HOLD >= 0) {
                    // QA only: run to the held time once, then freeze.
                    if (sim.clock == 0.0) stepSim(sim, g, MoodOrbJarMotion.REPLICA_HOLD, autoPlay, uncapped = true)
                } else {
                    stepSim(sim, g, elapsed * MoodOrbJarMotion.QA_SPEED, autoPlay)
                }
                frame.publish(sim, g, avatarX(g), avatarY(g))
                // Events from the finger and from the steps both wait in the sim until a frame hands them over.
                for (index in sim.events.indices) onEvent(sim.events[index])
                sim.events.clear()
                frame.tick.longValue++
            }
        }

        val act = remember(sim) {
            { input: MoodOrbJarInput -> applyInput(sim, currentGeometry.value, input) }
        }

        // A touch on a picker orb or on the newest entry becomes this block's drag; anything else is left alone.
        fun hits(x: Double, y: Double): Boolean {
            val g = currentGeometry.value
            for (m in MoodOrbJarMoods.indices) {
                if (
                    hypot(x - g.slotX[m], y - g.slotY) <
                    MoodOrbJarTokens.Style.slotHitHalfWidth * g.s
                ) {
                    return true
                }
            }
            return sim.lastAdded >= 0 && y > g.rowY && y < g.rowY + g.rowH && x < g.w - 64 * g.s
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(sim) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = (down.position.x / density).toDouble()
                        val y = (down.position.y / density).toDouble()
                        if (!hits(x, y)) {
                            // The React Native pan fails here and its finalizer sends a release with no velocity.
                            act(MoodOrbJarInput.Up(0.0, 0.0))
                            return@awaitEachGesture
                        }
                        act(MoodOrbJarInput.Down(x, y))
                        down.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val velocity = tracker.calculateVelocity()
                                if (!change.pressed) {
                                    act(MoodOrbJarInput.Up((velocity.x / density).toDouble(), (velocity.y / density).toDouble()))
                                    released = true
                                    change.consume()
                                    break
                                }
                                act(
                                    MoodOrbJarInput.Move(
                                        (change.position.x / density).toDouble(),
                                        (change.position.y / density).toDouble(),
                                        (velocity.x / density).toDouble(),
                                        (velocity.y / density).toDouble(),
                                    ),
                                )
                                change.consume()
                            }
                        } finally {
                            // A cancelled gesture ends like the pan's finalizer: a release with no velocity.
                            if (!released) act(MoodOrbJarInput.Up(0.0, 0.0))
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawMoodOrbJarScene(currentGeometry.value, frame, art, pens)
            }

            MoodOrbJarHeader(safeTop = safeTop.toDouble(), g = geometry)

            MoodOrbJarMoods.forEachIndexed { index, mood ->
                MoodOrbJarSlotLabel(
                    index = index,
                    name = mood.name,
                    g = geometry,
                    onAdd = { act(MoodOrbJarInput.Add(index)) },
                )
            }

            MoodOrbJarMonthLabel(count = frame.count, g = geometry, reduced = reduced)

            MoodOrbJarEntryList(
                entries = entries,
                frame = frame,
                width = width - MoodOrbJarLayout.SIDE * geometry.s * 2,
                onUndo = { act(MoodOrbJarInput.UndoCommand) },
                modifier = Modifier.offset(
                    x = (MoodOrbJarLayout.SIDE * geometry.s).dp,
                    y = geometry.rowY.dp,
                ),
            )

            // The shared element flies above the list, so it lands on the new card's avatar.
            Canvas(Modifier.fillMaxSize()) {
                drawMoodOrbJarGhost(currentGeometry.value, frame, art)
            }
        }
    }
}

/** Back, calendar and more: the header's three icons (they only exist for the look and for accessibility). */
@Composable
private fun MoodOrbJarHeader(safeTop: Double, g: MoodOrbJarGeometry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = MoodOrbJarTokens.Style.headerInset.dp,
                end = MoodOrbJarTokens.Style.headerInset.dp,
                top = safeTop.dp,
            )
            .height((MoodOrbJarLayout.HEADER * g.s).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MoodOrbJarLayout.ICON.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = MoodOrbJarCopy.BACK
                },
            contentAlignment = Alignment.Center,
        ) {
            // A 12 pt square with a left and a bottom border of 2.2 pt, turned 45 degrees: a chevron.
            Canvas(
                Modifier
                    .padding(start = MoodOrbJarTokens.Style.chevronInset.dp)
                    .size(MoodOrbJarTokens.Style.chevronSize.dp),
            ) {
                val stroke = MoodOrbJarTokens.Style.chevronStroke.dp.toPx()
                val chevron = Path().apply {
                    moveTo(stroke / 2, 0f)
                    lineTo(stroke / 2, size.height - stroke / 2)
                    lineTo(size.width, size.height - stroke / 2)
                }
                rotate(45f, pivot = center) {
                    drawPath(
                        path = chevron,
                        color = MoodOrbJarColors.ink,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt, join = StrokeJoin.Miter),
                    )
                }
            }
        }
        Row {
            Box(
                Modifier
                    .size(MoodOrbJarLayout.ICON.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = MoodOrbJarCopy.CALENDAR
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(MoodOrbJarTokens.Style.calendarSize.dp)
                        .border(
                            MoodOrbJarTokens.Style.calendarStroke.dp,
                            MoodOrbJarColors.ink,
                            RoundedCornerShape(MoodOrbJarTokens.Style.calendarRadius.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "14",
                        color = MoodOrbJarColors.ink,
                        fontSize = MoodOrbJarTokens.Style.calendarTextSize.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = MoodOrbJarTokens.Style.calendarTextTop.dp),
                    )
                }
            }
            Box(
                Modifier
                    .size(MoodOrbJarLayout.ICON.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = MoodOrbJarCopy.MORE
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "•••",
                    color = MoodOrbJarColors.ink,
                    fontSize = MoodOrbJarTokens.Style.moreTextSize.sp,
                    letterSpacing = MoodOrbJarTokens.Style.moreTracking.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The picker's label under one orb; the orb itself is drawn by the canvas. The touch is the screen's own drag. */
@Composable
private fun MoodOrbJarSlotLabel(index: Int, name: String, g: MoodOrbJarGeometry, onAdd: () -> Unit) {
    Box(
        Modifier
            .offset(
                x = (g.slotX[index] - MoodOrbJarTokens.Style.slotHitHalfWidth * g.s).dp,
                y = (g.slotY - MoodOrbJarTokens.Style.slotHitTop * g.s).dp,
            )
            .size(
                width = (MoodOrbJarTokens.Style.slotHitHalfWidth * 2 * g.s).dp,
                height = (MoodOrbJarLayout.SLOT_LABEL_H * g.s).dp,
            )
            .semantics {
                role = Role.Button
                contentDescription = MoodOrbJarCopy.add(name)
                onClick {
                    onAdd()
                    true
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(text = name, color = MoodOrbJarColors.label, fontSize = MoodOrbJarLayout.LABEL_SIZE.sp)
    }
}

/** The month under the jar's shoulder and the pile's count, which rolls like an odometer when an orb joins or leaves. */
@Composable
private fun MoodOrbJarMonthLabel(count: IntState, g: MoodOrbJarGeometry, reduced: Boolean) {
    val density = LocalDensity.current
    val rise = with(density) { MoodOrbJarTokens.Style.entryRise.dp.roundToPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .offset(y = (g.mouthY + MoodOrbJarLayout.JAR_HEIGHT * MoodOrbJarLayout.LABEL_Y * g.s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MoodOrbJarCopy.MONTH,
            color = MoodOrbJarColors.mute,
            fontSize = MoodOrbJarTokens.Style.monthSize.sp,
            letterSpacing = MoodOrbJarTokens.Style.monthTracking.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .padding(top = MoodOrbJarSpace.XS.dp)
                .height(MoodOrbJarTokens.Style.countBoxHeight.dp)
                .clipToBounds(),
        ) {
            AnimatedContent(
                targetState = count.intValue,
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    val transition = if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // FadeInDown (260 ms) for the new count, FadeOutUp (200 ms) for the old one.
                        (
                            fadeIn(tween(MoodOrbJarTokens.Style.countEnterMs, easing = MoodOrbJarEase)) +
                                slideInVertically(tween(MoodOrbJarTokens.Style.countEnterMs, easing = MoodOrbJarEase)) { rise }
                            ) togetherWith (
                            fadeOut(tween(MoodOrbJarTokens.Style.countExitMs, easing = MoodOrbJarEase)) +
                                slideOutVertically(tween(MoodOrbJarTokens.Style.countExitMs, easing = MoodOrbJarEase)) { -rise }
                            )
                    }
                    transition.using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "orb count",
            ) { n ->
                Text(
                    text = n.toString(),
                    color = MoodOrbJarColors.ink,
                    fontSize = MoodOrbJarTokens.Style.countSize.sp,
                    lineHeight = MoodOrbJarTokens.Style.countLineHeight.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}
