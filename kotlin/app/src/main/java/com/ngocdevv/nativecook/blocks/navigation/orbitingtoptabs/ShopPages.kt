package com.ngocdevv.nativecook.blocks.navigation.orbitingtoptabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.min

private val Ink = Color(0xFF111111)
private val Plate = Color(0xFFE9E9E6)
private const val MARGIN = 20f

// How far a picture trails its page at most, in dp; a photo is this much wider than the page for it.
internal const val PARALLAX = 60f

private val Olive = SneakerColors(Color(0xFF5E6B4A), Color(0xFF4A553A), Color(0xFF1E1E1C), Color(0xFFF4F2EA), Color(0xFF1B1B1B))
private val Night = SneakerColors(Color(0xFF2C2C30), Color(0xFF1D1D20), Color(0xFF8C8C92), Color(0xFFF4F2EA), Color(0xFF1B1B1B))
private val Rust = SneakerColors(Color(0xFF6B3A2E), Color(0xFF4F2A21), Color(0xFFE9DCC8), Color(0xFFF4F2EA), Color(0xFFC9A26B))
private val Rose = SneakerColors(Color(0xFFF2BCCB), Color(0xFFE79DB3), Color(0xFFFFFFFF), Color(0xFFF8F4EE), Color(0xFFC9A26B))
private val Slate = SneakerColors(Color(0xFF5B6770), Color(0xFF46515A), Color(0xFFE8E4DA), Color(0xFFF4F2EA), Color(0xFF26282B))

private const val ARCHIVE_PHOTO = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=920&h=880&q=80"
private const val TRAIL_PHOTO = "https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=920&h=800&q=80"
private const val ARCHIVE_TEXT =
    "ARCHIVE REVIVAL pairs washed denim, hooded fleece and track pants from the nineties with new cuts and trims: old favourites and fresh pieces in one wardrobe that talks across the decades."
private const val TRAIL_TEXT =
    "The Ridge Runner has landed. A lugged sole that grips, a ripstop upper and a pull tab at the heel, in two colours: moss or slate. Which one are you taking up the hill?"

private val ShelfShoes = listOf(Night to "2019", Rust to "2020", Rose to "2020", Slate to "2021")
private const val SHELF_SHOE = 96f
private const val SHELF_STEP = 136f

// The first shoe of the shelf is cut by the left edge.
private const val SHELF_START = -38f
private val NewIn = listOf(Slate to "RIDGE RUNNER", Rose to "COURT LOW", Rust to "GUM SOLE 74", Olive to "FIELD TRAINER")

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink, spacing: Float = 0f, underline: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textDecoration = if (underline) TextDecoration.Underline else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Small capitals, as on the tabs. */
internal fun caps(color: Color = Ink, underline: Boolean = false) = type(9, 12, FontWeight.SemiBold, color, spacing = 1f, underline = underline)

/** A photo behind a window as wide as the page. The photo is wider, and trails the page by [drift] dp. */
@Composable
private fun Photo(url: String, width: Float, height: Float, drift: () -> Float, content: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).build() }
    Box(Modifier.fillMaxWidth().height(height.dp).clipToBounds().background(Plate)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width((width + PARALLAX).dp)
                .height(height.dp)
                .graphicsLayer { translationX = (drift() - PARALLAX).dp.toPx() },
        )
        content()
    }
}

@Composable
private fun Heading(title: String, link: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MARGIN.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = caps())
        BasicText(link, style = type(10, 13))
    }
}

@Composable
private fun Story(text: String) {
    BasicText(text, Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = 12.dp), style = type(15, 18, FontWeight.SemiBold, spacing = -0.3f))
}

@Composable
private fun NewInPage(width: Float) {
    val cell = (width - MARGIN * 3f) / 2f
    Column(Modifier.padding(top = 24.dp)) {
        Heading("THIS WEEK", "8 Styles")
        Column(Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            for (pair in NewIn.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MARGIN.dp)) {
                    for ((colors, name) in pair) {
                        Column(Modifier.width(cell.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Sneaker(cell - 16f, colors)
                            BasicText(name, Modifier.padding(top = 10.dp), style = caps(), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowsePage(width: Float, height: Float, drift: () -> Float) {
    val shoe = min(300f, width - 60f)
    Sneaker(
        shoe,
        Olive,
        Modifier
            .offset(((width - shoe) / 2f).dp, (height * 0.28f - shoe * SNEAKER_RATIO / 2f).dp)
            .graphicsLayer { translationX = drift().dp.toPx() },
    )
    Heading("JUST DROPPED", "Shop All", Modifier.offset(y = (height * 0.61f).dp))
    ShelfShoes.forEachIndexed { slot, (colors, year) ->
        Column(
            Modifier.offset((SHELF_START + slot * SHELF_STEP).dp, (height * 0.73f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sneaker(SHELF_SHOE, colors)
            BasicText(year, Modifier.padding(top = 14.dp), style = type(8, 10))
        }
    }
}

@Composable
private fun ArchivePage(width: Float, height: Float, drift: () -> Float) {
    Column {
        Photo(ARCHIVE_PHOTO, width, height * 0.6875f, drift) {
            Box(Modifier.fillMaxSize().padding(start = MARGIN.dp, bottom = 14.dp), contentAlignment = Alignment.BottomStart) {
                BasicText("SHOP NOW", style = caps(Color.White, underline = true))
            }
        }
        Story(ARCHIVE_TEXT)
    }
}

@Composable
private fun TrailPage(width: Float, height: Float, drift: () -> Float) {
    val shoe = min(300f, width - 60f)
    Column {
        Photo(TRAIL_PHOTO, width, height * 0.625f, drift)
        Story(TRAIL_TEXT)
    }
    // The top of the next shoe, cut by the bar.
    Sneaker(shoe, Slate, Modifier.offset(((width - shoe) / 2f).dp, (height - 46f).dp))
}

/**
 * The sample pages under the tabs: a shop's front, two stories and what is new. Replace them with
 * the app's own.
 *
 * @param index which of the four sample pages this is.
 * @param drift how far the page's picture trails its text while the page comes in, in dp; read while drawing.
 */
@Composable
internal fun ShopPage(index: Int, width: Float, height: Float, drift: () -> Float) {
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.White)) {
        when (index) {
            0 -> NewInPage(width)
            1 -> BrowsePage(width, height, drift)
            2 -> ArchivePage(width, height, drift)
            else -> TrailPage(width, height, drift)
        }
    }
}
