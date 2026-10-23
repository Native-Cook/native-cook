import SwiftUI
import UIKit

/*
 Particle Listening Ring — a song search that listens: a ring of coloured dots breathes with the
 voice it hears.

 Look: a warm off-white screen (#FBFAF7) with a round back button top left, the caption "Hum,
 sing, or play the tune" (17 pt, #5E6168) centred at 15.5 % of the height, and in the middle
 (centre at 46 % of the height, radius 30 % of the width) a ring of 72 dots plus 12 that fall into
 the middle when it is loud. Four colours: coral #FF6B57, violet #6C4CF5, teal #19B39A, sun #FFB020.
 At the bottom a white mic button (60 pt, soft shadow) with "Hold anywhere to hum along" under it.

 Each dot (seeded, the same numbers in every stack): an angle around the ring with a little
 jitter, a colour, a rest radius 1–2.4 pt and a loud radius 2.6–7.2 pt. With the level a:
   radius on the ring  R·(1 + 0.03j)·(1 + 0.12a) + R·a·(0.16·n(θ, t) + 0.12·j2)
                       (n: sines of 3θ and 5θ drifting 2 and 3 times a loop)
   angle               θ + 0.18·sin(2πt/6) + 0.05·sin(2πkt/6 + φ)
   size                rest·(1 − 0.35a)·twinkle + growth(colour, t)·loud·pulse
 The voice has three swells (1.1 s violet, 2.45 s teal and sun, 3.85 s coral): the leading colour
 grows to full size, the others to a quarter, and a soft wash of
 the leading colours (16 %, radius 1.6 R, from a point 0.35 R out in that colour's quarter) glows
 behind the ring. In the quiet dips half of the ring empties (dots fade
 to 10 % and shrink to 28 %), the empty half turning once a loop. The 16 inner dots fall up to 75 % of the
 way to the centre when the level passes 0.2–0.8.

 autoPlay timeline (loop = 6 s): 0–0.5 s a still, thin ring of specks; 0.5–5.4 s the three swells
 with dips between them; 5.4–6 s the thin ring again. Every motion runs a whole number of times per
 loop, so 6.0 is the frame at 0.0.

 Touch: hold anywhere and you are the voice: the level rises to 1 in 0.3 s (falls over 0.6 s after
 release), the colour of the quarter under the finger leads, and the dots near the finger's angle
 are pushed out by up to 22 % of the radius; dragging around moves the push (a light tick each time
 the leading colour changes). The script fades out in 0.25 s at the touch and comes back at the
 first loop start at least 2 s after the release (the ring is at rest there).
 Reduced motion: the ring stands still at the middle swell; touches do not move it.
 */

struct ParticleListeningRing: View {
    var autoPlay = false

    static let loop = ParticleListeningRingMotion.loop
    typealias M = ParticleListeningRingMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appear = Date.now
    /// The script plays from this moment on; before it, it is faded out since `scriptCut`.
    @State private var scriptFrom = Date.distantPast
    @State private var scriptCut = Date.distantPast
    @State private var press: (point: CGPoint, at: Date)? = nil
    @State private var released: (angle: Double, at: Date, amount: Double)? = nil
    @State private var lastQuarter = -1
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @State private var tick = UISelectionFeedbackGenerator()

    static let background = Color(red: 0.984, green: 0.980, blue: 0.969)
    static let ink = Color(red: 0.165, green: 0.169, blue: 0.188)
    static let caption = Color(red: 0.369, green: 0.380, blue: 0.408)

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var center: CGPoint
        var radius: Double
        var micY: Double

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            center = CGPoint(x: size.width / 2, y: size.height * 0.46)
            radius = size.width * 0.32
            micY = size.height - max(insets.bottom, 16) - 78
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                let drive = drive(at: timeline.date, layout: layout)
                ZStack(alignment: .topLeading) {
                    ring(drive, layout: layout)
                    chrome(drive, layout: layout)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(touch(layout: layout))
            }
            .ignoresSafeArea()
        }
        .background(Self.background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            appear = .now
            haptic.prepare()
            tick.prepare()
            if !autoPlay {
                scriptCut = .distantPast
                scriptFrom = .distantFuture
            }
        }
    }

    // MARK: Time

    private func drive(at now: Date, layout: Layout) -> M.Drive {
        if reduceMotion { return M.Drive(t: 2.45, gain: 1, touch: 0, touchAngle: 0) }
        let e = now.timeIntervalSince(appear)
        let t = e - floor(e / M.loop) * M.loop
        let gain = now >= scriptFrom ? 1 : 1 - M.easeOut(now.timeIntervalSince(scriptCut) / 0.25)
        var touch = 0.0, angle = 0.0
        if let press {
            touch = M.easeOut(now.timeIntervalSince(press.at) / 0.3)
            angle = atan2(press.point.y - layout.center.y, press.point.x - layout.center.x)
        } else if let released {
            touch = released.amount * (1 - M.easeOut(now.timeIntervalSince(released.at) / 0.6))
            angle = released.angle
        }
        // A finger's voice flutters too.
        touch *= 1 + 0.15 * sin(2 * .pi * 3.1 * e) + 0.08 * sin(2 * .pi * 5.3 * e + 1)
        return M.Drive(t: t, gain: gain, touch: touch, touchAngle: angle)
    }

    // MARK: Touch

    private func touch(layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                let now = Date.now
                if press == nil {
                    // The script fades out from what it shows now.
                    if now >= scriptFrom { scriptCut = now }
                    scriptFrom = .distantFuture
                    press = (value.location, now)
                    released = nil
                    lastQuarter = quarter(value.location, layout: layout)
                    haptic.impactOccurred(intensity: 0.6)
                } else {
                    press?.point = value.location
                    let q = quarter(value.location, layout: layout)
                    if q != lastQuarter {
                        lastQuarter = q
                        tick.selectionChanged()
                    }
                }
            }
            .onEnded { value in
                guard !reduceMotion, let press else { return }
                let now = Date.now
                let amount = M.easeOut(now.timeIntervalSince(press.at) / 0.3)
                let angle = atan2(value.location.y - layout.center.y, value.location.x - layout.center.x)
                released = (angle, now, amount)
                self.press = nil
                guard autoPlay else { return }
                // The script comes back at the first loop start at least 2 s from now.
                let e = now.timeIntervalSince(appear) + 2
                scriptFrom = appear.addingTimeInterval(ceil(e / M.loop) * M.loop)
            }
    }

    private func quarter(_ p: CGPoint, layout: Layout) -> Int {
        let a = atan2(p.y - layout.center.y, p.x - layout.center.x)
        return Int(((a / (2 * .pi) + 1).truncatingRemainder(dividingBy: 1)) * 4) % 4
    }

    // MARK: Drawing

    private func ring(_ drive: M.Drive, layout: Layout) -> some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.background))
            let colors = M.colors.map { Color(red: $0.0, green: $0.1, blue: $0.2) }
            // A soft wash of the leading colours behind the ring, as strong as the voice.
            for c in 0..<4 {
                let w = min(1, M.lead(drive, c))
                if w < 0.01 { continue }
                let a = Double(c) * .pi / 2 + .pi / 4
                let p = CGPoint(x: layout.center.x + 0.35 * layout.radius * cos(a), y: layout.center.y + 0.35 * layout.radius * sin(a))
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                    Gradient(colors: [colors[c].opacity(0.16 * w), colors[c].opacity(0)]),
                    center: p, startRadius: 0, endRadius: layout.radius * 1.6))
            }
            for i in 0..<M.count {
                let d = M.dot(i, drive: drive, center: (layout.center.x, layout.center.y), R: layout.radius)
                let r = d.radius
                if r < 0.15 || d.alpha < 0.02 { continue }
                ctx.fill(Path(ellipseIn: CGRect(x: d.x - r, y: d.y - r, width: 2 * r, height: 2 * r)),
                         with: .color(colors[d.color].opacity(d.alpha)))
            }
        }
        .frame(width: layout.size.width, height: layout.size.height)
        .accessibilityElement()
        .accessibilityLabel("Listening")
    }

    private func chrome(_ drive: M.Drive, layout: Layout) -> some View {
        let level = min(1, drive.gain * M.level(drive.t) + drive.touch)
        return ZStack(alignment: .topLeading) {
            // Back button.
            ZStack {
                Circle().fill(Color(red: 0.937, green: 0.929, blue: 0.918))
                Self.chevron.stroke(Self.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .frame(width: 8, height: 14)
            }
            .frame(width: 36, height: 36)
            .offset(x: 20, y: layout.insets.top + 8)
            .accessibilityLabel("Back")

            Text("Hum, sing, or play the tune")
                .font(.system(size: 17))
                .foregroundStyle(Self.caption)
                .lineLimit(1)
                .frame(width: layout.size.width)
                .offset(y: layout.size.height * 0.155 - 11)

            // The mic button, with a soft ring that swells with the level.
            ZStack {
                Circle().stroke(Color(red: 1.0, green: 0.420, blue: 0.341).opacity(0.28 * level), lineWidth: 2)
                    .frame(width: 60 + 28 * level, height: 60 + 28 * level)
                Circle().fill(.white)
                    .frame(width: 60, height: 60)
                    .shadow(color: .black.opacity(0.08), radius: 12, y: 4)
                Self.mic.stroke(Self.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .frame(width: 18, height: 26)
            }
            .frame(width: 100, height: 100)
            .offset(x: layout.size.width / 2 - 50, y: layout.micY - 50)

            Text("Hold anywhere to hum along")
                .font(.system(size: 13))
                .foregroundStyle(Self.caption.opacity(0.8))
                .lineLimit(1)
                .frame(width: layout.size.width)
                .offset(y: layout.micY + 44)
        }
        .allowsHitTesting(false)
    }

    static let chevron = Path { p in
        p.move(to: CGPoint(x: 7, y: 1))
        p.addLine(to: CGPoint(x: 1, y: 7))
        p.addLine(to: CGPoint(x: 7, y: 13))
    }

    /// A microphone in an 18 × 26 box: capsule, cradle and stand.
    static let mic = Path { p in
        p.addRoundedRect(in: CGRect(x: 5, y: 0, width: 8, height: 15), cornerSize: CGSize(width: 4, height: 4))
        p.move(to: CGPoint(x: 1, y: 11))
        p.addCurve(to: CGPoint(x: 17, y: 11), control1: CGPoint(x: 1, y: 22), control2: CGPoint(x: 17, y: 22))
        p.move(to: CGPoint(x: 9, y: 19.5))
        p.addLine(to: CGPoint(x: 9, y: 25))
    }
}
