package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

/** Invented to-dos. A note row shows the small document glyph after its title. */
internal data class DragMultiSelectTask(
    val id: String,
    val title: String,
    val note: Boolean,
)

internal val DragMultiSelectTasks: List<DragMultiSelectTask> = listOf(
    DragMultiSelectTask(id = "t0", title = "Return Halo band", note = false),
    DragMultiSelectTask(id = "t1", title = "Set up Simplify PWA", note = false),
    DragMultiSelectTask(id = "t2", title = "Product Manager (North America)", note = true),
    DragMultiSelectTask(id = "t3", title = "Interactive SVG Animations is ready", note = true),
    DragMultiSelectTask(id = "t4", title = "Try voice cloning", note = true),
    DragMultiSelectTask(id = "t5", title = "Restaurant List", note = true),
    DragMultiSelectTask(id = "t6", title = "Pata sala", note = true),
    DragMultiSelectTask(id = "t7", title = "Kerala", note = true),
    DragMultiSelectTask(id = "t8", title = "Train", note = true),
    DragMultiSelectTask(id = "t9", title = "Tetto", note = true),
    DragMultiSelectTask(id = "t10", title = "Mockups", note = true),
    DragMultiSelectTask(id = "t11", title = "Glossy Icon Tutorial", note = true),
    DragMultiSelectTask(id = "t12", title = "Zeno Hoodie", note = true),
    DragMultiSelectTask(id = "t13", title = "Harbor Tribune Article", note = true),
    DragMultiSelectTask(id = "t14", title = "China City", note = true),
    DragMultiSelectTask(id = "t15", title = "Check out Cmd + G", note = false),
    DragMultiSelectTask(id = "t16", title = "Steddy select time", note = false),
    DragMultiSelectTask(id = "t17", title = "Renew passport", note = false),
    DragMultiSelectTask(id = "t18", title = "Plant the basil", note = true),
    DragMultiSelectTask(id = "t19", title = "Call the landlord", note = false),
    DragMultiSelectTask(id = "t20", title = "Sort the receipts", note = true),
    DragMultiSelectTask(id = "t21", title = "Book the dentist", note = false),
    DragMultiSelectTask(id = "t22", title = "Fix the bike light", note = false),
    DragMultiSelectTask(id = "t23", title = "Gift for Ines", note = true),
)
