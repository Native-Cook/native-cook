package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.ui.graphics.Color

/** One invented transaction (transactions.ts; [day] is the reference's `when`). The icon is a Feather glyph. */
internal class TiltTransaction(
    val key: String,
    val title: String,
    val day: String,
    val amount: String,
    val icon: FeatherIcon,
    val tint: Color,
)

internal val SPENDING = listOf(
    TiltTransaction("groceries", "Groceries", "Today", "-34$", FeatherIcon.ShoppingCart, TiltIconTint.yellow),
    TiltTransaction("rent", "Rent", "Today", "-114$", FeatherIcon.Home, TiltIconTint.lilac),
    TiltTransaction("coffee", "Corner Café", "Yesterday", "-4.80$", FeatherIcon.Coffee, TiltIconTint.peach),
    TiltTransaction("metro", "Metro card", "Yesterday", "-25$", FeatherIcon.Navigation, TiltIconTint.sky),
    TiltTransaction("cinema", "Cinema", "Sat", "-18$", FeatherIcon.Film, TiltIconTint.rose),
    TiltTransaction("gym", "Climbing gym", "Fri", "-45$", FeatherIcon.Activity, TiltIconTint.mint),
    TiltTransaction("books", "Bookshop", "Thu", "-22$", FeatherIcon.BookOpen, TiltIconTint.yellow),
    TiltTransaction("pharmacy", "Pharmacy", "Wed", "-12.60$", FeatherIcon.PlusSquare, TiltIconTint.mint),
)

/** A list entry: [restoring] is set for a row that Undo brings back (it slides in from the left). */
internal class TiltRow(val item: TiltTransaction, val restoring: Boolean = false)

/** '-34$' -> 34: deleting a spend gives it back to the balance. */
internal fun amountOf(item: TiltTransaction): Float {
    val digits = item.amount.filter { it.isDigit() || it == '.' }
    return digits.toFloatOrNull() ?: 0f
}
