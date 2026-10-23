package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

/** Value-for-value layout tokens from `event-pass-share/theme.ts`, in reference points. */
internal object EventPassShareTheme {
    data class RectToken(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    data class HandleToken(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    data class SheetToken(
        val top: Float,
        val radius: Float,
        val handle: HandleToken,
        val headerTop: Float,
        val headerFontSize: Float,
        val close: CloseToken,
    )

    data class CloseToken(val left: Float, val size: Float, val glyph: Float)

    data class LinkToken(
        val top: Float,
        val hitHeight: Float,
        val width: Float,
        val height: Float,
        val padding: Float,
        val gap: Float,
        val fontSize: Float,
        val icon: Float,
    )

    data class StageToken(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val wordmarkTop: Float,
        val actionCenterY: Float,
        val copyCenterX: Float,
        val downloadCenterX: Float,
        val actionHit: Float,
        val actionIcon: Float,
    )

    data class PassToken(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val poster: PosterToken,
        val titleLeft: Float,
        val titleTop: Float,
        val titleWidth: Float,
        val titleFontSize: Float,
        val titleLineHeight: Float,
        val dateLeft: Float,
        val dateTop: Float,
        val dateWidth: Float,
        val dateFontSize: Float,
        val dateLineHeight: Float,
    )

    data class PosterToken(val left: Float, val top: Float, val size: Float)

    data class ShareButtonToken(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
    )

    data class FoilFallbackToken(
        val stops: IntArray,
        val angle: FoilFallbackAngle,
        val opacity: Float,
    )

    data class FoilFallbackAngle(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    val sheet = SheetToken(
        top = 61f,
        radius = 44f,
        handle = HandleToken(left = 180.5f, top = 69f, width = 41f, height = 4f),
        headerTop = 78f,
        headerFontSize = 17.4f,
        close = CloseToken(left = 341f, size = 44f, glyph = 30f),
    )
    val link = LinkToken(
        top = 126f,
        hitHeight = 44f,
        width = 158f,
        height = 34f,
        padding = 13.5f,
        gap = 7.5f,
        fontSize = 16f,
        icon = 15f,
    )
    val stage = StageToken(
        left = 20.5f,
        top = 194f,
        width = 361f,
        height = 540f,
        radius = 14f,
        wordmarkTop = 682f,
        actionCenterY = 710f,
        copyCenterX = 318.2f,
        downloadCenterX = 355f,
        actionHit = 44f,
        actionIcon = 16.6f,
    )
    val pass = PassToken(
        left = 66.35f,
        top = 273.3f,
        width = 270.3f,
        height = 382f,
        radius = 23f,
        poster = PosterToken(left = 9.1f, top = 9.5f, size = 250.4f),
        titleLeft = 16.7f,
        titleTop = 272f,
        titleWidth = 238f,
        titleFontSize = 22.7f,
        titleLineHeight = 27f,
        dateLeft = 17.3f,
        dateTop = 345f,
        dateWidth = 210f,
        dateFontSize = 13f,
        dateLineHeight = 18f,
    )
    val shareButton = ShareButtonToken(
        left = 20.5f,
        top = 775.5f,
        width = 361f,
        height = 51f,
        fontSize = 18f,
    )
    val foilFallback = FoilFallbackToken(
        stops = intArrayOf(0xFF7FD9C2.toInt(), 0xFFB7A6F2.toInt(), 0xFFF2B8D0.toInt(), 0xFFF3E3A0.toInt(), 0xFF8FD3E8.toInt()),
        angle = FoilFallbackAngle(x1 = 0f, y1 = 0f, x2 = 1f, y2 = 0.6f),
        opacity = 0.85f,
    )
}
