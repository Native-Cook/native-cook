import SwiftUI
import UIKit

/// Foil Stamp Button — a stationery shop's checkout, "Paper & Press — Order #2047". A cream cotton
/// card waits for its monogram; the big round brass stamp at the bottom is the button. Pressing it
/// sends the stamp down (scale 1 → 0.94, the shadows tighten); on release it springs back and the
/// card shows the seal embossed in the paper, then a sweep lays champagne foil over it, and the
/// label becomes a drawn check and "Sealed". The timeline is in FoilStampButtonMotion (loop 8 s).
///
/// Touch: hold the stamp; the longer the hold, the deeper the press (full at 0.55 s). A release
/// before 0.35 s leaves a faint blind emboss with no foil, a full hold a crisp foil seal. Dragging
/// the card sideways swings the lamp, so the emboss and the foil's highlights move (rubber-banded,
/// sprung back on release). 2 s after the last touch the seal dissolves (0.8 s) and the loop starts
/// again from t = 0. Reduced motion: the sealed card, still; a tap on the stamp fades the seal out
/// or in (0.3 s).
struct FoilStampButton: View {
    /// Strikes, foils and dissolves the seal by itself, on a loop of `loop` s.
    var autoPlay = false

    static let loop = FoilStampButtonMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = FoilStampButtonLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            FoilStampButtonScene(layout: layout, autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(FoilStampButtonPalette.table.ignoresSafeArea())
    }
}

enum FoilStampButtonPalette {
    static let ink = Color(red: 0x2B / 255, green: 0x22 / 255, blue: 0x19 / 255)
    static let mute = Color(red: 0x7D / 255, green: 0x6D / 255, blue: 0x5C / 255)
    static let faint = Color(red: 0x9C / 255, green: 0x8A / 255, blue: 0x72 / 255)
    static let label = Color(red: 0xF3 / 255, green: 0xE3 / 255, blue: 0xBF / 255)
    static let foot = Color(red: 0x6F / 255, green: 0x60 / 255, blue: 0x4F / 255)
    static let table = Color(red: 0xE6 / 255, green: 0xDD / 255, blue: 0xCF / 255)

    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255,
            green: Double((v >> 8) & 0xFF) / 255,
            blue: Double(v & 0xFF) / 255,
            opacity: a
        )
    }
}

private struct FoilStampButtonScene: View {
    let layout: FoilStampButtonLayout
    let autoPlay: Bool
    let reduced: Bool

    @Environment(\.displayScale) private var displayScale
    @State private var engine = FoilStampButtonEngine()
    /// Keeps the timeline running while a touch's animations play outside autoPlay.
    @State private var awake = false

    private typealias P = FoilStampButtonPalette
    private typealias M = FoilStampButtonMotion

    var body: some View {
        let scale = min(displayScale, 3)
        let mask = FoilStampButtonArt.sealMask(radius: layout.sealR, scale: scale)
        let side = layout.maskSide
        let card = layout.card
        let button = layout.button
        let br = layout.buttonR

        ZStack(alignment: .topLeading) {
            FoilStampButtonPaper(layout: layout).equatable()

            TimelineView(.animation(paused: !(autoPlay || awake))) { timeline in
                let frame = engine.frame(at: timeline.date.timeIntervalSinceReferenceDate, autoPlay: autoPlay, reduced: reduced)
                let pose = frame.pose
                let _ = settle(frame.settled)
                ZStack(alignment: .topLeading) {
                    Image(uiImage: mask)
                        .resizable()
                        .frame(width: side, height: side)
                        .layerEffect(
                            ShaderLibrary.foilStampButtonSeal(
                                .float2(layout.seal.x - side / 2, layout.seal.y - side / 2),
                                .float(layout.sealR),
                                .float(pose.depth),
                                .float(pose.foil),
                                .float(pose.dissolve),
                                .float(pose.ring),
                                .float(pose.light),
                                .float(M.lightEl)
                            ),
                            maxSampleOffset: CGSize(width: 1, height: 1)
                        )
                        .offset(x: layout.seal.x - side / 2, y: layout.seal.y - side / 2)

                    FoilStampButtonCanvas(layout: layout, pose: pose)
                        .frame(width: layout.width, height: layout.height)

                    stampLabels(pose)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header
            cardText
            rows

            Text("Hold to press · drag the card to move the light")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(P.faint)
                .multilineTextAlignment(.center)
                .frame(width: layout.width - 48)
                .offset(x: 24, y: layout.hintTop)
                .allowsHitTesting(false)

            // The card: drag it sideways to swing the lamp.
            Color.clear
                .frame(width: card.width, height: card.height)
                .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .gesture(tiltGesture)
                .accessibilityElement()
                .accessibilityLabel("Monogram preview card. Drag sideways to move the light.")
                .offset(x: card.minX, y: card.minY)

            // The stamp's hit area.
            Color.clear
                .frame(width: br * 2 + 16, height: br * 2 + 16)
                .contentShape(Circle())
                .gesture(stampGesture)
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Seal order. Hold to press the stamp.")
                .offset(x: button.x - br - 8, y: button.y - br - 8)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }

    /// Lets the timeline sleep again once the finger's animations have finished.
    private func settle(_ settled: Bool) {
        guard settled, awake else { return }
        DispatchQueue.main.async { awake = false }
    }

    private func wake() {
        if !awake { awake = true }
    }

    // MARK: Text

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("PAPER & PRESS")
                .font(.system(size: 11, weight: .bold))
                .kerning(2.4)
                .foregroundStyle(P.faint)
            Text("Order #2047")
                .font(.system(size: 30, weight: .bold).monospacedDigit())
                .kerning(-0.4)
                .foregroundStyle(P.ink)
            Text("Wedding suite · ships in 6 days")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(P.mute)
        }
        .frame(width: layout.width - 48, alignment: .leading)
        .offset(x: 24, y: layout.headerTop)
        .allowsHitTesting(false)
    }

    private var cardText: some View {
        let card = layout.card
        return ZStack(alignment: .top) {
            HStack {
                Text("MONOGRAM PREVIEW")
                    .font(.system(size: 10, weight: .bold))
                    .kerning(1.6)
                    .foregroundStyle(P.faint)
                Spacer(minLength: 8)
                Text("No. 1 / 120")
                    .font(.system(size: 11, weight: .semibold).monospacedDigit())
                    .foregroundStyle(P.faint)
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)

            Text("Elena & Vic · 14 June 2027")
                .font(.custom("Georgia-Italic", size: 14))
                .foregroundStyle(P.foot)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 16)
        }
        .frame(width: card.width, height: card.height, alignment: .top)
        .offset(x: card.minX, y: card.minY)
        .allowsHitTesting(false)
    }

    private var rows: some View {
        let lines: [(String, String)] = [
            ("Cotton card, 600 gsm × 120", "$216.00"),
            ("Champagne foil seal", "$132.00"),
            ("Hand-deckled envelopes", "$36.00"),
        ]
        return VStack(spacing: 6) {
            ForEach(0..<lines.count, id: \.self) { i in
                let (name, price) = lines[i]
                HStack(alignment: .firstTextBaseline) {
                    Text(name)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(P.mute)
                    Spacer(minLength: 8)
                    Text(price)
                        .font(.system(size: 14, weight: .semibold).monospacedDigit())
                        .foregroundStyle(P.ink)
                }
                .frame(height: 22)
            }
            Rectangle()
                .fill(Color(red: 80 / 255, green: 60 / 255, blue: 35 / 255, opacity: 0.25))
                .frame(height: 1 / max(displayScale, 1))
                .padding(.vertical, 4)
            HStack(alignment: .firstTextBaseline) {
                Text("Total")
                    .font(.system(size: 16, weight: .bold))
                Spacer(minLength: 8)
                Text("$384.00")
                    .font(.system(size: 16, weight: .bold).monospacedDigit())
            }
            .foregroundStyle(P.ink)
            .frame(height: 22)
        }
        .frame(width: layout.card.width)
        .offset(x: layout.card.minX, y: layout.rowsTop)
        .allowsHitTesting(false)
    }

    /// "Seal order" and "Sealed", pressed and scaled with the stamp.
    private func stampLabels(_ pose: FoilStampButtonPose) -> some View {
        let s = 1 - M.pressScale * pose.press
        let b = layout.button
        let w = layout.buttonR * 2 + 16
        return ZStack {
            Text("Seal order")
                .font(.system(size: 15, weight: .bold))
                .kerning(0.2)
                .foregroundStyle(P.label)
                .opacity(1 - pose.check)
                .scaleEffect(s)
                .offset(y: pose.lift - pose.check * 6)
            Text("Sealed")
                .font(.system(size: 13, weight: .bold))
                .kerning(0.2)
                .foregroundStyle(P.label)
                .opacity(pose.check)
                .scaleEffect(s)
                .offset(y: 11 + pose.lift + (1 - pose.check) * 6)
        }
        .frame(width: w, height: w)
        .offset(x: b.x - w / 2, y: b.y - w / 2)
    }

    // MARK: Gestures

    private var stampGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !engine.stampDown else { return }
                wake()
                engine.stampBegan(now: Date.timeIntervalSinceReferenceDate, reduced: reduced)
            }
            .onEnded { _ in
                engine.stampEnded(now: Date.timeIntervalSinceReferenceDate)
            }
    }

    private var tiltGesture: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                guard !reduced else { return }
                wake()
                engine.tilt(translation: value.translation.width, now: Date.timeIntervalSinceReferenceDate)
            }
            .onEnded { value in
                guard !reduced else { return }
                engine.tiltEnded(velocity: value.velocity.width, now: Date.timeIntervalSinceReferenceDate)
            }
    }
}

/// The paper never changes, so it lives outside the timeline and is drawn by its own shader once.
private struct FoilStampButtonPaper: View, Equatable {
    let layout: FoilStampButtonLayout

    var body: some View {
        let c = layout.card
        Rectangle()
            .fill(.white)
            .frame(width: layout.width, height: layout.height)
            .colorEffect(ShaderLibrary.foilStampButtonPaper(
                .float2(layout.width, layout.height),
                .float4(c.minX, c.minY, c.width, c.height),
                .float(22)
            ))
            .allowsHitTesting(false)
    }
}

/// The flakes, the stamp's shadows and the stamp, for one pose.
private struct FoilStampButtonCanvas: View {
    let layout: FoilStampButtonLayout
    let pose: FoilStampButtonPose

    private typealias M = FoilStampButtonMotion
    private typealias P = FoilStampButtonPalette

    var body: some View {
        Canvas { ctx, _ in
            drawFlakes(&ctx)
            drawStamp(&ctx)
        }
    }

    private func drawFlakes(_ ctx: inout GraphicsContext) {
        guard pose.flakeAge >= 0 else { return }
        let opacity = min(1, pose.flakeAge / 0.08) * (1 - pose.dissolve) * min(1, pose.depth / 0.6)
        guard opacity > 0.001 else { return }
        let seal = layout.seal
        let R = layout.sealR
        var path = Path()
        for i in 0..<M.flakes {
            let f = M.flake(i, age: pose.flakeAge, radius: R)
            let cx = seal.x + f.x
            let cy = seal.y + f.y
            for k in 0..<4 {
                let ang = f.angle + Double(k) * .pi / 2
                let rr = f.size * (k % 2 == 0 ? 1 : 0.55)
                let pt = CGPoint(x: cx + cos(ang) * rr, y: cy + sin(ang) * rr)
                if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
        }
        var layer = ctx
        layer.opacity = opacity
        layer.fill(path, with: .linearGradient(
            Gradient(colors: [P.hex(0x8A6A2C), P.hex(0xF3DC9A), P.hex(0xB8913F), P.hex(0xFFF0C4), P.hex(0x9A7635)]),
            startPoint: CGPoint(x: seal.x - R, y: seal.y - R),
            endPoint: CGPoint(x: seal.x + R, y: seal.y + R)
        ))
    }

    private func circle(_ c: CGPoint, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }

    private func drawStamp(_ ctx: inout GraphicsContext) {
        let b = layout.button
        let br = layout.buttonR
        let press = max(0, pose.press)

        // The stamp's two shadows on the linen.
        ctx.drawLayer { l in
            l.opacity = 0.34 - 0.18 * press
            l.addFilter(.blur(radius: 16 - 9 * press))
            l.fill(circle(CGPoint(x: b.x, y: b.y + 12 - 9 * pose.press), br - 4), with: .color(P.hex(0x3C2810)))
        }
        ctx.drawLayer { l in
            l.opacity = 0.28 + 0.14 * press
            l.addFilter(.blur(radius: 3))
            l.fill(circle(CGPoint(x: b.x, y: b.y + 2), br - 1), with: .color(P.hex(0x2A1A08)))
        }

        // Lifted, pressed, squashed round its centre.
        var s = ctx
        let k = 1 - M.pressScale * pose.press - M.squashScale * pose.squash
        s.translateBy(x: b.x, y: b.y + pose.lift)
        s.scaleBy(x: k, y: k)
        s.translateBy(x: -b.x, y: -b.y)

        // Brass ring, a dark groove, and the walnut face.
        s.fill(circle(b, br), with: .conicGradient(
            Gradient(colors: [
                P.hex(0x7A5523), P.hex(0xE2C27A), P.hex(0xFFF1C9), P.hex(0xB48638), P.hex(0x5E3F18),
                P.hex(0xC9A253), P.hex(0xF4DC9C), P.hex(0x9A6F2C), P.hex(0x7A5523),
            ]),
            center: b, angle: .zero
        ))
        s.stroke(circle(b, br - 0.75), with: .linearGradient(
            Gradient(colors: [P.hex(0xFFF8E2, 0.75), P.hex(0xFFF8E2, 0), P.hex(0x462C0C, 0.5)]),
            startPoint: CGPoint(x: b.x, y: b.y - br), endPoint: CGPoint(x: b.x, y: b.y + br)
        ), lineWidth: 1.5)
        s.fill(circle(b, br - 7), with: .color(P.hex(0x3A2711)))
        s.fill(circle(b, br - 9), with: .radialGradient(
            Gradient(colors: [P.hex(0x5D4130), P.hex(0x3A281B), P.hex(0x24170F), P.hex(0x1B110B)]),
            center: CGPoint(x: b.x - 12, y: b.y - 16), startRadius: 0, endRadius: br * 1.3
        ))
        s.stroke(circle(b, br - 9.5), with: .linearGradient(
            Gradient(colors: [P.hex(0xFFF0D2, 0.22), P.hex(0xFFF0D2, 0), P.hex(0x000000, 0.3)]),
            startPoint: CGPoint(x: b.x, y: b.y - br), endPoint: CGPoint(x: b.x, y: b.y + br)
        ), lineWidth: 1)
        if pose.check > 0.001 {
            let check = FoilStampButtonArt.checkPath(cx: b.x, cy: b.y - 9).trimmedPath(from: 0, to: min(1, pose.check))
            s.stroke(check, with: .color(P.label), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
        }
    }
}

/// Who owns the screen (the loop or the finger), and the finger's animated values.
@MainActor
final class FoilStampButtonEngine {
    private typealias M = FoilStampButtonMotion
    private typealias C = FoilStampButtonCurve

    // Holding the stamp: full depth after holdFull s; a hold under holdWeak s is a weak strike.
    private static let holdFull = 0.55
    private static let holdWeak = 0.35
    // After the last touch the screen waits idle s, then eases back to rest over reset s.
    private static let idle = 2.0
    private static let reset = 0.8
    // Dragging the card swings the light by at most lightDrag radians.
    private static let lightDrag = 0.9

    private var loopStart: Double?
    private var clock = 0.0
    private var user = false
    private var reduced = false
    private var lastTouch = 0.0
    private var resetAt = -1.0
    private(set) var stampDown = false
    private var holdStart = 0.0
    private var reachedFull = false
    private var dragging = false
    private var lightAtGrab = 0.0

    private var press = FoilStampButtonTrack(0)
    private var squash = FoilStampButtonTrack(0)
    private var depth = FoilStampButtonTrack(0)
    private var foil = FoilStampButtonTrack(0)
    private var dissolve = FoilStampButtonTrack(0)
    private var ring = FoilStampButtonTrack(0)
    private var light = FoilStampButtonTrack(0)
    private var check = FoilStampButtonTrack(0)
    private var flakeStart = -1.0

    private let impact = UIImpactFeedbackGenerator(style: .light)

    private var configured = false

    /// Starts in the loop's rest, or, under reduced motion, on the sealed card.
    private func configure(reduced: Bool) {
        guard !configured || reduced != self.reduced else { return }
        configured = true
        self.reduced = reduced
        stampDown = false
        dragging = false
        resetAt = -1
        if reduced {
            let s = FoilStampButtonPose.sealed
            user = true
            depth.set(s.depth)
            foil.set(s.foil)
            check.set(s.check)
            dissolve.set(0)
            press.set(0)
            squash.set(0)
            ring.set(0)
            light.set(0)
            flakeStart = -10
        } else {
            user = false
            loopStart = nil
            clock = 0
        }
    }

    struct Frame {
        var pose: FoilStampButtonPose
        /// Nothing is moving and nothing will until the next touch.
        var settled: Bool
    }

    func frame(at now: Double, autoPlay: Bool, reduced: Bool) -> Frame {
        configure(reduced: reduced)
        if !user {
            if autoPlay {
                let start = loopStart ?? now
                loopStart = start
                clock = (now - start).truncatingRemainder(dividingBy: M.loop)
            } else {
                loopStart = nil
                clock = 0
            }
            return Frame(pose: M.loopPose(clock), settled: !autoPlay)
        }
        if !reduced { stepUser(now) }
        if !user { return Frame(pose: M.loopPose(clock), settled: !autoPlay) }
        let settled = reduced && [depth, foil, dissolve, check].allSatisfy { $0.done(at: now) }
        return Frame(pose: userPose(now), settled: settled)
    }

    private func stepUser(_ t: Double) {
        if stampDown {
            let held = t - holdStart
            press.set(M.easeOut(min(1, held / Self.holdFull)))
            if held >= Self.holdFull && !reachedFull {
                reachedFull = true
                contact(t)
                impact.impactOccurred()
            }
            return
        }
        if dragging { return }
        if resetAt < 0 && t - lastTouch > Self.idle {
            if depth.value(at: t) > 0.001 { dissolve.timing(1, duration: Self.reset, curve: .easeInOut, now: t) }
            check.timing(0, duration: 0.45, curve: .easeOut, now: t)
            press.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            light.timing(0, duration: Self.reset, curve: .easeInOut, now: t)
            resetAt = t + Self.reset + 0.05
        } else if resetAt > 0 && t >= resetAt {
            resetAt = -1
            clock = 0
            loopStart = t
            user = false
        }
    }

    private func userPose(_ t: Double) -> FoilStampButtonPose {
        let p = press.value(at: t)
        return FoilStampButtonPose(
            lift: M.descent * p,
            press: p,
            squash: squash.value(at: t),
            depth: depth.value(at: t),
            foil: foil.value(at: t),
            dissolve: dissolve.value(at: t),
            ring: ring.value(at: t),
            light: M.lightAz + light.value(at: t),
            check: check.value(at: t),
            flakeAge: flakeStart == -1 ? -1 : t - flakeStart
        )
    }

    /// Takes over from the loop without a jump: the finger's pose starts as the loop's frame.
    private func touched(_ now: Double) {
        lastTouch = now
        resetAt = -1
        if user { return }
        let p = M.loopPose(clock)
        press.set(p.press)
        squash.set(0)
        depth.set(p.depth)
        foil.set(p.foil)
        dissolve.set(p.dissolve)
        ring.set(0)
        light.set(p.light - M.lightAz)
        check.set(p.check)
        flakeStart = p.flakeAge >= 0 ? now - p.flakeAge : -1
        user = true
    }

    private func contact(_ t: Double) {
        squash.pulse(peak: 1, up: 0.07, down: 0.11, now: t)
        ring.pulse(peak: 1, up: 0.12, down: 0.34, now: t)
    }

    func stampBegan(now: Double, reduced: Bool) {
        stampDown = true
        touched(now)
        if reduced {
            // A tap fades the seal out, or back in.
            let on = depth.value(at: now) > 0.5 && dissolve.value(at: now) < 0.5
            if on {
                dissolve.timing(1, duration: 0.3, curve: .easeInOut, now: now)
                check.timing(0, duration: 0.3, curve: .easeInOut, now: now)
            } else {
                dissolve.set(0)
                depth.timing(1, duration: 0.3, curve: .easeInOut, now: now)
                foil.set(1)
                check.timing(1, duration: 0.3, curve: .easeInOut, now: now)
            }
            return
        }
        holdStart = now
        reachedFull = false
        // The old seal makes way for the new strike.
        depth.timing(0, duration: 0.16, now: now)
        foil.timing(0, duration: 0.16, now: now)
        check.timing(0, duration: 0.18, curve: .easeOut, now: now)
    }

    func stampEnded(now: Double) {
        guard stampDown else { return }
        stampDown = false
        if reduced { return }
        let held = now - holdStart
        lastTouch = now
        let strength = held < Self.holdWeak
            ? 0.3 + (held / Self.holdWeak) * 0.25
            : min(1, 0.55 + ((held - Self.holdWeak) / (Self.holdFull - Self.holdWeak)) * 0.45)
        if !reachedFull {
            contact(now)
            impact.impactOccurred()
        }
        press.spring(0, duration: 0.55, dampingRatio: 0.45, now: now)
        dissolve.set(0)
        depth.timing(strength, duration: 0.3, curve: .easeOut, now: now)
        let crisp = strength > 0.6
        flakeStart = crisp ? now : -1
        foil.set(0)
        if crisp {
            foil.timing(1, duration: 1.8, curve: .easeInOut, delay: 0.5, now: now)
            check.timing(1, duration: 0.6, curve: .easeOut, delay: 1.1, now: now)
        }
    }

    func tilt(translation: Double, now: Double) {
        if !dragging {
            touched(now)
            dragging = true
            lightAtGrab = light.value(at: now)
        }
        lastTouch = now
        light.set(lightAtGrab + (M.rubber(translation, 140) / 140) * Self.lightDrag)
    }

    func tiltEnded(velocity: Double, now: Double) {
        dragging = false
        lastTouch = now
        light.spring(0, duration: 0.7, dampingRatio: 0.7, velocity: (velocity / 140) * Self.lightDrag * 0.4, now: now)
    }
}
