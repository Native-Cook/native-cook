import SwiftUI
import UIKit

/*
 Magnetic Letters Name — a family app's "Who's joining?" sign-up whose name field is the fridge
 door. A loose heap of glossy plastic magnet letters lies on the brushed steel; typing (or tapping
 a magnet) flies a letter up to the name line, where the door's pull snaps it on with a little
 bounce; backspace pops the last one off and back onto the heap. Magnets can be dragged anywhere,
 onto the line (in between letters) or off it.

 autoPlay (loop = 9 s): rest 0–1.3 · "NOAH" typed 1.3–3.4 (a flight of 0.38 s every 0.42 s) ·
 "Continue" lights 3.6 · hold 3.6–5.2 · backspace ×4 5.2–7.0 (0.45 s apart) · rest 7.0–9.0.
 A touch takes over at once from where the loop is; 2 s after the last one every magnet flies home
 (≤ 0.9 s) and the loop starts again from 0. Reduced motion: no loop; letters jump to their place.

 Drawing: the door is one image baked off the main thread; the magnets are baked face and shadow
 images that one Canvas places every frame something moves.
*/
struct MagneticLettersName: View {
    /// Spells "NOAH" on the door, lights "Continue", backspaces it away, and starts over.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = MagneticLettersNameEngine()
    @State private var art: MagneticLettersNameArt?
    @State private var artKey = ""
    @State private var text = ""
    @State private var awake = false
    @FocusState private var focused: Bool
    @GestureState private var pressing = false
    @State private var press = MagneticLettersNamePress()

    private typealias D = MagneticLettersNameDoor

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(size: size, insets: insets)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(D.mid.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let key = "\(Int(size.width))x\(Int(size.height))-\(Int(insets.top))-\(Int(insets.bottom))"
        let L = layout(size: size, insets: insets)
        let auto = autoPlay && !reduceMotion
        return TimelineView(.animation(paused: art == nil || !(auto || awake))) { timeline in
            let _ = engine.step(to: timeline.date, auto: auto)
            ZStack(alignment: .topLeading) {
                // Reading the timeline's date makes the canvas redraw every frame; the engine is a
                // reference SwiftUI cannot watch.
                let now = timeline.date
                Canvas { ctx, _ in
                    _ = now
                    draw(ctx, L)
                }
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(drag(L))
                    .accessibilityElement()
                    .accessibilityLabel("Fridge door with magnet letters. Tap a magnet to add it to your name, drag magnets, or type.")
                    .onChange(of: pressing) { _, down in
                        // A drag the system cancels never reaches onEnded.
                        if !down { DispatchQueue.main.async { finishPress() } }
                    }

                header(insets: insets)
                    .frame(width: size.width - 28 - 56, alignment: .leading)
                    .offset(x: 28, y: insets.top + 18)
                    .allowsHitTesting(false)

                HStack {
                    Text("YOUR NAME")
                        .font(.system(size: 11, weight: .bold))
                        .kerning(1.4)
                        .foregroundStyle(D.inkFaint)
                    Spacer(minLength: 0)
                    Text("\(engine.count) / \(engine.maxFit)")
                        .font(.system(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(D.inkFaint)
                }
                .frame(width: L.lineX1 - L.lineX0)
                .offset(x: L.lineX0, y: L.lineY - L.capH - 40)
                .allowsHitTesting(false)

                backspaceKey
                    .offset(x: L.lineX1 + 10, y: L.letterY - 20)

                TextField("", text: $text)
                    .focused($focused)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .keyboardType(.asciiCapable)
                    .submitLabel(.done)
                    .accessibilityLabel("Your name")
                    .frame(width: 2, height: 2)
                    .opacity(0.01)
                    .offset(x: -100, y: 0)
                    .onChange(of: text) { _, next in typed(next) }

                continueButton(ready: engine.ready, enabled: engine.count >= 2)
                    .frame(width: size.width - 48)
                    .offset(x: 24, y: size.height - insets.bottom - 20 - 56)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
        .task(id: key) {
            engine.reduced = reduceMotion
            engine.configure(L)
            engine.onText = { value in
                DispatchQueue.main.async { if text != value { text = value } }
            }
            engine.onSnap = { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            if artKey == key, art != nil { return }
            let px = min(3, Double(displayScale))
            // The door and the magnets are baked off the main thread; the loop starts at 0 once they are in.
            let bitmaps = await Task.detached(priority: .userInitiated) {
                MagneticLettersNameArt.bake(L, px: px)
            }.value
            art = MagneticLettersNameArt(bitmaps)
            artKey = key
        }
        .onChange(of: reduceMotion) { _, r in engine.reduced = r }
    }

    private func layout(size: CGSize, insets: EdgeInsets) -> MagneticLettersNameLayout {
        if let L = engine.L, L.W == size.width, L.H == size.height, L.top == insets.top { return L }
        return MagneticLettersNameLayout(W: size.width, H: size.height, top: insets.top, bottom: insets.bottom)
    }

    // MARK: - Drawing

    private func draw(_ ctx: GraphicsContext, _ L: MagneticLettersNameLayout) {
        guard let art, engine.poses.count == L.magnets.count else {
            ctx.fill(Path(CGRect(x: 0, y: 0, width: L.W, height: L.H)), with: .color(D.mid))
            return
        }
        ctx.draw(art.door, in: CGRect(x: 0, y: 0, width: L.W, height: L.H))
        let order = engine.drawOrder()
        let levels = MagneticLettersNameArt.levels
        let sc = L.shadowCell
        for i in order {
            let p = engine.poses[i]
            let lv = min(levels - 1, Int((p.lift * Double(levels - 1)).rounded()))
            var c = ctx
            c.translateBy(x: p.x + 1.2 + 7 * p.lift, y: p.y + 2 + 13 * p.lift)
            c.rotate(by: .radians(p.rot))
            let k = p.scale * (1 + 0.05 * p.lift)
            c.scaleBy(x: k, y: k)
            c.draw(art.shadowImages[i][lv], in: CGRect(x: -sc / 2, y: -sc / 2, width: sc, height: sc))
        }
        let cell = L.cell
        for i in order {
            let p = engine.poses[i]
            var c = ctx
            c.translateBy(x: p.x, y: p.y)
            c.rotate(by: .radians(p.rot))
            c.scaleBy(x: p.scale, y: p.scale)
            c.draw(art.faceImages[i], in: CGRect(x: -cell / 2, y: -cell / 2, width: cell, height: cell))
        }
    }

    // MARK: - Overlays

    private func header(insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("FRIDGE · MARLOW HOUSEHOLD")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(D.inkFaint)
            Text("Who's joining?")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(D.ink)
            Text("Spell your name on the door. Tap a magnet, drag one, or just type.")
                .font(.system(size: 15, weight: .medium))
                .lineSpacing(2)
                .foregroundStyle(D.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var backspaceKey: some View {
        Button {
            wake()
            engine.backspace()
        } label: {
            Image(systemName: "delete.left")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(D.inkSoft)
                .frame(width: 40, height: 40)
                .background {
                    Circle()
                        .fill(Color(red: 0xEE / 255, green: 0xEF / 255, blue: 0xEC / 255))
                        .overlay(
                            Circle()
                                .strokeBorder(
                                    LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: .center),
                                    lineWidth: 1
                                )
                        )
                        .shadow(color: Color(red: 40 / 255, green: 44 / 255, blue: 48 / 255).opacity(0.22), radius: 1, y: 1)
                        .shadow(color: Color(red: 40 / 255, green: 44 / 255, blue: 48 / 255).opacity(0.12), radius: 7, y: 6)
                }
                .contentShape(Circle().inset(by: -8))
        }
        .buttonStyle(MagneticLettersNameKeyStyle())
        .accessibilityLabel("Delete last letter")
    }

    private func continueButton(ready r: Double, enabled: Bool) -> some View {
        Button {
            if enabled { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        } label: {
            Text("Continue")
                .font(.system(size: 17, weight: .bold))
                .kerning(0.2)
                .foregroundStyle(D.mix(0x8A8F8D, 0xF4F6FB, r))
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(D.mix(0xC9CCCA, 0x2350A8, r))
                        .shadow(color: Color(red: 24 / 255, green: 52 / 255, blue: 110 / 255).opacity(0.08 + 0.3 * r), radius: (6 + 18 * r) / 2, y: 2 + 8 * r)
                )
                .scaleEffect(1 + 0.03 * sin(.pi * min(1, r)))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Touch

    private func wake() {
        awake = true
        scheduleSleep()
    }

    /// Outside autoPlay the timeline stops once nothing moves.
    private func scheduleSleep() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            if engine.moving || press.down { scheduleSleep() } else { awake = false }
        }
    }

    private func drag(_ L: MagneticLettersNameLayout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { v in
                if !press.down {
                    press = MagneticLettersNamePress(down: true, panning: false, began: Date())
                    wake()
                }
                let dx = v.translation.width
                let dy = v.translation.height
                if !press.panning && dx * dx + dy * dy > 36 {
                    press.panning = true
                    engine.grab(v.startLocation.x, v.startLocation.y)
                }
                if press.panning { engine.drag(v.location.x, v.location.y, v.velocity.width) }
            }
            .onEnded { v in
                if press.panning {
                    engine.dropHeld()
                } else if Date().timeIntervalSince(press.began) < 0.4 {
                    switch engine.tap(v.location.x, v.location.y) {
                    case .show: focused = true
                    case .hide: focused = false
                    case .none: break
                    }
                }
                press = MagneticLettersNamePress()
                wake()
            }
    }

    private func finishPress() {
        guard press.down else { return }
        if press.panning { engine.dropHeld() }
        press = MagneticLettersNamePress()
        wake()
    }

    private func typed(_ next: String) {
        let clean = String(next.uppercased().filter { $0 >= "A" && $0 <= "Z" }.prefix(engine.maxFit))
        if clean != next {
            text = clean
            return
        }
        // The engine's own updates of the field come back here; only the keyboard's are applied.
        if clean == engine.nameText { return }
        wake()
        engine.applyText(clean)
    }
}

/// A press on the door: whether it has turned into a drag, and when it began (a tap is < 0.4 s).
struct MagneticLettersNamePress {
    var down = false
    var panning = false
    var began = Date.distantPast
}

struct MagneticLettersNameKeyStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

enum MagneticLettersNameDoor {
    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
    }

    /// Mixes two colours in sRGB (as the RN interpolateColor does).
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        let k = min(max(t, 0), 1)
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 255) / 255 }
        return Color(.sRGB,
                     red: ch(a, 16) + (ch(b, 16) - ch(a, 16)) * k,
                     green: ch(a, 8) + (ch(b, 8) - ch(a, 8)) * k,
                     blue: ch(a, 0) + (ch(b, 0) - ch(a, 0)) * k,
                     opacity: 1)
    }

    static let mid = color(0xE8E9E5)
    static let ink = color(0x22262B)
    static let inkSoft = color(0x22262B, 0.56)
    static let inkFaint = color(0x22262B, 0.34)
}
