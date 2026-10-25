import SwiftUI
import UIKit

/*
 Icing Name Form — "Crumb & Co · Write on the cake". An 8-inch cake seen from above on a gold drum,
 a ring of piped rosettes round its edge. Whatever is typed in the message field is piped onto it in
 buttercream: the nozzle follows each letter's strokes and lays a glossy tube with a pressure bulge
 where it starts, lifting between strokes. The colour chips re-pipe the message in another icing,
 which flows through the letters. The price rolls with every letter.

 Touch: typing pipes each new letter (0.22 s + 5 ms a pt of icing); the message re-centres with a
 0.35 s glide; a chip flows the new colour through the letters in 0.7 s. Drag on the cake to turn the
 turntable (it follows the finger round its centre and coasts on release). With autoPlay, 2 s after
 the last touch (and with the field closed) the cake turns home and cross-fades back to the loop's
 first frame in 0.8 s. Reduced motion: "Happy birthday" stays piped; typed letters appear whole.

 One Canvas: counter and cake baked once at 2x off the main thread; per frame the cake image, the
 lettering paths, the knife and the piping bag.
 */
struct IcingNameForm: View {
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = IcingNameFormEngine()
    @State private var text = IcingNameFormMotion.restText
    @State private var icing = 0
    @State private var running = true
    @State private var hero: CGRect = .zero
    @State private var fieldBottom: CGFloat = 0
    @State private var art: IcingNameFormArtImages?
    @State private var keyboardTop: CGFloat = .infinity
    @FocusState private var focus: Bool

    private typealias S = IcingNameFormStyle
    private static let space = "icingNameForm"

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing, height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .ignoresSafeArea(.keyboard)
        .background(S.background.ignoresSafeArea())
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { n in
            let end = (n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .zero
            let h = end.minY
            let d = (n.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            withAnimation(.timingCurve(0.32, 0.72, 0, 1, duration: d > 0 ? d : 0.25)) { keyboardTop = end == .zero ? .infinity : h }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let geo: IcingNameFormGeo? = hero.width > 0 ? IcingNameFormGeo(w: size.width, h: size.height, hero: hero) : nil
        let keyboard = keyboardTop.isFinite ? max(0, size.height - keyboardTop) : 0
        let lift = max(0, keyboard - (size.height - fieldBottom - 12))
        return ZStack(alignment: .topLeading) {
            S.background
            TimelineView(.animation(paused: !running)) { timeline in
                let date = timeline.date
                let _ = engine.step(date)
                Canvas { ctx, _ in
                    _ = date
                    draw(ctx)
                }
                .frame(width: size.width, height: size.height)
            }
            .allowsHitTesting(false)

            column(insets: insets)
                .frame(width: size.width, height: size.height, alignment: .top)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: Self.space)
        .clipped()
        .offset(y: -lift)
        .onAppear { engine.setMode(auto: autoPlay && !reduceMotion, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, r in
            engine.setMode(auto: autoPlay && !r, reduced: r)
            wake()
        }
        .onChange(of: geo) { _, g in
            if let g { engine.configure(g) }
            engine.updateShown(text: text, icing: icing)
        }
        .task(id: geo) {
            guard let geo else { return }
            engine.configure(geo)
            engine.updateShown(text: text, icing: icing)
            let baked = await Task.detached(priority: .userInitiated) { () -> IcingNameFormArtImages? in
                guard let t = IcingNameFormBake.table(geo), let c = IcingNameFormBake.cake(geo) else { return nil }
                return IcingNameFormArtImages(table: Image(decorative: t, scale: 2), cake: Image(decorative: c, scale: 2))
            }.value
            art = baked
            engine.artReady = baked != nil
            wake()
        }
        .onChange(of: text) { _, t in engine.updateShown(text: t, icing: icing) }
        .onChange(of: icing) { _, i in engine.updateShown(text: text, icing: i) }
        .onChange(of: focus) { _, f in
            engine.focusChanged(f)
            wake()
        }
        .task(id: running) {
            // Applies the loop's threshold events (script text, chip) and pauses when idle.
            guard running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(33))
                if let t = engine.pendingText {
                    engine.pendingText = nil
                    if t != text { text = t }
                }
                if let i = engine.pendingIcing {
                    engine.pendingIcing = nil
                    if i != icing { icing = i }
                }
                if !engine.wantsFrames && engine.artReady {
                    running = false
                    return
                }
            }
        }
    }

    private func wake() {
        if !running { running = true }
    }

    // MARK: Drawing

    private func draw(_ ctx: GraphicsContext) {
        guard let g = engine.geo, let art else { return }
        ctx.draw(art.table, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
        let cake = ctx.resolve(art.cake)
        let e = engine
        guard e.mode == .user || e.mode == .back, let u = e.user else {
            IcingNameFormScene.drawLoopFrame(ctx, g, cake: cake, t: e.mode == .auto ? e.loopT : 0, rest: e.restL, second: e.secondL, t0: e.sched.t0, t1: e.sched.t1)
            return
        }
        let now = e.now
        let k = e.returnK
        let a = e.turn
        IcingNameFormArt.drawCake(ctx, g, cake: cake, x: g.cx, y: g.cy, turn: a)
        let flow = e.reduced ? 1 : IcingNameFormMotion.easeInOut(IcingNameFormMotion.seg(now, u.colorAt, u.colorAt + IcingNameFormEngine.flowFor))
        IcingNameFormScene.drawWriting(
            ctx, u.L, x: g.cx, y: g.cy, turn: a, frac: u.fracs(now, instant: e.reduced),
            sx: u.shift(now, axis: 0), sy: u.shift(now, axis: 1), a: u.colorFrom, b: u.colorTo, flow: flow, alpha: 1 - k, clipLeft: nil
        )
        if k > 0 {
            IcingNameFormScene.drawWriting(ctx, e.restL, x: g.cx, y: g.cy, turn: a, frac: nil, sx: nil, sy: nil, a: 0, b: 0, flow: 0, alpha: k, clipLeft: nil)
        }
        // The nozzle works in the cake's frame while it pipes; at rest it hangs where it always does.
        let p = e.pen
        let park = e.park
        let c = cos(a), s = sin(a)
        let busy = 1 - p.2
        let lx = p.0 * c - p.1 * s
        let ly = p.0 * s + p.1 * c
        let x = lx * busy + park.0 * (1 - busy)
        let y = ly * busy + park.1 * (1 - busy)
        IcingNameFormArt.drawNozzle(
            ctx, x: g.cx + x + (park.0 - x) * k, y: g.cy + y + (park.1 - y) * k,
            lift: max(p.2, k), rgb: IcingNameFormArt.icings[u.colorTo].rgb
        )
    }

    // MARK: Controls

    private func column(insets: EdgeInsets) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(S.ink)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle().fill(Color(red: 1, green: 252 / 255, blue: 247 / 255, opacity: 0.85))
                            .shadow(color: Color(red: 70 / 255, green: 40 / 255, blue: 20 / 255, opacity: 0.16), radius: 1, y: 1)
                            .shadow(color: Color(red: 70 / 255, green: 40 / 255, blue: 20 / 255, opacity: 0.08), radius: 8, y: 6)
                    )
                Spacer(minLength: 0)
                Text("Crumb & Co")
                    .font(.system(size: 15, weight: .semibold))
                    .kerning(0.2)
                    .foregroundStyle(S.ink)
                Spacer(minLength: 0)
                Text("Step 2 of 3")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(S.muted)
                    .frame(width: 76, alignment: .trailing)
            }
            .frame(height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text("Write on the cake")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.4)
                    .foregroundStyle(S.ink)
                    .frame(height: 36)
                Text("Piped by hand in Swiss meringue buttercream")
                    .font(.system(size: 15))
                    .foregroundStyle(S.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 12)

            heroArea

            field

            HStack(alignment: .center, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    smallLabel("Icing")
                    TimelineView(.animation(paused: !running)) { _ in
                        IcingNameFormName(position: engine.chipPos)
                    }
                }
                Spacer(minLength: 0)
                chips
            }
            .frame(height: 48)
            .padding(.top, 16)

            HStack(alignment: .bottom, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    smallLabel("Total")
                    Text("Vanilla bean · 8 in · serves 10")
                        .font(.system(size: 14))
                        .foregroundStyle(S.muted)
                }
                Spacer(minLength: 0)
                TimelineView(.animation(paused: !running)) { _ in
                    IcingNameFormPrice(price: engine.price)
                }
            }
            .padding(.top, 16)

            Button {} label: {
                Text("Add to order")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(red: 0xFB / 255, green: 0xF1 / 255, blue: 0xE6 / 255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Capsule().fill(S.ink)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                            .shadow(color: S.ink.opacity(0.25), radius: 2, y: 2)
                            .shadow(color: S.ink.opacity(0.18), radius: 12, y: 12)
                    )
            }
            .buttonStyle(IcingNameFormPress(scale: 0.97))
            .padding(.top, 16)
        }
        .padding(.horizontal, 20)
        .padding(.top, insets.top + 6)
        .padding(.bottom, insets.bottom + 12)
    }

    private func smallLabel(_ s: String) -> some View {
        Text(s.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .kerning(0.4)
            .foregroundStyle(S.muted)
    }

    private var heroArea: some View {
        Color.clear
            .frame(maxWidth: .infinity, minHeight: 240, maxHeight: .infinity)
            .contentShape(Rectangle())
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { hero = $0 }
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                    .onChanged { v in
                        if !engine.touching { engine.dragBegan(at: v.startLocation) }
                        let moved = hypot(v.translation.width, v.translation.height) >= 2
                        if moved { engine.dragMoved(to: v.location, started: true) }
                        wake()
                    }
                    .onEnded { v in
                        engine.dragEnded(at: v.location, velocity: v.velocity)
                        wake()
                    }
            )
            .accessibilityElement()
            .accessibilityLabel(text.isEmpty ? "Cake preview with no message piped on top. Drag to turn the cake." : "Cake preview with \"\(text)\" piped on top. Drag to turn the cake.")
    }

    private var field: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Message")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(S.muted)
                TextField(
                    "",
                    text: Binding(get: { text }, set: { onType($0) }),
                    prompt: Text("Write something sweet").foregroundStyle(Color(red: 0xBF / 255, green: 0xA9 / 255, blue: 0x98 / 255))
                )
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(S.ink)
                .tint(IcingNameFormArt.icings[icing].color)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused($focus)
                .padding(.vertical, 2)
                .padding(.trailing, 52)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            Text("\(text.count)/\(IcingNameFormFont.maxLetters)")
                .font(.system(size: 13).monospacedDigit())
                .foregroundStyle(Color(red: 0xB3 / 255, green: 0x9C / 255, blue: 0x8A / 255))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, 16)
                .padding(.bottom, 12)
                .allowsHitTesting(false)
        }
        .frame(height: 64)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(S.card)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.9), lineWidth: 1)
                        .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)))
                )
                .shadow(color: Color(red: 70 / 255, green: 40 / 255, blue: 20 / 255, opacity: 0.14), radius: 1, y: 1)
                .shadow(color: Color(red: 70 / 255, green: 40 / 255, blue: 20 / 255, opacity: 0.07), radius: 12, y: 10)
        )
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .named(Self.space)).maxY } action: { fieldBottom = $0 }
    }

    private var chips: some View {
        HStack(spacing: S.chipGap) {
            ForEach(Array(IcingNameFormArt.icings.enumerated()), id: \.offset) { i, ic in
                Button {
                    pick(i)
                } label: {
                    Circle()
                        .fill(ic.color
                            .shadow(.inner(color: .black.opacity(0.2), radius: 3, y: -3))
                            .shadow(.inner(color: .white.opacity(0.35), radius: 1.5, y: 2)))
                        .frame(width: S.chip, height: S.chip)
                        .overlay(alignment: .topLeading) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Color.white.opacity(0.35))
                                .frame(width: 13, height: 7)
                                .rotationEffect(.degrees(-20))
                                .offset(x: 8, y: 6)
                        }
                        .shadow(color: Color(red: 50 / 255, green: 25 / 255, blue: 15 / 255, opacity: 0.3), radius: 1, y: 1)
                        .shadow(color: Color(red: 50 / 255, green: 25 / 255, blue: 15 / 255, opacity: 0.14), radius: 5, y: 4)
                        .padding(-6)
                        .contentShape(Circle())
                        .padding(6)
                }
                .buttonStyle(IcingNameFormPress(scale: 0.94))
                .accessibilityLabel("\(ic.name) icing")
                .accessibilityAddTraits(icing == i ? .isSelected : [])
            }
        }
        .overlay(alignment: .topLeading) {
            TimelineView(.animation(paused: !running)) { _ in
                Circle()
                    .strokeBorder(S.ink, lineWidth: 2)
                    .frame(width: S.chip + 10, height: S.chip + 10)
                    .offset(x: -5 + engine.chipPos * (S.chip + S.chipGap), y: -5)
            }
            .allowsHitTesting(false)
        }
    }

    // MARK: Actions

    private func onType(_ value: String) {
        let next = String(Array(value).prefix(IcingNameFormFont.maxLetters))
        let prev = text
        engine.type(next, prev: prev, icing: icing)
        text = next
        wake()
    }

    private func pick(_ i: Int) {
        let previous = icing
        engine.pick(i, text: text, previous: previous)
        icing = i
        UISelectionFeedbackGenerator().selectionChanged()
        wake()
    }
}

struct IcingNameFormArtImages {
    let table: Image
    let cake: Image
}

enum IcingNameFormStyle {
    static let ink = Color(red: 0x3B / 255, green: 0x24 / 255, blue: 0x18 / 255)
    static let muted = Color(red: 0x8C / 255, green: 0x74 / 255, blue: 0x62 / 255)
    static let card = Color(red: 0xFF / 255, green: 0xFB / 255, blue: 0xF6 / 255)
    static let background = Color(red: 0xF1 / 255, green: 0xE9 / 255, blue: 0xDF / 255)
    static let chip: CGFloat = 38
    static let chipGap: CGFloat = 14
    static let digitH: CGFloat = 30
}

struct IcingNameFormPress: ButtonStyle {
    var scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
    }
}

/// The icing's name, rolling to the chosen one.
struct IcingNameFormName: View {
    var position: Double
    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(IcingNameFormArt.icings.enumerated()), id: \.offset) { i, ic in
                let d = Double(i) - position
                Text(ic.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(IcingNameFormStyle.ink)
                    .frame(height: 20)
                    .opacity(max(0, 1 - abs(d) * 1.2))
                    .offset(y: d * 20)
            }
        }
        .frame(width: 92, height: 20, alignment: .topLeading)
        .clipped()
    }
}

/// The total, each digit rolling on its own strip.
struct IcingNameFormPrice: View {
    var price: (Double, Double, Double)

    private func digit(_ cents: Double, _ k: Int) -> Double {
        Double(Int(cents) / Int(pow(10, Double(k))) % 10)
    }

    private func column(_ place: Int) -> some View {
        let a = digit(price.0, place)
        let b = digit(price.1, place)
        return VStack(spacing: 0) {
            ForEach(0..<10, id: \.self) { n in
                Text("\(n)")
                    .font(.system(size: 26, weight: .bold).monospacedDigit())
                    .foregroundStyle(IcingNameFormStyle.ink)
                    .frame(width: 17, height: IcingNameFormStyle.digitH)
            }
        }
        .offset(y: -(a + (b - a) * price.2) * IcingNameFormStyle.digitH)
        .frame(width: 17, height: IcingNameFormStyle.digitH, alignment: .top)
        .clipped()
    }

    var body: some View {
        HStack(spacing: 0) {
            glyph("$")
            column(3)
            column(2)
            glyph(".")
            column(1)
            column(0)
        }
        .accessibilityElement()
        .accessibilityLabel("Total price")
    }

    private func glyph(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 26, weight: .bold))
            .foregroundStyle(IcingNameFormStyle.ink)
            .frame(height: IcingNameFormStyle.digitH)
    }
}
