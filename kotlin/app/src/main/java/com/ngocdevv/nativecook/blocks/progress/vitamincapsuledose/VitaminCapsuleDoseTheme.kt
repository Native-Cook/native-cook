package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

internal object VitaminCapsuleDoseTheme {
    const val INK = 0xFF0B0C14.toInt()
    const val SUB = 0xFF5B6280.toInt()
    const val FILL = 0xFFEEF0F4.toInt()
    const val PINK = 0xFFEC5F7E.toInt()
    const val PINK_TEXT = 0xFFEA4D70.toInt()
    const val PINK_DEEP = 0xFFD9446A.toInt()
    const val RING = 0xFF474F74.toInt()
    const val GREEN = 0xFF3EAA2D.toInt()
    data class Medicine(val name: String, val kind: String, val dose: String, val daily: String,
        val each: String, val shape: Double, val times: List<String>)
    val meds = listOf(
        Medicine("Lumivit", "Dietary supplement", "20mg pills", "60 mg", "3x Pills", 0.0, listOf("10:00 AM", "3:00 PM", "6:00 PM")),
        Medicine("Calmora", "Antacid suspension", "200ml", "30 ml", "3x Spoons", 2.0, listOf("8:00 AM", "1:00 PM", "8:00 PM")),
        Medicine("Mentab", "Pain relief", "500mg tablets", "1000 mg", "2x Pills", 1.0, listOf("9:00 AM", "2:00 PM", "9:00 PM")),
    )
}
