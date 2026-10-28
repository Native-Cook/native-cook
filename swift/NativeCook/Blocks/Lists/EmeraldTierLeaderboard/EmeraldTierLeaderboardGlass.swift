import SwiftUI
import UIKit

// Port of EmeraldGlass.tsx, EmeraldLightSurface.tsx and EmeraldMaterials.tsx.

enum EmeraldTierPalette {
    /// `#RRGGBB` or React Native's `#RRGGBBAA`.
    static func color(_ hex: String) -> Color {
        var s = hex
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        let v = UInt64(s, radix: 16) ?? 0
        if s.count == 8 {
            return Color(.sRGB, red: Double((v >> 24) & 255) / 255, green: Double((v >> 16) & 255) / 255,
                         blue: Double((v >> 8) & 255) / 255, opacity: Double(v & 255) / 255)
        }
        return Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255,
                     blue: Double(v & 255) / 255, opacity: 1)
    }
}

enum EmeraldTierImages {
    private static func load(_ name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Emerald Tier Leaderboard image: \(name)")
            return UIImage()
        }
        return image
    }
    static let atlas = load("EmeraldTierLeaderboard-leaderboard-atlas.png")
    static let theoPortrait = load("EmeraldTierLeaderboard-theo-portrait.png")
    static let crest = load("EmeraldTierLeaderboard-emerald-crest.png")
    static let crown = load("EmeraldTierLeaderboard-gold-crown.png")
}

enum EmeraldTierGlassKind {
    case green, smoke, selected, row

    /// MATERIAL table: solid colour and light amount.
    var solidHex: String {
        switch self {
        case .green: return "#0A1B18"
        case .smoke: return "#081513"
        case .selected: return "#153B2D"
        case .row: return "#0A1715"
        }
    }
    var light: Double {
        switch self {
        case .green: return 0.32
        case .smoke: return 0.28
        case .selected: return 0
        case .row: return 0.13
        }
    }
}

/// Inky upper body, a contained lower light reservoir, and a thin refractive edge.
struct EmeraldTierLightSurface: View {
    let width: Double
    let height: Double
    let radius: Double
    let light: Double
    var response = 0.0
    var phase = 0.0

    var body: some View {
        Rectangle()
            .fill(Color.black)
            .colorEffect(ShaderLibrary.emeraldLightGlass(.float2(Float(width), Float(height)), .float(Float(radius)), .float(Float(light)),
                                                         .float(Float(response)), .float(Float(phase))))
            .frame(width: width, height: height)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Layout is measured once; the bounded surface never draws or filters its labels.
struct EmeraldTierGlass: View {
    let kind: EmeraldTierGlassKind
    let radius: Double
    var solid = false

    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let hairline = 1 / max(1, displayScale)
        let shape = RoundedRectangle(cornerRadius: radius)
        // A selected control is an inset on one glass plane, not another luminous glass layer.
        if kind == .selected {
            let border = EmeraldTierPalette.color(solid ? "#D6FFEA80" : "#BAF7DB3D")
            let topBorder = EmeraldTierPalette.color("#D6FFEA66")
            GeometryReader { proxy in
                let fade = min(1, radius / max(1, proxy.size.height))
                ZStack {
                    shape.fill(solid ? EmeraldTierPalette.color(kind.solidHex) : EmeraldTierPalette.color("#67DFA51A"))
                    if !solid {
                        LinearGradient(colors: [EmeraldTierPalette.color("#D9FFEF0A"), EmeraldTierPalette.color("#51D99F0D"),
                                                EmeraldTierPalette.color("#073C2C26")], startPoint: .top, endPoint: .bottom)
                            .clipShape(shape)
                    }
                    // Border with its own, brighter top edge (RN: borderTopColor).
                    shape.strokeBorder(LinearGradient(stops: [.init(color: topBorder, location: 0), .init(color: border, location: fade)],
                                                      startPoint: .top, endPoint: .bottom), lineWidth: hairline)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        } else {
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    shape.fill(EmeraldTierPalette.color(kind.solidHex))
                    if !solid && proxy.size.width > 0 && proxy.size.height > 0 {
                        EmeraldTierLightSurface(width: Double(proxy.size.width), height: Double(proxy.size.height),
                                                radius: radius, light: kind.light)
                            .clipShape(shape)
                    }
                    if solid { shape.strokeBorder(EmeraldTierPalette.color("#D6FFEA70"), lineWidth: hairline) }
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}

/// The artwork, with one gold glint that crosses it when the rank lands (`reactToRank`).
/// Without `reactToRank` the shader's `power` is 0 and its output equals its input, so the
/// plain image is drawn instead.
struct EmeraldTierArtMaterial: View {
    let image: UIImage
    let width: Double
    let height: Double
    let responsePhase: Double
    let response: Double
    var reactToRank = false

    var body: some View {
        let base = Image(uiImage: image).resizable().scaledToFit().frame(width: width, height: height)
        Group {
            if reactToRank {
                base.layerEffect(ShaderLibrary.emeraldArt(.float2(Float(width), Float(height)), .float(Float(responsePhase)),
                                                          .float(Float(response * 0.50))), maxSampleOffset: .zero)
            } else {
                base
            }
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
