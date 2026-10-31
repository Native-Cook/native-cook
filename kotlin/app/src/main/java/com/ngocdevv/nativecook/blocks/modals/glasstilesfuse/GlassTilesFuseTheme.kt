package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import android.content.Context
import android.graphics.Typeface

internal class GlassTilesFuseTheme(context:Context) {
    val regular:Typeface=Typeface.createFromAsset(context.assets,"glasstilesfuse/glasstilesfuse_Inter-Regular.ttf")
    val semibold:Typeface=Typeface.createFromAsset(context.assets,"glasstilesfuse/glasstilesfuse_Inter-SemiBold.ttf")
    val bold:Typeface=Typeface.createFromAsset(context.assets,"glasstilesfuse/glasstilesfuse_Inter-Bold.ttf")
    companion object {
        const val PAGE=0xFF111111.toInt(); const val SHEET=0xFF141414.toInt(); const val GRABBER=0xFF3A3A3C.toInt()
        const val ROW_NAME=0xFFE8E8E8.toInt(); const val ROW_SUB=0xFF8E8E93.toInt()
        const val PILL=0xFF232323.toInt(); const val PILL_INK=0xFFD6D6D6.toInt()
        const val TITLE=0xFFF2F2F2.toInt(); const val BODY=0xFF999999.toInt()
        const val BUTTON=0xFFF2F2F2.toInt(); const val BUTTON_INK=0xFF111111.toInt()
        val ROWS=listOf("Contacts" to "On this iPhone","Mail" to "iCloud, Outlook","Calendar" to "Work, Family","Cloud Drive" to "2 folders shared")
    }
}
