import SwiftUI
import UIKit

/*
 Weight Breathing Glyph: a type specimen of one giant capital S that breathes between hairline and
 black along a weight axis, with the glyph's skeleton (its points and the lines between them) drawn
 over it like a font editor.

 The outline is Inter Tight's S at its three masters (weights 100, 400 and 900), kept here as point
 lists (font units, 2048 to the em). The font has no avar table, so a weight between two masters is
 exactly the linear blend of their points, which is what the variable font itself draws. A contour is
 TrueType quadratics: two off-curve points in a row have an implied on-curve point half-way.

 Layout (pt, W × H the whole screen, on #ebeae6): a black pill "Type specimen 01" and "● Variable"
 at top + 12; "U+0053" and "wght 100–900" at top + 56; the S scaled to fit W − 48 at its widest
 (black) advance and centred in the room between top + 72 and the panel, with hairline guides at
 its cap height and baseline; below, the readout (the weight, 56 pt monospaced, the style name and
 "wght"), the axis (ticks every 100, a black knob at the weight) and a caption.

 Drawing: the outline at 900 as a ghost (0.75 pt, ink 28%), the current outline filled #0b0b0b,
 then the skeleton in white with a difference blend, so it reads dark on the paper and light on the
 letter: the control polygon (0.75 pt), on-curve points as dots (r 3.2) and off-curve points as
 rings (r 2.4).

 Timeline of the self-playing loop (`WeightBreathingGlyph.loop` = 8 s, two breaths of 4 s):
   weight = 100 + 800 · (½ − ½ cos(2π t / 4 s)), so 100 (the resting state, hairline) at 0, 4 and 8 s
   and 900 (black) at 2 and 6 s, easing in and out at both ends like a sine.

 Touch: dragging sideways sets the weight directly (the axis's width, W − 48, spans 100 to 900),
 with a selection haptic each time it crosses a hundred. 2 s after the finger lifts the weight eases
 back to 100 (0.8 s, ease-in-out) and the loop starts over. Reduced motion: the S stands still at
 400 (Regular) and the loop is off; dragging still sets the weight, and it stays where it is left.
*/
struct WeightBreathingGlyph: View {
    var badge = "Type specimen 01"
    var tag = "Variable"
    var caption = "Drag sideways to set the weight"
    /// Breathes the weight by itself; the pattern repeats every `loop` seconds.
    var autoPlay = false

    static let loop = 8.0

    enum M {
        static let breath = 4.0
        static let idle = 2.0
        static let back = 0.8
        static let paper = Color(hex: 0xebeae6)
        static let ink = Color(hex: 0x0b0b0b)
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var loopStart = Date.now
    /// The weight a finger set, and the tween back to rest after it lifts.
    @State private var touched: Double? = nil
    @State private var back = Tween(100)
    @State private var returning = false
    @State private var dragStart: Double? = nil
    @State private var lifts = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            TimelineView(.animation(paused: reduceMotion || (!autoPlay && !returning))) { timeline in
                Canvas { context, size in
                    let weight = weight(at: timeline.date)
                    draw(&context, size: size, top: insets.top, bottom: insets.bottom, weight: weight)
                }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .gesture(drag(width: width))
            .accessibilityElement()
            .accessibilityLabel("The letter S")
            .accessibilityValue("Weight \(Int(weight(at: .now).rounded()))")
        }
        .background(M.paper.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            loopStart = .now
            haptic.prepare()
        }
        .task(id: lifts) { await settle() }
    }

    // MARK: Weight

    private func weight(at date: Date) -> Double {
        if let touched { return touched }
        if returning { return back.value(at: date) }
        if reduceMotion { return 400 }
        guard autoPlay else { return 100 }
        let t = date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: Self.loop)
        return 100 + 800 * (0.5 - 0.5 * cos(2 * .pi * t / M.breath))
    }

    private func drag(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let base = dragStart ?? weight(at: .now)
                if dragStart == nil {
                    dragStart = base
                    returning = false
                }
                let next = min(max(base + value.translation.width * 800 / max(width - 48, 1), 100), 900)
                if let previous = touched, Int(previous / 100) != Int(next / 100) { haptic.selectionChanged() }
                touched = next
            }
            .onEnded { _ in
                dragStart = nil
                lifts += 1
            }
    }

    /// 2 s after the finger lifts the weight eases back to rest and the loop starts over.
    private func settle() async {
        guard lifts > 0, !reduceMotion, let from = touched else { return }
        try? await Task.sleep(for: .seconds(M.idle))
        guard !Task.isCancelled, dragStart == nil else { return }
        back = Tween(from)
        back.retarget(100, duration: M.back, curve: .easeInOut)
        touched = nil
        returning = true
        try? await Task.sleep(for: .seconds(M.back))
        guard !Task.isCancelled, dragStart == nil else { return }
        loopStart = .now
        returning = false
    }

    // MARK: Drawing

    private func draw(_ context: inout GraphicsContext, size: CGSize, top: Double, bottom: Double, weight: Double) {
        let w = size.width, h = size.height
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.paper))

        // Header.
        let mono = { (s: Double, weight: Font.Weight) in Font.system(size: s, weight: weight, design: .monospaced) }
        let pill = context.resolve(Text(badge.uppercased()).font(mono(11, .medium)).tracking(1).foregroundColor(.white))
        let pillSize = pill.measure(in: CGSize(width: w, height: 100))
        let pillRect = CGRect(x: 24, y: top + 12, width: pillSize.width + 24, height: 26)
        context.fill(capsule(pillRect), with: .color(M.ink))
        context.draw(pill, at: CGPoint(x: pillRect.midX, y: pillRect.midY), anchor: .center)
        let tagText = context.resolve(Text(tag.uppercased()).font(mono(11, .medium)).tracking(1).foregroundColor(M.ink))
        let tagSize = tagText.measure(in: CGSize(width: w, height: 100))
        context.draw(tagText, at: CGPoint(x: w - 24, y: pillRect.midY), anchor: .trailing)
        context.fill(Path(ellipseIn: CGRect(x: w - 24 - tagSize.width - 14, y: pillRect.midY - 3, width: 6, height: 6)), with: .color(M.ink))
        let resolver = context
        let small = { (text: String) in resolver.resolve(Text(text).font(mono(10, .regular)).foregroundColor(M.ink.opacity(0.45))) }
        context.draw(small("U+0053"), at: CGPoint(x: 24, y: top + 56), anchor: .leading)
        context.draw(small("wght 100–900"), at: CGPoint(x: w - 24, y: top + 56), anchor: .trailing)

        // Panel, laid out from the bottom up.
        let floor = h - bottom - 16
        let trackY = floor - 44
        let readout = floor - 76
        let panelTop = readout - 56

        // The glyph: fitted to the black advance and to the room above the panel, centred on its advance.
        let room = (top: top + 72, bottom: panelTop - 16)
        let k = min((w - 48) / WeightBreathingGlyphOutline.blackAdvance, (room.bottom - room.top) / 1600)
        let baseline = (room.top + room.bottom) / 2 + (1510 - 18) * k / 2
        let outline = WeightBreathingGlyphOutline.at(weight)
        let x0 = w / 2 - outline.advance * k / 2
        let ghostX0 = w / 2 - WeightBreathingGlyphOutline.blackAdvance * k / 2
        let place = { (x: Double, y: Double, origin: Double) in CGPoint(x: origin + x * k, y: baseline - y * k) }

        // Guides at cap height and baseline.
        for (y, label) in [(baseline - 1490 * k, "Cap height 1490"), (baseline, "Baseline 0")] {
            context.fill(Path(CGRect(x: 0, y: y - 0.25, width: w, height: 0.5)), with: .color(M.ink.opacity(0.18)))
            context.draw(small(label), at: CGPoint(x: 24, y: y - 4), anchor: .bottomLeading)
        }

        let ghost = WeightBreathingGlyphOutline.path(WeightBreathingGlyphOutline.at(900).points.map { place($0.x, $0.y, ghostX0) })
        context.stroke(ghost, with: .color(M.ink.opacity(0.28)), lineWidth: 0.75)
        let points = outline.points.map { place($0.x, $0.y, x0) }
        context.fill(WeightBreathingGlyphOutline.path(points), with: .color(M.ink))

        // The skeleton, in white with a difference blend.
        var skeleton = context
        skeleton.blendMode = .difference
        var polygon = Path()
        polygon.addLines(points)
        polygon.closeSubpath()
        skeleton.stroke(polygon, with: .color(.white), lineWidth: 0.75)
        for (i, p) in points.enumerated() {
            if WeightBreathingGlyphOutline.on[i] {
                skeleton.fill(Path(ellipseIn: CGRect(x: p.x - 3.2, y: p.y - 3.2, width: 6.4, height: 6.4)), with: .color(.white))
            } else {
                skeleton.stroke(Path(ellipseIn: CGRect(x: p.x - 2.4, y: p.y - 2.4, width: 4.8, height: 4.8)), with: .color(.white), lineWidth: 1)
            }
        }

        // Readout.
        let number = context.resolve(Text("\(Int(weight.rounded()))").font(mono(56, .medium)).foregroundColor(M.ink))
        let measured = number.measure(in: CGSize(width: w, height: 200))
        context.draw(number, at: CGPoint(x: 24, y: readout - number.firstBaseline(in: measured)), anchor: .topLeading)
        let name = context.resolve(Text(Self.styleName(weight)).font(mono(17, .medium)).foregroundColor(M.ink))
        let nameSize = name.measure(in: CGSize(width: w, height: 100))
        context.draw(name, at: CGPoint(x: w - 24, y: readout - name.firstBaseline(in: nameSize)), anchor: .topTrailing)
        let axisLabel = small("wght")
        let axisSize = axisLabel.measure(in: CGSize(width: w, height: 100))
        context.draw(axisLabel, at: CGPoint(x: w - 24, y: readout - 24 - axisLabel.firstBaseline(in: axisSize)), anchor: .topTrailing)

        // Axis: a hairline, a tick every 100 (taller at 100, 400 and 900) and the knob.
        let span = w - 48
        context.fill(Path(CGRect(x: 24, y: trackY - 0.5, width: span, height: 1)), with: .color(M.ink.opacity(0.3)))
        for step in 0...8 {
            let x = 24 + span * Double(step) / 8
            let major = step == 0 || step == 3 || step == 8
            context.fill(Path(CGRect(x: x - 0.5, y: trackY - (major ? 10 : 6), width: 1, height: major ? 10 : 6)), with: .color(M.ink.opacity(major ? 0.6 : 0.3)))
        }
        for (value, anchor) in [(100.0, UnitPoint.leading), (400, .center), (900, .trailing)] {
            let x = 24 + span * (value - 100) / 800
            context.draw(small("\(Int(value))"), at: CGPoint(x: anchor == .center ? x : (value == 100 ? 24 : w - 24), y: floor - 26), anchor: anchor)
        }
        let knobX = 24 + span * (weight - 100) / 800
        context.fill(capsule(CGRect(x: knobX - 2, y: trackY - 16, width: 4, height: 24)), with: .color(M.ink))
        let captionText = context.resolve(Text(caption).font(mono(11, .regular)).foregroundColor(M.ink.opacity(0.5)))
        context.draw(captionText, at: CGPoint(x: 24, y: floor), anchor: .bottomLeading)
    }

    static func styleName(_ weight: Double) -> String {
        let names = ["Thin", "ExtraLight", "Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black"]
        return names[min(max(Int((weight / 100).rounded()) - 1, 0), 8)]
    }
}

/// Inter Tight's S at weights 100, 400 and 900 (font units, y up), and its outline at any weight between.
enum WeightBreathingGlyphOutline {
    static let blackAdvance = 1316.0
    static let advances = [1254.0, 1210, 1316]

    static let thin: [Double] = [
        622, -24, 486, -24, 283, 71, 165, 240, 158, 350, 204, 350, 211, 253, 317, 106, 500, 24, 622, 24, 751, 24, 943, 113, 1050, 273, 1050, 378, 1050, 458, 980, 579, 831, 673, 710, 710, 542, 762, 366, 817, 192, 999, 192, 1128, 192, 1238, 307, 1412, 507, 1512, 634, 1512, 762, 1512, 955, 1410, 1069, 1233, 1076, 1118, 1030, 1118, 1019, 1270, 807, 1466, 634, 1466, 521, 1466, 341, 1379, 238, 1226, 238, 1128, 238, 1053, 306, 932, 446, 841, 552, 808, 722, 756, 818, 726, 959, 652, 1051, 560, 1096, 447, 1096, 378, 1096, 262, 977, 80, 764, -24,
    ]
    static let regular: [Double] = [
        604, -26, 444, -26, 210, 77, 77, 262, 68, 384, 254, 384, 262, 300, 361, 190, 515, 136, 604, 136, 707, 136, 872, 204, 968, 326, 968, 408, 968, 482, 884, 574, 749, 632, 670, 654, 488, 706, 313, 756, 110, 943, 110, 1094, 110, 1219, 245, 1406, 476, 1510, 618, 1510, 762, 1510, 986, 1407, 1118, 1230, 1122, 1118, 946, 1118, 933, 1228, 748, 1350, 612, 1350, 514, 1350, 366, 1286, 284, 1174, 284, 1102, 284, 1023, 382, 926, 517, 872, 572, 856, 722, 814, 780, 798, 923, 744, 1056, 652, 1142, 509, 1142, 404, 1142, 284, 1016, 89, 776, -26,
    ]
    static let black: [Double] = [
        678, -18, 484, -18, 195, 97, 35, 337, 34, 524, 418, 524, 422, 456, 483, 362, 594, 314, 672, 314, 737, 314, 825, 346, 870, 403, 870, 440, 870, 474, 827, 525, 729, 567, 646, 584, 496, 616, 299, 658, 72, 861, 72, 1036, 72, 1178, 226, 1391, 497, 1510, 674, 1510, 855, 1510, 1118, 1389, 1261, 1170, 1262, 1024, 874, 1024, 869, 1097, 772, 1178, 674, 1178, 612, 1178, 532, 1149, 494, 1097, 494, 1064, 494, 1029, 535, 976, 624, 937, 694, 922, 816, 896, 939, 870, 1116, 784, 1229, 669, 1282, 525, 1282, 440, 1282, 293, 1137, 88, 867, -18,
    ]
    /// On-curve (true) or off-curve (false), point by point; the S is one contour.
    static let on: [Bool] = [1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0].map { $0 == 1 }

    /// The outline at `weight` (100 … 900): its points and its advance.
    static func at(_ weight: Double) -> (points: [(x: Double, y: Double)], advance: Double) {
        let w = min(max(weight, 100), 900)
        let (a, b, t, adv) = w <= 400
            ? (thin, regular, (w - 100) / 300, advances[0] + (advances[1] - advances[0]) * (w - 100) / 300)
            : (regular, black, (w - 400) / 500, advances[1] + (advances[2] - advances[1]) * (w - 400) / 500)
        let points = stride(from: 0, to: a.count, by: 2).map { i in
            (x: a[i] + (b[i] - a[i]) * t, y: a[i + 1] + (b[i + 1] - a[i + 1]) * t)
        }
        return (points, adv)
    }

    /// The closed quadratic contour through `points` (already on screen).
    static func path(_ points: [CGPoint]) -> Path {
        var path = Path()
        let n = points.count
        guard let first = on.firstIndex(of: true), n == on.count else { return path }
        path.move(to: points[first])
        var i = 1
        while i <= n {
            let index = (first + i) % n
            if on[index] {
                path.addLine(to: points[index])
                i += 1
            } else {
                let next = (first + i + 1) % n
                if on[next] {
                    path.addQuadCurve(to: points[next], control: points[index])
                    i += 2
                } else {
                    let mid = CGPoint(x: (points[index].x + points[next].x) / 2, y: (points[index].y + points[next].y) / 2)
                    path.addQuadCurve(to: mid, control: points[index])
                    i += 1
                }
            }
        }
        path.closeSubpath()
        return path
    }
}
