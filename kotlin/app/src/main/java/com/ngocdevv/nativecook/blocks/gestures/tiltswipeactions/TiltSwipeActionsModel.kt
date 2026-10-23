package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal object TiltSwipeActionsColor {
    val Paper = Color(0xFFF5F1E8)
    val Stock = Color(0xFFFBF9F4)
    val Ink = Color(0xFF17181D)
    val Ink2 = Color(0xFF6E695F)
    val Ink3 = Color(0xFFA39D91)
    val Ivory = Paper
    val Brass = Color(0xFFC8AD7E)
    val BrassPaper = Color(0xFF8B6B3A)
    val BrassDot = Color(0xFFA6844F)
    val TabBar = Color(0xFFEFEADF)
    val MonoToast = Color(0xFF2E2F36)
    val Flag = Color(0xFF2B2C33)
    val Delete = Color(0xFF8F3A2C)
}

internal object TiltSwipeActionsLayout {
    const val GUTTER = 16f
    const val CARD_HEIGHT = 128f
    const val CARD_RADIUS = 20f
    const val CARD_FRAME_INSET = 6f
    const val SECTION_GAP = 28f
    const val SECTION_TITLE = 32f
    const val LIST_GAP = 12f
    const val LIST_PAD = 5f
    const val ROW = 72f
    const val CIRCLE = 40f
    const val TEXT = 52f
    const val INSET = 4f
    const val BAR = 64f
    const val OUTER = 14f
    const val BROKEN = 4f
    const val MINI = 16f
    const val MONO = 28f
    const val HUD = 40f
    const val HUD_OVER = 56f
    const val TOAST = 56f
    const val TOAST_RADIUS = 16f
    const val TILE = 72f
    const val TAB_BAR = 54f
    const val TAB = 56f
    const val TAB_HEIGHT = 40f
    const val TAB_GAP = 8f
    const val FAB = 48f
    const val FINGER = 40f
    const val OPEN_X = -144f
}

internal data class TiltSwipeActionsScene(
    val width: Float,
    val height: Float,
    val topInset: Float,
    val bottomInset: Float,
) {
    val cardTop = topInset
    val listWidth = width - 2f * TiltSwipeActionsLayout.GUTTER
    val sectionTop = cardTop + TiltSwipeActionsLayout.CARD_HEIGHT + TiltSwipeActionsLayout.SECTION_GAP
    val listTop = sectionTop + TiltSwipeActionsLayout.SECTION_TITLE + TiltSwipeActionsLayout.LIST_GAP
    val rowsTop = listTop + TiltSwipeActionsLayout.LIST_PAD
    val barTop = height - bottomInset - TiltSwipeActionsLayout.TAB_BAR
    val toastTop = barTop - TiltSwipeActionsLayout.LIST_GAP - TiltSwipeActionsLayout.TOAST - 4f
    val linkTop = barTop - 64f
    val fabLeft = width - TiltSwipeActionsLayout.GUTTER - TiltSwipeActionsLayout.FAB
    val fabTop = barTop + 4f
    val capacity = fit(toastTop)
    val capacityUnderToast = fit(toastTop - 8f)

    fun rowAt(y: Float): Int {
        val index = floor((y - rowsTop) / TiltSwipeActionsLayout.ROW).toInt()
        return index.takeIf { it in Transactions.indices && y >= rowsTop } ?: -1
    }

    private fun fit(bottom: Float): Int = maxOf(
        3,
        floor((bottom - listTop - TiltSwipeActionsLayout.LIST_PAD - 3f) / TiltSwipeActionsLayout.ROW).toInt(),
    )
}

internal enum class TiltSwipeActionsGlyph {
    Bag, Bolt, Dining, Tram, Ticket, Dumbbell, Users, Flag, Trash, Undo, Plus, Home, Chart, Card
}

internal data class TiltSwipeActionsTransaction(
    val key: String,
    val title: String,
    val category: String,
    val day: String,
    val amount: Float,
    val icon: TiltSwipeActionsGlyph,
)

internal data class TiltSwipeActionsPerson(val name: String, val initials: String)

internal val Transactions = listOf(
    TiltSwipeActionsTransaction("greenleaf", "Greenleaf Market", "Groceries", "Today", -34f, TiltSwipeActionsGlyph.Bag),
    TiltSwipeActionsTransaction("harbour", "Harbour Electric", "Utilities", "Today", -114f, TiltSwipeActionsGlyph.Bolt),
    TiltSwipeActionsTransaction("saigon", "Little Saigon Kitchen", "Dining", "Yesterday", -96.6f, TiltSwipeActionsGlyph.Dining),
    TiltSwipeActionsTransaction("metro", "Metro card", "Transport", "Yesterday", -25f, TiltSwipeActionsGlyph.Tram),
    TiltSwipeActionsTransaction("cinema", "Lumen Cinema", "Entertainment", "Sat", -18f, TiltSwipeActionsGlyph.Ticket),
    TiltSwipeActionsTransaction("boulder", "Boulder Hall", "Fitness", "Fri", -45f, TiltSwipeActionsGlyph.Dumbbell),
)

internal val People = listOf(
    TiltSwipeActionsPerson("You", "LT"),
    TiltSwipeActionsPerson("Mai", "MP"),
    TiltSwipeActionsPerson("Leo", "LN"),
    TiltSwipeActionsPerson("Kai", "KO"),
    TiltSwipeActionsPerson("Ana", "AR"),
)

internal fun shares(amount: Float, count: Int): List<Float> {
    val total = (abs(amount) * 100f).roundToInt()
    val each = total / count
    return List(count) { index -> (if (index == 0) total - each * (count - 1) else each) / 100f }
}

internal fun money(value: Float, signed: Boolean = true): Pair<String, String> {
    val cents = (abs(value) * 100f).roundToInt()
    val grouped = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val prefix = if (signed && value < 0f && cents > 0) "–" else ""
    return "$prefix$$grouped" to ".${(cents % 100).toString().padStart(2, '0')}"
}

internal fun moneyText(value: Float, signed: Boolean = true): String {
    val (whole, cents) = money(value, signed)
    return whole + cents
}

internal fun weekTotal(values: List<Float>): Float = (values.sum() * 100f).roundToInt() / 100f

internal fun smooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun cosineEase(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return ((1.0 - kotlin.math.cos(Math.PI * t)) / 2.0).toFloat()
}

internal fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

