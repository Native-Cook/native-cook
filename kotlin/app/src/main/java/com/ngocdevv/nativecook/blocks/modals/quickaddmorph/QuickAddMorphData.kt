package com.ngocdevv.nativecook.blocks.modals.quickaddmorph

data class QuickAddTask(
    val id: String,
    val title: String,
    val meta: String,
    val priority: Boolean = false,
    val done: Boolean = false,
)

val QuickAddTasks = listOf(
    QuickAddTask("rent", "Pay the November rent", "Home · 9:00", done = true),
    QuickAddTask("deck", "Send the Q4 deck to Priya", "Work · 11:30", priority = true),
    QuickAddTask("plants", "Water the balcony plants", "Home · 12:00", done = true),
    QuickAddTask("review", "Review the onboarding copy", "Work · 14:00"),
    QuickAddTask("parcel", "Pick up the parcel", "Errands · 16:15"),
    QuickAddTask("gym", "Leg day at the gym", "Health · 18:00"),
    QuickAddTask("groceries", "Buy oat milk and eggs", "Errands · 18:45"),
    QuickAddTask("call", "Call Mum about Sunday", "Home · 20:00"),
)

val QuickAddLists = listOf("Inbox", "Home", "Work", "Errands")

/** What the autoPlay loop types and adds. */
internal object QuickAddScript {
    const val TITLE = "Book the dentist"
    const val META = "Home · Today"
    const val PRIORITY = true
}
