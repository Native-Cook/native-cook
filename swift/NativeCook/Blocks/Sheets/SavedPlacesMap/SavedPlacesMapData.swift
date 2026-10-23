import CoreGraphics

struct SavedPlacesMapMarker: Identifiable {
    enum Tag { case saved, liked }
    let id: String
    let x: Double
    let baseline: Double
    let name: String
    let subtitle: String
    let tag: Tag
    let emoji: String
    let emojiSize: Double

    var center: CGPoint { CGPoint(x: x - SavedPlacesMapData.markerDiscDx, y: baseline - SavedPlacesMapData.markerDiscDy) }
}

enum SavedPlacesMapGlyphKind { case bookmark, thumb, star }

struct SavedPlacesMapGlyph: Identifiable {
    let id: Int
    let x: Double
    let y: Double
    let kind: SavedPlacesMapGlyphKind
}

struct SavedPlacesMapGlyphPath: Identifiable {
    let id: Int
    let glyph: SavedPlacesMapGlyph
    let target: CGPoint
    let delay: Double
    let cluster: Int
}

struct SavedPlacesMapCluster: Identifiable {
    let id: String
    let center: CGPoint
    let bubble: CGPoint
    let count: Int
    let delays: [Double]
}

enum SavedPlacesMapData {
    static let markerDisc = 100.0
    static let markerDiscDx = 63.0
    static let markerDiscDy = 12.0
    static let markerIconDx = 36.0
    static let markerBookmarkDy = 58.0
    static let markerSavedDy = 39.0
    static let markerSubDy = 31.0
    static let markerNameSize = 29.9
    static let markerSubSize = 25.8
    static let markerHitW = 240.0
    static let visitedIndex = 1
    static let radius = markerDisc / 2

    static let markers = [
        SavedPlacesMapMarker(id: "lumen", x: 845, baseline: 444, name: "Lumen Coffee", subtitle: "coffee & pastries", tag: .saved, emoji: "☕", emojiSize: 15.6),
        SavedPlacesMapMarker(id: "mabel", x: 419, baseline: 742.5, name: "Mabel's Corner Café", subtitle: "piano singalong", tag: .saved, emoji: "🎭", emojiSize: 17),
        SavedPlacesMapMarker(id: "linden", x: 500, baseline: 946, name: "Linden Wick", subtitle: "pourover coffee", tag: .saved, emoji: "☕", emojiSize: 15.6),
        SavedPlacesMapMarker(id: "fern", x: 847, baseline: 926, name: "Pale Fern", subtitle: "nordic roastery", tag: .liked, emoji: "☕", emojiSize: 15.6),
        SavedPlacesMapMarker(id: "aurelle", x: 564, baseline: 1141, name: "Cafe Aurelle", subtitle: "parisian cafe", tag: .saved, emoji: "☕", emojiSize: 15.6),
    ]

    static let glyphs: [SavedPlacesMapGlyph] = {
        let raw: [(Double, Double, SavedPlacesMapGlyphKind)] = [
            (453, 646, .bookmark), (424, 649, .bookmark), (460, 745, .bookmark), (462, 759, .bookmark),
            (647, 810, .bookmark), (357, 835, .bookmark), (549, 850, .bookmark), (402, 872, .bookmark),
            (626, 883, .bookmark), (710, 934, .bookmark), (406, 1044, .bookmark), (608, 1052, .bookmark),
            (667, 1090, .bookmark), (633, 1151, .bookmark), (616, 1173, .bookmark),
            (399, 785, .thumb), (419, 786, .thumb), (608, 809, .thumb), (372, 813, .thumb),
            (437, 814, .thumb), (414, 815, .thumb), (396, 830, .thumb), (588, 841, .thumb),
            (418, 853, .thumb), (571, 861, .thumb), (647, 865, .thumb), (671, 913, .thumb),
            (775, 1005, .thumb), (251, 1129, .thumb), (206, 1135, .thumb), (171, 1186, .thumb),
            (113, 1192, .thumb), (223, 1195, .thumb),
            (580, 404, .star), (730, 563, .star), (722, 565, .star), (457, 815, .star), (859, 941, .star),
        ]
        return raw.enumerated().map { SavedPlacesMapGlyph(id: $0.offset, x: $0.element.0, y: $0.element.1, kind: $0.element.2) }
    }()

    static let glyphPaths: [SavedPlacesMapGlyphPath] = {
        let nearest = glyphs.map { glyph -> (cluster: Int, distance: Double) in
            var best = 0
            var distance = Double.greatestFiniteMagnitude
            for (index, marker) in markers.enumerated() {
                let value = hypot(glyph.x - marker.center.x, glyph.y - marker.center.y)
                if value < distance { best = index; distance = value }
            }
            return (best, distance)
        }
        let order = nearest.indices.sorted { nearest[$0].distance < nearest[$1].distance }
        var ranks = Array(repeating: 0, count: glyphs.count)
        for (rank, index) in order.enumerated() { ranks[index] = rank }
        let last = max(1, glyphs.count - 1)
        return glyphs.enumerated().map { index, glyph in
            let cluster = nearest[index].cluster
            let center = markers[cluster].center
            return SavedPlacesMapGlyphPath(
                id: glyph.id,
                glyph: glyph,
                target: CGPoint(x: center.x + SavedPlacesMapTheme.bubbleDx, y: center.y + SavedPlacesMapTheme.bubbleDy),
                delay: SavedPlacesMapMotion.CLUSTER.span * Double(ranks[index]) / Double(last),
                cluster: cluster
            )
        }
    }()

    static let clusters: [SavedPlacesMapCluster] = markers.enumerated().map { index, marker in
        let paths = glyphPaths.filter { $0.cluster == index }
        let center = marker.center
        return SavedPlacesMapCluster(
            id: marker.id,
            center: center,
            bubble: CGPoint(x: center.x + SavedPlacesMapTheme.bubbleDx, y: center.y + SavedPlacesMapTheme.bubbleDy),
            count: paths.count,
            delays: paths.map(\.delay)
        )
    }

    static func glyphSize(_ kind: SavedPlacesMapGlyphKind) -> CGSize {
        switch kind {
        case .bookmark: CGSize(width: 28, height: 32)
        case .thumb: CGSize(width: 34, height: 38)
        case .star: CGSize(width: 32, height: 31)
        }
    }
}
