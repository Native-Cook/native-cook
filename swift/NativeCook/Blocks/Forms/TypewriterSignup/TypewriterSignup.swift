import SwiftUI
import UIKit

/*
 Typewriter Sign Up — a writing app's sign-up where the form is typed onto a sheet in a desk
 typewriter. Each keystroke swings a typebar up out of the basket to the platen (70 ms up,
 110 ms back), the carriage steps one glyph left on a stiff spring, and the glyph lands with a
 little ink spread and its own baseline jitter and ink density. Moving to the next field is a
 carriage return with the margin bell; "Pull the sheet" (or dragging the sheet up) pulls it out
 of the carriage, and a blank one feeds back in.

 One Canvas draws the desk, header, sheet, carriage, basket and bell from pre-rendered images;
 the three fields and the CTA are native views over it. Per frame only transforms, glyph images
 and two small paths change. The script is in TypewriterSignupMotion.swift.
*/
struct TypewriterSignup: View {
    /// Types the three fields, pulls the sheet and feeds a blank one, every `loop` seconds.
    var autoPlay = false

    static let loop = 10.0

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            TypewriterSignupStage(autoPlay: autoPlay, size: size, insetTop: insets.top, insetBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .ignoresSafeArea(.keyboard)
        .background(TypewriterSignupArt.desk[1].ignoresSafeArea())
    }
}

private struct TypewriterSignupStage: View {
    let autoPlay: Bool
    let size: CGSize
    let insetTop: Double
    let insetBottom: Double

    private typealias A = TypewriterSignupArt
    private typealias M = TypewriterSignupMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var model = TypewriterSignupModel()
    @State private var glyphs = TypewriterSignupGlyphs()
    /// True while the user state is live outside autoPlay, so the timeline runs.
    @State private var awake = false
    @FocusState private var focus: Int?

    private static let fields: [(label: String, placeholder: String)] = [
        ("Name", "Your pen name"),
        ("Email", "you@inkwell.co"),
        ("Password", "At least 8 characters"),
    ]

    var body: some View {
        let g = TypewriterSignupGeo(w: size.width, h: size.height, insetTop: insetTop, insetBottom: insetBottom)
        let auto = autoPlay && !reduced
        model.configure(auto: auto, reduced: reduced, autoPlay: autoPlay, onSleep: { awake = false })
        return TimelineView(.animation(paused: !(auto || awake))) { timeline in
            let pose = model.frame(timeline.date)
            ZStack(alignment: .topLeading) {
                canvas(g: g, pose: pose)
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(sheetDrag(g))
                    .accessibilityElement()
                    .accessibilityLabel("A sheet in a typewriter. Your entries are typed onto it. Drag the sheet up to pull it out.")

                form(g: g, pose: pose)
                    .padding(.horizontal, 24)
                    .padding(.top, g.formTop)
                    .frame(width: size.width, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
        .onChange(of: focus) { _, f in
            wake()
            if let f { model.onFocus(f) } else { model.onBlur() }
        }
    }


    private func wake() {
        if !awake { awake = true }
    }

    // MARK: Drawing

    private func canvas(g: TypewriterSignupGeo, pose p: TypewriterSignupPose) -> some View {
        let lines = model.shownLines
        let inkOpacity = model.inkOpacity
        let glyphs = glyphs
        // Rendered synchronously on the first pass (and cached per size), so the opening frame
        // is already complete.
        let art = TypewriterSignupImages.cached(g: g, displayScale: displayScale)
        return Canvas { c, _ in
            c.draw(art.background, in: CGRect(x: 0, y: 0, width: g.screenW, height: g.screenH))
            c.scaleBy(x: g.scale, y: g.scale)

            let paperLeft = g.cx - A.textX - p.col * A.adv - A.adv / 2
            let paperTop = g.sy - A.topPad - p.line * A.lineH - p.pull

            // The sheet, clipped where it goes into the platen.
            do {
                var s = c
                s.clip(to: Path(CGRect(x: -200, y: -3000, width: g.screenW + 400, height: 3000 + g.platenY - 4)))
                let f = p.flex
                s.translateBy(x: paperLeft + A.paperW / 2, y: paperTop + A.paperH)
                s.rotate(by: .radians(-1.6 * f * .pi / 180))
                s.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.03 * f, d: 1, tx: 0, ty: 0))
                s.scaleBy(x: 1 - 0.025 * abs(f), y: 1)
                s.translateBy(x: -A.paperW / 2, y: -A.paperH)
                s.draw(art.paper, in: art.paperRect)
                drawInk(&s, lines: lines, pose: p, opacity: inkOpacity, glyphs: glyphs)
            }

            // Carriage.
            do {
                var k = c
                k.translateBy(x: paperLeft, y: 0)
                k.draw(art.carriage, in: art.carriageRect)
            }
            c.draw(art.body, in: art.bodyRect)

            // The fan of typebars at rest.
            var restBars = Path()
            var restHeads = Path()
            for i in 0..<M.bars {
                if i == p.bar && p.strike > 0.001 { continue }
                let (a, b) = g.barTip(i, 0)
                restBars.move(to: a)
                restBars.addLine(to: b)
                restHeads.addEllipse(in: CGRect(x: b.x - 2.6, y: b.y - 2.6, width: 5.2, height: 5.2))
            }
            c.stroke(restBars, with: .color(A.hex(0x8E959C)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
            c.fill(restHeads, with: .color(A.hex(0xC3C9CE)))
            c.draw(art.deck, in: art.deckRect)

            // The striking bar, over the deck.
            if p.strike > 0.001 {
                let (a, b) = g.barTip(p.bar, p.strike)
                var bar = Path()
                bar.move(to: a)
                bar.addLine(to: b)
                c.stroke(bar, with: .color(A.hex(0xD5DADE)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                let s = 2.6 + p.strike * 1.6
                c.fill(Path(roundedRect: CGRect(x: b.x - s, y: b.y - s * 0.8, width: s * 2, height: s * 1.6), cornerRadius: 1), with: .color(A.hex(0x2F3336)))
            }

            // The margin bell and its glow ring.
            let bx = g.w - 52
            let by = g.deckY + 24
            do {
                var b = c
                b.translateBy(x: bx, y: by + 8)
                b.rotate(by: .degrees(p.bell * 14))
                b.translateBy(x: -bx, y: -(by + 8))
                drawBell(&b, x: bx, y: by)
            }
            if p.bellGlow > 0.001 {
                let r = 12 + (1 - p.bellGlow) * 22
                c.stroke(Path(ellipseIn: CGRect(x: bx - r, y: by - r, width: r * 2, height: r * 2)), with: .color(A.brass.opacity(p.bellGlow * 0.55)), lineWidth: 1.5)
            }
        }
    }

    /// Glyphs of the three lines in three ink densities, and the newest one's bleed.
    private func drawInk(_ c: inout GraphicsContext, lines: [[Character]], pose p: TypewriterSignupPose, opacity: Double, glyphs: TypewriterSignupGlyphs) {
        guard opacity > 0.001 else { return }
        let inks: [(UIColor, Double, Double)] = [
            (UIColor(red: 43 / 255, green: 36 / 255, blue: 32 / 255, alpha: 0.72), 0, 0),
            (UIColor(red: 43 / 255, green: 36 / 255, blue: 32 / 255, alpha: 0.86), 0, 0),
            (UIColor(red: 38 / 255, green: 31 / 255, blue: 28 / 255, alpha: 0.97), 0.3, 0.2),
        ]
        let pad = TypewriterSignupGlyphs.pad
        let gs = glyphs.size
        c.opacity = opacity
        for l in 0..<3 {
            let n = min(p.n[l], TypewriterSignupModel.maxCols, lines[l].count)
            for k in 0..<n {
                let lf = Double(l), kf = Double(k)
                let x = A.textX + kf * A.adv + (M.hash(lf * 97 + kf * 3 + 1) - 0.5) * 0.8
                let y = A.topPad + lf * A.lineH + (M.hash(lf * 53 + kf * 7 + 2) - 0.5) * 1.4
                let ink = Int(floor(M.hash(lf * 11 + kf * 5 + 3) * 3))
                let (color, dx, dy) = inks[ink]
                let img = glyphs.image(lines[l][k], ink: ink, color: color)
                c.draw(img, in: CGRect(x: x + dx - pad, y: y + dy - pad - glyphs.ascender, width: gs.width, height: gs.height))
            }
        }
        // The newest glyph, drawn again blurred while its ink spreads (0.3 s).
        if p.inkAge >= 0 && p.inkAge <= 0.3 {
            let l = Int(min(2, max(0, p.line.rounded())))
            let k = min(p.n[l], TypewriterSignupModel.maxCols) - 1
            if k >= 0 && k < lines[l].count {
                let img = glyphs.image(lines[l][k], ink: 9, color: A.inkUI)
                let x = A.textX + Double(k) * A.adv
                let y = A.topPad + Double(l) * A.lineH
                c.drawLayer { f in
                    f.opacity = 0.55 * (1 - min(1, p.inkAge / 0.3))
                    f.addFilter(.blur(radius: 1.6))
                    f.draw(img, in: CGRect(x: x - pad, y: y - pad - glyphs.ascender, width: gs.width, height: gs.height))
                }
            }
        }
    }

    /// The margin bell: a small chrome dome on a post.
    private func drawBell(_ c: inout GraphicsContext, x: Double, y: Double) {
        var dome = Path()
        dome.move(to: CGPoint(x: x - 11, y: y + 8))
        dome.addCurve(to: CGPoint(x: x, y: y - 11), control1: CGPoint(x: x - 11, y: y - 6), control2: CGPoint(x: x - 5, y: y - 11))
        dome.addCurve(to: CGPoint(x: x + 11, y: y + 8), control1: CGPoint(x: x + 5, y: y - 11), control2: CGPoint(x: x + 11, y: y - 6))
        dome.closeSubpath()
        c.fill(Path(roundedRect: CGRect(x: x - 13, y: y + 7, width: 26, height: 4), cornerRadius: 2), with: .color(A.hex(0x3D4247)))
        c.fill(dome, with: .color(A.hex(0x9AA1A8)))
        c.stroke(dome, with: .color(.white.opacity(0.35)), lineWidth: 1)
        c.fill(Path(roundedRect: CGRect(x: x - 6, y: y - 8, width: 4, height: 10), cornerRadius: 2), with: .color(.white.opacity(0.7)))
        c.fill(Path(roundedRect: CGRect(x: x - 1.5, y: y - 15, width: 3, height: 5), cornerRadius: 1.5), with: .color(A.hex(0xC3C9CE)))
    }

    // MARK: Form

    private func form(g: TypewriterSignupGeo, pose p: TypewriterSignupPose) -> some View {
        let fieldH = g.compact ? 52.0 : 56.0
        return VStack(spacing: 16) {
            VStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { l in
                    field(l, height: fieldH)
                }
            }
            .opacity(model.mode == 0 ? p.fields : 1)

            cta(height: g.compact ? 48 : 52, pose: p)
        }
    }

    private func field(_ l: Int, height: Double) -> some View {
        let f = Self.fields[l]
        let text = Binding<String>(
            get: { model.fieldText(l) },
            set: { v in
                wake()
                model.onChange(l, v)
            }
        )
        let prompt = Text(f.placeholder).foregroundStyle(A.rgba(241, 232, 216, 0.28))
        return VStack(alignment: .leading, spacing: 2) {
            Text(f.label.uppercased())
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(A.rgba(207, 174, 109, 0.9))
            Group {
                if l == 2 {
                    SecureField(f.label, text: text, prompt: prompt)
                        .textContentType(.newPassword)
                } else {
                    TextField(f.label, text: text, prompt: prompt)
                        .textContentType(l == 0 ? .name : .emailAddress)
                        .keyboardType(l == 0 ? .default : .emailAddress)
                        .textInputAutocapitalization(l == 0 ? .words : .never)
                }
            }
            .autocorrectionDisabled()
            .submitLabel(l == 2 ? .done : .next)
            .onSubmit { focus = l < 2 ? l + 1 : nil }
            .focused($focus, equals: l)
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(A.cream)
            .tint(A.brass)
            .padding(.vertical, 4)
            .accessibilityLabel(f.label)
        }
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .bottomLeading)
        .overlay(alignment: .bottom) {
            Rectangle().fill(A.rgba(241, 232, 216, 0.22)).frame(height: 1 / displayScale)
        }
    }

    private func cta(height: Double, pose p: TypewriterSignupPose) -> some View {
        ZStack {
            LinearGradient(
                stops: [
                    .init(color: A.hex(0xF7EFDF), location: 0),
                    .init(color: A.hex(0xEAD9BB), location: 0.55),
                    .init(color: A.hex(0xD9C39B), location: 1),
                ],
                startPoint: .top, endPoint: .bottom)
            HStack(spacing: 8) {
                Image(systemName: "arrow.up.doc").font(.system(size: 17))
                Text("Pull the sheet").font(.system(size: 17, weight: .semibold))
            }
            .opacity(1 - p.done)
            .offset(y: -6 * p.done)
            HStack(spacing: 8) {
                Image(systemName: "checkmark").font(.system(size: 16, weight: .semibold))
                Text("Draft started").font(.system(size: 17, weight: .semibold))
            }
            .opacity(p.done)
            .offset(y: 6 * (1 - p.done))
        }
        .foregroundStyle(A.ink)
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(Capsule())
        .overlay(alignment: .top) {
            // Inset top highlight.
            Capsule().strokeBorder(
                LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
        .shadow(color: .black.opacity(0.35), radius: 14, y: 10)
        .scaleEffect(1 - 0.03 * p.press)
        .contentShape(Capsule())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    wake()
                    model.pressIn()
                }
                .onEnded { v in
                    model.pressOut()
                    if abs(v.translation.width) < 40 && abs(v.translation.height) < 40 { tapCta() }
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Pull the sheet")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { tapCta() }
    }

    private func tapCta() {
        wake()
        focus = nil
        model.onCta()
    }

    private func sheetDrag(_ g: TypewriterSignupGeo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !model.dragStarted {
                    model.dragStarted = true
                    if model.dragBegin(at: v.startLocation, g: g) { wake() }
                }
                model.dragUpdate(translation: v.translation.height, velocity: v.velocity.height, g: g)
            }
            .onEnded { v in
                model.dragEnd(translation: v.translation.height, velocity: v.velocity.height, g: g)
                model.dragStarted = false
            }
    }
}

/// The user state and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class TypewriterSignupModel {
    /// Most glyphs a line of the sheet takes.
    static let maxCols = 18
    /// Seconds after the last touch before the screen eases back and the loop restarts.
    private static let idle = 2.0
    private static let returnLen = 0.85
    private static let ease = TypewriterSignupValue.Curve.bezier(TypewriterSignupBezier(x1: 0.77, y1: 0, x2: 0.175, y2: 1))
    private static let outQuint = TypewriterSignupValue.Curve.bezier(TypewriterSignupBezier(x1: 0.23, y1: 1, x2: 0.32, y2: 1))
    private static let pullCurve = TypewriterSignupValue.Curve.bezier(TypewriterSignupBezier(x1: 0.33, y1: 0, x2: 0.2, y2: 1))
    private typealias M = TypewriterSignupMotion

    private var auto = false
    private var reduced = false
    private var autoPlay = false
    private var onSleep: () -> Void = {}

    private var last: Date?
    private(set) var now = 0.0
    private var clock = 0.0
    /// 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest.
    private(set) var mode = 0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var focused = -1
    private var dragging = false
    var dragStarted = false

    private var uLines: [[Character]] = [[], [], []]
    private var uN = [0, 0, 0]
    private var uLine = TypewriterSignupValue(0)
    private var uCol = TypewriterSignupValue(0)
    private var uStrikeAt = -9.0
    private var uBar = 4
    private var uBellAt = -9.0
    private var uPull = TypewriterSignupValue(0)
    private var uFlex = TypewriterSignupValue(0)
    private var uDone = TypewriterSignupValue(0)
    private var uInk = TypewriterSignupValue(1)
    private var uPress = TypewriterSignupValue(0)
    /// What the fields hold in the user state.
    private var fieldValues = ["", "", ""]
    private var pose = TypewriterSignupPose()

    private let tick = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    func configure(auto: Bool, reduced: Bool, autoPlay: Bool, onSleep: @escaping () -> Void) {
        if auto != self.auto { clock = 0 }
        self.auto = auto
        self.reduced = reduced
        self.autoPlay = autoPlay
        self.onSleep = onSleep
        if mode == 0 { pose = restingPose() }
    }

    private func restingPose() -> TypewriterSignupPose {
        auto ? M.autoPose(clock) : (reduced && autoPlay ? M.filledPose() : M.restPose())
    }

    /// The lines the sheet shows: the script in autoPlay, what was typed otherwise.
    var shownLines: [[Character]] { mode == 0 ? M.script : uLines }
    var inkOpacity: Double { mode == 0 ? 1 : uInk.value }

    func fieldText(_ l: Int) -> String {
        if mode == 0 {
            let n = pose.n[l]
            return l == 2 ? String(repeating: "x", count: n) : String(M.script[l].prefix(n))
        }
        return fieldValues[l]
    }

    // MARK: Frame

    func frame(_ date: Date) -> TypewriterSignupPose {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.064)
        guard dt > 0 else { return pose }
        now += dt
        if mode == 0 {
            if auto { clock = (clock + dt).truncatingRemainder(dividingBy: TypewriterSignup.loop) }
            pose = restingPose()
            return pose
        }
        var done: [() -> Void] = []
        for c in [uLine.step(now: now, dt: dt), uCol.step(now: now, dt: dt), uPull.step(now: now, dt: dt),
                  uFlex.step(now: now, dt: dt), uDone.step(now: now, dt: dt), uInk.step(now: now, dt: dt),
                  uPress.step(now: now, dt: dt)] {
            if let c { done.append(c) }
        }
        done.forEach { $0() }
        if mode == 1 {
            if focused < 0 && !dragging && now - lastTouch > Self.idle {
                mode = 2
                returnAt = now
                let d = reduced ? 0 : 0.8
                uInk.timing(to: 0, duration: reduced ? 0 : 0.35, now: now)
                uCol.timing(to: 0, duration: d, curve: Self.ease, now: now)
                uLine.timing(to: 0, duration: d, curve: Self.ease, now: now)
                uPull.timing(to: 0, duration: d, curve: Self.ease, now: now)
                uDone.timing(to: 0, duration: 0.4, now: now)
            }
        } else if now - returnAt > Self.returnLen {
            uN = [0, 0, 0]
            uInk.set(1)
            clock = 0
            mode = 0
            fieldValues = ["", "", ""]
            pose = restingPose()
            if !auto {
                let sleep = onSleep
                DispatchQueue.main.async { sleep() }
            }
            return pose
        }
        pose = userPose()
        return pose
    }

    private func userPose() -> TypewriterSignupPose {
        var p = M.restPose()
        p.n = uN
        p.line = uLine.value
        p.col = uCol.value
        let tau = now - uStrikeAt
        p.strike = reduced ? 0 : M.strikeCurve(tau)
        p.inkAge = tau
        p.bar = uBar
        let b = M.bellCurve(now - uBellAt)
        p.bell = reduced ? 0 : b.0
        p.bellGlow = b.1
        p.pull = uPull.value
        p.flex = uFlex.value
        p.done = uDone.value
        p.press = uPress.value
        return p
    }

    // MARK: Taking over

    /// Freezes the current loop frame into the user state, so taking over never jumps.
    private func enterUser() {
        if mode == 1 { return }
        let p = mode == 0 ? restingPose() : M.restPose()
        var vals = [String(M.script[0].prefix(p.n[0])), String(M.script[1].prefix(p.n[1])), String(repeating: "x", count: p.n[2])]
        if p.pull >= M.sheetAway - 1 { vals = ["", "", ""] }
        uLines = (0..<3).map { Array(M.script[$0].prefix(vals[$0].count)) }
        uN = vals.map(\.count)
        uLine.set(p.line)
        uCol.set(p.col)
        uPull.set(p.pull)
        uFlex.set(0)
        uDone.set(p.done)
        uInk.set(1)
        uBellAt = -9
        uStrikeAt = -9
        fieldValues = vals
        mode = 1
        lastTouch = now
        if p.pull > 1 {
            uPull.timing(to: 0, duration: reduced ? 0 : 0.7, curve: Self.outQuint, now: now)
        }
    }

    private func touch() { lastTouch = now }

    func onFocus(_ l: Int) {
        enterUser()
        let prev = Int(uLine.value.rounded())
        focused = l
        touch()
        let len = min(fieldValues[l].count, Self.maxCols)
        let d = reduced ? 0 : 0.45
        uLine.timing(to: Double(l), duration: d, curve: Self.ease, now: now)
        uCol.timing(to: Double(len), duration: d, curve: Self.ease, now: now)
        if l != prev { uBellAt = now }
    }

    func onBlur() {
        focused = -1
        touch()
    }

    func onChange(_ l: Int, _ raw: String) {
        enterUser()
        let v = String(raw.prefix(l == 1 ? 40 : 24))
        let old = fieldValues[l]
        fieldValues[l] = v
        let shown = l == 2 ? String(repeating: "•", count: v.count) : v
        uLines[l] = Array(shown)
        uN[l] = v.count
        touch()
        let col = min(v.count, Self.maxCols)
        if v.count > old.count && v.count <= Self.maxCols {
            uStrikeAt = now
            uBar = M.barFor(l, v.count - 1)
            tick.selectionChanged()
            if v.count == Self.maxCols - 3 { uBellAt = now }
        }
        if reduced {
            uCol.set(Double(col))
        } else {
            uCol.spring(to: Double(col), damping: 14, stiffness: 700, mass: 0.5)
        }
    }

    func pressIn() {
        uPress.timing(to: 1, duration: 0.12, now: now)
    }

    func pressOut() {
        uPress.timing(to: 0, duration: 0.16, now: now)
    }

    func onCta() {
        enterUser()
        touch()
        pullOut(velocity: 0)
    }

    private func pullOut(velocity: Double) {
        let d = reduced ? 0 : max(0.26, min(0.52, (520 - velocity * 0.12) / 1000))
        uFlex.timing(to: 0, duration: d, now: now)
        uPull.timing(to: M.sheetAway, duration: d, curve: Self.pullCurve, now: now) { [weak self] in
            self?.pulled()
        }
    }

    private func pulled() {
        success.notificationOccurred(.success)
        uDone.timing(to: 1, duration: 0.3, now: now)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { [weak self] in
            self?.feedBlank()
        }
    }

    private func feedBlank() {
        guard mode == 1 else { return }
        fieldValues = ["", "", ""]
        uLines = [[], [], []]
        uN = [0, 0, 0]
        uLine.set(0)
        uCol.set(0)
        uPull.timing(to: 0, duration: reduced ? 0 : 0.9, curve: Self.outQuint, now: now)
        lastTouch = now
    }

    // MARK: Dragging the sheet

    /// Touches are in screen points; the machine is drawn scaled by g.scale.
    func dragBegin(at p: CGPoint, g: TypewriterSignupGeo) -> Bool {
        typealias A = TypewriterSignupArt
        let x = p.x / g.scale
        let y = p.y / g.scale
        let left = g.cx - A.textX - pose.col * A.adv - A.adv / 2
        let top = g.sy - A.topPad - pose.line * A.lineH - pose.pull
        let inside = x > left && x < left + A.paperW && y > top && y < g.platenY
        dragging = inside
        if inside { enterUser() }
        return inside
    }

    func dragUpdate(translation: Double, velocity: Double, g: TypewriterSignupGeo) {
        guard dragging else { return }
        lastTouch = now
        let up = Self.rubber(max(0, -translation / g.scale), 70)
        let down = min(0, -translation / g.scale)
        uPull.set(up + down / (1 + -down / 12))
        uFlex.set(max(-1, min(1, -velocity / 1600)))
    }

    func dragEnd(translation: Double, velocity: Double, g: TypewriterSignupGeo) {
        defer {
            dragging = false
            lastTouch = now
        }
        guard dragging else { return }
        if -translation > 90 || velocity < -700 {
            pullOut(velocity: velocity)
        } else {
            uPull.spring(to: 0, velocity: -velocity / g.scale, damping: 16, stiffness: 260)
            uFlex.spring(to: 0, damping: 12, stiffness: 200)
        }
    }

    private static func rubber(_ x: Double, _ limit: Double) -> Double {
        if x <= limit { return x }
        let over = x - limit
        return limit + over / (1 + over / 120)
    }
}
