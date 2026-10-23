package com.ngocdevv.nativecook.blocks.navigation.depthsidedrawer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Earbuds
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TabletMac
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rgba

private class Product(val label: String, val color: Color, val glyph: ImageVector)

private class Tab(val id: String, val label: String, val icon: ImageVector, val lit: ImageVector)

/** What the body shows: the shop front for the first destination, a shelf for any other. */
private data class Shown(val index: Int, val title: String)

private val Background = Color(0xFFFAFCFE)
private val Ink = Color(0xFF2B2B33)
private val Mute = Color(0xFFA9B0BA)
private val Outline = Color(0xFFEEF1F4)
private val TabIdle = Color(0xFFB0B5BF)
private val Alert = Color(0xFFF0443C)
private val Glyph = Color.White.copy(alpha = 0.8f)
private val BrandSky = listOf(Color(0xFF7ED8F5), Color(0xFF35A7F0))
private val Night = Color(0xFF23232B)

private const val MARGIN = 20
private const val GAP = 12
private const val TILE = 112
private const val TILE_RADIUS = 16
private const val BRAND_WIDTH = 240
private const val BRAND_HEIGHT = 130
private const val DEAL_HEIGHT = 150
private const val TABS = 50

// The old destination fades out while the new one fades in.
private const val SWAP = 250

private val Products = listOf(
    Product("Laptops", Color(0xFFF6B8C8), Icons.Outlined.Laptop),
    Product("Headphones", Color(0xFFFBC813), Icons.Outlined.Headphones),
    Product("Storage", Color(0xFFF4A6A6), Icons.Outlined.Usb),
    Product("Tablets", Color(0xFFBCF5C8), Icons.Outlined.TabletMac),
    Product("Watches", Night, Icons.Outlined.Watch),
    Product("Earbuds", Color(0xFFBCF5C8), Icons.Outlined.Earbuds),
    Product("Cameras", Color(0xFFF6B8C8), Icons.Outlined.PhotoCamera),
)

/** The bar's tabs. A tab opens the menu item that has its id. */
private val Bar = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("cart", "Cart", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab("wishlist", "Wishlist", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab("account", "Account", Icons.Outlined.Person, Icons.Filled.Person),
)

private fun type(size: Int, line: Int, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TileShape = RoundedCornerShape(TILE_RADIUS.dp)

@Composable
private fun Tile(product: Product, glyph: Int, modifier: Modifier) {
    Box(modifier.background(product.color, TileShape), contentAlignment = Alignment.Center) {
        Icon(product.glyph, contentDescription = null, tint = Glyph, modifier = Modifier.size(glyph.dp))
        BasicText(
            product.label,
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
            style = type(10, 13, Color.White, FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun Heading(title: String) {
    Row(
        Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = 20.dp, bottom = GAP.dp).fillMaxWidth().height(22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = type(17, 22, Ink, FontWeight.SemiBold), maxLines = 1)
        BasicText("View all", style = type(12, 16, Mute))
    }
}

/** A row of tiles from the left margin; what does not fit runs off the right edge. */
@Composable
private fun Shelf(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start, unbounded = true).padding(horizontal = MARGIN.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP.dp),
    ) { content() }
}

/** Two tiles that share the width. */
@Composable
private fun Halves(first: Product, second: Product, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = MARGIN.dp), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
        Tile(first, 56, Modifier.weight(1f).height(DEAL_HEIGHT.dp))
        Tile(second, 56, Modifier.weight(1f).height(DEAL_HEIGHT.dp))
    }
}

/** The shop front: three shelves, each running off the right edge or filling the width. */
@Composable
private fun Front() {
    Column {
        Heading("Recommended")
        Shelf {
            for (product in Products.take(4)) Tile(product, 48, Modifier.size(TILE.dp))
        }
        Heading("Popular Brand")
        Shelf {
            Box(
                Modifier.size(BRAND_WIDTH.dp, BRAND_HEIGHT.dp).background(Brush.verticalGradient(BrandSky), TileShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.TabletMac, contentDescription = null, tint = Glyph, modifier = Modifier.size(64.dp))
                BasicText(
                    "Northwind",
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                    style = type(15, 19, Color.White, FontWeight.SemiBold),
                )
            }
            Tile(Products[4], 56, Modifier.size(BRAND_WIDTH.dp, BRAND_HEIGHT.dp))
        }
        Heading("Daily Deals")
        Halves(Products[5], Products[6])
    }
}

/** Any other destination: its name over four tiles. */
@Composable
private fun Destination(title: String, index: Int) {
    val picks = List(4) { Products[(index * 2 + it) % Products.size] }
    Column {
        BasicText(
            title,
            Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = 20.dp),
            style = type(24, 30, Ink, FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            "${picks.size} items",
            Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = 2.dp, bottom = 16.dp),
            style = type(13, 18, Mute),
        )
        Halves(picks[0], picks[1], Modifier.padding(bottom = GAP.dp))
        Halves(picks[2], picks[3], Modifier.padding(bottom = GAP.dp))
    }
}

/**
 * The sample screen the drawer moves aside: a shop with a top bar, a search field, the content of
 * the chosen destination and a tab bar. Replace it with the app's own screens.
 *
 * @param title the destination's name; the page under it is the shop front when [index] is 0.
 * @param index the destination's place in the menu, which picks what its shelf holds.
 * @param active the destination's id: the tab with the same id is lit.
 * @param enabled false while the page is aside, when its buttons take no touches.
 */
@Composable
internal fun ShopPage(
    title: String,
    index: Int,
    active: String,
    accent: Color,
    top: Dp,
    bottom: Dp,
    enabled: Boolean,
    onMenu: () -> Unit,
    onTab: (String) -> Unit,
) {
    val barShadow = remember { SoftRoundRect(rgba(43, 43, 51, 0.06f), sigma = 8f) }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.padding(top = (top - 4.dp).coerceAtLeast(0.dp)).fillMaxWidth().height(44.dp).padding(start = 9.dp, end = MARGIN.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .size(44.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onMenu)
                    .semantics { contentDescription = "Menu" },
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) { Box(Modifier.size(22.dp, 2.dp).background(Ink, CircleShape)) }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Mail, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(16.dp))
            Box {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
                Box(Modifier.offset(2.dp, 1.dp).size(7.dp).background(Alert, CircleShape))
            }
        }

        Row(
            Modifier
                .padding(start = MARGIN.dp, end = MARGIN.dp, top = 5.dp)
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            BasicText("Find your items", Modifier.weight(1f), style = type(14, 18, Mute), maxLines = 1)
            Icon(Icons.Outlined.CropFree, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            Crossfade(Shown(index, title), animationSpec = tween(SWAP, easing = LinearEasing), label = "destination") { shown ->
                if (shown.index == 0) Front() else Destination(shown.title, shown.index)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val width = size.width / density
                    val height = size.height / density
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { barShadow.draw(it.nativeCanvas, 0f, -4f, width, height, 24f) }
                    }
                }
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(start = 8.dp, end = 8.dp, bottom = bottom)
                .height(TABS.dp),
        ) {
            for (tab in Bar) {
                val lit = tab.id == active
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Tab) { onTab(tab.id) }
                        .semantics { selected = lit },
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (lit) tab.lit else tab.icon, contentDescription = null, tint = if (lit) accent else TabIdle, modifier = Modifier.size(22.dp))
                    BasicText(tab.label, style = type(11, 14, if (lit) accent else TabIdle), maxLines = 1)
                }
            }
        }
    }
}
