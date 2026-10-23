package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

private val PAGE = Color.White
private val INK = Color(0xFF14141F)
private val MUTE = Color(0xFF6B6B7B)
private val WELL = Color(0xFFF6F6F8)
private val PINK = Color(0xFFF2547D)
private val MARGIN = 20.dp
// React Native's hairline width doubled, on a 3x screen.
private val HAIRLINE = (2f / 3f).dp
private const val ME = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=120&h=120&q=80"
private val BANNER_SHAPE = RoundedCornerShape(22.dp)
private val BANNER_FILL = Brush.linearGradient(listOf(Color(0xFFF1EDFF), Color(0xFFE4DDFD)))

private class Interest(val label: String, val icon: ImageVector, val fill: Color, val ink: Color)

private val INTERESTS = listOf(
    listOf(
        Interest("Gaming", Icons.Filled.SportsEsports, Color(0xFFE1ECFF), Color(0xFF2F6FE0)),
        Interest("Skincare", Icons.Filled.AutoAwesome, Color(0xFFFFF4C4), Color(0xFFA67C00)),
        Interest("Hiking", Icons.Filled.Signpost, Color(0xFFFFEEDE), Color(0xFFD0661C)),
        Interest("Pizza", Icons.Filled.LocalPizza, Color(0xFFFFE1EC), Color(0xFFD6366B)),
    ),
    listOf(
        Interest("Pop", Icons.Filled.MusicNote, Color(0xFFFFE1EC), Color(0xFFD6366B)),
        Interest("Dogs", Icons.Filled.Pets, Color(0xFFDCF7E4), Color(0xFF1A9A4A)),
        Interest("Country", Icons.Filled.Headset, Color(0xFFFFE0E0), Color(0xFFD63636)),
        Interest("Space", Icons.Filled.Public, Color(0xFFDFF6FA), Color(0xFF1695A8)),
    ),
)

/**
 * The screen behind the sheet: a heading, the banner that opens the picks, interests and an empty
 * chat list. [bannerScale] is read while drawing.
 */
@Composable
internal fun PageBehind(
    top: Dp,
    bottom: Dp,
    title: String,
    banner: String,
    bannerCaption: String,
    bannerScale: () -> Float,
    onBanner: () -> Unit,
    onBannerPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestBanner by rememberUpdatedState(onBanner)
    val latestPress by rememberUpdatedState(onBannerPress)
    Box(modifier.fillMaxSize().background(PAGE).clipToBounds()) {
        Row(
            Modifier.offset(y = top + 6.dp).fillMaxWidth().padding(horizontal = MARGIN).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val context = LocalContext.current
            val request = remember(context) { ImageRequest.Builder(context).data(ME).crossfade(200).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(end = 4.dp).size(36.dp).clip(CircleShape).background(Color(0xFFE8C9A8)),
            )
            Text(title, Modifier.weight(1f), color = INK, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Round(Icons.Outlined.VerifiedUser)
            Round(Icons.Outlined.Tune)
        }

        Box(
            Modifier
                .offset(x = MARGIN, y = top + 62.dp)
                .fillMaxWidth()
                .padding(end = MARGIN * 2)
                .height(88.dp)
                .graphicsLayer {
                    val scale = bannerScale()
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            latestPress(true)
                            tryAwaitRelease()
                            latestPress(false)
                        },
                        onTap = { latestBanner() },
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(BANNER_FILL, BANNER_SHAPE)
                    .border(HAIRLINE, Color(0xFFDDD5FB), BANNER_SHAPE)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .dropShadow(CircleShape, Shadow(radius = 8.dp, color = INDIGO, offset = DpOffset(0.dp, 3.dp), alpha = 0.2f))
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Badge(48.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(banner, color = INK, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                    Text(bannerCaption, color = MUTE, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = INDIGO, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-6).dp)
                    .widthIn(min = 22.dp)
                    .height(22.dp)
                    .background(PINK, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("5", color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Row(
            Modifier.offset(y = top + 162.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Box(Modifier.size(16.dp, 6.dp).background(INDIGO, CircleShape))
            Box(Modifier.size(6.dp).background(Color(0xFFDCDCE4), CircleShape))
            Box(Modifier.size(6.dp).background(Color(0xFFDCDCE4), CircleShape))
        }

        SectionRow("Explore Interests", "See All", INDIGO, FontWeight.Bold, Modifier.offset(y = top + 190.dp))
        INTERESTS.forEachIndexed { index, row ->
            // Wider than the screen, as in React Native: the last pill runs off the right edge.
            Row(
                Modifier.offset(x = MARGIN, y = top + 228.dp + 44.dp * index).wrapWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (interest in row) {
                    Row(
                        Modifier.height(36.dp).background(interest.fill, CircleShape).padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(interest.icon, null, tint = interest.ink, modifier = Modifier.size(15.dp))
                        Text(interest.label, color = interest.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        SectionRow("Chats", "0 Requests", Color(0xFFA9A9B6), FontWeight.SemiBold, Modifier.offset(y = top + 334.dp))
        Column(
            Modifier
                .offset(x = MARGIN, y = top + 374.dp)
                .fillMaxWidth()
                .padding(end = MARGIN * 2)
                .background(WELL, RoundedCornerShape(24.dp))
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.padding(bottom = 6.dp).size(76.dp, 56.dp)) {
                Box(Modifier.size(48.dp).background(Color(0xFFECE8FB), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChatBubble, null, tint = Color(0xFFC9BFF7), modifier = Modifier.size(22.dp))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .dropShadow(CircleShape, Shadow(radius = 8.dp, color = INDIGO, offset = DpOffset(0.dp, 3.dp), alpha = 0.18f))
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Forum, null, tint = INDIGO, modifier = Modifier.size(24.dp))
                }
            }
            Text("No chats yet!", color = INK, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Text(
                "When you chat with new people on $title, they'll show up here",
                Modifier.padding(horizontal = 8.dp),
                color = MUTE,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .dropShadow(CircleShape, Shadow(radius = 10.dp, color = INDIGO, offset = DpOffset(0.dp, 5.dp), alpha = 0.3f))
                    .background(INDIGO, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Find New People", color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(PAGE)) {
            Box(Modifier.fillMaxWidth().height((1f / 3f).dp).background(Color(0xFFE6E6EC)))
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = bottom + 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Tab { Icon(Icons.Outlined.SentimentSatisfied, null, tint = MUTE, modifier = Modifier.size(26.dp)) }
                Tab {
                    Icon(Icons.Filled.Explore, null, tint = INDIGO, modifier = Modifier.size(27.dp))
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 2.dp).size(5.dp).background(INDIGO, CircleShape))
                }
                Tab { Icon(Icons.Outlined.Forum, null, tint = MUTE, modifier = Modifier.size(25.dp)) }
            }
        }
    }
}

/** Lets a row be as wide as its content, past the screen's edge. */
private fun Modifier.wrapWidth() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
}

@Composable
private fun Round(icon: ImageVector) {
    Box(Modifier.size(40.dp).background(WELL, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = INK, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionRow(text: String, trailing: String, trailingColor: Color, trailingWeight: FontWeight, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = MARGIN), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, Modifier.alignByBaseline(), color = INK, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
        Text(trailing, Modifier.alignByBaseline(), color = trailingColor, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = trailingWeight)
    }
}

@Composable
private fun Tab(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(64.dp, 40.dp), contentAlignment = Alignment.Center, content = content)
}
