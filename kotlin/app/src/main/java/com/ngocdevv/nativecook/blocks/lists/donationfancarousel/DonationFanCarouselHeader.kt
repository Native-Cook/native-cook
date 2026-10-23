package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Layout

/**
 * `styles.header` in the React Native block: "Latest donations" on the left and the counter on the
 * right, in one row that is inset 59 dp on both sides.
 */
@Composable
internal fun DonationFanCarouselHeader(geometry: DonationFanCarouselGeometry) {
    val scale = geometry.scale
    val header = Layout.Header
    Row(
        modifier = Modifier
            .offset((header.Side * scale).dp, (header.Top * scale).dp)
            .width(geometry.stageWidth.dp - (header.Side * scale * 2f).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "Latest donations",
            style = donationFanCarouselTextStyle(
                weight = FontWeight.Medium,
                size = header.FontSize * scale,
                lineHeight = header.LineHeight * scale,
                tracking = header.HeadingTracking,
                color = Colors.Ink,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        BasicText(
            text = "04/24",
            // `top: -1.3` moves the counter up without changing the row.
            modifier = Modifier.offset(y = header.CounterTop.dp),
            style = donationFanCarouselTextStyle(
                weight = FontWeight.Medium,
                size = header.CounterSize * scale,
                lineHeight = header.LineHeight * scale,
                tracking = header.CounterTracking,
                color = Colors.Ink,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
