package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @param glasses Round glasses on the face.
 * @param photo A portrait; without one, a flat drawn face stands in.
 * @param tags A few more interests, shown as chips.
 */
data class Profile(
    val name: String,
    val rating: String,
    val details: String,
    val likes: String,
    val match: Int,
    val skin: Color,
    val hair: Color,
    val shirt: Color,
    val glasses: Boolean = false,
    val photo: String? = null,
    val tags: List<String> = emptyList(),
)

private val MUTE = Color(0xFF6B6B7B)
private val INK = Color(0xFF14141F)
private val GREEN = Color(0xFF22C55E)
private val WELL = Color(0xFFF4F4F6)
private const val AVATAR = 108f
private const val RING = AVATAR + 12f
// React Native's hairline width doubled, on a 3x screen.
private val HAIRLINE = (2f / 3f).dp
private val CARD_SHAPE = RoundedCornerShape(28.dp)
private val RING_FILL = Brush.linearGradient(listOf(Color(0xFF8F7BEF), INDIGO, Color(0xFFF2547D)))

/** A flat face: shoulders, a head, hair and, for some, round glasses, on a pale disc. */
@Composable
private fun Avatar(profile: Profile) {
    Canvas(Modifier.size(AVATAR.dp)) {
        // Drawn in a 96-dp box, scaled to the avatar's size.
        scale(density * AVATAR / 96f, pivot = Offset.Zero) {
            drawCircle(Color(0xFFEEEEF6), 48f, Offset(48f, 48f))

            // Shoulders: two curves and the minor arc of a 48-dp circle across the bottom.
            val shirt = Path().apply {
                moveTo(14f, 90f)
                cubicTo(16f, 70f, 30f, 64f, 48f, 64f)
                cubicTo(66f, 64f, 80f, 70f, 82f, 90f)
                val centreY = 90f - sqrt(48f * 48f - 34f * 34f)
                val start = atan2(90f - centreY, 34f)
                val end = atan2(90f - centreY, -34f)
                for (step in 1..24) {
                    val angle = start + (end - start) * step / 24f
                    lineTo(48f + cos(angle) * 48f, centreY + sin(angle) * 48f)
                }
                close()
            }
            drawPath(shirt, profile.shirt)

            val neck = Path().apply {
                moveTo(42f, 56f)
                lineTo(54f, 56f)
                lineTo(54f, 68f)
                cubicTo(54f, 70f, 42f, 70f, 42f, 68f)
                close()
            }
            drawPath(neck, profile.skin)
            drawCircle(profile.skin, 19f, Offset(48f, 42f))

            val hair = Path().apply {
                moveTo(28f, 40f)
                cubicTo(26f, 24f, 38f, 16f, 50f, 17f)
                cubicTo(62f, 18f, 70f, 26f, 67f, 40f)
                cubicTo(63f, 33f, 58f, 30f, 52f, 31f)
                cubicTo(44f, 33f, 36f, 31f, 28f, 40f)
                close()
            }
            drawPath(hair, profile.hair)

            for (x in listOf(41f, 55f)) {
                drawCircle(INK, 2f, Offset(x, 44f))
                if (profile.glasses) drawCircle(INK, 6f, Offset(x, 44f), style = Stroke(1.5f))
            }
            val mouth = Path().apply {
                moveTo(43f, 53f)
                cubicTo(46f, 56f, 50f, 56f, 53f, 53f)
            }
            drawPath(mouth, INK, style = Stroke(1.5f, cap = StrokeCap.Round))
        }
    }
}

/** One profile card: the person, what they like, and the Chat button. */
@Composable
internal fun ProfileCard(profile: Profile, chatLabel: String, modifier: Modifier = Modifier, onChat: (() -> Unit)? = null) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .dropShadow(CARD_SHAPE, Shadow(radius = 24.dp, color = Color(0xFF2A1D7A), offset = DpOffset(0.dp, 12.dp), alpha = 0.1f))
            .background(Color.White, CARD_SHAPE)
            .border(HAIRLINE, Color(0xFFECECF2), CARD_SHAPE),
    ) {
        Column(Modifier.fillMaxSize().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(RING.dp).background(RING_FILL, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size((AVATAR + 6f).dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    if (profile.photo != null) {
                        val context = LocalContext.current
                        val request = remember(profile.photo, context) { ImageRequest.Builder(context).data(profile.photo).crossfade(200).build() }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(AVATAR.dp).clip(CircleShape).background(Color(0xFFEEEEF6)),
                        )
                    } else {
                        Avatar(profile)
                    }
                }
            }
            Row(
                Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(profile.name, color = INK, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                Row(
                    Modifier.height(20.dp).background(GREEN, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(9.dp))
                    Text(profile.rating, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Details(profile.details)
                Icon(Icons.Outlined.LocationOn, null, tint = MUTE, modifier = Modifier.size(13.dp))
                Details("3 km away")
            }
            Row(
                Modifier
                    .padding(top = 18.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(WELL, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).background(Color(0xFFECE8FB), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SportsFootball, null, tint = INDIGO, modifier = Modifier.size(16.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Likes ")
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = INK)) { append(profile.likes) }
                        },
                        color = MUTE,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                    )
                    Text(
                        buildAnnotatedString {
                            append("You're ")
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = GREEN)) { append("${profile.match}%") }
                            append(" compatible")
                        },
                        color = MUTE,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            if (profile.tags.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (tag in profile.tags) {
                        Box(
                            Modifier.height(30.dp).border(HAIRLINE, Color(0xFFE4E4EC), CircleShape).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tag, color = Color(0xFF45455A), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 18.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .dropShadow(CircleShape, Shadow(radius = 12.dp, color = INDIGO, offset = DpOffset(0.dp, 6.dp), alpha = 0.35f))
                    .background(INDIGO, CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChat?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Text(chatLabel, color = Color.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Corner { Icon(Icons.Filled.MoreHoriz, null, tint = MUTE, modifier = Modifier.size(16.dp)) }
            Corner { Icon(Icons.Filled.Close, null, tint = MUTE, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
private fun Details(text: String) {
    Text(text, Modifier.padding(end = 4.dp), color = MUTE, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Corner(icon: @Composable () -> Unit) {
    Box(Modifier.size(32.dp).background(WELL, CircleShape), contentAlignment = Alignment.Center) { icon() }
}
