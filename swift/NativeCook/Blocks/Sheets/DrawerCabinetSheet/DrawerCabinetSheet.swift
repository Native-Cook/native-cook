import SwiftUI
import UIKit

/*
 Drawer Cabinet Sheet — "Drawers · Office supplies". The bottom sheet is a wooden drawer: an oak
 front with a brass pull that slides out from under the cabinet's top board, and behind it the
 box of supplies — pens in a felt tray, paper clips, binder clips, an eraser, a pencil, tape and
 sticky notes — every one on its own spring, so they rattle and slide as the drawer starts and
 stops. Drag it to half or full (detents, rubber band past full); let go near closed and the
 soft-close damper eases it home. Tap a supply to lift it out onto the desk; tap it there to put
 it back.

 autoPlay (loop = 8 s, DrawerCabinetSheetMotion): rest 0–1.2 · pulled to half 1.2–2.2 · to full
 2.8–3.6 · the blue pen picked 4.0–5.0 · soft close 5.6–7.0 · the desk clears 7.0–7.5 · rest. A
 touch takes over at once; 2 s after the last, the drawer soft-closes, what is on the desk flies
 back (0.9 s) and the loop restarts. Reduced motion: no loop, no rattle; the drawer moves without
 overshoot and picked things appear on the desk.

 Drawing: one Canvas — the desk image, the drawer image (moved, clipped at the board), the
 supplies in the drawer (baked shadow and face images), the board image, then the supplies out
 on the desk. All art is baked off the main thread.
*/
struct DrawerCabinetSheet: View {
    /// Opens the drawer to half and full, picks a pen onto the desk, soft-closes.
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = DrawerCabinetSheetEngine()
    @State private var art: DrawerCabinetSheetImages?
    @State private var artKey = ""
    @State private var chipName = "Blue fineliner"
    @State private var awake = false
    @State private var press = DrawerCabinetSheetPress()
    @GestureState private var pressing = false

    private static let ink = Color(.sRGB, red: 0x2A / 255, green: 0x1E / 255, blue: 0x14 / 255, opacity: 1)
    private static let paper = Color(.sRGB, red: 0xEF / 255, green: 0xE7 / 255, blue: 0xDA / 255, opacity: 1)

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
        .background(Self.paper.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let L = DrawerCabinetSheetLayout(W: size.width, H: size.height, top: insets.top, bottom: insets.bottom)
        let key = "\(Int(size.width))x\(Int(size.height))-\(Int(insets.top))-\(Int(insets.bottom))"
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: art == nil || !(auto || awake))) { timeline in
                let f = engine.step(to: timeline.date, auto: auto)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in draw(ctx, L, f) }
                        .frame(width: L.W, height: L.H)
                    chip(f.chip)
                        .frame(width: L.W - 30, alignment: .trailing)
                        .offset(y: L.matY + 14)
                }
                .frame(width: L.W, height: L.H, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .frame(width: L.W, height: L.H)
                .contentShape(Rectangle())
                .gesture(gesture)
                .onChange(of: pressing) { _, down in
                    if !down { DispatchQueue.main.async { finishPress() } }
                }
                .accessibilityLabel("Supplies drawer. Drag the brass pull to open it; tap a supply to put it on the desk.")

            VStack(alignment: .leading, spacing: 4) {
                Text("DRAWERS")
                    .font(.system(size: 11, weight: .heavy))
                    .kerning(2)
                    .foregroundStyle(Color(.sRGB, red: 0x9A / 255, green: 0x6A / 255, blue: 0x3A / 255, opacity: 1))
                Text("Office supplies")
                    .font(.system(size: 32, weight: .heavy))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("Top drawer · \(engine.items.isEmpty ? 15 : engine.items.count) items · pull to open")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.ink.opacity(0.62))
            }
            .frame(width: L.W - 44, alignment: .leading)
            .offset(x: 22, y: insets.top + 14)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .task(id: key) {
            engine.reduced = reduceMotion
            engine.configure(L)
            engine.onFeel = { kind in
                UIImpactFeedbackGenerator(style: kind == 0 ? .light : .soft).impactOccurred()
            }
            engine.onChip = { name in DispatchQueue.main.async { chipName = name } }
            if artKey == key, art != nil { return }
            let px = min(3, Double(displayScale))
            let items = engine.items
            // Everything is baked off the main thread; the loop starts at 0 once it is in.
            let bitmaps = await Task.detached(priority: .userInitiated) { DrawerCabinetSheetArt.bake(L, items, px: px) }.value
            art = DrawerCabinetSheetImages(bitmaps)
            artKey = key
        }
        .onChange(of: reduceMotion) { _, r in engine.reduced = r }
    }

    // MARK: - Drawing

    private func draw(_ ctx: GraphicsContext, _ L: DrawerCabinetSheetLayout, _ f: DrawerCabinetSheetFrame) {
        guard let art, !engine.items.isEmpty else {
            ctx.fill(Path(CGRect(x: 0, y: 0, width: L.W, height: L.H)), with: .color(Self.paper))
            return
        }
        let items = engine.items
        let pad = DrawerCabinetSheetArt.pad
        ctx.draw(art.desk, in: CGRect(x: 0, y: 0, width: L.W, height: L.H))
        var box = ctx
        box.clip(to: Path(CGRect(x: 0, y: 0, width: L.W, height: L.boardY)))
        box.draw(art.sheet, in: CGRect(
            x: L.sheetX,
            y: L.boardY - DrawerCabinetSheetLayout.panel - DrawerCabinetSheetLayout.shadowPad - f.open,
            width: L.sheetW, height: L.sheetH
        ))
        func put(_ c: GraphicsContext, _ image: Image, _ it: DrawerCabinetSheetItem, _ x: Double, _ y: Double, _ rot: Double, _ scale: Double) {
            var c = c
            c.translateBy(x: x, y: y)
            c.rotate(by: .radians(rot))
            c.scaleBy(x: scale, y: scale)
            let w = it.w + pad * 2
            let h = it.h + pad * 2
            c.draw(image, in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))
        }
        for s in f.inside { put(box, art.shadows[s.i], items[s.i], s.x + 1.5, s.y + 2.5, s.rot, 1) }
        for s in f.inside { put(box, art.faces[s.i], items[s.i], s.x, s.y, s.rot, 1) }
        // Inside the cabinet it is darker: the last few centimetres before the board.
        let darkY = L.boardY - max(0, min(46, f.open))
        box.fill(
            Path(CGRect(x: L.sheetX, y: darkY, width: L.sheetW, height: 46)),
            with: .linearGradient(
                Gradient(colors: [Color(.sRGB, red: 30 / 255, green: 16 / 255, blue: 4 / 255, opacity: 0), Color(.sRGB, red: 30 / 255, green: 16 / 255, blue: 4 / 255, opacity: 0.55)]),
                startPoint: CGPoint(x: 0, y: L.boardY - 46), endPoint: CGPoint(x: 0, y: L.boardY)
            )
        )
        ctx.draw(art.board, in: CGRect(x: 0, y: L.boardY - 20, width: L.W, height: L.H - L.boardY + 20))
        guard !f.out.isEmpty else { return }
        var desk = ctx
        desk.opacity = f.topAlpha
        var shade = desk
        shade.opacity = f.topAlpha * 0.6
        for s in f.out {
            put(shade, art.shadows[s.i], items[s.i], s.x + 3 + 12 * s.lift, s.y + 4 + 22 * s.lift, s.rot, s.scale * (1 + 0.06 * s.lift))
        }
        for s in f.out { put(desk, art.faces[s.i], items[s.i], s.x, s.y, s.rot, s.scale) }
    }

    private func chip(_ c: Double) -> some View {
        Text("\(chipName) · on your desk")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Self.ink)
            .lineLimit(1)
            .padding(.horizontal, 12)
            .frame(maxWidth: 240)
            .frame(height: 30)
            .fixedSize(horizontal: true, vertical: false)
            .background(
                Capsule()
                    .fill(Color(.sRGB, red: 246 / 255, green: 240 / 255, blue: 228 / 255, opacity: 0.94))
                    .shadow(color: Color(.sRGB, red: 20 / 255, green: 30 / 255, blue: 20 / 255, opacity: 0.25), radius: 1, y: 1)
                    .shadow(color: Color(.sRGB, red: 20 / 255, green: 30 / 255, blue: 20 / 255, opacity: 0.18), radius: 7, y: 6)
            )
            .opacity(c)
            .offset(y: 8 * (1 - c))
    }

    // MARK: - Touch

    private func wake() {
        awake = true
        scheduleSleep()
    }

    /// Outside autoPlay the timeline stops once nothing moves.
    private func scheduleSleep() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            if engine.moving || engine.dragging || press.down { scheduleSleep() } else { awake = false }
        }
    }

    /// A pan (moved 6 pt, begun on the drawer) or a tap (under 0.35 s, not moved).
    private var gesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, s, _ in s = true }
            .onChanged { v in
                if !press.down {
                    press = DrawerCabinetSheetPress(down: true, began: Date())
                }
                let dx = v.translation.width
                let dy = v.translation.height
                if !press.moved && dx * dx + dy * dy > 36 {
                    press.moved = true
                    press.panning = engine.dragBegan(atY: v.startLocation.y)
                    if press.panning { wake() }
                }
                if press.panning { engine.dragMoved(translationY: v.translation.height) }
            }
            .onEnded { v in
                if press.panning {
                    engine.dragEnded(velocityY: v.velocity.height)
                } else if !press.moved && Date().timeIntervalSince(press.began) < 0.35 {
                    engine.tap(v.location.x, v.location.y)
                }
                press = DrawerCabinetSheetPress()
                wake()
            }
    }

    private func finishPress() {
        guard press.down else { return }
        if press.panning { engine.dragEnded(velocityY: 0) }
        press = DrawerCabinetSheetPress()
        wake()
    }
}

struct DrawerCabinetSheetPress {
    var down = false
    var moved = false
    var panning = false
    var began = Date.distantPast
}

/// The baked bitmaps as images a Canvas draws.
struct DrawerCabinetSheetImages {
    let desk: Image
    let sheet: Image
    let board: Image
    let faces: [Image]
    let shadows: [Image]

    init(_ b: DrawerCabinetSheetArt.Bitmaps) {
        desk = Image(decorative: b.desk, scale: b.px)
        sheet = Image(decorative: b.sheet, scale: b.px)
        board = Image(decorative: b.board, scale: b.px)
        faces = b.faces.map { Image(decorative: $0, scale: b.px) }
        shadows = b.shadows.map { Image(decorative: $0, scale: b.px) }
    }
}
