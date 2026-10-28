package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Avatar: a circular crop of the portrait atlas (or Theo's own portrait)
// ---------------------------------------------------------------------------------------------

private val CROPS = mapOf(
    "ari" to intArrayOf(379, 449, 98), "milo" to intArrayOf(230, 513, 82), "juno" to intArrayOf(549, 525, 82),
    "theo" to intArrayOf(245, 760, 47), "noa" to intArrayOf(245, 827, 47), "kai" to intArrayOf(245, 894, 47),
)

@Composable
internal fun EmeraldAvatar(name: String, size: Float) {
    val assets = LocalEmeraldAssets.current
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width
        val b = 1.5f * density
        drawCircle(hex("#2c5747"), radius = s / 2)
        if (assets != null) {
            val inner = (s - 2 * b).roundToInt()
            val circle = Path().apply { addOval(Rect(b, b, s - b, s - b)) }
            clipPath(circle) {
                if (name == "theo") {
                    drawImage(
                        assets.theo, dstOffset = IntOffset(b.roundToInt(), b.roundToInt()), dstSize = IntSize(inner, inner),
                        filterQuality = FilterQuality.High,
                    )
                } else {
                    val (x, y, d) = CROPS.getValue(name).let { Triple(it[0], it[1], it[2]) }
                    drawImage(
                        assets.atlas, srcOffset = IntOffset(x, y), srcSize = IntSize(d, d),
                        dstOffset = IntOffset(b.roundToInt(), b.roundToInt()), dstSize = IntSize(inner, inner),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        }
        drawCircle(hex("#ffffff60"), radius = s / 2 - b / 2, style = Stroke(b))
    }
}

// ---------------------------------------------------------------------------------------------
// Crests drawn in code (the Crest SVG)
// ---------------------------------------------------------------------------------------------

private class LockTone(val light: Color, val dark: Color, val base: Color, val inner: Color, val edge: Color, val lock: Color, val hole: Color)

private val LOCKED_TONES = mapOf(
    "emerald" to LockTone(hex("#a6e1c5"), hex("#297e62"), hex("#549f88"), hex("#39957b"), hex("#8ed6bd"), hex("#b7e7d2"), hex("#4d9e80")),
    "sapphire" to LockTone(hex("#b7d2ff"), hex("#2a58a8"), hex("#4f74b4"), hex("#3b63aa"), hex("#a3c4ff"), hex("#dce8ff"), hex("#3b63aa")),
    "diamond" to LockTone(hex("#eef6fa"), hex("#71899a"), hex("#8aa1b0"), hex("#6f8898"), hex("#dbe9f1"), hex("#f3f8fb"), hex("#6f8898")),
)

private val CREST_BACK: Path by lazy { PathParser().parsePathString("M8 30 0 43 13 66 23 96 50 110 78 96 89 67 100 43 91 31Z").toPath() }
private val CREST_FACE: Path by lazy { PathParser().parsePathString("m50 0 41 22-4 61-19 22H32L13 83 9 22Z").toPath() }
private val CREST_INNER: Path by lazy { PathParser().parsePathString("m50 14 28 16-3 47-14 15H39L25 77l-3-47Z").toPath() }
private val CREST_SHACKLE: Path by lazy { PathParser().parsePathString("M40 49v-9a10 10 0 0 1 20 0v9").toPath() }

@Composable
private fun Crest(locked: Boolean = false, pale: Boolean = false, letter: String = "E", tone: String = "emerald") {
    val lockTone = LOCKED_TONES.getValue(tone)
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 51f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#fff0cc")
        }
    }
    Canvas(Modifier.size(100.dp, 112.dp)) {
        scale(density, Offset.Zero) {
            drawPath(CREST_BACK, if (locked) lockTone.base else hex("#b65a24"))
            val bounds = CREST_FACE.getBounds()
            drawPath(
                CREST_FACE,
                Brush.linearGradient(
                    listOf(
                        if (locked) lockTone.light else if (pale) hex("#ffe2ac") else hex("#ffc283"),
                        if (locked) lockTone.dark else if (pale) hex("#e4965a") else hex("#b9410e"),
                    ),
                    start = Offset(bounds.left, bounds.top), end = Offset(bounds.right, bounds.bottom),
                ),
            )
            drawPath(CREST_INNER, if (locked) lockTone.inner else hex("#d66d2e"))
            drawPath(CREST_INNER, if (locked) lockTone.edge else hex("#ffd49e"), style = Stroke(4f))
            if (locked) {
                drawPath(CREST_SHACKLE, lockTone.lock, style = Stroke(6f))
                drawRoundRect(lockTone.lock, Offset(33f, 48f), Size(34f, 27f), CornerRadius(5f))
                drawCircle(lockTone.hole, 4f, Offset(50f, 59f))
            } else {
                drawIntoCanvas { it.nativeCanvas.drawText(letter, 50f, 76f, paint) }
            }
        }
    }
}

/** A locked crest keeps the design's green ghost at rest and takes its own league's tone in focus. */
@Composable
private fun LockedCrest(index: Int, tone: String, league: EmeraldLeagueFocus) {
    Box(Modifier.size(100.dp, 112.dp)) {
        Crest(locked = true)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = max(0f, 1 - kotlin.math.abs(index - league.focus)) }) {
            Crest(locked = true, tone = tone)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// EmeraldTierLeaderboard
// ---------------------------------------------------------------------------------------------

private const val SCREEN_READER_ANNOUNCEMENT =
    "Theo Park is first with 1050 points. Ari Vale second, Milo Chen third, Juno Reed fourth."

private data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val ROWS = listOf(Triple("05", "noa", 730) to "Noa Ellis", Triple("06", "kai", 710) to "Kai Rowan")

/**
 * Rank Recast leaderboard. [previewTime] freezes the demo at a second of the loop and
 * [previewReducedMotion] forces the Reduced Motion state (both for deterministic previews).
 */
@Composable
fun EmeraldTierLeaderboard(
    autoPlay: Boolean = true,
    previewTime: Float? = null,
    previewReducedMotion: Boolean? = null,
    onRendererStatus: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val systemReduced = rememberLiveReducedMotion()
    val reduced = previewReducedMotion ?: systemReduced
    val solidMaterial = rememberEmeraldSolidMaterial()
    val assets = rememberEmeraldAssets()
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetTop = safe.getTop(density) / density.density
    val insetBottom = safe.getBottom(density) / density.density

    var viewportWidth by remember { mutableFloatStateOf(393f) }
    var viewportHeight by remember { mutableFloatStateOf(852f) }
    var tab by remember { mutableStateOf("Leagues") }
    var nav by remember { mutableStateOf("Tournaments") }

    val widthScale = viewportWidth / 393
    val usableHeight = viewportHeight - insetTop - insetBottom - 78 * widthScale
    val scale = min(widthScale, max(.65f, usableHeight / 714))

    // Haptic impact when the crown seats (UIImpactFeedbackGenerator Light) plus the screen-reader announcement.
    val motion = remember(autoPlay, previewTime, view) {
        EmeraldRankMotion(autoPlay, previewTime, reduced, max(120f, 241.5f * scale)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val manager = view.context.getSystemService(AccessibilityManager::class.java)
            if (manager != null && manager.isEnabled && manager.isTouchExplorationEnabled) {
                view.announceForAccessibility(SCREEN_READER_ANNOUNCEMENT)
            }
        }
    }
    // useLeagueFocus: Haptics.selectionAsync() on every league change a finger causes.
    val league = remember(motion, view) { EmeraldLeagueFocus(motion) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
    SideEffect {
        motion.reduced = reduced
        motion.width = max(120f, 241.5f * scale)
        league.enabled = previewTime == null && tab == "Leagues"
        league.reduced = reduced
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, motion) {
        val observer = LifecycleEventObserver { _, _ ->
            motion.setResumed(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        motion.setResumed(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(motion) { motion.applyPreview() }
    LaunchedEffect(motion, reduced) { motion.onReducedChanged() }
    // One clock for everything: the demo, the animations and the league focus.
    LaunchedEffect(motion, league) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                val ms = nanos / 1_000_000.0
                motion.frame(ms)
                league.frame(ms)
            }
        }
    }

    val rankFrame = remember(motion) {
        derivedStateOf { emeraldRankFrame(motion.rankPhase, motion.reduced, motion.responseAllowed) }
    }
    val toScreen = scale / density.density

    CompositionLocalProvider(LocalEmeraldAssets provides assets) {
        Box(
            Modifier
                .fillMaxSize()
                .background(hex("#080a09"))
                .clipToBounds()
                .onSizeChanged {
                    viewportWidth = it.width / density.density
                    viewportHeight = it.height / density.density
                },
        ) {
            BlockSystemBars(darkIcons = false)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to hex("#00c88e"), .23f to hex("#008c67"), .40f to hex("#064b37"),
                        .58f to hex("#071e14"), .82f to hex("#080a09"),
                    ),
                ),
            )
            LeagueTint(league)
            // The 393 x 714 design, scaled from its top-left corner and centred horizontally.
            Box(
                Modifier
                    .offset(((viewportWidth - 393 * scale) / 2).dp, (insetTop + 8).dp)
                    .requiredSize(393.dp, 714.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                // header
                Box(Modifier.offset(20.dp, 0.dp).size(304.dp, 42.dp)) {
                    EmeraldGlass(GlassKind.green, 25f, solidMaterial, Modifier.matchParentSize())
                    Row(Modifier.fillMaxSize().padding(4.dp)) {
                        listOf("Leagues", "Challenges").forEach { label ->
                            EmeraldButton(
                                Modifier.weight(1f).fillMaxHeight(), 22f, label, Role.Tab, tab == label,
                                onPress = {
                                    tab = label
                                    if (label == "Leagues") motion.replay()
                                },
                            ) {
                                if (tab == label) EmeraldGlass(GlassKind.selected, 22f, solidMaterial, Modifier.matchParentSize())
                                EmeraldText(
                                    label, 12f, if (tab == label) Color.White else hex("#94bbac"), Modifier.align(Alignment.Center),
                                    if (tab == label) FontWeight.Bold else null,
                                )
                            }
                        }
                    }
                }
                EmeraldButton(
                    Modifier.offset(334.dp, 0.dp).size(44.dp), 0f, "Replay league update", onPress = { motion.replay() },
                ) {
                    Icon(Icons.Filled.Notifications, null, Modifier.align(Alignment.Center).size(27.dp), hex("#77f38a"))
                    Box(Modifier.align(Alignment.TopEnd).offset((-7).dp, 4.dp).size(8.dp).background(hex("#ff8278"), CircleShape))
                }

                LeagueCrestRow(
                    league,
                    Modifier
                        .offset(0.dp, 57.dp)
                        .zIndex(3f)
                        .emeraldPan(
                            key = league, enabled = { league.enabled }, vertical = false, activeOffset = 10f, failOffset = 14f,
                            toScreen = toScreen, onStart = { league.onPanStart() }, onUpdate = { league.onPanUpdate(it) },
                            onEnd = { league.onPanEnd(it) }, onFinalize = {},
                        ),
                ) { item, index ->
                    when {
                        index == CURRENT_LEAGUE -> ArtMaterial(assets?.crest, 100f, 112f)
                        item.state == LeagueState.locked -> LockedCrest(index, if (item.key == "diamond") "diamond" else "sapphire", league)
                        else -> Crest(pale = item.key == "amber", letter = item.name.take(1))
                    }
                }
                LeagueHeadline(league, solidMaterial, challenges = tab != "Leagues")

                // arena
                Box(
                    Modifier
                        .offset(0.dp, 273.dp)
                        .size(393.dp, 335.dp)
                        .leagueRecede(league)
                        .emeraldPan(
                            key = motion, enabled = { motion.panEnabled }, vertical = true, activeOffset = 8f, failOffset = 14f,
                            toScreen = toScreen, onStart = { motion.onPanStart(it) }, onUpdate = { motion.onPanUpdate(it) },
                            onEnd = { motion.onPanEnd(it) }, onFinalize = { motion.onPanFinalize() },
                        )
                        .semantics {
                            contentDescription = "Rank Recast. Pull the fourth-place row upward to preview the new leader; pull downward to reverse."
                        },
                ) {
                    EmeraldRecastScene(rankFrame, solidMaterial, onRendererStatus)
                    EmeraldButton(
                        Modifier.offset(19.dp, 288.dp).size(355.dp, 47.dp).zIndex(15f), 13f,
                        "Replay the fourth-place row becoming the leader", onPress = { motion.replay() },
                    )
                    EmeraldButton(
                        Modifier.offset(135.dp, 169.dp).size(123.dp, 105.dp).zIndex(15f), 5f,
                        "Replay Rank Recast", onPress = { motion.replay() },
                    )
                }

                // standings 05 and 06
                Column(
                    Modifier.offset(19.dp, 614.dp).width(355.dp).leagueRecede(league),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ROWS.forEach { (row, name) ->
                        val (number, avatar, score) = row
                        Box(Modifier.fillMaxWidth().height(47.dp)) {
                            EmeraldGlass(GlassKind.row, 13f, solidMaterial, Modifier.matchParentSize())
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EmeraldText(number, 12f, hex("#b5b9b5"), tabular = true)
                                EmeraldAvatar(avatar, 30f)
                                EmeraldText(name, 12f, Color.White, Modifier.weight(1f), FontWeight.Medium)
                                EmeraldText(score.toString(), 12f, Color.White, tabular = true)
                                EmeraldText("ϟ", 20f, hex("#ffc846"), Modifier.offset((-8).dp, 0.dp))
                            }
                        }
                    }
                }
            }

            // bottom navigation, sized by the width scale only
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = (19 * widthScale).dp, end = (19 * widthScale).dp, bottom = max(8f, insetBottom).dp)
                    .fillMaxWidth()
                    .height((52 * widthScale).dp),
            ) {
                EmeraldGlass(GlassKind.smoke, 29f, solidMaterial, Modifier.matchParentSize())
                Row(Modifier.fillMaxSize().padding(2.dp)) {
                    listOf(
                        NavItem("Home", Icons.Filled.Home),
                        NavItem("Tournaments", Icons.Filled.EmojiEvents),
                        NavItem("Profile", Icons.Filled.Person),
                    ).forEach { item ->
                        val selected = nav == item.label
                        EmeraldButton(
                            Modifier.weight(1f).fillMaxHeight(), 25f, item.label, Role.Tab, selected,
                            onPress = {
                                nav = item.label
                                if (item.label == "Tournaments") motion.replay()
                            },
                        ) {
                            if (selected) EmeraldGlass(GlassKind.selected, 25f, solidMaterial, Modifier.matchParentSize())
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val tone = if (selected) Color.White else hex("#a7ada6")
                                Icon(item.icon, null, Modifier.size((20 * widthScale).dp), tone)
                                EmeraldText(item.label, 10f, tone)
                            }
                        }
                    }
                }
            }
        }
    }
}
