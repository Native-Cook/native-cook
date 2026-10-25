import SwiftUI
import UIKit

/*
 Magnet Snap Dock — "Studio", a drawing app whose tool dock is magnetic. Bring a finger near it
 and the six enamel tool tiles lean toward it, the nearest growing to 1.35× while its neighbours
 make room; dashed field lines flow from the tiles to the fingertip. Let go over a tool and it
 clicks down onto the dock's plate (a spring with a little overshoot), the plate slides under it
 and the panel above swaps to that tool's settings. Move away and the tiles spring home.

 Two layers: a static image under the panel (backdrop and artboard, rendered once) and a live
 Canvas over it (dock, plate, tiles, field lines, fingertip). The dock and the six tile faces are
 rendered once; per frame: positions, shadows and up to 18 dashed curves. autoPlay: loop = 8 s,
 analytic (MagnetSnapDockMotion).

 Modes: 0 loop · 1 finger down · 2 released (tiles spring home) · 3 back to Pen and the loop
 (2 s after the last touch, 0.9 s).
 */
struct MagnetSnapDock: View {
    /// Plays the 8 s demo: approach, sweep the dock, pick Brush, then tap Pen again.
    var autoPlay = false

    static let loop = MagnetSnapDockMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = MagnetSnapDockEngine()
    /// Keeps the timeline running while tiles, plate or panel still move (outside autoPlay).
    @State private var active = false

    fileprivate static let background = Color(hex: 0x16181C)
    fileprivate static let ink = Color(hex: 0xF1EDE6)
    fileprivate static let inkSoft = Color(hex: 0x9A9CA3)

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = MagnetSnapDockGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let art = engine.art(for: g, scale: displayScale)
        return TimelineView(.animation(paused: !auto && !active)) { timeline in
            let f = engine.step(to: timeline.date, g: g, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                art.backdrop
                    .resizable()
                    .frame(width: size.width, height: size.height)
                    .allowsHitTesting(false)

                header
                    .frame(width: size.width - 40)
                    .offset(x: 20, y: insets.top + 12)

                MagnetSnapDockPanel(sel: f.sel, clock: f.clock)
                    .frame(width: size.width - 32, height: g.panelH)
                    .offset(x: 16, y: g.panelTop)

                Canvas { c, _ in
                    var c = c
                    c.translateBy(x: 0, y: -g.boardTop)
                    draw(c, g: g, art: art, f: f, reduced: reduced)
                }
                .frame(width: size.width, height: max(0, size.height - g.boardTop))
                .contentShape(Rectangle())
                .gesture(touch(g: g, reduced: reduced))
                .offset(y: g.boardTop)
                .accessibilityElement()
                .accessibilityAddTraits(.allowsDirectInteraction)
                .accessibilityLabel("Tool dock, \(MagnetSnapDockPanel.toolNames[f.sel.to]) selected. Drag near the dock and let go on a tool.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .coordinateSpace(name: "dock")
            .onChange(of: f.idle) { _, idle in
                if idle { active = false }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func draw(_ c: GraphicsContext, g: MagnetSnapDockGeo, art: MagnetSnapDockArt.Images, f: MagnetSnapDockFrame, reduced: Bool) {
        let M = MagnetSnapDockMotion.self
        c.draw(art.dock, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
        let s = f.sel
        MagnetSnapDockArt.drawPlate(c, g, reduced ? g.xs[s.to] : M.plateX(g, s, f.clock), MagnetSnapDockArt.toolColors[s.to])
        var t = f.tiles
        let click = reduced ? (0.0, 1.0) : M.clickAt(f.clock - s.at)
        for i in 0..<M.tools {
            if i == s.to {
                t.y[i] += click.0
                t.scale[i] *= click.1
            }
            MagnetSnapDockArt.drawTileShadow(c, t.x[i], t.y[i], t.scale[i], max(0, g.cy - t.y[i]) + (t.scale[i] - 1) * 24)
        }
        MagnetSnapDockArt.drawField(c, t, f.finger.0, f.finger.1, f.phase)
        let side = M.icon * 2
        for i in 0..<M.tools {
            let size = side * t.scale[i]
            c.draw(art.faces[i], in: CGRect(x: t.x[i] - size / 2, y: t.y[i] - size / 2, width: size, height: size))
        }
        MagnetSnapDockArt.drawFinger(c, f.finger.0, f.finger.1, f.finger.2)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 3) {
                Text("STUDIO")
                    .font(.system(size: 11, weight: .heavy))
                    .kerning(2.4)
                    .foregroundStyle(Color(hex: 0xC4532F))
                Text("Harbor study")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("Autosaved 2 min ago · 6 layers")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Self.inkSoft)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .allowsHitTesting(false)
            Button {} label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(.white.opacity(0.08)))
                    .overlay(
                        Circle().strokeBorder(
                            LinearGradient(colors: [.white.opacity(0.08), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)),
                            lineWidth: 1
                        )
                    )
                    .contentShape(Circle())
            }
            .buttonStyle(MagnetSnapDockPressStyle())
            .accessibilityLabel("Share")
            .padding(.top, 14)
        }
    }

    private func touch(g: MagnetSnapDockGeo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("dock"))
            .onChanged { value in
                active = true
                engine.touch(at: value.location, g: g)
            }
            .onEnded { value in
                engine.release(at: value.location, g: g)
            }
    }
}

private struct MagnetSnapDockPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// What one frame shows.
struct MagnetSnapDockFrame {
    var tiles: MagnetSnapDockTiles
    /// x, y, ring alpha.
    var finger: (Double, Double, Double)
    var sel: MagnetSnapDockSelection
    /// The clock the selection is timed on (loop time in autoPlay, real time under touch).
    var clock: Double
    var phase: Double
    /// Everything has come to rest and no finger is down (lets the timeline pause outside autoPlay).
    var idle: Bool
}

/// The tiles, the finger, the selection and the autoPlay clock, stepped once a frame.
@MainActor
final class MagnetSnapDockEngine {
    private typealias M = MagnetSnapDockMotion

    private static let returnAfter = 2.0
    private static let returnFor = 0.9
    private static let springW = 16.0
    private static let springZ = 0.72

    private var images: MagnetSnapDockArt.Images?
    private var imagesKey: (MagnetSnapDockGeo, Double)?

    private var now = 0.0
    private var last: Double?
    private var loopStart: Double?
    private var mode: Int?
    /// Finger: x, y, strength, target strength.
    private var finger = (0.0, 0.0, 0.0, 0.0)
    /// Tiles under touch, and their velocities.
    private var tiles: MagnetSnapDockTiles?
    private var vel = MagnetSnapDockTiles(zero: true)
    private var sel = MagnetSnapDockSelection(from: M.restTool, to: M.restTool, at: -10)
    private var touching = false
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var nearest = -1
    private var lastFrame: MagnetSnapDockFrame?

    private static let tick = UISelectionFeedbackGenerator()
    private static let snap = UIImpactFeedbackGenerator(style: .light)

    func art(for g: MagnetSnapDockGeo, scale: Double) -> MagnetSnapDockArt.Images {
        if let images, let key = imagesKey, key.0 == g, key.1 == scale { return images }
        let made = MagnetSnapDockArt.render(g, scale: scale)
        images = made
        imagesKey = (g, scale)
        return made
    }

    func step(to date: Date, g: MagnetSnapDockGeo, auto: Bool, reduced: Bool) -> MagnetSnapDockFrame {
        let t0 = date.timeIntervalSinceReferenceDate
        if let last, t0 <= last, let lastFrame { return lastFrame }
        let dt = min(last.map { t0 - $0 } ?? 1 / 60, 1 / 30)
        last = t0
        now = t0
        if loopStart == nil { loopStart = t0 }
        if mode == nil { mode = auto ? 0 : 2 }

        if mode == 0 {
            let t = (t0 - (loopStart ?? t0)).truncatingRemainder(dividingBy: M.loop)
            let (l, fp) = M.loopLayout(g, t)
            sel = M.loopSelection(t)
            let frame = MagnetSnapDockFrame(
                tiles: l, finger: (fp.0, fp.1, fp.3), sel: sel, clock: t,
                phase: -(t * 36).truncatingRemainder(dividingBy: 12), idle: false
            )
            lastFrame = frame
            return frame
        }
        // Touch: the finger's presence eases in and out; the tiles ride springs to the magnet layout.
        finger.2 += (finger.3 - finger.2) * (1 - exp(-dt / 0.08))
        let target = M.layout(g, finger.0, finger.1, reduced ? 0 : finger.2)
        var st: MagnetSnapDockTiles
        if let tiles {
            st = tiles
        } else {
            st = MagnetSnapDockTiles()
            st.x = g.xs
            st.y = [Double](repeating: g.cy, count: M.tools)
            vel = MagnetSnapDockTiles(zero: true)
        }
        let w = Self.springW, z = Self.springZ
        let h = dt / 2
        var moving = false
        for _ in 0..<2 {
            for i in 0..<M.tools {
                vel.x[i] += (-w * w * (st.x[i] - target.x[i]) - 2 * z * w * vel.x[i]) * h
                st.x[i] += vel.x[i] * h
                vel.y[i] += (-w * w * (st.y[i] - target.y[i]) - 2 * z * w * vel.y[i]) * h
                st.y[i] += vel.y[i] * h
                vel.scale[i] += (-w * w * (st.scale[i] - target.scale[i]) - 2 * z * w * vel.scale[i]) * h
                st.scale[i] += vel.scale[i] * h
            }
        }
        for i in 0..<M.tools where abs(st.x[i] - target.x[i]) > 0.05 || abs(st.y[i] - target.y[i]) > 0.05 || abs(vel.x[i]) > 0.5 || abs(st.scale[i] - target.scale[i]) > 0.001 {
            moving = true
        }
        st.pull = target.pull
        tiles = st
        if mode == 2 && auto && t0 - lastTouch > Self.returnAfter {
            mode = 3
            returnAt = t0
            if sel.to != M.restTool { sel = MagnetSnapDockSelection(from: sel.to, to: M.restTool, at: t0) }
        } else if mode == 3 && t0 - returnAt > Self.returnFor {
            mode = 0
            loopStart = t0
            tiles = nil
        }
        let settled = t0 - sel.at > 1.6 && finger.2 < 0.005 && !moving
        let frame = MagnetSnapDockFrame(
            tiles: st, finger: (finger.0, finger.1, finger.2), sel: sel, clock: t0,
            phase: -(t0 * 36).truncatingRemainder(dividingBy: 12),
            idle: !auto && mode == 2 && !touching && settled
        )
        lastFrame = frame
        return frame
    }

    private func nearestTile(_ g: MagnetSnapDockGeo, _ x: Double) -> Int {
        var best = 0
        for i in 1..<M.tools where abs(g.xs[i] - x) < abs(g.xs[best] - x) { best = i }
        return best
    }

    private func takeOver() {
        guard mode == 0, let f = lastFrame else { return }
        tiles = f.tiles
        vel = MagnetSnapDockTiles(zero: true)
        let t = (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)
        let s = M.loopSelection(t)
        sel = MagnetSnapDockSelection(from: s.from, to: s.to, at: now - (t - s.at))
        finger = (f.finger.0, f.finger.1, f.finger.2, 0)
    }

    func touch(at p: CGPoint, g: MagnetSnapDockGeo) {
        if !touching {
            touching = true
            takeOver()
            mode = 1
            finger = (p.x, p.y, finger.2, 1)
            nearest = nearestTile(g, p.x)
            lastTouch = now
            return
        }
        finger = (p.x, p.y, finger.2, 1)
        let n = nearestTile(g, p.x)
        if n != nearest && p.y > g.cy - 170 {
            nearest = n
            Self.tick.selectionChanged()
        }
        lastTouch = now
    }

    func release(at p: CGPoint, g: MagnetSnapDockGeo) {
        touching = false
        guard mode == 1 else { return }
        let n = nearestTile(g, p.x)
        let cx = tiles?.x[n] ?? g.xs[n]
        let cy = tiles?.y[n] ?? g.cy
        let reach = 30 * (tiles?.scale[n] ?? 1)
        if abs(p.x - cx) < reach && abs(p.y - cy) < reach + 8 && n != sel.to {
            sel = MagnetSnapDockSelection(from: sel.to, to: n, at: now)
            Self.snap.impactOccurred()
        }
        finger.3 = 0
        mode = 2
        lastTouch = now
    }
}
