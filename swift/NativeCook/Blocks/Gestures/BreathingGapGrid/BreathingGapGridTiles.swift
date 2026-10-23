import SwiftUI

/// A photo, or a square of type set in one colour on another.
struct BreathingGapGridTile {
    enum Kind {
        /// `uri` is a copy about the size of a closed tile; `full` a larger one, loaded only once the tile opens.
        case photo(uri: String, full: String?, tint: UInt32)
        case type(lines: [String], ink: UInt32, paper: UInt32)
    }

    var title: String
    var note: String
    var kind: Kind

    var isPhoto: Bool {
        if case .photo = kind { return true }
        return false
    }

    /// The colour the tile shows until its photo arrives, and the colour of its glow when open.
    var ground: UInt32 {
        switch kind {
        case let .photo(_, _, tint): tint
        case let .type(_, _, paper): paper
        }
    }

    static func photo(_ title: String, note: String, id: String, tint: UInt32) -> BreathingGapGridTile {
        // Eighty photos stay light enough in memory at tile size; the one that opens is fetched again, sharp.
        BreathingGapGridTile(title: title, note: note, kind: .photo(uri: unsplash(id, 400), full: unsplash(id, 1080), tint: tint))
    }

    static func type(_ title: String, note: String, lines: [String], ink: UInt32, paper: UInt32) -> BreathingGapGridTile {
        BreathingGapGridTile(title: title, note: note, kind: .type(lines: lines, ink: ink, paper: paper))
    }

    private static func unsplash(_ id: String, _ side: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(side)&h=\(side)&q=80"
    }

    static let samples: [BreathingGapGridTile] = [
        .photo("Ninety", note: "Portrait, black and white", id: "photo-1685041552051-13de9e56228c", tint: 0x2b2b2b),
        .photo("Stem", note: "Still life, white", id: "photo-1471086569966-db3eebc25a59", tint: 0xe8e6e1),
        .photo("Veil", note: "Portrait, red", id: "photo-1727341557146-4abab94d0812", tint: 0x8e1a14),
        .photo("Plaster", note: "Sculpture", id: "photo-1604854574894-1be73ca43cb8", tint: 0xd9d6d0),
        .photo("Blue Hour", note: "Ceramics", id: "photo-1660721671073-e139688fa3cf", tint: 0x5d7389),
        .photo("Runner", note: "Object, blue", id: "photo-1618677831708-0e7fda3148b4", tint: 0x3c5aa8),
        .photo("Dune", note: "Landscape", id: "photo-1785270101102-fe2b694a024f", tint: 0xc48a52),
        .type("A", note: "Type, cobalt", lines: ["A"], ink: 0xffffff, paper: 0x1f3fd1),
        .photo("Profile", note: "Portrait, black and white", id: "photo-1657412235453-8adb25a45cd0", tint: 0x1e1e1e),
        .photo("Lavender", note: "Dried flowers", id: "photo-1772441835051-4d32e651661d", tint: 0x6a5a8a),
        .photo("Mask", note: "Sculpture, dark", id: "photo-1561910377-c2a014ffd1cd", tint: 0x141414),
        .photo("Pink", note: "Object, magenta", id: "photo-1628558422453-576457df4b0d", tint: 0xc2337f),
        .photo("Cobalt", note: "Portrait, blue", id: "photo-1629511565591-a1d494ad6c58", tint: 0x22356b),
        .type("Room", note: "Type, three times", lines: ["ROOM", "ROOM", "ROOM"], ink: 0xffffff, paper: 0x0b0b0b),
        .photo("Pair", note: "Still life, colour", id: "photo-1655388446578-741cb6f55d14", tint: 0xd8c6c3),
        .photo("Director", note: "Object, chair", id: "photo-1758491037354-26d601d3cadd", tint: 0xe3e0da),
        .photo("Gaze", note: "Portrait, editorial", id: "photo-1506863530036-1efeddceb993", tint: 0x343434),
        .photo("Grey Room", note: "Portrait, studio", id: "photo-1768247695726-022586dea3a1", tint: 0x0e0e0e),
        .photo("Scarlet", note: "Portrait, red", id: "photo-1687360440648-ec9708d52086", tint: 0xaa564a),
        .photo("Azure", note: "Portrait, blue", id: "photo-1675790991952-4006c7a67cad", tint: 0x1d1315),
        .photo("Marble", note: "Sculpture, bust", id: "photo-1637666505754-7416ebd70cbf", tint: 0xc5c7cb),
        .photo("Cast", note: "Sculpture, plaster", id: "photo-1447758902204-48010b87c24d", tint: 0x8b8e8f),
        .photo("Vessel", note: "Ceramics", id: "photo-1631125915902-d8abe9225ff2", tint: 0xa09989),
        .photo("Pause", note: "Still life", id: "photo-1604935067269-27c7e8b36618", tint: 0xb6a899),
        .photo("Speaker", note: "Object", id: "photo-1604782206219-3b9576575203", tint: 0xac8f77),
        .photo("Pampas", note: "Dried flowers", id: "photo-1772510758813-a45fc1570a2c", tint: 0xb58c6d),
        .photo("Midnight", note: "Flowers", id: "photo-1783680599080-abe4d5634c3a", tint: 0x342217),
        .photo("Cut Out", note: "Fashion", id: "photo-1603189343302-e603f7add05a", tint: 0x727272),
        .photo("Atelier", note: "Fashion, studio", id: "photo-1717765698239-88731a968d20", tint: 0x5f5a54),
        .photo("Noir", note: "Fashion, black and white", id: "photo-1592833578500-1082e18665a3", tint: 0xc1c1c1),
        .photo("Plain Oak", note: "Object, chair", id: "photo-1640938776314-4d303f8a1380", tint: 0xccb048),
        .photo("Linen", note: "Portrait, editorial", id: "photo-1762291629616-3e2c044c79a0", tint: 0x25201f),
        .photo("Low Key", note: "Portrait, studio", id: "photo-1557053910-d9eadeed1c58", tint: 0xb16934),
        .type("N", note: "Type, red", lines: ["N"], ink: 0xe5322d, paper: 0xf2efe8),
        .photo("Crimson", note: "Portrait, red", id: "photo-1727341556787-7292f7515aec", tint: 0x6f0703),
        .photo("Marine", note: "Portrait, blue", id: "photo-1752665696094-b733943393d9", tint: 0xa37a62),
        .photo("Pale", note: "Sculpture, bust", id: "photo-1729008712499-5e6b58420789", tint: 0x303030),
        .photo("Chalk", note: "Sculpture, plaster", id: "photo-1563301323-094e5843a962", tint: 0x948778),
        .photo("Glaze", note: "Ceramics", id: "photo-1597696929736-6d13bed8e6a8", tint: 0xcbbfb4),
        .photo("Balance", note: "Still life", id: "photo-1705684989920-2ae5e583c0e7", tint: 0x221e18),
        .photo("Arcs", note: "Object", id: "photo-1783372519845-6cc782fb0f68", tint: 0x6c6b6b),
        .photo("Bouquet", note: "Dried flowers", id: "photo-1586244897859-2cd81e1cad1f", tint: 0xe0ddd5),
        .type("Hi.", note: "Type, black on white", lines: ["Hi."], ink: 0x111111, paper: 0xffffff),
        .photo("Meadow", note: "Flowers", id: "photo-1762969217477-af1c7b85db17", tint: 0x806645),
        .photo("High Sky", note: "Fashion", id: "photo-1574015974293-817f0ebebb74", tint: 0x4a4a4a),
        .photo("Contrast", note: "Fashion, black and white", id: "photo-1754315052701-0b98f6ba786b", tint: 0x5f6165),
        .photo("Tulip", note: "Object, chair", id: "photo-1659246973603-748505504837", tint: 0xad9383),
        .photo("Dusk", note: "Portrait, editorial", id: "photo-1765334666990-1bf2ce733864", tint: 0x403c3b),
        .photo("Halo", note: "Portrait, studio", id: "photo-1770327960728-ad08ea1e921e", tint: 0x4b4b4b),
        .photo("Ember", note: "Portrait, red", id: "photo-1727341557531-343cd4364501", tint: 0x653e2a),
        .photo("Relic", note: "Sculpture, bust", id: "photo-1570877901348-07076379b061", tint: 0x544844),
        .photo("Gesso", note: "Sculpture, plaster", id: "photo-1742495211859-d2e95a1e9354", tint: 0x563f2d),
        .photo("Urn", note: "Ceramics", id: "photo-1631125915732-b98f8774f675", tint: 0xaca9a7),
        .photo("Rest", note: "Still life", id: "photo-1615020977625-6c5092907e9d", tint: 0xc4c3b9),
        .photo("Prism", note: "Object", id: "photo-1620987278429-ab178d6eb547", tint: 0xd7c7ad),
        .photo("Thistle", note: "Dried flowers", id: "photo-1770816307374-d1ee7e423342", tint: 0xac8d76),
        .photo("Orchard", note: "Flowers", id: "photo-1554482091-af22387a7a0c", tint: 0x7d777a),
        .photo("Fringe", note: "Fashion", id: "photo-1562151270-c7d22ceb586a", tint: 0xa2b6b6),
        .photo("Armchair", note: "Object, chair", id: "photo-1722357533644-9b20e356ba5f", tint: 0xab9685),
        .photo("Hush", note: "Portrait, editorial", id: "photo-1674932668403-33398b81c92f", tint: 0x80746b),
        .photo("Matte", note: "Portrait, studio", id: "photo-1535713875002-d1d0cf377fde", tint: 0xa39ea1),
        .photo("Bust", note: "Sculpture, bust", id: "photo-1747191092806-c7da11881986", tint: 0x837c73),
        .photo("Moulded", note: "Sculpture, plaster", id: "photo-1781707870248-e2607e82bac0", tint: 0xa5a5a5),
        .photo("Loop", note: "Ceramics", id: "photo-1643569556871-91ec60671ed7", tint: 0xafa190),
        .type("Look", note: "Type, cobalt on white", lines: ["LOOK", "LOOK"], ink: 0x1f3fd1, paper: 0xffffff),
        .type("O", note: "Type, white on red", lines: ["O"], ink: 0xffffff, paper: 0xe5322d),
        .photo("Duo", note: "Still life", id: "photo-1608575474456-55113364dd66", tint: 0x959085),
        .photo("Plinth", note: "Object", id: "photo-1621544170639-fb0189e518ba", tint: 0xd6d2d1),
        .photo("Harvest", note: "Dried flowers", id: "photo-1604304194650-3ba3cfa752fd", tint: 0xc7c0b2),
        .photo("Drape", note: "Fashion", id: "photo-1662532577856-e8ee8b138a8b", tint: 0x644a7a),
        .photo("Slats", note: "Object, chair", id: "photo-1572297794744-a19043e308f8", tint: 0xcab2ac),
        .photo("Copper", note: "Portrait, editorial", id: "photo-1606143412458-acc5f86de897", tint: 0x2c2c2a),
        .photo("Studio", note: "Portrait, studio", id: "photo-1768247695735-e10baa73e917", tint: 0x181818),
        .photo("Classic", note: "Sculpture, bust", id: "photo-1565170556450-bdfc81864ca1", tint: 0x1a1924),
        .type("Gap", note: "Type, black on paper", lines: ["GAP"], ink: 0x0b0b0b, paper: 0xf2efe8),
        .photo("Kiln", note: "Ceramics", id: "photo-1481401908818-600b7a676c0d", tint: 0x84938b),
        .photo("Facet", note: "Object", id: "photo-1703843943664-867123b25898", tint: 0xbab6b0),
        .photo("Wild Oat", note: "Dried flowers", id: "photo-1615851908107-5047d87d0327", tint: 0xdcd7d9),
        .photo("Tailor", note: "Fashion", id: "photo-1779399147755-cb3c1a62c3de", tint: 0x8b9598),
        .photo("Navy", note: "Object, chair", id: "photo-1748887522222-ae67e761fb64", tint: 0x899198),
        .photo("Tilt", note: "Portrait, editorial", id: "photo-1774567753803-f26f0add5800", tint: 0x2d2d2d),
        .photo("Terracotta", note: "Ceramics", id: "photo-1526198049595-f32cde2a219d", tint: 0xe1e2e3),
        .photo("Field", note: "Dried flowers", id: "photo-1546810738-b0c7fe348c6b", tint: 0xbdc5bf),
        .photo("Swing", note: "Fashion", id: "photo-1596993100471-c3905dafa78e", tint: 0x939278),
        .photo("Glance", note: "Portrait, editorial", id: "photo-1772566737819-d8898e6580a4", tint: 0x222222),
        .photo("Quiet", note: "Portrait, editorial", id: "photo-1563170446-9c3c0622d8a9", tint: 0x31241c),
        .photo("Still", note: "Portrait, editorial", id: "photo-1612928414075-bc722ade44f1", tint: 0x878787),
    ]
}
