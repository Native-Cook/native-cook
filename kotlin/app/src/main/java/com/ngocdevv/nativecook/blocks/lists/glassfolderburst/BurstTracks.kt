package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

internal object BurstTracks {
    data class Track(val title: String, val artist: String, val seconds: Int) {
        val duration = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        val description = "$title, $artist, ${seconds / 60} minutes ${seconds % 60} seconds"
    }
    const val title = "Low Tide"
    val tracks = listOf(
        Track("Glass Hours", "Ivory Kessel", 222),
        Track("Night Ferry", "Mara Voss", 245),
        Track("Slow Voltage", "Okano & Reed", 198),
        Track("Paper Moons", "The Halden Set", 236),
        Track("Undertow", "Sable Arc", 261),
        Track("Kerosene Sky", "Juno Vale", 213),
        Track("Soft Machines", "Calder Lune", 227),
        Track("Harbor Lights", "Wren Atlas", 252),
        Track("After the Rain", "Nilo Ferreira", 189),
    )
    fun subtitle(n: Int): String = "$n tracks · ${kotlin.math.floor(tracks.take(n).sumOf { it.seconds } / 60.0 + .5).toInt()} min"
}
