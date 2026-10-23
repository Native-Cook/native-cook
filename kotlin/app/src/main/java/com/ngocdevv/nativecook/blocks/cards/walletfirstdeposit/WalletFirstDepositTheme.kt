package com.ngocdevv.nativecook.blocks.cards.walletfirstdeposit

import androidx.compose.ui.graphics.Color

internal const val REF_W = 1206f
internal const val REF_INSET = 186f
internal const val REF_PT = 402f
internal const val CONTENT_BOTTOM = 2780f
internal const val ASCENT = 0.953f

internal val Page = Color.White
internal val Card = Color(0xFFF6F6F8)
internal val Pill = Color(0xFFEEEEEF)
internal val Ink = Color.Black
internal val Secondary = Color(0xFF8E8E93)
internal val PillText = Color(0xFF6D6D72)
internal val Label = Color(0xFF9C9CA0)
internal val Axis = Color(0xFFC5C5C9)
internal val Decimals = Color(0xFFBEBEC0)
internal val Green = Color(0xFF1F9F3B)
internal val Track = Color(0xFFDADADC)
internal val BarStart = Color(0xFF1455BA)
internal val BarEnd = Color(0xFF1A98E6)
internal val Dot = Color(0xFFD9474C)
internal val Hairline = Color(0xFFE7E7EA)
internal val Orange = Color(0xFFEF8838)
internal val Peach = Color(0xFFF7DCC8)
internal val PlotTop = Color(0xFFE3E3E5)
internal val ForecastTop = Color(0xFFE5E5E7)
internal val Dash = Color(0xFFCFCFD3)
internal val Cafe = Color(0xFF0B7449)
internal val Nut = Color(0xFFC4C4C6)

internal data class BoxRef(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float)
internal data class PointRef(val x: Float, val y: Float)

internal val Banner = BoxRef(22.25f, 745f, 1159.25f, 313.8f, 48f)
internal val TxCard = BoxRef(22.25f, 1051.7f, 1159.25f, 436.2f, 72f)

internal fun actionBox(index: Int) = BoxRef(22.25f + index * 388.85f, 1234.8f, 381.6f, 414.6f, 81f)
internal fun payeeBox(index: Int) = BoxRef(22.25f + index * 291.3f, 1665f, 285.3f, 307f, 72f)

internal val AvatarOrb = listOf(Color(0xFF0A0596), Color(0xFF2336B5), Color(0xFF4196D9), Color(0xFF70C4E7), Color(0xFFC3EBFC))
internal val DieterOrb = listOf(Color(0xFF0A63A8), Color(0xFF1A82C1), Color(0xFF55B9D0), Color(0xFF97D6C5), Color(0xFFEAF8B4))
internal val PayeeOrbs = listOf(
    listOf(Color(0xFF08A606), Color(0xFF5E9E78), Color(0xFFBB8DDC), Color(0xFFDEAEDB), Color(0xFFF7E5C1)),
    listOf(Color(0xFF7FA012), Color(0xFF77B07A), Color(0xFF63BBC6), Color(0xFF94D7BD), Color(0xFFE5FAB6)),
    listOf(Color(0xFFB02BAF), Color(0xFF8C3CB9), Color(0xFF3F60C9), Color(0xFF759EBE), Color(0xFFD8EEB5)),
    listOf(Color(0xFFA70300), Color(0xFFC01D0D), Color(0xFFF84535), Color(0xFFF28C4B), Color(0xFFE8EA7E)),
)

internal val ChartLine = listOf(
    PointRef(72f, 2569f), PointRef(146f, 2569f), PointRef(244f, 2486f), PointRef(262f, 2475f), PointRef(282f, 2466f),
    PointRef(314f, 2466f), PointRef(350f, 2430f), PointRef(366f, 2423f), PointRef(390f, 2421f), PointRef(400f, 2418f),
    PointRef(416f, 2418f), PointRef(464f, 2408f), PointRef(470f, 2407f),
)
internal val ChartForecast = listOf(
    PointRef(470f, 2434f), PointRef(608f, 2433f), PointRef(638f, 2420f), PointRef(722f, 2422f), PointRef(736f, 2411f),
    PointRef(758f, 2403f), PointRef(810f, 2399f), PointRef(826f, 2395f), PointRef(854f, 2384f), PointRef(950f, 2383f),
    PointRef(962f, 2375f), PointRef(998f, 2355f), PointRef(1058f, 2344f), PointRef(1070f, 2336f), PointRef(1134f, 2330f),
)
internal val ChartTrend = listOf(PointRef(72f, 2569f), PointRef(146f, 2569f), PointRef(250f, 2492f), PointRef(318f, 2484f), PointRef(352f, 2442f), PointRef(470f, 2434f))
