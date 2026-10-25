import SwiftUI
import UIKit

/*
 Cross Stitch Input — "Stitchbook · Name your sampler". A wooden hoop of cream aida cloth fills the
 middle of the screen; whatever is typed in the name field is cross-stitched onto the cloth by a
 needle and thread, one X at a time, in the colour of the chosen skein. The stitch counter and the
 thread's name roll as they change.

 The cloth, hoop and printed border are baked once at 2x off the main thread; the name's stitches,
 the needle and its thread are drawn per frame in one Canvas.

 Touch: typing stitches each new letter (0.15 s + 55 ms a stitch), the needle following the newest
 letter and going back to rest when idle; the name re-centres on the grid with a 0.35 s glide; a skein
 chip re-threads the whole name (0.3 s). Tapping the hoop focuses the field. With autoPlay, 2 s after
 the field loses focus the cloth cross-fades back to the loop's first frame (0.8 s) and the loop
 starts again. Reduced motion: "Hi" stays stitched, nothing moves by itself, typed letters appear
 whole.
 */
struct CrossStitchInput: View {
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = CrossStitchInputEngine()
    @State private var text = CrossStitchInputMotion.restText
    @State private var thread = CrossStitchInputMotion.restThread
    @State private var running = true
    @State private var hero: CGRect = .zero
    @State private var fieldBottom: CGFloat = 0
    @State private var art: CrossStitchInputImages?
    @State private var keyboardTop: CGFloat = .infinity
    @FocusState private var focus: Bool

    private typealias S = CrossStitchInputStyle
    private static let space = "crossStitchInput"

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
            let d = (n.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            withAnimation(.timingCurve(0.32, 0.72, 0, 1, duration: d > 0 ? d : 0.25)) { keyboardTop = end == .zero ? .infinity : end.minY }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let geo: CrossStitchInputGeo? = hero.width > 0 ? CrossStitchInputGeo(w: size.width, h: size.height, hero: hero) : nil
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
        .task(id: geo) {
            guard let geo else { return }
            engine.geo = geo
            let scene = await Task.detached(priority: .userInitiated) { CrossStitchInputBake.scene(geo) }.value
            guard let scene, let border = CrossStitchInputBake.border(geo) else { return }
            art = CrossStitchInputImages(scene: Image(decorative: scene, scale: 2), border: border)
            engine.artReady = true
            wake()
        }
        .onChange(of: focus) { _, f in
            engine.focusChanged(f)
            if f { engine.takeOver(shown: text, thread: thread) }
            wake()
        }
        .task(id: running) {
            // Applies the loop's threshold events (the field's text, the chip) and pauses when idle.
            guard running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(33))
                if let t = engine.pendingText {
                    engine.pendingText = nil
                    if t != text { text = t }
                }
                if let i = engine.pendingThread {
                    engine.pendingThread = nil
                    if i != thread { thread = i }
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
        let e = engine
        let full = CGRect(x: 0, y: 0, width: g.w, height: g.h)
        ctx.draw(art.scene, in: full)
        ctx.draw(art.border, in: full)
        switch e.mode {
        case .auto, .still:
            CrossStitchInputArt.drawLoopFrame(ctx, g, t: e.mode == .auto ? e.loopT : 0, alpha: 1, withNeedle: true, rest: e.restL, second: e.secondL)
        case .user, .back:
            let u = e.user
            let now = e.now
            let k = e.returnK
            let rgb = u.colour(now)
            let gp = e.reduced ? u.layout.count.map(Double.init) : u.glyphPresent(now)
            CrossStitchInputArt.drawStitches(
                ctx, g, u.layout.cx, u.layout.cy, present: Double(u.layout.total), rgb: rgb, alpha: 1 - k,
                glyphs: .init(shiftX: u.shift(now, axis: 0), shiftY: u.shift(now, axis: 1), glyphOf: u.layout.glyphOf, present: gp, first: u.layout.first)
            )
            if k > 0 { CrossStitchInputArt.drawLoopFrame(ctx, g, t: 0, alpha: k, withNeedle: false, rest: e.restL, second: e.secondL) }
            let n = e.needle
            CrossStitchInputArt.drawNeedle(
                ctx, x: n.0 + (g.parkX - n.0) * k, y: n.1 + (g.parkY - n.1) * k, angle: -0.95,
                lift: n.4 * 0.5, fromX: n.2, fromY: n.3, rgb: rgb, alpha: 1
            )
        }
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
                        Circle().fill(Color(red: 1, green: 251 / 255, blue: 243 / 255, opacity: 0.8))
                            .shadow(color: S.shade.opacity(0.18), radius: 1, y: 1)
                            .shadow(color: S.shade.opacity(0.08), radius: 8, y: 6)
                    )
                Spacer(minLength: 0)
                Text("Stitchbook")
                    .font(.system(size: 15, weight: .semibold))
                    .kerning(0.2)
                    .foregroundStyle(S.ink)
                Spacer(minLength: 0)
                Text("KIT 04")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.2)
                    .foregroundStyle(S.muted)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .overlay(Capsule().strokeBorder(S.ink.opacity(0.18), lineWidth: 1))
            }
            .frame(height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text("Name your sampler")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.4)
                    .foregroundStyle(S.ink)
                    .frame(height: 36)
                Text("Two strands on 14-count aida · up to 10 letters")
                    .font(.system(size: 15))
                    .foregroundStyle(S.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 12)

            Color.clear
                .frame(maxWidth: .infinity, minHeight: 220, maxHeight: .infinity)
                .contentShape(Rectangle())
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { hero = $0 }
                .onTapGesture { focus = true }
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Sampler preview reading \(text.isEmpty ? "nothing yet" : text). Double tap to edit the name.")

            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    statLabel("Stitches")
                    TimelineView(.animation(paused: !running)) { _ in
                        let c = engine.counts
                        HStack(spacing: 0) {
                            CrossStitchInputDigits(value: c.0, places: 3)
                            Text(" / ")
                                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                                .foregroundStyle(S.muted)
                            CrossStitchInputDigits(value: c.1, places: 3)
                        }
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    statLabel("Thread")
                    TimelineView(.animation(paused: !running)) { _ in
                        CrossStitchInputThreadName(position: engine.chipPos)
                    }
                }
            }
            .padding(.bottom, 12)

            field

            HStack(spacing: S.chipGap) {
                ForEach(Array(CrossStitchInputArt.threads.enumerated()), id: \.offset) { i, th in
                    Button {
                        pickThread(i)
                    } label: {
                        Circle()
                            .fill(CrossStitchInputArt.col(th.rgb)
                                .shadow(.inner(color: .black.opacity(0.22), radius: 3, y: -3))
                                .shadow(.inner(color: .white.opacity(0.28), radius: 1.5, y: 2)))
                            .frame(width: S.chip, height: S.chip)
                            .overlay(alignment: .topLeading) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.white.opacity(0.28))
                                    .frame(width: 14, height: 8)
                                    .rotationEffect(.degrees(-20))
                                    .offset(x: 8, y: 6)
                            }
                            .shadow(color: Color(red: 46 / 255, green: 30 / 255, blue: 20 / 255, opacity: 0.3), radius: 1, y: 1)
                            .shadow(color: Color(red: 46 / 255, green: 30 / 255, blue: 20 / 255, opacity: 0.14), radius: 5, y: 4)
                            .padding(-6)
                            .contentShape(Circle())
                            .padding(6)
                    }
                    .buttonStyle(CrossStitchInputPress(scale: 0.94))
                    .accessibilityLabel("\(th.name) thread")
                    .accessibilityAddTraits(thread == i ? .isSelected : [])
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
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .padding(.top, 16)

            Button {} label: {
                Text("Save sampler")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(red: 0xF6 / 255, green: 0xEE / 255, blue: 0xDF / 255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Capsule().fill(S.ink)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                            .shadow(color: S.ink.opacity(0.25), radius: 2, y: 2)
                            .shadow(color: S.ink.opacity(0.18), radius: 12, y: 12)
                    )
            }
            .buttonStyle(CrossStitchInputPress(scale: 0.97))
            .padding(.top, 16)
        }
        .padding(.horizontal, 20)
        .padding(.top, insets.top + 6)
        .padding(.bottom, insets.bottom + 12)
    }

    private func statLabel(_ s: String) -> some View {
        Text(s.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .kerning(0.4)
            .foregroundStyle(S.muted)
    }

    private var field: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Name on the sampler")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(S.muted)
                TextField(
                    "",
                    text: Binding(get: { text }, set: { onType($0) }),
                    prompt: Text("Type a name").foregroundStyle(Color(red: 0xB3 / 255, green: 0xA5 / 255, blue: 0x94 / 255))
                )
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(S.ink)
                .tint(CrossStitchInputArt.col(CrossStitchInputArt.threads[thread].rgb))
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused($focus)
                .padding(.vertical, 2)
                .padding(.trailing, 48)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            Text("\(text.count)/\(CrossStitchInputFont.maxLetters)")
                .font(.system(size: 13).monospacedDigit())
                .foregroundStyle(Color(red: 0xA8 / 255, green: 0x98 / 255, blue: 0x8A / 255))
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
                        .strokeBorder(Color.white.opacity(0.8), lineWidth: 1)
                        .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)))
                )
                .shadow(color: S.shade.opacity(0.16), radius: 1, y: 1)
                .shadow(color: S.shade.opacity(0.08), radius: 12, y: 10)
        )
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .named(Self.space)).maxY } action: { fieldBottom = $0 }
    }

    // MARK: Actions

    private func onType(_ value: String) {
        let next = String(Array(value).prefix(CrossStitchInputFont.maxLetters))
        if engine.mode != .user { engine.takeOver(shown: text, thread: thread) }
        let prev = text
        engine.type(next, prev: prev)
        text = next
        if next.count > prev.count { UISelectionFeedbackGenerator().selectionChanged() }
        wake()
    }

    private func pickThread(_ i: Int) {
        if engine.mode != .user { engine.takeOver(shown: text, thread: thread) }
        engine.pickThread(i)
        thread = i
        UISelectionFeedbackGenerator().selectionChanged()
        wake()
    }
}

struct CrossStitchInputImages {
    let scene: Image
    let border: Image
}

enum CrossStitchInputStyle {
    static let ink = Color(red: 0x2E / 255, green: 0x26 / 255, blue: 0x20 / 255)
    static let muted = Color(red: 0x7D / 255, green: 0x6D / 255, blue: 0x5C / 255)
    static let card = Color(red: 0xFB / 255, green: 0xF7 / 255, blue: 0xEF / 255)
    static let shade = Color(red: 74 / 255, green: 50 / 255, blue: 32 / 255)
    static let background = Color(red: 0xED / 255, green: 0xE3 / 255, blue: 0xD3 / 255)
    static let chip: CGFloat = 40
    static let chipGap: CGFloat = 14
    static let digitH: CGFloat = 22
}

struct CrossStitchInputPress: ButtonStyle {
    var scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
    }
}

/// A rolling counter: each place is a strip 0…9, 0 that rolls as the value passes it.
struct CrossStitchInputDigits: View {
    var value: Double
    var places: Int

    private static func position(_ v: Double, _ k: Int) -> Double {
        let p = pow(10, Double(k))
        let d = floor(v / p).truncatingRemainder(dividingBy: 10)
        if k == 0 { return v - floor(v / 10) * 10 }
        let lower = v - floor(v / p) * p
        return d + max(0, lower - (p - 1))
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach((0..<places).reversed(), id: \.self) { k in
                let v = max(0, value)
                VStack(spacing: 0) {
                    ForEach(0..<11, id: \.self) { n in
                        Text("\(n % 10)")
                            .font(.system(size: 18, weight: .bold).monospacedDigit())
                            .foregroundStyle(CrossStitchInputStyle.ink)
                            .frame(width: 12, height: CrossStitchInputStyle.digitH)
                    }
                }
                .offset(y: -Self.position(v, k) * CrossStitchInputStyle.digitH)
                .opacity(k == 0 ? 1 : min(1, max(0, v - pow(10, Double(k)) + 1)))
                .frame(width: 12, height: CrossStitchInputStyle.digitH, alignment: .top)
                .clipped()
            }
        }
    }
}

/// The thread's name, rolling to the chosen one.
struct CrossStitchInputThreadName: View {
    var position: Double
    var body: some View {
        ZStack(alignment: .topTrailing) {
            ForEach(Array(CrossStitchInputArt.threads.enumerated()), id: \.offset) { i, th in
                let d = Double(i) - position
                Text(th.name)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(CrossStitchInputStyle.ink)
                    .frame(height: CrossStitchInputStyle.digitH)
                    .opacity(max(0, 1 - abs(d) * 1.2))
                    .offset(y: d * CrossStitchInputStyle.digitH)
            }
        }
        .frame(width: 96, height: CrossStitchInputStyle.digitH, alignment: .topTrailing)
        .clipped()
    }
}
