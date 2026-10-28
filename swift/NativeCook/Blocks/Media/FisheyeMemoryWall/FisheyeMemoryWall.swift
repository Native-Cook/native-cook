import SwiftUI
import UIKit

/*
 Fisheye Memory Wall — the year view of an invented journal app ("Keepsake"): "2026 in moments".

 Look: an endless dark wall of small rounded tiles, each a tiny memory drawn in code (a pan of
 shakshuka, a sunset over the sea, a ridge, a flat white, a birthday cake, a skyline at night, a
 tulip, a beach umbrella, a hot-air balloon, a lemon tart, a tent under the stars, gradients and date
 cards), seen through a strong fisheye lens that fills the screen: tiles in the middle are 1.6 times
 their size, tiles near the rim shrink and curve away, and the rim falls into a dark vignette.

 Hero: the bulge. One Metal shader traces every point back to the wall through a barrel function and
 samples a pre-rendered atlas of one wall period (repeat addressing), so the wall never ends and the
 tiles stay crisp. As the wall pans, tiles swell through the middle and squeeze away at the rim. The
 caption card names the memory under the centre; its text fades out in the gutters between tiles.

 autoPlay: loop = 9 s. Rest 0–1 s on the birthday cake. 1.0–7.6 s the wall pans one whole period
 sideways (easeInOutCubic) with an S-shaped bob (90 pt · sin 2πe); the bulge grows from k 1 to 1.42
 (1.0–3.0 s) and relaxes back (5.6–8.2 s). One period on, the wall is where it began, so frame P is
 frame 0.
 Touch: drag to wander (the centre tile follows the finger 1 : 1, a throw keeps momentum), pinch to
 strengthen or relax the bulge. A light tick when a new memory reaches the centre. 2 s after the last
 touch the wall eases to the nearest copy of rest (1.2 s) and the loop restarts.
 Reduced motion: the still rest frame (the lens stays); drag still pans, no loop.
*/
struct FisheyeMemoryWall: View {
    /// Plays the 9 s pan-and-bulge loop until touched.
    var autoPlay = false

    typealias M = FisheyeMemoryWallModel
    static let loop = FisheyeMemoryWallModel.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var driver = FisheyeMemoryWallDriver()
    @State private var live = false
    @State private var token = 0
    @State private var dragging = false
    @State private var pinching = false

    static let background: UInt32 = 0x07090D
    static let ink: UInt32 = 0xF5F3EE
    static let accent: UInt32 = 0xFF9A6B

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.color(Self.background).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let atlas = FisheyeMemoryWallAtlas.shared
        let scale = max(1, displayScale)
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let pose = driver.pose(at: now, auto: auto)
            Canvas { c, s in
                Self.draw(&c, size: s, top: insets.top, bottom: insets.bottom, pose: pose, atlas: atlas, scale: scale)
            }
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(drag(auto: auto).simultaneously(with: pinch(auto: auto)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("2026 in moments. A wall of memories under a fisheye lens. Drag to wander, pinch to change the bulge.")
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Colours and text

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static func text(_ s: String, _ size: CGFloat, _ weight: Font.Weight, _ color: Color, tracking: CGFloat = 0) -> Text {
        Text(s).font(.system(size: size, weight: weight)).tracking(tracking).foregroundColor(color)
    }

    // MARK: Drawing

    static func draw(_ c: inout GraphicsContext, size: CGSize, top: Double, bottom: Double,
                     pose: FisheyeMemoryWallPose, atlas: Image, scale: Double) {
        let w = size.width
        let h = size.height
        let full = CGRect(origin: .zero, size: size)
        // The memory under the centre gets a focus ring; it dims while the centre crosses a gutter.
        let m = M.memory(atX: M.restX + pose.x, y: M.restY + pose.y)
        let r = M.tiles[m.tile].rect
        let ring = pose.ring * M.clamp((m.depth + 2.5) / 8)
        let shader = ShaderLibrary.FisheyeMemoryWallLens(
            .image(atlas),
            .float4(w / 2, h / 2, w * 0.53, h * 0.5),
            .float4(M.wrap(M.restX + pose.x, M.periodW), M.wrap(M.restY + pose.y, M.periodH), pose.k,
                    M.atlasScale / scale),
            .float4(M.periodW, M.periodH, 1 / scale, 0),
            .float4(r.minX, r.minY, r.width, r.height),
            .float2(M.tiles[m.tile].radius, ring),
            .color(color(background)))
        c.fill(Path(full), with: .shader(shader, bounds: full))

        // Scrims keep the header and the caption readable over the rim.
        let bg = color(background)
        c.fill(Path(CGRect(x: 0, y: 0, width: w, height: top + 170)),
               with: .linearGradient(Gradient(stops: [.init(color: bg.opacity(0.94), location: 0),
                                                      .init(color: bg.opacity(0.7), location: 0.45),
                                                      .init(color: bg.opacity(0), location: 1)]),
                                     startPoint: .zero, endPoint: CGPoint(x: 0, y: top + 170)))
        c.fill(Path(CGRect(x: 0, y: h - bottom - 210, width: w, height: bottom + 210)),
               with: .linearGradient(Gradient(stops: [.init(color: bg.opacity(0), location: 0),
                                                      .init(color: bg.opacity(0.75), location: 0.5),
                                                      .init(color: bg.opacity(0.94), location: 1)]),
                                     startPoint: CGPoint(x: 0, y: h - bottom - 210), endPoint: CGPoint(x: 0, y: h)))
        header(&c, w: w, top: top)
        caption(&c, w: w, h: h, bottom: bottom, pose: pose)
    }

    static func header(_ c: inout GraphicsContext, w: Double, top: Double) {
        let y = top + 12
        c.draw(text("KEEPSAKE", 11, .semibold, color(ink, 0.55), tracking: 2.2), at: CGPoint(x: 24, y: y + 8), anchor: .leading)
        c.draw(text("2026 in moments", 30, .bold, color(ink), tracking: -0.6), at: CGPoint(x: 24, y: y + 38), anchor: .leading)
        c.draw(text("1,284 memories · 212 days", 13, .medium, color(ink, 0.6)), at: CGPoint(x: 24, y: y + 66), anchor: .leading)
    }

    static func caption(_ c: inout GraphicsContext, w: Double, h: Double, bottom: Double, pose: FisheyeMemoryWallPose) {
        let card = CGRect(x: 20, y: h - max(bottom, 16) - 46 - 64, width: w - 40, height: 64)
        let shape = Path(roundedRect: card, cornerRadius: 22, style: .continuous)
        var shadow = c
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(shape.offsetBy(dx: 0, dy: 8), with: .color(.black.opacity(0.35)))
        c.fill(shape, with: .color(color(0x0F131A, 0.82)))
        c.stroke(shape, with: .linearGradient(Gradient(colors: [.white.opacity(0.16), .white.opacity(0.04)]),
                                              startPoint: CGPoint(x: 0, y: card.minY), endPoint: CGPoint(x: 0, y: card.maxY)),
                 lineWidth: 1)
        // The memory under the centre; its text dims while the centre crosses a gutter.
        let m = M.memory(atX: M.restX + pose.x, y: M.restY + pose.y)
        let tile = M.tiles[m.tile]
        let a = 0.3 + 0.7 * M.clamp((m.depth + 2.5) / 8)
        let (title, place) = M.captions[tile.design]
        c.draw(text(M.months[tile.month], 10, .bold, color(accent, a), tracking: 1.4),
               at: CGPoint(x: card.minX + 36, y: card.minY + 21), anchor: .center)
        c.draw(text("\(tile.day)", 22, .bold, color(ink, a)), at: CGPoint(x: card.minX + 36, y: card.minY + 41), anchor: .center)
        c.fill(Path(CGRect(x: card.minX + 70, y: card.minY + 14, width: 1, height: 36)), with: .color(.white.opacity(0.12)))
        c.draw(text(title, 16, .semibold, color(ink, a)), at: CGPoint(x: card.minX + 86, y: card.minY + 24), anchor: .leading)
        c.draw(text(place, 12, .medium, color(ink, 0.55 * a)), at: CGPoint(x: card.minX + 86, y: card.minY + 44), anchor: .leading)
        // An open chevron.
        var chevron = Path()
        chevron.move(to: CGPoint(x: card.maxX - 26, y: card.midY - 6))
        chevron.addLine(to: CGPoint(x: card.maxX - 20, y: card.midY))
        chevron.addLine(to: CGPoint(x: card.maxX - 26, y: card.midY + 6))
        c.stroke(chevron, with: .color(color(ink, 0.45)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
        c.draw(text("Drag to wander · pinch to bulge", 11, .medium, color(ink, 0.4)),
               at: CGPoint(x: w / 2, y: card.maxY + 18), anchor: .center)
    }

    // MARK: Touch

    private func drag(auto: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if !dragging {
                    dragging = true
                    driver.beginDrag(at: now, auto: auto)
                    bump()
                }
                if driver.drag(dx: v.translation.width, dy: v.translation.height) { haptic() }
            }
            .onEnded { v in
                dragging = false
                driver.endDrag(vx: v.velocity.width, vy: v.velocity.height, at: Date().timeIntervalSinceReferenceDate)
                bump()
            }
    }

    private func pinch(auto: Bool) -> some Gesture {
        MagnifyGesture()
            .onChanged { v in
                if !pinching {
                    pinching = true
                    driver.beginPinch(at: Date().timeIntervalSinceReferenceDate, auto: auto)
                    bump()
                }
                driver.pinch(v.magnification)
            }
            .onEnded { _ in
                pinching = false
                driver.endPinch(at: Date().timeIntervalSinceReferenceDate)
                bump()
            }
    }

    /// Keeps the timeline running while the driver moves on its own, then lets it pause.
    private func bump() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            while true {
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard mine == token else { return }
                if !driver.live(at: Date().timeIntervalSinceReferenceDate) {
                    live = false
                    return
                }
            }
        }
    }

    private func haptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.6)
    }
}
