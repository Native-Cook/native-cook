import SwiftUI
import UIKit

/*
 Slot Reel List — "Supper Slots", a spin-for-dinner picker. Three drums (mains, sides, drinks) turn
 behind a cherry-enamel cabinet's window; each is a list wrapped round a cylinder, so its rows
 squeeze and darken as they turn away, and blur with speed. Drag a drum to scroll it (it snaps to a
 row with a bounce); pull the chrome lever to spin all three: they stop one after another with a
 clunk, and the winning row lights up with a sweep while the card below rolls to the new supper.

 autoPlay (loop = 9 s, SlotReelListMotion): rest 0–1.3 · lever 1.3–1.9 · spin 1.9–4.8 (stops 3.6,
 4.1, 4.6) · win sweep 4.8–5.8 · hold 5.8–7.0 · drums roll back to their resting rows 7.0–8.4 ·
 rest. A touch takes over at once; 2 s after the last one the drums roll back (0.9 s) and the loop
 starts again. Reduced motion: no loop, spins land at once, no sweep.

 Drawing: a baked back image, one Metal layerEffect for the three drums (reading the baked strip),
 a baked front image (shadows, glass, payline), and the lever (a few shapes in a Canvas).
*/
struct SlotReelList: View {
    /// Pulls the lever, spins to a supper, lights the win, rolls back and starts over.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = SlotReelListEngine()
    @State private var art: SlotReelListArt?
    @State private var artKey = ""
    @State private var rows = SlotReelListMotion.restRows
    @State private var awake = false
    @State private var lastDragY = 0.0
    @GestureState private var leverDown = false
    @GestureState private var reelDown = false

    private typealias I = SlotReelListInk

    var body: some View {
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
        .background(I.wall.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let L = SlotReelListLayout(W: size.width, H: size.height, top: insets.top, bottom: insets.bottom)
        let key = "\(Int(size.width))x\(Int(size.height))-\(Int(insets.top))-\(Int(insets.bottom))"
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            if let art {
                art.back
                    .resizable()
                    .frame(width: L.W, height: L.H)

                TimelineView(.animation(paused: !(auto || awake))) { timeline in
                    let f = engine.step(to: timeline.date, auto: auto)
                    ZStack(alignment: .topLeading) {
                        drums(L, art: art, frame: f)
                            .offset(x: L.win.minX, y: L.win.minY)
                        art.front
                            .resizable()
                            .frame(width: L.win.width + SlotReelListLayout.frontPad * 2, height: L.win.height + SlotReelListLayout.frontPad * 2)
                            .offset(x: L.win.minX - SlotReelListLayout.frontPad, y: L.win.minY - SlotReelListLayout.frontPad)
                        Canvas { ctx, _ in drawLever(ctx, L, angle: f.lever) }
                            .frame(width: L.W, height: L.H)
                    }
                    .frame(width: L.W, height: L.H, alignment: .topLeading)
                }
                .allowsHitTesting(false)
            }

            Color.clear
                .frame(width: L.win.width, height: L.win.height)
                .contentShape(Rectangle())
                .gesture(reelGesture(L))
                .offset(x: L.win.minX, y: L.win.minY)
                .accessibilityLabel("Reels: drag one to scroll it")
                .onChange(of: reelDown) { _, down in
                    if !down { DispatchQueue.main.async { if engine.dragging >= 0 { engine.reelEnded(velocityY: 0); wake() } } }
                }

            Color.clear
                .frame(width: 56, height: L.arm * 2 + 60)
                .contentShape(Rectangle())
                .gesture(leverGesture(L))
                .offset(x: L.W - 56, y: L.pivot.y - L.arm - 30)
                .accessibilityLabel("Lever: pull down to spin")
                .onChange(of: leverDown) { _, down in
                    if !down { DispatchQueue.main.async { if engine.leverHeld { engine.leverEnded(); wake() } } }
                }

            header
                .frame(width: L.W - 44, alignment: .leading)
                .offset(x: 22, y: insets.top + 14)
                .allowsHitTesting(false)

            card
                .frame(width: L.W - 28, height: max(0, L.H - insets.bottom - 12 - L.cardY))
                .offset(x: 14, y: L.cardY)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .task(id: key) {
            engine.reduced = reduceMotion
            engine.rowH = L.rowH
            engine.onRows = { next in
                DispatchQueue.main.async {
                    if reduceMotion { rows = next } else { withAnimation(.linear(duration: 0.32)) { rows = next } }
                }
            }
            engine.onFeel = { kind in
                switch kind {
                case 0: UISelectionFeedbackGenerator().selectionChanged()
                case 1: UIImpactFeedbackGenerator(style: .light).impactOccurred()
                default: UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
            }
            if artKey == key, art != nil { return }
            let px = min(3, Double(displayScale))
            let bitmaps = await Task.detached(priority: .userInitiated) { SlotReelListArt.bake(L, px: px) }.value
            art = SlotReelListArt(bitmaps)
            artKey = key
        }
        .onChange(of: reduceMotion) { _, r in engine.reduced = r }
    }

    // MARK: - Drums

    /// The strip at the layer's top-left; the shader wraps it round the three cylinders.
    private func drums(_ L: SlotReelListLayout, art: SlotReelListArt, frame f: SlotReelListFrame) -> some View {
        let strip = L.stripSize
        let tall = max(L.win.height, strip.height)
        return art.strip
            .resizable()
            .interpolation(.high)
            .frame(width: strip.width, height: strip.height)
            .frame(width: L.win.width, height: tall, alignment: .topLeading)
            .layerEffect(
                ShaderLibrary.SlotReelListDrums(
                    .float2(L.win.width, L.win.height),
                    .float(L.colW),
                    .float(L.gap),
                    .float(L.rowH),
                    .float(L.radius),
                    .float(Double(SlotReelListMotion.rows)),
                    .float3(f.off[0], f.off[1], f.off[2]),
                    .float3(f.blur[0], f.blur[1], f.blur[2]),
                    .float(f.sweep),
                    .float(f.glow)
                ),
                maxSampleOffset: .zero
            )
            .frame(width: L.win.width, height: L.win.height, alignment: .top)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Lever

    /// An arm from the hub to the knob; tipping toward the viewer shortens the arm and swells the
    /// knob (it comes closer).
    private func drawLever(_ ctx: GraphicsContext, _ L: SlotReelListLayout, angle a: Double) {
        let z = max(0, sin(a))
        let ky = L.pivot.y - L.arm * cos(a)
        let r = 15 * (1 + 0.32 * z)
        let w = 9 * (1 + 0.2 * z)
        let px = L.pivot.x
        // Shadow under the knob.
        var shadow = ctx
        shadow.addFilter(.blur(radius: 7))
        let sx = px + 4 + 6 * z
        let sy = ky + 6 + 10 * z
        shadow.fill(Path(ellipseIn: CGRect(x: sx - r, y: sy - r, width: r * 2, height: r * 2)), with: .color(.black.opacity(0.45)))
        // The chrome arm.
        let armY = min(L.pivot.y, ky)
        let armH = abs(ky - L.pivot.y)
        ctx.fill(
            Path(roundedRect: CGRect(x: px - w / 2, y: armY, width: w, height: armH), cornerRadius: min(4, w / 2, armH / 2)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: I.hex(0x5D6368), location: 0), .init(color: I.hex(0xF4F6F7), location: 0.35),
                    .init(color: I.hex(0xB4BABE), location: 0.6), .init(color: I.hex(0x4A4F53), location: 1),
                ]),
                startPoint: CGPoint(x: px - 7, y: 0), endPoint: CGPoint(x: px + 7, y: 0)
            )
        )
        // The cherry knob, lit from up and to the left.
        ctx.fill(
            Path(ellipseIn: CGRect(x: px - r, y: ky - r, width: r * 2, height: r * 2)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: I.hex(0xFFB4A4), location: 0), .init(color: I.hex(0xE2302C), location: 0.3),
                    .init(color: I.hex(0x9C1418), location: 0.75), .init(color: I.hex(0x56080B), location: 1),
                ]),
                center: CGPoint(x: px - r * 0.35, y: ky - r * 0.4), startRadius: 0, endRadius: 30
            )
        )
    }

    // MARK: - Overlays

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("SUPPER SLOTS")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2)
                .foregroundStyle(I.gold)
            Text("Spin for dinner")
                .font(.system(size: 32, weight: .heavy))
                .kerning(-0.5)
                .foregroundStyle(I.cream)
            Text("3 pulls left tonight · Serves 2")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(I.creamSoft)
        }
    }

    private var card: some View {
        let pick = SlotReelListMenu.pick(rows)
        let roll = AnyTransition.offset(y: -25).combined(with: .opacity)
        let insertion = reduceMotion ? AnyTransition.identity : roll.animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.32))
        let removal = reduceMotion ? AnyTransition.identity : roll.animation(.easeOut(duration: 0.22))
        return VStack(alignment: .leading, spacing: 12) {
            Text("TONIGHT'S SUPPER")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(I.creamFaint)
            ZStack(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(pick.main)
                        .font(.system(size: 24, weight: .heavy))
                        .kerning(-0.3)
                        .foregroundStyle(I.cream)
                        .lineLimit(1)
                    Text(pick.line)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(I.creamSoft)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        chip("\(pick.minutes) min")
                        chip("\(pick.kcal) kcal")
                        chip("Serves 2")
                    }
                    .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(pick.key)
                .transition(.asymmetric(insertion: insertion, removal: removal))
            }
            .frame(height: 92, alignment: .topLeading)
            .clipped()
            Spacer(minLength: 0)
            HStack(spacing: 12) {
                Button {} label: {
                    Text("Cook this")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(I.hex(0x2B1708))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Capsule().fill(I.gold).shadow(color: I.hex(0xF0B54A).opacity(0.25), radius: 8, y: 6))
                }
                .buttonStyle(SlotReelListPressStyle())
                Button {} label: {
                    Text("Save combo")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(I.cream)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(Capsule().strokeBorder(I.hex(0xF5EDE0).opacity(0.22), lineWidth: 1))
                        .contentShape(Capsule())
                }
                .buttonStyle(SlotReelListPressStyle())
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(I.card)
                    .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.35), radius: 18, y: 16)
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(
                        LinearGradient(colors: [I.hex(0xFFE6D2).opacity(0.1), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.08)),
                        lineWidth: 1
                    )
            }
        )
    }

    private func chip(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 12, weight: .semibold).monospacedDigit())
            .foregroundStyle(I.cream)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(RoundedRectangle(cornerRadius: 12).fill(I.hex(0xF5EDE0).opacity(0.08)))
    }

    // MARK: - Touch

    private func wake() {
        awake = true
        scheduleSleep()
    }

    /// Outside autoPlay the timeline stops once nothing moves.
    private func scheduleSleep() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            if engine.moving || engine.dragging >= 0 || engine.leverHeld { scheduleSleep() } else { awake = false }
        }
    }

    private func leverGesture(_ L: SlotReelListLayout) -> some Gesture {
        DragGesture(minimumDistance: 2)
            .updating($leverDown) { _, s, _ in s = true }
            .onChanged { v in
                if !engine.leverHeld {
                    engine.leverBegan()
                    wake()
                }
                engine.leverMoved(translationY: v.translation.height, arm: L.arm)
            }
            .onEnded { _ in
                engine.leverEnded()
                wake()
            }
    }

    private func reelGesture(_ L: SlotReelListLayout) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .updating($reelDown) { _, s, _ in s = true }
            .onChanged { v in
                if engine.dragging < 0 {
                    let x0 = v.startLocation.x
                    let c = max(0, min(2, Int(floor(x0 / (L.colW + L.gap)))))
                    engine.reelBegan(column: c)
                    lastDragY = v.translation.height
                    wake()
                }
                engine.reelMoved(deltaY: v.translation.height - lastDragY)
                lastDragY = v.translation.height
            }
            .onEnded { v in
                engine.reelEnded(velocityY: v.velocity.height)
                wake()
            }
    }
}

struct SlotReelListPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

enum SlotReelListInk {
    static func hex(_ v: UInt32) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: 1)
    }

    static let cream = hex(0xF5EDE0)
    static let creamSoft = hex(0xF5EDE0).opacity(0.62)
    static let creamFaint = hex(0xF5EDE0).opacity(0.4)
    static let gold = hex(0xF0B54A)
    static let wall = hex(0x1B120E)
    static let card = hex(0x2A1B16)
}
