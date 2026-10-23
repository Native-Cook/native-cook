package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import androidx.compose.ui.graphics.Color

/** Every color, size, baseline, and position from the accepted React Native theme. */
internal object FindFriendsInviteTheme {
    const val REF_W = 402f
    const val REF_TOP = 62f
    const val ASCENT = 0.795f
    const val DESCENT = 0.24f

    object Font {
        const val medium = "findfriendsinvite/findfriendsinvite_Anybody_Medium.ttf"
        const val semi = "findfriendsinvite/findfriendsinvite_Anybody_SemiBold.ttf"
        const val bold = "findfriendsinvite/findfriendsinvite_Anybody_Bold.ttf"
        const val extra = "findfriendsinvite/findfriendsinvite_Anybody_ExtraBold.ttf"
    }

    object Colors {
        val ink = Color(0xFF000000)
        val white = Color(0xFFFFFFFF)
        val card1 = Color(0xFFFFFFFF)
        val card2 = Color(0xFFF6F6F7)
        val placeholder = Color(0xFFC4C4C4)
        val buttonBlack = Color(0xFF000000)
        val planeFold = Color(0xFF1E5BEA)
        val planeInk = Color(0xFF0262FD)
        val avatarPink = Color(0xFFF298B9)
        val avatarGreen = Color(0xFFADEEA9)
        val avatarPurple = Color(0xFFCCC0FB)
        val avatarGrey = Color(0xFFD5D5D8)

        val pageStops = listOf(
            0f to Color(0xFFF6F6F6),
            67f to Color(0xFFF6F6F6),
            100f to Color(0xFFF4F4F4),
            133f to Color(0xFFEFEFEF),
            200f to Color(0xFFEBEBEB),
            267f to Color(0xFFEAEAEA),
            350f to Color(0xFFECECEC),
            400f to Color(0xFFEFEFEF),
            600f to Color(0xFFEFEFEF),
            700f to Color(0xFFEDEDED),
            767f to Color(0xFFECECEC),
            833f to Color(0xFFEAEAEA),
            874f to Color(0xFFE7E7E7),
        )

        val inviteStops = arrayOf(
            0f to Color(0xFF0262FD),
            0.6f to Color(0xFF0160FE),
            0.8f to Color(0xFF2F52FE),
            0.9f to Color(0xFF6F3BFF),
            0.98f to Color(0xFF9239F1),
            1f to Color(0xFF9638EE),
        )
    }

    object Space {
        const val cardMargin = 9f
        const val cardGap = 21f
        const val cardPadInvite = 32f
        const val cardPadCheck = 14.7f
        const val buttonGap = 8f
    }

    object Radius {
        const val card = 38f
        const val pill = 24f
        const val invite = 26.5f
        const val check = 21f
    }

    data class TextToken(
        val size: Float,
        val font: String,
        val letterSpacing: Float,
        val color: Color,
        val lineHeight: Float? = null,
    )

    object Type {
        val placeholder = TextToken(13.6f, Font.medium, 0f, Colors.placeholder)
        val h1 = TextToken(18f, Font.extra, 0f, Colors.ink)
        val h2 = TextToken(20.5f, Font.extra, 0f, Colors.ink)
        val body = TextToken(12.35f, Font.medium, 0f, Colors.ink, 17f)
        val bodyStrong = TextToken(12.6f, Font.semi, 0f, Colors.ink)
        val invite = TextToken(15.5f, Font.bold, 0f, Colors.white)
        val check = TextToken(12.85f, Font.semi, 0f, Colors.white)
    }

    data class RectToken(val x: Float, val y: Float, val width: Float, val height: Float)
    data class ShadowToken(val dy: Float, val blur: Float, val opacity: Float)
    data class CloseToken(val centerX: Float, val centerY: Float, val size: Float, val stroke: Float)
    data class BaselineToken(val x: Float, val baseline: Float)
    data class CupToken(val centerX: Float, val centerY: Float, val width: Float, val height: Float)
    data class PlaneToken(val x: Float, val y: Float, val size: Float)
    data class AvatarToken(
        val diameter: Float,
        val centerX: Float,
        val centerY: Float,
        val background: Color,
        val source: String,
        val head: Float,
        val dy: Float,
    )

    object Box {
        val pill = RectToken(23.3f, 61.3f, 310.3f, 48f)
        val pillShadow = ShadowToken(4f, 6f, 0.4f)
        val close = CloseToken(368.2f, 85.3f, 12.4f, 2f)
        val placeholder = BaselineToken(38.7f, 90.7f)
        val card1 = RectToken(0f, 125.3f, 0f, 274.3f)
        val cup = CupToken(157f + 260f / 6f, 152f + 342f / 6f, 260f / 3f, 342f / 3f)
        const val h1Baseline = 286.7f
        val invite = RectToken(0f, 314.7f, 0f, 53f)
        val plane = PlaneToken(138.3f, 333f, 17.7f)
        val inviteLabel = BaselineToken(167.7f, 347.3f)
        val card2 = RectToken(0f, 420.7f, 0f, 293.3f)
        val avatars = listOf(
            AvatarToken(36.3f, 136f, 485.2f, Colors.avatarPink, "h3", 0.6f, -0.01f),
            AvatarToken(67f, 182.7f, 470f, Colors.avatarGreen, "h1", 0.62f, 0f),
            AvatarToken(49f, 236.5f, 478.7f, Colors.avatarPurple, "h2", 0.85f, 0f),
            AvatarToken(25.5f, 271.3f, 485.2f, Colors.avatarGrey, "h5", 0.77f, -0.04f),
        )
        const val h2Baseline = 548.7f
        val bodyBaselines = floatArrayOf(594.7f, 611.7f, 628.7f)
        val lock = BaselineToken(228.3f, 14f)
        val check = RectToken(0f, 657f, 0f, 41.7f)
        const val checkLabelBaseline = 683f
    }

    object Copy {
        const val placeholder = "search for friends"
        const val h1 = "have friends with good taste?"
        const val invite = "invite them"
        const val h2 = "find your friends"
        val body = listOf(
            listOf("see who's already on mellow by checking your" to false),
            listOf("contacts. " to false, "we never upload or store your" to true),
            listOf("contacts. " to true),
        )
        const val lock = "\uD83D\uDD12"
        const val check = "check contacts"
        const val cupAccessibility = "Coffee cup"
    }
}
