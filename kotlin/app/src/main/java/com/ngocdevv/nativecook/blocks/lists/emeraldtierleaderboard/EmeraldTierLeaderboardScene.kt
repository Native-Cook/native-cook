package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val IDS = listOf(RecastActorId.ari, RecastActorId.milo, RecastActorId.juno, RecastActorId.theo)
private val LABELS = mapOf(
    RecastActorId.ari to "Ari Vale", RecastActorId.milo to "Milo Chen",
    RecastActorId.juno to "Juno Reed", RecastActorId.theo to "Theo Park",
)
private val INITIAL_SCORES = mapOf(
    RecastActorId.ari to 1000, RecastActorId.milo to 950, RecastActorId.juno to 900, RecastActorId.theo to 850,
)
private val WHITE = Color.White
private val RANK_COLORS = mapOf(1 to hex("#FFD66C"), 2 to hex("#BBBDB6"), 3 to hex("#BD8C66"), 4 to hex("#9EA39E"))
private val RANKS = mapOf(
    RecastActorId.ari to (1 to 2), RecastActorId.milo to (2 to 3), RecastActorId.juno to (3 to 4), RecastActorId.theo to (4 to 1),
)
private const val RANK_LINE = 40f
private val SPARKS = listOf(0, 1, 2, 3, 4, 5)

private fun finite(value: Float, fallback: Float = 0f) = if (value.isNaN() || value.isInfinite()) fallback else value
private fun unit(value: Float) = max(0f, min(1f, finite(value)))

// ---------------------------------------------------------------------------------------------
// EmeraldRecastSurface.tsx (the Skia path: four glass bodies in one bounded shader)
// ---------------------------------------------------------------------------------------------

/** Four glass bodies share a single bounded scene. Native labels/photos are separate siblings above it. */
@Composable
private fun RecastSurface(recast: State<RecastFrame>, solid: Boolean) {
    if (SUPPORTS_AGSL) {
        val shader = remember { RuntimeShader(RECAST_AGSL) }
        Canvas(Modifier.fillMaxSize()) {
            val frame = recast.value
            // recastWriteUniforms: motion, then rect and material per body in RECAST_SURFACE_ORDER.
            shader.setFloatUniform("motion", unit(frame.phase), unit(frame.response), unit(frame.responsePhase), if (frame.reduced) 1f else 0f)
            // gpuActive is always false here (no WebGPU path on Android), so the optics stay in this shader.
            shader.setFloatUniform("flags", if (solid) 1f else 0f, 1f)
            for (i in 0 until 4) {
                val body = frame.surface(RECAST_SURFACE_ORDER[i])
                val width = max(0f, finite(body.width))
                val height = max(0f, finite(body.height))
                shader.setFloatUniform("rect$i", finite(body.x), finite(body.y), width, height)
                shader.setFloatUniform(
                    "material$i",
                    max(0f, min(finite(body.radius), min(width / 2, height / 2))),
                    max(0f, min(15f, finite(body.depth))), unit(body.glow), 0f,
                )
            }
            drawShader(shader, RECAST_STAGE_WIDTH, RECAST_STAGE_HEIGHT)
        }
    } else {
        // Below API 33 (no AGSL): flat bodies in the same painter order, like the RN fallback.
        Canvas(Modifier.fillMaxSize()) {
            val frame = recast.value
            for (id in RECAST_SURFACE_ORDER) {
                val body = frame.surface(id)
                drawRoundRect(
                    color = hex("#0A1B18"),
                    topLeft = Offset(body.x * density, body.y * density),
                    size = Size(body.width * density, body.height * density),
                    cornerRadius = CornerRadius(body.radius * density),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// EmeraldRecastScene.tsx
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecastParticipant(id: RecastActorId, recast: State<RecastFrame>, winner: Boolean) {
    val i = id.ordinal
    val left by remember { derivedStateOf { recast.value.actors[i].rowMix > .5f } }
    val rank = if (winner) mapOf(RecastActorId.ari to 2, RecastActorId.milo to 3, RecastActorId.juno to 4, RecastActorId.theo to 1)[id]
    else mapOf(RecastActorId.ari to 1, RecastActorId.milo to 2, RecastActorId.juno to 3, RecastActorId.theo to 4)[id]
    val semanticScore = if (id == RecastActorId.theo && winner) 1050 else INITIAL_SCORES.getValue(id)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(70.dp)
                .zIndex(if (id == RecastActorId.theo) 12f else 10f)
                .graphicsLayer {
                    val a = recast.value.actors[i]
                    translationX = (a.cx - 35) * density
                    translationY = (a.cy - 35) * density
                    scaleX = a.size / 70
                    scaleY = a.size / 70
                }
                .semantics(mergeDescendants = true) { contentDescription = "${LABELS.getValue(id)}, rank $rank, $semanticScore points" },
        ) { EmeraldAvatar(id.name, 70f) }
        Box(
            Modifier
                .size(110.dp, 18.dp)
                .zIndex(14f)
                .graphicsLayer {
                    val a = recast.value.actors[i]
                    alpha = a.labelOpacity
                    translationX = a.nameX * density
                    translationY = a.nameY * density
                },
        ) {
            EmeraldText(
                LABELS.getValue(id), 12f, WHITE, Modifier.fillMaxWidth(), FontWeight.SemiBold, 17f, -.1f,
                if (left) TextAlign.Start else TextAlign.Center, maxLines = 1,
            )
        }
        Row(
            Modifier
                .size(54.dp, 21.dp)
                .zIndex(14f)
                .graphicsLayer {
                    val a = recast.value.actors[i]
                    alpha = a.labelOpacity
                    translationX = (a.scoreX - 27) * density
                    translationY = a.scoreY * density
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (id == RecastActorId.theo) {
                // The one score that counts up while Theo climbs.
                val score by remember { derivedStateOf { recast.value.actors[i].score } }
                EmeraldText(score.toString(), 14f, WHITE, Modifier.width(35.dp), lineHeight = 19f, align = TextAlign.End, tabular = true)
            } else {
                EmeraldText(INITIAL_SCORES.getValue(id).toString(), 14f, WHITE, lineHeight = 19f, tabular = true)
            }
            EmeraldText("ϟ", 19f, hex("#FFC846"), weight = FontWeight.Bold, lineHeight = 21f)
        }
        EmeraldText(
            "04", 12f, hex("#B5B9B5"),
            Modifier
                .zIndex(14f)
                .graphicsLayer {
                    val a = recast.value.actors[i]
                    alpha = a.rowMix * a.labelOpacity
                    translationX = 33 * density
                    translationY = 303 * density
                },
            lineHeight = 18f,
        )
    }
}

@Composable
private fun Laurel(id: RecastActorId, recast: State<RecastFrame>) {
    val (from, to) = RANKS.getValue(id)
    // The mirror transform of the RN style is overridden by the animated style's own transform, so it is not mirrored.
    EmeraldText(
        "❧", 25f, hex("#BDA448"),
        Modifier.graphicsLayer {
            val r = recast.value.roll
            val shown = if (from == 1) 1 - r else if (to == 1) r else 0f
            alpha = shown
            scaleX = 0.7f + 0.3f * shown
            scaleY = 0.7f + 0.3f * shown
        },
    )
}

/** Each plinth keeps its numeral and rolls it to the new order, like a mechanical counter. */
@Composable
private fun RecastRank(id: RecastActorId, recast: State<RecastFrame>) {
    val i = id.ordinal
    val (from, to) = RANKS.getValue(id)
    Row(
        Modifier
            .size(72.dp, 40.dp)
            .zIndex(8f)
            .graphicsLayer {
                val f = recast.value
                val b = f.surfaces[i]
                alpha = b.rankOpacity * (1 - f.actors[i].rowMix)
                translationX = (b.x + b.width / 2 - 36) * density
                translationY = (b.y + b.height / 2 - 20) * density
            },
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (from == 1 || to == 1) Laurel(id, recast)
        Box(Modifier.height(RANK_LINE.dp).widthIn(min = 22.dp).clipToBounds()) {
            Column(
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -recast.value.roll * RANK_LINE * density },
            ) {
                listOf(from, to).forEach { rank ->
                    Box(Modifier.height(RANK_LINE.dp), contentAlignment = Alignment.Center) {
                        EmeraldText(
                            rank.toString(), 29f, RANK_COLORS.getValue(rank), weight = FontWeight.Bold,
                            lineHeight = RANK_LINE, align = TextAlign.Center, tabular = true,
                        )
                    }
                }
            }
        }
        if (from == 1 || to == 1) Laurel(id, recast)
    }
}

private val RAIL = hex("#7DF0B526")

/** The boundary between the podium (promoted) and the list lights up where someone crosses it. */
@Composable
private fun PromotionLine(recast: State<RecastFrame>) {
    Box(Modifier.offset(19.dp, (PROMOTION_LINE_Y - 7).dp).size(355.dp, 14.dp).zIndex(9f)) {
        Box(Modifier.offset(0.dp, 6.5.dp).size((355 - 96).dp, 1.dp).background(RAIL))
        Flare(recast, actor = RecastActorId.theo, strength = 1f, core = hex("#B9FFD9"), glow = hex("#5CF2A8"))
        Flare(recast, actor = RecastActorId.juno, strength = .55f, core = hex("#E8F3EE"), glow = hex("#9DB5AA"))
        Box(Modifier.align(Alignment.CenterEnd).height(14.dp), contentAlignment = Alignment.Center) {
            EmeraldText("▲ PROMOTION ZONE", 7.5f, hex("#7DF0B5"), weight = FontWeight.Bold, letterSpacing = 1.1f)
        }
    }
}

@Composable
private fun Flare(recast: State<RecastFrame>, actor: RecastActorId, strength: Float, core: Color, glow: Color) {
    val i = actor.ordinal
    val shadow = remember(glow) { SoftRoundRect(glow.copy(alpha = .9f), sigma = 3f) }
    Box(
        Modifier
            .offset((-19).dp, 0.dp)
            .size(120.dp, 14.dp)
            .graphicsLayer {
                val a = recast.value.actors[i]
                val near = exp(-((a.cy - PROMOTION_LINE_Y) / 22).pow(2))
                alpha = near * strength
                translationX = (a.cx - 60) * density
                scaleX = 0.6f + near * 0.4f
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(120.dp, 2.dp)
                // shadowColor / shadowOpacity .9 / shadowRadius 6, no offset
                .drawBehind {
                    scale(density, Offset.Zero) { drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 0f, 120f, 2f, 1f) } }
                }
                .background(core, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun CrownSpark(index: Int, recast: State<RecastFrame>) {
    val angle = -PI.toFloat() / 2 + (index - 2.5f) * 0.5f
    Box(
        Modifier
            .offset((17 - 2.5f).dp, (18 - 2.5f).dp)
            .size(5.dp)
            .graphicsLayer {
                val p = recast.value.crown.sparkle
                val reach = 10 + 16 * (1 - (1 - p).pow(2))
                alpha = if (p > 0 && p < 1) sin(p * PI.toFloat()) else 0f
                translationX = cos(angle) * reach * density
                translationY = sin(angle) * reach * density
                rotationZ = 45f
                scaleX = 1 - p * 0.6f
                scaleY = 1 - p * 0.6f
            }
            .background(hex("#FFE7A3"), RoundedCornerShape(1.dp)),
    )
}

@Composable
internal fun EmeraldRecastScene(
    rank: State<EmeraldFrame>,
    solid: Boolean,
    onRendererStatus: ((String) -> Unit)?,
) {
    val recast = remember(rank) {
        derivedStateOf {
            val source = rank.value
            val next = recastFrame(source.progress, source.reduced)
            if (!source.allowResponse) next.withoutResponse() else next
        }
    }
    val winner by remember(recast) { derivedStateOf { recast.value.phase >= .90f } }
    val assets = LocalEmeraldAssets.current
    // Reported like the RN surface: 'skia-solid' for opaque materials, otherwise 'skia' (AGSL) or 'skia-fallback'.
    androidx.compose.runtime.LaunchedEffect(solid, SUPPORTS_AGSL) {
        onRendererStatus?.invoke(if (solid) "skia-solid" else if (SUPPORTS_AGSL) "skia" else "skia-fallback")
    }
    Box(
        Modifier
            .size(RECAST_STAGE_WIDTH.dp, RECAST_STAGE_HEIGHT.dp)
            .graphicsLayer {
                val source = rank.value
                translationY = (if (source.reduced) 0f else (source.progress - source.rawProgress) * 18) * density
            },
    ) {
        RecastSurface(recast, solid)
        PromotionLine(recast)
        IDS.forEach { RecastRank(it, recast) }
        IDS.forEach { RecastParticipant(it, recast, winner) }
        Box(
            Modifier
                .size(34.dp, 30.dp)
                .zIndex(18f)
                .graphicsLayer {
                    val c = recast.value.crown
                    alpha = c.opacity
                    translationX = c.x * density
                    translationY = c.y * density
                },
        ) {
            Box(
                Modifier
                    .size(34.dp, 30.dp)
                    .graphicsLayer {
                        val c = recast.value.crown
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                        rotationZ = c.rotation
                        scaleX = 1 + 0.09f * c.squash
                        scaleY = 1 - 0.13f * c.squash
                    },
            ) { ArtMaterial(assets?.crown, 34f, 30f) }
            SPARKS.forEach { CrownSpark(it, recast) }
        }
        Box(
            Modifier
                .size(68.dp, 19.dp)
                .zIndex(16f)
                .graphicsLayer {
                    val gain = recast.value.gain
                    alpha = gain.opacity
                    translationX = gain.x * density
                    translationY = gain.y * density
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            EmeraldText("+200 XP", 10f, hex("#B2F9D8"), weight = FontWeight.SemiBold, lineHeight = 15f)
        }
    }
}
