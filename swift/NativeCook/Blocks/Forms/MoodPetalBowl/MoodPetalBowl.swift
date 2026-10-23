import SwiftUI
import UIKit

struct MoodPetalBowl: View {
    static let loop = MoodPetalBowlMath.loop
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var bowlRecognizing = false
    @GestureState private var labelRecognizing = false
    @State private var epoch = Date()
    @State private var visible = false
    @State private var records = MoodPetalBowlMath.initialRecords
    @State private var chosenMood = MoodPetalBowlMood.calm
    @State private var chosenDay = 3
    @State private var event: MoodPetalBowlEvent?
    @State private var session: MoodPetalBowlSession?
    @State private var blockedOwners: Set<MoodPetalBowlOwner> = []
    @State private var quietSave: MoodPetalBowlQuietSave?
    @State private var displayed = MoodPetalBowlDisplayed()

    private var paused: Bool {
        !visible || scenePhase != .active || reduced || previewTime != nil || session != nil || (!autoPlay && event == nil)
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 393
            let height = max(852, geometry.size.height / scale)
            let bottomLift = max(0, geometry.safeAreaInsets.bottom / scale - 34)
            TimelineView(.animation(paused: paused)) { timeline in
                let scene = scene(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    MoodPetalBowlArt(scene: scene, logicalHeight: height)
                        .allowsHitTesting(false).accessibilityHidden(true)
                    Canvas { _, _ in
                        displayed.scene = scene
                        displayed.date = timeline.date
                    }.allowsHitTesting(false).accessibilityHidden(true)
                    nativeContent(scene: scene, bottomLift: bottomLift)
                }
                .frame(width: 393, height: height, alignment: .topLeading)
                .coordinateSpace(name: "MoodPetalBowlLogical")
                .scaleEffect(scale, anchor: .topLeading)
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            }
        }
        .background(Color(moodPetalBowlHex: 0xF4F1EA))
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            _ = MoodPetalBowlResources.shared
            epoch = Date()
            visible = true
        }
        .onDisappear { visible = false; clearTransient() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { clearTransient() }
            else { epoch = Date() }
        }
        .onChange(of: reduced) { _, _ in clearTransient() }
        .onChange(of: autoPlay) { _, _ in
            if session == nil && event == nil { epoch = Date() }
        }
        .onChange(of: bowlRecognizing) { _, value in
            if !value { recognizerStopped(.bowl) }
        }
        .onChange(of: labelRecognizing) { _, value in
            if !value { recognizerStopped(.label) }
        }
        .task(id: event?.id) {
            guard let current = event else { return }
            do { try await Task.sleep(for: .seconds(2.70)) } catch { return }
            guard event?.id == current.id, session == nil else { return }
            event = nil
            chosenMood = .calm; chosenDay = 3
            epoch = current.date.addingTimeInterval(2.70)
        }
        .task(id: quietSave?.id) {
            guard let current = quietSave else { return }
            do { try await Task.sleep(for: .seconds(2)) } catch { return }
            if quietSave?.id == current.id { quietSave = nil }
        }
    }

    private func nativeContent(scene: MoodPetalBowlScene, bottomLift: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Text("Today I feel")
                .font(.system(size: 16)).foregroundStyle(Color(moodPetalBowlHex: 0x191A17))
                .frame(width: 225, height: 22).position(x: 196.5, y: 181.5)

            Color.clear.contentShape(Rectangle())
                .frame(width: 323, height: 293).position(x: 196.5, y: 283.5)
                .gesture(bowlGesture)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(scene.dominantMood.title.capitalized) mood, selected \(MoodPetalBowlMath.days[scene.selectedDay]) \(12 + scene.selectedDay)")
                .accessibilityHint("Swipe up or down to change mood. Drag to stir the bowl.")
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: chooseMood(scene.dominantMood.stepped(1), direction: 1)
                    case .decrement: chooseMood(scene.dominantMood.stepped(-1), direction: -1)
                    @unknown default: break
                    }
                }

            ForEach(MoodPetalBowlMood.allCases, id: \.rawValue) { mood in
                Text(mood.title).font(.system(size: 49.5, weight: .regular))
                    .tracking(0.6).foregroundStyle(Color(moodPetalBowlHex: 0x191A17))
                    .frame(width: 273, height: 55)
                    .opacity(scene.labelWeights[mood.rawValue])
                    .position(x: 196.5 + scene.labelOffsets[mood.rawValue], y: 478.5)
                    .allowsHitTesting(false).accessibilityHidden(true)
            }
            Color.clear.contentShape(Rectangle())
                .frame(width: 273, height: 55).position(x: 196.5, y: 478.5)
                .gesture(labelGesture).accessibilityHidden(true)

            Button { chooseMood(scene.dominantMood.stepped(-1), direction: -1) } label: {
                MoodPetalBowlTriangle(right: false).fill()
                    .frame(width: 6.5, height: 10)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(Color(moodPetalBowlHex: 0x191A17))
                .position(x: 43, y: 479.5).accessibilityLabel("Previous mood")
            Button { chooseMood(scene.dominantMood.stepped(1), direction: 1) } label: {
                MoodPetalBowlTriangle(right: true).fill()
                    .frame(width: 6.5, height: 10)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(Color(moodPetalBowlHex: 0x191A17))
                .position(x: 350, y: 479.5).accessibilityLabel("Next mood")

            Text("Saved for \(MoodPetalBowlMath.days[scene.captionDay])")
                .font(.system(size: 11.5)).foregroundStyle(Color(moodPetalBowlHex: 0x827F77))
                .frame(width: 273, height: 18).opacity(scene.captionAlpha)
                .position(x: 196.5, y: 526)
                .accessibilityHidden(scene.captionAlpha < 0.5)

            RoundedRectangle(cornerRadius: 22)
                .fill(Color(moodPetalBowlHex: 0xEDE8DE))
                .frame(width: 44, height: 88).position(x: scene.capsuleX, y: 603)
                .allowsHitTesting(false).accessibilityHidden(true)
            ForEach(0..<7, id: \.self) { day in
                let x = MoodPetalBowlMath.dateX[day]
                let dot = scene.dots[day]
                Circle().fill(Color(red: dot.r, green: dot.g, blue: dot.b, opacity: dot.a))
                    .frame(width: 6, height: 6).position(x: x, y: 574)
                    .allowsHitTesting(false).accessibilityHidden(true)
                Text(["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][day])
                    .font(.system(size: 14)).foregroundStyle(Color(moodPetalBowlHex: 0x827F77))
                    .position(x: x, y: 596).allowsHitTesting(false).accessibilityHidden(true)
                Text("\(12 + day)").font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color(moodPetalBowlHex: 0x191A17))
                    .position(x: x, y: 619.2).allowsHitTesting(false).accessibilityHidden(true)
                Button { chooseDay(day) } label: {
                    Color.clear.frame(width: 44, height: 88).contentShape(Rectangle())
                }.buttonStyle(.plain).position(x: x, y: 603)
                    .accessibilityLabel("\(MoodPetalBowlMath.days[day]) \(12 + day)")
                    .accessibilityValue(records[day]?.title.capitalized ?? "No mood saved")
                    .accessibilityAddTraits(scene.selectedDay == day ? .isSelected : [])
            }
            RoundedRectangle(cornerRadius: 31).fill(Color(moodPetalBowlHex: 0x1D1E19))
                .frame(width: 345, height: 62).position(x: 196.5, y: 785 - bottomLift)
                .allowsHitTesting(false).accessibilityHidden(true)
            Button(action: save) {
                Image(systemName: "plus").font(.system(size: 24, weight: .regular))
                    .foregroundStyle(Color(moodPetalBowlHex: 0x10A97B))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).position(x: 82, y: 785 - bottomLift)
                .accessibilityLabel("Save mood")
            MoodPetalBowlFooterGlyph(kind: .sliders)
                .position(x: 159, y: 785 - bottomLift).accessibilityHidden(true)
            MoodPetalBowlFooterGlyph(kind: .friends)
                .position(x: 236, y: 785 - bottomLift).accessibilityHidden(true)
            MoodPetalBowlFooterGlyph(kind: .bars)
                .position(x: 313, y: 785 - bottomLift).accessibilityHidden(true)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func scene(at date: Date) -> MoodPetalBowlScene {
        if reduced {
            var s = MoodPetalBowlMath.settled(chosenMood, day: chosenDay, records: records)
            if let saved = quietSave { s.captionAlpha = 1; s.captionDay = saved.day }
            return s
        }
        if let previewTime { return MoodPetalBowlMath.authored(previewTime, records: records) }
        if let session { return session.current }
        if let event {
            let age = max(0, date.timeIntervalSince(event.date))
            if age < 2 { return eventScene(event, age: age) }
            if age < 2.70 {
                return MoodPetalBowlMath.recovering(from: eventScene(event, age: 2), elapsed: age - 2, records: records)
            }
            return autoPlay ? MoodPetalBowlMath.authored(age - 2.70, records: records) : MoodPetalBowlMath.rest(records: records)
        }
        return autoPlay ? MoodPetalBowlMath.authored(max(0, date.timeIntervalSince(epoch)), records: records) : MoodPetalBowlMath.rest(records: records)
    }
    private func eventScene(_ event: MoodPetalBowlEvent, age: Double) -> MoodPetalBowlScene {
        switch event.kind {
        case let .transition(plan): return MoodPetalBowlMath.transitioning(plan, elapsed: age)
        case let .release(from, base, cancelled): return MoodPetalBowlMath.released(from: from, base: base, elapsed: age, cancelled: cancelled)
        case let .hold(scene): return scene
        }
    }
    private func snapshot() -> MoodPetalBowlScene { displayed.scene ?? scene(at: Date()) }
    private var canAct: Bool { visible && scenePhase == .active && previewTime == nil && session == nil }
    private func chooseMood(_ mood: MoodPetalBowlMood, direction: Double) {
        guard canAct else { return }
        choose(mood, day: snapshot().selectedDay, direction: direction)
    }
    private func chooseDay(_ day: Int) {
        guard canAct else { return }
        let old = snapshot()
        choose(records[day] ?? .calm, day: day, direction: day < old.selectedDay ? -1 : 1)
    }
    private func choose(_ mood: MoodPetalBowlMood, day: Int, direction: Double, velocity: Double = 0,
                        captured: MoodPetalBowlScene? = nil) {
        let old = captured ?? snapshot()
        chosenMood = mood; chosenDay = day; quietSave = nil
        if reduced { event = nil }
        else {
            let plan = MoodPetalBowlMath.transition(from: old, mood: mood, day: day, records: records,
                                                   direction: direction, velocityX: velocity)
            event = .init(date: Date(), kind: .transition(plan))
        }
        impact()
    }
    private func save() {
        guard canAct else { return }
        var current = snapshot()
        let day = current.selectedDay, mood = current.dominantMood
        records[day] = mood; chosenMood = mood; chosenDay = day
        current.dots[day] = .init(hex: mood.hex)
        current.captionAlpha = 1; current.captionDay = day
        if reduced { quietSave = .init(day: day) }
        else { event = .init(date: Date(), kind: .hold(current)) }
        impact()
    }
    private func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.65) }

    private var bowlGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("MoodPetalBowlLogical"))
            .updating($bowlRecognizing) { _, active, _ in active = true }
            .onChanged { update(.bowl, value: $0, origin: .zero) }
            .onEnded { finish(.bowl, value: $0, origin: .zero, cancelled: false) }
    }
    private var labelGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("MoodPetalBowlLogical"))
            .updating($labelRecognizing) { _, active, _ in active = true }
            .onChanged { update(.label, value: $0, origin: .zero) }
            .onEnded { finish(.label, value: $0, origin: .zero, cancelled: false) }
    }
    private func update(_ owner: MoodPetalBowlOwner, value: DragGesture.Value, origin: CGPoint) {
        guard !reduced, previewTime == nil, visible, scenePhase == .active else { return }
        guard !blockedOwners.contains(owner) else { return }
        let point = CGPoint(x: value.location.x + origin.x, y: value.location.y + origin.y)
        if let active = session, active.owner != owner { blockedOwners.insert(owner); return }
        guard var active = session else {
            let capture = snapshot()
            event = nil; quietSave = nil
            session = .init(owner: owner, base: capture, current: capture, anchor: point, lastPoint: point, lastDate: value.time)
            return
        }
        // A stationary callback is not a fresh velocity sample. The first Down is exact.
        guard active.lastPoint != point else { return }
        let dt = max(1.0 / 240, value.time.timeIntervalSince(active.lastDate))
        let velocity = CGSize(width: (point.x - active.lastPoint.x) / dt, height: (point.y - active.lastPoint.y) / dt)
        active.lastPoint = point; active.lastDate = value.time; active.velocity = velocity
        active.translation = CGSize(width: point.x - active.anchor.x, height: point.y - active.anchor.y)
        if owner == .bowl {
            active.current = MoodPetalBowlMath.stirring(from: active.base, anchor: active.anchor,
                                                       translation: active.translation, velocity: velocity)
            let distance = zip(active.base.tokens, active.current.tokens).map { hypot($0.center.x-$1.center.x,$0.center.y-$1.center.y) }.max() ?? 0
            if !active.haptic && distance >= 3 { active.haptic = true; impact() }
        }
        session = active
    }
    private func finish(_ owner: MoodPetalBowlOwner, value: DragGesture.Value?, origin: CGPoint, cancelled: Bool) {
        guard !blockedOwners.contains(owner) else { blockedOwners.remove(owner); return }
        guard session?.owner == owner else { return }
        if let value { update(owner, value: value, origin: origin) }
        guard let active = session else { return }
        session = nil
        if owner == .label && !cancelled && abs(active.translation.width) >= 48 {
            let step = active.translation.width < 0 ? 1 : -1
            let sampleAge = max(0, (value?.time ?? Date()).timeIntervalSince(active.lastDate))
            let releaseVelocity = active.velocity.width * (1 - MoodPetalBowlMath.smooth(sampleAge / 0.12))
            choose(active.base.dominantMood.stepped(step), day: active.base.selectedDay, direction: Double(step),
                   velocity: releaseVelocity, captured: active.current)
        } else if owner == .bowl {
            event = .init(date: Date(), kind: .release(from: active.current, base: active.base, cancelled: cancelled))
        } else { event = .init(date: Date(), kind: .hold(active.current)) }
    }
    private func recognizerStopped(_ owner: MoodPetalBowlOwner) {
        let identity = session?.owner == owner ? session?.id : nil
        Task { @MainActor in
            await Task.yield()
            let active = owner == .bowl ? bowlRecognizing : labelRecognizing
            guard !active else { return }
            if let identity, session?.id == identity {
                finish(owner, value: nil, origin: .zero, cancelled: true)
            } else { blockedOwners.remove(owner) }
        }
    }
    private func clearTransient() {
        session = nil; event = nil; quietSave = nil; blockedOwners.removeAll()
        epoch = Date()
    }
}

private enum MoodPetalBowlOwner: Hashable { case bowl, label }
private struct MoodPetalBowlSession {
    let id = UUID()
    let owner: MoodPetalBowlOwner
    let base: MoodPetalBowlScene
    var current: MoodPetalBowlScene
    let anchor: CGPoint
    var lastPoint: CGPoint
    var lastDate: Date
    var velocity = CGSize.zero
    var translation = CGSize.zero
    var haptic = false
}
private struct MoodPetalBowlEvent {
    enum Kind {
        case transition(MoodPetalBowlTransition)
        case release(from: MoodPetalBowlScene, base: MoodPetalBowlScene, cancelled: Bool)
        case hold(MoodPetalBowlScene)
    }
    let id = UUID()
    let date: Date
    let kind: Kind
}
private struct MoodPetalBowlQuietSave { let id = UUID(); let day: Int }
private final class MoodPetalBowlDisplayed {
    var scene: MoodPetalBowlScene?
    var date = Date()
}
private struct MoodPetalBowlTriangle: Shape {
    let right: Bool
    func path(in rect: CGRect) -> Path {
        Path { p in
            let base = right ? rect.minX : rect.maxX
            let point = right ? rect.maxX : rect.minX
            p.move(to: CGPoint(x: base, y: rect.minY))
            p.addLine(to: CGPoint(x: point, y: rect.midY))
            p.addLine(to: CGPoint(x: base, y: rect.maxY))
            p.closeSubpath()
        }
    }
}
private enum MoodPetalBowlGlyphKind { case sliders, friends, bars }
private struct MoodPetalBowlFooterGlyph: View {
    let kind: MoodPetalBowlGlyphKind
    var body: some View {
        Canvas { context, _ in
            let ink = Color(moodPetalBowlHex: 0xF3F1E7)
            let stroke = StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)
            switch kind {
            case .sliders:
                var lines = Path()
                for y in [8.0, 16.0] {
                    lines.move(to: CGPoint(x: 3, y: y)); lines.addLine(to: CGPoint(x: 21, y: y))
                }
                context.stroke(lines, with: .color(ink), style: stroke)
                for center in [CGPoint(x: 9, y: 8), CGPoint(x: 15, y: 16)] {
                    context.fill(Path(ellipseIn: CGRect(x: center.x - 2.2, y: center.y - 2.2, width: 4.4, height: 4.4)), with: .color(ink))
                }
            case .friends:
                context.fill(Path(ellipseIn: CGRect(x: 5.5, y: 5, width: 5, height: 5)), with: .color(ink))
                context.fill(Path(ellipseIn: CGRect(x: 14, y: 6.2, width: 4, height: 4)), with: .color(ink))
                var busts = Path()
                busts.move(to: CGPoint(x: 2, y: 19))
                busts.addCurve(to: CGPoint(x: 14, y: 19), control1: CGPoint(x: 2, y: 11.6), control2: CGPoint(x: 14, y: 11.6))
                busts.closeSubpath()
                busts.move(to: CGPoint(x: 13.8, y: 18))
                busts.addCurve(to: CGPoint(x: 22, y: 18), control1: CGPoint(x: 13, y: 12), control2: CGPoint(x: 22, y: 12))
                busts.closeSubpath()
                context.fill(busts, with: .color(ink))
            case .bars:
                var bars = Path()
                for (x, height) in zip([6.0, 10.0, 14.0, 18.0], [13.0, 20.0, 16.0, 7.0]) {
                    bars.move(to: CGPoint(x: x, y: 21.5))
                    bars.addLine(to: CGPoint(x: x, y: 21.5 - height))
                }
                context.stroke(bars, with: .color(ink), style: stroke)
            }
        }
        .frame(width: 24, height: 24)
        .allowsHitTesting(false).accessibilityHidden(true)
    }
}
extension Color {
    init(moodPetalBowlHex value: UInt32) {
        self.init(.sRGB, red: Double((value >> 16) & 255) / 255,
                  green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
}
