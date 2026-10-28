package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.graphics.Bitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One specimen as the panels read it. */
internal class HabitatPanelSpec(
    val name: String,
    val number: String,
    val image: Bitmap,
    val copy: String,
    val stats: List<Int>,
    val evolution: List<Stage>,
) {
    class Stage(val name: String, val number: String, val level: Int? = null)
}

private val TABS = listOf("About", "Base stats", "Evolution")
private val STAT_LABELS = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")

/** Element tints in carousel order, repeated so colour interpolation wraps 2 -> 0. */
private val ELEMENT_COLORS = listOf(0xFFF2A766, 0xFF7FD3EA, 0xFFA9DD7F, 0xFFF2A766).map { Color(it) }
private const val STAT_CEILING = 150f
private val TAB_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Reanimated's interpolateColor in RGB over [0, 1, 2, 3]. */
private fun elementColor(position: Float): Color {
    val x = clamp(position, 0f, 3f)
    val i = min(2, floor(x).toInt())
    val f = x - i
    val a = ELEMENT_COLORS[i]
    val b = ELEMENT_COLORS[i + 1]
    return Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)
}

/** Value of one base stat at a continuous carousel position: it morphs with the swipe itself. */
private fun statAt(stats: List<List<Int>>, stat: Int, position: Float): Float {
    val p = wrapPosition(position)
    val from = floor(p).toInt() % 3
    val to = (from + 1) % 3
    val f = p - floor(p)
    val eased = f * f * (3 - 2 * f)
    return stats[from][stat] + (stats[to][stat] - stats[from][stat]) * eased
}

private fun hex(value: Long) = Color(value)

@Composable
private fun StatRow(
    index: Int, stats: List<List<Int>>, motion: HabitatMotion, reveal: HabitatAnim, scale: Float, height: Float,
) {
    val density = LocalDensity.current.density
    // Rows fill in reading order, 45 ms apart, from one shared reveal.
    val line = 15 * scale
    Row(Modifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(STAT_LABELS[index], 9.5f * scale, hex(0xFF9FAE9E), Modifier.width((44 * scale).dp), letterSpacing = 0.3f, maxFontScale = 1.3f)
        TextSwap(motion, line, Modifier.width((26 * scale).dp).padding(end = 10.dp), items = stats.map { values ->
            { HText("${values[index]}", 11.5f * scale, hex(0xFFF1F4E5), Modifier.fillMaxWidth(), FontWeight.SemiBold, lineHeight = line, align = TextAlign.End, tabular = true, maxFontScale = 1.3f) }
        })
        Box(
            Modifier.weight(1f).height((4 * scale).dp)
                .background(hex(0x14FFFFFF), RoundedCornerShape((2 * scale).dp)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        motion.observe()
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        val local = if (motion.reduced) 1f else {
                            val t = max(0f, min(1f, (reveal.value - index * 0.075f) / 0.6f))
                            1 - (1 - t).pow(3)
                        }
                        scaleX = min(1f, statAt(stats, index, motion.position) / STAT_CEILING) * local
                    }
                    .drawBehind { drawRoundRect(elementColor(wrapPosition(motion.position)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2 * scale * density)) },
            )
        }
    }
}

@Composable
private fun Evolution(spec: HabitatPanelSpec, motion: HabitatMotion, reveal: HabitatAnim, scale: Float, width: Float) {
    val current = spec.evolution.indexOfFirst { it.number == spec.number }
    val node = 40 * scale
    val column = 74 * scale
    val centers = listOf(column / 2, width / 2, width - column / 2)
    Box(Modifier.fillMaxSize()) {
        for (link in 0..1) {
            EvolutionLink(
                order = link, reached = link < current, level = spec.evolution.getOrNull(link + 1)?.level,
                from = centers[link] + node / 2 + 6 * scale, to = centers[link + 1] - node / 2 - 6 * scale, top = node / 2,
                motion = motion, reveal = reveal, scale = scale,
            )
        }
        spec.evolution.forEachIndexed { stageIndex, stage ->
            EvolutionStage(
                stage = stage, active = stageIndex == current, order = stageIndex, image = spec.image, motion = motion,
                reveal = reveal, scale = scale, node = node, left = centers[stageIndex] - column / 2, width = column,
            )
        }
    }
}

@Composable
private fun EvolutionLink(
    order: Int, reached: Boolean, level: Int?, from: Float, to: Float, top: Float,
    motion: HabitatMotion, reveal: HabitatAnim, scale: Float,
) {
    val width = max(0f, to - from)
    Box(Modifier.offset(from.dp, (top - 0.5f).dp).size(width.dp, 1.dp)) {
        Box(Modifier.fillMaxSize().background(hex(0x22D4E4DA)))
        // Reached links draw toward the specimen on show; the next evolution stays a quiet rail.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    motion.observe()
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    val t = if (motion.reduced) 1f else max(0f, min(1f, (reveal.value - 0.12f - order * 0.16f) / 0.45f))
                    scaleX = if (reached) 1 - (1 - t).pow(3) else 0f
                }
                .background(hex(0x88E0EACC)),
        )
        if (level != null) {
            Box(Modifier.fillMaxWidth().offset(0.dp, (-9 * scale).dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.height((18 * scale).dp)
                        .border(Dp.Hairline, hex(0x2AD4E4DA), RoundedCornerShape((9 * scale).dp))
                        .background(hex(0xFF18241F), RoundedCornerShape((9 * scale).dp))
                        .padding(horizontal = (6 * scale).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HText("Lv $level", 8.5f * scale, hex(0xFFC9D4C3), weight = FontWeight.SemiBold, letterSpacing = 0.4f, lineHeight = 18 * scale, maxFontScale = 1.3f)
                }
            }
        }
    }
}

@Composable
private fun EvolutionStage(
    stage: HabitatPanelSpec.Stage, active: Boolean, order: Int, image: Bitmap, motion: HabitatMotion,
    reveal: HabitatAnim, scale: Float, node: Float, left: Float, width: Float,
) {
    val bitmap = remember(image) { image.asImageBitmap() }
    Box(
        Modifier.offset(left.dp, 0.dp).width(width.dp).fillMaxHeight()
            .graphicsLayer {
                motion.observe()
                if (motion.reduced) { alpha = 1f; scaleX = 1f; scaleY = 1f } else {
                    val t = max(0f, min(1f, (reveal.value - order * 0.16f) / 0.5f))
                    val eased = 1 - (1 - t).pow(3)
                    alpha = eased
                    scaleX = 0.92f + 0.08f * eased
                    scaleY = 0.92f + 0.08f * eased
                }
            },
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.size(node.dp)
                    .background(if (active) hex(0x12FFFFFF) else hex(0x08FFFFFF), shape)
                    .border(if (active) 1.dp else Dp.Hairline, if (active) hex(0xAAE9EFD8) else hex(0x33D4E4DA), shape),
                contentAlignment = Alignment.Center,
            ) {
                if (active) Image(bitmap, null, Modifier.size((node * 0.92f).dp))
                else HText("#${stage.number}", 9.5f * scale, hex(0xFFA5B49D), weight = FontWeight.SemiBold, letterSpacing = 0.6f, tabular = true, maxFontScale = 1.3f)
            }
            HText(
                stage.name, 10f * scale, if (active) hex(0xFFF1F4E5) else hex(0xFF9FAE9E), Modifier.padding(top = (6 * scale).dp),
                if (active) FontWeight.SemiBold else FontWeight.Medium, maxFontScale = 1.3f, maxLines = 1,
            )
        }
    }
}

/** The tabs, the underline and the three panels. */
@Composable
internal fun HabitatPanels(
    specs: List<HabitatPanelSpec>,
    motion: HabitatMotion,
    scale: Float,
    width: Float,
    tabsTop: Float,
    contentTop: Float,
    contentHeight: Float,
    tab: Int,
    onTab: (Int) -> Unit,
    selectedIndex: Int,
) {
    val density = LocalDensity.current.density
    val reducedMotion = motion.reduced
    var layouts by remember { mutableStateOf(listOf<Pair<Float, Float>?>(null, null, null)) }
    val underlineX = remember { motion.clocked(HabitatAnim(0f)) }
    val underlineWidth = remember { motion.clocked(HabitatAnim(21 * scale)) }
    val reveal = remember { motion.clocked(HabitatAnim(1f)) }
    val stats = specs.map { it.stats }
    LaunchedEffect(layouts, reducedMotion, scale, tab) {
        val target = layouts[tab] ?: return@LaunchedEffect
        // The underline keeps the short 21 pt mark of the design, centred on the label's start.
        underlineX.spring(target.first, 380f, 1f, always = reducedMotion)
        underlineWidth.spring(min(21 * scale, target.second), 380f, 1f, always = reducedMotion)
    }
    LaunchedEffect(reducedMotion, tab) {
        if (reducedMotion) { reveal.set(1f); return@LaunchedEffect }
        reveal.set(0f)
        reveal.timing(1f, 900f)
    }
    val rowHeight = min(24 * scale, contentHeight / 3)
    val panelWidth = width - 54 * scale

    // Tabs.
    Box(
        Modifier.offset((27 * scale).dp, tabsTop.dp).width(panelWidth.dp).height((32 * scale).dp),
    ) {
        Box(Modifier.fillMaxWidth().height(Dp.Hairline).background(hex(0x19D4E4DA)))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((31 * scale).dp)) {
            TABS.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .onGloballyPositioned { c ->
                            val x = c.positionInParent().x / density
                            val w = c.size.width / density
                            if (layouts[index]?.first != x || layouts[index]?.second != w) {
                                layouts = layouts.toMutableList().also { it[index] = x to w }
                            }
                        }
                        .pointerInput(index) { detectTapGestures { onTab(index) } },
                ) {
                    val color = animateColorLike(if (tab == index) hex(0xFFF1F4E5) else hex(0xFF8F9E8F))
                    HText(label, 11f * scale, color, weight = FontWeight.SemiBold, maxFontScale = 1.3f)
                }
            }
        }
        Box(
            Modifier
                .offset(0.dp, (12 + 11 * scale * 1.2f + 12 * scale - 2).dp)
                .graphicsLayer {
                    motion.observe()
                    translationX = underlineX.value * density
                }
                .drawBehind {
                    drawRoundRect(hex(0xFFE0EACC), size = Size(underlineWidth.value * density, 2 * density), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2 * density))
                }
                .size(1.dp),
        )
    }

    // About.
    Panel(0, tab, reducedMotion, scale, contentTop, contentHeight, panelWidth) {
        TextSwap(motion, contentHeight, items = specs.mapIndexed { index, spec ->
            {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = tab == 0 && index == selectedIndex)) {
                    HText(spec.copy, 11.5f * scale, hex(0xFFC4CEC0), letterSpacing = 0.02f, lineHeight = 19 * scale)
                }
            }
        })
    }
    // Base stats.
    Panel(1, tab, reducedMotion, scale, contentTop, contentHeight, panelWidth) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((22 * scale).dp)) {
            for (column in 0..1) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    for (row in 0..2) StatRow(column * 3 + row, stats, motion, reveal, scale, rowHeight)
                }
            }
        }
    }
    // Evolution.
    Panel(2, tab, reducedMotion, scale, contentTop, contentHeight, panelWidth) {
        TextSwap(motion, contentHeight, items = specs.map { spec -> { Evolution(spec, motion, reveal, scale, panelWidth) } })
    }
}

@Composable
private fun animateColorLike(target: Color): Color {
    val r by animateFloatAsState(target.red, tween(150, easing = TAB_EASING), label = "r")
    val g by animateFloatAsState(target.green, tween(150, easing = TAB_EASING), label = "g")
    val b by animateFloatAsState(target.blue, tween(150, easing = TAB_EASING), label = "b")
    return Color(r, g, b, 1f)
}

/** A panel fades (and rises 4 pt) in when its tab is chosen: 160 ms, only the fade under Reduced Motion. */
@Composable
private fun Panel(index: Int, tab: Int, reducedMotion: Boolean, scale: Float, top: Float, height: Float, width: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val shown by animateFloatAsState(if (tab == index) 1f else 0f, tween(160, easing = TAB_EASING), label = "panel")
    Box(
        Modifier.offset((27 * scale).dp, top.dp).size(width.dp, height.dp)
            .graphicsLayer {
                alpha = shown
                translationY = if (reducedMotion) 0f else (1f - shown) * 4 * scale * density
            },
    ) { content() }
}
