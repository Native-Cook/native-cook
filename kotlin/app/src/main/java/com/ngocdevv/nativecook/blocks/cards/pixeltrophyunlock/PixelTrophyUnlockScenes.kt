package com.ngocdevv.nativecook.blocks.cards.pixeltrophyunlock

internal object PixelTrophyUnlockScenes {
    data class Scene(val sky: List<String>, val sun: List<Double>, val sunColor: String, val glow: String, val moon: Boolean, val stars: Boolean, val skyline: String, val land: List<String>, val emblem: String, val ink: String, val shade: String)
    val emblems = mapOf(
        "star" to listOf(".....#.....",".....#.....","....###....","....###....","###########",".#########.","..#######..","..###+###..",".###...###.",".##.....##.",".#.......#."),
        "burst" to listOf("#....#....#",".#...#...#.","..#..#..#..","...#####...","....###....","###########","....###....","...#####...","..#..#..#..",".#...#...#.","#....#....#"),
        "flame" to listOf(".....#.....","....##.....","....###....","...####.#..","..#######..","..###+###..",".###+++###.",".##+++++##.",".##++#++##.","..##+#+##..","...#####..."),
        "shoe" to listOf("...........","..####.....","..#++#.....","..#++#.....","..#++####..","..#+++++##.",".##+++++++#",".#########+",".#########.","..++.++.++.","..........."),
        "ten" to listOf("...........",".##..####..","###.##..##.",".##.##..##.",".##.##..##.",".##.##..##.",".##.##..##.",".##.##..##.","####.####..","...........","+++++++++++"),
        "bolt" to listOf("......###..",".....###...","....###....","...###.....","..#######..",".....###...","....###....","...###.....","..###......",".##........","##........."),
        "wave" to listOf("...........","...........",".###....##.","##.##..##..","#...####...","...........",".###....##.","##.##..##..","#...####...","...........","..........."),
        "moonrun" to listOf("....###....","...#.......","..#........","..#........","..#.....#..","...#...#...","....###....","...........",".#.#.#.#.#.","...........","..........."),
    )
    val scenes = mapOf(
        "supernova" to Scene(listOf("#160b30","#b62ad8","#ffcf7a"), listOf(0.5,0.42,0.2), "#fff7e0", "#ff9be8", false, false, "mountains", listOf("#0c5c52","#22c79a"), "burst", "#ffffff", "#ffd27a"),
        "superstar" to Scene(listOf("#070c2e","#14246a","#2c4aa0"), listOf(0.0,0.0,0.0), "#000000", "#000000", false, true, "city", listOf("#0b1238","#1d2c66"), "star", "#ffe066", "#ff9d2e"),
        "sorcerer" to Scene(listOf("#0e1534","#3a2f7a","#7d58c4"), listOf(0.0,0.0,0.0), "#000000", "#000000", true, true, "pines", listOf("#0b1a22","#17323a"), "flame", "#ff8a3c", "#ffd166"),
        "stepstar" to Scene(listOf("#ff5d3a","#ff9a3c","#ffd65a"), listOf(0.5,0.55,0.24), "#fff3c4", "#ffbf6a", false, false, "hills", listOf("#5a2a6e","#8a3f8f"), "shoe", "#ffffff", "#ffd0e6"),
        "tenfold" to Scene(listOf("#0f4b5c","#2c8f99","#9fe3d1"), listOf(0.0,0.0,0.0), "#000000", "#000000", false, false, "track", listOf("#a6402e","#e0694a"), "ten", "#ffffff", "#ffd166"),
        "fastest" to Scene(listOf("#1f1238","#6b2f6e","#ff8d5c"), listOf(0.7,0.5,0.16), "#ffe0a0", "#ff7a4a", false, false, "road", listOf("#232032","#3a3550"), "bolt", "#fff06a", "#ff9d2e"),
        "momentum" to Scene(listOf("#0e2a24","#1f5b44","#6fc28a"), listOf(0.0,0.0,0.0), "#000000", "#000000", false, false, "pines", listOf("#0a1d17","#163a2c"), "wave", "#c8ff6a", "#ffffff"),
        "nightrun" to Scene(listOf("#06091c","#141c4a","#2a2f72"), listOf(0.0,0.0,0.0), "#000000", "#000000", true, true, "road", listOf("#0c0f24","#1a1f3e"), "moonrun", "#ffffff", "#9fb4ff"),
        "hillclimb" to Scene(listOf("#2a1b4f","#7a4fb0","#f2a6c8"), listOf(0.3,0.45,0.14), "#fff0f5", "#ffc2dc", false, false, "mountains", listOf("#3b1f4f","#6a3b80"), "star", "#ffffff", "#ffd6ec"),
        "streak" to Scene(listOf("#3a0d10","#a3291f","#ff8a3c"), listOf(0.0,0.0,0.0), "#000000", "#000000", false, false, "hills", listOf("#2a0b0e","#4f1a1c"), "flame", "#ffe06a", "#ff6a3c"),
    )
}
