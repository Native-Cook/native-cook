import CoreText
import SwiftUI
import UIKit

/*
 Revolving Text Ring — an open-air festival poster whose name runs around a turning 3D ring.

 Two bands of capitals are wrapped around a vertical cylinder of radius R (39 % of the screen
 width, at most 170 pt), seen from 22° above with a 1000 pt focal length: a big band
 "OPEN AIR • OPEN AIR • " and, under it, a small tracked band with the dates and place. Each
 band's font size is chosen so that its text goes round exactly once. Every glyph is a flat tile
 tangent to the cylinder, drawn with the affine map of the projection at its centre, so the
 letters on the far side read mirrored through the open ring. Glyphs are drawn back to front;
 a glyph's opacity runs from 0.3 (facing away) to 1 (facing the viewer).

 autoPlay timeline (loop = 8 s):
   rotation   constant, one turn every 4 s (two per loop), the front moving left like a marquee.
   0.0 … 3.2  light: paper (#EEEDE8) with near-black ink (#0E0E10).
   3.2 … 4.0  colours invert (ease-in-out): dark paper, light ink, the whole screen together.
   4.0 … 7.2  dark.
   7.2 … 8.0  back to light; t = 8 is the same frame as t = 0 (two whole turns, light).

 Touch: dragging sideways turns the ring under the finger (1 pt = 1/R rad), with a selection
 haptic every 30°; on release it keeps the finger's speed and slows with a 0.5 s time constant.
 A tap inverts the colours (0.6 s). 2 s after the last touch the ring eases to its starting
 angle and the light colours over 1 s, and the loop starts again.
 Reduced motion: the ring stands still in the light colours; dragging still turns it (no glide),
 and a tap cross-fades the colours.
 */

struct RevolvingTextRing: View {
    var bigBand = "OPEN AIR • OPEN AIR • "
    var smallBand = "FRIDAY TO SUNDAY · RIVERSIDE PARK · GATES AT 4 PM · "
    var header = "OPEN AIR 26"
    var dates = "JUN 26–28"
    var title = "Three days by the river."
    var subtitle = "Forty artists, two stages and one long weekend outdoors."
    var action = "Get passes"
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop: Double = 8
    static let turn: Double = 4
    static let tilt: Double = 22 * .pi / 180
    static let focal: Double = 1000
    static let glide: Double = 0.5
    static let tick = Double.pi / 6
    static let paper = (r: 0.933, g: 0.929, b: 0.910) // #EEEDE8
    static let night = (r: 0.055, g: 0.055, b: 0.063) // #0E0E10

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoStart: Date? = nil
    /// The manual angle, and the glide after a release (when, speed in rad/s).
    @State private var theta = 0.0
    @State private var release: (date: Date, speed: Double)? = nil
    @State private var dragStart: Double? = nil
    @State private var lastTick = 0
    /// The colours by hand: dark amount from `darkFrom` towards `darkTo` since `darkSince`.
    @State private var darkFrom = 0.0
    @State private var darkTo = 0.0
    @State private var darkSince = Date.distantPast
    /// The ease back to the start before the loop restarts.
    @State private var returning: (date: Date, theta: Double, target: Double, dark: Double)? = nil
    @State private var resume: Task<Void, Never>? = nil
    /// Reduced motion: true while a tap's colour fade plays, so the timeline can pause otherwise.
    @State private var fading = false
    @State private var haptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let ring = RevolvingTextRingGeometry(big: bigBand, small: smallBand, width: size.width)
            TimelineView(.animation(paused: reduceMotion && dragStart == nil && !fading)) { timeline in
                let now = timeline.date
                let angle = angle(at: now)
                let dark = dark(at: now)
                let bg = Self.mix(Self.paper, Self.night, dark)
                let ink = Self.mix(Self.night, Self.paper, dark)
                ZStack(alignment: .topLeading) {
                    bg
                    canvas(ring: ring, size: size, angle: angle, ink: ink)
                        .contentShape(Rectangle())
                        .gesture(drag(radius: ring.radius))
                    chrome(size: size, insets: insets, ring: ring, ink: ink, bg: bg)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func angle(at date: Date) -> Double {
        if let autoStart, date >= autoStart, !reduceMotion {
            let e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return 2 * .pi * e / Self.turn
        }
        if let r = returning {
            let u = RevolvingTextRingMath.easeInOut(min(1, max(0, date.timeIntervalSince(r.date) / 1.0)))
            return r.theta + (r.target - r.theta) * u
        }
        if let release, !reduceMotion {
            let t = max(0, date.timeIntervalSince(release.date))
            return theta + release.speed * Self.glide * (1 - exp(-t / Self.glide))
        }
        return theta
    }

    private func dark(at date: Date) -> Double {
        if let autoStart, date >= autoStart, !reduceMotion {
            let e = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            if e < 3.2 { return 0 }
            if e < 4.0 { return RevolvingTextRingMath.easeInOut((e - 3.2) / 0.8) }
            if e < 7.2 { return 1 }
            return 1 - RevolvingTextRingMath.easeInOut((e - 7.2) / 0.8)
        }
        if let r = returning {
            let u = RevolvingTextRingMath.easeInOut(min(1, max(0, date.timeIntervalSince(r.date) / 1.0)))
            return r.dark * (1 - u)
        }
        let u = RevolvingTextRingMath.easeInOut(min(1, max(0, date.timeIntervalSince(darkSince) / 0.6)))
        return darkFrom + (darkTo - darkFrom) * u
    }

    /// Freezes whatever is playing into the manual state.
    private func takeOver(at now: Date) {
        resume?.cancel()
        let a = angle(at: now), d = dark(at: now)
        theta = a
        release = nil
        returning = nil
        autoStart = nil
        darkFrom = d
        darkTo = d
        darkSince = now
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            let a = angle(at: now)
            let target = (a / (2 * .pi)).rounded() * 2 * .pi
            returning = (now, a, target, dark(at: now))
            autoStart = now.addingTimeInterval(1.0)
        }
    }

    private func drag(radius: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if dragStart == nil {
                    takeOver(at: now)
                    dragStart = theta
                    lastTick = Int((theta / Self.tick).rounded(.down))
                }
                guard let start = dragStart, abs(value.translation.width) > 2 || abs(value.translation.height) > 6 else { return }
                theta = start - value.translation.width / radius
                let t = Int((theta / Self.tick).rounded(.down))
                if t != lastTick {
                    lastTick = t
                    haptic.selectionChanged()
                }
            }
            .onEnded { value in
                let now = Date.now
                let moved = hypot(value.translation.width, value.translation.height) > 6
                dragStart = nil
                if moved {
                    if !reduceMotion { release = (now, max(-12, min(12, -value.velocity.width / radius))) }
                } else {
                    let d = dark(at: now)
                    darkFrom = d
                    darkTo = d > 0.5 ? 0 : 1
                    darkSince = now
                    tapHaptic.impactOccurred()
                    if reduceMotion {
                        fading = true
                        Task { @MainActor in
                            try? await Task.sleep(for: .seconds(0.65))
                            if darkSince == now { fading = false }
                        }
                    }
                }
                scheduleResume()
            }
    }

    // MARK: Drawing

    private func canvas(ring: RevolvingTextRingGeometry, size: CGSize, angle: Double, ink: Color) -> some View {
        let center = CGPoint(x: size.width / 2, y: size.height * 0.43)
        return Canvas { ctx, _ in
            var resolved: [String: GraphicsContext.ResolvedText] = [:]
            func text(_ ch: Character, big: Bool) -> GraphicsContext.ResolvedText {
                let key = "\(big ? "B" : "S")\(ch)"
                if let r = resolved[key] { return r }
                let r = ctx.resolve(Text(String(ch))
                    .font(RevolvingTextRingFont.font(size: big ? ring.bigSize : ring.smallSize))
                    .foregroundColor(ink))
                resolved[key] = r
                return r
            }
            for tile in ring.tiles(angle: angle, center: center) {
                var c = ctx
                c.concatenate(tile.transform)
                c.opacity = tile.opacity
                c.draw(text(tile.char, big: tile.big), at: .zero, anchor: .center)
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityElement()
        .accessibilityLabel("\(bigBand) \(smallBand)")
    }

    private func chrome(size: CGSize, insets: EdgeInsets, ring: RevolvingTextRingGeometry, ink: Color, bg: Color) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text(header)
                Spacer()
                Text(dates)
            }
            .font(RevolvingTextRingFont.font(size: 13))
            .tracking(1.5)
            .foregroundStyle(ink)
            .padding(.horizontal, 24)
            .frame(width: size.width)
            .offset(y: insets.top + 16)
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .font(RevolvingTextRingFont.font(size: 26))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                Text(subtitle)
                    .font(.system(size: 16))
                    .lineSpacing(3)
                    .opacity(0.6)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(ink)
            .frame(width: size.width - 48, alignment: .leading)
            .offset(x: 24, y: size.height - max(insets.bottom, 20) - 16 - 56 - 32 - 112)
            .allowsHitTesting(false)

            Button {
                onAction?()
            } label: {
                Text(action)
                    .font(RevolvingTextRingFont.font(size: 16))
                    .tracking(0.5)
                    .foregroundStyle(bg)
                    .frame(width: size.width - 48, height: 56)
                    .background(Capsule().fill(ink))
            }
            .buttonStyle(.plain)
            .offset(x: 24, y: size.height - max(insets.bottom, 20) - 16 - 56)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    static func mix(_ a: (r: Double, g: Double, b: Double), _ b: (r: Double, g: Double, b: Double), _ t: Double) -> Color {
        Color(red: a.r + (b.r - a.r) * t, green: a.g + (b.g - a.g) * t, blue: a.b + (b.b - a.b) * t)
    }
}

// MARK: - Font

enum RevolvingTextRingFont {
    static let name = "ArchivoBlack-Regular"
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "RevolvingTextRing-ArchivoBlack-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func font(size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    /// Advances of a string's characters at 1 pt (no kerning, as in every stack).
    static func advances(_ text: String) -> [Double] {
        _ = registered
        let font = CTFontCreateWithName(name as CFString, 1, nil)
        var chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, glyphs.count)
        return adv.map { Double($0.width) }
    }
}

// MARK: - Geometry

/// The ring's bands: every glyph's angle on the cylinder and its height.
struct RevolvingTextRingGeometry {
    struct Glyph {
        var char: Character
        var big: Bool
        /// Angle of the glyph's centre on the ring at rotation 0 (0 faces the viewer, growing to the right).
        var phi: Double
        /// Height of the band's centre (y down).
        var y: Double
    }

    struct Tile {
        var char: Character
        var big: Bool
        var transform: CGAffineTransform
        var opacity: Double
        var depth: Double
    }

    static let smallTracking = 0.32 // em
    static let capHeight = 0.688 // em, Archivo Black

    let radius: Double
    let bigSize: Double
    let smallSize: Double
    let glyphs: [Glyph]

    init(big: String, small: String, width: Double) {
        let r = min(width * 0.39, 170)
        radius = r
        let circumference = 2 * .pi * r
        let bigAdv = RevolvingTextRingFont.advances(big)
        let smallAdv = RevolvingTextRingFont.advances(small)
        // Each band's size makes its text go round exactly once.
        bigSize = circumference / bigAdv.reduce(0, +)
        smallSize = circumference / (smallAdv.reduce(0, +) + Self.smallTracking * Double(smallAdv.count))
        let hBig = Self.capHeight * bigSize, hSmall = Self.capHeight * smallSize
        let gap = 0.45 * hSmall
        let yBig = -(hSmall + gap) / 2, ySmall = (hBig + gap) / 2
        var list: [Glyph] = []
        var s = 0.0
        for (ch, a) in zip(big, bigAdv) {
            let w = a * bigSize
            if ch != " " { list.append(Glyph(char: ch, big: true, phi: (s + w / 2) / r, y: yBig)) }
            s += w
        }
        s = 0
        for (ch, a) in zip(small, smallAdv) {
            let w = a * smallSize
            if ch != " " { list.append(Glyph(char: ch, big: false, phi: (s + w / 2) / r, y: ySmall)) }
            s += w + Self.smallTracking * smallSize
        }
        glyphs = list
    }

    /// Projects a point on the ring (x right, y down, z towards the viewer) seen from above.
    func project(_ x: Double, _ y: Double, _ z: Double, center: CGPoint) -> (CGPoint, Double) {
        let t = RevolvingTextRing.tilt
        let y2 = y * cos(t) + z * sin(t)
        let z2 = -y * sin(t) + z * cos(t)
        let p = RevolvingTextRing.focal / (RevolvingTextRing.focal - z2)
        return (CGPoint(x: center.x + x * p, y: center.y + y2 * p), z2)
    }

    /// Every glyph's tile at a rotation, back to front.
    func tiles(angle: Double, center: CGPoint) -> [Tile] {
        var tiles: [Tile] = []
        tiles.reserveCapacity(glyphs.count)
        for g in glyphs {
            let phi = g.phi - angle
            let (sp, cp) = (sin(phi), cos(phi))
            let x = radius * sp, z = radius * cp
            let (c, depth) = project(x, g.y, z, center: center)
            // The tile's axes: along the ring (tangent) and down the cylinder, by finite differences.
            let e = 1.0
            let (ct, _) = project(x + cp * e, g.y, z - sp * e, center: center)
            let (cu, _) = project(x, g.y + e, z, center: center)
            let transform = CGAffineTransform(
                a: (ct.x - c.x) / e, b: (ct.y - c.y) / e,
                c: (cu.x - c.x) / e, d: (cu.y - c.y) / e,
                tx: c.x, ty: c.y
            )
            // Facing: the outward normal's part towards the viewer after the tilt.
            let facing = cp * cos(RevolvingTextRing.tilt)
            let opacity = 0.3 + 0.7 * RevolvingTextRingMath.smoothstep(-0.15, 0.35, facing)
            tiles.append(Tile(char: g.char, big: g.big, transform: transform, opacity: opacity, depth: depth))
        }
        return tiles.sorted { $0.depth < $1.depth }
    }
}

enum RevolvingTextRingMath {
    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }
}
