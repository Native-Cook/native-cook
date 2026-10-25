import SwiftUI
import UIKit

/*
 Punch Card Form — a member card for an invented "Analytical Society": three real text fields
 (name, year, wing) above an 80-column manila punch card on a machine bed. Every letter typed is
 punched into its own column with Hollerith code: the head strikes, light from the lamp under the
 bed shines through the new holes, chads tumble out, the letter prints along the top edge and the
 card steps on one column. Submit slides the card down into the reader (its lamps chase) while a
 fresh blank card registers from the right.

 autoPlay (loop = 9 s, PunchCardFormMotion): rest; "ADA" typed 1.5–2.9 s, a tab to YEAR, "2026"
 3.62–4.22 s, "WEST" 4.46–5.0 s; Submit 5.2 s: the card feeds down 5.25–6.1 s, the next one
 registers 5.7–7.2 s, the label rolls Submit card → Reading… → Welcome, member and back; rest.

 Touch: focus a field and type; Submit feeds the card. Two seconds after the last keystroke (with
 the keyboard down) the holes fade as the card slides back to column 1 and the loop restarts.
 Reduced motion: the typed card, still, before it is submitted.
 */
struct PunchCardForm: View {
    /// Types a member card, submits it into the reader and loads a fresh one, every loop.
    var autoPlay = false

    static let loop = 9.0
    typealias M = PunchCardFormMotion
    typealias Art = PunchCardFormArt

    static let amber = Color(hex: 0xE2B05C)
    static let cream = Color(hex: 0xEFE8D6)
    static let labels = ["Submit card", "Reading…", "Welcome, member"]
    static let placeholders = ["Your name", "Year", "Wing"]
    /// Monospace advance of the field's type (Menlo is 0.602 em), for the demo's caret.
    static let advance = 17 * 0.602

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = PunchCardFormModel()
    @State private var art: Art.Baked?
    @State private var awake = false
    @State private var values = ["", "", ""]
    @State private var caret = -1
    @State private var userMode = false
    @FocusState private var focus: Int?

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let roomH = size.height - insets.top - insets.bottom
            let k = min(size.width / M.designW, roomH / M.designH, 1.15)
            let ox = (size.width - M.designW * k) / 2
            let oy = insets.top + max(0, (roomH - M.designH * k) / 2)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, sz in Art.drawRoom(ctx, size: sz, art: art) }
                    .frame(width: size.width, height: size.height)
                design
                    .frame(width: M.designW, height: M.designH, alignment: .topLeading)
                    .scaleEffect(k, anchor: .topLeading)
                    .offset(x: ox, y: oy)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .ignoresSafeArea(.keyboard)
        .background(Color(hex: 0x121613).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task { art = await Art.shared.value }
        .onChange(of: focus) { _, now in
            if let i = now { onFocus(i) } else { model.blur(at: Date.now.timeIntervalSinceReferenceDate) }
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var paused: Bool { !((auto && art != nil) || awake) }

    private func frame(_ date: Date) -> PunchCardFormModel.Scene {
        model.frame(date.timeIntervalSinceReferenceDate, auto: auto && art != nil, reduced: reduceMotion) { event in
            DispatchQueue.main.async {
                switch event {
                case let .demo(n, field):
                    values = [String(M.script[0].prefix(min(n, 3))), String(M.script[1].prefix(max(0, min(n - 3, 4)))),
                              String(M.script[2].prefix(max(0, n - 7)))]
                    caret = field
                case .clear:
                    values = ["", "", ""]
                case .demoMode:
                    userMode = false
                    awake = false
                }
            }
        }
    }

    private var design: some View {
        ZStack(alignment: .topLeading) {
            PunchCardFormHeader()
            fields
            TimelineView(.animation(paused: paused)) { timeline in
                scene(frame(timeline.date))
            }
            .frame(width: M.designW, height: M.canvasH, alignment: .topLeading)
            .allowsHitTesting(false)
            .offset(y: M.canvasY)
            Color.clear
                .contentShape(Rectangle())
                .frame(width: M.designW, height: M.canvasH)
                .onTapGesture { focus = nil }
                .offset(y: M.canvasY)
                .accessibilityHidden(true)
            submitButton
            Text("Cards are read at the front desk · Gallery 3")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.rgba(239, 232, 214, 0.45))
                .frame(width: 345)
                .offset(x: 24, y: 700)
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }

    // MARK: Fields

    private var fields: some View {
        let widths = [146.625, 86.25, 112.125]
        return HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { i in
                let f = M.fields[i]
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(f.label) · \(f.first)–\(f.first + f.size - 1)")
                        .font(.system(size: 9, weight: .bold).monospacedDigit()).kerning(1.2)
                        .foregroundStyle(Color.rgba(236, 228, 206, 0.55))
                        .lineLimit(1)
                    ZStack(alignment: .topLeading) {
                        TextField("", text: Binding(get: { values[i] }, set: { onChange(i, $0) }),
                                  prompt: Text(Self.placeholders[i]).foregroundColor(Color.rgba(236, 228, 206, 0.32)))
                            .font(.custom("Menlo-Bold", fixedSize: 17))
                            .foregroundStyle(Color(hex: 0xF2EBD8))
                            .tint(Self.amber)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .keyboardType(i == 1 ? .numberPad : .asciiCapable)
                            .submitLabel(.next)
                            .onSubmit { focus = i < 2 ? i + 1 : nil }
                            .focused($focus, equals: i)
                            .frame(height: 28)
                            .accessibilityLabel("\(f.label), columns \(f.first) to \(f.first + f.size - 1)")
                        if caret == i && !userMode {
                            TimelineView(.animation(paused: paused)) { timeline in
                                let t = frame(timeline.date).t
                                RoundedRectangle(cornerRadius: 1)
                                    .fill(Self.amber)
                                    .frame(width: 2, height: 20)
                                    .opacity(sin(2 * Double.pi * 9 * t / M.loop) > -0.2 ? 1 : 0)
                            }
                            .frame(width: 2, height: 20)
                            .offset(x: Double(values[i].count) * Self.advance, y: 5)
                            .allowsHitTesting(false)
                        }
                    }
                    .padding(.top, 2)
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .frame(width: widths[i], height: 58, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(caret == i ? Self.amber.opacity(0.08) : Color.rgba(236, 228, 206, 0.06))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(caret == i ? Color.rgba(226, 176, 92, 0.7) : Color.rgba(236, 228, 206, 0.16), lineWidth: caret == i ? 1 : 0.5))
                        .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                )
                .contentShape(Rectangle())
                .onTapGesture { focus = i }
            }
        }
        .offset(x: 16, y: 98)
    }

    private func onFocus(_ i: Int) {
        awake = true
        caret = i
        if !userMode { values = ["", "", ""] }
        userMode = true
        model.takeOver(at: Date.now.timeIntervalSinceReferenceDate, startCol: Double(M.fields[i].first), focused: true)
    }

    private func clean(_ text: String, _ field: Int) -> String {
        let up = text.uppercased()
        let kept = up.unicodeScalars.filter { s in
            field == 1 ? (s.value >= 48 && s.value <= 57)
                : ((s.value >= 65 && s.value <= 90) || (s.value >= 48 && s.value <= 57) || s.value == 32 || s.value == 45)
        }
        var out = ""
        out.unicodeScalars.append(contentsOf: kept)
        return String(out.prefix(M.fields[field].size))
    }

    private func onChange(_ i: Int, _ text: String) {
        let next = clean(text, i)
        if next == values[i] { return }
        if next.count > values[i].count { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        values[i] = next
        awake = true
        model.punch(field: i, codes: next.unicodeScalars.map { Int($0.value) }, at: Date.now.timeIntervalSinceReferenceDate)
    }

    // MARK: Submit

    private var submitButton: some View {
        Button {
            focus = nil
            awake = true
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            let now = Date.now.timeIntervalSinceReferenceDate
            model.takeOver(at: now, startCol: 1, focused: false)
            model.submit(at: now)
            userMode = true
            caret = -1
        } label: {
            TimelineView(.animation(paused: paused)) { timeline in
                let at = frame(timeline.date).label
                VStack(spacing: 0) {
                    ForEach(Self.labels, id: \.self) { l in
                        Text(l)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(Color(hex: 0x2A1A06))
                            .frame(height: 22)
                    }
                }
                .offset(y: -at * 22)
                .frame(width: 300, height: 22, alignment: .top)
                .clipped()
            }
            .frame(width: 361, height: 52)
            .background(
                Capsule()
                    .fill(LinearGradient(stops: [
                        .init(color: Color(hex: 0xF3CF86), location: 0), .init(color: Color(hex: 0xE2AE57), location: 0.55),
                        .init(color: Color(hex: 0xC68D39), location: 1),
                    ], startPoint: .top, endPoint: .bottom))
                    .overlay(Capsule().stroke(LinearGradient(colors: [Color.rgba(255, 246, 220, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                    .shadow(color: .black.opacity(0.45), radius: 1, y: 1)
                    .shadow(color: Color(hex: 0xC68D39).opacity(0.25), radius: 12, y: 10)
            )
        }
        .buttonStyle(PunchCardFormPressStyle())
        .accessibilityLabel("Submit card")
        .offset(x: 16, y: 632)
    }

    // MARK: The machine

    private func scene(_ s: PunchCardFormModel.Scene) -> some View {
        let showTyped = s.feed < 1 && s.register > 0.02
        let x0 = M.cardLeft(s.col)
        let y0 = M.cardY + s.feed * (M.cardH + 24)
        var strike = 0.0
        for i in 0..<M.cols where s.chars[i] != 0 { strike = max(strike, M.strokeAt(s.now - s.times[i])) }
        return ZStack(alignment: .topLeading) {
            PunchCardFormBed(art: art)
            if s.register < 1 {
                PunchCardFormCard(art: art)
                    .offset(x: M.cardLeft(1) + s.register * M.registerFrom, y: M.cardY)
            }
            if showTyped {
                ZStack(alignment: .topLeading) {
                    PunchCardFormCard(art: art)
                        .offset(x: x0, y: y0)
                    Canvas { ctx, _ in Self.drawHoles(ctx, s, x0: x0, y0: y0) }
                        .frame(width: M.designW, height: M.slotY + 6)
                }
                .frame(width: M.designW, height: M.slotY + 6, alignment: .topLeading)
                .clipped()
            }
            Rectangle()
                .fill(Color(hex: 0xE0533F).opacity(0.35))
                .frame(width: 1, height: Double(M.rows) * M.rowPitch + 6)
                .offset(x: M.stationX - 0.5, y: M.cardY + M.marginTop - 6)
            PunchCardFormHead()
                .offset(x: M.stationX - 40, y: M.cardY - 2 + strike * 3 - 40)
            Canvas { ctx, _ in Self.drawChads(ctx, s) }
                .frame(width: M.designW, height: M.canvasH)
            PunchCardFormReader(art: art)
                .offset(y: s.busy * 0.35 * sin(2 * Double.pi * 270 * s.t / 9))
            Canvas { ctx, _ in Self.drawLamps(ctx, s) }
                .frame(width: M.designW, height: M.canvasH)
        }
        .frame(width: M.designW, height: M.canvasH, alignment: .topLeading)
    }

    /// Holes (light from the lamp under the bed comes through them) and the printed letters.
    static func drawHoles(_ ctx: GraphicsContext, _ s: PunchCardFormModel.Scene, x0: Double, y0: Double) {
        var holes = Path()
        var any = false
        let glyphColor = Color(hex: 0x2A2620)
        for i in 0..<M.cols {
            let code = s.chars[i]
            if code == 0 { continue }
            let age = s.now - s.times[i]
            if age < 0 { continue }
            let cx = x0 + M.colX(Double(i + 1))
            if cx < -10 || cx > M.designW + 10 { continue }
            let mask = M.holeMask(code)
            if age > 0.025 {
                for r in 0..<M.rows where mask & (1 << r) != 0 {
                    let cy = y0 + M.rowY(r)
                    holes.addRoundedRect(in: CGRect(x: cx - M.holeW / 2, y: cy - M.holeH / 2, width: M.holeW, height: M.holeH),
                                         cornerSize: CGSize(width: 1.1, height: 1.1))
                    any = true
                }
            }
            let a = min(1, age / 0.08)
            let ch = String(UnicodeScalar(UInt8(code)))
            let glyph = ctx.resolve(Text(ch).font(.custom("Menlo-Bold", fixedSize: 10)).foregroundStyle(glyphColor.opacity(a * s.ink)))
            ctx.draw(glyph, at: CGPoint(x: cx - 3, y: y0 + M.marginTop - 12 + (1 - a) * -2 + 10 * 0.22), anchor: .bottomLeading)
        }
        guard any else { return }
        ctx.fill(holes, with: .linearGradient(
            Gradient(stops: [.init(color: Color(hex: 0xFFFBEA).opacity(s.ink), location: 0), .init(color: Color(hex: 0xFFEBB8).opacity(s.ink), location: 0.5),
                             .init(color: Color(hex: 0xFFD27E).opacity(s.ink), location: 1)]),
            startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y0 + M.cardH)))
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 5))
            l.blendMode = .plusLighter
            l.fill(holes, with: .color(Color(hex: 0xFFC864).opacity(0.7 * s.ink)))
        }
        ctx.stroke(holes, with: .color(Color(hex: 0x7A6238).opacity(0.9 * s.ink)), lineWidth: 1)
    }

    /// Chads tumbling out of the station and into the reader's bin, as one path.
    static func drawChads(_ ctx: GraphicsContext, _ s: PunchCardFormModel.Scene) {
        var chads = Path()
        var n = 0
        for i in 0..<M.cols {
            let code = s.chars[i]
            if code == 0 { continue }
            let age = s.now - s.times[i]
            if age < 0.02 || age > 0.9 { continue }
            let mask = M.holeMask(code)
            for r in 0..<M.rows where mask & (1 << r) != 0 {
                let p = M.chadPose(i + 1, r, age - 0.02)
                if p.y > M.slotY + 8 { continue }
                let ca = cos(p.spin), sa = sin(p.spin)
                let hw = M.holeW / 2 * max(0.25, abs(p.flip)), hh = M.holeH / 2
                func pt(_ a: Double, _ b: Double) -> CGPoint { CGPoint(x: p.x + a * ca - b * sa, y: p.y + a * sa + b * ca) }
                chads.move(to: pt(-hw, -hh))
                chads.addLine(to: pt(hw, -hh))
                chads.addLine(to: pt(hw, hh))
                chads.addLine(to: pt(-hw, hh))
                chads.closeSubpath()
                n += 1
            }
        }
        guard n > 0 else { return }
        ctx.drawLayer { l in
            l.addFilter(.blur(radius: 1.5))
            l.translateBy(x: 1.5, y: 3)
            l.fill(chads, with: .color(.black.opacity(0.35)))
        }
        ctx.fill(chads, with: .color(Color(hex: 0xE6D4A8)))
        ctx.stroke(chads, with: .color(Color(hex: 0xA08A5C)), lineWidth: 0.6)
    }

    /// Lamps: ready (green) at rest; while reading they chase, amber and green.
    static func drawLamps(_ ctx: GraphicsContext, _ s: PunchCardFormModel.Scene) {
        for i in 0..<4 {
            let x = 36 + Double(i) * 30
            let y = M.slotY + 80
            let chase = 0.5 + 0.5 * sin(2 * Double.pi * 18 * s.t / 9 - Double(i) * 1.4)
            let idleOn = i == 0 ? 1.0 : 0.08
            let on = idleOn * (1 - s.busy) + s.busy * (i % 2 == 0 ? chase : 1 - chase)
            let hue = i == 0 || i == 2 ? Color(hex: 0x7CFF9A) : Color(hex: 0xFFC25A)
            ctx.fill(Path(ellipseIn: CGRect(x: x - 4.2, y: y - 4.2, width: 8.4, height: 8.4)), with: .color(hue.opacity(0.15 + 0.85 * on)))
            // The halo: a blurred 7 pt disc (σ 6), drawn as its radial falloff and added.
            var g = ctx
            g.blendMode = .plusLighter
            g.fill(Path(ellipseIn: CGRect(x: x - 19, y: y - 19, width: 38, height: 38)), with: .radialGradient(
                Gradient(stops: [.init(color: hue.opacity(0.55 * on * 0.62), location: 0), .init(color: hue.opacity(0.55 * on * 0.4), location: 0.37),
                                 .init(color: hue.opacity(0), location: 1)]),
                center: CGPoint(x: x, y: y), startRadius: 0, endRadius: 19))
        }
    }
}

// MARK: - Model

final class PunchCardFormModel {
    typealias M = PunchCardFormMotion

    struct Scene {
        var chars: [Int]
        var times: [Double]
        var now: Double
        var col: Double
        var feed: Double
        var register: Double
        var busy: Double
        var t: Double
        var ink: Double
        var label: Double
    }

    enum Event {
        case demo(Int, Int)
        case clear
        case demoMode
    }

    /// The demo's stand-in for "now" in reduced motion: the card typed, before Submit.
    static let stillT = 5.1

    private var autoStart: Double?
    private var lastFrame: Double?
    private var lastNow = -1.0
    private(set) var scene = Scene(chars: M.autoChar, times: M.autoTime, now: 0, col: 1, feed: 0, register: 1, busy: 0, t: 0, ink: 1, label: 0)

    private var owned = false
    private var focused = false
    private var busy = false
    private var returning = false
    private var lastActivity = 0.0
    private var returnEnd = 0.0
    private var busyEnd = 0.0
    private var demoKey = -1
    private var wasFresh = true
    private var chars = [Int](repeating: 0, count: M.cols)
    private var times = [Double](repeating: -1, count: M.cols)
    private var col = M.Track(1), feed = M.Track(0), register = M.Track(1), ink = M.Track(1), label = M.Track(0)

    private func blank() {
        chars = [Int](repeating: 0, count: M.cols)
        times = [Double](repeating: -1, count: M.cols)
    }

    func frame(_ now: Double, auto: Bool, reduced: Bool, events: (Event) -> Void) -> Scene {
        if now == lastNow { return scene }
        lastNow = now
        defer { lastFrame = now }
        if owned {
            if busy && now >= busyEnd {
                blank()
                col.set(1); feed.set(0); register.set(1)
                busy = false
                lastActivity = now
            }
            if !focused && !busy && !returning && now - lastActivity >= 2 {
                // Back to rest within 0.9 s: the holes fade as the card slides back to column 1.
                returning = true
                ink.timing(0, 0.32, at: now)
                col.timing(1, 0.76, .bezier(0.65, 0, 0.35, 1), at: now)
                returnEnd = now + 0.76
            }
            if returning && now >= returnEnd {
                blank()
                ink.set(1)
                returning = false
                owned = false
                autoStart = now
                demoKey = -1
                events(.demoMode)
            }
        }
        if owned {
            let fresh = register.value(now) >= 1 && feed.value(now) <= 0 && chars.allSatisfy { $0 == 0 }
            if fresh && !wasFresh { events(.clear) }
            wasFresh = fresh
            let t = now.truncatingRemainder(dividingBy: M.loop)
            scene = Scene(chars: chars, times: times, now: now, col: col.value(now), feed: feed.value(now), register: register.value(now),
                          busy: busy ? 1 : 0, t: t, ink: ink.value(now), label: label.value(now))
            return scene
        }
        var t = 0.0
        if reduced {
            t = Self.stillT
        } else if auto {
            if autoStart == nil { autoStart = now }
            if let last = lastFrame, now - last > 0.25, let s = autoStart { autoStart = s + (now - last) - 1 / 60 }
            t = max(0, (now - autoStart!).truncatingRemainder(dividingBy: M.loop))
        } else {
            autoStart = nil
        }
        let key = M.autoTyped(t) * 10 + (M.autoField(t) + 1)
        if key != demoKey {
            demoKey = key
            events(.demo(M.autoTyped(t), M.autoField(t)))
        }
        if reduced {
            scene = Scene(chars: M.autoChar, times: M.autoTime, now: M.loop, col: M.autoColumn(Self.stillT), feed: 0, register: 1, busy: 0,
                          t: Self.stillT, ink: 1, label: 0)
        } else {
            scene = Scene(chars: M.autoChar, times: M.autoTime, now: t, col: M.autoColumn(t), feed: M.autoFeed(t), register: M.autoRegister(t),
                          busy: M.autoBusy(t), t: t, ink: 1, label: M.autoLabel(t))
        }
        return scene
    }

    /// A finger or a keyboard takes the desk: the demo stops, and a blank card waits at `startCol`.
    func takeOver(at now: Double, startCol: Double, focused: Bool) {
        lastActivity = now
        if focused { self.focused = true }
        if owned && !returning { return }
        returning = false
        owned = true
        blank()
        col.set(startCol); feed.set(0); register.set(1); ink.set(1); label.set(0)
        busy = false
        wasFresh = true
    }

    func blur(at now: Double) {
        focused = false
        lastActivity = now
    }

    /// One field's text changed: punch (or clear) the columns that differ, and move the card on.
    func punch(field: Int, codes: [Int], at now: Double) {
        let f = M.fields[field]
        for i in 0..<f.size {
            let code = i < codes.count ? codes[i] : 0
            let k = f.first - 1 + i
            if chars[k] != code {
                chars[k] = code
                times[k] = code == 0 ? -1 : now
            }
        }
        let next = min(f.first + codes.count, f.first + f.size - 1)
        col.spring(Double(next), damping: 18, stiffness: 260, mass: 0.6, delay: 0.07, at: now)
        lastActivity = now
    }

    /// Submit: the card goes into the reader, a fresh one registers from the right.
    func submit(at now: Double) {
        lastActivity = now
        busy = true
        label.run([
            .init(to: 1, kind: .timing(0.25, .inOutQuad)),
            .init(delay: 0.65, to: 2, kind: .timing(0.25, .inOutQuad)),
            .init(delay: 1.3, to: 0, kind: .timing(0.3, .inOutQuad)),
        ], at: now)
        feed.timing(1, 0.85, .bezier(0.65, 0, 0.35, 1), at: now)
        register.timing(0, 1.5, .bezier(0.2, 0.9, 0.25, 1.06), delay: 0.45, at: now)
        busyEnd = now + 1.95
    }
}

// MARK: - Static pieces

struct PunchCardFormHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("ANALYTICAL SOCIETY")
                .font(.system(size: 12, weight: .bold)).kerning(2.4)
                .foregroundStyle(PunchCardForm.amber)
            Text("Member card")
                .font(.system(size: 32, weight: .heavy)).kerning(-0.6)
                .foregroundStyle(PunchCardForm.cream)
            Text("Each letter is punched into its own column.")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color.rgba(239, 232, 214, 0.6))
        }
        .frame(width: 345, alignment: .leading)
        .offset(x: 24, y: 8)
        .allowsHitTesting(false)
    }
}

/// One card, with the two shadows it casts on the bed.
struct PunchCardFormCard: View {
    var art: PunchCardFormArt.Baked?

    var body: some View {
        let outline = PunchCardFormArt.outline()
        ZStack(alignment: .topLeading) {
            outline.fill(Color.black.opacity(0.4)).blur(radius: 9).offset(y: 6)
            outline.fill(Color.black.opacity(0.45)).blur(radius: 1.5).offset(y: 1)
            if let card = art?.card {
                Image(decorative: card, scale: PunchCardFormArt.bake)
                    .resizable()
                    .frame(width: PunchCardFormMotion.cardW, height: PunchCardFormMotion.cardH)
            } else {
                outline.fill(Color(hex: 0xE2D0A4))
            }
        }
        .frame(width: PunchCardFormMotion.cardW, height: PunchCardFormMotion.cardH, alignment: .topLeading)
    }
}

struct PunchCardFormBed: View {
    var art: PunchCardFormArt.Baked?

    var body: some View {
        Canvas { ctx, _ in PunchCardFormArt.drawBed(ctx, art: art) }
            .frame(width: PunchCardFormMotion.designW, height: PunchCardFormMotion.canvasH)
    }
}

struct PunchCardFormReader: View {
    var art: PunchCardFormArt.Baked?

    var body: some View {
        Canvas { ctx, _ in PunchCardFormArt.drawReader(ctx, art: art) }
            .frame(width: PunchCardFormMotion.designW, height: PunchCardFormMotion.canvasH)
    }
}

struct PunchCardFormHead: View {
    var body: some View {
        Canvas { ctx, _ in PunchCardFormArt.drawHead(ctx) }
            .frame(width: 80, height: 80)
    }
}

struct PunchCardFormPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}
