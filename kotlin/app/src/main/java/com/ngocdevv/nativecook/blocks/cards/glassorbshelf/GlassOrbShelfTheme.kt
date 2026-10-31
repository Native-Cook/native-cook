package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

internal object GlassOrbShelfTheme {
    data class App(val first: String,val rest: String,val tint: Int,val body: String) {
        val label get() = "$first $rest. $body"
    }
    val apps=listOf(
        App("Non-fiction","summaries",0xFF5DD39C.toInt(),"Detailed outlines of popular non-fiction books, to learn a topic in ten minutes."),
        App("Travel","bucket list",0xFF78B4FF.toInt(),"Keep track of places you’ve been and dream of visiting, on beautiful cards."),
        App("Film photo","studio",0xFF62D2DF.toInt(),"Turn your photos into soft film shots with grain, light leaks and classic stocks."),
        App("Receipt to","recipe",0xFFFF8A73.toInt(),"Snap your grocery bill and get three recipes made from what you bought.")
    )
    const val PAGE=0xFF0D0E11.toInt()
    const val CARD=0xFF17181D.toInt()
    const val INK=0xFFF2F3F5.toInt()
    const val BODY=0xFFA4A7AF.toInt()
    const val MUTE=0xFF6C707A.toInt()
}
