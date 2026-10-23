package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Optical left margin, shared by the morphing line and the tail. */
private val LINE_INSET = 14.dp

/** The tail's baseline below the top of its line, as a fraction of the size. */
private const val TAIL_BASELINE = 0.73f

/**
 * The ball's name (BallWordmark.tsx), the largest thing on the screen and printed behind the
 * ball: the identity in full white, and "BALL" under it as a ghost the ball stands in. Only the
 * first line morphs, letter by letter; the tail is the same word on every ball.
 *
 * `size` is the display size and `width` the canvas for the morph (the screen's width).
 * `typeScale` is the capped font scale, which the tail follows and the morphing line does not.
 */
@Composable
internal fun BallWordmark(
    hero: String,
    tail: String,
    size: Dp,
    width: Dp,
    typeScale: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "$hero $tail"
            heading()
        },
    ) {
        MorphingText(
            text = hero,
            fitTexts = WORDMARK_HEROES,
            fontSize = size,
            // The optical tightening a display face needs at this size.
            tracking = size * -0.037f,
            width = width,
            // Room for the glyphs' own ascent and the rise they enter from.
            height = size * 1.34f,
            paddingLeft = LINE_INSET,
            color = Color.White,
            reducedMotion = reducedMotion,
        )
        BasicText(
            text = tail,
            // Pulled up under the first line, so the two read as one block. The reference sets it
            // in a line box 1.02 × the size, where iOS puts the baseline 0.73 × the size down.
            modifier = Modifier
                .offset(y = size * -0.44f)
                .padding(start = LINE_INSET)
                .paddingFromBaseline(top = size * (TAIL_BASELINE * typeScale)),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.15f),
                fontSize = (size.value * typeScale).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3f * typeScale).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
