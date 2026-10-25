import SwiftUI
import UIKit

/*
 Barometer Needle Chart — a sailing weather screen ("Saltmarsh Harbour") whose hero is a brass
 aneroid barometer over a 48-hour pressure chart. Scrubbing the chart swings the blued needle on a
 spring to the reading at that hour; the brass set hand stays at now (1012 hPa), the readout rolls
 like an odometer and the forecast word and trend roll as the needle crosses their bands. Tap the
 glass and the needle twitches, as people tap real barometers.

 One Canvas: a baked image of everything static (background, dial, chart card), the needle, a
 baked image of the glass (reflections, set hand, knob), then the readout and chart cursor. Both
 images are baked off the main thread. Timeline and physics: BarometerNeedleChartMotion.swift.

 Touch: drag on the chart to scrub (rubber-band past ±24 h); the needle runs the same spring. A
 tap on the dial kicks the needle (22°/s). 2 s after the last touch the cursor eases back to now
 (0.9 s) and the loop restarts from t = 0. Reduced motion: no loop, no spring or jitter; the
 needle points straight at the reading.
 */
struct BarometerNeedleChart: View {
    /// Plays the 9 s scrub through the storm and the forecast on a loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = BarometerNeedleChartEngine()
    @State private var art: BarometerNeedleChartImages?
    @State private var awake = true

    private typealias P = BarometerNeedleChartPaint

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let g = BarometerNeedleChartGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: g) { await bake(g) }
        }
        .background(P.color(0x0F1C24).ignoresSafeArea())
    }

    private func bake(_ g: BarometerNeedleChartGeo) async {
        guard g.w > 0, g.h > 0, art?.geo != g else { return }
        let scale = min(displayScale, 3)
        let images = await Task.detached(priority: .userInitiated) {
            _ = BarometerNeedleChartMotion.needleTable
            return BarometerNeedleChartImages(
                geo: g,
                base: BarometerNeedleChartArt.base(g, scale: scale),
                glass: BarometerNeedleChartArt.glass(g, scale: scale)
            )
        }.value
        art = images
    }

    @ViewBuilder
    private func stage(_ g: BarometerNeedleChartGeo) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        ZStack(alignment: .topLeading) {
            P.color(0x0F1C24)
            if let art, art.geo == g {
                TimelineView(.animation(paused: !auto && !awake)) { timeline in
                    Canvas { ctx, _ in
                        let pose = engine.step(timeline.date, auto: auto, reduced: reduced, g: g)
                        if engine.settled && !auto {
                            DispatchQueue.main.async { if engine.settled { awake = false } }
                        }
                        ctx.draw(Image(uiImage: art.base), in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                        BarometerNeedleChartFrame.needle(ctx, g, angle: pose.angle)
                        ctx.draw(Image(uiImage: art.glass), in: CGRect(x: g.cx - g.rb, y: g.cy - g.rb, width: g.rb * 2, height: g.rb * 2))
                        BarometerNeedleChartFrame.overlay(ctx, g, hour: pose.hour, hpaNeedle: BarometerNeedleChartMotion.hpaOf(pose.angle))
                    }
                }
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            engine.touch(v.location, start: v.startLocation, g: g)
                        }
                        .onEnded { v in
                            awake = true
                            engine.release(v.location, g: g, reduced: reduced)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Barometer at 1012 hectopascals, change. Drag the chart to see the pressure at another hour; tap the dial to tap the glass.")

                stats(g)
            }
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func stats(_ g: BarometerNeedleChartGeo) -> some View {
        HStack(spacing: 8) {
            ForEach(0..<Self.statRows.count, id: \.self) { i in
                let row = Self.statRows[i]
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.0)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(P.color(0xE9E0CC, 0.5))
                    Text(row.1)
                        .font(.system(size: 15, weight: .semibold).monospacedDigit())
                        .foregroundStyle(P.color(0xF1EAD9))
                        .lineLimit(1)
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(P.color(0xF1EAD9, 0.06))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(P.color(0xF1EAD9, 0.12), lineWidth: 1 / displayScale)
                )
            }
        }
        .frame(width: g.w - 32, height: 56)
        .offset(x: 16, y: g.statsTop)
        .allowsHitTesting(false)
    }

    private static let statRows: [(String, String)] = [
        ("Wind", "SW 14 kn"),
        ("Swell", "1.2 m · 9 s"),
        ("High tide", "16:48"),
    ]
}

struct BarometerNeedleChartImages {
    let geo: BarometerNeedleChartGeo
    let base: UIImage
    let glass: UIImage
}

/// The clock, the needle spring and the finger, stepped once a frame from the Canvas.
final class BarometerNeedleChartEngine {
    private typealias M = BarometerNeedleChartMotion

    /// Seconds after the last touch before the screen eases back to rest, and how long that takes.
    private static let idle = 2.0
    private static let returnTime = 0.9
    /// Velocity kick of a tap on the glass, degrees per second.
    private static let tapKick = 22.0

    /// 0 loop / rest, 1 a finger is in charge, 2 easing back to rest.
    private var mode = 0
    /// Loop time; −1 while the loop is not running.
    private var loopT = -1.0
    private var last: Double?
    private var wasAuto: Bool?
    private var lastTouch = 0.0
    private var touching = false
    private var scrubbing = false
    private var downAt = CGPoint.zero
    private var userHour = 0.0
    private var x = M.angleOf(M.pressureAt(0))
    private var v = 0.0
    private var returnFrom = (hour: 0.0, at: 0.0)
    private var word: Int?
    private(set) var settled = false
    private let tick = UISelectionFeedbackGenerator()
    private let tap = UIImpactFeedbackGenerator(style: .light)

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ date: Date, auto: Bool, reduced: Bool, g: BarometerNeedleChartGeo) -> (hour: Double, angle: Double) {
        let t1 = date.timeIntervalSinceReferenceDate
        let raw = last.map { t1 - $0 } ?? 0
        last = t1
        let dt = min(max(raw, 0), 0.05)
        if wasAuto != auto {
            wasAuto = auto
            mode = 0
            loopT = auto ? 0 : -1
            userHour = 0
        }
        var hour = userHour
        let angle: Double
        settled = false
        if mode == 0 && loopT >= 0 {
            // A frame that stalls pauses the loop instead of skipping ahead.
            if raw > 0 && raw <= 0.25 { loopT = (loopT + raw).truncatingRemainder(dividingBy: M.loop) }
            let t = loopT
            hour = M.cursorAt(t)
            let s = M.sampleTable(t)
            angle = s + M.jitterAt(t)
            x = angle
            v = (M.sampleTable(t + 1.0 / 120) - s) * 120
            userHour = hour
        } else {
            if mode == 2 {
                let k = M.easeInOut(M.seg(t1, returnFrom.at, returnFrom.at + Self.returnTime))
                hour = M.mix(returnFrom.hour, 0, k)
                userHour = hour
                if t1 > returnFrom.at + Self.returnTime + 0.5 {
                    mode = 0
                    loopT = 0
                }
            } else if mode == 1 && !touching {
                // Let go past an end: the cursor springs back inside.
                let inside = M.clamp(hour, -M.span, M.span)
                if inside != hour {
                    hour += (inside - hour) * (1 - exp(-dt * 14))
                    if abs(inside - hour) < 0.001 { hour = inside }
                    userHour = hour
                }
                if auto && t1 - lastTouch > Self.idle {
                    mode = 2
                    returnFrom = (hour, t1)
                }
            }
            let target = M.angleOf(M.pressureAt(M.clamp(hour, -M.span, M.span)))
            if reduced {
                x = target
                v = 0
            } else {
                (x, v) = M.stepNeedle(x, v, target: target, dt: dt)
            }
            angle = x
            settled = !auto && !touching && mode != 2 && abs(x - target) < 0.003 && abs(v) < 0.01
                && hour == M.clamp(hour, -M.span, M.span)
        }
        // A selection tick each time a finger drags the needle into another forecast word.
        let w = Int(floor(M.wordIndex(M.hpaOf(angle)) + 0.5))
        if let word, w != word, mode == 1 { tick.selectionChanged() }
        word = w
        return (hour, angle)
    }

    private func hourFromX(_ g: BarometerNeedleChartGeo, _ px: CGFloat) -> Double {
        let h = g.hourAtX(px)
        let span = M.span
        // Rubber-band past either end.
        if h > span { return span + (h - span) / (1 + (h - span) / 6) }
        if h < -span { return -span - (-span - h) / (1 + (-span - h) / 6) }
        return h
    }

    func touch(_ p: CGPoint, start: CGPoint, g: BarometerNeedleChartGeo) {
        if !touching {
            // A finger lands: it takes over from the loop.
            touching = true
            mode = 1
            loopT = -1
            downAt = start
            let inCard = start.y >= g.card.minY && start.y <= g.card.maxY
            scrubbing = inCard
            if inCard { userHour = hourFromX(g, start.x) }
            tick.prepare()
        }
        lastTouch = clock
        if scrubbing { userHour = hourFromX(g, p.x) }
    }

    func release(_ p: CGPoint, g: BarometerNeedleChartGeo, reduced: Bool) {
        touching = false
        lastTouch = clock
        if !scrubbing {
            let moved = hypot(p.x - downAt.x, p.y - downAt.y)
            let onDial = hypot(downAt.x - g.cx, downAt.y - g.cy) < g.rb
            if moved < 12 && onDial && !reduced {
                v += x > M.angleOf(M.nowHpa) ? -Self.tapKick : Self.tapKick
                tap.impactOccurred()
            }
        }
        scrubbing = false
    }
}
