package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val PanelColors = arrayOf(
    Color(0xFF06040F),
    Color(0xFF06040F),
    Color(0xFF2C1478),
    Color(0xFF4B2FD6),
    Color(0xFF6A45F5),
    Color(0xFFA48CFF),
)
private val BowlSolid = Color(0xFF06040F)
private val BowlClear = Color(0x0006040F)

private const val HERO_LABEL =
    "A sample payment card crosses a violet seam and becomes a token pattern. Drag across to scrub or use Replay below."

/** The panel reaches this far up under the nav (`marginTop: -60` with `paddingTop: 60`). */
private const val PANEL_OVERLAP = 60

/**
 * `heroPanel`: the violet panel with its bottom corners rounded by 32 dp. A gradient from the dark
 * page down to lilac, with a dark bowl (an ellipse fading out) under the card's lane so the violet
 * climbs the screen's edges; on it the hero with the card, the seam and the dust, and under that the
 * headline, the copy and the Talk to an Expert button.
 *
 * The gradient's stops sit at the lane, the card's bottom and the hero's bottom as fractions of
 * the panel's own height, in the panel's dp (the panel starts 60 dp under the nav and the hero 1 dp
 * below that).
 *
 * @param draggable whether a drag across the hero scrubs the pass
 * @param onReplay the Talk to an Expert button was pressed
 */
@Composable
internal fun CipherSeamPanel(
    session: CipherSeamSession,
    heroWidth: Float,
    heroHeight: Float,
    reduced: Boolean,
    draggable: Boolean,
    onReplay: () -> Unit,
) {
    val cardHeight = CipherSeamTimeline.CARD_HEIGHT * heroWidth / CipherSeamTimeline.DESIGN_WIDTH
    val heroBottom = 61f + heroHeight
    val cardBottom = 61f + heroHeight / 2f + cardHeight / 2f
    val laneTop = cardBottom - cardHeight - 30f
    Box(
        Modifier
            .layout { measurable, constraints ->
                // A negative top margin: the panel is laid out 60 dp higher than the flow puts it.
                val placeable = measurable.measure(constraints)
                val overlap = PANEL_OVERLAP.dp.roundToPx()
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .drawBehind {
                val panelHeight = size.height / density
                val locations = floatArrayOf(
                    0f,
                    laneTop / panelHeight,
                    (cardBottom + 20f) / panelHeight,
                    (heroBottom + 90f) / panelHeight,
                    0.88f,
                    1f,
                )
                // The stops have to rise; a very short panel would otherwise cross them.
                for (i in 1 until locations.size) locations[i] = maxOf(locations[i], locations[i - 1])
                val stops = Array(PanelColors.size) { locations[it] to PanelColors[it] }
                drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))

                // The bowl: an ellipse of the page's colour, `heroWidth` wide and 344 dp tall.
                val rx = heroWidth * 0.5f * density
                val ry = 172f * density
                withTransform({
                    translate(heroWidth / 2f * density, (cardBottom - 92f) * density)
                    scale(1f, ry / rx, pivot = Offset.Zero)
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to BowlSolid,
                            0.7f to BowlSolid,
                            1f to BowlClear,
                            center = Offset.Zero,
                            radius = rx,
                        ),
                        radius = rx,
                        center = Offset.Zero,
                    )
                }
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = PANEL_OVERLAP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(1.dp))
            PanelHero(session, heroWidth, heroHeight, cardHeight, reduced, draggable)
            HeroCopy(reduced, onReplay)
        }
    }
}

/**
 * `visual`: the hero, `heroWidth` by `heroHeight`, clipped. The dust lane, the card and token planes
 * (hidden from accessibility: they are one picture) and the seam over them. A drag across it scrubs
 * the pass.
 */
@Composable
private fun PanelHero(
    session: CipherSeamSession,
    heroWidth: Float,
    heroHeight: Float,
    cardHeight: Float,
    reduced: Boolean,
    draggable: Boolean,
) {
    val cardTop = (heroHeight - cardHeight) / 2f
    Box(
        Modifier
            .size(heroWidth.dp, heroHeight.dp)
            .clipToBounds()
            .semantics {
                contentDescription = HERO_LABEL
                role = Role.Image
            }
            .cipherSeamDrag(draggable, session, heroWidth),
    ) {
        CipherSeamDust(session.clock, heroWidth, heroHeight, cardTop, cardHeight, reduced)
        Box(
            Modifier
                .offset(y = cardTop.dp)
                .fillMaxWidth()
                .height(cardHeight.dp)
                .clearAndSetSemantics { },
        ) {
            CipherSeamPlane(session.clock, heroWidth, cardHeight, right = false, reduced = reduced)
            CipherSeamPlane(session.clock, heroWidth, cardHeight, right = true, reduced = reduced)
            CipherSeamPlane(session.clock, heroWidth, cardHeight, right = true, reduced = reduced, previous = true)
        }
        CipherSeamSeamLayer(session.clock, heroWidth, heroHeight, cardTop, cardHeight, reduced)
    }
}

/** `copy`: the headline, the two lines under it and the Talk to an Expert button, centred. */
@Composable
private fun HeroCopy(reduced: Boolean, onReplay: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, start = 10.dp, end = 10.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = "Flexible Payments Security",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(
                color = Color.White,
                fontSize = cipherSeamSp(28f, TITLE_TEXT_SCALE),
                lineHeight = cipherSeamSp(34f, TITLE_TEXT_SCALE),
                letterSpacing = cipherSeamFixedSp(-0.5f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
        BasicText(
            text = "Maximum protection, minimum compliance\nburden",
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(
                color = Color(0xFFE3DAFF),
                fontSize = cipherSeamSp(15f),
                lineHeight = cipherSeamSp(18f),
                textAlign = TextAlign.Center,
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
        BasicText(
            text = "Take control of your payments stack — easily tokenize cards, optimize margins, comply with PCI, avoid gateway lock-in, or spin-up card issuing programs.",
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 380.dp),
            style = TextStyle(
                color = Color(0xFFE6DEFF),
                fontSize = cipherSeamSp(11.4f),
                lineHeight = cipherSeamSp(20f),
                textAlign = TextAlign.Center,
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
        CipherSeamPressScale(
            label = "Replay the card transformation",
            reduced = reduced,
            onPress = onReplay,
            modifier = Modifier.padding(top = 14.dp),
            surface = Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "Talk to an Expert",
                    style = TextStyle(
                        color = Color(0xFF120A22),
                        fontSize = cipherSeamSp(14f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeightStyle = CipherSeamLineHeightStyle,
                    ),
                )
                CipherSeamGlyph(CipherSeamIcon.ChevronForward, 15f, Color(0xFF120A22))
            }
        }
    }
}

/** `maxFontSizeMultiplier={1.5}` of the headline. */
private const val TITLE_TEXT_SCALE = 1.5f
