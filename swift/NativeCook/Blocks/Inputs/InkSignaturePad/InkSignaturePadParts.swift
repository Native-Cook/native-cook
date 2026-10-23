import SwiftUI
import UIKit

// MARK: - Script

/// The signature autoPlay writes: "Mara Ellison" in a quick cursive hand, an i-dot and an underline
/// flourish. Each stroke is a run of key points in a small grid (x 0…102, the letters' baseline at
/// y 40.5, the x-height 9 units above it and the loops of the capitals and the l's up to y 12), drawn
/// through as a Catmull-Rom curve and timed like a hand: slower in the tight turns, faster on the
/// long runs, quickest on the flourish.
enum InkSignaturePadScript {
    private struct Stroke {
        /// Pause before the pen lands, in seconds.
        let gap: Double
        /// Speed on a straight run, in grid units a second.
        let speed: Double
        let keys: [(Double, Double)]
    }

    private static let baseline = 40.5
    // The hand leans right: x moves this many units per unit of height above the baseline.
    private static let slant = 0.22
    // "Ellison", its dot and nothing else start this far right of where their keys say, to open a
    // word space after "Mara".
    private static let wordSpace = 4.0

    private static let strokes: [Stroke] = [
        // M, a, r, a in one stroke.
        Stroke(gap: 0, speed: 235, keys: [
            (2, 41), (4, 33), (6.5, 20), (8.5, 13.5), (10, 13), (11, 16), (12, 26), (13, 38), (13.5, 40),
            (13.8, 36), (15, 24), (17, 16), (18.5, 15), (19.5, 18), (20.5, 28), (21.5, 38), (22.5, 40.5),
            (24.5, 39), (26.5, 35), (29, 32), (30, 31.5),
            (28, 31), (26.3, 33.5), (26.2, 37.5), (27.8, 40), (30, 39), (31.3, 35.5), (31.8, 31.5),
            (31.6, 36), (32, 39.8), (33.5, 40.3), (35, 38),
            (36.2, 33), (37.2, 31.2), (38.4, 32.6), (39.4, 31.4), (40, 33), (40.2, 38), (41, 40.2), (42.5, 38.5),
            (44.5, 33), (46, 31.6), (44, 31.2), (42.6, 34), (42.8, 38.2), (44.6, 40.2), (46.4, 38.6),
            (47.2, 34.5), (47.6, 31.2), (47.4, 36), (47.9, 39.8), (49.5, 40.4), (51.5, 38),
        ]),
        // E, l, l, i, s, o, n in one stroke.
        Stroke(gap: 0.26, speed: 245, keys: [
            (62, 17), (60.5, 14.5), (57.5, 14), (55.5, 16.5), (56.5, 21), (59.5, 24),
            (56, 25), (53.5, 28.5), (53.5, 34), (55.5, 39), (59.5, 40.5), (63, 38.5), (65, 35),
            (67, 28), (69, 19), (69.5, 14), (68.5, 12), (67.2, 13.5), (66.8, 19), (67.2, 30), (67.8, 38.5),
            (69, 40.4), (70.8, 39),
            (72.5, 30), (74, 20), (74.3, 14.5), (73.3, 12.6), (72.1, 14.2), (71.8, 20), (72.2, 31), (72.8, 39),
            (74, 40.5), (75.6, 38.4),
            (76.6, 34), (77, 31.5), (77.1, 36), (77.6, 40), (79, 40), (80.3, 37.5),
            (81.6, 31.2), (83.6, 35.5), (83.8, 39), (82, 40.6), (80.6, 39.6), (82.4, 40.4), (84.6, 39),
            (86.5, 33), (88.4, 31.4), (86.6, 32.5), (85.9, 36.5), (87.2, 40.2), (89.4, 39.6), (90.3, 35.5),
            (89.4, 31.8), (88.3, 32.4), (90.5, 32.8), (92, 32.5),
            (93, 32), (93.4, 36), (93.4, 40.2), (93.8, 35), (95.6, 31.6), (97.4, 32.6), (97.8, 36.5),
            (98, 40), (99.5, 40.6), (101.5, 38),
        ]),
        // The dot of the i.
        Stroke(gap: 0.22, speed: 60, keys: [(77.6, 25.6), (78.4, 25.1)]),
        // The flourish under the name, right to left and back.
        Stroke(gap: 0.24, speed: 420, keys: [
            (99, 44.5), (85, 46), (65, 47), (45, 47.2), (25, 46.5), (12, 45), (7, 43), (9.5, 41.8), (14, 42.3),
        ]),
    ]

    // Samples are taken about this far apart along a stroke, in grid units.
    private static let step = 0.45
    // How much a turn slows the hand: speed × (FLOOR + (1 − FLOOR) / (1 + TURN · curvature)).
    private static let floorShare = 0.32
    private static let turnK = 9.0
    // The pen speeds up over its first RAMP units and slows over its last RAMP units.
    private static let ramp = 5.0

    /// The key points drawn through as one smooth curve, sampled about `step` apart.
    private static func densify(_ keys: [(Double, Double)]) -> [(Double, Double)] {
        var out = [keys[0]]
        for i in 0..<(keys.count - 1) {
            let p0 = keys[max(0, i - 1)]
            let p1 = keys[i]
            let p2 = keys[i + 1]
            let p3 = keys[min(keys.count - 1, i + 2)]
            let steps = max(1, Int(ceil(hypot(p2.0 - p1.0, p2.1 - p1.1) / step)))
            for k in 1...steps {
                let u = Double(k) / Double(steps)
                out.append((InkSignaturePadInk.catmull(p0.0, p1.0, p2.0, p3.0, u), InkSignaturePadInk.catmull(p0.1, p1.1, p2.1, p3.1, u)))
            }
        }
        return out
    }

    /// The signature laid out between `left` and `right` with its baseline at `baseline` (pt), timed
    /// sample by sample, as ink samples (x, y, ms from its start, 0, stroke), and how many strokes.
    static func samples(left: Double, right: Double, baseline line: Double) -> (samples: [Double], strokes: Int) {
        let laid = strokes.enumerated().map { index, s in
            s.keys.map { ($0.0 + (baseline - $0.1) * slant + (index == 1 || index == 2 ? wordSpace : 0), $0.1) }
        }
        let xs = laid.flatMap { $0.map(\.0) }
        let minX = xs.min() ?? 0
        let maxX = xs.max() ?? 1
        let unit = (right - left) / (maxX - minX)
        var out: [Double] = []
        var clock = 0.0
        for (index, keys) in laid.enumerated() {
            let s = strokes[index]
            clock += s.gap
            let points = densify(keys)
            // Arc length to every sample, for the ramps at both ends.
            var along = [0.0]
            for i in 1..<points.count {
                along.append(along[i - 1] + hypot(points[i].0 - points[i - 1].0, points[i].1 - points[i - 1].1))
            }
            let total = along[along.count - 1]
            for i in 0..<points.count {
                if i > 0 {
                    let ds = along[i] - along[i - 1]
                    // Curvature from the turn between the segment before and the one after.
                    let a = points[max(0, i - 1)]
                    let b = points[i]
                    let c = points[min(points.count - 1, i + 1)]
                    let h1 = atan2(b.1 - a.1, b.0 - a.0)
                    let h2 = atan2(c.1 - b.1, c.0 - b.0)
                    var turn = abs(h2 - h1)
                    if turn > .pi { turn = 2 * .pi - turn }
                    let curvature = turn / max(ds, 1e-3)
                    let ends = min(1, 0.35 + (0.65 * min(along[i], total - along[i])) / ramp)
                    let speed = s.speed * (floorShare + (1 - floorShare) / (1 + turnK * curvature)) * ends
                    clock += ds / max(speed, 1)
                }
                out.append(contentsOf: [left + (points[i].0 - minX) * unit, line + (points[i].1 - baseline) * unit, clock * 1000, 0, Double(index)])
            }
        }
        return (out, strokes.count)
    }
}

// MARK: - Wax seal

/// A disc of red sealing wax pressed with a monogram. The rim is not a circle: wax spreads unevenly,
/// so its radius wanders by a few points on three ripples.
struct InkSignaturePadSeal: View {
    static let size = 92.0
    private static let radius = 34.0
    private static let ring = 24.0
    private static let rim: Path = {
        let ripples: [(k: Double, amp: Double, phase: Double)] = [(5, 1.7, 0.3), (9, 1.1, 1.2), (14, 0.6, 2.1)]
        let c = size / 2
        var p = Path()
        let steps = 120
        for i in 0...steps {
            let a = Double(i) / Double(steps) * .pi * 2
            let r = ripples.reduce(radius) { $0 + $1.amp * sin($1.k * a + $1.phase) }
            let point = CGPoint(x: c + r * cos(a), y: c + r * sin(a))
            if i == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        p.closeSubpath()
        return p
    }()

    let monogram: String

    var body: some View {
        Canvas { context, _ in
            let c = Self.size / 2
            var wax = context
            wax.addFilter(.shadow(color: Color(.sRGB, red: 30 / 255, green: 4 / 255, blue: 6 / 255, opacity: 0.45), radius: 5, x: 0, y: 3))
            wax.fill(
                Self.rim,
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0xc8333a), location: 0),
                        .init(color: Color(hex: 0x9d1b23), location: 0.55),
                        .init(color: Color(hex: 0x6a0d14), location: 1),
                    ]),
                    center: CGPoint(x: c - 9, y: c - 11),
                    startRadius: 0,
                    endRadius: Self.radius * 1.35
                )
            )
            // The pressed ring: a dark groove with light catching its lower edge.
            let ring = Self.ring
            context.stroke(Path(ellipseIn: CGRect(x: c - ring, y: c + 0.8 - ring, width: ring * 2, height: ring * 2)), with: .color(Color(.sRGB, red: 1, green: 190 / 255, blue: 180 / 255, opacity: 0.22)), lineWidth: 1.4)
            context.stroke(Path(ellipseIn: CGRect(x: c - ring, y: c - ring, width: ring * 2, height: ring * 2)), with: .color(Color(.sRGB, red: 60 / 255, green: 0, blue: 8 / 255, opacity: 0.55)), lineWidth: 1.6)
            // The monogram, its baseline 7 pt under the centre, with a light edge below it.
            let font = UIFont(name: "Baskerville-SemiBold", size: 21) ?? .systemFont(ofSize: 21, weight: .semibold)
            let width = (monogram as NSString).size(withAttributes: [.font: font]).width
            let top = c + 7 - font.ascender
            context.draw(Text(monogram).font(Font(font)).foregroundColor(Color(.sRGB, red: 1, green: 190 / 255, blue: 180 / 255, opacity: 0.28)), at: CGPoint(x: c - width / 2 + 0.6, y: top + 0.9), anchor: .topLeading)
            context.draw(Text(monogram).font(Font(font)).foregroundColor(Color(hex: 0x5c0910)), at: CGPoint(x: c - width / 2, y: top), anchor: .topLeading)
            // A soft gloss on the upper left of the wax.
            context.fill(
                Path(ellipseIn: CGRect(x: c - 13 - 9, y: c - 14 - 9, width: 18, height: 18)),
                with: .radialGradient(
                    Gradient(colors: [Color(.sRGB, red: 1, green: 220 / 255, blue: 215 / 255, opacity: 0.35), Color(.sRGB, red: 1, green: 220 / 255, blue: 215 / 255, opacity: 0)]),
                    center: CGPoint(x: c - 13, y: c - 14),
                    startRadius: 0,
                    endRadius: 9
                )
            )
        }
        .frame(width: Self.size, height: Self.size)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum InkSignaturePadPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func address(_ id: String, width: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }

    static func cached(_ address: String) -> UIImage? {
        cache.object(forKey: address as NSString)
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }

    /// The rectangle `image` covers `rect` with, cropped about its centre.
    static func cover(_ image: CGSize, in rect: CGRect) -> CGRect {
        guard image.width > 0, image.height > 0 else { return rect }
        let scale = max(rect.width / image.width, rect.height / image.height)
        let w = image.width * scale
        let h = image.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }
}
