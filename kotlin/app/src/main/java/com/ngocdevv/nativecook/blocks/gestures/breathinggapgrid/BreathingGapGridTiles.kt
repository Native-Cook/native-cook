package com.ngocdevv.nativecook.blocks.gestures.breathinggapgrid

import androidx.compose.ui.graphics.Color

/** A photo, or a square of type set in one colour on another. */
sealed interface GridTile {
    val title: String
    val note: String

    /**
     * @param uri A copy about the size of a closed tile.
     * @param full A larger copy, loaded only once the tile opens.
     */
    data class Photo(
        override val title: String,
        override val note: String,
        val uri: String,
        val full: String?,
        val tint: Color,
    ) : GridTile

    data class Type(
        override val title: String,
        override val note: String,
        val lines: List<String>,
        val ink: Color,
        val paper: Color,
    ) : GridTile
}

private fun unsplash(id: String, side: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$side&h=$side&q=80"

// Eighty photos stay light enough in memory at tile size; the one that opens is fetched again, sharp.
private fun gridPhoto(title: String, note: String, id: String, tint: Long) =
    GridTile.Photo(title, note, unsplash(id, 400), unsplash(id, 1080), Color(tint))

internal val GridTiles: List<GridTile> = listOf(
    gridPhoto("Ninety", "Portrait, black and white", "photo-1685041552051-13de9e56228c", 0xFF2B2B2B),
    gridPhoto("Stem", "Still life, white", "photo-1471086569966-db3eebc25a59", 0xFFE8E6E1),
    gridPhoto("Veil", "Portrait, red", "photo-1727341557146-4abab94d0812", 0xFF8E1A14),
    gridPhoto("Plaster", "Sculpture", "photo-1604854574894-1be73ca43cb8", 0xFFD9D6D0),
    gridPhoto("Blue Hour", "Ceramics", "photo-1660721671073-e139688fa3cf", 0xFF5D7389),
    gridPhoto("Runner", "Object, blue", "photo-1618677831708-0e7fda3148b4", 0xFF3C5AA8),
    gridPhoto("Dune", "Landscape", "photo-1785270101102-fe2b694a024f", 0xFFC48A52),
    GridTile.Type("A", "Type, cobalt", listOf("A"), Color(0xFFFFFFFF), Color(0xFF1F3FD1)),
    gridPhoto("Profile", "Portrait, black and white", "photo-1657412235453-8adb25a45cd0", 0xFF1E1E1E),
    gridPhoto("Lavender", "Dried flowers", "photo-1772441835051-4d32e651661d", 0xFF6A5A8A),
    gridPhoto("Mask", "Sculpture, dark", "photo-1561910377-c2a014ffd1cd", 0xFF141414),
    gridPhoto("Pink", "Object, magenta", "photo-1628558422453-576457df4b0d", 0xFFC2337F),
    gridPhoto("Cobalt", "Portrait, blue", "photo-1629511565591-a1d494ad6c58", 0xFF22356B),
    GridTile.Type("Room", "Type, three times", listOf("ROOM", "ROOM", "ROOM"), Color(0xFFFFFFFF), Color(0xFF0B0B0B)),
    gridPhoto("Pair", "Still life, colour", "photo-1655388446578-741cb6f55d14", 0xFFD8C6C3),
    gridPhoto("Director", "Object, chair", "photo-1758491037354-26d601d3cadd", 0xFFE3E0DA),
    gridPhoto("Gaze", "Portrait, editorial", "photo-1506863530036-1efeddceb993", 0xFF343434),
    gridPhoto("Grey Room", "Portrait, studio", "photo-1768247695726-022586dea3a1", 0xFF0E0E0E),
    gridPhoto("Scarlet", "Portrait, red", "photo-1687360440648-ec9708d52086", 0xFFAA564A),
    gridPhoto("Azure", "Portrait, blue", "photo-1675790991952-4006c7a67cad", 0xFF1D1315),
    gridPhoto("Marble", "Sculpture, bust", "photo-1637666505754-7416ebd70cbf", 0xFFC5C7CB),
    gridPhoto("Cast", "Sculpture, plaster", "photo-1447758902204-48010b87c24d", 0xFF8B8E8F),
    gridPhoto("Vessel", "Ceramics", "photo-1631125915902-d8abe9225ff2", 0xFFA09989),
    gridPhoto("Pause", "Still life", "photo-1604935067269-27c7e8b36618", 0xFFB6A899),
    gridPhoto("Speaker", "Object", "photo-1604782206219-3b9576575203", 0xFFAC8F77),
    gridPhoto("Pampas", "Dried flowers", "photo-1772510758813-a45fc1570a2c", 0xFFB58C6D),
    gridPhoto("Midnight", "Flowers", "photo-1783680599080-abe4d5634c3a", 0xFF342217),
    gridPhoto("Cut Out", "Fashion", "photo-1603189343302-e603f7add05a", 0xFF727272),
    gridPhoto("Atelier", "Fashion, studio", "photo-1717765698239-88731a968d20", 0xFF5F5A54),
    gridPhoto("Noir", "Fashion, black and white", "photo-1592833578500-1082e18665a3", 0xFFC1C1C1),
    gridPhoto("Plain Oak", "Object, chair", "photo-1640938776314-4d303f8a1380", 0xFFCCB048),
    gridPhoto("Linen", "Portrait, editorial", "photo-1762291629616-3e2c044c79a0", 0xFF25201F),
    gridPhoto("Low Key", "Portrait, studio", "photo-1557053910-d9eadeed1c58", 0xFFB16934),
    GridTile.Type("N", "Type, red", listOf("N"), Color(0xFFE5322D), Color(0xFFF2EFE8)),
    gridPhoto("Crimson", "Portrait, red", "photo-1727341556787-7292f7515aec", 0xFF6F0703),
    gridPhoto("Marine", "Portrait, blue", "photo-1752665696094-b733943393d9", 0xFFA37A62),
    gridPhoto("Pale", "Sculpture, bust", "photo-1729008712499-5e6b58420789", 0xFF303030),
    gridPhoto("Chalk", "Sculpture, plaster", "photo-1563301323-094e5843a962", 0xFF948778),
    gridPhoto("Glaze", "Ceramics", "photo-1597696929736-6d13bed8e6a8", 0xFFCBBFB4),
    gridPhoto("Balance", "Still life", "photo-1705684989920-2ae5e583c0e7", 0xFF221E18),
    gridPhoto("Arcs", "Object", "photo-1783372519845-6cc782fb0f68", 0xFF6C6B6B),
    gridPhoto("Bouquet", "Dried flowers", "photo-1586244897859-2cd81e1cad1f", 0xFFE0DDD5),
    GridTile.Type("Hi.", "Type, black on white", listOf("Hi."), Color(0xFF111111), Color(0xFFFFFFFF)),
    gridPhoto("Meadow", "Flowers", "photo-1762969217477-af1c7b85db17", 0xFF806645),
    gridPhoto("High Sky", "Fashion", "photo-1574015974293-817f0ebebb74", 0xFF4A4A4A),
    gridPhoto("Contrast", "Fashion, black and white", "photo-1754315052701-0b98f6ba786b", 0xFF5F6165),
    gridPhoto("Tulip", "Object, chair", "photo-1659246973603-748505504837", 0xFFAD9383),
    gridPhoto("Dusk", "Portrait, editorial", "photo-1765334666990-1bf2ce733864", 0xFF403C3B),
    gridPhoto("Halo", "Portrait, studio", "photo-1770327960728-ad08ea1e921e", 0xFF4B4B4B),
    gridPhoto("Ember", "Portrait, red", "photo-1727341557531-343cd4364501", 0xFF653E2A),
    gridPhoto("Relic", "Sculpture, bust", "photo-1570877901348-07076379b061", 0xFF544844),
    gridPhoto("Gesso", "Sculpture, plaster", "photo-1742495211859-d2e95a1e9354", 0xFF563F2D),
    gridPhoto("Urn", "Ceramics", "photo-1631125915732-b98f8774f675", 0xFFACA9A7),
    gridPhoto("Rest", "Still life", "photo-1615020977625-6c5092907e9d", 0xFFC4C3B9),
    gridPhoto("Prism", "Object", "photo-1620987278429-ab178d6eb547", 0xFFD7C7AD),
    gridPhoto("Thistle", "Dried flowers", "photo-1770816307374-d1ee7e423342", 0xFFAC8D76),
    gridPhoto("Orchard", "Flowers", "photo-1554482091-af22387a7a0c", 0xFF7D777A),
    gridPhoto("Fringe", "Fashion", "photo-1562151270-c7d22ceb586a", 0xFFA2B6B6),
    gridPhoto("Armchair", "Object, chair", "photo-1722357533644-9b20e356ba5f", 0xFFAB9685),
    gridPhoto("Hush", "Portrait, editorial", "photo-1674932668403-33398b81c92f", 0xFF80746B),
    gridPhoto("Matte", "Portrait, studio", "photo-1535713875002-d1d0cf377fde", 0xFFA39EA1),
    gridPhoto("Bust", "Sculpture, bust", "photo-1747191092806-c7da11881986", 0xFF837C73),
    gridPhoto("Moulded", "Sculpture, plaster", "photo-1781707870248-e2607e82bac0", 0xFFA5A5A5),
    gridPhoto("Loop", "Ceramics", "photo-1643569556871-91ec60671ed7", 0xFFAFA190),
    GridTile.Type("Look", "Type, cobalt on white", listOf("LOOK", "LOOK"), Color(0xFF1F3FD1), Color(0xFFFFFFFF)),
    GridTile.Type("O", "Type, white on red", listOf("O"), Color(0xFFFFFFFF), Color(0xFFE5322D)),
    gridPhoto("Duo", "Still life", "photo-1608575474456-55113364dd66", 0xFF959085),
    gridPhoto("Plinth", "Object", "photo-1621544170639-fb0189e518ba", 0xFFD6D2D1),
    gridPhoto("Harvest", "Dried flowers", "photo-1604304194650-3ba3cfa752fd", 0xFFC7C0B2),
    gridPhoto("Drape", "Fashion", "photo-1662532577856-e8ee8b138a8b", 0xFF644A7A),
    gridPhoto("Slats", "Object, chair", "photo-1572297794744-a19043e308f8", 0xFFCAB2AC),
    gridPhoto("Copper", "Portrait, editorial", "photo-1606143412458-acc5f86de897", 0xFF2C2C2A),
    gridPhoto("Studio", "Portrait, studio", "photo-1768247695735-e10baa73e917", 0xFF181818),
    gridPhoto("Classic", "Sculpture, bust", "photo-1565170556450-bdfc81864ca1", 0xFF1A1924),
    GridTile.Type("Gap", "Type, black on paper", listOf("GAP"), Color(0xFF0B0B0B), Color(0xFFF2EFE8)),
    gridPhoto("Kiln", "Ceramics", "photo-1481401908818-600b7a676c0d", 0xFF84938B),
    gridPhoto("Facet", "Object", "photo-1703843943664-867123b25898", 0xFFBAB6B0),
    gridPhoto("Wild Oat", "Dried flowers", "photo-1615851908107-5047d87d0327", 0xFFDCD7D9),
    gridPhoto("Tailor", "Fashion", "photo-1779399147755-cb3c1a62c3de", 0xFF8B9598),
    gridPhoto("Navy", "Object, chair", "photo-1748887522222-ae67e761fb64", 0xFF899198),
    gridPhoto("Tilt", "Portrait, editorial", "photo-1774567753803-f26f0add5800", 0xFF2D2D2D),
    gridPhoto("Terracotta", "Ceramics", "photo-1526198049595-f32cde2a219d", 0xFFE1E2E3),
    gridPhoto("Field", "Dried flowers", "photo-1546810738-b0c7fe348c6b", 0xFFBDC5BF),
    gridPhoto("Swing", "Fashion", "photo-1596993100471-c3905dafa78e", 0xFF939278),
    gridPhoto("Glance", "Portrait, editorial", "photo-1772566737819-d8898e6580a4", 0xFF222222),
    gridPhoto("Quiet", "Portrait, editorial", "photo-1563170446-9c3c0622d8a9", 0xFF31241C),
    gridPhoto("Still", "Portrait, editorial", "photo-1612928414075-bc722ade44f1", 0xFF878787),
)
