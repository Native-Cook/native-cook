import CoreText
import SwiftUI
import UIKit

enum SaveToCollectionTokens {
    enum Palette {
        static let page = Color(red: 10 / 255, green: 10 / 255, blue: 11 / 255)
        static let ink = Color(red: 244 / 255, green: 244 / 255, blue: 242 / 255)
        static let mute = ink.opacity(0.56)
        static let faint = ink.opacity(0.32)
        static let tile = Color(red: 24 / 255, green: 24 / 255, blue: 26 / 255)
        static let hairline = Color.white.opacity(0.12)
        static let rim = Color.white.opacity(0.22)
        static let glassFill = Color(red: 30 / 255, green: 30 / 255, blue: 32 / 255).opacity(0.42)
        static let sheetFill = Color(red: 22 / 255, green: 22 / 255, blue: 24 / 255).opacity(0.72)
        static let scrim = Color.black.opacity(0.50)
        static let ghost = Color.white.opacity(0.10)
        static let finger = Color.white.opacity(0.16)
        static let fingerEdge = Color.white.opacity(0.40)
    }

    enum Layout {
        static let edge: CGFloat = 16
        static let gap: CGFloat = 8
        static let radius: CGFloat = 14
        static let titleHeight: CGFloat = 44
        static let chipsTop: CGFloat = 52
        static let chipHeight: CGFloat = 32
        static let gridTop: CGFloat = 100
        static let barHeight: CGFloat = 60
        static let barGap: CGFloat = 8
        static let saveSize: CGFloat = 60
        static let sheetTop: CGFloat = 72
        static let sheetRadius: CGFloat = 32
        static let finger: CGFloat = 44
        static let sheetCaption: CGFloat = 48
        static let columnHeights: [[CGFloat]] = [[236, 196, 252], [196, 268, 224]]
    }

    enum FontName {
        static let regular = "Inter24pt-Regular"
        static let medium = "Inter-Medium"
        static let semibold = "Inter-SemiBold"
    }

    enum Motion {
        static let easeOut = (0.23, 1.0, 0.32, 1.0)
        static let easeInOut = (0.77, 0.0, 0.175, 1.0)
        static let lift = (duration: 0.380, damping: 1.0)
        static let home = (duration: 0.420, damping: 0.82)
        static let gulp = (duration: 0.360, damping: 0.55)
        static let sheet = (duration: 0.480, damping: 0.88)
        static let reduced = (duration: 0.200, damping: 1.0)
        static let drop = 0.260
        static let roll = 0.220
        static let toastIn = 0.220
        static let toastOut = 0.160
        static let sheetOut = 0.300
        static let arriveDelay = 0.380
        static let arrive = 0.420
        static let mark = 0.200
        static let finger = 0.150
        static let pressOut = 0.150
        static let hold = 0.300
        static let resume = 2.0
        static let tidy = 0.600
    }

    enum Save {
        static let liftScale: CGFloat = 0.04
        static let liftRise: CGFloat = 4
        static let magnetFar: CGFloat = 150
        static let magnetNear: CGFloat = 36
        static let magnetPull: CGFloat = 0.38
        static let magnetShrink: CGFloat = 0.55
        static let dropRadius: CGFloat = 64
        static let lean: CGFloat = 0.012
        static let leanMax: CGFloat = 10
        static let countBefore = 12
    }

    enum Demo {
        static let tile = 4
        static let fingerIn = 0.900
        static let down = 1.000
        static let lift = 1.350
        static let carryStart = 1.450
        static let carryEnd = 2.750
        static let release = 2.850
        static let tapIn = 3.850
        static let tap = 4.050
        static let close = 7.300
        static let reset = 8.600
    }

    struct Photo: Identifiable {
        let id: String
        let title: String
        let place: String
        let asset: String
    }

    static let photos: [Photo] = [
        .init(id: "boardwalk", title: "Boardwalk to the dunes", place: "Sylt", asset: "SaveToCollection-boardwalk"),
        .init(id: "village", title: "Last light on the cliff", place: "Oia", asset: "SaveToCollection-village"),
        .init(id: "road", title: "Road into the valley", place: "Utah", asset: "SaveToCollection-road"),
        .init(id: "pool", title: "Pool under the palms", place: "Bali", asset: "SaveToCollection-pool"),
        .init(id: "lake", title: "Still water, early", place: "Alberta", asset: "SaveToCollection-lake"),
        .init(id: "shore", title: "Shoreline from above", place: "Maldives", asset: "SaveToCollection-shore"),
    ]
    static let collectionBefore = [5, 1, 3]
    static let collectionName = "Slow coast"

    enum Glyph {
        case home
        case search
        case inbox
        case bookmark
        case close
    }
}

enum SaveToCollectionAssets {
    private static let fontFiles = [
        "SaveToCollection-Inter-Regular",
        "SaveToCollection-Inter-Medium",
        "SaveToCollection-Inter-SemiBold",
    ]

    static let registerFonts: Void = {
        for file in fontFiles {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static let images: [UIImage] = SaveToCollectionTokens.photos.compactMap { photo in
        guard let url = Bundle.main.url(forResource: photo.asset, withExtension: "jpg") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    static func image(_ index: Int) -> Image {
        guard images.indices.contains(index) else { return Image(systemName: "photo") }
        return Image(uiImage: images[index])
    }

    static func font(_ name: String, size: CGFloat) -> Font {
        _ = registerFonts
        return .custom(name, fixedSize: size)
    }
}

struct SaveToCollectionLayout: Equatable {
    let size: CGSize
    let top: CGFloat
    let bottom: CGFloat
    let columnWidth: CGFloat
    let tiles: [CGRect]
    let barY: CGFloat
    let save: CGPoint
    let sheetY: CGFloat

    init(size: CGSize, top: CGFloat, bottom: CGFloat) {
        self.size = size
        self.top = top
        self.bottom = bottom
        columnWidth = (size.width - SaveToCollectionTokens.Layout.edge * 2 - SaveToCollectionTokens.Layout.gap) / 2
        let scale = columnWidth / 181
        let gridY = top + SaveToCollectionTokens.Layout.gridTop
        var frames: [CGRect] = []
        for column in 0 ..< 2 {
            var y = gridY
            for rawHeight in SaveToCollectionTokens.Layout.columnHeights[column] {
                let height = (rawHeight * scale).rounded()
                let x = SaveToCollectionTokens.Layout.edge + CGFloat(column) * (columnWidth + SaveToCollectionTokens.Layout.gap)
                frames.append(CGRect(x: x, y: y, width: columnWidth, height: height))
                y += height + SaveToCollectionTokens.Layout.gap
            }
        }
        tiles = frames
        barY = size.height - max(bottom, SaveToCollectionTokens.Layout.edge)
            - SaveToCollectionTokens.Layout.barGap - SaveToCollectionTokens.Layout.barHeight
        save = CGPoint(
            x: size.width - SaveToCollectionTokens.Layout.edge - SaveToCollectionTokens.Layout.saveSize / 2,
            y: barY + SaveToCollectionTokens.Layout.barHeight / 2
        )
        sheetY = top + SaveToCollectionTokens.Layout.sheetTop
    }

    func tile(at point: CGPoint) -> Int? {
        tiles.firstIndex { $0.contains(point) && point.y < barY - 8 }
    }
}

struct SaveToCollectionPresentation {
    var carryIndex: Int?
    var centre: CGPoint = .zero
    var lift: CGFloat = 0
    var lean: CGFloat = 0
    var pull: CGFloat = 0
    var drop: CGFloat = 0
    var gulp: CGFloat = 1
    var roll: CGFloat = 1
    var markedIndex: Int?
    var mark: CGFloat = 0
    var toast: CGFloat = 0
    var sheet: CGFloat = 0
    var arrive: CGFloat = 1
    var press: CGFloat = 0
    var count = SaveToCollectionTokens.Save.countBefore
    var ghostCentre: CGPoint = .zero
    var ghostOpacity: CGFloat = 0
    var ghostPress: CGFloat = 0

    static let rest = SaveToCollectionPresentation()
}

enum SaveToCollectionMath {
    static func clamp(_ value: CGFloat) -> CGFloat { min(max(value, 0), 1) }

    static func segment(_ time: Double, _ start: Double, _ end: Double) -> CGFloat {
        guard end > start else { return time >= end ? 1 : 0 }
        return clamp(CGFloat((time - start) / (end - start)))
    }

    static func mix(_ from: CGFloat, _ to: CGFloat, _ progress: CGFloat) -> CGFloat {
        from + (to - from) * clamp(progress)
    }

    static func easeOut(_ progress: CGFloat) -> CGFloat {
        cubicBezier(progress, SaveToCollectionTokens.Motion.easeOut)
    }

    static func easeInOut(_ progress: CGFloat) -> CGFloat {
        cubicBezier(progress, SaveToCollectionTokens.Motion.easeInOut)
    }

    static func smoothstep(edge0: CGFloat, edge1: CGFloat, value: CGFloat) -> CGFloat {
        let t = clamp((value - edge0) / (edge1 - edge0))
        return t * t * (3 - 2 * t)
    }

    static func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
        hypot(a.x - b.x, a.y - b.y)
    }

    static func spring(_ elapsed: Double, duration: Double, damping: Double) -> CGFloat {
        guard elapsed > 0 else { return 0 }
        guard elapsed < duration else { return 1 }
        let p = elapsed / duration
        if damping >= 0.999 {
            let value = 1 - exp(-7.0 * p) * (1 + 7.0 * p)
            let end = 1 - exp(-7.0) * 8
            return CGFloat(value / end)
        }
        let decay = 7.5 * damping
        let waves = 1.25 + (1 - damping) * 1.5
        let value = 1 - exp(-decay * p) * cos(2 * .pi * waves * p)
        return CGFloat(value)
    }

    static func cubicBezier(
        _ progress: CGFloat,
        _ control: (Double, Double, Double, Double)
    ) -> CGFloat {
        let x = Double(clamp(progress))
        var low = 0.0
        var high = 1.0
        var t = x
        for _ in 0 ..< 14 {
            t = (low + high) / 2
            if cubic(t, control.0, control.2) < x { low = t } else { high = t }
        }
        return CGFloat(cubic(t, control.1, control.3))
    }

    private static func cubic(_ t: Double, _ first: Double, _ second: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * first + 3 * u * t * t * second + t * t * t
    }
}
