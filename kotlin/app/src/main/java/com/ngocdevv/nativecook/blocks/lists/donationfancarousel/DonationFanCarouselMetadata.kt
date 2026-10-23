package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Layout

/**
 * One of the two metadata blocks under the fan (`styles.metadata`): the donor's name over
 * "$ donation • time ago", centred across the stage. The two blocks sit on top of each other and
 * cross-fade with the page; [opacity] is read in the layer block, so a frame does not recompose it.
 */
@Composable
internal fun DonationFanCarouselMetadata(
    name: String,
    amount: String,
    time: String,
    geometry: DonationFanCarouselGeometry,
    opacity: () -> Double,
) {
    val scale = geometry.scale
    val metadata = Layout.Metadata
    Column(
        modifier = Modifier
            .offset(y = (metadata.Top * scale).dp)
            .requiredWidth(geometry.stageWidth.dp)
            .graphicsLayer { alpha = opacity().toFloat() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = name,
            style = donationFanCarouselTextStyle(
                weight = FontWeight.SemiBold,
                size = metadata.NameSize * scale,
                lineHeight = metadata.NameLine * scale,
                tracking = metadata.NameTracking,
                color = Colors.Ink,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        // `<Text><Text style={detailAmount}>$100 donation • </Text><Text style={detailTime}>10m ago</Text></Text>`.
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Colors.DetailAmount)) { append(amount) }
                withStyle(SpanStyle(color = Colors.Secondary)) { append(time) }
            },
            style = donationFanCarouselTextStyle(
                weight = FontWeight.Normal,
                size = metadata.DetailSize * scale,
                lineHeight = metadata.DetailLine * scale,
                tracking = metadata.DetailTracking,
                color = Colors.Secondary,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
