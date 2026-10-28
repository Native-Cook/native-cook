import SwiftUI
import UIKit

/*
 Dither Asterisk Sign In — the sign-in screen of "Footnote", an invented reading app.

 Look: a black stage, a big white asterisk built from a 31 × 31 grid of square cells, the heavy
 wordmark "FOOTNOTE", "Read between the lines", three dark pill buttons (passkey, email, phone,
 with glyphs drawn in code) and "or browse without an account".

 Hero: the mark is sliced. Vertical slices of the grid slide up and down by whole cells, their
 cells thin into a glyph dither (■ 1 0 +) and leave glyph streaks where they were, row bands
 shear sideways at the peak, then every slice springs home and the asterisk is whole again.
 The mark is drawn as one path per frame (DitherAsteriskSigninMotion.markPath).

 autoPlay: loop = 7 s, see DitherAsteriskSigninMotion.swift for the script.
 Touch: drag across the mark to slice the columns under the finger (their offset and dither follow
 the finger's movement and speed); release and they spring back. Light haptics on the first slice of a drag
 and when the mark is whole again. 2 s after the release, once whole, the loop restarts from t = 0.
 Reduced motion: the whole mark, still; no loop, no touch.
*/
struct DitherAsteriskSignin: View {
    /// Plays the 7 s slice-and-reassemble loop until touched.
    var autoPlay = false

    typealias M = DitherAsteriskSigninMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = DitherAsteriskSigninSim()
    @State private var live = false

    static let bg = Color(red: 6 / 255, green: 6 / 255, blue: 6 / 255)
    static let ink = Color(red: 244 / 255, green: 244 / 255, blue: 242 / 255)
    static let tagline = Color(red: 214 / 255, green: 214 / 255, blue: 214 / 255)
    static let muted = Color(red: 124 / 255, green: 124 / 255, blue: 128 / 255)
    static let pill = Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255)

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        let g = DitherAsteriskSigninGeo(w: max(1, size.width), h: max(1, size.height), top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            RadialGradient(colors: [Color(red: 21 / 255, green: 21 / 255, blue: 21 / 255), Self.bg],
                           center: UnitPoint(x: 0.5, y: g.markCY / g.h),
                           startRadius: 0, endRadius: 300)
            TimelineView(.animation(paused: reduced || (!auto && !live))) { tl in
                let f = reduced ? M.autoFrame(0) : sim.frame(at: tl.date, auto: auto)
                Canvas { c, _ in
                    if f.glow > 0.001 {
                        c.fill(Path(ellipseIn: CGRect(x: g.w / 2 - 190, y: g.markCY - 190, width: 380, height: 380)),
                               with: .radialGradient(
                                   Gradient(colors: [Color.white.opacity(f.glow), Color.white.opacity(0)]),
                                   center: CGPoint(x: g.w / 2, y: g.markCY), startRadius: 0, endRadius: 190))
                    }
                    // Streaks never reach into the wordmark, even under a hard drag.
                    c.clip(to: Path(CGRect(x: 0, y: 0, width: g.w, height: g.wordTop + 4)))
                    c.fill(M.markPath(f, left: g.markLeft, top: g.markTop), with: .color(Self.ink))
                }
                .frame(width: g.w, height: g.h)
            }
            .allowsHitTesting(false)

            Text("FOOTNOTE")
                .font(.system(size: 70, weight: .black))
                .kerning(-1)
                .foregroundStyle(Self.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: g.w - 24, height: 78)
                .offset(x: 12, y: g.wordTop)
            Text("Read between the lines")
                .font(.system(size: 25, weight: .regular))
                .foregroundStyle(Self.tagline)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: g.w - 32, height: 30)
                .offset(x: 16, y: g.tagTop)

            button("Continue with passkey", icon: 0, y: g.buttonTop(0), g)
            button("Continue with email", icon: 1, y: g.buttonTop(1), g)
            button("Continue with phone", icon: 2, y: g.buttonTop(2), g)

            (Text("or  ").foregroundStyle(Self.muted)
                + Text("browse without an account").fontWeight(.semibold).foregroundStyle(Self.ink))
                .font(.system(size: 14))
                .lineLimit(1)
                .frame(width: g.w, height: 20)
                .offset(y: g.footerCY - 10)

            // The mark's touch area.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: g.w, height: g.touchBottom - g.touchTop)
                .offset(y: g.touchTop)
                .gesture(drag(g), including: reduced ? .none : .all)
                .accessibilityElement()
                .accessibilityLabel("Footnote logo, an asterisk made of pixels. Drag across it to slice it.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .onAppear {
            sim.onFeel = {
                DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            }
        }
    }

    private func button(_ label: String, icon: Int, y: Double, _ g: DitherAsteriskSigninGeo) -> some View {
        ZStack(alignment: .leading) {
            Capsule().fill(Self.pill)
            Capsule().strokeBorder(
                LinearGradient(colors: [Color.white.opacity(0.07), Color.white.opacity(0)],
                               startPoint: .top, endPoint: .center),
                lineWidth: 1)
            Text(label)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Self.ink)
                .lineLimit(1)
                .frame(width: g.w - 32, height: 54)
            DitherAsteriskSigninIcon(kind: icon)
                .stroke(Self.ink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 20, height: 20)
                .offset(x: 20)
        }
        .frame(width: g.w - 32, height: 54)
        .offset(x: 16, y: y)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private func drag(_ g: DitherAsteriskSigninGeo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let col = (v.location.x - g.markLeft - M.cell / 2) / M.pitch
                live = true
                sim.touch(x: v.location.x, y: v.location.y, col: col, at: Date())
            }
            .onEnded { _ in
                sim.release(at: Date())
                if !autoPlay || reduceMotion {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 6) { if !sim.live { live = false } }
                }
            }
    }
}

struct DitherAsteriskSigninGeo {
    typealias M = DitherAsteriskSigninMotion
    var w: Double
    var h: Double
    var top: Double
    var bottom: Double

    var span: Double { Double(M.n) * M.pitch - 1 }
    var markLeft: Double { ((w - span) / 2 * 3).rounded() / 3 }
    var markTop: Double { (top + 64).rounded() }
    var markCY: Double { markTop + span / 2 }
    var wordTop: Double { markTop + span + 34 }
    var tagTop: Double { wordTop + 82 }
    var footerCY: Double { h - bottom - 24 }
    func buttonTop(_ i: Int) -> Double { footerCY - 32 - 54 - Double(2 - i) * 66 }
    var touchTop: Double { max(top, markTop - 40) }
    var touchBottom: Double { markTop + span + 34 }
}

/// The sign-in glyphs in a 20 × 20 box: 0 key, 1 envelope, 2 phone.
struct DitherAsteriskSigninIcon: Shape {
    var kind: Int

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let o = rect.origin
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: o.x + x, y: o.y + y) }
        switch kind {
        case 0:
            p.addEllipse(in: CGRect(x: o.x + 2, y: o.y + 6, width: 8, height: 8))
            p.move(to: pt(10, 10)); p.addLine(to: pt(18.5, 10))
            p.move(to: pt(15, 10)); p.addLine(to: pt(15, 13))
            p.move(to: pt(18, 10)); p.addLine(to: pt(18, 13.5))
        case 1:
            p.addRoundedRect(in: CGRect(x: o.x + 1.5, y: o.y + 4, width: 17, height: 12),
                             cornerSize: CGSize(width: 2, height: 2))
            p.move(to: pt(2.5, 5.5)); p.addLine(to: pt(10, 11)); p.addLine(to: pt(17.5, 5.5))
        default:
            p.addRoundedRect(in: CGRect(x: o.x + 5, y: o.y + 1.5, width: 10, height: 17),
                             cornerSize: CGSize(width: 2.5, height: 2.5))
            p.move(to: pt(8.5, 15.2)); p.addLine(to: pt(11.5, 15.2))
        }
        return p
    }
}
