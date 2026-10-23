import SwiftUI
import UIKit

/// A fabric banner flying from a pole over the sea at dusk, simulated as a verlet cloth with wind.
/// Folds catch the light from their normals and the weave shows in the cloth. A switch sets the
/// wind (calm, breeze, gale); dragging grabs the cloth at the nearest point and pulls it, letting go
/// lets it billow; a fast swipe past the cloth gusts the wind, and a tap stills it or brings it back.
struct ClothFlagMesh: View {
    /// Raises the wind, gusts, pulls a corner down and lets it fly, then stills, on a loop.
    var autoPlay = false
    /// The lines under the banner.
    var label = "House banner · No. 7"
    var title = "Saltmarsh Regatta"
    var subtitle = "Flown at the harbour mouth since 1874"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ClothFlagMeshEngine()
    @State private var mode: Int?

    private typealias M = ClothFlagMeshMetrics
    private typealias P = ClothFlagMeshPalette

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = ClothFlagMeshLayout(top: insets.top + M.flagTop)
        // The readout sits on the right, level with the caption's title.
        let readoutBase = size.height - insets.bottom - 140
        let auto = autoPlay && !reduceMotion
        let selected = mode ?? (auto ? 0 : 1)
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, layout: layout, autoPlay: auto, reducedMotion: reduceMotion)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        ClothFlagMeshDrawing.scenery(ctx, size: size)
                        ClothFlagMeshDrawing.pole(ctx, top: layout.top, end: size.height - insets.bottom - M.captionClear)
                        ClothFlagMeshDrawing.shadow(ctx, outline: frame.outline)
                    }
                    ClothFlagMeshMeshView(engine: engine, frame: frame.number)
                    Canvas { ctx, _ in
                        ClothFlagMeshDrawing.rings(ctx, rings: frame.rings)
                        ClothFlagMeshDrawing.readout(ctx, knots: frame.knots, calm: frame.mode == 0, right: size.width - 24, baseline: readoutBase)
                    }
                }
                .frame(width: size.width, height: size.height)
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .gesture(
                // One recogniser stands in for the race of a pan (after 4 pt of travel) and a tap (a
                // touch lifted within 300 ms that never travelled that far).
                DragGesture(minimumDistance: 0)
                    .onChanged { engine.touchChanged(location: $0.location, time: $0.time) }
                    .onEnded { engine.touchEnded(location: $0.location, velocity: $0.velocity, time: $0.time) }
            )
            .accessibilityElement()
            .accessibilityLabel("\(title) banner. Drag the cloth to pull it, swipe past it to gust the wind, tap to still or raise the wind.")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { engine.toggleNow() }

            VStack(alignment: .leading, spacing: 4) {
                Text(label.uppercased())
                    .font(.custom("AvenirNext-Bold", fixedSize: 11))
                    .kerning(2.2)
                    .foregroundStyle(Color(hex: P.muted))
                Text(title)
                    .font(.custom("Baskerville-SemiBold", fixedSize: 30))
                    .foregroundStyle(Color(hex: P.ink))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(subtitle)
                    .font(.custom("AvenirNext-Medium", fixedSize: 13))
                    .foregroundStyle(Color(hex: P.muted))
                    .lineLimit(2)
            }
            .frame(width: max(0, size.width - 24 - 130), alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .frame(width: size.width - 130, height: size.height - insets.bottom - 108, alignment: .bottomTrailing)
            .allowsHitTesting(false)

            VStack(spacing: 12) {
                Text("Drag the cloth · Swipe for a gust · Tap to still the wind")
                    .font(.custom("AvenirNext-Medium", fixedSize: 13))
                    .foregroundStyle(Color(hex: P.muted))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .allowsHitTesting(false)
                ClothFlagMeshSwitch(selected: selected, reduceMotion: reduceMotion) { m in
                    engine.pick(m)
                    mode = m
                }
            }
            .padding(.horizontal, 20)
            .frame(width: size.width, height: size.height - insets.bottom - 14, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: P.night))
        .clipped()
        .onAppear {
            engine.onMode = { m in mode = m }
        }
    }
}

/// The wind switch: Calm, Breeze, Gale, with a pill that slides to the chosen one on a spring.
private struct ClothFlagMeshSwitch: View {
    let selected: Int
    let reduceMotion: Bool
    let pick: (Int) -> Void

    var body: some View {
        GeometryReader { geo in
            let segment = (geo.size.width - 8) / Double(ClothFlagMeshMetrics.modes.count)
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.rgba(243, 236, 224, 0.94))
                    .frame(width: max(0, segment), height: 38)
                    .offset(x: 4 + segment * Double(selected))
                    .animation(reduceMotion ? nil : .spring(response: 0.38, dampingFraction: 0.82), value: selected)
                HStack(spacing: 0) {
                    ForEach(ClothFlagMeshMetrics.modes.indices, id: \.self) { i in
                        let isOn = i == selected
                        Button { pick(i) } label: {
                            Text(ClothFlagMeshMetrics.modes[i])
                                .font(.custom("AvenirNext-DemiBold", fixedSize: 15))
                                .foregroundStyle(isOn ? Color(hex: 0x1A2030) : Color(hex: ClothFlagMeshPalette.ink))
                                .frame(width: max(0, segment), height: 38)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(ClothFlagMeshSegmentStyle(selected: isOn))
                        .accessibilityLabel("\(ClothFlagMeshMetrics.modes[i]) wind")
                        .accessibilityAddTraits(isOn ? .isSelected : [])
                    }
                }
                .padding(.horizontal, 4)
            }
            .frame(width: geo.size.width, height: 46)
            .background(Capsule().fill(Color.white.opacity(0.08)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.16), lineWidth: 1 / 3))
        }
        .frame(height: 46)
    }
}

/// A segment dims while pressed, unless it is the chosen one.
private struct ClothFlagMeshSegmentStyle: ButtonStyle {
    let selected: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed && !selected ? 0.7 : 1)
    }
}

enum ClothFlagMeshPalette {
    static let night: UInt32 = 0x0f1626
    static let ink: UInt32 = 0xf3ece0
    static let muted: UInt32 = 0xB5A79A
}

/// Everything around the cloth, as plain functions on a graphics context.
private enum ClothFlagMeshDrawing {
    typealias M = ClothFlagMeshMetrics

    /// The sky at dusk over a still sea, with a low headland on the horizon.
    static func scenery(_ ctx: GraphicsContext, size: CGSize) {
        let w = size.width
        let horizon = size.height * M.horizon
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: horizon)), with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x0f1626), location: 0),
                .init(color: Color(hex: 0x1d2a46), location: 0.45),
                .init(color: Color(hex: 0x4a4a64), location: 0.8),
                .init(color: Color(hex: 0x9a7062), location: 1),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: 0, y: horizon)
        ))
        // The headland: a long low hill on the right, with a lower spit before it.
        var land = Path()
        land.move(to: CGPoint(x: w * 0.42, y: horizon))
        land.addCurve(to: CGPoint(x: w * 0.74, y: horizon - 17), control1: CGPoint(x: w * 0.55, y: horizon - 4), control2: CGPoint(x: w * 0.6, y: horizon - 15))
        land.addCurve(to: CGPoint(x: w + 2, y: horizon - 12), control1: CGPoint(x: w * 0.86, y: horizon - 19), control2: CGPoint(x: w * 0.93, y: horizon - 11))
        land.addLine(to: CGPoint(x: w + 2, y: horizon))
        land.closeSubpath()
        ctx.fill(land, with: .color(Color(hex: 0x2f2c3c)))
        ctx.fill(Path(CGRect(x: 0, y: horizon, width: w, height: size.height - horizon)), with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x5d5262), location: 0),
                .init(color: Color(hex: 0x262c40), location: 0.22),
                .init(color: Color(hex: 0x0c101b), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: horizon),
            endPoint: CGPoint(x: 0, y: size.height)
        ))
        // A few long, faint glints of the last light on the water.
        let lines: [(Double, Double, Double, Double)] = [(0.18, 0.012, 0.3, 0.22), (0.52, 0.02, 0.22, 0.16), (0.08, 0.04, 0.2, 0.1), (0.66, 0.055, 0.18, 0.08)]
        for (x, y, len, a) in lines {
            var glint = Path()
            let yy = horizon + size.height * y
            glint.move(to: CGPoint(x: w * x, y: yy))
            glint.addLine(to: CGPoint(x: w * (x + len), y: yy))
            ctx.stroke(glint, with: .color(.rgba(236, 196, 170, a)), lineWidth: 1)
        }
    }

    /// The pole: a brushed metal shaft with a gold ball on top and a halyard down its side. Its foot
    /// fades into the dark water above the caption, so the shaft never runs through the text.
    static func pole(_ ctx: GraphicsContext, top: Double, end: Double) {
        ctx.drawLayer { layer in poleBody(layer, top: top, end: end) }
    }

    private static func poleBody(_ ctx: GraphicsContext, top: Double, end: Double) {
        let fadeFrom = max(top + 40, end - M.poleFade)
        let shaft = GraphicsContext.Shading.linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x22262e), location: 0),
                .init(color: Color(hex: 0xdfe3ea), location: 0.32),
                .init(color: Color(hex: 0x7d8490), location: 0.6),
                .init(color: Color(hex: 0x1c2027), location: 1),
            ]),
            startPoint: CGPoint(x: M.poleX - M.poleW / 2, y: 0),
            endPoint: CGPoint(x: M.poleX + M.poleW / 2, y: 0)
        )
        ctx.fill(Path(CGRect(x: M.poleX - M.poleW / 2, y: top - 14, width: M.poleW, height: end - top + 14)), with: shaft)
        // The truck under the ball.
        ctx.fill(Path(roundedRect: CGRect(x: M.poleX - 6, y: top - 17, width: 12, height: 5), cornerRadius: 2), with: shaft)
        ctx.fill(
            Path(ellipseIn: CGRect(x: M.poleX - 7.5, y: top - 25 - 7.5, width: 15, height: 15)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xfff1c4), location: 0),
                    .init(color: Color(hex: 0xd7a64c), location: 0.45),
                    .init(color: Color(hex: 0x6b4815), location: 1),
                ]),
                center: CGPoint(x: M.poleX - 2.6, y: top - 28),
                startRadius: 0,
                endRadius: 10
            )
        )
        var halyard = Path()
        halyard.move(to: CGPoint(x: M.poleX + M.poleW / 2 + 1.5, y: top - 17))
        halyard.addLine(to: CGPoint(x: M.poleX + M.poleW / 2 + 1.5, y: end))
        ctx.stroke(halyard, with: .color(Color(hex: 0xe8e0d4, opacity: 0.35)), lineWidth: 0.8)
        var fade = ctx
        fade.blendMode = .destinationIn
        fade.fill(
            Path(CGRect(x: M.poleX - 12, y: fadeFrom, width: 24, height: end - fadeFrom + 2)),
            with: .linearGradient(
                Gradient(colors: [.black, .black.opacity(0)]),
                startPoint: CGPoint(x: 0, y: fadeFrom),
                endPoint: CGPoint(x: 0, y: end)
            )
        )
    }

    /// The soft shade the banner casts on the sky behind it, so it sits off the sky.
    static func shadow(_ ctx: GraphicsContext, outline: [CGPoint]) {
        guard outline.count > 2 else { return }
        var path = Path()
        path.addLines(outline)
        path.closeSubpath()
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: 12))
            layer.translateBy(x: 10, y: 18)
            layer.fill(path, with: .color(Color.rgba(5, 7, 12, 0.3)))
        }
    }

    /// Rings holding the hoist to the pole, over the cloth.
    static func rings(_ ctx: GraphicsContext, rings: [Double]) {
        for y in rings {
            ctx.stroke(
                Path(ellipseIn: CGRect(x: M.poleX - 6, y: y - 2.5, width: 12, height: 5)),
                with: .color(Color(hex: 0xd9b36a)),
                lineWidth: 1.6
            )
        }
    }

    private static let knotsFont = UIFont(name: "AvenirNext-DemiBold", size: 40) ?? .systemFont(ofSize: 40, weight: .semibold)
    private static let unitFont = UIFont(name: "AvenirNext-DemiBold", size: 15) ?? .systemFont(ofSize: 15, weight: .semibold)
    private static let smallFont = UIFont(name: "AvenirNext-Bold", size: 11) ?? .systemFont(ofSize: 11, weight: .bold)

    /// Draws `text` with its baseline on `y` and its right end on `right`.
    private static func text(_ ctx: GraphicsContext, _ string: String, _ font: UIFont, _ color: UInt32, right: Double, y: Double) {
        let resolved = ctx.resolve(Text(string).font(Font(font)).foregroundStyle(Color(hex: color)))
        ctx.draw(resolved, at: CGPoint(x: right, y: y - font.descender), anchor: .bottomTrailing)
    }

    private static func width(_ string: String, _ font: UIFont) -> Double {
        (string as NSString).size(withAttributes: [.font: font]).width
    }

    /// The wind readout, on the right of the caption: "WIND", or "CALM" once a tap has stilled it.
    static func readout(_ ctx: GraphicsContext, knots: Double, calm: Bool, right: Double, baseline: Double) {
        let value = "\(Int(floor(max(0, knots) + 0.5)))"
        let unitW = width("kn", unitFont)
        text(ctx, "kn", unitFont, ClothFlagMeshPalette.ink, right: right, y: baseline)
        text(ctx, value, knotsFont, ClothFlagMeshPalette.ink, right: right - unitW - 5, y: baseline)
        text(ctx, calm ? "CALM" : "WIND", smallFont, ClothFlagMeshPalette.muted, right: right, y: baseline - 44)
    }
}
