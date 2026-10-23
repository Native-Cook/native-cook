package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import android.os.SystemClock
import kotlin.math.sin

@Composable
internal fun GiftCardPickerPortfolio(
    width: Float,
    height: Float,
    dy: Float,
    bottomDelta: Float,
    visible: Boolean,
    state: GiftCardPickerMotionState,
    onGift: () -> Unit,
) {
    val portfolio = GiftCardPickerLayout.Portfolio
    val innerWidth = width - portfolio.Side * 2f
    val tileWidth = (innerWidth - portfolio.TileGap * 2f) / 3f

    Box(
        Modifier
            .fillMaxSize()
            .background(GiftCardPickerTheme.Page)
            .then(if (visible) Modifier else Modifier.semantics { hideFromAccessibility() }),
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = (portfolio.BackX - portfolio.BackSize / 2f).dp,
                    y = (portfolio.BackY - portfolio.BackSize / 2f + dy).dp,
                )
                .size(portfolio.BackSize.dp)
                .then(giftCardPickerSurfaceShadow(CircleShape))
                .background(Color.White, CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Back"
                },
            contentAlignment = Alignment.Center,
        ) {
            GiftCardPickerIcon(GiftCardPickerIconKind.Back, Modifier.size(20.dp))
        }

        Text(
            text = "Portfolio",
            modifier = Modifier.offset(y = (portfolio.LabelY - 11f + dy).dp).fillMaxWidth(),
            style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, color = GiftCardPickerTheme.Ink),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "USD 8,000.00",
            modifier = Modifier
                .offset(y = (portfolio.TotalY - 29f + dy).dp)
                .fillMaxWidth()
                .semantics { heading() },
            style = TextStyle(
                fontSize = portfolio.TotalSize.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
                color = GiftCardPickerTheme.Ink,
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = buildAnnotatedString {
                append("USD\u20090.00 · 0.00% · ")
                withStyle(SpanStyle(color = GiftCardPickerTheme.Ink)) { append("All time") }
            },
            modifier = Modifier.offset(y = (portfolio.ChangeY - 11f + dy).dp).fillMaxWidth(),
            style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, color = GiftCardPickerTheme.Mute),
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier
                .offset(x = portfolio.Side.dp, y = (portfolio.TilesTop + dy).dp)
                .width(innerWidth.dp)
                .height(portfolio.TileHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(portfolio.TileGap.dp),
        ) {
            GiftCardPickerPortfolioTile("Deposit", GiftCardPickerIconKind.Deposit, tileWidth)
            GiftCardPickerPortfolioTile("Analyze", GiftCardPickerIconKind.Analyze, tileWidth)
            GiftCardPickerPortfolioTile("Transfer", GiftCardPickerIconKind.Transfer, tileWidth)
        }

        GiftCardPickerCardSurface(
            modifier = Modifier
                .offset(x = portfolio.Side.dp, y = (portfolio.CashTop + dy).dp)
                .width(innerWidth.dp)
                .height(portfolio.CashHeight.dp),
        ) {
            Text(
                text = "Cash",
                modifier = Modifier.offset(x = 20.dp, y = 20.dp),
                style = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = GiftCardPickerTheme.Ink),
            )
            Text(
                text = "USD\u20098,000.00",
                modifier = Modifier.offset(x = 20.dp, y = 47.dp),
                style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum", color = GiftCardPickerTheme.Ink),
            )
            Box(
                modifier = Modifier
                    .offset(x = 20.dp, y = 85.dp)
                    .fillMaxWidth()
                    .padding(end = 40.dp)
                    .height(47.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GiftCardPickerTheme.Orange)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Get 3% annual interest for free"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Get 3% annual interest for free",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                    textAlign = TextAlign.Center,
                )
            }
        }

        GiftCardPickerGiftTile(
            modifier = Modifier
                .offset(x = portfolio.Side.dp, y = (portfolio.GiftTop + dy).dp)
                .width(innerWidth.dp)
                .height(portfolio.GiftHeight.dp),
            state = state,
            enabled = visible,
            onGift = onGift,
        )

        GiftCardPickerCardSurface(
            modifier = Modifier
                .offset(x = portfolio.Side.dp, y = (portfolio.TransactionsTop + dy).dp)
                .width(innerWidth.dp)
                .height(portfolio.TransactionsHeight.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Transactions",
                    style = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = GiftCardPickerTheme.Ink),
                )
                Text(
                    text = "4 in June",
                    style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = GiftCardPickerTheme.Ink),
                )
            }
        }

        GiftCardPickerCardSurface(
            modifier = Modifier
                .offset(x = portfolio.Side.dp, y = (portfolio.PromoTop + dy).dp)
                .width(innerWidth.dp)
                .height(160.dp),
        ) {
            Text(
                text = "How to grow your gift",
                modifier = Modifier.offset(x = 20.dp, y = 18.dp),
                style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3A3A3E)),
            )
        }

        Box(
            modifier = Modifier
                .offset(y = (portfolio.EdgeTop + dy).dp)
                .fillMaxWidth()
                .height(heightForEdge(height, portfolio.EdgeTop, dy).dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.76f)),
                    ),
                ),
        )

        GiftCardPickerTabRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(GiftCardPickerLayout.ScreenHeight - portfolio.TabTop - portfolio.TabHeight + bottomDelta).dp)
                .width(innerWidth.dp)
                .height(portfolio.TabHeight.dp),
        )
    }
}

@Composable
private fun GiftCardPickerPortfolioTile(
    label: String,
    icon: GiftCardPickerIconKind,
    width: Float,
) {
    Column(
        modifier = Modifier
            .width(width.dp)
            .height(GiftCardPickerLayout.Portfolio.TileHeight.dp)
            .then(giftCardPickerSurfaceShadow(RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp)))
            .background(GiftCardPickerTheme.Tile, RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp))
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GiftCardPickerIcon(icon, Modifier.size(28.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GiftCardPickerTheme.Ink),
        )
    }
}

@Composable
private fun GiftCardPickerCardSurface(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .then(giftCardPickerSurfaceShadow(RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp)))
            .background(GiftCardPickerTheme.Tile, RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp)),
        content = content,
    )
}

@Composable
private fun GiftCardPickerGiftTile(
    modifier: Modifier,
    state: GiftCardPickerMotionState,
    enabled: Boolean,
    onGift: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        state.setPress(state.pressGift, pressed, SystemClock.uptimeMillis())
        state.wakeFrames()
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val landing = ((state.commit.value - 2_120f / 2_540f) / (420f / 2_540f)).coerceIn(0f, 1f)
                val bounce = 1f + 0.03f * sin(landing * Math.PI.toFloat())
                val scale = (1f - (1f - GiftCardPickerMotionSpec.PressScale) * state.pressGift.value) * bounce
                scaleX = scale
                scaleY = scale
            }
            .then(giftCardPickerSurfaceShadow(RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp)))
            .clip(RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp))
            .border(
                width = 3.dp,
                color = GiftCardPickerTheme.Orange.copy(
                    alpha = 0.8f * sin(
                        ((state.commit.value - 2_120f / 2_540f) / (420f / 2_540f)).coerceIn(0f, 1f) * Math.PI.toFloat(),
                    ),
                ),
                shape = RoundedCornerShape(GiftCardPickerLayout.Portfolio.Radius.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onGift,
            )
            .semantics { contentDescription = "Choose your gift stock" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to GiftCardPickerTheme.Tile,
                            0.34f to GiftCardPickerTheme.Tile,
                            0.62f to Color(0xFFFFD2B4),
                            1f to Color(0xFFFF9A5E),
                        ),
                    ),
                ),
        )
        val tileArt = ImageBitmap.imageResource(com.ngocdevv.nativecook.R.drawable.giftcardpicker_tile_art)
        Image(
            painter = remember(tileArt) { BitmapPainter(tileArt, filterQuality = FilterQuality.High) },
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(GiftCardPickerLayout.Portfolio.ArtWidth.dp)
                .height(GiftCardPickerLayout.Portfolio.GiftHeight.dp),
            contentScale = ContentScale.FillBounds,
        )
        Text(
            text = "Choose your\ngift stock",
            modifier = Modifier.offset(x = 20.dp, y = 19.dp),
            style = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = GiftCardPickerTheme.Ink2),
        )
    }
}

@Composable
private fun GiftCardPickerTabRow(
    modifier: Modifier,
) {
    val layout = GiftCardPickerLayout.Portfolio
    val tabBarShape = RoundedCornerShape(layout.TabHeight.dp / 2)
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .width(layout.TabWidth.dp)
                .height(layout.TabHeight.dp)
                .clip(tabBarShape)
                .background(Color.White.copy(alpha = 0.72f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GiftCardPickerTab("Home", GiftCardPickerIconKind.Home, badge = true)
            GiftCardPickerTab("Pay", GiftCardPickerIconKind.Pay)
            GiftCardPickerTab("Invest", GiftCardPickerIconKind.Invest, active = true)
            GiftCardPickerTab("Invite", null, invite = true)
        }
        Box(
            modifier = Modifier
                .size(layout.Chat.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.72f))
                .semantics {
                    role = Role.Button
                    contentDescription = "Chat"
                },
            contentAlignment = Alignment.Center,
        ) {
            GiftCardPickerIcon(GiftCardPickerIconKind.Chat, Modifier.size(26.dp), Color(0xFF6D6D72))
        }
    }
}

@Composable
private fun GiftCardPickerTab(
    label: String,
    icon: GiftCardPickerIconKind?,
    active: Boolean = false,
    badge: Boolean = false,
    invite: Boolean = false,
) {
    Column(
        modifier = Modifier
            .width(70.75.dp)
            .height(GiftCardPickerLayout.Portfolio.TabHeight.dp - 8.dp)
            .clip(CircleShape)
            .background(if (active) Color(0x0F767680) else Color.Transparent)
            .semantics {
                role = Role.Tab
                contentDescription = label
                selected = active
            },
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            if (invite) {
                Text(
                    text = "\$100",
                    style = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6D6D72)),
                )
            } else if (icon != null) {
                GiftCardPickerIcon(
                    icon,
                    Modifier.size(if (icon == GiftCardPickerIconKind.Home) 26.dp else 26.dp),
                    if (active) GiftCardPickerTheme.Orange else Color(0xFF6D6D72),
                )
            }
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-5).dp)
                        .size(16.dp)
                        .background(GiftCardPickerTheme.Badge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("1", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) GiftCardPickerTheme.Orange else Color(0xFF6D6D72),
            ),
        )
    }
}

private fun giftCardPickerSurfaceShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    Modifier.shadow(
        elevation = 12.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor = Color.Black.copy(alpha = 0.05f),
    )

private fun heightForEdge(screenHeight: Float, edgeTop: Float, dy: Float): Float =
    (screenHeight - edgeTop - dy).coerceAtLeast(0f)
