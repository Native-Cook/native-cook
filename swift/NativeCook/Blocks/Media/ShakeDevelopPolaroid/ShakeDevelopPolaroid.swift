import CoreMotion
import SwiftUI
import UIKit

/*
 Shake Develop Polaroid — a freshly ejected instant print that develops as you shake it.

 Look: a dark aubergine viewer (#1F161C, a soft warm lift #3A2632 behind the print). Header: a back
 chevron and a "•••" in 36 pt translucent circles, "Today" over "10:10 AM" in the middle. The print: a
 warm white instant film (#F5F2EC, corner 4 pt) 80 % of the width (at most 330 pt), a square photo inset
 16 pt with a 64 pt bottom margin, centred at 45 % of the height, with a deep shadow that slides against
 the shake. Under it a phone glyph and "Shake to develop" (or a check and "Developed"), and a 140 pt bar
 for how far it has developed. A bottom bar: share, the roll (a 40 pt thumbnail of the developed photo,
 white 2 pt border), trash.

 Development and the autoPlay timeline (loop = 8 s): ShakeDevelopPolaroidModel.swift. The undeveloped
 photo is desaturated, flat and milky, with a veil that lifts from the bottom up.

 Touch: drag the print back and forth: it follows the finger (90 % across, 25 % down, tilting 12° per
 print width) and every point of sideways travel develops it (1/1600 per point); on release it springs
 back (e^(−5 s), 2.2 Hz). On a phone, shaking it does the same through Core Motion: each jolt above 0.35 g
 kicks the print sideways and develops it. A light haptic every tenth, a success haptic when developed,
 then the glint. 2 s after the last touch the print finishes developing by itself (0.8 s) and the loop
 carries on from the drop into the roll.
 Reduced motion: no loop and no shake; the print shows developed, with "Developed".
 */

struct ShakeDevelopPolaroid: View {
    var autoPlay = false

    static let loop = ShakeDevelopPolaroidMotion.loop
    typealias M = ShakeDevelopPolaroidMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Date? = nil
    /// The manual state.
    @State private var dev = 0.0
    @State private var drag: CGSize? = nil
    @State private var lastX = 0.0
    @State private var release: (date: Date, x: Double, y: Double, r: Double)? = nil
    @State private var kick: (date: Date, amp: Double)? = nil
    @State private var developedAt: Date? = nil
    @State private var finishing: (date: Date, from: Double)? = nil
    @State private var holding = false
    /// Resets by itself when the system cancels the drag (onEnded is not called then).
    @GestureState private var pressing = false
    @State private var active = false
    @State private var resume: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var notify = UINotificationFeedbackGenerator()
    @State private var motion = CMMotionManager()
    @State private var lastKick = Date.distantPast

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var card: CGRect
        var photo: CGRect   // in card coordinates
        var hintY: Double
        var barY: Double
        var dockY: Double

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            let w = min(size.width * 0.8, 330)
            let h = 16 + (w - 32) + 64
            card = CGRect(x: (size.width - w) / 2, y: size.height * 0.45 - h / 2, width: w, height: h)
            photo = CGRect(x: 16, y: 16, width: w - 32, height: w - 32)
            hintY = card.maxY + 36
            barY = hintY + 28
            dockY = size.height - max(insets.bottom, 20) - 30
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: !running)) { timeline in
                let s = scene(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    backdrop(layout)
                    header(layout)
                    hint(layout, s)
                    dock(layout, s)
                    printCard(layout, s, gesture: dragGesture(layout))
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .onChange(of: pressing) { _, now in if !now { endDrag() } }
        .onAppear {
            light.prepare()
            notify.prepare()
            if reduceMotion {
                dev = 1
                developedAt = .distantPast
            } else {
                if autoPlay { autoStart = .now }
                startMotion()
            }
        }
        .onDisappear {
            resume?.cancel()
            motion.stopDeviceMotionUpdates()
        }
    }

    private var running: Bool { (autoStart != nil && !reduceMotion) || active }

    // MARK: Time

    private func scene(at date: Date) -> ShakeDevelopPolaroidScene {
        if let start = autoStart, date >= start {
            let t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
            return M.scene(t)
        }
        var s = ShakeDevelopPolaroidScene()
        s.develop = dev
        if let f = finishing {
            let u = date.timeIntervalSince(f.date)
            s.develop = f.from + (1 - f.from) * M.inOut(u / 0.8)
            if f.from < 1 { s.glint = u < 0.75 ? u / 0.75 : -1 }
            s.label = f.from >= 1 ? 1 : M.smoothstep(0.5, 0.8, u)
        } else if let d = developedAt {
            let u = date.timeIntervalSince(d)
            s.label = M.smoothstep(0, 0.3, u)
            if u >= 0 && u < 0.7 { s.glint = u / 0.7 }
        }
        if let drag {
            s.x = drag.width * 0.9
            s.y = drag.height * 0.25
            s.rotation = s.x / 314 * 12
        } else if let r = release, !reduceMotion {
            let tau = date.timeIntervalSince(r.date)
            let f = exp(-5 * tau) * cos(2 * .pi * 2.2 * tau)
            s.x = r.x * f; s.y = r.y * f; s.rotation = r.r * f
        }
        if let k = kick, !reduceMotion {
            let tau = date.timeIntervalSince(k.date)
            let f = exp(-6 * tau) * sin(2 * .pi * 3 * tau)
            s.x += k.amp * f
            s.rotation += k.amp / 314 * 40 * f
        }
        return s
    }

    // MARK: Touch and shake

    /// Stops the loop and keeps what it showed, unless the print is on its way to the roll.
    private func takeOver(at now: Date) -> Bool {
        resume?.cancel()
        active = true
        if let start = autoStart, !reduceMotion {
            let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
            guard t < 5.9 || t >= 7.5 else { return false }
            let s = M.scene(t)
            dev = s.develop
            developedAt = s.develop >= 1 ? now.addingTimeInterval(-1) : nil
            release = (now, s.x, s.y, s.rotation)
            autoStart = nil
        }
        if finishing != nil {
            dev = scene(at: now).develop
            if dev >= 1 { developedAt = now.addingTimeInterval(-1) }
            finishing = nil
        }
        return true
    }

    private func develop(by amount: Double, at now: Date) {
        guard dev < 1 else { return }
        let before = dev
        dev = min(1, dev + amount)
        if Int(dev * 10) > Int(before * 10) && dev < 1 { light.impactOccurred(intensity: 0.5) }
        if dev >= 1 {
            developedAt = now
            notify.notificationOccurred(.success)
        }
    }

    private func dragGesture(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !holding {
                    holding = takeOver(at: now)
                    guard holding else { return }
                    lastX = 0
                    release = nil
                }
                guard holding else { return }
                if reduceMotion {
                    drag = CGSize(width: value.translation.width * 0.25, height: value.translation.height * 0.25)
                    return
                }
                drag = value.translation
                let dx = abs(value.translation.width - lastX)
                lastX = value.translation.width
                develop(by: dx / 1600, at: now)
            }
            .onEnded { _ in endDrag() }
            .updating($pressing) { _, state, _ in state = true }
    }

    private func endDrag() {
        guard holding else { return }
        holding = false
        let s = scene(at: .now)
        drag = nil
        release = reduceMotion ? nil : (.now, s.x, s.y, s.rotation)
        scheduleResume()
    }

    private func startMotion() {
        guard motion.isDeviceMotionAvailable else { return }
        motion.deviceMotionUpdateInterval = 1.0 / 60
        motion.startDeviceMotionUpdates(to: .main) { data, _ in
            guard let a = data?.userAcceleration else { return }
            let g = (a.x * a.x + a.y * a.y + a.z * a.z).squareRoot()
            let now = Date.now
            guard g > 0.35, now.timeIntervalSince(lastKick) > 0.12, !holding else { return }
            guard takeOver(at: now) else { return }
            lastKick = now
            kick = (now, max(-24, min(24, a.x * 60)))
            develop(by: min(0.06, (g - 0.35) * 0.05 + 0.015), at: now)
            scheduleResume()
        }
    }

    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            guard !reduceMotion else {
                drag = nil
                active = false
                return
            }
            let now = Date.now
            // The print finishes developing by itself (0.8 s), then the loop carries on from the drop into the roll.
            finishing = (now, dev)
            release = nil
            kick = nil
            try? await Task.sleep(for: .seconds(0.8))
            guard !Task.isCancelled else { return }
            finishing = nil
            if autoPlay {
                dev = 0
                developedAt = nil
                autoStart = Date.now.addingTimeInterval(-5.9)
            } else {
                dev = 1
                developedAt = .distantPast
                active = false
            }
        }
    }

    // MARK: Drawing

    private func backdrop(_ layout: Layout) -> some View {
        Canvas { ctx, sz in
            ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(Color(red: 0.122, green: 0.086, blue: 0.110)))
            ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .radialGradient(
                Gradient(colors: [Color(red: 0.227, green: 0.149, blue: 0.196), Color(red: 0.227, green: 0.149, blue: 0.196).opacity(0)]),
                center: CGPoint(x: layout.card.midX, y: layout.card.midY), startRadius: 0, endRadius: sz.width * 0.85))
        }
        .frame(width: layout.size.width, height: layout.size.height)
        .allowsHitTesting(false)
    }

    private func header(_ layout: Layout) -> some View {
        HStack {
            circleButton { ShakeDevelopPolaroidGlyph(kind: .back).stroke(.white.opacity(0.85), style: .init(lineWidth: 1.8, lineCap: .round, lineJoin: .round)) }
            Spacer()
            VStack(spacing: 1) {
                Text("Today").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                Text("10:10 AM").font(.system(size: 12)).foregroundStyle(.white.opacity(0.55))
            }
            Spacer()
            circleButton { ShakeDevelopPolaroidGlyph(kind: .more).fill(.white.opacity(0.85)) }
        }
        .padding(.horizontal, 20)
        .frame(width: layout.size.width, height: 44)
        .offset(y: layout.insets.top + 6)
        .allowsHitTesting(false)
    }

    private func circleButton<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ZStack {
            Circle().fill(.white.opacity(0.08))
            content().frame(width: 18, height: 18)
        }
        .frame(width: 36, height: 36)
    }

    private func hint(_ layout: Layout, _ s: ShakeDevelopPolaroidScene) -> some View {
        let shaking = s.label < 1 ? s.x / 17 * 14 : 0
        return ZStack(alignment: .top) {
            HStack(spacing: 8) {
                ShakeDevelopPolaroidGlyph(kind: .phone)
                    .stroke(.white.opacity(0.75), style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                    .rotationEffect(.degrees(reduceMotion ? 0 : shaking))
                Text("Shake to develop").font(.system(size: 14, weight: .medium)).foregroundStyle(.white.opacity(0.75))
            }
            .opacity(1 - s.label)
            HStack(spacing: 8) {
                ShakeDevelopPolaroidGlyph(kind: .check)
                    .stroke(Color(red: 0.55, green: 0.90, blue: 0.80), style: .init(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 20, height: 20)
                Text("Developed").font(.system(size: 14, weight: .medium)).foregroundStyle(.white.opacity(0.9))
            }
            .opacity(s.label)
            // How far it has developed.
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.12)).frame(width: 140, height: 3)
                Capsule().fill(Color(red: 0.55, green: 0.90, blue: 0.80)).frame(width: max(3, 140 * s.develop), height: 3)
            }
            .opacity(min(1, max(0, s.bar)))
            .offset(y: 28)
        }
        .frame(width: layout.size.width)
        .offset(y: layout.hintY - 10)
        .allowsHitTesting(false)
    }

    private func dock(_ layout: Layout, _ s: ShakeDevelopPolaroidScene) -> some View {
        HStack {
            ShakeDevelopPolaroidGlyph(kind: .share)
                .stroke(.white.opacity(0.75), style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 22, height: 22)
            Spacer()
            ShakeDevelopPolaroidAssets.photo
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 36, height: 36)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .padding(2)
                .background(Color(red: 0.96, green: 0.95, blue: 0.925), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .scaleEffect(1 + s.bump)
            Spacer()
            ShakeDevelopPolaroidGlyph(kind: .trash)
                .stroke(.white.opacity(0.75), style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 22, height: 22)
        }
        .padding(.horizontal, 32)
        .frame(width: layout.size.width, height: 44)
        .offset(y: layout.dockY - 22)
        .allowsHitTesting(false)
    }

    private func printCard<G: Gesture>(_ layout: Layout, _ s: ShakeDevelopPolaroidScene, gesture: G) -> some View {
        let c = layout.card
        let roll = CGPoint(x: layout.size.width / 2, y: layout.dockY)
        let cx = c.midX + (roll.x - c.midX) * s.toRoll + s.x
        let cy = c.midY + (roll.y - c.midY) * s.toRoll + s.y
        let rotation = s.rotation * (1 - s.toRoll)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(LinearGradient(colors: [Color(red: 0.965, green: 0.953, blue: 0.929), Color(red: 0.925, green: 0.910, blue: 0.880)],
                                     startPoint: .top, endPoint: .bottom))
            ShakeDevelopPolaroidPhoto(develop: s.develop, glint: s.glint)
                .frame(width: layout.photo.width, height: layout.photo.height)
                .offset(x: layout.photo.minX, y: layout.photo.minY)
        }
        .frame(width: c.width, height: c.height, alignment: .topLeading)
        .compositingGroup()
        .rotationEffect(.degrees(rotation))
        .scaleEffect(s.scale)
        .shadow(color: .black.opacity(0.55), radius: 24, x: -s.x * 0.35, y: 16 + s.y * 0.3)
        // Print and shadow fade as one, so the shadow never shows through a half-faded print.
        .compositingGroup()
        .opacity(s.alpha)
        .contentShape(Rectangle())
        .gesture(gesture)
        .position(x: cx, y: cy)
        .accessibilityElement()
        .accessibilityLabel(s.develop >= 1 ? "Instant photo of a turquoise mountain lake at sunrise" : "Instant photo, still developing")
    }
}

/// The photo with its development: colour matrix, the milky veil lifting from the bottom, the glint.
struct ShakeDevelopPolaroidPhoto: View {
    var develop: Double
    var glint: Double

    var body: some View {
        Canvas { ctx, size in
            let rect = CGRect(origin: .zero, size: size)
            let image = ctx.resolve(ShakeDevelopPolaroidAssets.photo)
            ctx.drawLayer { l in
                let m = ShakeDevelopPolaroidMotion.matrix(develop)
                var cm = ColorMatrix()
                cm.r1 = Float(m[0]); cm.r2 = Float(m[1]); cm.r3 = Float(m[2]); cm.r4 = Float(m[3]); cm.r5 = Float(m[4])
                cm.g1 = Float(m[5]); cm.g2 = Float(m[6]); cm.g3 = Float(m[7]); cm.g4 = Float(m[8]); cm.g5 = Float(m[9])
                cm.b1 = Float(m[10]); cm.b2 = Float(m[11]); cm.b3 = Float(m[12]); cm.b4 = Float(m[13]); cm.b5 = Float(m[14])
                cm.a1 = 0; cm.a2 = 0; cm.a3 = 0; cm.a4 = 1; cm.a5 = 0
                l.addFilter(.colorMatrix(cm))
                l.draw(image, in: rect)
            }
            // The veil, as nine stops from the top (v = 1) to the bottom (v = 0).
            let stops = (0...8).map { i -> Gradient.Stop in
                let v = 1 - Double(i) / 8
                return .init(color: Color(red: 0.843, green: 0.867, blue: 0.855).opacity(ShakeDevelopPolaroidMotion.haze(develop, v)),
                             location: Double(i) / 8)
            }
            ctx.fill(Path(rect), with: .linearGradient(Gradient(stops: stops), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            if glint >= 0 {
                let g = -0.2 + 1.4 * glint
                let a = 0.35 * sin(.pi * glint)
                ctx.fill(Path(rect), with: .linearGradient(
                    Gradient(stops: [.init(color: .white.opacity(0), location: max(0, g - 0.15)),
                                     .init(color: .white.opacity(a), location: min(1, max(0, g))),
                                     .init(color: .white.opacity(0), location: min(1, g + 0.15))]),
                    startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)))
            }
            // The photo's edge sits a hair into the paper.
            ctx.stroke(Path(rect.insetBy(dx: 0.25, dy: 0.25)), with: .color(.black.opacity(0.15)), lineWidth: 0.5)
        }
    }
}

/// Line glyphs on a 24 × 24 grid.
struct ShakeDevelopPolaroidGlyph: Shape {
    enum Kind { case back, more, phone, check, share, trash }
    var kind: Kind

    func path(in rect: CGRect) -> Path {
        var p = Path()
        switch kind {
        case .back:
            p.move(to: CGPoint(x: 14.5, y: 5)); p.addLine(to: CGPoint(x: 7.5, y: 12)); p.addLine(to: CGPoint(x: 14.5, y: 19))
        case .more:
            for x in [5.0, 12, 19] { p.addEllipse(in: CGRect(x: x - 1.8, y: 10.2, width: 3.6, height: 3.6)) }
        case .phone:
            p.addRoundedRect(in: CGRect(x: 7, y: 3, width: 10, height: 18), cornerSize: CGSize(width: 2.5, height: 2.5))
            p.move(to: CGPoint(x: 10.5, y: 18)); p.addLine(to: CGPoint(x: 13.5, y: 18))
            p.move(to: CGPoint(x: 3.5, y: 8)); p.addQuadCurve(to: CGPoint(x: 3.5, y: 16), control: CGPoint(x: 1.5, y: 12))
            p.move(to: CGPoint(x: 20.5, y: 8)); p.addQuadCurve(to: CGPoint(x: 20.5, y: 16), control: CGPoint(x: 22.5, y: 12))
        case .check:
            p.move(to: CGPoint(x: 5, y: 12.5)); p.addLine(to: CGPoint(x: 10, y: 17.5)); p.addLine(to: CGPoint(x: 19, y: 7))
        case .share:
            p.move(to: CGPoint(x: 12, y: 3.5)); p.addLine(to: CGPoint(x: 12, y: 15))
            p.move(to: CGPoint(x: 7.5, y: 8)); p.addLine(to: CGPoint(x: 12, y: 3.5)); p.addLine(to: CGPoint(x: 16.5, y: 8))
            p.move(to: CGPoint(x: 8, y: 11)); p.addLine(to: CGPoint(x: 5, y: 11)); p.addLine(to: CGPoint(x: 5, y: 21))
            p.addLine(to: CGPoint(x: 19, y: 21)); p.addLine(to: CGPoint(x: 19, y: 11)); p.addLine(to: CGPoint(x: 16, y: 11))
        case .trash:
            p.move(to: CGPoint(x: 4, y: 6.5)); p.addLine(to: CGPoint(x: 20, y: 6.5))
            p.move(to: CGPoint(x: 9.5, y: 6.5)); p.addLine(to: CGPoint(x: 9.5, y: 4)); p.addLine(to: CGPoint(x: 14.5, y: 4)); p.addLine(to: CGPoint(x: 14.5, y: 6.5))
            p.move(to: CGPoint(x: 6, y: 6.5)); p.addLine(to: CGPoint(x: 7, y: 20)); p.addLine(to: CGPoint(x: 17, y: 20)); p.addLine(to: CGPoint(x: 18, y: 6.5))
        }
        let s = min(rect.width, rect.height) / 24
        return p.applying(CGAffineTransform(scaleX: s, y: s).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
    }
}

enum ShakeDevelopPolaroidAssets {
    static let photo: Image = {
        guard let url = Bundle.main.url(forResource: "ShakeDevelopPolaroid-lake", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return Image(systemName: "photo") }
        return Image(uiImage: image)
    }()
}
