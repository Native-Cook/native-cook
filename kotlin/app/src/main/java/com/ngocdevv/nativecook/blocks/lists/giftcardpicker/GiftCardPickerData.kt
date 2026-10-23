package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.R

internal object GiftCardPickerTheme {
    val Page = Color(0xFFF2F2F6)
    val Tile = Color(0xFFFBFBFD)
    val Ink = Color(0xFF111114)
    val Ink2 = Color(0xFF5C5C62)
    val Mute = Color(0xFF8A8A90)
    val Dim = Color.Black.copy(alpha = 0.19f)
    val SheetLight = Color(0xFFF5F5F9)
    val Orange = Color(0xFFFC4D00)
    val Caption = Color(0xFF74747A)
    val Terms = Color(0xFF6E6E73)
    val SkeletonBg = Color(0xFF19191C)
    val Skeleton = Color(0xFF28282E)
    val White = Color.White
    val Choose = Color(0xFFECEBF0)
    val ChooseInk = Color(0xFF1C1C1E)
    val Down = Color(0xFFFF4F86)
    val Up = Color(0xFF5EE06A)
    val Badge = Color(0xFFFF3B30)
}

internal object GiftCardPickerLayout {
    const val ScreenWidth = 402f
    const val ScreenHeight = 874f

    object Sheet {
        const val Top = 62f
        const val Radius = 30f
        const val CloseSize = 44f
        const val CloseLeft = 15f
        const val CloseTop = 16f
    }

    object Button {
        const val Side = 40f
        const val Height = 55f
        const val Bottom = 55f
        const val Radius = 20f
        const val Font = 17f
    }

    object Discover {
        const val TitleSize = 32f
        const val TitleLine = 41f
        val TitleY = floatArrayOf(405f, 446f)
        const val SubSize = 17f
        const val SubY = 488f
        const val TermsBottom = 143f
        const val CaptionBottom = 64f
        const val OrbCenterX = 201f
        const val OrbCenterY = 425f
        const val SphereCenterX = 201f
        const val SphereCenterY = 452f
        const val SphereRadius = 151.5f
        const val AmountSize = 46f
        const val AmountY = 431f
        val NowY = floatArrayOf(480f, 502f)
    }

    object Stock {
        const val TitleSize = 28f
        val TitleY = floatArrayOf(169f, 203f)
        const val CardTop = 328f
        const val CardWidth = 196f
        const val CardHeight = 260f
        const val CardRadius = 18f
        const val Spacing = 227f
        const val SideSkew = -3.3f
        const val SideScale = 0.88f
        const val Logo = 96f
        const val LogoY = 93f
        const val NameSize = 28f
        const val NameY = 178f
        const val PercentSize = 15f
        const val PercentY = 207f
        const val DotsY = 634f
        const val Dot = 7f
        const val DotGap = 16f
        const val DescriptionSize = 16f
        const val DescriptionLine = 21f
        const val DescriptionBottom = 168f
    }

    object Skeleton {
        val Title = listOf(157f to 165f, 191f to 210f)
        const val TitleHeight = 23f
        const val DotsY = 631f
        const val DotsWidth = 44f
        const val DotsHeight = 7f
        val Description = listOf(703f to 235f, 721f to 195f)
        const val DescriptionHeight = 6f
    }

    object Portfolio {
        const val BackX = 37f
        const val BackY = 84f
        const val BackSize = 44f
        const val LabelY = 146f
        const val TotalY = 189f
        const val TotalSize = 43f
        const val ChangeY = 235f
        const val TilesTop = 277f
        const val TileHeight = 87f
        const val TileGap = 20f
        const val Side = 20f
        const val Radius = 24f
        const val CashTop = 383f
        const val CashHeight = 150f
        const val GiftTop = 557f
        const val GiftHeight = 87f
        const val ArtWidth = 142f
        const val TransactionsTop = 665f
        const val TransactionsHeight = 85f
        const val PromoTop = 771f
        const val EdgeTop = 768f
        const val EdgeBlur = 22f
        const val TabTop = 793f
        const val TabHeight = 58f
        const val TabWidth = 291f
        const val Chat = 61f
    }

    const val TouchRetention = 16f
}

internal enum class GiftCardPickerMark {
    Rings,
    Petals,
    Ribbon,
    Lens,
    Wordmark,
}

internal data class GiftCardPickerStockData(
    val id: String,
    val name: String,
    val change: Int,
    val lines: List<String>,
    val tail: String,
    val mark: GiftCardPickerMark,
    val markBackground: Color,
    val brand: Color,
    val card: Int,
    val backdropTint: Color,
)

internal val GiftCardPickerStocks = listOf(
    GiftCardPickerStockData(
        id = "mira",
        name = "Mira",
        change = -18,
        lines = listOf("Connects billions through the world's", "most popular apps while using a"),
        tail = "dv",
        mark = GiftCardPickerMark.Rings,
        markBackground = Color(0xFF1F6FF2),
        brand = Color(0xFF1D5FD6),
        card = R.drawable.giftcardpicker_card_mira,
        backdropTint = Color(0xFF1B2A8A),
    ),
    GiftCardPickerStockData(
        id = "tilecraft",
        name = "Tilecraft",
        change = -16,
        lines = listOf("Equips the modern world with essential", "software and massive cloud syste"),
        tail = "m",
        mark = GiftCardPickerMark.Petals,
        markBackground = Color(0xFFF4F4F6),
        brand = Color(0xFF1F7A5C),
        card = R.drawable.giftcardpicker_card_tilecraft,
        backdropTint = Color(0xFF9AA25C),
    ),
    GiftCardPickerStockData(
        id = "reelbox",
        name = "Reelbox",
        change = -33,
        lines = listOf("Entertains millions of households with an", "endless library of hit movies, seri"),
        tail = "e",
        mark = GiftCardPickerMark.Ribbon,
        markBackground = Color(0xFF2A0D10),
        brand = Color(0xFFB3121B),
        card = R.drawable.giftcardpicker_card_reelbox,
        backdropTint = Color(0xFF3A0004),
    ),
    GiftCardPickerStockData(
        id = "voltex",
        name = "Voltex",
        change = 40,
        lines = listOf("Powers the brains of the world's most", "advanced AI with unmatched ha"),
        tail = "r",
        mark = GiftCardPickerMark.Lens,
        markBackground = Color(0xFF6FB51A),
        brand = Color(0xFF4A8A12),
        card = R.drawable.giftcardpicker_card_voltex,
        backdropTint = Color(0xFF2C5A10),
    ),
    GiftCardPickerStockData(
        id = "nova",
        name = "Nova",
        change = -12,
        lines = listOf("Facilitates millions of daily payments as", "the invisible backbone of global "),
        tail = "c",
        mark = GiftCardPickerMark.Wordmark,
        markBackground = Color(0xFF1D4FB0),
        brand = Color(0xFF1D4FB0),
        card = R.drawable.giftcardpicker_card_nova,
        backdropTint = Color(0xFF2C58C8),
    ),
)

internal const val GiftCardPickerAmount = "USD 3"
