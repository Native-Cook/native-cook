import SwiftUI
import UIKit

/*
 Dymo Label Input — "Shelfwise · Label the jar". A handheld embossing label maker sits on the counter
 under a pantry shelf. Typing turns its character wheel to each letter (a spin with a detent click),
 the trigger is squeezed and the letter is pressed into glossy red tape as raised, stress-whitened
 plastic; the tape steps along. Return (or Cut) snaps the cutter and the strip flies onto the jar
 of oats, where it sticks with a squash and wraps round the glass.

 Touch: type in the field; or turn the wheel with a finger (detents, a tick each) and tap the
 trigger to press the letter under the head; tape colour chips re-colour the tape (0.25 s). After a
 cut the labelled jar slides away and a fresh one comes in. With autoPlay, 2 s after the last touch
 (field closed, no strip in flight) the scene cross-fades to the loop's first frame (0.8 s).
 Reduced motion: the rest frame, still; typed letters appear pressed at once, the strip lands
 without flying.

 One Canvas. Wall, shelf, jar, body and wheel are baked once at 2x off the main thread; per frame
 the Canvas places them and draws the tape and its letters.
 */
struct DymoLabelInput: View {
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = DymoLabelInputEngine()
    @State private var text = ""
    @State private var tape = 0
    @State private var ring = 0
    @State private var running = true
    @State private var hero: CGRect = .zero
    @State private var fieldBottom: CGFloat = 0
    @State private var art: DymoLabelInputImages?
    @State private var keyboardTop: CGFloat = .infinity
    @State private var drag: DymoLabelInputDrag?
    @FocusState private var focus: Bool

    private typealias S = DymoLabelInputStyle
    private static let space = "dymoLabelInput"

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
        let geo: DymoLabelInputGeo? = hero.width > 0 ? DymoLabelInputGeo(w: size.width, h: size.height, hero: hero) : nil
        let keyboard = keyboardTop.isFinite ? max(0, size.height - keyboardTop) : 0
        let lift = max(0, keyboard - (size.height - fieldBottom - 12))
        return ZStack(alignment: .topLeading) {
            S.background
            TimelineView(.animation(paused: !running)) { timeline in
                let date = timeline.date
                let _ = engine.step(date)
                Canvas { ctx, _ in
                    _ = date
                    guard let geo, let art else { return }
                    DymoLabelInputArt.drawScene(ctx, geo, art, engine.pose())
                    let k = engine.returnK
                    if k > 0 {
                        var l = ctx
                        l.opacity = k
                        l.drawLayer { layer in DymoLabelInputArt.drawScene(layer, geo, art, DymoLabelInputMotion.loopPose(0)) }
                    }
                }
                .frame(width: size.width, height: size.height)
            }
            .allowsHitTesting(false)

            column(insets: insets, geo: geo)
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
            let baked = await Task.detached(priority: .userInitiated) { () -> DymoLabelInputImages? in
                guard let wall = DymoLabelInputBake.wall(geo), let jar = DymoLabelInputBake.jar(geo),
                      let body = DymoLabelInputBake.body(geo), let wheel = DymoLabelInputBake.wheel(geo) else { return nil }
                return DymoLabelInputImages(
                    wall: Image(decorative: wall, scale: 2), jar: Image(decorative: jar, scale: 2),
                    body: Image(decorative: body, scale: 2), wheel: Image(decorative: wheel, scale: 2)
                )
            }.value
            art = baked
            engine.artReady = baked != nil
            wake()
        }
        .onChange(of: focus) { _, f in
            engine.focused = f
            if f { engine.takeOver() }
            wake()
        }
        .task(id: running) {
            // Applies the loop's threshold events (the field's text, the reset) and pauses when idle.
            guard running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(33))
                applyPending()
                if engine.mode == .auto || engine.mode == .still, ring != 0 { ring = 0 }
                if !engine.wantsFrames && engine.artReady {
                    running = false
                    return
                }
            }
        }
    }

    private func applyPending() {
        if let t = engine.pendingText {
            engine.pendingText = nil
            if t != text { text = t }
        }
        if let i = engine.pendingTape {
            engine.pendingTape = nil
            tape = i
        }
    }

    private func wake() {
        if !running { running = true }
    }

    // MARK: Controls

    private func column(insets: EdgeInsets, geo: DymoLabelInputGeo?) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(S.ink)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle().fill(Color(red: 1, green: 252 / 255, blue: 247 / 255, opacity: 0.85))
                            .shadow(color: S.shade.opacity(0.16), radius: 1, y: 1)
                            .shadow(color: S.shade.opacity(0.08), radius: 8, y: 6)
                    )
                Spacer(minLength: 0)
                Text("Shelfwise")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(S.ink)
                Spacer(minLength: 0)
                Text("Pantry · Shelf 2")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(S.muted)
            }
            .frame(height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text("Label the jar")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.4)
                    .foregroundStyle(S.ink)
                    .frame(height: 36)
                Text("Pressed into 9 mm glossy tape")
                    .font(.system(size: 15))
                    .foregroundStyle(S.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 12)

            Color.clear
                .frame(maxWidth: .infinity, minHeight: 280, maxHeight: .infinity)
                .contentShape(Rectangle())
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { hero = $0 }
                .gesture(machineGesture(geo))
                .accessibilityElement()
                .accessibilityLabel("Label maker. Tape reads \(text.isEmpty ? "nothing yet" : text). Turn the wheel and tap the handle to press a letter.")

            HStack(spacing: 12) {
                field
                Button(action: onCut) {
                    Image(systemName: "scissors")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(S.paper)
                        .frame(width: 64, height: 64)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(S.machine)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .strokeBorder(Color.white.opacity(0.18), lineWidth: 1)
                                        .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)))
                                )
                                .shadow(color: Color(red: 20 / 255, green: 30 / 255, blue: 28 / 255, opacity: 0.3), radius: 1, y: 1)
                                .shadow(color: Color(red: 20 / 255, green: 30 / 255, blue: 28 / 255, opacity: 0.16), radius: 10, y: 10)
                        )
                }
                .buttonStyle(DymoLabelInputPress(scale: 0.95))
                .accessibilityLabel("Cut the label")
            }
            .onGeometryChange(for: CGFloat.self) { $0.frame(in: .named(Self.space)).maxY } action: { fieldBottom = $0 }

            HStack(spacing: 0) {
                Text("TAPE")
                    .font(.system(size: 12, weight: .semibold))
                    .kerning(0.4)
                    .foregroundStyle(S.muted)
                Spacer(minLength: 0)
                chips
            }
            .frame(height: 48)
            .padding(.top, 16)

            Button {} label: {
                Text("Save to pantry")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(S.paper)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Capsule().fill(S.ink)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                            .shadow(color: S.ink.opacity(0.25), radius: 2, y: 2)
                            .shadow(color: S.ink.opacity(0.18), radius: 12, y: 12)
                    )
            }
            .buttonStyle(DymoLabelInputPress(scale: 0.97))
            .padding(.top, 16)
        }
        .padding(.horizontal, 20)
        .padding(.top, insets.top + 6)
        .padding(.bottom, insets.bottom + 12)
    }

    private var field: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Label text")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(S.muted)
            TextField(
                "",
                text: Binding(get: { text }, set: { onType($0) }),
                prompt: Text("TYPE A LABEL").foregroundStyle(Color(red: 0xBD / 255, green: 0xAF / 255, blue: 0x9E / 255))
            )
            .font(.system(size: 20, weight: .bold))
            .kerning(2)
            .foregroundStyle(S.ink)
            .tint(DymoLabelInputArt.col(DymoLabelInputMotion.tapes[tape].rgb))
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled()
            .submitLabel(.done)
            .focused($focus)
            .onSubmit {
                onCut()
                // Return cuts and keeps the keyboard up for the next label.
                focus = true
            }
            .padding(.vertical, 2)
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: 64, alignment: .top)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(S.card)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.9), lineWidth: 1)
                        .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)))
                )
                .shadow(color: S.shade.opacity(0.14), radius: 1, y: 1)
                .shadow(color: S.shade.opacity(0.07), radius: 12, y: 10)
        )
    }

    private var chips: some View {
        HStack(spacing: S.chipGap) {
            ForEach(Array(DymoLabelInputMotion.tapes.enumerated()), id: \.offset) { i, tp in
                Button {
                    pickTape(i)
                } label: {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(DymoLabelInputArt.col(tp.rgb)
                            .shadow(.inner(color: .black.opacity(0.22), radius: 2.5, y: -3))
                            .shadow(.inner(color: .white.opacity(0.3), radius: 1.5, y: 2)))
                        .frame(width: S.chip, height: S.chip)
                        .overlay(alignment: .top) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color.white.opacity(0.32))
                                .frame(height: 4)
                                .padding(.horizontal, 5)
                                .padding(.top, 5)
                        }
                        .shadow(color: Color(red: 30 / 255, green: 20 / 255, blue: 10 / 255, opacity: 0.3), radius: 1, y: 1)
                        .shadow(color: Color(red: 30 / 255, green: 20 / 255, blue: 10 / 255, opacity: 0.14), radius: 5, y: 4)
                        .padding(-6)
                        .contentShape(Rectangle())
                        .padding(6)
                }
                .buttonStyle(DymoLabelInputPress(scale: 0.94))
                .accessibilityLabel("\(tp.name) tape")
                .accessibilityAddTraits(tape == i ? .isSelected : [])
            }
        }
        .overlay(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(S.ink, lineWidth: 2)
                .frame(width: S.chip + 10, height: S.chip + 10)
                .offset(x: -5 + CGFloat(ring) * (S.chip + S.chipGap), y: -5)
                .animation(reduceMotion ? nil : .spring(response: 0.4, dampingFraction: 0.75), value: ring)
                .allowsHitTesting(false)
        }
    }

    // MARK: Gestures

    /// Turn the wheel with a finger; tap the trigger (anywhere on the machine's handle) to press.
    private func machineGesture(_ geo: DymoLabelInputGeo?) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
            .onChanged { v in
                guard let geo else { return }
                if drag == nil {
                    drag = DymoLabelInputDrag(last: v.startLocation)
                    engine.touchDown()
                    wake()
                }
                guard var d = drag else { return }
                let dist = hypot(v.translation.width, v.translation.height)
                if !d.turning && dist >= 3 { d.turning = true }
                if d.turning {
                    let change = CGSize(width: v.location.x - d.last.x, height: v.location.y - d.last.y)
                    if engine.turn(at: v.location, by: change, geo: geo) {
                        UISelectionFeedbackGenerator().selectionChanged()
                    }
                }
                d.last = v.location
                drag = d
            }
            .onEnded { v in
                guard let geo else { return }
                let d = drag ?? DymoLabelInputDrag(last: v.startLocation)
                drag = nil
                engine.release(at: v.location, velocity: v.velocity, geo: geo, turned: d.turning)
                let dist = hypot(v.translation.width, v.translation.height)
                if !d.turning && dist < 8 {
                    let x = v.location.x, y = v.location.y
                    // The handle and trigger: right of the wheel, below the tape.
                    if x > geo.wheelX + geo.wheelR * 0.4 && y > geo.wheelY - geo.wheelR * 0.5 && y < geo.wheelY + geo.wheelR * 1.1 {
                        engine.takeOver()
                        if let t = engine.pressTrigger() { text = t }
                        applyPending()
                    }
                }
                wake()
            }
    }

    // MARK: Actions

    private func onType(_ value: String) {
        text = engine.type(value)
        wake()
    }

    private func onCut() {
        if engine.cut() { text = "" }
        wake()
    }

    private func pickTape(_ i: Int) {
        engine.pickTape(i)
        applyPending()
        tape = i
        ring = i
        UISelectionFeedbackGenerator().selectionChanged()
        wake()
    }
}

struct DymoLabelInputDrag {
    var last: CGPoint
    var turning = false
}

enum DymoLabelInputStyle {
    static let ink = Color(red: 0x2B / 255, green: 0x27 / 255, blue: 0x22 / 255)
    static let muted = Color(red: 0x85 / 255, green: 0x78 / 255, blue: 0x67 / 255)
    static let card = Color(red: 0xFF / 255, green: 0xFC / 255, blue: 0xF7 / 255)
    static let paper = Color(red: 0xF7 / 255, green: 0xF1 / 255, blue: 0xE8 / 255)
    static let machine = Color(red: 0x3B / 255, green: 0x57 / 255, blue: 0x52 / 255)
    static let shade = Color(red: 60 / 255, green: 40 / 255, blue: 20 / 255)
    static let background = Color(red: 0xED / 255, green: 0xE4 / 255, blue: 0xD7 / 255)
    static let chip: CGFloat = 36
    static let chipGap: CGFloat = 12
}

struct DymoLabelInputPress: ButtonStyle {
    var scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
    }
}
