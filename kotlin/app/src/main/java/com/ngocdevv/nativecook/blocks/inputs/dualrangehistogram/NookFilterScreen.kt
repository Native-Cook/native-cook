package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PAGE = Color(0xFFF6F1E8)
private val INK = Color(0xFF2A2420)
private val MUTE = Color(0xFF7C7267)
private val CTA_INK = Color(0xFFFBF6EE)
private const val SIDE = 20f
private val NO_AMENITIES = listOf(false, false, false)

private val CTA_TEXT = TextStyle(color = CTA_INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

/** The filter sheet of Nook, an invented rental search app, around the histogram. */
@Composable
fun NookFilterScreen(autoPlay: Boolean = true) {
    BlockSystemBars(darkIcons = true)
    val haptics = LocalHapticFeedback.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding().value
    val bottom = insets.calculateBottomPadding().value

    var placeType by remember { mutableIntStateOf(0) }
    var bedrooms by remember { mutableIntStateOf(0) }
    var amenities by remember { mutableStateOf(NO_AMENITIES) }
    var resetKey by remember { mutableIntStateOf(0) }
    val count = remember { mutableFloatStateOf(0f) }

    val bins = remember(placeType, bedrooms, amenities) {
        var share = 1.0
        amenities.forEachIndexed { i, on -> if (on) share *= AMENITIES[i].share }
        makeBins(placeType, bedrooms, share)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PAGE)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740f
        val contentW = min(width, 520f) - SIDE * 2
        val amenityRows = if (compact) 2 else 3
        val reserved = if (compact) 370f else 420f
        val chartHeight = max(96f, min(if (compact) 150f else 196f, height - top - bottom - reserved - amenityRows * 40f)).roundToInt()

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = (top + if (compact) 4f else 10f).dp, bottom = (max(bottom, 12f) + 4f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.width(contentW.dp).height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Filters",
                    style = TextStyle(color = INK, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Clear all",
                    style = TextStyle(color = MUTE, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        placeType = 0
                        bedrooms = 0
                        amenities = NO_AMENITIES
                        resetKey++
                    },
                )
            }

            Column(
                Modifier.width(contentW.dp).weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            ) {
                NookPlacePicker(width = contentW.dp, placeType = placeType, onPlaceType = { placeType = it })

                Column {
                    Text("Price per month", style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                    Text(
                        "Most homes in Harbour District are $1,900 to $2,800",
                        style = TextStyle(color = MUTE, fontSize = 13.sp),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    DualRangeHistogram(
                        bins = bins,
                        minValue = PRICE_MIN,
                        maxValue = PRICE_MAX,
                        step = PRICE_STEP,
                        minGap = PRICE_GAP,
                        defaultValue = PRICE_DEFAULT,
                        width = contentW.dp,
                        chartHeight = chartHeight.dp,
                        autoPlay = autoPlay,
                        count = count,
                        resetKey = resetKey,
                    )
                }

                NookControls(
                    width = contentW.dp,
                    bedrooms = bedrooms,
                    amenities = amenities,
                    amenityRows = amenityRows,
                    onBedrooms = { bedrooms = it },
                    onAmenity = { index, on ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        amenities = amenities.mapIndexed { i, v -> if (i == index) on else v }
                    },
                )
            }

            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            Row(
                Modifier
                    .width(contentW.dp)
                    .height(52.dp)
                    .graphicsLayer {
                        val s = if (pressed) 0.98f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(Color(42, 36, 32).copy(alpha = 0.3f), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(42, 36, 32).copy(alpha = 0.18f), 10.dp, 24.dp, 26.dp)
                    .background(INK, RoundedCornerShape(26.dp))
                    .clickable(interactionSource = press, indication = null, role = Role.Button) {}
                    .clearAndSetSemantics {
                        contentDescription = "Show homes"
                        role = Role.Button
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val value = { count.floatValue.toDouble() }
                val tick = { 0L }
                Text("Show ", style = CTA_TEXT, softWrap = false)
                RollingDigit(value, tick, place = 100, width = 10.5.dp, height = 22.dp, style = CTA_TEXT, hideLeading = true)
                RollingDigit(value, tick, place = 10, width = 10.5.dp, height = 22.dp, style = CTA_TEXT, hideLeading = true)
                RollingDigit(value, tick, place = 1, width = 10.5.dp, height = 22.dp, style = CTA_TEXT, lowest = true)
                Text(" homes", style = CTA_TEXT, softWrap = false)
            }
        }
    }
}
