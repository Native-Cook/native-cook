import SwiftUI
import UIKit

/// A dream's emotional trace, gathered by a finger into a field of coloured dots.
struct MagneticFeelingField: View {
    static let loop = 8.0
    var autoPlay = false
    /// Freeze a deterministic frame for previews or visual comparison.
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var epoch = Date.now
    @State private var touched: Date?
    @State private var finger = CGPoint(x: 0.5, y: 0.5)
    @State private var held = false
    @State private var velocity = CGSize.zero
    @State private var saved = false
    @State private var lastMood = 0
    @State private var lastHaptic = Date.distantPast
    @State private var labelTransition = MagneticFeelingFieldLabelTransition.rest
    private let ink = Color(red: 0.15, green: 0.18, blue: 0.17)

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            TimelineView(.animation(paused: reduced || previewTime != nil || (!autoPlay && touched == nil))) { clock in
                let focus = previewTime.map { Self.script($0.truncatingRemainder(dividingBy: Self.loop)) } ?? focus(at: clock.date)
                let wake = drift(at: clock.date)
                let activity = fieldActivity(focus, at: clock.date)
                canonicalScreen(focus: focus, wake: wake, activity: activity, label: labelPose(at: clock.date, focus: focus))
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: 393 * scale, height: 852 * scale, alignment: .topLeading)
                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
            }
        }
        .background(Color(red: 0.933, green: 0.941, blue: 0.918))
        .ignoresSafeArea().preferredColorScheme(.light)
        .task(id: touched) {
            guard touched != nil, !held else { return }
            do { try await Task.sleep(for: .seconds(2.6)); touched = nil } catch {}
        }
    }

    private func canonicalScreen(focus: CGPoint, wake: CGSize, activity: Double, label: [MagneticFeelingFieldLabelGlyph]) -> some View {
        ZStack(alignment: .topLeading) {
            Color(red: 0.933, green: 0.941, blue: 0.918)
            Circle().fill(Self.tone(focus).opacity(0.10 * activity))
                .frame(width: 420, height: 420).blur(radius: 75).position(x: 215, y: 590)
            pageHeader
            dreamCard
            feelingGlass(focus: focus, activity: activity)
            feelingHeader(focus: focus, label: label)
            field(focus: focus, wake: wake, activity: activity)
                .frame(width: 344, height: 255).offset(x: 24.5, y: 467)
            fieldInteraction(focus: focus)
            Text("SLIDE TO FIND THE FEELING").font(.system(size: 9, weight: .medium, design: .monospaced))
                .tracking(1.6).foregroundStyle(ink.opacity(0.4)).frame(width: 393).offset(y: 748)
            saveButton
        }
        .foregroundStyle(ink)
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var pageHeader: some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text("REVERIE / JOURNAL").font(.system(size: 10, weight: .semibold, design: .monospaced)).tracking(2)
                Spacer()
                Image(systemName: "moon.stars").font(.system(size: 20, weight: .light))
            }.frame(width: 345).offset(x: 24, y: 80)
            Text("A trace of\nlast night.").font(.system(size: 38, weight: .regular, design: .serif))
                .tracking(-1.4).lineSpacing(-3).offset(x: 24, y: 113)
            Text("FRIDAY, OCTOBER 16").font(.system(size: 10, weight: .medium, design: .monospaced))
                .tracking(1.5).foregroundStyle(ink.opacity(0.48)).offset(x: 24, y: 218)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var dreamCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("THE FLOATING GARDEN").font(.system(size: 10, weight: .semibold)).tracking(1.5)
                Spacer()
                Text("06:42").font(.system(size: 10, design: .monospaced))
            }
            Text("I followed a paper boat through a garden\nwhere the flowers floated in the air.")
                .font(.system(size: 15)).lineSpacing(5)
        }
        .foregroundStyle(ink.opacity(0.65)).padding(20)
        .frame(width: 345, height: 112, alignment: .topLeading)
        .background(.white.opacity(0.42), in: RoundedRectangle(cornerRadius: 22))
        .offset(x: 24, y: 245)
    }

    private func feelingGlass(focus: CGPoint, activity: Double) -> some View {
        let tone = Self.tone(focus)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 38).fill(Color.white.opacity(0.64))
                .overlay {
                    RadialGradient(colors: [tone.opacity(0.43 * activity), tone.opacity(0.19 * activity), .clear],
                                   center: UnitPoint(x: focus.x, y: focus.y), startRadius: 12, endRadius: 315)
                        .clipShape(RoundedRectangle(cornerRadius: 38))
                }
                .overlay(RoundedRectangle(cornerRadius: 38).stroke(Color.white.opacity(0.62), lineWidth: 0.8))
                .frame(width: 379, height: 371).offset(x: 7, y: 370)
                .shadow(color: ink.opacity(0.025), radius: 2, y: 2)
                .shadow(color: ink.opacity(0.045), radius: 21, y: 12)
            RoundedRectangle(cornerRadius: 30).fill(Color.white.opacity(0.29))
                .overlay {
                    RoundedRectangle(cornerRadius: 30).stroke(
                        LinearGradient(colors: [.white.opacity(0.72), .white.opacity(0.11), .white.opacity(0.58)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.8)
                }
                .frame(width: 344, height: 255).offset(x: 24.5, y: 467)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func feelingHeader(focus: CGPoint, label: [MagneticFeelingFieldLabelGlyph]) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("This dream left me feeling").foregroundStyle(ink)
            MagneticFeelingFieldLabel(glyphs: label)
                .foregroundStyle(Self.tone(focus).opacity(0.83))
                .accessibilityHidden(true)
        }
        .font(.system(size: 20.5, weight: .semibold))
        .tracking(-0.4)
        .frame(width: 323, height: 54, alignment: .topLeading).offset(x: 35, y: 396)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("This dream left me feeling " + ["Serene", "Wonder", "Tender"][Self.mood(focus.x)])
    }

    private func labelPose(at date: Date, focus: CGPoint) -> [MagneticFeelingFieldLabelGlyph] {
        if reduced { return MagneticFeelingFieldLabelTransition.word(Self.mood(focus.x)) }
        if let previewTime { return MagneticFeelingFieldLabelTransition.loopPose(previewTime) }
        if let touched {
            let now = date.timeIntervalSinceReferenceDate
            if !held, let crossing = MagneticFeelingFieldLabelTransition.returnCrossing(x: finger.x) {
                let start = touched.timeIntervalSinceReferenceDate + 2 + 0.6 * crossing
                if now >= start {
                    let from = labelTransition.pose(at: start)
                    return MagneticFeelingFieldLabelTransition(from: from, target: 0, started: start).pose(at: now)
                }
            }
            return labelTransition.pose(at: now)
        }
        guard autoPlay else { return MagneticFeelingFieldLabelTransition.word(0) }
        return MagneticFeelingFieldLabelTransition.loopPose(max(0, date.timeIntervalSince(epoch)))
    }

    /// Only a touch or a discrete mood change writes state; the timeline never does.
    private func retargetLabel(to focus: CGPoint, at date: Date, capturing: Bool) {
        let target = Self.mood(focus.x)
        guard capturing || target != labelTransition.target else { return }
        let shown = labelPose(at: date, focus: self.focus(at: date))
        labelTransition = .init(from: shown, target: target, started: date.timeIntervalSinceReferenceDate)
    }

    static func mood(_ x: Double) -> Int { x > 0.65 ? 1 : x < 0.35 ? 2 : 0 }

    private func field(focus: CGPoint, wake: CGSize, activity: Double) -> some View {
        Canvas { context, _ in
            let cx = 24 + focus.x * 296, cy = 23.5 + focus.y * 208
            for row in 0..<11 {
                for col in 0..<11 {
                    let x = Double(col) * 29.6 + 24, y = Double(row) * 20.8 + 23.5
                    let dx = cx - x, dy = cy - y
                    let influence = exp(-(dx * dx + dy * dy) / 2550)
                    let radius = 1.35 + 7.65 * influence * activity
                    let px = x + dx * influence * (reduced ? 0 : 0.04) - wake.height * influence * 0.18
                    let py = y + dy * influence * (reduced ? 0 : 0.04) + wake.width * influence * 0.18
                    let sample = CGPoint(x: Double(col) / 10, y: Double(row) / 10)
                    let tint = Self.dotTone(sample, activity: influence * activity)
                    let disc = CGRect(x: px - radius, y: py - radius, width: radius * 2, height: radius * 2)
                    context.fill(Path(ellipseIn: disc), with: .color(tint.opacity(0.20 + (reduced ? 0.23 : 0.63) * influence * activity)))
                }
            }
            guard !reduced else { return }
            let diameter = 24 + 12 * activity
            let halo = CGRect(x: cx - diameter / 2, y: cy - diameter / 2, width: diameter, height: diameter)
            context.drawLayer { lens in
                lens.addFilter(.shadow(color: .white.opacity(0.72 * activity), radius: 9))
                lens.fill(Path(ellipseIn: halo), with: .linearGradient(
                    Gradient(colors: [.white.opacity(0.46), Self.tone(focus).opacity(0.23), .white.opacity(0.30)]),
                    startPoint: CGPoint(x: cx - 10, y: cy - 14), endPoint: CGPoint(x: cx + 12, y: cy + 16)))
                lens.stroke(Path(ellipseIn: halo), with: .color(.white.opacity(0.79 * activity)), lineWidth: 1.3)
            }
        }
        .accessibilityHidden(true)
    }

    private func fieldInteraction(focus: CGPoint) -> some View {
        let mood = focus.x > 0.65 ? 1 : focus.x < 0.35 ? 2 : 0
        return VStack(spacing: 0) {
            Spacer().frame(height: 467)
            HStack(spacing: 0) {
                Spacer().frame(width: 24.5)
                Color.clear.frame(width: 344, height: 255).contentShape(RoundedRectangle(cornerRadius: 30))
                    .accessibilityElement().accessibilityLabel("Feeling field")
                    .accessibilityValue(["Serene", "Wonder", "Tender"][mood]).gesture(drag)
                Spacer().frame(width: 24.5)
            }
            Spacer(minLength: 0)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var saveButton: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 774)
            HStack(spacing: 0) {
                Spacer().frame(width: 24)
                Button {
                    let now = Date.now
                    let shown = self.focus(at: now)
                    retargetLabel(to: shown, at: now, capturing: true)
                    finger = shown
                    velocity = .zero
                    touched = now
                    epoch = now.addingTimeInterval(2.6)
                    saved.toggle()
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    HStack {
                        Text(saved ? "Feeling saved" : "Keep this feeling")
                        Image(systemName: saved ? "checkmark" : "arrow.up.right")
                    }
                    .font(.system(size: 14, weight: .medium)).frame(width: 345, height: 42)
                    .background(ink, in: Capsule()).foregroundStyle(Color(red: 0.95, green: 0.96, blue: 0.93))
                }
                .padding(.vertical, 1)
                Spacer().frame(width: 24)
            }
            Spacer(minLength: 0)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func fieldActivity(_ focus: CGPoint, at date: Date) -> Double {
        let distance = hypot(focus.x - 0.5, focus.y - 0.5)
        var activity = Self.smooth(distance / 0.22)
        if let touched {
            let age = date.timeIntervalSince(touched)
            activity = max(activity, held ? 1 : 1 - Self.smooth((age - 2) / 0.6))
        }
        return 0.18 + 0.82 * activity
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0).onChanged { value in
            let now = Date.now
            let next = CGPoint(x: min(1, max(0, (value.location.x - 24) / 296)), y: min(1, max(0, (value.location.y - 23.5) / 208)))
            retargetLabel(to: next, at: now, capturing: !held)
            held = true
            touched = now
            velocity = CGSize(width: min(12, max(-12, (value.predictedEndTranslation.width - value.translation.width) * 0.06)), height: min(12, max(-12, (value.predictedEndTranslation.height - value.translation.height) * 0.06)))
            finger = next
            let mood = finger.x > 0.65 ? 1 : finger.x < 0.35 ? 2 : 0
            if mood != lastMood, Date.now.timeIntervalSince(lastHaptic) > 0.18 {
                UISelectionFeedbackGenerator().selectionChanged(); lastMood = mood; lastHaptic = .now
            }
        }.onEnded { _ in
            held = false
            touched = .now
            epoch = Date.now.addingTimeInterval(2.6)
        }
    }

    private func focus(at date: Date) -> CGPoint {
        if reduced { return touched == nil ? CGPoint(x: 0.5, y: 0.5) : finger }
        if let touch = touched {
            let elapsed = date.timeIntervalSince(touch)
            if held || elapsed < 2 { return finger }
            if elapsed < 2.6 {
                let p = Self.smooth((elapsed - 2) / 0.6)
                return CGPoint(x: finger.x + (0.5 - finger.x) * p, y: finger.y + (0.5 - finger.y) * p)
            }
        }
        guard autoPlay else { return CGPoint(x: 0.5, y: 0.5) }
        return Self.script(max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop))
    }

    private func drift(at date: Date) -> CGSize {
        guard !reduced else { return .zero }
        if let touched {
            let fade = exp(-max(0, date.timeIntervalSince(touched)) * 8)
            return CGSize(width: velocity.width * fade, height: velocity.height * fade)
        }
        guard autoPlay else { return .zero }
        let t = (previewTime ?? max(0, date.timeIntervalSince(epoch))).truncatingRemainder(dividingBy: Self.loop)
        let a = Self.script(t), b = Self.script(max(0, t - 0.08))
        return CGSize(width: (a.x - b.x) * 296 * 0.35, height: (a.y - b.y) * 208 * 0.35)
    }

    static func smooth(_ value: Double) -> Double { let t = min(1, max(0, value)); return t * t * (3 - 2 * t) }
    static func script(_ t: Double) -> CGPoint {
        let points = [CGPoint(x: 0.5, y: 0.5), CGPoint(x: 0.82, y: 0.42),
                      CGPoint(x: 0.38, y: 0.18), CGPoint(x: 0.20, y: 0.66),
                      CGPoint(x: 0.60, y: 0.82), CGPoint(x: 0.85, y: 0.36),
                      CGPoint(x: 0.80, y: 0.68), CGPoint(x: 0.72, y: 0.48),
                      CGPoint(x: 0.5, y: 0.5)]
        let times = [1.1, 1.85, 2.6, 3.4, 4.2, 5.0, 5.8, 6.5, 7.45]
        if t <= times[0] { return points[0] }
        for index in 1..<times.count where t < times[index] {
            let u = smooth((t - times[index - 1]) / (times[index] - times[index - 1]))
            return CGPoint(x: points[index - 1].x + (points[index].x - points[index - 1].x) * u,
                           y: points[index - 1].y + (points[index].y - points[index - 1].y) * u)
        }
        return points[points.count - 1]
    }

    private static func toneComponents(_ point: CGPoint) -> (Double, Double, Double) {
        let angle = (atan2(point.y - 0.5, point.x - 0.5) / (2 * .pi) + 1).truncatingRemainder(dividingBy: 1)
        let colors: [(Double, Double, Double)] = [
            (0.45, 0.72, 0.80), (0.55, 0.63, 0.83), (0.68, 0.59, 0.79),
            (0.83, 0.58, 0.73), (0.88, 0.61, 0.57), (0.88, 0.73, 0.43),
            (0.73, 0.78, 0.35), (0.47, 0.76, 0.66), (0.45, 0.72, 0.80)
        ]
        let position = angle * 8, index = min(7, Int(position)), u = position - Double(index)
        let a = colors[index], b = colors[index + 1]
        return (a.0 + (b.0 - a.0) * u, a.1 + (b.1 - a.1) * u, a.2 + (b.2 - a.2) * u)
    }

    static func tone(_ point: CGPoint) -> Color {
        let rgb = toneComponents(point)
        return Color(.sRGB, red: rgb.0, green: rgb.1, blue: rgb.2)
    }

    private static func dotTone(_ point: CGPoint, activity: Double) -> Color {
        let rgb = toneComponents(point), neutral = (0.64, 0.66, 0.67)
        let amount = smooth(activity * 2)
        return Color(.sRGB, red: neutral.0 + (rgb.0 - neutral.0) * amount,
                     green: neutral.1 + (rgb.1 - neutral.1) * amount,
                     blue: neutral.2 + (rgb.2 - neutral.2) * amount)
    }
}


/// One visible glyph, including an interrupted transition's exact drawing pose.
private struct MagneticFeelingFieldLabelGlyph {
    var character: String
    var center: Double
    var width: Double
    var scaleX = 1.0
    var blur = 0.0
    var opacity = 1.0
}

private struct MagneticFeelingFieldLabel: View {
    var glyphs: [MagneticFeelingFieldLabelGlyph]

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(glyphs.indices, id: \.self) { index in
                let glyph = glyphs[index]
                Text(glyph.character)
                    .font(.system(size: 20.5, weight: .semibold)).tracking(-0.4)
                    .frame(width: glyph.width, height: MagneticFeelingFieldLabelTransition.lineHeight)
                    .blur(radius: glyph.blur)
                    .scaleEffect(x: glyph.scaleX, y: 1, anchor: .center)
                    .opacity(glyph.opacity)
                    .accessibilityHidden(true)
                    .position(x: glyph.center, y: MagneticFeelingFieldLabelTransition.lineHeight / 2)
            }
        }
        .frame(width: 323, height: MagneticFeelingFieldLabelTransition.lineHeight, alignment: .topLeading)
    }
}

private struct MagneticFeelingFieldLabelTransition {
    static let stagger = 0.019
    static let duration = 0.12
    static let lineHeight = UIFont.systemFont(ofSize: 20.5, weight: .semibold).lineHeight
    private static let names = ["Serene", "Wonder", "Tender"]
    private static let layouts: [[MagneticFeelingFieldLabelGlyph]] = names.map { name in
        let font = UIFont.systemFont(ofSize: 20.5, weight: .semibold)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: -0.4]
        let characters = Array(name).map(String.init)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: name, attributes: attributes))
        return characters.indices.map { index in
            let left = CTLineGetOffsetForStringIndex(line, index, nil)
            let width = (characters[index] as NSString).size(withAttributes: attributes).width
            return MagneticFeelingFieldLabelGlyph(character: characters[index], center: left + width / 2, width: width)
        }
    }

    var from: [MagneticFeelingFieldLabelGlyph]
    var target: Int
    var started: Double

    static var rest: Self { .init(from: word(0), target: 0, started: 0) }
    static func word(_ mood: Int) -> [MagneticFeelingFieldLabelGlyph] { layouts[mood] }

    func pose(at time: Double) -> [MagneticFeelingFieldLabelGlyph] {
        let destination = Self.word(target)
        return from.indices.map { index in
            let old = from[index], new = destination[index]
            let u = min(1, max(0, (time - started - Double(index) * Self.stagger) / Self.duration))
            if u <= 0 { return old }
            if u >= 1 { return new }
            let travel = MagneticFeelingField.smooth(u)
            let center = old.center + (new.center - old.center) * travel
            if old.character == new.character {
                return .init(character: new.character, center: center, width: new.width,
                             scaleX: old.scaleX + (1 - old.scaleX) * travel,
                             blur: old.blur * (1 - travel), opacity: old.opacity + (1 - old.opacity) * travel)
            }
            if u < 0.5 {
                let p = MagneticFeelingField.smooth(u * 2)
                return .init(character: old.character, center: center, width: old.width,
                             scaleX: old.scaleX + (0.25 - old.scaleX) * p,
                             blur: old.blur + (3 - old.blur) * p, opacity: old.opacity + (0.38 - old.opacity) * p)
            }
            let p = MagneticFeelingField.smooth((u - 0.5) * 2)
            return .init(character: new.character, center: center, width: new.width,
                         scaleX: 0.25 + 0.75 * p, blur: 3 * (1 - p), opacity: 0.38 + 0.62 * p)
        }
    }

    /// The analytic route is sampled only once to find, then bisect, its mood crossings.
    private static let events: [MagneticFeelingFieldLabelTransition] = {
        var result: [Self] = [.rest]
        var previousMood = 0
        for step in 1...1920 {
            let time = Double(step) / 240
            let nextMood = MagneticFeelingField.mood(MagneticFeelingField.script(time).x)
            guard nextMood != previousMood else { continue }
            var lower = Double(step - 1) / 240, upper = time
            for _ in 0..<24 {
                let mid = (lower + upper) / 2
                if MagneticFeelingField.mood(MagneticFeelingField.script(mid).x) == previousMood { lower = mid } else { upper = mid }
            }
            let start = (lower + upper) / 2
            let shown = result[result.count - 1].pose(at: start)
            result.append(.init(from: shown, target: nextMood, started: start))
            previousMood = nextMood
        }
        return result
    }()

    static func loopPose(_ raw: Double) -> [MagneticFeelingFieldLabelGlyph] {
        let t = (raw.truncatingRemainder(dividingBy: 8) + 8).truncatingRemainder(dividingBy: 8)
        let event = events.last(where: { $0.started <= t }) ?? .rest
        return event.pose(at: t)
    }

    /// Same inverse smoothstep as the field's delayed return, with no per-frame state.
    static func returnCrossing(x: Double) -> Double? {
        guard x > 0.65 || x < 0.35 else { return nil }
        let p = x > 0.65 ? (x - 0.65) / (x - 0.5) : (0.35 - x) / (0.5 - x)
        var lower = 0.0, upper = 1.0
        for _ in 0..<24 {
            let mid = (lower + upper) / 2
            if MagneticFeelingField.smooth(mid) < p { lower = mid } else { upper = mid }
        }
        return (lower + upper) / 2
    }
}
