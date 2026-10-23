import SwiftUI
import UIKit

@MainActor
struct LumaRunStudio: View {
    static let loop = LumaRunStudioMath.loop
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @StateObject private var cache = LumaRunStudioCache(source: LumaRunStudioResources.shared.source)
    @State private var images: [LumaRunStudioBitmap] = []
    @State private var epoch = Date()
    @State private var visible = false
    @State private var generation = 0
    @State private var inputGeneration = 0
    @State private var acceptedSequence = -1
    @State private var manual: LumaRunStudioScene?
    @State private var session: LumaRunStudioSession?
    @State private var event: LumaRunStudioEvent?
    @State private var decoration: LumaRunStudioDecoration?
    @State private var compareTween: LumaRunStudioCompare?
    @State private var handoffStarted = false
    @State private var kept = false
    @State private var previousHandle = LumaRunStudioOwner.low
    @State private var displayed = LumaRunStudioDisplayed()
    private typealias M = LumaRunStudioMath

    private var ready: Bool { images.count == 64 }
    private var paused: Bool {
        if !ready || !visible || scenePhase != .active || previewTime != nil { return true }
        if decoration != nil || compareTween != nil { return false }
        if session != nil { return true }
        if let event { return event.phase != 2 && !event.offsetsMoving }
        return reduced || !autoPlay
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 393
            let height = max(852, geometry.size.height / scale)
            let bottom = max(0, geometry.safeAreaInsets.bottom / scale - 34)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: paused)) { clock in
                    let result = current(at: clock.date)
                    ZStack(alignment: .topLeading) {
                        LumaRunStudioArt(scene: result.0, logicalHeight: height)
                        controls(result.0, bottom: bottom)
                        Canvas { _, _ in
                            displayed.scene = result.0; displayed.velocity = result.1; displayed.date = clock.date
                        }.frame(width: 1, height: 1).allowsHitTesting(false).accessibilityHidden(true)
                    }.frame(width: 393, height: height, alignment: .topLeading)
                }
                LumaRunStudioTouch(enabled: ready && visible && scenePhase == .active && previewTime == nil,
                    generation: inputGeneration, accepts: { owner(at: $0, scene: snapshot()) != nil }, onSample: receive)
                    .frame(width: 393, height: height).accessibilityHidden(true)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading).clipped()
        }
        .ignoresSafeArea().preferredColorScheme(.dark)
        .onAppear { visible = true; epoch = Date() }
        .onDisappear { visible = false; reset(); cache.cancelPreparation() }
        .onChange(of: scenePhase) { _, value in
            if value != .active { reset() }
            else { epoch = Date(); displayed.scene = nil; if ready && reduced { requestReduced() } }
        }
        .onChange(of: reduced) { _, _ in reset(); if ready && reduced { requestReduced() } }
        .onChange(of: autoPlay) { _, _ in reset(); if ready && reduced { requestReduced() } }
        .onChange(of: previewTime) { _, _ in reset(); if ready && reduced { requestReduced() } }
        .task {
            let loaded = await cache.prepareLoop(knots: M.knots)
            guard !Task.isCancelled, loaded.count == 64 else { return }
            images = loaded; epoch = Date(); displayed.scene = nil
            if ready { UIAccessibility.post(notification: .announcement, argument: "Local preview ready") }
            if reduced { requestReduced() }
        }
        .onReceive(cache.$publication) { if let value = $0 { accept(value) } }
        .onReceive(cache.$recovery) { value in
            guard let value, value.generation == generation, value.bitmaps.count == 6 else { return }
            event?.recovery = value.bitmaps
        }
        .task(id: event?.id) { await runEvent() }
        .task(id: decoration?.id) { await finishDecoration() }
        .task(id: compareTween?.id) { await finishCompare() }
    }

    private func controls(_ scene: LumaRunStudioScene, bottom: Double) -> some View {
        let ink = Self.color(0xF0EEDC), secondary = Self.color(0x9EA394)
        return ZStack(alignment: .topLeading) {
            Button {
                guard visible, scenePhase == .active, session == nil else { return }
                reset(); cache.cancelPreparation(); dismiss()
            } label: {
                Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 42, y: 86).accessibilityLabel("Back")
            Text("Luma Run").font(.system(size: 18, weight: .semibold)).foregroundStyle(ink)
                .frame(width: 235, height: 24).position(x: 196.5, y: 86)
            Button(action: save) {
                Image(systemName: kept ? "checkmark" : "square.and.arrow.down").font(.system(size: 18)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 351, y: 86).accessibilityLabel(kept ? "Edit kept" : "Keep edit")
            Color.clear.frame(width: 335, height: 384).position(x: 196.5, y: 328)
                .accessibilityElement().accessibilityLabel("Canyon preview")
                .accessibilityValue("Strength \(Int((scene.params.q * 100).rounded())) percent")
                .accessibilityHint("Drag vertically to sort bright runs. Drag horizontally to move the region.")
                .accessibilityAdjustableAction { direction in adjust(.image, by: direction == .increment ? 0.1 : -0.1) }
            Color.clear.frame(width: 44, height: 44).position(x: 334 + scene.params.roiX, y: 478 + scene.params.roiY)
                .accessibilityElement().accessibilityLabel("Sorting region")
                .accessibilityValue("Horizontal \(Int(scene.params.roiX)), vertical \(Int(scene.params.roiY))")
                .accessibilityAdjustableAction { direction in adjust(.region, by: direction == .increment ? 5 : -5) }
            Text("Pixel sorting").font(.system(size: 15, weight: .medium)).foregroundStyle(ink)
                .frame(width: 190, height: 18, alignment: .leading).position(x: 119, y: 554)
            Text(ready ? "Bright runs · preview ready" : "Preparing local preview…")
                .font(.system(size: 11)).foregroundStyle(secondary)
                .accessibilityLabel(ready ? "Local preview ready" : "Preparing local preview")
                .frame(width: 230, height: 17, alignment: .leading).position(x: 139, y: 574.5)
            Text("Original").font(.system(size: 11, weight: .medium)).foregroundStyle(ink)
                .frame(width: 89, height: 34).background(Self.color(0x242724), in: Capsule())
                .overlay(Capsule().stroke(Self.color(0x484E43), lineWidth: 0.8))
                .position(x: 324.5, y: 554).accessibilityHidden(true)
            Color.clear.frame(width: 89, height: 44).position(x: 324.5, y: 554)
                .accessibilityElement().accessibilityLabel("Original").accessibilityAddTraits(.isButton)
                .accessibilityHint("Hold to compare with the original canyon.").accessibilityAction { toggleCompare() }
            Text("Luma window").font(.system(size: 13, weight: .medium)).foregroundStyle(ink)
                .frame(width: 345, height: 20, alignment: .leading).position(x: 196.5, y: 601)
            Text("\(Int((scene.params.low * 100).rounded()))–\(Int((scene.params.high * 100).rounded()))%")
                .font(.system(size: 12)).monospacedDigit().foregroundStyle(secondary)
                .frame(width: 345, height: 20, alignment: .trailing).position(x: 196.5, y: 601)
            thresholdHit(.low, scene: scene).position(x: 24 + 345 * scene.params.low - scene.appearance.anticipation + scene.appearance.lowOffset, y: 631)
            thresholdHit(.high, scene: scene).position(x: 24 + 345 * scene.params.high + scene.appearance.anticipation + scene.appearance.highOffset, y: 631)
            Text("Direction").font(.system(size: 12, weight: .medium)).foregroundStyle(secondary)
                .frame(width: 240, height: 15, alignment: .leading).position(x: 160, y: 691.5)
            Text("\(Int(scene.params.angle.rounded()))°").font(.system(size: 19)).monospacedDigit().foregroundStyle(ink)
                .frame(width: 240, height: 23, alignment: .leading).position(x: 160, y: 713.5)
            Color.clear.frame(width: 44, height: 44).position(x: 329, y: 700)
                .accessibilityElement().accessibilityLabel("Sort direction")
                .accessibilityValue("\(Int(scene.params.angle.rounded())) degrees")
                .accessibilityAdjustableAction { direction in adjust(.dial, by: direction == .increment ? 15 : -15) }
            Button(action: save) {
                HStack(spacing: 7) {
                    if kept { Image(systemName: "checkmark").font(.system(size: 12, weight: .semibold)).accessibilityHidden(true) }
                    Text(kept ? "Edit kept" : "Keep this edit").font(.system(size: 15, weight: .semibold))
                }.foregroundStyle(Self.color(0x20251A)).frame(width: 345, height: 56)
                    .background(Self.color(0xD8E68B), in: RoundedRectangle(cornerRadius: 15))
            }.buttonStyle(.plain).position(x: 196.5, y: 765 - bottom)
            Text("All changes stay on this device").font(.system(size: 10)).foregroundStyle(secondary)
                .frame(width: 293, height: 15).position(x: 196.5, y: 809.5 - bottom)
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }
    private func thresholdHit(_ which: LumaRunStudioOwner, scene: LumaRunStudioScene) -> some View {
        Color.clear.frame(width: 44, height: 44).accessibilityElement()
            .accessibilityLabel(which == .low ? "Low luma threshold" : "High luma threshold")
            .accessibilityValue("\(Int(((which == .low ? scene.params.low : scene.params.high) * 100).rounded())) percent")
            .accessibilityAdjustableAction { direction in adjust(which, by: direction == .increment ? 0.01 : -0.01) }
    }
    private func original() -> LumaRunStudioScene {
        let bitmap = LumaRunStudioBitmap(image: LumaRunStudioResources.shared.source.canonical, params: .rest)
        return .init(pair: .init(bitmap), params: .rest, appearance: .editing, compare: 0, kept: kept)
    }
    private func current(at date: Date) -> (LumaRunStudioScene, LumaRunStudioParams) {
        guard ready else { return (original(), M.zeroVelocity) }
        if !reduced, let previewTime { return M.authored(previewTime, images: images, kept: kept) }
        if !reduced, session == nil, event == nil {
            return autoPlay ? M.authored(max(0, date.timeIntervalSince(epoch)), images: images, kept: kept) : (original(), M.zeroVelocity)
        }
        var scene = manual ?? original()
        scene.kept = kept
        if let event, !reduced, event.phase == 2, let recovery = event.recovery {
            let age = M.clamp(date.timeIntervalSince(event.date) - 2, 0, 0.60)
            let sample = M.sample(recovery, times: [0, 0.12, 0.24, 0.36, 0.48, 0.60], age: age)
            scene.pair = sample.0; scene.params = sample.1
            let u = M.cubic(age / 0.60)
            scene.appearance.bracketAlpha += (1 - scene.appearance.bracketAlpha) * u
            scene.appearance.handleScale += (1 - scene.appearance.handleScale) * u
            scene.appearance.anticipation *= 1 - u; scene.compare *= 1 - u
            scene.appearance.lowOffset *= 1 - u; scene.appearance.highOffset *= 1 - u
            return (scene, sample.2)
        }
        if let decoration {
            let u = M.smooth(date.timeIntervalSince(decoration.date) / 0.120)
            scene.appearance.bracketAlpha = decoration.from.bracketAlpha + (1 - decoration.from.bracketAlpha) * u
            scene.appearance.handleScale = decoration.from.handleScale + (1 - decoration.from.handleScale) * u
            scene.appearance.anticipation = decoration.from.anticipation * (1 - u)
        }
        if let compareTween {
            scene.compare = compareTween.from + (compareTween.target - compareTween.from) * M.smooth(date.timeIntervalSince(compareTween.date) / compareTween.duration)
        }
        if let event, event.offsetsMoving {
            let age = max(0, date.timeIntervalSince(event.date))
            scene.appearance.lowOffset = M.spring(event.from.appearance.lowOffset, velocity: 0, target: 0, elapsed: age)
            scene.appearance.highOffset = M.spring(event.from.appearance.highOffset, velocity: 0, target: 0, elapsed: age)
        }
        return (scene, M.zeroVelocity)
    }
    private func snapshot() -> LumaRunStudioScene { displayed.scene ?? current(at: Date()).0 }
    private var canAct: Bool { ready && visible && scenePhase == .active && previewTime == nil && session == nil }
    private func newScope(_ scene: LumaRunStudioScene) {
        generation += 1; cache.invalidate(generation: generation); acceptedSequence = -1
        manual = scene; event = nil; decoration = nil; compareTween = nil; handoffStarted = false
    }
    private func accept(_ value: LumaRunStudioPublication) {
        guard value.generation == generation, value.sequence > acceptedSequence, visible else { return }
        acceptedSequence = value.sequence
        let before = snapshot()
        let changed = LumaRunStudioCache.quantized(before.params) != value.bitmap.params
        var next = before; next.pair = .init(value.bitmap); next.params = value.bitmap.params; manual = next
        if changed {
            kept = false
            if !handoffStarted {
                handoffStarted = true; decoration = .init(generation: generation, date: Date(), from: before.appearance)
            }
        }
        if event?.settlementDone == true { prepareRecoveryIfReady() }
    }
    private func requestReduced() {
        newScope(original())
        cache.requestManual(params: .reduced, generation: generation)
    }
    private func owner(at p: CGPoint, scene: LumaRunStudioScene) -> LumaRunStudioOwner? {
        if CGRect(x: 280, y: 532, width: 89, height: 44).contains(p) { return .compare }
        if CGRect(x: 307, y: 678, width: 44, height: 44).contains(p) { return .dial }
        if CGRect(x: 2, y: 609, width: 389, height: 44).contains(p) {
            let a = 24 + 345 * scene.params.low - scene.appearance.anticipation + scene.appearance.lowOffset
            let b = 24 + 345 * scene.params.high + scene.appearance.anticipation + scene.appearance.highOffset
            let da = abs(p.x - a), db = abs(p.x - b)
            return da == db ? previousHandle : (da < db ? .low : .high)
        }
        if CGRect(x: 312 + scene.params.roiX, y: 456 + scene.params.roiY, width: 44, height: 44).contains(p) { return .region }
        return CGRect(x: 29, y: 136, width: 335, height: 384).contains(p) ? .image : nil
    }
    private func receive(_ sample: LumaRunStudioTouchSample) {
        guard ready, visible, scenePhase == .active, previewTime == nil else { return }
        switch sample.phase {
        case .began:
            guard session == nil else { return }
            let captured = snapshot()
            guard let owner = owner(at: sample.location, scene: captured) else { return }
            let inherited = displayed.velocity
            newScope(captured)
            session = .init(id: sample.id, owner: owner, base: captured, anchor: sample.location,
                            last: sample.location, lastDate: sample.date, inherited: inherited)
            if owner == .low || owner == .high { previousHandle = owner }
            if owner == .compare { compareTween = .init(generation: generation, date: sample.date, from: captured.compare, target: 1, duration: 0.12) }
        case .moved: move(sample)
        case .ended: end(sample, cancelled: false)
        case .cancelled: end(sample, cancelled: true)
        }
    }
    private func move(_ sample: LumaRunStudioTouchSample) {
        guard var s = session, s.id == sample.id, s.last != sample.location else { return }
        let dt = max(1.0 / 240, sample.date.timeIntervalSince(s.lastDate))
        let vx = M.clamp((sample.location.x - s.last.x) / dt, -1200, 1200)
        let vy = M.clamp((sample.location.y - s.last.y) / dt, -1200, 1200)
        let dx = sample.location.x - s.anchor.x, dy = sample.location.y - s.anchor.y
        var p = s.base.params, v = M.zeroVelocity
        switch s.owner {
        case .image: p.q -= dy / 220; p.roiX += dx * 0.35; v.q = -vy / 220; v.roiX = vx * 0.35
        case .region: p.roiX += dx; p.roiY += dy; v.roiX = vx; v.roiY = vy
        case .low: p.low = M.clamp(p.low + dx / 345, 0.08, p.high - 0.08); v.low = vx / 345
        case .high: p.high = M.clamp(p.high + dx / 345, p.low + 0.08, 0.92); v.high = vx / 345
        case .dial:
            let a = atan2(s.anchor.y - 700, s.anchor.x - 329), b = atan2(sample.location.y - 700, sample.location.x - 329)
            let delta = atan2(sin(b - a), cos(b - a)) * 180 / .pi
            p.angle = M.clamp(p.angle + delta, -90, 90)
            let old = atan2(s.last.y - 700, s.last.x - 329)
            v.angle = atan2(sin(b - old), cos(b - old)) * 180 / .pi / dt
        case .compare: break
        }
        if s.owner == .low || s.owner == .high {
            var visual = manual ?? s.base
            let raw = (s.owner == .low ? s.base.params.low : s.base.params.high) + dx / 345
            let actual = s.owner == .low ? p.low : p.high
            let prior = s.owner == .low ? s.base.appearance.lowOffset : s.base.appearance.highOffset
            let offset = M.rubber((raw - actual) * 345) + prior * (1 - M.smooth(abs(dx) / 44))
            if s.owner == .low { visual.appearance.lowOffset = offset } else { visual.appearance.highOffset = offset }
            manual = visual
            let band = Int(floor(actual / 0.25))
            if band != s.band && sample.date.timeIntervalSince(s.hapticDate) >= 0.08 { s.band = band; s.hapticDate = sample.date; impact() }
        }
        s.last = sample.location; s.lastDate = sample.date; s.velocity = v; s.pointerY = vy; s.changed = true
        session = s
        if s.owner != .compare, LumaRunStudioCache.quantized(p) != LumaRunStudioCache.quantized(snapshot().params) {
            cache.requestManual(params: M.bounded(p), generation: generation)
        }
    }
    private func end(_ sample: LumaRunStudioTouchSample, cancelled: Bool) {
        guard session?.id == sample.id else { return }
        if !cancelled { move(sample) }
        guard let s = session else { return }
        let captured = current(at: sample.date).0
        let weight = 1 - M.smooth(max(0, sample.date.timeIntervalSince(s.lastDate)) / 0.12)
        var velocity = s.changed ? s.velocity : s.inherited
        velocity = M.mix(M.zeroVelocity, velocity, weight)
        let target = cancelled ? LumaRunStudioParams.rest : M.settledTarget(captured.params, velocityY: s.pointerY * weight)
        session = nil; newScope(captured)
        event = .init(generation: generation, date: sample.date, from: captured, target: target, velocity: velocity,
                      duration: cancelled ? 0.18 : 0.60, hold: s.owner == .compare && !cancelled)
        if s.owner == .compare { compareTween = .init(generation: generation, date: sample.date, from: captured.compare, target: 0, duration: 0.16) }
        if !cancelled { impact() }
    }
    private func adjust(_ owner: LumaRunStudioOwner, by delta: Double) {
        guard canAct else { return }
        let captured = snapshot(); var p = captured.params
        switch owner {
        case .low: p.low = M.clamp(p.low + delta, 0.08, p.high - 0.08)
        case .high: p.high = M.clamp(p.high + delta, p.low + 0.08, 0.92)
        case .dial: p.angle = M.clamp(p.angle + delta, -90, 90)
        case .region: p.roiX = M.clamp(p.roiX + delta, -30, 30)
        case .image: p.q = M.clamp(p.q + delta)
        case .compare: return
        }
        newScope(captured)
        event = .init(generation: generation, date: Date(), from: captured, target: M.bounded(p), velocity: M.zeroVelocity, duration: 0.60)
        if reduced { cache.requestManual(params: p, generation: generation) }
        impact()
    }
    private func save() {
        guard canAct else { return }
        let captured = snapshot(); newScope(captured); kept = true
        event = .init(generation: generation, date: Date(), from: captured, target: captured.params, velocity: M.zeroVelocity, duration: 0, hold: true)
        impact()
    }
    private func toggleCompare() {
        guard canAct else { return }
        let captured = snapshot(); newScope(captured)
        event = .init(generation: generation, date: Date(), from: captured, target: captured.params, velocity: M.zeroVelocity, duration: 0, hold: true)
        compareTween = .init(generation: generation, date: Date(), from: captured.compare, target: captured.compare >= 0.5 ? 0 : 1, duration: 0.16)
    }
    private func prepareRecoveryIfReady() {
        guard var e = event, e.generation == generation, e.settlementDone, !e.recoveryRequested, !reduced else { return }
        let current = manual ?? e.from
        let target = e.hold ? current.params : LumaRunStudioCache.quantized(e.target)
        guard LumaRunStudioCache.quantized(current.params) == target else { return }
        e.recoveryRequested = true; event = e
        cache.prepareRecovery(params: [0.0, 0.12, 0.24, 0.36, 0.48, 0.60].map { M.recoveryParams(from: current.params, elapsed: $0) }, generation: generation)
    }
    private func runEvent() async {
        guard let initial = event else { return }
        do {
            if reduced {
                if !initial.hold { cache.requestManual(params: initial.target, generation: initial.generation) }
                try await sleep(until: initial.date.addingTimeInterval(0.12))
                guard event?.id == initial.id else { return }
                event = nil; return
            }
            if !initial.hold {
                let ages = initial.duration < 0.60 ? [0.125, 0.18] : [0.125, 0.25, 0.375, 0.50, 0.60]
                for age in ages {
                    try await sleep(until: initial.date.addingTimeInterval(age))
                    guard event?.id == initial.id, initial.generation == generation else { return }
                    let p = initial.duration < 0.60 ? M.mix(initial.from.params, .rest, M.smooth(age / 0.18)) :
                        M.settlement(from: initial.from.params, velocity: initial.velocity, target: initial.target, elapsed: age)
                    cache.requestManual(params: p, generation: initial.generation)
                }
            }
            guard event?.id == initial.id else { return }
            event?.settlementDone = true; event?.offsetsMoving = false
            manual?.appearance.lowOffset = 0; manual?.appearance.highOffset = 0
            prepareRecoveryIfReady()
            try await sleep(until: initial.date.addingTimeInterval(2))
            guard event?.id == initial.id, generation == initial.generation else { return }
            event?.phase = 2
            try await sleep(until: initial.date.addingTimeInterval(2.60))
            guard event?.id == initial.id, generation == initial.generation else { return }
            manual = nil; event = nil; decoration = nil; compareTween = nil
            generation += 1; cache.invalidate(generation: generation); epoch = initial.date.addingTimeInterval(2.60); displayed.scene = nil
        } catch { return }
    }
    private func finishDecoration() async {
        guard let d = decoration else { return }
        do { try await sleep(until: d.date.addingTimeInterval(0.120)) } catch { return }
        guard decoration?.id == d.id, generation == d.generation else { return }
        manual?.appearance.bracketAlpha = 1; manual?.appearance.handleScale = 1; manual?.appearance.anticipation = 0; decoration = nil
    }
    private func finishCompare() async {
        guard let c = compareTween else { return }
        do { try await sleep(until: c.date.addingTimeInterval(c.duration)) } catch { return }
        guard compareTween?.id == c.id, generation == c.generation else { return }
        manual?.compare = c.target; compareTween = nil
    }
    private func sleep(until date: Date) async throws { try await Task.sleep(for: .seconds(max(0, date.timeIntervalSinceNow))) }
    private func reset() {
        generation += 1; inputGeneration += 1; cache.invalidate(generation: generation)
        session = nil; event = nil; decoration = nil; compareTween = nil; manual = nil
        handoffStarted = false; acceptedSequence = -1; displayed.scene = nil; epoch = Date()
    }
    private func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.6) }
    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
}
private enum LumaRunStudioOwner { case image, region, low, high, dial, compare }
private struct LumaRunStudioSession {
    let id: UUID
    let owner: LumaRunStudioOwner
    let base: LumaRunStudioScene
    let anchor: CGPoint
    var last: CGPoint
    var lastDate: Date
    let inherited: LumaRunStudioParams
    var velocity = LumaRunStudioMath.zeroVelocity
    var pointerY = 0.0
    var changed = false
    var band = -1
    var hapticDate = Date.distantPast
}
private struct LumaRunStudioEvent {
    let id = UUID()
    let generation: Int
    let date: Date
    let from: LumaRunStudioScene
    let target: LumaRunStudioParams
    let velocity: LumaRunStudioParams
    let duration: Double
    var hold = false
    var phase = 0
    var settlementDone = false
    var recoveryRequested = false
    var recovery: [LumaRunStudioBitmap]?
    var offsetsMoving = true
}
private struct LumaRunStudioDecoration {
    let id = UUID(); let generation: Int; let date: Date; let from: LumaRunStudioAppearance
}
private struct LumaRunStudioCompare {
    let id = UUID(); let generation: Int; let date: Date; let from: Double; let target: Double; let duration: Double
}
private final class LumaRunStudioDisplayed {
    var scene: LumaRunStudioScene?
    var velocity = LumaRunStudioMath.zeroVelocity
    var date = Date()
}
