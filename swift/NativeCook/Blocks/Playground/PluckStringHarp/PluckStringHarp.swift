import SwiftUI
import UIKit

/// A harp of eight glowing strings on a carved wooden frame, in a dark room. Drag across the
/// strings to pluck them: each one is caught by the finger, pulled aside, and slips free into a
/// ringing standing wave whose glow brightens with its swing. Neighbours shimmer in sympathy, note
/// names float up and fade, and a quick strum leaves a trail of light.
struct PluckStringHarp: View {
    /// Plays an arpeggio, a strum down and a strum up, on a loop. Off by default.
    var autoPlay = false
    /// The small line above the harp, and the hint below it.
    var title = "Eight strings · C major"
    var hint = "Drag across the strings"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PluckStringHarpEngine()

    private typealias C = PluckStringHarpPalette

    var body: some View {
        // The block draws under the system bars and keeps its parts clear of them itself.
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
        .background(Color(hex: C.room[2]).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = PluckStringHarpDrawing.layout(
            width: size.width,
            height: size.height,
            insetTop: insets.top,
            insetBottom: insets.bottom
        )
        let auto = autoPlay && !reduceMotion
        let still = reduceMotion
        return ZStack(alignment: .topLeading) {
            // Everything that never moves, drawn once.
            Canvas { ctx, canvasSize in
                PluckStringHarpDrawing.drawFrame(&ctx, layout, size: canvasSize)
            }

            TimelineView(.animation) { timeline in
                let sim = engine.step(to: timeline.date, geometry: layout.geometry, looping: auto, still: still)
                // The sim is one object mutated in place: its clock is captured too, so SwiftUI sees
                // a new canvas every frame and redraws it.
                let time = sim.time
                Canvas { ctx, _ in
                    _ = time
                    PluckStringHarpLive.draw(&ctx, sim: sim, layout: layout, still: still)
                }
            }

            Text(title.uppercased())
                .font(.system(size: 12, weight: .semibold))
                .kerning(2.4)
                .foregroundStyle(Color(hex: C.label))
                .frame(width: size.width)
                .offset(y: insets.top + 22)

            Text(hint)
                .font(.custom("Georgia-Italic", fixedSize: 15))
                .foregroundStyle(Color(hex: C.label))
                .opacity(0.75)
                .frame(width: size.width, height: size.height - insets.bottom - 26, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touch($0.location) }
                .onEnded { _ in engine.lift() }
        )
        .accessibilityElement()
        .accessibilityLabel("A harp with eight strings. Drag across the strings to play.")
    }
}

/// Owns the simulation, steps it against the display clock and answers a finger's plucks with a tick.
@MainActor
final class PluckStringHarpEngine {
    private var sim = PluckStringHarpSim(looping: false, still: false)
    private var lastDate: Date?
    private var lastLooping: Bool?
    private var lastStill: Bool?
    private var touching = false
    // Plucks already answered with a haptic.
    private var answered = 0
    private let selection = UISelectionFeedbackGenerator()

    func step(to date: Date, geometry: PluckStringHarpGeometry, looping: Bool, still: Bool) -> PluckStringHarpSim {
        if lastLooping != looping || lastStill != still {
            lastLooping = looping
            lastStill = still
            sim.looping = looping
            sim.still = still
            sim.clock = 0
        }
        let dt = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        sim.step(geometry, frameDt: dt)
        if sim.userPlucks > answered {
            answered = sim.userPlucks
            selection.selectionChanged()
        }
        return sim
    }

    func touch(_ p: CGPoint) {
        if !touching {
            touching = true
            selection.prepare()
            sim.fingerDown(p.x, p.y)
        } else {
            sim.fingerMove(p.x, p.y)
        }
    }

    func lift() {
        touching = false
        sim.fingerUp()
    }
}

/// Draws every string, its glow and its vibration, the floating notes and the finger's light.
enum PluckStringHarpLive {
    private typealias C = PluckStringHarpPalette
    private typealias D = PluckStringHarpDrawing
    private typealias P = PluckStringHarpPhysics

    // Thickness of the lowest and highest string (pt).
    static let gaugeLow = 2.6
    static let gaugeHigh = 1.35
    // Points sampled along each string when it is drawn.
    static let stringSamples = 30
    // Glow around a sounding string: a wide haze and a tight core.
    static let hazeWidth = 16.0
    static let hazeBlur = 11.0
    static let coreWidth = 5.0
    static let coreBlur = 3.5
    // Note names rise this far (pt) over their life.
    static let noteRise = 74.0
    static let noteSize: CGFloat = 26

    private static let noteFont = Font.custom("Georgia-Italic", fixedSize: noteSize)

    /// The string's colour between idle and hot, mixed per channel and rounded like the RN `mixColor`.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let x = Double((a >> shift) & 0xff)
            let y = Double((b >> shift) & 0xff)
            return (x + (y - x) * t).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    static func draw(_ ctx: inout GraphicsContext, sim: PluckStringHarpSim, layout: PluckStringHarpLayout, still: Bool) {
        let g = layout.geometry
        // The room warms with the music.
        var energy = 0.0
        for i in 0..<P.strings { energy += sim.heat[i] }
        energy = min(1, energy / 3)
        if energy > 0.005 {
            var warm = ctx
            warm.opacity = energy
            warm.fill(
                D.circle(layout.centreX, layout.centreY, 300),
                with: .radialGradient(
                    Gradient(colors: [Color.rgba(255, 170, 80, 0.16), Color.rgba(255, 170, 80, 0)]),
                    center: CGPoint(x: layout.centreX, y: layout.centreY),
                    startRadius: 0,
                    endRadius: 300
                )
            )
        }

        for i in 0..<P.strings {
            let x = g.x[i]
            let y0 = g.top[i]
            let length = g.bottom[i] - y0
            let heat = sim.heat[i]
            var path = Path()
            var widest = 0.0
            var envelope = [Double](repeating: 0, count: stringSamples + 1)
            for k in 0...stringSamples {
                let s = Double(k) / Double(stringSamples)
                let p = CGPoint(x: x + sim.displacement(i, s), y: y0 + length * s)
                if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
                envelope[k] = sim.envelope(i, s)
                widest = max(widest, envelope[k])
            }

            // The blur of a vibrating string: the whole width it sweeps, faintly lit.
            if widest > 0.5 {
                var band = Path()
                for k in 0...stringSamples {
                    let s = Double(k) / Double(stringSamples)
                    let p = CGPoint(x: x + envelope[k], y: k == 0 ? y0 : y0 + length * s)
                    if k == 0 { band.move(to: p) } else { band.addLine(to: p) }
                }
                for k in stride(from: stringSamples, through: 0, by: -1) {
                    let s = Double(k) / Double(stringSamples)
                    band.addLine(to: CGPoint(x: x - envelope[k], y: y0 + length * s))
                }
                band.closeSubpath()
                D.fill(&ctx, band, C.glow, 0.2 * min(1, widest / 5), blur: 2.5)
            }
            if heat > 0.01 {
                D.stroke(&ctx, path, C.glow, hazeWidth, 0.3 * heat, blur: hazeBlur)
                D.stroke(&ctx, path, C.glowCore, coreWidth, 0.8 * heat, blur: coreBlur)
            }
            let gauge = gaugeLow + ((gaugeHigh - gaugeLow) * Double(i)) / Double(P.strings - 1)
            ctx.stroke(
                path,
                with: .color(mix(C.stringIdle, C.stringHot, min(1, heat * 1.15))),
                style: StrokeStyle(lineWidth: gauge, lineCap: .round, lineJoin: .round)
            )
            // A hairline of light along the string's lit side.
            var lit = ctx
            lit.translateBy(x: -gauge * 0.22, y: 0)
            D.stroke(&lit, path, 0xfff6e6, 0.7, 0.18 + 0.55 * heat)
        }

        // Note names drifting up from where each string was plucked.
        for n in 0..<P.notePool {
            let age = sim.noteAge[n]
            if age < 0 { continue }
            let t = age / P.noteLife
            let alpha = min(1, age / 0.12) * pow(max(0, 1 - t), 1.5)
            let string = sim.noteString[n]
            let name = P.noteNames[string]
            let rise = still ? 0 : noteRise * easeOut(t)
            let nx = sim.noteX[n] + 16 + (still ? 0 : sin(t * 3 + Double(string)) * 5)
            let ny = sim.noteY[n] - 10 - rise
            let halo = ctx.resolve(Text(name).font(noteFont).foregroundStyle(D.color(C.glow)))
            let ink = ctx.resolve(Text(name).font(noteFont).foregroundStyle(D.color(C.noteInk)))
            let bounds = CGSize(width: 200, height: 200)
            let width = ink.measure(in: bounds).width
            let baseline = ink.firstBaseline(in: bounds)
            let origin = CGPoint(x: nx - width / 2, y: ny - baseline)
            ctx.drawLayer { layer in
                layer.opacity = alpha * 0.7
                layer.addFilter(.blur(radius: 6))
                layer.draw(halo, at: origin, anchor: .topLeading)
            }
            var inkCtx = ctx
            inkCtx.opacity = alpha
            inkCtx.draw(ink, at: origin, anchor: .topLeading)
        }

        // The finger's light, and a short trail behind it. The dots share one blurred layer.
        var anyTrail = false
        for k in 0..<P.trail {
            let age = sim.time - sim.trailT[k]
            if age <= P.trailLife && age >= 0 { anyTrail = true }
        }
        if anyTrail {
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: 7))
                for k in 0..<P.trail {
                    let age = sim.time - sim.trailT[k]
                    if age > P.trailLife || age < 0 { continue }
                    let t = age / P.trailLife
                    layer.fill(D.circle(sim.trailX[k], sim.trailY[k], 10 * (1 - t * 0.5)), with: .color(D.color(C.glowCore, 0.32 * (1 - t) * (1 - t))))
                }
            }
        }
        if sim.down {
            D.fill(&ctx, D.circle(sim.fx, sim.fy, 20), C.glow, 0.4, blur: 14)
            D.fill(&ctx, D.circle(sim.fx, sim.fy, 5), 0xfff4e2, 0.85, blur: 2)
        }
    }
}
