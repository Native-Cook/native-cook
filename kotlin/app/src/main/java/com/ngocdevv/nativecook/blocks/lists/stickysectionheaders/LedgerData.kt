package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import androidx.compose.ui.graphics.Color

/** Ledger's activity history (invented): day sections with their payments, newest first. */

class LedgerTxn(
    val id: String,
    val name: String,
    val meta: String,
    /** Cents; negative = money out, positive = money in. */
    val cents: Int,
    /** Glyph colour pair for the merchant disc. */
    val tint: Pair<Color, Color>,
    val initial: String,
)

class LedgerSection(val id: String, val label: String, val month: Int, val txns: List<LedgerTxn>)

/** One entry of the flat list the LazyColumn renders. */
sealed interface LedgerItem {
    val key: String

    class Header(override val key: String, val label: String, val count: Int, val spent: Int) : LedgerItem
    class Row(override val key: String, val txn: LedgerTxn, val last: Boolean) : LedgerItem
}

val LEDGER_MONTHS = listOf("Nov", "Oct", "Sep", "Aug", "Jul", "Jun")
val LEDGER_MONTH_NAMES = listOf("November", "October", "September", "August", "July", "June")

private val T_FOOD = Color(0xFFF3A65A) to Color(0xFFD9733A)
private val T_COFFEE = Color(0xFFB98A64) to Color(0xFF8A5D3F)
private val T_TRANSIT = Color(0xFF6AA3E8) to Color(0xFF3B6FC4)
private val T_HOME = Color(0xFF8FC5A2) to Color(0xFF4F936B)
private val T_FUN = Color(0xFFE58FB0) to Color(0xFFBF5683)
private val T_HEALTH = Color(0xFF7FCBD0) to Color(0xFF3E979F)
private val T_BILLS = Color(0xFFA7A1E6) to Color(0xFF6C63C4)
private val T_INCOME = Color(0xFF9FD88A) to Color(0xFF4EA33C)

private fun txn(id: String, name: String, meta: String, cents: Int, tint: Pair<Color, Color>) =
    LedgerTxn(id, name, meta, cents, tint, name.substring(0, 1))

val LEDGER_SECTIONS: List<LedgerSection> = listOf(
    LedgerSection(
        "d1124", "Today", 0,
        listOf(
            txn("a1", "Lumen Coffee", "Coffee · 8:12 am", -540, T_COFFEE),
            txn("a2", "Northway Transit", "Transport · 8:40 am", -290, T_TRANSIT),
            txn("a3", "Fernhill Market", "Groceries · 12:05 pm", -4635, T_FOOD),
            txn("a4", "Saltgrass Kitchen", "Dining · 7:48 pm", -3860, T_FOOD),
        ),
    ),
    LedgerSection(
        "d1123", "Yesterday", 0,
        listOf(
            txn("b1", "Cinder Gym", "Health · 7:02 am", -2400, T_HEALTH),
            txn("b2", "Quill Books", "Shopping · 3:20 pm", -1899, T_FUN),
            txn("b3", "Lumen Coffee", "Coffee · 4:10 pm", -480, T_COFFEE),
        ),
    ),
    LedgerSection(
        "d1121", "Sat, Nov 21", 0,
        listOf(
            txn("c1", "Harbor Pharmacy", "Health · 10:30 am", -1275, T_HEALTH),
            txn("c2", "Bolt & Thread", "Clothing · 1:15 pm", -6400, T_FUN),
            txn("c3", "Fernhill Market", "Groceries · 5:40 pm", -3120, T_FOOD),
            txn("c4", "Paper Lantern", "Cinema · 8:00 pm", -2650, T_FUN),
            txn("c5", "Northway Transit", "Transport · 11:20 pm", -1430, T_TRANSIT),
        ),
    ),
    LedgerSection(
        "d1118", "Wed, Nov 18", 0,
        listOf(
            txn("e1", "Brightline Studio", "Salary · 9:00 am", 324000, T_INCOME),
            txn("e2", "Pebble Mobile", "Bills · 9:30 am", -2900, T_BILLS),
            txn("e3", "Saltgrass Kitchen", "Dining · 1:05 pm", -1745, T_FOOD),
        ),
    ),
    LedgerSection(
        "d1030", "Fri, Oct 30", 1,
        listOf(
            txn("f1", "Orchard Lane Homes", "Rent · 8:00 am", -86000, T_HOME),
            txn("f2", "Lumen Coffee", "Coffee · 8:25 am", -540, T_COFFEE),
            txn("f3", "Fernhill Market", "Groceries · 6:10 pm", -5280, T_FOOD),
            txn("f4", "Northway Transit", "Transport · 6:45 pm", -290, T_TRANSIT),
        ),
    ),
    LedgerSection(
        "d1025", "Sun, Oct 25", 1,
        listOf(
            txn("g1", "Sundial Bakery", "Dining · 10:40 am", -1420, T_FOOD),
            txn("g2", "Paper Lantern", "Cinema · 6:30 pm", -2650, T_FUN),
            txn("g3", "Quill Books", "Shopping · 7:55 pm", -2200, T_FUN),
        ),
    ),
    LedgerSection(
        "d1020", "Tue, Oct 20", 1,
        listOf(
            txn("h1", "Cinder Gym", "Health · 7:00 am", -2400, T_HEALTH),
            txn("h2", "Gridwell Energy", "Bills · 9:00 am", -7136, T_BILLS),
            txn("h3", "Fernhill Market", "Groceries · 5:20 pm", -3965, T_FOOD),
            txn("h4", "Lumen Coffee", "Coffee · 5:35 pm", -480, T_COFFEE),
        ),
    ),
    LedgerSection(
        "d0928", "Mon, Sep 28", 2,
        listOf(
            txn("i1", "Harbor Pharmacy", "Health · 11:10 am", -960, T_HEALTH),
            txn("i2", "Saltgrass Kitchen", "Dining · 8:15 pm", -4490, T_FOOD),
            txn("i3", "Northway Transit", "Transport · 10:40 pm", -1210, T_TRANSIT),
        ),
    ),
    LedgerSection(
        "d0917", "Thu, Sep 17", 2,
        listOf(
            txn("j1", "Pebble Mobile", "Bills · 9:30 am", -2900, T_BILLS),
            txn("j2", "Bolt & Thread", "Clothing · 2:00 pm", -11800, T_FUN),
            txn("j3", "Sundial Bakery", "Dining · 4:45 pm", -875, T_FOOD),
            txn("j4", "Fernhill Market", "Groceries · 6:30 pm", -2740, T_FOOD),
        ),
    ),
    LedgerSection(
        "d0829", "Sat, Aug 29", 3,
        listOf(
            txn("k1", "Kestrel Air", "Travel · 6:05 am", -21200, T_TRANSIT),
            txn("k2", "Lumen Coffee", "Coffee · 6:40 am", -620, T_COFFEE),
            txn("k3", "Seabright Inn", "Travel · 3:00 pm", -16800, T_HOME),
            txn("k4", "Saltgrass Kitchen", "Dining · 8:30 pm", -5230, T_FOOD),
        ),
    ),
    LedgerSection(
        "d0812", "Wed, Aug 12", 3,
        listOf(
            txn("l1", "Gridwell Energy", "Bills · 9:00 am", -6412, T_BILLS),
            txn("l2", "Cinder Gym", "Health · 6:15 pm", -2400, T_HEALTH),
            txn("l3", "Fernhill Market", "Groceries · 7:05 pm", -3390, T_FOOD),
        ),
    ),
    LedgerSection(
        "d0726", "Sun, Jul 26", 4,
        listOf(
            txn("m1", "Tidewater Market", "Groceries · 11:20 am", -1950, T_FOOD),
            txn("m2", "Paper Lantern", "Cinema · 7:10 pm", -2650, T_FUN),
            txn("m3", "Northway Transit", "Transport · 10:05 pm", -340, T_TRANSIT),
        ),
    ),
    LedgerSection(
        "d0709", "Thu, Jul 9", 4,
        listOf(
            txn("n1", "Harbor Pharmacy", "Health · 9:40 am", -1525, T_HEALTH),
            txn("n2", "Quill Books", "Shopping · 1:30 pm", -3100, T_FUN),
            txn("n3", "Saltgrass Kitchen", "Dining · 7:45 pm", -4180, T_FOOD),
        ),
    ),
    LedgerSection(
        "d0630", "Tue, Jun 30", 5,
        listOf(
            txn("o1", "Orchard Lane Homes", "Rent · 8:00 am", -86000, T_HOME),
            txn("o2", "Pebble Mobile", "Bills · 9:30 am", -2900, T_BILLS),
            txn("o3", "Sundial Bakery", "Dining · 10:15 am", -1140, T_FOOD),
            txn("o4", "Lumen Coffee", "Coffee · 4:20 pm", -480, T_COFFEE),
        ),
    ),
    LedgerSection(
        "d0612", "Fri, Jun 12", 5,
        listOf(
            txn("p1", "Cinder Gym", "Health · 7:00 am", -2400, T_HEALTH),
            txn("p2", "Fernhill Market", "Groceries · 6:00 pm", -4815, T_FOOD),
            txn("p3", "Paper Lantern", "Cinema · 9:20 pm", -2650, T_FUN),
        ),
    ),)

/** "$1,234.50", with a "−" or "+" in front when signed. */
fun formatMoney(cents: Int, signed: Boolean): String {
    val abs = kotlin.math.abs(cents)
    val dollars = "%,d".format(java.util.Locale.US, abs / 100)
    val body = "\$$dollars.${(abs % 100).toString().padStart(2, '0')}"
    if (!signed) return body
    return if (cents < 0) "−$body" else "+$body"
}

/** Money out only (a day's "spent"), in cents. */
fun daySpent(s: LedgerSection): Int = s.txns.sumOf { if (it.cents < 0) -it.cents else 0 }

/** The flat list the LazyColumn renders (built once per data set). */
fun buildLedgerItems(sections: List<LedgerSection>): List<LedgerItem> {
    val out = ArrayList<LedgerItem>()
    for (s in sections) {
        out.add(LedgerItem.Header("h-${s.id}", s.label, s.txns.size, daySpent(s)))
        s.txns.forEachIndexed { i, t -> out.add(LedgerItem.Row(t.id, t, i == s.txns.size - 1)) }
    }
    return out
}
