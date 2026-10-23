import SwiftUI
import UIKit

/// A warm class picker whose elemental marks are written by a moving ink tip.
struct FourElementsClassPicker: View {
    static let loop: Double = 8
    var autoPlay = false
    /// Freezes a deterministic loop phase for visual review.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var lastTouch = Date.distantPast
    @State private var traceStarted = Date.distantPast
    @State private var traceDuration = 0.65
    @State private var manualBase = FourElementsClassPickerPose.rest
    @State private var manualIndex = 0
    @State private var manualLive = false
    @State private var dragging = false
    @State private var dragIndex = -1
    @State private var detailStartIndex = 0
    @State private var detailDragging = false
    @State private var spaceHeld = false
    @State private var lastHaptic = Date.distantPast

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                FourElementsClassPickerStyle.paper.ignoresSafeArea()
                canonicalScreen
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale)
                    .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .task(id: lastTouch) {
            guard manualLive else { return }
            try? await Task.sleep(for: .seconds(2.65))
            guard !Task.isCancelled, !dragging, !detailDragging else { return }
            manualLive = false
        }
    }

    private var canonicalScreen: some View {
        ZStack(alignment: .topLeading) {
            paperGrain
            header
            TimelineView(.animation(minimumInterval: nil, paused: timelinePaused)) { timeline in
                let pose = currentPose(at: timeline.date)
                VStack(spacing: 0) {
                    Spacer().frame(height: 250)
                    tileGrid(pose)
                    Spacer().frame(height: 28)
                    detailPanel(pose)
                    Spacer().frame(height: 19)
                    reserveButton(pose, now: timeline.date)
                    Spacer(minLength: 0)
                }
                .frame(width: 393, height: 852, alignment: .top)
            }
            Text("Swipe across the elements")
                .font(.system(size: 10)).foregroundStyle(FourElementsClassPickerStyle.color(0xA89787))
                .position(x: 196.5, y: 801)
                .accessibilityHidden(true)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .clipped()
    }

    private var timelinePaused: Bool {
        reduceMotion || previewTime != nil || (!autoPlay && !manualLive)
    }

    private var paperGrain: some View {
        Canvas { context, _ in
            for index in 0..<160 {
                let x = Self.hash(Double(index) + 0.3) * 393
                let y = Self.hash(Double(index) + 24.7) * 852
                let length = 2 + Self.hash(Double(index) + 92.1) * 5
                var line = Path()
                line.move(to: CGPoint(x: x, y: y))
                line.addLine(to: CGPoint(x: x + length, y: y - 0.8))
                context.stroke(line, with: .color(FourElementsClassPickerStyle.rust.opacity(0.025)), lineWidth: 0.55)
            }
        }
        .frame(width: 393, height: 852)
        .accessibilityHidden(true)
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                for index in 0..<3 {
                    var dash = Path()
                    dash.move(to: CGPoint(x: 28, y: 92 + index * 7))
                    dash.addQuadCurve(to: CGPoint(x: 46 - index * 5, y: 92 + index * 7),
                                      control: CGPoint(x: 35, y: 90 + index * 7))
                    context.stroke(dash, with: .color(FourElementsClassPickerStyle.rust),
                                   style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                }
            }
            Text("Veyra").font(.system(size: 42, weight: .regular, design: .serif)).tracking(-2)
                .foregroundStyle(FourElementsClassPickerStyle.rust).position(x: 196.5, y: 91)
            Text("WHOLE DAYS").font(.system(size: 8, weight: .medium)).tracking(3)
                .foregroundStyle(FourElementsClassPickerStyle.color(0xAE8B71)).position(x: 198, y: 131)
            Image(systemName: "bell").font(.system(size: 15, weight: .regular))
                .foregroundStyle(FourElementsClassPickerStyle.rust).position(x: 355, y: 99)
            Text("What feels right\nfor you today?")
                .font(.system(size: 21, weight: .medium)).lineSpacing(3)
                .multilineTextAlignment(.center).foregroundStyle(FourElementsClassPickerStyle.color(0x9D7B64))
                .frame(width: 303, height: 62).position(x: 196.5, y: 194)
        }
        .frame(width: 393, height: 225, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func tileGrid(_ pose: FourElementsClassPickerPose) -> some View {
        VStack(spacing: 18) {
            HStack(spacing: 13) {
                tile(index: 0, card: pose.cards[0], selected: pose.selected)
                tile(index: 1, card: pose.cards[1], selected: pose.selected)
            }
            HStack(spacing: 13) {
                tile(index: 2, card: pose.cards[2], selected: pose.selected)
                tile(index: 3, card: pose.cards[3], selected: pose.selected)
            }
        }
        .frame(width: 331, height: 332)
        .highPriorityGesture(gridDrag)
    }

    private func tile(index: Int, card: FourElementsClassPickerCardPose, selected: Int) -> some View {
        let item = FourElementsClassPickerStyle.items[index]
        return Button { select(index) } label: {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 12)
                    .fill(FourElementsClassPickerStyle.tile)
                    .overlay(RoundedRectangle(cornerRadius: 12).fill(item.color.opacity(0.035 * card.selection)))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(FourElementsClassPickerStyle.color(0xF4EFE8), lineWidth: 1))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(item.color.opacity(0.4 * card.selection), lineWidth: 1))
                    .shadow(color: .black.opacity(0.07), radius: 2, y: 1)
                    .shadow(color: FourElementsClassPickerStyle.color(0x89725E).opacity(0.10 + card.selection * 0.04),
                            radius: 8 + card.selection * 6, y: 5 + card.selection * 4)
                FourElementsClassPickerMarkView(index: index, card: card)
                    .frame(width: 84, height: 84).position(x: 79.5, y: 61)
                Text(item.name).font(.system(size: 9, weight: .semibold)).tracking(2)
                    .foregroundStyle(item.color).position(x: 79.5, y: 115)
                Text(item.discipline).font(.system(size: 7, weight: .medium)).tracking(1.3)
                    .foregroundStyle(FourElementsClassPickerStyle.color(0xA99786)).position(x: 79.5, y: 134)
                Capsule().fill(item.color.opacity(0.38 * card.selection))
                    .frame(width: 46 * card.progress, height: 0.8)
                    .position(x: 79.5, y: 124)
            }
            .frame(width: 159, height: 157, alignment: .topLeading)
            .offset(y: -card.lift)
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .frame(width: 159, height: 157)
        .accessibilityLabel("\(item.name), \(item.discipline)")
        .accessibilityValue(selected == index ? "Selected" : "")
    }

    private func detailPanel(_ pose: FourElementsClassPickerPose) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 15).fill(FourElementsClassPickerStyle.color(0xEBE3D4))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(Color.white.opacity(0.40), lineWidth: 1))
            detailContent(index: pose.detailFrom)
                .opacity(max(0, 1 - pose.detailMix * 2)).offset(y: -4 * pose.detailMix)
            detailContent(index: pose.detailTo)
                .opacity(max(0, pose.detailMix * 2 - 1)).offset(y: 4 * (1 - pose.detailMix))
        }
        .frame(width: 331, height: 101, alignment: .topLeading)
        .contentShape(RoundedRectangle(cornerRadius: 15))
        .gesture(detailDrag)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(FourElementsClassPickerStyle.items[pose.detailMix < 0.5 ? pose.detailFrom : pose.detailTo].className)
        .accessibilityHint("Swipe left or right to choose a class")
    }

    private func detailContent(index: Int) -> some View {
        let item = FourElementsClassPickerStyle.items[index]
        return ZStack(alignment: .topLeading) {
            FourElementsClassPickerMarkView(index: index, card: .complete)
                .frame(width: 26, height: 26).position(x: 31, y: 34)
            Text(item.className).font(.system(size: 10, weight: .semibold)).tracking(1.1)
                .foregroundStyle(FourElementsClassPickerStyle.color(0x8A684F))
                .frame(width: 246, height: 15, alignment: .leading).offset(x: 57, y: 16)
            Text(item.time).font(.system(size: 11))
                .foregroundStyle(FourElementsClassPickerStyle.color(0x9A8571))
                .frame(width: 246, height: 16, alignment: .leading).offset(x: 57, y: 36)
            Text(item.line).font(.system(size: 11))
                .foregroundStyle(FourElementsClassPickerStyle.color(0x9E8772))
                .frame(width: 295, height: 17, alignment: .leading).offset(x: 18, y: 63)
        }
        .frame(width: 331, height: 101, alignment: .topLeading)
    }

    private func reserveButton(_ pose: FourElementsClassPickerPose, now: Date) -> some View {
        let held = spaceHeld && now.timeIntervalSince(lastTouch) < 2.4
        return Button { reserve() } label: {
            HStack(spacing: 12) {
                Text(held ? "Space held for you" : "Reserve your space")
                    .font(.system(size: 12, weight: .medium))
                Spacer()
                ZStack {
                    Text(FourElementsClassPickerStyle.items[pose.detailFrom].price).opacity(max(0, 1 - pose.detailMix * 2))
                    Text(FourElementsClassPickerStyle.items[pose.detailTo].price).opacity(max(0, pose.detailMix * 2 - 1))
                }.font(.system(size: 13, weight: .medium))
                Image(systemName: held ? "checkmark" : "arrow.right").font(.system(size: 12, weight: .medium))
            }
            .foregroundStyle(FourElementsClassPickerStyle.color(0xFCF6ED))
            .padding(.horizontal, 24)
            .frame(width: 331, height: 48)
            .background(FourElementsClassPickerStyle.color(0x9D6045), in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(held ? "Space held for you" : "Reserve your space")
    }

    private var gridDrag: some Gesture {
        DragGesture(minimumDistance: 3)
            .onChanged { value in
                let column = value.location.x >= 172 ? 1 : 0
                let row = value.location.y >= 175 ? 1 : 0
                let index = row * 2 + column
                if index != dragIndex {
                    dragIndex = index
                    let speed = hypot(value.velocity.width, value.velocity.height)
                    select(index, duration: speed > 500 ? 0.42 : 0.65)
                }
                dragging = true
                lastTouch = Date()
                manualLive = true
            }
            .onEnded { _ in
                dragging = false
                dragIndex = -1
                lastTouch = Date()
            }
    }

    private var detailDrag: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                if !detailDragging {
                    detailStartIndex = currentPose(at: Date()).selected
                    select(detailStartIndex)
                    detailDragging = true
                }
                let projected = Double(detailStartIndex) - value.translation.width / 86
                let index = min(3, max(0, Int(projected.rounded())))
                if index != manualIndex || !manualLive { select(index) }
                lastTouch = Date()
                manualLive = true
            }
            .onEnded { value in
                let projected = Double(detailStartIndex) - value.translation.width / 86 - value.velocity.width * 0.0009
                let index = min(3, max(0, Int(projected.rounded())))
                if index != manualIndex { select(index) }
                detailDragging = false
                lastTouch = Date()
            }
    }

    private func select(_ index: Int, duration: Double = 0.65) {
        let now = Date()
        manualBase = currentPose(at: now)
        manualIndex = index
        traceStarted = now
        traceDuration = duration
        lastTouch = now
        manualLive = true
        spaceHeld = false
        if now.timeIntervalSince(lastHaptic) > 0.12 {
            lastHaptic = now
            UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
        }
    }

    private func reserve() {
        let now = Date()
        let chosen = currentPose(at: now).selected
        select(chosen)
        traceStarted = now.addingTimeInterval(-traceDuration)
        spaceHeld = true
    }

    private func currentPose(at now: Date) -> FourElementsClassPickerPose {
        if let previewTime { return reduceMotion ? .reduced(index: manualIndex) : Self.loopPose(previewTime) }
        if reduceMotion { return .reduced(index: manualIndex) }
        let touchAge = now.timeIntervalSince(lastTouch)
        if touchAge < 2.6 || dragging || detailDragging {
            let traceAge = max(0, now.timeIntervalSince(traceStarted))
            let progress = Self.ease(traceAge / traceDuration)
            let tip = Self.clamp(traceAge / 0.04) * (1 - Self.clamp((traceAge - traceDuration) / 0.12))
            let pose = FourElementsClassPickerPose.manual(
                from: manualBase, index: manualIndex, progress: progress, tip: tip,
                lift: 4 * Self.liftSpring(traceAge)
            )
            return (dragging || detailDragging)
                ? pose
                : pose.returningToRest(Self.ease((touchAge - 2) / 0.6))
        }
        guard autoPlay else { return .rest }
        let time = lastTouch == .distantPast ? now.timeIntervalSince(epoch) : touchAge - 2.6
        return Self.loopPose(time)
    }

    private static func loopPose(_ rawTime: Double) -> FourElementsClassPickerPose {
        let t = (rawTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        if t < 1.4 || t >= 7.6 { return .rest }
        let index: Int
        let previous: Int
        let start: Double
        let duration: Double
        let detailStart: Double
        let detailEnd: Double
        if t < 2.15 {
            index = 1; previous = 0; start = 1.4; duration = 0.65; detailStart = 1.9; detailEnd = 2.15
        } else if t < 2.9 {
            index = 2; previous = 1; start = 2.15; duration = 0.65; detailStart = 2.65; detailEnd = 2.9
        } else if t < 5 {
            index = 3; previous = 2; start = 2.9; duration = 0.9; detailStart = 3.65; detailEnd = 3.95
        } else {
            index = 0; previous = 3; start = 5; duration = 1.1; detailStart = 5.95; detailEnd = 6.25
        }
        let age = t - start
        let raw = clamp(age / duration)
        let progress = index == 1 || index == 2 ? 1 - pow(1 - raw, 3) : ease(raw)
        let tip = clamp(age / 0.04) * (1 - clamp((age - duration) / 0.12))
        var pose = FourElementsClassPickerPose.transition(
            index: index, previous: previous, progress: progress, tip: tip,
            lift: 4 * liftSpring(age), previousLift: previous == 0 ? 0 : 4
        )
        pose.detailFrom = previous
        pose.detailTo = index
        pose.detailMix = ease((t - detailStart) / (detailEnd - detailStart))
        if t >= 6.25 { pose.cards[0].lift = 4 * (1 - ease((t - 6.25) / 1.35)) }
        return pose
    }

    private static func liftSpring(_ age: Double) -> Double {
        guard age < 0.65 else { return 1 }
        return max(0, min(1.045, 1 - exp(-10 * age) * (cos(14 * age) + (10 / 14.0) * sin(14 * age))))
    }

    private static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private static func ease(_ value: Double) -> Double {
        let u = clamp(value)
        return u * u * (3 - 2 * u)
    }
    private static func hash(_ value: Double) -> Double {
        let n = sin(value * 12.9898) * 43758.5453
        return n - floor(n)
    }
}

private struct FourElementsClassPickerCardPose {
    var guide = 0.44
    var oldProgress = 1.0
    var oldAlpha = 0.0
    var progress = 0.0
    var alpha = 0.0
    var selection = 0.0
    var lift = 0.0
    var tip = 0.0
    static let complete = FourElementsClassPickerCardPose(guide: 0.13, progress: 1, alpha: 1)
}

private struct FourElementsClassPickerPose {
    var cards: [FourElementsClassPickerCardPose]
    var selected: Int
    var detailFrom: Int
    var detailTo: Int
    var detailMix: Double

    static var rest: Self {
        var cards = Array(repeating: FourElementsClassPickerCardPose(), count: 4)
        cards[0] = .complete
        cards[0].selection = 1
        return .init(cards: cards, selected: 0, detailFrom: 0, detailTo: 0, detailMix: 1)
    }

    static func reduced(index: Int) -> Self {
        var cards = Array(repeating: FourElementsClassPickerCardPose.complete, count: 4)
        cards[index].selection = 1
        return .init(cards: cards, selected: index, detailFrom: index, detailTo: index, detailMix: 1)
    }

    static func transition(index: Int, previous: Int, progress: Double, tip: Double,
                           lift: Double, previousLift: Double) -> Self {
        var cards = Array(repeating: FourElementsClassPickerCardPose(), count: 4)
        let selection = smooth(progress / 0.6)
        cards[previous].oldAlpha = 1 - smooth(progress / 0.65)
        cards[previous].selection = 1 - selection
        cards[previous].lift = previousLift * (1 - selection)
        cards[index].guide = 0.44 - 0.31 * smooth(progress / 0.3)
        cards[index].progress = progress
        cards[index].alpha = 1
        cards[index].selection = selection
        cards[index].lift = lift
        cards[index].tip = tip
        return .init(cards: cards, selected: index, detailFrom: previous, detailTo: index, detailMix: progress)
    }

    static func manual(from base: Self, index: Int, progress: Double, tip: Double, lift: Double) -> Self {
        var cards = base.cards
        let fade = smooth(progress / 0.65)
        for i in 0..<4 {
            let old = base.cards[i]
            cards[i].oldProgress = old.alpha >= old.oldAlpha ? old.progress : old.oldProgress
            cards[i].oldAlpha = max(old.alpha, old.oldAlpha) * (1 - fade)
            cards[i].alpha = 0
            cards[i].guide = old.guide + (0.44 - old.guide) * fade
            cards[i].selection = old.selection * (1 - fade)
            cards[i].lift = old.lift * (1 - fade)
            cards[i].tip = 0
        }
        cards[index].guide = base.cards[index].guide + (0.13 - base.cards[index].guide) * fade
        cards[index].progress = progress
        cards[index].alpha = 1
        cards[index].selection = base.cards[index].selection + (1 - base.cards[index].selection) * fade
        cards[index].lift = base.cards[index].lift + (lift - base.cards[index].lift) * fade
        cards[index].tip = tip
        let previous = base.detailMix < 0.5 ? base.detailFrom : base.detailTo
        return .init(cards: cards, selected: index, detailFrom: previous, detailTo: index,
                     detailMix: smooth((progress - 0.75) / 0.25))
    }

    func returningToRest(_ amount: Double) -> Self {
        guard amount > 0 else { return self }
        let rest = Self.rest
        var result = self
        for i in 0..<4 {
            result.cards[i].guide += (rest.cards[i].guide - cards[i].guide) * amount
            result.cards[i].oldAlpha *= 1 - amount
            result.cards[i].progress += (rest.cards[i].progress - cards[i].progress) * amount
            result.cards[i].alpha += (rest.cards[i].alpha - cards[i].alpha) * amount
            result.cards[i].selection += (rest.cards[i].selection - cards[i].selection) * amount
            result.cards[i].lift *= 1 - amount
            result.cards[i].tip *= 1 - amount
        }
        result.detailFrom = detailTo
        result.detailTo = 0
        result.detailMix = amount
        result.selected = amount < 0.5 ? selected : 0
        return result
    }

    private static func smooth(_ value: Double) -> Double {
        let u = min(1, max(0, value))
        return u * u * (3 - 2 * u)
    }
}

private struct FourElementsClassPickerMarkView: View {
    var index: Int
    var card: FourElementsClassPickerCardPose

    var body: some View {
        Canvas { context, size in
            let mark = FourElementsClassPickerMarks.all[index]
            let ink = FourElementsClassPickerStyle.items[index].color
            context.scaleBy(x: size.width / 100, y: size.height / 100)
            let style = StrokeStyle(lineWidth: 3.1, lineCap: .round, lineJoin: .round)
            context.stroke(mark.path, with: .color(ink.opacity(card.guide)), style: style)
            if card.oldAlpha > 0.001 {
                context.stroke(mark.path.trimmedPath(from: 0, to: card.oldProgress),
                               with: .color(ink.opacity(card.oldAlpha)), style: style)
            }
            if card.alpha > 0.001 && card.progress > 0.0001 {
                context.stroke(mark.path.trimmedPath(from: 0, to: card.progress),
                               with: .color(ink.opacity(card.alpha)),
                               style: StrokeStyle(lineWidth: 3.1 + 0.5 * card.tip, lineCap: .round, lineJoin: .round))
            }
            if card.tip > 0.001 && card.progress > 0 && card.progress < 1.0001 {
                let tail = mark.path.trimmedPath(from: max(0, card.progress - 0.12), to: card.progress)
                context.stroke(tail, with: .color(Color.white.opacity(0.35 * card.tip)),
                               style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                let point = mark.point(at: card.progress)
                let glow = Path(ellipseIn: CGRect(x: point.x - 6, y: point.y - 6, width: 12, height: 12))
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: 4))
                    layer.fill(glow, with: .color(ink.opacity(0.22 * card.tip)))
                }
                let tip = Path(ellipseIn: CGRect(x: point.x - 3.2, y: point.y - 3.2, width: 6.4, height: 6.4))
                context.fill(tip, with: .color(ink.opacity(card.tip)))
                let core = Path(ellipseIn: CGRect(x: point.x - 1, y: point.y - 1, width: 2, height: 2))
                context.fill(core, with: .color(Color.white.opacity(0.65 * card.tip)))
            }
        }
        .accessibilityHidden(true)
    }
}

private struct FourElementsClassPickerMark {
    let path: Path
    let lookup: [CGPoint]
    func point(at fraction: Double) -> CGPoint {
        let position = min(127, max(0, fraction * 127))
        let lower = Int(position)
        let upper = min(127, lower + 1)
        let u = position - Double(lower)
        return CGPoint(x: lookup[lower].x + (lookup[upper].x - lookup[lower].x) * u,
                       y: lookup[lower].y + (lookup[upper].y - lookup[lower].y) * u)
    }
}

private struct FourElementsClassPickerMarkBuilder {
    var path = Path()
    var points: [CGPoint] = []
    var distances: [Double] = []
    var length = 0.0
    var current = CGPoint.zero

    mutating func move(_ x: Double, _ y: Double) {
        current = CGPoint(x: x, y: y)
        path.move(to: current)
        points.append(current); distances.append(length)
    }
    mutating func curve(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double, _ y: Double) {
        let start = current
        let end = CGPoint(x: x, y: y)
        let c1 = CGPoint(x: x1, y: y1), c2 = CGPoint(x: x2, y: y2)
        path.addCurve(to: end, control1: c1, control2: c2)
        for step in 1...24 {
            let u = Double(step) / 24, v = 1 - u
            let p = CGPoint(x: v * v * v * start.x + 3 * v * v * u * c1.x + 3 * v * u * u * c2.x + u * u * u * end.x,
                            y: v * v * v * start.y + 3 * v * v * u * c1.y + 3 * v * u * u * c2.y + u * u * u * end.y)
            length += hypot(p.x - current.x, p.y - current.y)
            points.append(p); distances.append(length); current = p
        }
    }
    mutating func circle(_ x: Double, _ y: Double, _ r: Double) {
        let k = r * 0.5522847498
        move(x, y - r)
        curve(x + k, y - r, x + r, y - k, x + r, y)
        curve(x + r, y + k, x + k, y + r, x, y + r)
        curve(x - k, y + r, x - r, y + k, x - r, y)
        curve(x - r, y - k, x - k, y - r, x, y - r)
    }
    func finish() -> FourElementsClassPickerMark {
        var lookup: [CGPoint] = []
        var cursor = 1
        for step in 0..<128 {
            let wanted = Double(step) / 127 * length
            while cursor < distances.count - 1 && distances[cursor] < wanted { cursor += 1 }
            let previous = max(0, cursor - 1)
            let span = distances[cursor] - distances[previous]
            let u = span > 0 ? (wanted - distances[previous]) / span : 1
            lookup.append(CGPoint(x: points[previous].x + (points[cursor].x - points[previous].x) * u,
                                  y: points[previous].y + (points[cursor].y - points[previous].y) * u))
        }
        return FourElementsClassPickerMark(path: path, lookup: lookup)
    }
}

private enum FourElementsClassPickerMarks {
    static let all: [FourElementsClassPickerMark] = [ground(), breathe(), kindle(), flow()]
    private static func ground() -> FourElementsClassPickerMark {
        var b = FourElementsClassPickerMarkBuilder()
        b.move(50, 9); b.curve(17, 32, 16, 65, 50, 91); b.curve(84, 65, 83, 32, 50, 9)
        b.move(50, 21); b.curve(39, 42, 40, 63, 50, 81)
        b.circle(79, 14, 3.5)
        return b.finish()
    }
    private static func breathe() -> FourElementsClassPickerMark {
        var b = FourElementsClassPickerMarkBuilder()
        b.move(50, 40); b.curve(34, 43, 31, 56, 48, 64); b.curve(62, 71, 74, 75, 81, 82)
        b.curve(89, 91, 66, 96, 50, 78); b.curve(34, 96, 11, 91, 19, 82)
        b.curve(28, 72, 42, 70, 55, 63); b.curve(69, 54, 66, 43, 50, 40)
        b.circle(50, 25, 8)
        return b.finish()
    }
    private static func kindle() -> FourElementsClassPickerMark {
        var b = FourElementsClassPickerMarkBuilder()
        b.move(43, 92); b.curve(66, 72, 62, 53, 48, 35); b.curve(40, 23, 33, 15, 23, 12)
        b.move(20, 39); b.curve(36, 45, 47, 48, 73, 36)
        b.move(49, 60); b.curve(36, 74, 35, 83, 33, 92)
        b.circle(58, 16, 4.5); b.circle(80, 28, 3)
        return b.finish()
    }
    private static func flow() -> FourElementsClassPickerMark {
        var b = FourElementsClassPickerMarkBuilder()
        b.move(9, 66); b.curve(22, 25, 37, 29, 49, 55); b.curve(62, 83, 77, 82, 91, 43)
        b.move(14, 47); b.curve(29, 17, 44, 32, 55, 46)
        b.circle(84, 19, 3.5)
        return b.finish()
    }
}

private struct FourElementsClassPickerItem {
    var name: String
    var discipline: String
    var color: Color
    var className: String
    var time: String
    var line: String
    var price: String
}

private enum FourElementsClassPickerStyle {
    static let paper = color(0xF6F2E9)
    static let tile = color(0xFFFDFA)
    static let rust = color(0x985B40)
    static let items: [FourElementsClassPickerItem] = [
        .init(name: "GROUND", discipline: "PILATES", color: color(0xC7A186), className: "GROUNDING PILATES",
              time: "Today · 10:30 · 45 min", line: "A little strength. A softer landing.", price: "18"),
        .init(name: "BREATHE", discipline: "BREATHWORK", color: rust, className: "OPEN AIR BREATHWORK",
              time: "Today · 12:00 · 20 min", line: "Find a little more room inside.", price: "12"),
        .init(name: "KINDLE", discipline: "MOVEMENT", color: color(0xC5A15B), className: "EMBER MOVEMENT",
              time: "Today · 17:30 · 50 min", line: "Move with warmth, leave with light.", price: "22"),
        .init(name: "FLOW", discipline: "SLOW YOGA", color: color(0x8E9F96), className: "SLOW CURRENT YOGA",
              time: "Today · 18:15 · 40 min", line: "Let the day loosen its grip.", price: "20")
    ]
    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255)
    }
}
