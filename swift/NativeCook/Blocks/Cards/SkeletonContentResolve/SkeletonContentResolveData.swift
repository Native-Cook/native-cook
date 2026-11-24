import SwiftUI

/// The two feeds of Hearth, an invented podcast app, and its drawn covers.
enum SkeletonContentResolveData {
    enum Motif { case sun, rings, bars, stripes, dots, arc, steps }

    struct Cover: Equatable {
        let motif: Motif
        let ground: [UInt32]
        let ink: UInt32
        let accent: UInt32
    }

    struct Episode: Equatable {
        let title: String
        let show: String
        let minutes: Int
        let day: String
        let cover: Cover
    }

    struct Feed: Equatable {
        let eyebrow: String
        let title: [String]
        let show: String
        let minutes: Int
        let cover: Cover
        let episodes: [Episode]
    }

    static let feeds: [Feed] = [
        Feed(eyebrow: "New this week", title: ["The Long Way", "Round the Coast"], show: "Salt & Signal", minutes: 42,
             cover: Cover(motif: .sun, ground: [0xf2b36b, 0xe9824a, 0xb4472f], ink: 0x2c2233, accent: 0xfbe3b0),
             episodes: [
                Episode(title: "How Bread Learned to Rise", show: "Crumb Theory", minutes: 31, day: "Today",
                        cover: Cover(motif: .bars, ground: [0xe8dcc4, 0xd9c7a3, 0xc6ae84], ink: 0x5a3d24, accent: 0xb8552f)),
                Episode(title: "Field Guide to Night Trains", show: "Slow Departures", minutes: 54, day: "Mon",
                        cover: Cover(motif: .rings, ground: [0x274046, 0x1e3138, 0x15232a], ink: 0x7fb6a8, accent: 0xf0c987)),
                Episode(title: "The Map That Was Wrong", show: "Paper Coasts", minutes: 27, day: "Sun",
                        cover: Cover(motif: .stripes, ground: [0xd7e0d2, 0xb9cdb8, 0x93b19b], ink: 0x2f4a43, accent: 0xf3ead7)),
                Episode(title: "Notes From a Quiet Kitchen", show: "Hearthside", minutes: 38, day: "Fri",
                        cover: Cover(motif: .arc, ground: [0x3a2430, 0x532b33, 0x7a3a32], ink: 0xf08a4b, accent: 0xf6d7a6)),
             ]),
        Feed(eyebrow: "Fresh for you", title: ["Small Hours,", "Big Weather"], show: "Overcast Club", minutes: 36,
             cover: Cover(motif: .rings, ground: [0x31405f, 0x27324d, 0x1a2136], ink: 0x9fb4e8, accent: 0xf2c36b),
             episodes: [
                Episode(title: "Why Rivers Bend", show: "Loose Gravel", minutes: 44, day: "Today",
                        cover: Cover(motif: .steps, ground: [0xcfe0dc, 0xa9c9c4, 0x7fada9], ink: 0x1f4a4c, accent: 0xf5efe0)),
                Episode(title: "Last Orchard on the Line", show: "Branch Lines", minutes: 58, day: "Today",
                        cover: Cover(motif: .dots, ground: [0xf0d9a8, 0xe6bf78, 0xd99f52], ink: 0x6b3a1d, accent: 0xfff3d6)),
                Episode(title: "Letters to a Lost Umbrella", show: "Odd Post", minutes: 22, day: "Tue",
                        cover: Cover(motif: .sun, ground: [0xe6c9d0, 0xcf9fb0, 0xa86d8a], ink: 0x3c2340, accent: 0xfbe9d2)),
                Episode(title: "Tuning a House by Ear", show: "Room Tone", minutes: 49, day: "Sat",
                        cover: Cover(motif: .bars, ground: [0x2b2a3f, 0x39355a, 0x4d4378], ink: 0xb9a8f0, accent: 0xf2c9a0)),
             ]),
    ]

    static let nowPlaying = Cover(motif: .dots, ground: [0x20424a, 0x17323a, 0x10242b], ink: 0x8fd0c4, accent: 0xf0c987)

    // Bar widths while loading: fixed per slot, so the skeleton never shows what is coming.
    static let eyebrowBar: CGFloat = 88
    static let titleABar: CGFloat = 132
    static let titleBBar: CGFloat = 164
    static let metaBar: CGFloat = 124
    static let playBar: CGFloat = 86
    static let rowTitleBars: [CGFloat] = [172, 204, 150, 186]
    static let rowMetaBars: [CGFloat] = [118, 96, 132, 104]
    static let dayBar: CGFloat = 34

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xff) / 255,
              green: Double((hex >> 8) & 0xff) / 255,
              blue: Double(hex & 0xff) / 255,
              opacity: alpha)
    }
}

/// One cover in a size × size box: a three-stop ground, a motif, a soft light from the top left.
struct SkeletonContentResolveCoverArt: View, Equatable {
    let spec: SkeletonContentResolveData.Cover
    let size: CGFloat

    var body: some View {
        Canvas { context, _ in
            Self.draw(spec, size, in: &context)
        }
        .frame(width: size, height: size)
    }

    private static func hash(_ i: Int) -> Double {
        let x = sin(Double(i) * 12.9898 + 4.1414) * 43758.5453
        return x - floor(x)
    }

    private static func circle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }

    private static func draw(_ spec: SkeletonContentResolveData.Cover, _ s: CGFloat, in context: inout GraphicsContext) {
        let c = SkeletonContentResolveData.color
        let box = CGRect(x: 0, y: 0, width: s, height: s)
        let ground = Gradient(stops: [
            .init(color: c(spec.ground[0], 1), location: 0),
            .init(color: c(spec.ground[1], 1), location: 0.55),
            .init(color: c(spec.ground[2], 1), location: 1),
        ])
        context.fill(Path(box), with: .linearGradient(ground, startPoint: CGPoint(x: s * 0.1, y: 0), endPoint: CGPoint(x: s * 0.25, y: s)))

        let ink = c(spec.ink, 1)
        let accent = c(spec.accent, 1)
        switch spec.motif {
        case .sun:
            context.fill(circle(s * 0.6, s * 0.44, s * 0.21), with: .color(accent))
            context.fill(Path(CGRect(x: 0, y: s * 0.62, width: s, height: s * 0.4)), with: .color(ink))
            context.fill(Path(CGRect(x: s * 0.35, y: s * 0.7, width: s * 0.5, height: s * 0.02)), with: .color(c(spec.accent, 0.5)))
            context.fill(Path(CGRect(x: s * 0.43, y: s * 0.78, width: s * 0.34, height: s * 0.02)), with: .color(c(spec.accent, 0.32)))
            context.fill(Path(CGRect(x: s * 0.5, y: s * 0.86, width: s * 0.2, height: s * 0.02)), with: .color(c(spec.accent, 0.18)))
        case .rings:
            for (i, r) in [0.2, 0.36, 0.52, 0.68, 0.84].enumerated() {
                context.stroke(circle(s * 0.3, s * 0.74, s * r), with: .color(c(spec.ink, 0.95 - Double(i) * 0.16)), lineWidth: s * 0.028)
            }
            context.fill(circle(s * 0.3, s * 0.74, s * 0.085), with: .color(accent))
        case .bars:
            for i in 0..<6 {
                let h = s * (0.22 + 0.5 * hash(i + 3))
                let bw = s * 0.085
                let rect = CGRect(x: s * 0.16 + CGFloat(i) * s * 0.122, y: s * 0.5 - h / 2, width: bw, height: h)
                context.fill(Path(roundedRect: rect, cornerRadius: bw / 2), with: .color(i == 2 ? accent : ink))
            }
        case .stripes:
            var turned = context
            turned.translateBy(x: s / 2, y: s / 2)
            turned.rotate(by: .radians(-0.6))
            turned.translateBy(x: -s / 2, y: -s / 2)
            for i in 0..<7 {
                let alpha = i == 3 ? 1 : 0.85 - 0.08 * Double(abs(i - 3))
                let rect = CGRect(x: -s * 0.3, y: s * 0.08 + CGFloat(i) * s * 0.135, width: s * 1.6, height: s * 0.07)
                turned.fill(Path(rect), with: .color(c(i == 3 ? spec.accent : spec.ink, alpha)))
            }
        case .dots:
            for i in 0..<16 {
                let col = CGFloat(i % 4)
                let row = CGFloat(i / 4)
                let big = hash(i + 11)
                let alpha = i == 6 ? 1 : 0.55 + 0.4 * big
                context.fill(circle(s * (0.2 + col * 0.2), s * (0.2 + row * 0.2), s * (0.03 + 0.045 * big)),
                             with: .color(c(i == 6 ? spec.accent : spec.ink, alpha)))
            }
        case .arc:
            context.fill(circle(s * 0.08, s, s * 0.72), with: .color(ink))
            context.fill(circle(s * 0.08, s, s * 0.48), with: .color(c(spec.ground[1], 1)))
            context.fill(circle(s * 0.08, s, s * 0.26), with: .color(accent))
            context.fill(circle(s * 0.76, s * 0.24, s * 0.055), with: .color(c(spec.accent, 0.9)))
        case .steps:
            for i in 0..<5 {
                let alpha = i == 4 ? 1 : 0.5 + Double(i) * 0.12
                let rect = CGRect(x: 0, y: s * (0.24 + CGFloat(i) * 0.14), width: s * (0.3 + CGFloat(i) * 0.14), height: s * 0.11)
                context.fill(Path(rect), with: .color(c(i == 4 ? spec.accent : spec.ink, alpha)))
            }
        }

        let light = Gradient(stops: [
            .init(color: .white.opacity(0.2), location: 0),
            .init(color: .white.opacity(0), location: 0.45),
            .init(color: .black.opacity(0.16), location: 1),
        ])
        context.fill(Path(box), with: .linearGradient(light, startPoint: .zero, endPoint: CGPoint(x: s, y: s)))
    }
}
