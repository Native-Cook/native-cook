import SwiftUI

// The SVG crests and portraits of EmeraldTierLeaderboard.tsx (Crest, LockedCrest, Avatar).

enum EmeraldTierCrestTone: String {
    case emerald, sapphire, diamond

    struct Palette { let light, dark, base, inner, edge, lock, hole: String }
    var palette: Palette {
        switch self {
        case .emerald: return Palette(light: "#a6e1c5", dark: "#297e62", base: "#549f88", inner: "#39957b", edge: "#8ed6bd", lock: "#b7e7d2", hole: "#4d9e80")
        case .sapphire: return Palette(light: "#b7d2ff", dark: "#2a58a8", base: "#4f74b4", inner: "#3b63aa", edge: "#a3c4ff", lock: "#dce8ff", hole: "#3b63aa")
        case .diamond: return Palette(light: "#eef6fa", dark: "#71899a", base: "#8aa1b0", inner: "#6f8898", edge: "#dbe9f1", lock: "#f3f8fb", hole: "#6f8898")
        }
    }
}

/// viewBox 0 0 100 112, drawn at 1:1 in a 100 x 112 box.
struct EmeraldTierCrest: View {
    var locked = false
    var pale = false
    var letter = "E"
    var tone = EmeraldTierCrestTone.emerald

    private static func color(_ hex: String) -> Color { EmeraldTierPalette.color(hex) }

    var body: some View {
        let lockTone = tone.palette
        // SVG x1 0 y1 0 x2 1 y2 1 over the shield's bounding box (9...91, 0...105) in the 100 x 112 box.
        let gradient = LinearGradient(
            colors: [Self.color(locked ? lockTone.light : pale ? "#ffe2ac" : "#ffc283"),
                     Self.color(locked ? lockTone.dark : pale ? "#e4965a" : "#b9410e")],
            startPoint: UnitPoint(x: 0.09, y: 0), endPoint: UnitPoint(x: 0.91, y: 105.0 / 112.0))
        ZStack(alignment: .topLeading) {
            // M8 30 0 43 13 66 23 96 50 110 78 96 89 67 100 43 91 31Z
            Path { p in
                p.move(to: CGPoint(x: 8, y: 30))
                for pt in [(0.0, 43.0), (13, 66), (23, 96), (50, 110), (78, 96), (89, 67), (100, 43), (91, 31)] { p.addLine(to: CGPoint(x: pt.0, y: pt.1)) }
                p.closeSubpath()
            }.fill(Self.color(locked ? lockTone.base : "#b65a24"))
            // m50 0 41 22-4 61-19 22H32L13 83 9 22Z
            Path { p in
                p.move(to: CGPoint(x: 50, y: 0))
                for pt in [(91.0, 22.0), (87, 83), (68, 105), (32, 105), (13, 83), (9, 22)] { p.addLine(to: CGPoint(x: pt.0, y: pt.1)) }
                p.closeSubpath()
            }.fill(gradient)
            // m50 14 28 16-3 47-14 15H39L25 77l-3-47Z
            let inner = Path { p in
                p.move(to: CGPoint(x: 50, y: 14))
                for pt in [(78.0, 30.0), (75, 77), (61, 92), (39, 92), (25, 77), (22, 30)] { p.addLine(to: CGPoint(x: pt.0, y: pt.1)) }
                p.closeSubpath()
            }
            inner.fill(Self.color(locked ? lockTone.inner : "#d66d2e"))
            inner.stroke(Self.color(locked ? lockTone.edge : "#ffd49e"), style: StrokeStyle(lineWidth: 4, lineJoin: .miter))
            if locked {
                // M40 49v-9a10 10 0 0 1 20 0v9 : a semicircle over the top.
                Path { p in
                    let k = 5.523
                    p.move(to: CGPoint(x: 40, y: 49))
                    p.addLine(to: CGPoint(x: 40, y: 40))
                    p.addCurve(to: CGPoint(x: 50, y: 30), control1: CGPoint(x: 40, y: 40 - k), control2: CGPoint(x: 50 - k, y: 30))
                    p.addCurve(to: CGPoint(x: 60, y: 40), control1: CGPoint(x: 50 + k, y: 30), control2: CGPoint(x: 60, y: 40 - k))
                    p.addLine(to: CGPoint(x: 60, y: 49))
                }.stroke(Self.color(lockTone.lock), style: StrokeStyle(lineWidth: 6, lineJoin: .miter))
                RoundedRectangle(cornerRadius: 5).fill(Self.color(lockTone.lock)).frame(width: 34, height: 27).offset(x: 33, y: 48)
                Circle().fill(Self.color(lockTone.hole)).frame(width: 8, height: 8).offset(x: 46, y: 55)
            } else {
                // <SvgText x=50 y=76 fontSize=51 bold, anchored at its middle>: the baseline sits at y = 76.
                Text(letter)
                    .font(.system(size: 51, weight: .bold))
                    .foregroundStyle(Self.color("#fff0cc"))
                    .fixedSize()
                    .frame(width: 100)
                    .alignmentGuide(.top) { d in d[.firstTextBaseline] - 76 }
            }
        }
        .frame(width: 100, height: 112, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

/// A locked crest keeps the design's green ghost at rest and takes its own league's tone in focus.
struct EmeraldTierLockedCrest: View {
    let index: Int
    let tone: EmeraldTierCrestTone
    let focus: Double

    var body: some View {
        ZStack {
            EmeraldTierCrest(locked: true)
            EmeraldTierCrest(locked: true, tone: tone).opacity(max(0, 1 - abs(Double(index) - focus)))
        }
        .frame(width: 100, height: 112)
    }
}

/// Square crops of the atlas, cut once.
enum EmeraldTierAvatarArt {
    /// [x, y, diameter] in atlas pixels (CROPS).
    static let crops: [String: [Double]] = [
        "ari": [379, 449, 98], "milo": [230, 513, 82], "juno": [549, 525, 82],
        "theo": [245, 760, 47], "noa": [245, 827, 47], "kai": [245, 894, 47],
    ]
    private static let cache: [String: UIImage] = {
        var result: [String: UIImage] = [:]
        for (name, c) in crops {
            if let cg = EmeraldTierImages.atlas.cgImage?.cropping(to: CGRect(x: c[0], y: c[1], width: c[2], height: c[2])) {
                result[name] = UIImage(cgImage: cg)
            }
        }
        // Theo's viewBox is the whole 1254 x 1254 portrait.
        result["theo"] = EmeraldTierImages.theoPortrait
        return result
    }()
    static func image(_ name: String) -> UIImage { cache[name] ?? UIImage() }
}

/// Circle portrait: 1.5pt #ffffff60 border, #2c5747 behind, the crop clipped to the inscribed circle.
struct EmeraldTierAvatar: View {
    let name: String
    let size: Double

    var body: some View {
        ZStack {
            Circle().fill(EmeraldTierPalette.color("#2c5747"))
            Image(uiImage: EmeraldTierAvatarArt.image(name))
                .resizable()
                .interpolation(.high)
                .frame(width: size - 3, height: size - 3)
                .clipShape(Circle())
            Circle().strokeBorder(EmeraldTierPalette.color("#ffffff60"), lineWidth: 1.5)
        }
        .frame(width: size, height: size)
    }
}
