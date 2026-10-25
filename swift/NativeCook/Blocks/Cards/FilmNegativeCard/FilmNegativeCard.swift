import SwiftUI
import UIKit

/*
 Film Negative Card — a photo-lab screen ("Darkroom Club — Roll 12, frame 7") with a strip of
 35 mm colour negative lying on a dark desk above a light table. Drag the strip onto the light:
 where it overlaps the lit panel the negative inverts into the positive photo (one Metal layer
 shader, FilmNegativeCard.metal), the light's soft edge sweeping across the frame; a loupe drifts
 over the face while it rests there. Timeline in FilmNegativeCardMotion.swift (loop = 8 s).

 Per frame: the strip's layer shader (uniforms only; its photo layer is static), two blurred
 shadow rects, the loupe ring and the NEGATIVE/POSITIVE chip. The desk and the rest of the roll
 are static views outside the timeline.

 Touch: drag the strip anywhere (vertical follows the finger, rubber-banded past the desk and the
 table; sideways it rubber-bands back). Release: a spring (0.45 s, damping ratio 0.78, carrying
 the finger's velocity) to the light or back to the desk, whichever the flick points at. Landing
 on the light brings the loupe in. Haptics: a selection tick when the strip passes halfway, a
 light impact when it lands on the light. 2 s after the last touch it eases back to the desk
 (0.9 s) and the loop starts again.
 Reduced motion: the strip rests on the light showing the positive; no loop, no dragging.
 */
struct FilmNegativeCard: View {
    /// Slides the negative onto the light table and back, with the loupe, on a loop.
    var autoPlay = false

    static let loop = FilmNegativeCardMotion.loop
    typealias M = FilmNegativeCardMotion
    typealias C = FilmNegativeCardPalette

    static let liftScale: Double = 0.018
    static let holdAfterTouch: Double = 2
    static let returnDuration: Double = 0.9
    static let rollH: CGFloat = 16

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drive = FilmNegativeCardDrive()
    /// Without autoPlay the timeline runs only while a finger or its spring is moving the strip.
    @State private var active = false
    @State private var settle: Task<Void, Never>? = nil
    @State private var selection = UISelectionFeedbackGenerator()
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var pressed = false

    var body: some View {
        GeometryReader { proxy in
            let ins = proxy.safeAreaInsets
            let L = FilmNegativeCardLayout(W: proxy.size.width + ins.leading + ins.trailing,
                                           H: proxy.size.height + ins.top + ins.bottom,
                                           top: ins.top, bottom: ins.bottom)
            ZStack(alignment: .topLeading) {
                FilmNegativeCardDesk(L: L).equatable()
                FilmNegativeCardRoll(L: L).equatable()
                TimelineView(.animation(paused: reduceMotion || !(autoPlay || active))) { timeline in
                    let v = drive.view(at: timeline.date, L: L, auto: autoPlay && !reduceMotion, reduced: reduceMotion)
                    moving(L, v)
                }
                Color.clear
                    .frame(width: L.W, height: L.H)
                    .contentShape(Rectangle())
                    .gesture(pan(L))
                    .accessibilityElement()
                    .accessibilityLabel("A strip of colour negative film. Drag it onto the light table to see it as a positive photo.")
                footer(L)
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            selection.prepare(); light.prepare()
            drive.begin(at: .now)
        }
        .onDisappear { settle?.cancel() }
    }

    // MARK: Drawing

    private func moving(_ L: FilmNegativeCardLayout, _ v: FilmNegativeCardDrive.View) -> some View {
        let s = 1 + Self.liftScale * v.lift
        let tx = L.stripX + v.x + L.stripW / 2 * (1 - s)
        let ty = v.y + L.stripH / 2 * (1 - s)
        let onAmount = M.clamp01((v.y - L.restY) / max(1, L.onY - L.restY))
        let P = L.panel
        let lit = CGRect(x: (P.minX - tx) / s, y: (P.minY - ty) / s, width: P.width / s, height: P.height / s)
        let F = L.frame
        let loupe = (x: F.minX + F.width * 0.66 + v.loupeDx, y: F.minY + F.height * 0.4 + v.loupeDy,
                     r: F.height * 0.3, a: CGFloat(v.loupe))
        let labels = FilmNegativeCardEdge.labels(L, roll: false)

        return ZStack(alignment: .topLeading) {
            // Shadows on the desk / table: tight contact + soft ambient that grows with lift.
            Canvas { ctx, _ in
                let grow = 6 * v.lift
                ctx.drawLayer { l in
                    l.opacity = max(0, 0.5 - 0.18 * v.lift - 0.2 * onAmount)
                    l.addFilter(.blur(radius: 14))
                    l.fill(Path(roundedRect: CGRect(x: L.stripX + v.x - grow / 2, y: v.y + 8 + 14 * v.lift, width: L.stripW + grow, height: L.stripH), cornerRadius: 8),
                           with: .color(.black))
                }
                ctx.drawLayer { l in
                    l.opacity = 0.75 * (1 - M.clamp01(v.lift))
                    l.addFilter(.blur(radius: 2.5))
                    l.fill(Path(roundedRect: CGRect(x: L.stripX + v.x + 2, y: v.y + 1.5 + 3 * v.lift, width: L.stripW - 4, height: L.stripH), cornerRadius: 5),
                           with: .color(.black))
                }
            }
            .frame(width: L.W, height: L.H)

            // The strip itself, with its edge print (lit print inside the light's rect).
            ZStack(alignment: .topLeading) {
                FilmNegativeCardStripView(L: L, roll: false, light: lit, loupe: loupe, sheen: v.y)
                Canvas { ctx, _ in
                    FilmNegativeCardEdge.draw(ctx, labels, color: C.edgeDark)
                    var c = ctx
                    c.clip(to: Path(lit))
                    FilmNegativeCardEdge.draw(c, labels, color: C.edgeLit)
                }
                .frame(width: L.stripW, height: L.stripH)
            }
            .frame(width: L.stripW, height: L.stripH, alignment: .topLeading)
            .transformEffect(CGAffineTransform(translationX: tx, y: ty).scaledBy(x: s, y: s))

            // The loupe's chrome ring, in screen space.
            if v.loupe > 0.001 {
                Canvas { ctx, _ in
                    let cx = tx + loupe.x * s, cy = ty + loupe.y * s
                    let r = loupe.r * s * (0.94 + 0.06 * loupe.a)
                    ctx.opacity = Double(loupe.a)
                    func circle(_ y: CGFloat, _ rr: CGFloat) -> Path {
                        Path(ellipseIn: CGRect(x: cx - rr, y: y - rr, width: rr * 2, height: rr * 2))
                    }
                    ctx.drawLayer { l in
                        l.addFilter(.blur(radius: 9))
                        l.fill(circle(cy + 7, r + 4), with: .color(.black.opacity(0.42)))
                    }
                    let chrome = Gradient(colors: [0x8C8782, 0xF3F0EA, 0x6A6662, 0xD9D4CC, 0x7D7873, 0xEDE9E2, 0x8C8782].map { C.rgb($0) })
                    ctx.stroke(circle(cy, r + 4), with: .conicGradient(chrome, center: CGPoint(x: cx, y: cy), angle: .zero), lineWidth: 8)
                    ctx.stroke(circle(cy, r - 0.5), with: .color(Color(.sRGB, red: 20 / 255, green: 14 / 255, blue: 10 / 255, opacity: 0.6)), lineWidth: 1.2)
                    ctx.stroke(circle(cy, r + 8.4), with: .color(Color(.sRGB, red: 1, green: 250 / 255, blue: 240 / 255, opacity: 0.35)), lineWidth: 0.8)
                }
                .frame(width: L.W, height: L.H)
            }

            header(L, onAmount: onAmount)
        }
        .frame(width: L.W, height: L.H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func header(_ L: FilmNegativeCardLayout, onAmount: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("DARKROOM CLUB")
                .font(.system(size: 12, weight: .bold)).tracking(2.2)
                .foregroundStyle(C.accent)
            Text("Roll 12, frame 7")
                .font(.system(size: 30, weight: .bold)).tracking(-0.6)
                .foregroundStyle(C.ink)
                .padding(.top, 4)
            Text("LUMA 400 · 36 exposures · developed Oct 14")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(C.mute)
                .padding(.top, 2)
            HStack {
                HStack(spacing: 8) {
                    Circle().fill(C.accent).frame(width: 6, height: 6)
                    VStack(alignment: .leading, spacing: 0) {
                        chipText("NEGATIVE")
                        chipText("POSITIVE")
                    }
                    .offset(y: -Self.rollH * M.smoothstep(0.66, 0.86, onAmount))
                    .frame(height: Self.rollH, alignment: .top)
                    .clipped()
                }
                .padding(.horizontal, 10)
                .frame(height: 24)
                .background(Capsule().fill(Color(.sRGB, red: 242 / 255, green: 166 / 255, blue: 90 / 255, opacity: 0.12)))
                .overlay(Capsule().strokeBorder(Color(.sRGB, red: 242 / 255, green: 166 / 255, blue: 90 / 255, opacity: 0.4), lineWidth: 1 / UIScreen.main.scale))
                Spacer(minLength: 8)
                Text("f/2.8 · 1/250 s · 50 mm")
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(C.mute)
            }
            .padding(.top, 12)
        }
        .frame(width: L.W - 48, alignment: .leading)
        .offset(x: 24, y: L.top + 12)
        .accessibilityElement(children: .combine)
    }

    private func chipText(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 11, weight: .bold)).tracking(1.4)
            .foregroundStyle(C.ink)
            .frame(height: Self.rollH)
    }

    private func footer(_ L: FilmNegativeCardLayout) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Lustre print, 4 × 6 in")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(C.mute)
                Text("$0.45 · ready Thursday")
                    .font(.system(size: 17, weight: .semibold).monospacedDigit())
                    .foregroundStyle(C.ink)
            }
            .layoutPriority(1)
            Spacer(minLength: 8)
            Text("Order print")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(C.rgb(0x2B1608))
                .padding(.horizontal, 24)
                .frame(height: 48)
                .background(
                    Capsule().fill(C.accent)
                        .overlay(Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.35), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                        .shadow(color: C.accent.opacity(0.22), radius: 12, y: 10)
                        .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                )
                .scaleEffect(pressed ? 0.97 : 1)
                .contentShape(Capsule())
                .onLongPressGesture(minimumDuration: 10, pressing: { down in
                    pressed = down
                    if down { selection.selectionChanged() }
                }, perform: {})
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: L.W - 44, height: FilmNegativeCardLayout.footerH)
        .offset(x: 24, y: L.footerY)
    }

    // MARK: Touch

    private func pan(_ L: FilmNegativeCardLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                let now = Date.now
                if !drive.touching {
                    guard drive.grab(at: now, y: value.startLocation.y, L: L, auto: autoPlay) else { return }
                    settle?.cancel()
                    active = true
                }
                if drive.move(at: now, dx: value.translation.width, dy: value.translation.height, L: L) {
                    selection.selectionChanged()
                }
            }
            .onEnded { value in
                guard drive.touching else { return }
                let now = Date.now
                if drive.release(at: now, vx: value.velocity.width, vy: value.velocity.height, L: L) {
                    light.impactOccurred()
                }
                settle?.cancel()
                if !autoPlay {
                    settle = Task { @MainActor in
                        try? await Task.sleep(for: .seconds(Self.holdAfterTouch + Self.returnDuration + 0.6))
                        if !Task.isCancelled && !drive.touching { active = false }
                    }
                }
            }
    }
}

/// The strip's state outside the autoPlay timeline: what the finger and its springs are doing.
/// A reference type read from the timeline's date, so the drawing never writes @State per frame.
final class FilmNegativeCardDrive {
    struct View {
        var y: CGFloat
        var x: CGFloat
        var lift: Double
        var loupe: Double
        var loupeDx: CGFloat
        var loupeDy: CGFloat
    }

    typealias M = FilmNegativeCardMotion
    enum Mode { case auto, manual, returning(until: Date) }

    var mode: Mode = .auto
    var touching = false
    private var autoStart = Date.now
    private var lastFrame: Date? = nil
    private var lastTouch = Date.distantPast
    private var grabY: CGFloat = 0
    private var grabX: CGFloat = 0
    private var halfSide = 0
    private let y = FilmNegativeCardChannel()
    private let x = FilmNegativeCardChannel()
    private let lift = FilmNegativeCardChannel()
    private let loupe = FilmNegativeCardChannel()

    func begin(at date: Date) {
        autoStart = date
        lastFrame = nil
        mode = .auto
    }

    private func loopTime(_ date: Date, auto: Bool) -> Double {
        guard auto else { return 0 }
        // A stalled frame pauses the loop clock instead of skipping ahead.
        if let last = lastFrame, date.timeIntervalSince(last) > 0.25 {
            autoStart = autoStart.addingTimeInterval(date.timeIntervalSince(last) - 1 / 60)
        }
        lastFrame = date
        return max(0, date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
    }

    func view(at date: Date, L: FilmNegativeCardLayout, auto: Bool, reduced: Bool) -> View {
        if reduced {
            let p = M.pose(3.02)
            return View(y: L.restY + CGFloat(p.on) * (L.onY - L.restY), x: 0, lift: p.lift, loupe: p.loupe,
                        loupeDx: CGFloat(p.loupeDx), loupeDy: CGFloat(p.loupeDy))
        }
        switch mode {
        case .manual where !touching && date.timeIntervalSince(lastTouch) > FilmNegativeCard.holdAfterTouch:
            let start = lastTouch.addingTimeInterval(FilmNegativeCard.holdAfterTouch)
            x.tween(to: 0, duration: FilmNegativeCard.returnDuration, curve: M.returnEase, at: start)
            y.tween(to: L.homeY, duration: FilmNegativeCard.returnDuration, curve: M.returnEase, at: start)
            loupe.tween(to: 0, duration: 0.25, curve: M.easeInOutQuad, at: start)
            mode = .returning(until: start.addingTimeInterval(FilmNegativeCard.returnDuration))
            return view(at: date, L: L, auto: auto, reduced: reduced)
        case let .returning(until) where date >= until:
            mode = .auto
            autoStart = until
            lastFrame = nil
            return view(at: date, L: L, auto: auto, reduced: reduced)
        case .auto:
            let p = M.pose(loopTime(date, auto: auto))
            return View(y: L.restY + CGFloat(p.on) * (L.onY - L.restY), x: 0, lift: p.lift, loupe: p.loupe,
                        loupeDx: CGFloat(p.loupeDx), loupeDy: CGFloat(p.loupeDy))
        default:
            return View(y: CGFloat(y.value(at: date)), x: CGFloat(x.value(at: date)),
                        lift: lift.value(at: date), loupe: loupe.value(at: date), loupeDx: 0, loupeDy: 0)
        }
    }

    /// A finger lands; true when it is on the strip.
    func grab(at date: Date, y py: CGFloat, L: FilmNegativeCardLayout, auto: Bool) -> Bool {
        let v = view(at: date, L: L, auto: auto, reduced: false)
        guard py > v.y - 16 && py < v.y + L.stripH + 16 else { return false }
        touching = true
        lastTouch = date
        if case .manual = mode {} else {
            y.set(Double(v.y)); x.set(Double(v.x)); loupe.set(v.loupe); lift.set(v.lift)
            mode = .manual
        }
        // Stop where it is under the finger.
        y.set(y.value(at: date)); x.set(x.value(at: date))
        grabY = CGFloat(y.value(at: date))
        grabX = CGFloat(x.value(at: date))
        halfSide = side(grabY, L)
        lift.spring(to: 1, response: 0.35, damping: 0.8, at: date)
        loupe.tween(to: 0, duration: 0.16, curve: M.easeInOutQuad, at: date)
        return true
    }

    private func side(_ yy: CGFloat, _ L: FilmNegativeCardLayout) -> Int {
        let on = M.clamp01((yy - L.restY) / max(1, L.onY - L.restY))
        return on > (1 + M.restOn) / 2 ? 1 : 0
    }

    /// The finger moves; true when the strip crosses halfway (a selection tick).
    func move(at date: Date, dx: CGFloat, dy: CGFloat, L: FilmNegativeCardLayout) -> Bool {
        guard touching else { return false }
        lastTouch = date
        let ny = M.rubber(Double(grabY + dy), Double(L.restY - 4), Double(L.onY + 4))
        y.set(ny)
        x.set(M.rubber(Double(grabX + dx), 0, 0))
        let s = side(CGFloat(ny), L)
        defer { halfSide = s }
        return s != halfSide
    }

    /// The finger lifts; true when the strip is sent to the light.
    func release(at date: Date, vx: CGFloat, vy: CGFloat, L: FilmNegativeCardLayout) -> Bool {
        touching = false
        lastTouch = date
        let cur = CGFloat(y.value(at: date))
        let toLight = cur + vy * 0.12 > (L.homeY + L.onY) / 2
        y.spring(to: Double(toLight ? L.onY : L.homeY), response: 0.45, damping: 0.78, velocity: Double(vy), at: date)
        x.spring(to: 0, response: 0.4, damping: 0.8, velocity: Double(vx), at: date)
        lift.spring(to: 0, response: 0.45, damping: 0.85, velocity: 0, at: date)
        if toLight {
            loupe.tween(to: 1, duration: 0.32, delay: 0.28, curve: M.loupeEase, at: date)
        }
        return toLight
    }
}
