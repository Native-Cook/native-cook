package com.ngocdevv.nativecook.blocks.inputs.liquidsearchmorph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// Recent searches pop in one after another, CHIP_STAGGER ms apart, once the bar has landed; the
// results pop TILE_STAGGER ms apart.
private const val CHIP_STAGGER = 45
private const val TILE_STAGGER = 40
private const val TILE_PHOTO = 196f
/** One result tile: photo, its margin, the name and the kind/price row. */
private const val TILE_HEIGHT = 250f
private const val ROW_GAP = 20f
private const val HERO_TOP = 222f
private const val HERO_HEIGHT = 238f

// Reanimated's withSpring({ duration, dampingRatio }) with its stiffness solved for the duration
// (mass 1): the pop (420 ms, 0.62) and the flow of tiles to their new places (520 ms, 0.82).
private val PopSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 973.4f, visibilityThreshold = 0.0005f)
private val FlowSpring = spring(dampingRatio = 0.82f, stiffness = 368.6f, visibilityThreshold = Offset(0.05f, 0.05f))

/** A sneaker or a scent, with its Unsplash photo. */
internal class Piece(val id: String, val name: String, val kind: String, val price: String, val photo: String)

internal val PIECES = listOf(
    Piece("fairline", "Fairline Duo", "Runner", "€168", "photo-1560769629-975ec94e6a86"),
    Piece("bordeaux", "Bordeaux Garnet", "Ring", "€212", "photo-1735212214451-a8f43a015309"),
    Piece("stairway", "Stairway Hoops", "Earrings", "€86", "photo-1751552147823-e2a844bce504"),
    Piece("airlace", "Airlace Chain", "Necklace", "€74", "photo-1751552147774-c374ae8e9910"),
    Piece("dune", "Dune Slip Low", "Slip-on", "€148", "photo-1603808033192-082d6919d3e1"),
    Piece("paired", "Paired Crimson 4", "Court", "€189", "photo-1605523741177-cd660595c2cf"),
    Piece("daisy", "Daisy Cluster", "Ring", "€164", "photo-1751551683594-ab054a031696"),
    Piece("rose", "Rose Braid", "Bangle", "€128", "photo-1735905131335-a921150a017d"),
    Piece("gilded", "Gilded Air Extrait", "Extrait · 30 ml", "€138", "photo-1622618991746-fe6004db3a47"),
    Piece("cabinet", "Cabinet Brooch", "Brooch", "€236", "photo-1615957579676-d1db3be6effe"),
    Piece("amber", "Amber Heirloom", "Eau de parfum · 75 ml", "€118", "photo-1535683577427-740aaac4ec25"),
    Piece("satin", "Satin Petal", "Eau de parfum · 50 ml", "€104", "photo-1543422655-ac1c6ca993ed"),
)

private val RECENT = listOf("runner", "amber", "court", "ring", "extrait", "parfum")

private val POPULAR = listOf("Fairline Duo", "Gilded Air Extrait", "Daisy Cluster", "Satin Petal")

/** The pieces whose name or kind holds the query, in the shop's order. */
private fun search(query: String): List<Piece> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return PIECES.filter { it.name.lowercase().contains(q) || it.kind.lowercase().contains(q) }
}

private fun photo(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

@Composable
private fun Photo(id: String, width: Int, modifier: Modifier = Modifier, fade: Int = 200) {
    val context = LocalContext.current
    val request = remember(id, width) { ImageRequest.Builder(context).data(photo(id, width)).crossfade(fade).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(SAND))
}

/** A soft drop shadow under a rounded shape, like a CSS box-shadow (blur is twice the sigma). */
private fun Modifier.softShadow(color: Color, blur: Float, dy: Float, radius: Float) = this.drawBehind {
    val shadow = SoftRoundRect(color, blur / 2)
    scale(density, density, pivot = Offset.Zero) {
        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, dy, size.width / density, size.height / density, radius) }
    }
}

// MARK: - Home page

@Composable
internal fun Home(top: Float, screenW: Float, screenH: Float, tileWidth: Float) {
    val hero = PIECES[4]
    val trendingTop = top + HERO_TOP + HERO_HEIGHT + 60
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .offset(SIDE.dp, (top + 12).dp)
                .size((screenW - SIDE * 2).coerceAtLeast(0f).dp, 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ATELIER NORD", color = INK, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.2.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .softShadow(INK.copy(alpha = 0.08f), blur = 8f, dy = 2f, radius = 20f)
                    .background(CARD, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = "Bag", tint = INK, modifier = Modifier.size(22.dp))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset((-4).dp, 4.dp)
                        .size(15.dp)
                        .background(INK, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("2", color = CREAM, fontSize = 9.sp, fontWeight = FontWeight.Bold, style = TextStyle(lineHeight = 11.sp))
                }
            }
        }

        Text(
            "Walk soft,\nsmell of rain.",
            modifier = Modifier.offset(SIDE.dp, (top + 58).dp).width((screenW - SIDE * 2).dp),
            color = INK,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            style = TextStyle(lineHeight = 40.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)),
        )

        Box(
            Modifier
                .offset(SIDE.dp, (top + HERO_TOP).dp)
                .size((screenW - SIDE * 2).coerceAtLeast(0f).dp, HERO_HEIGHT.dp)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            Photo(hero.photo, 900, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height((HERO_HEIGHT * 0.55f).dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.5f)))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("NEW THIS WEEK", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Row(Modifier.fillMaxWidth()) {
                    Text(hero.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.weight(1f))
                    Text(hero.price, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
                }
            }
        }

        Row(
            Modifier
                .offset(SIDE.dp, (top + HERO_TOP + HERO_HEIGHT + 24).dp)
                .width((screenW - SIDE * 2).dp),
        ) {
            Text("Trending now", color = INK, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text("See all", color = MUTED, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
        }

        Row(
            Modifier
                .offset(SIDE.dp, trendingTop.dp)
                .size((screenW - SIDE * 2).coerceAtLeast(0f).dp, (screenH - trendingTop).coerceAtLeast(0f).dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
        ) {
            for (piece in listOf(PIECES[0], PIECES[8])) {
                Column(Modifier.width(tileWidth.dp)) {
                    Photo(piece.photo, 500, Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(20.dp)))
                    Text(piece.name, color = INK, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text(piece.price, color = MUTED, fontSize = 13.sp)
                }
            }
        }
    }
}

// MARK: - Sheet

/**
 * Pops a chip, a row or a tile in: from 0.84 scale and 10 dp lower on a spring, fading up over
 * 200 ms, after [delayMs]. Under reduced motion it only fades in.
 */
@Composable
private fun Pop(delayMs: Int, still: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val rise = remember { Animatable(if (still) 1f else 0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (still) {
            fade.animateTo(1f, tween(300, easing = InOutQuad))
        } else {
            launch {
                delay(delayMs.toLong())
                rise.animateTo(1f, PopSpring)
            }
            delay(delayMs.toLong())
            fade.animateTo(1f, tween(200, easing = InOutQuad))
        }
    }
    Box(
        modifier.graphicsLayer {
            val s = 0.84f + 0.16f * rise.value
            scaleX = s
            scaleY = s
            translationY = (1 - rise.value) * 10.dp.toPx()
            alpha = fade.value
        },
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Suggestions(still: Boolean, pick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = SIDE.dp, end = SIDE.dp, top = 12.dp)) {
        Label("RECENT", Modifier.padding(bottom = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RECENT.forEachIndexed { index, word ->
                Pop(220 + index * CHIP_STAGGER, still) {
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    Row(
                        Modifier
                            .height(36.dp)
                            .softShadow(INK.copy(alpha = 0.08f), blur = 3f, dy = 1f, radius = 18f)
                            .background(if (pressed) Color(0xFFEBE6DC) else CARD, RoundedCornerShape(18.dp))
                            .clickable(interactionSource = source, indication = null) { pick(word) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = MUTED, modifier = Modifier.size(15.dp))
                        Text(word, color = INK, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Label("POPULAR", Modifier.padding(top = 30.dp, bottom = 4.dp))
        POPULAR.forEachIndexed { index, name ->
            Pop(420 + index * CHIP_STAGGER, still) {
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (pressed) INK.copy(alpha = 0.04f) else Color.Transparent)
                        .drawBehind {
                            drawRect(INK.copy(alpha = 0.12f), Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Size(size.width, 1f))
                        }
                        .clickable(interactionSource = source, indication = null) { pick(name.substringBefore(' ')) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("%02d".format(index + 1), color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(22.dp))
                    Text(name, color = INK, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.NorthWest, contentDescription = null, tint = MUTED, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = modifier)
}

// MARK: - Results

/** A tile on the grid: where it sits, the place it popped in at, and whether it is fading out. */
private class GridEntry(val piece: Piece, var index: Int, val enterIndex: Int, var leaving: Boolean = false)

/**
 * The tiles on screen, kept a moment past the results they belong to so the ones that stop matching
 * can fade out where they stood.
 */
private class GridState {
    val entries = LinkedHashMap<String, GridEntry>()
    private var shown: List<Piece>? = null
    var version by mutableIntStateOf(0)

    fun sync(results: List<Piece>) {
        if (results == shown) return
        shown = results
        val ids = results.map { it.id }.toSet()
        for (entry in entries.values) if (entry.piece.id !in ids) entry.leaving = true
        results.forEachIndexed { index, piece ->
            val entry = entries[piece.id]
            if (entry == null || entry.leaving) entries[piece.id] = GridEntry(piece, index, index) else entry.index = index
        }
    }

    fun gone(entry: GridEntry) {
        if (entries[entry.piece.id] === entry) {
            entries.remove(entry.piece.id)
            version++
        }
    }
}

/**
 * The results: a count line and a two-column grid whose tiles pop in, fade out when they stop
 * matching and spring to their new places.
 */
@Composable
internal fun Results(query: String, tileWidth: Float, bottomInset: Float, still: Boolean, onDrag: () -> Unit) {
    val results = remember(query) { search(query) }
    val grid = remember { GridState() }
    grid.sync(results)
    @Suppress("UNUSED_VARIABLE")
    val version = grid.version
    val rows = (results.size + 1) / 2
    val gridHeight = if (rows == 0) 0f else rows * (TILE_HEIGHT + ROW_GAP) - ROW_GAP
    val dismiss = remember(onDrag) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) onDrag()
                return Offset.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(dismiss)
            .verticalScroll(rememberScrollState())
            .padding(start = SIDE.dp, end = SIDE.dp, bottom = (bottomInset + 40).dp),
    ) {
        val count = if (results.isEmpty()) "Nothing for “$query”" else "${results.size} ${if (results.size == 1) "piece" else "pieces"} for “$query”"
        Text(count, color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
        if (results.isEmpty()) {
            val fade = remember { Animatable(0f) }
            LaunchedEffect(Unit) { fade.animateTo(1f, tween(200, easing = InOutQuad)) }
            Text("Try a shape, a scent or a colour.", color = INK, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.graphicsLayer { alpha = fade.value })
        }
        Box(Modifier.size((tileWidth * 2 + GRID_GAP).dp, gridHeight.dp)) {
            for (entry in grid.entries.values.toList()) {
                key(entry) {
                    GridTile(entry, entry.index, entry.leaving, tileWidth, still, onGone = { grid.gone(entry) })
                }
            }
        }
    }
}

@Composable
private fun GridTile(entry: GridEntry, index: Int, leaving: Boolean, tileWidth: Float, still: Boolean, onGone: () -> Unit) {
    val density = LocalDensity.current
    val target = Offset((index % 2) * (tileWidth + GRID_GAP), (index / 2) * (TILE_HEIGHT + ROW_GAP))
    val place = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target) {
        if (place.targetValue != target) {
            if (still) place.snapTo(target) else place.animateTo(target, FlowSpring)
        }
    }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            fade.animateTo(0f, tween(160, easing = InOutQuad))
            onGone()
        }
    }
    Pop(
        min(entry.enterIndex, 6) * TILE_STAGGER,
        still,
        Modifier
            .offset { IntOffset((place.value.x * density.density).roundToInt(), (place.value.y * density.density).roundToInt()) }
            .graphicsLayer { alpha = fade.value },
    ) {
        Tile(entry.piece, tileWidth)
    }
}

@Composable
private fun Tile(piece: Piece, width: Float) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .width(width.dp)
            .graphicsLayer {
                val s = if (pressed) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = source, indication = null) {}
            .semantics { contentDescription = "${piece.name}, ${piece.price}" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Photo(piece.photo, 500, Modifier.padding(bottom = 8.dp).fillMaxWidth().height(TILE_PHOTO.dp).clip(RoundedCornerShape(20.dp)), fade = 160)
        Text(piece.name, color = INK, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(piece.kind, color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Text(piece.price, color = INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
