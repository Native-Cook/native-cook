import SwiftUI
import UIKit

/*
 Aquarium Feed List — "Reef Log · Tank 2", a reef keeper's log whose entries float inside the
 tank: glass cards behind which six fish cruise, tails waving. Scrolling stirs the water: the fish
 scatter away from the scroll and bubbles stream up; caustic light plays over everything; the list
 fades into water at both ends. Tap an entry and the fish come to it.

 autoPlay (loop = 9 s): rest 0–1.4 (fish cruising) · scripted scroll down 1.4–4.4 (scatter,
 bubbles) · settle 4.4–5.6 (fish regroup) · tap the fifth card 5.8 (fish gather round it) ·
 scroll back 6.6–8.2 · rest. A touch (drag or tap) takes over at once; 2 s after the last one the
 list scrolls home and the fish swim back to their places (0.9 s), and the loop restarts.
 Reduced motion: no loop, fish hold still, no bubbles; the list scrolls as usual.

 Drawing: the water is a gradient; on it a Canvas with the fish (paths rebuilt each frame) and the
 bubbles; over that a UIScrollView of frosted glass cards, so the fish pass behind the glass and
 never over the text; on top the light (one Metal colorEffect whose alpha is its brightness) and
 the edge fades.
*/
struct AquariumFeedList: View {
    /// Scrolls the log, stirs the fish, taps an entry so they gather, scrolls back.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = AquariumFeedListEngine()
    @State private var awake = false

    private typealias S = AquariumFeedListScene
    static let water: [UInt32] = [0x2C97A8, 0x177587, 0x0D5367, 0x083A4B]

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(AquariumFeedListGeometry(W: size.width, H: size.height, top: insets.top, bottom: insets.bottom))
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(S.color(Self.water[2]).ignoresSafeArea())
    }

    private func stage(_ g: AquariumFeedListGeometry) -> some View {
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: S.color(Self.water[0]), location: 0), .init(color: S.color(Self.water[1]), location: 0.35),
                    .init(color: S.color(Self.water[2]), location: 0.7), .init(color: S.color(Self.water[3]), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )

            TimelineView(.animation(paused: !(auto || awake))) { timeline in
                let f = engine.step(to: timeline.date, auto: auto, geo: g)
                ZStack(alignment: .topLeading) {
                    // Behind the cards' glass: the fish and the bubbles.
                    Canvas { ctx, _ in drawTank(ctx, f, g) }
                        .allowsHitTesting(false)

                    AquariumFeedListScroll(
                        engine: engine, width: g.W,
                        top: g.listTop + 16, bottom: g.bottom + 48,
                        focus: f.focus
                    )

                    // Over everything: the light (transparent where none falls) and the fades into water.
                    Rectangle()
                        .colorEffect(ShaderLibrary.AquariumFeedListLight(
                            .float2(g.W, g.H),
                            .float(f.ph),
                            .float(f.stir),
                            .float(f.scroll),
                            .float(g.listTop),
                            .float(S.row),
                            .float(S.cardH),
                            .float(Double(S.entries.count))
                        ))
                        .allowsHitTesting(false)
                    fades(g)
                        .allowsHitTesting(false)
                }
                .frame(width: g.W, height: g.H, alignment: .topLeading)
            }

            header(g)
                .allowsHitTesting(false)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .clipped()
        .onAppear {
            engine.reduced = reduceMotion
            engine.onTouch = { if !awake { awake = true } }
        }
        .onChange(of: reduceMotion) { _, r in engine.reduced = r }
    }

    // MARK: - Drawing

    private static let shadows: [Image] = S.fish.map { f in
        let pad = 18.0
        let w = f.len * 0.9 + pad * 2
        let h = f.dep * 0.5 + pad * 2
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = false
        let img = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { ctx in
            let c = ctx.cgContext
            // Blurred by its shadow: the oval itself is drawn far off to the left.
            let far = 20000.0
            c.setShadow(offset: CGSize(width: far * 2, height: 0), blur: 12 * 2, color: CGColor(srgbRed: 2 / 255, green: 18 / 255, blue: 26 / 255, alpha: 0.28))
            c.setFillColor(CGColor(gray: 0, alpha: 1))
            c.fillEllipse(in: CGRect(x: pad - far, y: pad, width: f.len * 0.9, height: f.dep * 0.5))
        }
        return Image(uiImage: img)
    }

    private func drawTank(_ ctx: GraphicsContext, _ f: AquariumFeedListFrame, _ g: AquariumFeedListGeometry) {
        for i in S.order {
            let spec = S.fish[i]
            let p = f.fish[i]
            var c = ctx
            c.opacity = 0.6 + 0.4 * spec.near
            c.drawLayer { l in
                l.translateBy(x: p.x, y: p.y)
                l.rotate(by: .radians(p.rot))
                l.scaleBy(x: p.sx * spec.near, y: spec.near)
                l.draw(Self.shadows[i], in: CGRect(x: -spec.len * 0.42 - 18, y: spec.dep * 0.9 - 18, width: spec.len * 0.9 + 36, height: spec.dep * 0.5 + 36))
                l.fill(
                    S.body(spec, p.phase, p.amp),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: S.color(spec.colors[0]), location: 0), .init(color: S.color(spec.colors[1]), location: 0.5),
                            .init(color: S.color(spec.colors[2]), location: 1),
                        ]),
                        startPoint: CGPoint(x: 0, y: -spec.dep / 2), endPoint: CGPoint(x: 0, y: spec.dep / 2)
                    )
                )
                if !spec.bands.isEmpty {
                    l.fill(S.bands(spec, p.phase, p.amp), with: .color(Color(.sRGB, red: 1, green: 250 / 255, blue: 240 / 255, opacity: 0.92)))
                }
                let ex = spec.len * 0.31
                let ey = -spec.dep * 0.06
                let er = spec.dep * 0.11
                l.fill(Path(ellipseIn: CGRect(x: ex - er, y: ey - er, width: er * 2, height: er * 2)), with: .color(S.color(0xF4F8F2)))
                let px = spec.len * 0.32
                let pr = spec.dep * 0.07
                l.fill(Path(ellipseIn: CGRect(x: px - pr, y: ey - pr, width: pr * 2, height: pr * 2)), with: .color(S.color(0x0B1A20)))
            }
        }
        let bub = S.bubbles(f.ft, reduceMotion ? 0 : f.stir, g.W, g.listTop, g.bottomY - 10)
        ctx.stroke(bub.rings, with: .color(Color(.sRGB, red: 214 / 255, green: 246 / 255, blue: 1, opacity: 0.6)), lineWidth: 1)
        ctx.fill(bub.rings, with: .color(Color(.sRGB, red: 190 / 255, green: 236 / 255, blue: 250 / 255, opacity: 0.1)))
        ctx.fill(bub.glints, with: .color(.white.opacity(0.75)))
    }

    private func fades(_ g: AquariumFeedListGeometry) -> some View {
        let teal = Color(.sRGB, red: 30 / 255, green: 130 / 255, blue: 149 / 255, opacity: 1)
        let deep = Color(.sRGB, red: 8 / 255, green: 58 / 255, blue: 75 / 255, opacity: 1)
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: [teal, teal.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: g.W, height: 70)
                .offset(y: g.listTop - 30)
            LinearGradient(
                stops: [.init(color: deep.opacity(0), location: 0), .init(color: deep.opacity(0.9), location: 0.7), .init(color: deep, location: 1)],
                startPoint: .top, endPoint: .bottom
            )
            .frame(width: g.W, height: g.H - g.bottomY + 90)
            .offset(y: g.bottomY - 90)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
    }

    private func header(_ g: AquariumFeedListGeometry) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("REEF LOG")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2)
                .foregroundStyle(S.color(0x8FE3F0))
            Text("Tank 2")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.color(0xEAF9FB))
            Text("220 L mixed reef · 6 fish · 14 corals")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color(.sRGB, red: 222 / 255, green: 246 / 255, blue: 250 / 255, opacity: 0.66))
            HStack(spacing: 8) {
                ForEach(["25.6 °C", "pH 8.2", "SG 1.025", "NO₃ 5 ppm"], id: \.self) { s in
                    Text(s)
                        .font(.system(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(S.color(0xDDF6FA))
                        .lineLimit(1)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.sRGB, red: 190 / 255, green: 240 / 255, blue: 250 / 255, opacity: 0.12)))
                }
            }
            .padding(.top, 10)
        }
        .padding(.horizontal, 22)
        .padding(.top, g.top + 14)
        .frame(width: g.W, height: g.listTop - 18, alignment: .topLeading)
        .background(
            LinearGradient(
                stops: [
                    .init(color: S.color(0x2C97A8), location: 0), .init(color: S.color(0x1F8497), location: 0.75),
                    .init(color: Color(.sRGB, red: 30 / 255, green: 130 / 255, blue: 149 / 255, opacity: 0), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
        )
    }
}
