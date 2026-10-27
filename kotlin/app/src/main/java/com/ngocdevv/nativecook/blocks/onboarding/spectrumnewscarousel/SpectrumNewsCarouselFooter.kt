package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The glyph of Ionicons' `logo-apple` (the icon of the React Native button), outlined from the
 * font on a 512-unit em with the y axis turned down so the top of the apple is at 0; it is 449 units tall.
 */
private const val APPLE_PATH =
    "M326 3Q334 1 337 0L340 0L341 6Q343 27 332 51Q321 75 300 90.5Q279 106 256 108L249 108L249 104Q248 95 250 84Q255 55 276 32.5Q297 10 326 3Z" +
        "M161 107Q161 107 161 107Q168 105 181 106Q198 106 226 116Q251 124 263.5 124Q276 124 299 116Q313 111 322 108.5Q331 106 348 106Q365 106 371 108" +
        "Q393 114 412 128Q416 131 425 140Q434 149 434 150Q434 150 427 155Q390 180 385 225Q382 254 395 281Q410 312 441 326Q441 326 448 329L445 336" +
        "Q432 364 413 392Q377 444 346 447Q330 449 307 439Q281 427 255 430Q240 431 220 439Q200 448 188 448Q155 448 122 404Q85 355 71 298" +
        "Q53 222 79 171Q91 147 114 129Q137 111 161 107Z"

private const val APPLE_EM = 512f
private const val APPLE_HEIGHT = 449f

/** `<Ionicons name="logo-apple" size={21} color="#FFFFFF" />`: the glyph on a 21 dp box, centred on its height. */
@Composable
private fun SpectrumNewsCarouselAppleLogo() {
    val apple = remember { PathParser().parsePathString(APPLE_PATH).toPath() }
    Canvas(Modifier.size(21.dp)) {
        val unit = size.width / APPLE_EM
        withTransform({
            translate(left = 0f, top = (size.height - APPLE_HEIGHT * unit) / 2f)
            scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero)
        }) {
            drawPath(apple, Color.White)
        }
    }
}

/**
 * The `footer` of the screen: the five page dots, the black Continue with Apple button and the fine print.
 * While the welcome is still opening ([interactive] false) it takes no touch and is hidden from accessibility.
 *
 * Each dot is 16 dp wide in the row, but its touch area and its dot sit as the React Native layout puts
 * them: the pressable inside is 30 dp wide (its padding), so the dot's centre is 15 dp from the left of
 * its slot, the row of 80 dp sits centred, and the last dot's area reaches 14 dp past the row.
 */
@Composable
internal fun SpectrumNewsCarouselFooter(
    page: Int,
    interactive: Boolean,
    reduced: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onGo: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        // `pager`: a row of five 48 dp tall dot buttons, 4 dp above the Apple button.
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Row(Modifier.align(Alignment.TopCenter).offset(x = 7.dp)) {
                SpectrumNewsCarouselPages.forEachIndexed { index, text ->
                    SpectrumNewsCarouselPressable(
                        onPress = { onGo(index) },
                        onPressIn = onPressIn,
                        onPressOut = onPressOut,
                        reduced = reduced,
                        pressedScale = 0.97f,
                        scaleMillis = if (reduced) 0 else 120,
                        dimsWhenReduced = false,
                        modifier = Modifier.width(if (index == SpectrumNewsCarouselPages.lastIndex) 30.dp else 16.dp).height(48.dp),
                        enabled = interactive,
                        label = "Page ${index + 1}: ${text.title}",
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = 12.dp)
                                .size(6.dp)
                                .background(if (page == index) Color(0xFF2B292E) else Color(0xFFDAD7D2), CircleShape),
                        )
                    }
                }
            }
        }
        // `appleButton`: 300 wide at most, at least 48 tall, radius 7, black, the logo and the label 6 apart.
        SpectrumNewsCarouselPressable(
            onPress = onContinue,
            onPressIn = onPressIn,
            onPressOut = onPressOut,
            reduced = reduced,
            pressedScale = 0.96f,
            scaleMillis = 150,
            dimsWhenReduced = true,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = interactive,
            label = "Continue with Apple",
        ) {
            Box(Modifier.matchParentSize().background(Color.Black, RoundedCornerShape(7.dp)))
            Row(
                Modifier.align(Alignment.Center).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpectrumNewsCarouselAppleLogo()
                SpectrumNewsCarouselBodyText(
                    "Continue with Apple",
                    size = 19f,
                    color = Color.White,
                    weight = FontWeight.Medium,
                    letterSpacing = -0.2f,
                    capScale = 1.6f,
                )
            }
        }
        // `finePrint`: 10 dp type on 13, 20 dp below the button and 14 above the bottom; the two links are underlined.
        val finePrint = buildAnnotatedString {
            append("By signing up for and using Spectrum, you agree to our\n")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("Terms of Service") }
            append(" & ")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("Privacy Policy") }
        }
        BasicText(
            text = finePrint,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
            style = TextStyle(
                color = Color(0xFF8A8A8A),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                lineHeightStyle = SpectrumNewsCarouselLineHeightStyle,
            ),
        )
    }
}
