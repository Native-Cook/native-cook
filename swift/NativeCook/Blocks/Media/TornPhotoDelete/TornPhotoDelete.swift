import SwiftUI
import UIKit

/*
 Torn Photo Delete — a day's instant photo you delete by tearing it in two.

 Look: a pale page (#EFEFF1, a soft pink glow behind the camera button at the lower right). Header: a trash
 glyph at the left, "Selected Day" over "Thursday, 2 July" in the middle. The print: a cream card
 (#F7EEDD → #EFE3CC, corner 3 pt, soft shadow) 85 % of the width (at most 340 pt), a 5:6 photo inset
 16 pt with a faint glare, and "02/07/2026" in a 13 pt mono in its 72 pt bottom margin; centred at 47 %
 of the height. A hint under it, "Drag across the print to tear it". When the print is gone, a dark toast
 "Photo deleted · Undo" rises above the camera button and "Nothing kept for this day" shows where it was.

 The tear (geometry in TornPhotoDeleteModel.swift): a jagged cut runs from the edge the finger started on
 towards the finger; the two flaps shear apart into a wedge (7 % of the distance from the tip), showing
 the white paper core along the cut. Released under 70 % of the way across, the flaps close (critically
 damped, ω = 16 /s); past it, the cut finishes and the halves fall away, spinning outwards.

 autoPlay: loop = 9 s, timeline in TornPhotoDeleteModel.swift: two short tears that heal, a full tear,
 the halves fall, the toast, a tap on Undo, the print comes back. A small grey disc shows the scripted
 finger.

 Touch: drag from anywhere on the print (the cut starts on the nearest edge); a light tick every 28 pt of
 tearing, a heavy one when it gives. Undo on the toast brings the print back. 2 s after the last touch the
 print comes back if it was torn and the loop starts again.
 Reduced motion: no loop. The cut still follows the finger; a finished tear fades the print out instead of
 dropping it, and it fades back in.
 */

struct TornPhotoDelete: View {
    var autoPlay = false

    static let loop = TornPhotoDeleteTimeline.loop
    typealias TL = TornPhotoDeleteTimeline
    typealias E = TornPhotoDeleteEase
    typealias Tear = TornPhotoDeleteTear

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The loop's t = 0. `quiet` hides the scripted finger on Undo (the loop was rejoined after a touch);
    /// `once` stops at the end of the loop instead of starting again (no autoPlay).
    @State private var autoStart: Date? = nil
    @State private var quietUntil: Date? = nil
    @State private var once = false

    /// The finger's tear.
    @State private var tear: Tear? = nil
    @State private var span = 0.0
    @State private var dragEdge: CGPoint? = nil
    @State private var healing: (tear: Tear, from: Date)? = nil
    @State private var committed: (tear: Tear, at: Date)? = nil
    @State private var gesture: GestureKind? = nil
    @State private var ticks = 0
    @State private var active = false
    /// Resets by itself when the system cancels a drag (onEnded is not called then).
    @GestureState private var pressing = false
    @State private var lastLayout: Layout? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var heavy = UIImpactFeedbackGenerator(style: .heavy)

    enum GestureKind { case tear, undo, none }

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var card: CGRect
        var photo: CGRect   // in card coordinates
        var toast: CGRect
        var undo: CGRect
        var fab: CGPoint

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            let w = min(size.width * 0.85, 340)
            let ph = (w - 32) * 1.2
            let h = 16 + ph + 72
            card = CGRect(x: (size.width - w) / 2, y: size.height * 0.47 - h / 2, width: w, height: h)
            photo = CGRect(x: 16, y: 16, width: w - 32, height: ph)
            let bottom = max(insets.bottom, 20)
            toast = CGRect(x: (size.width - 264) / 2, y: size.height - bottom - 128, width: 264, height: 48)
            undo = CGRect(x: toast.maxX - 84, y: toast.minY, width: 84, height: toast.height)
            fab = CGPoint(x: size.width - 24 - 28, y: size.height - bottom - 44)
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: !running)) { timeline in
                let scene = scene(at: timeline.date, layout: layout)
                ZStack(alignment: .topLeading) {
                    TornPhotoDeleteBackdrop(layout: layout)
                    header(layout)
                    emptyNote(layout, scene.empty)
                    Text("Drag across the print to tear it")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(Color(red: 0.56, green: 0.56, blue: 0.59))
                        .frame(width: size.width)
                        .offset(y: layout.card.maxY + 22)
                        .opacity(scene.hint)
                    card(layout, scene)
                    toast(layout, scene)
                    if let d = scene.dot { finger(d) }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(layout))
            }
            .ignoresSafeArea()
        }
        .onChange(of: pressing) { _, down in
            guard !down else { return }
            // A drag the system cancelled never ends: tidy up after it.
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(0.1))
                guard gesture != nil, let layout = lastLayout else { return }
                if gesture == .tear, let t = tear, t.length > 0 {
                    healing = (t, .now)
                    tear = nil
                }
                gesture = nil
                dragEdge = nil
                _ = layout
                scheduleResume()
            }
        }
        .onAppear {
            light.prepare()
            heavy.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    private var running: Bool { (autoStart != nil && !reduceMotion) || active }

    // MARK: Time

    private func scene(at date: Date, layout: Layout) -> TornPhotoDeleteScene {
        let undoCenter = CGPoint(x: layout.undo.midX, y: layout.undo.midY)
        if let start = autoStart, date >= start {
            var t = date.timeIntervalSince(start)
            if once { t = min(t, Self.loop) }
            let e = t.truncatingRemainder(dividingBy: Self.loop)
            let quiet = quietUntil.map { date < $0 } ?? false
            var s = TL.scene(once && t >= Self.loop ? Self.loop : e, card: layout.card, undo: undoCenter, quiet: quiet)
            if reduceMotion { s.cardScale = 1; s.cardLift = 0 }
            return s
        }
        var s = TornPhotoDeleteScene()
        if let c = committed {
            let f = date.timeIntervalSince(c.at)
            var tr = c.tear
            if reduceMotion {
                s.cardAlpha = 1 - E.smoothstep(0, 0.3, f)
                tr.fall = nil
            } else {
                tr.fall = f
                if f >= 1.2 { s.cardAlpha = 0 }
            }
            if s.cardAlpha > 0 { s.tear = tr }
            s.toast = reduceMotion ? E.smoothstep(0.35, 0.65, f) : E.outBack((f - 0.35) / 0.4)
            s.empty = E.smoothstep(0.55, 0.95, f)
            s.hint = 1 - E.smoothstep(0, 0.3, f)
            return s
        }
        if let t = tear, t.length > 0 {
            s.tear = t
        } else if let h = healing {
            var t = h.tear
            t.open = reduceMotion ? 0 : E.heal(t.open, date.timeIntervalSince(h.from))
            if t.open > 0 { s.tear = t }
        }
        return s
    }

    // MARK: Touch

    private func drag(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { value in
                lastLayout = layout
                let now = Date.now
                if gesture == nil { begin(at: value.startLocation, now: now, layout: layout) }
                guard gesture == .tear, let edge = dragEdge else { return }
                let p = CGPoint(x: value.location.x - layout.card.minX, y: value.location.y - layout.card.minY)
                var t = tear ?? Tear(start: edge, angle: 0, length: 0, open: 0, seed: 101)
                if tear == nil {
                    // Wait until the finger has moved into the card before choosing the direction.
                    let dx = p.x - edge.x, dy = p.y - edge.y
                    guard hypot(dx, dy) > 10 else { return }
                    t.angle = atan2(dy, dx) * 180 / .pi
                    t.seed = 101 + Int(now.timeIntervalSince1970.truncatingRemainder(dividingBy: 97))
                    let s = Tear.span(start: edge, angle: t.angle, card: layout.card.size)
                    guard s > 40 else { return }
                    span = s
                }
                let u = t.u
                t.length = min(span * 1.06, max(0, (p.x - edge.x) * u.dx + (p.y - edge.y) * u.dy))
                t.open = Tear.opening(t.length)
                let k = Int(t.length / 28)
                if k > ticks { light.impactOccurred(intensity: 0.5) }
                ticks = k
                tear = t
                if t.length >= span { commit(now: now) }
            }
            .onEnded { value in
                let now = Date.now
                defer { gesture = nil; dragEdge = nil }
                switch gesture {
                case .undo:
                    if layout.undo.insetBy(dx: -8, dy: -8).contains(value.location) { undo(now: now) } else { scheduleResume() }
                case .tear:
                    if let t = tear, t.length > 0 {
                        if t.length >= span * 0.7 { commit(now: now) } else {
                            healing = (t, now)
                            tear = nil
                        }
                    }
                    scheduleResume()
                default:
                    scheduleResume()
                }
            }
    }

    private func begin(at point: CGPoint, now: Date, layout: Layout) {
        resume?.cancel()
        active = true
        let current = scene(at: now, layout: layout)
        if current.toast > 0.5, layout.undo.insetBy(dx: -8, dy: -8).contains(point) {
            gesture = .undo
            return
        }
        // Only a print at rest (or in a short tear) can be torn.
        let resting = current.cardAlpha >= 1 && current.cardScale >= 0.999 && current.tear?.fall == nil && committed == nil
        guard resting, layout.card.insetBy(dx: -30, dy: -30).contains(point) else {
            gesture = .none
            return
        }
        gesture = .tear
        autoStart = nil
        quietUntil = nil
        once = false
        if let t = current.tear { healing = (t, now) } else { healing = nil }
        tear = nil
        ticks = 0
        // The nearest point on the card's edge.
        let c = layout.card
        let p = CGPoint(x: min(max(point.x, c.minX), c.maxX) - c.minX, y: min(max(point.y, c.minY), c.maxY) - c.minY)
        let d = [p.x, c.width - p.x, p.y, c.height - p.y]
        let i = d.firstIndex(of: d.min()!)!
        dragEdge = [CGPoint(x: 0, y: p.y), CGPoint(x: c.width, y: p.y), CGPoint(x: p.x, y: 0), CGPoint(x: p.x, y: c.height)][i]
    }

    private func commit(now: Date) {
        guard var t = tear, gesture == .tear else { return }
        gesture = .none
        t.length = span * 1.06
        t.open = Tear.opening(t.length)
        committed = (t, now)
        tear = nil
        healing = nil
        heavy.impactOccurred()
    }

    private func undo(now: Date) {
        light.impactOccurred(intensity: 0.8)
        committed = nil
        tear = nil
        healing = nil
        // Rejoin the loop where its own Undo is pressed; without autoPlay, play that part once.
        autoStart = now.addingTimeInterval(-TL.undoPress)
        quietUntil = now.addingTimeInterval(Self.loop)
        once = !autoPlay || reduceMotion
        scheduleIdle()
    }

    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            if committed != nil {
                committed = nil
                autoStart = now.addingTimeInterval(-TL.toastOut)
                quietUntil = now.addingTimeInterval(Self.loop)
                once = !autoPlay || reduceMotion
            } else if autoPlay && !reduceMotion {
                tear = nil
                healing = nil
                autoStart = now
            }
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            active = false
        }
    }

    private func scheduleIdle() {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            active = false
        }
    }

    // MARK: Drawing

    private func header(_ layout: Layout) -> some View {
        let grey = Color(red: 0.55, green: 0.55, blue: 0.58)
        return ZStack {
            VStack(spacing: 2) {
                Text("Selected Day")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(red: 0.33, green: 0.33, blue: 0.36))
                Text("Thursday, 2 July")
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(grey)
            }
            TornPhotoDeleteGlyph.trash
                .stroke(grey, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 22, height: 22)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 24)
        }
        .frame(width: layout.size.width, height: 44)
        .offset(y: layout.insets.top + 6)
        .allowsHitTesting(false)
    }

    private func emptyNote(_ layout: Layout, _ alpha: Double) -> some View {
        VStack(spacing: 10) {
            TornPhotoDeleteGlyph.photo
                .stroke(Color(red: 0.70, green: 0.70, blue: 0.73), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 30, height: 30)
            Text("Nothing kept for this day")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color(red: 0.45, green: 0.45, blue: 0.48))
            Text("Take today's photo with the camera.")
                .font(.system(size: 13))
                .foregroundStyle(Color(red: 0.60, green: 0.60, blue: 0.63))
        }
        .frame(width: layout.size.width)
        .offset(y: layout.card.midY - 50)
        .opacity(alpha)
        .allowsHitTesting(false)
    }

    private func card(_ layout: Layout, _ s: TornPhotoDeleteScene) -> some View {
        let image = TornPhotoDeleteAssets.photo
        return Canvas { ctx, _ in
            guard s.cardAlpha > 0 else { return }
            let c = layout.card
            ctx.opacity = s.cardAlpha
            ctx.translateBy(x: c.midX, y: c.midY - s.cardLift)
            ctx.scaleBy(x: s.cardScale, y: s.cardScale)
            ctx.translateBy(x: -c.width / 2, y: -c.height / 2)
            let resolved = ctx.resolve(image)
            let date = ctx.resolve(Text("02/07/2026")
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(Color(red: 0.16, green: 0.15, blue: 0.14)))

            // Shadows of every piece first, then the pieces, so no piece's shadow falls on its neighbour.
            func shadow(_ transform: CGAffineTransform, clip: Path?) {
                ctx.drawLayer { outer in
                    outer.concatenate(transform)
                    outer.addFilter(.shadow(color: .black.opacity(0.16), radius: 14, x: 0, y: 8))
                    outer.drawLayer { g in
                        if let clip { g.clip(to: clip) }
                        g.fill(Path(roundedRect: CGRect(origin: .zero, size: c.size), cornerRadius: 3),
                               with: .color(Color(red: 0.95, green: 0.91, blue: 0.83)))
                    }
                }
            }
            func piece(_ transform: CGAffineTransform, clip: Path?, core: Path?, coreAlpha: Double = 1) {
                ctx.drawLayer { g in
                    g.concatenate(transform)
                    if let clip { g.clip(to: clip) }
                    TornPhotoDelete.drawCard(&g, size: c.size, photo: layout.photo, image: resolved, date: date)
                    if let core {
                        g.clip(to: Path(roundedRect: CGRect(origin: .zero, size: c.size), cornerRadius: 3))
                        g.fill(core, with: .color(Color(red: 0.995, green: 0.985, blue: 0.965).opacity(coreAlpha)))
                    }
                }
            }

            guard let t = s.tear, t.length > 0 else {
                shadow(.identity, clip: nil)
                piece(.identity, clip: nil, core: nil)
                return
            }
            let cut = t.cut()
            let coreAlpha = t.fall != nil ? 1 : E.smoothstep(0, 0.02, t.open)
            let sides = [1.0, -1.0]
            if t.fall == nil { shadow(.identity, clip: t.rest()) }
            for side in sides { shadow(t.transform(side, card: c.size), clip: t.region(side, cut: cut)) }
            if t.fall == nil { piece(.identity, clip: t.rest(), core: nil) }
            for side in sides {
                piece(t.transform(side, card: c.size), clip: t.region(side, cut: cut),
                      core: coreAlpha > 0.01 ? t.core(side, cut: cut) : nil, coreAlpha: coreAlpha)
            }
        }
        .frame(width: layout.size.width, height: layout.size.height)
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel("Photo of 2 July 2026: a road through red rocks")
    }

    static func drawCard(_ g: inout GraphicsContext, size: CGSize, photo: CGRect, image: GraphicsContext.ResolvedImage,
                         date: GraphicsContext.ResolvedText) {
        let paper = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 3)
        g.fill(paper, with: .linearGradient(
            Gradient(colors: [Color(red: 0.969, green: 0.933, blue: 0.867), Color(red: 0.937, green: 0.890, blue: 0.800)]),
            startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)))
        // The photo, filled into its 5:6 window.
        g.drawLayer { p in
            p.clip(to: Path(photo))
            let isz = image.size
            let scale = max(photo.width / max(isz.width, 1), photo.height / max(isz.height, 1))
            let w = isz.width * scale, h = isz.height * scale
            p.draw(image, in: CGRect(x: photo.midX - w / 2, y: photo.midY - h / 2, width: w, height: h))
            // A faint glare across the gloss.
            p.fill(Path(photo), with: .linearGradient(
                Gradient(stops: [.init(color: .white.opacity(0), location: 0.25), .init(color: .white.opacity(0.13), location: 0.42),
                                 .init(color: .white.opacity(0), location: 0.6)]),
                startPoint: CGPoint(x: photo.minX, y: photo.minY), endPoint: CGPoint(x: photo.maxX, y: photo.maxY)))
        }
        g.stroke(Path(photo.insetBy(dx: 0.25, dy: 0.25)), with: .color(.black.opacity(0.12)), lineWidth: 0.5)
        g.draw(date, at: CGPoint(x: size.width / 2, y: photo.maxY + 36), anchor: .center)
    }

    private func toast(_ layout: Layout, _ s: TornPhotoDeleteScene) -> some View {
        let r = layout.toast
        return HStack(spacing: 0) {
            TornPhotoDeleteGlyph.trash
                .stroke(.white.opacity(0.7), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 16, height: 16)
                .padding(.leading, 18)
            Text("Photo deleted")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.white.opacity(0.92))
                .padding(.leading, 10)
            Spacer(minLength: 8)
            Rectangle().fill(.white.opacity(0.14)).frame(width: 1, height: 22)
            Text("Undo")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color(red: 1.0, green: 0.62, blue: 0.70))
                .frame(width: 83, height: r.height)
                .background(Color.white.opacity(0.14 * s.undoPress))
                .scaleEffect(1 - 0.06 * s.undoPress)
        }
        .frame(width: r.width, height: r.height)
        .background(Color(red: 0.11, green: 0.11, blue: 0.12), in: Capsule())
        .clipShape(Capsule())
        .shadow(color: .black.opacity(0.18), radius: 16, y: 8)
        .offset(x: r.minX, y: r.minY + (reduceMotion ? 0 : (1 - s.toast) * 90))
        .opacity(min(1, max(0, s.toast) * 1.5))
        .allowsHitTesting(false)
    }

    private func finger(_ d: (p: CGPoint, alpha: Double, press: Double)) -> some View {
        Circle()
            .fill(Color(red: 0.35, green: 0.35, blue: 0.38).opacity(0.28))
            .overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1.5))
            .frame(width: 40, height: 40)
            .scaleEffect(1.25 - 0.25 * d.press)
            .offset(x: d.p.x - 20, y: d.p.y - 20)
            .opacity(d.alpha)
            .allowsHitTesting(false)
    }
}

/// The page: a pale ground, the pink glow and the camera button, the handle.
struct TornPhotoDeleteBackdrop: View {
    var layout: TornPhotoDelete.Layout

    var body: some View {
        let size = layout.size
        ZStack(alignment: .topLeading) {
            Canvas { ctx, sz in
                ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(Color(red: 0.937, green: 0.937, blue: 0.945)))
                ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .radialGradient(
                    Gradient(colors: [.white.opacity(0.7), .white.opacity(0)]),
                    center: CGPoint(x: sz.width / 2, y: sz.height * 0.42), startRadius: 0, endRadius: sz.width * 0.8))
                ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .radialGradient(
                    Gradient(colors: [Color(red: 1.0, green: 0.55, blue: 0.66).opacity(0.42), Color(red: 1.0, green: 0.55, blue: 0.66).opacity(0)]),
                    center: layout.fab, startRadius: 0, endRadius: 120))
                // The handle.
                let hy = sz.height - max(layout.insets.bottom, 20) - 14
                var chevron = Path()
                chevron.move(to: CGPoint(x: sz.width / 2 - 9, y: hy - 3))
                chevron.addLine(to: CGPoint(x: sz.width / 2, y: hy + 2))
                chevron.addLine(to: CGPoint(x: sz.width / 2 + 9, y: hy - 3))
                ctx.stroke(chevron, with: .color(Color(red: 0.70, green: 0.70, blue: 0.73)),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
            .frame(width: size.width, height: size.height)

            ZStack {
                Circle().fill(LinearGradient(colors: [Color(red: 1.0, green: 0.56, blue: 0.66), Color(red: 1.0, green: 0.36, blue: 0.51)],
                                             startPoint: .top, endPoint: .bottom))
                TornPhotoDeleteGlyph.camera
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 24, height: 24)
            }
            .frame(width: 56, height: 56)
            .shadow(color: Color(red: 1.0, green: 0.36, blue: 0.51).opacity(0.35), radius: 12, y: 6)
            .offset(x: layout.fab.x - 28, y: layout.fab.y - 28)
        }
        .allowsHitTesting(false)
    }
}

/// Line glyphs on a 24 × 24 grid, scaled to the frame.
enum TornPhotoDeleteGlyph {
    static let trash = Glyph(kind: .trash)
    static let camera = Glyph(kind: .camera)
    static let photo = Glyph(kind: .photo)

    struct Glyph: Shape {
        enum Kind { case trash, camera, photo }
        var kind: Kind

        func path(in rect: CGRect) -> Path {
            var p = Path()
            switch kind {
            case .trash:
                p.move(to: CGPoint(x: 4, y: 6.5)); p.addLine(to: CGPoint(x: 20, y: 6.5))
                p.move(to: CGPoint(x: 9.5, y: 6.5)); p.addLine(to: CGPoint(x: 9.5, y: 4)); p.addLine(to: CGPoint(x: 14.5, y: 4)); p.addLine(to: CGPoint(x: 14.5, y: 6.5))
                p.move(to: CGPoint(x: 6, y: 6.5)); p.addLine(to: CGPoint(x: 7, y: 20)); p.addLine(to: CGPoint(x: 17, y: 20)); p.addLine(to: CGPoint(x: 18, y: 6.5))
                p.move(to: CGPoint(x: 10.2, y: 10)); p.addLine(to: CGPoint(x: 10.5, y: 16.5))
                p.move(to: CGPoint(x: 13.8, y: 10)); p.addLine(to: CGPoint(x: 13.5, y: 16.5))
            case .camera:
                p.move(to: CGPoint(x: 3, y: 8.5)); p.addLine(to: CGPoint(x: 3, y: 18.5)); p.addLine(to: CGPoint(x: 21, y: 18.5)); p.addLine(to: CGPoint(x: 21, y: 8.5))
                p.addLine(to: CGPoint(x: 16.5, y: 8.5)); p.addLine(to: CGPoint(x: 15, y: 5.5)); p.addLine(to: CGPoint(x: 9, y: 5.5)); p.addLine(to: CGPoint(x: 7.5, y: 8.5)); p.closeSubpath()
                p.addEllipse(in: CGRect(x: 8.5, y: 9.5, width: 7, height: 7))
            case .photo:
                p.addRoundedRect(in: CGRect(x: 3, y: 4, width: 18, height: 16), cornerSize: CGSize(width: 2.5, height: 2.5))
                p.move(to: CGPoint(x: 3.5, y: 17)); p.addLine(to: CGPoint(x: 9, y: 11.5)); p.addLine(to: CGPoint(x: 13.5, y: 16)); p.addLine(to: CGPoint(x: 16, y: 13.5)); p.addLine(to: CGPoint(x: 20.5, y: 18))
                p.addEllipse(in: CGRect(x: 14, y: 7, width: 3, height: 3))
            }
            let s = min(rect.width, rect.height) / 24
            return p.applying(CGAffineTransform(scaleX: s, y: s).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
        }
    }
}

enum TornPhotoDeleteAssets {
    static let photo: Image = {
        guard let url = Bundle.main.url(forResource: "TornPhotoDelete-road", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return Image(systemName: "photo") }
        return Image(uiImage: image)
    }()
}
