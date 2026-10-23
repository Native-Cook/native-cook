import SwiftUI
import UIKit

struct LenticularEditionPass: View {
    static let loop = LenticularEditionPassMath.loop
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @State private var visible = false
    @State private var epoch = Date()
    @State private var session: LenticularEditionPassSession?
    @State private var event: LenticularEditionPassEvent?
    @State private var touchGeneration = UUID()
    @State private var reducedBase = LenticularEditionPassFrame(pose: .zero, explicitViewB: 0)
    @State private var reducedTargetView = 0.0
    @State private var displayed = LenticularEditionPassDisplayed()

    private var paused: Bool {
        if !visible || scenePhase != .active || previewTime != nil { return true }
        if let session { return !session.supportActive }
        if let event { return event.phase == 1 }
        return reduced || !autoPlay
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = geometry.size.width / 393
            let height = max(852, geometry.size.height / scale)
            let bottomLift = max(0, geometry.safeAreaInsets.bottom / scale - 34)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: paused)) { timeline in
                    let current = frame(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        LenticularEditionPassArt(frame: current, logicalHeight: height)
                        controls(current, bottomLift: bottomLift)
                        Canvas { _, _ in
                            displayed.frame = current
                            displayed.date = timeline.date
                        }.frame(width: 1, height: 1).allowsHitTesting(false).accessibilityHidden(true)
                    }
                    .frame(width: 393, height: height, alignment: .topLeading)
                }

                // Stable raw-touch ownership is outside the changing Timeline subtree.
                LenticularEditionPassTouch(enabled: visible && scenePhase == .active && !reduced && previewTime == nil,
                    generation: touchGeneration,
                    accepts: { LenticularEditionPassGeometry.inverseHit(screen: $0, pose: snapshot().pose) != nil },
                    onSample: receiveTouch)
                    .frame(width: 350, height: 265).offset(x: 21.5, y: 242.5)
                    .accessibilityHidden(true)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            .clipped()
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            _ = LenticularEditionPassResources.shared
            epoch = Date(); visible = true; displayed.frame = nil
        }
        .onDisappear { visible = false; clearTransient() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { clearTransient() }
            else { epoch = Date(); displayed.frame = nil }
        }
        .onChange(of: reduced) { _, _ in
            clearTransient(); reducedTargetView = 0; reducedBase = .init(pose: .zero, explicitViewB: 0)
        }
        .onChange(of: autoPlay) { _, _ in clearTransient() }
        .onChange(of: previewTime) { _, _ in clearTransient() }
        .task(id: session?.supportGeneration) { await completeSupport() }
        .task(id: event?.id) { await completeEvent() }
    }

    private func controls(_ current: LenticularEditionPassFrame, bottomLift: Double) -> some View {
        let ink = LenticularEditionPassResources.color(0x192C35)
        let secondary = LenticularEditionPassResources.color(0x78868B)
        let viewB = LenticularEditionPassGeometry.meanViewB(current) > 0.5
        return ZStack(alignment: .topLeading) {
            Button(action: back) {
                Image(systemName: "arrow.left").font(.system(size: 20, weight: .regular))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 36, y: 92)
                .accessibilityLabel("Back")
            Text("EDITION / 07").font(.system(size: 10.5, weight: .medium)).tracking(1.6)
                .foregroundStyle(ink).frame(width: 220, height: 20, alignment: .leading)
                .position(x: 172, y: 91)
            VStack(alignment: .leading, spacing: 0) {
                Text("Between").fixedSize(horizontal: false, vertical: true)
                    .frame(height: 35, alignment: .topLeading).accessibilityHidden(true)
                Text("two views").fixedSize(horizontal: false, vertical: true)
                    .frame(height: 35, alignment: .topLeading).accessibilityHidden(true)
            }.font(.system(size: 32, weight: .regular)).tracking(-0.8).foregroundStyle(ink)
                .frame(width: 345, height: 76, alignment: .topLeading).position(x: 196.5, y: 159)
                .accessibilityElement(children: .ignore).accessibilityLabel("Between two views")
            Text("02 VIEWS").font(.system(size: 10, weight: .medium)).tracking(1.0)
                .foregroundStyle(ink).frame(width: 94, height: 25)
                .overlay(Capsule().stroke(LenticularEditionPassResources.color(0xCDD4D0), lineWidth: 0.8))
                .position(x: 322, y: 222.5)

            Color.clear.contentShape(Rectangle()).frame(width: 350, height: 265)
                .position(x: 196.5, y: 375)
                .accessibilityElement().accessibilityLabel("Quiet Orbit, edition 7 of 128")
                .accessibilityValue(viewB ? "View two of two" : "View one of two")
                .accessibilityHint("Drag to turn the card, or adjust to choose a view.")
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: chooseView(1)
                    case .decrement: chooseView(0)
                    @unknown default: break
                    }
                }
                .onChange(of: fullView(current)) { _, value in updateHaptic(value) }
            Text("Quiet Orbit").font(.system(size: 23, weight: .medium)).foregroundStyle(ink)
                .frame(width: 345, height: 31, alignment: .leading).position(x: 196.5, y: 564.5)
            Text("No. 07 of 128").font(.system(size: 12.5)).foregroundStyle(secondary)
                .frame(width: 345, height: 19, alignment: .leading).position(x: 196.5, y: 595.5)
            Text("An angle changes the story.").font(.system(size: 12.5)).foregroundStyle(secondary)
                .frame(width: 345, height: 19, alignment: .leading).position(x: 196.5, y: 617.5)
            ForEach(0..<2, id: \.self) { view in
                Button { chooseView(view) } label: {
                    LenticularEditionPassResources.shared.artImages[view]
                        .resizable().interpolation(.high).frame(width: 58, height: 37)
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(
                            (view == 1) == viewB ? ink : LenticularEditionPassResources.color(0xCDD4D0),
                            lineWidth: (view == 1) == viewB ? 1.2 : 0.6))
                        .frame(width: 58, height: 44).contentShape(Rectangle())
                }.buttonStyle(.plain).position(x: 53 + Double(view) * 70, y: 669.5)
                    .accessibilityLabel(view == 0 ? "View one" : "View two")
                    .accessibilityAddTraits((view == 1) == viewB ? .isSelected : [])
            }
            Text("Turn it in your hands.").font(.system(size: 12)).foregroundStyle(secondary)
                .frame(width: 345, height: 19, alignment: .leading).position(x: 196.5, y: 720.5)
            Button(action: collect) {
                ZStack {
                    Capsule().fill(LenticularEditionPassResources.color(0x20343E))
                    Text("Add to collection").opacity(1 - current.collectionMix)
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .semibold))
                        Text("Collected")
                    }.opacity(current.collectionMix)
                }
                .font(.system(size: 15.5, weight: .semibold))
                .foregroundStyle(LenticularEditionPassResources.color(0xF2F2E9))
                .frame(width: 345, height: 54).contentShape(Capsule())
                .accessibilityHidden(true)
            }.buttonStyle(.plain).position(x: 196.5, y: 782 - bottomLift)
                .accessibilityLabel(current.collectionMix >= 0.5 ? "Collected" : "Add to collection")
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func frame(at date: Date) -> LenticularEditionPassFrame {
        if reduced {
            if let event { return eventFrame(event, age: age(of: event, date: date)) }
            return reducedBase
        }
        if let previewTime { return LenticularEditionPassMath.authored(previewTime) }
        if let session { return heldFrame(session, date: date) }
        if let event {
            let elapsed = age(of: event, date: date)
            if elapsed < 2 { return eventFrame(event, age: elapsed) }
            if elapsed < 2.65 {
                return LenticularEditionPassMath.recovering(from: eventFrame(event, age: 2), elapsed: elapsed - 2)
            }
            return autoPlay ? LenticularEditionPassMath.authored(elapsed - 2.65) : LenticularEditionPassMath.rest()
        }
        return autoPlay ? LenticularEditionPassMath.authored(max(0, date.timeIntervalSince(epoch))) : LenticularEditionPassMath.rest()
    }
    private func age(of event: LenticularEditionPassEvent, date: Date) -> Double {
        if event.phase == 1 { return event.settleDuration }
        return max(0, date.timeIntervalSince(event.date))
    }
    private func eventFrame(_ event: LenticularEditionPassEvent, age: Double) -> LenticularEditionPassFrame {
        switch event.kind {
        case let .release(view): return LenticularEditionPassMath.released(from: event.from, view: view, elapsed: age)
        case let .cancel(base): return LenticularEditionPassMath.cancelled(from: event.from, base: base, elapsed: age)
        case .collect: return LenticularEditionPassMath.collecting(from: event.from, elapsed: age)
        case let .reduced(view, collection):
            var result = LenticularEditionPassMath.reduced(from: event.from, view: view, elapsed: age)
            if collection { result.collectionMix = event.from.collectionMix + (1 - event.from.collectionMix) * LenticularEditionPassMath.ease(age / 0.18) }
            if age >= 2 { result.collectionMix *= 1 - LenticularEditionPassMath.ease((age - 2) / 0.18) }
            return result
        }
    }
    private func heldFrame(_ active: LenticularEditionPassSession, date: Date) -> LenticularEditionPassFrame {
        if let terminal = active.terminal, !active.supportActive { return terminal }
        if !active.changed { var first = active.base; first.angularVelocity = .zero; return first }
        return LenticularEditionPassMath.dragged(from: active.base, translation: active.translation,
            yawSupport: active.yawSupport, pitchSupport: active.pitchSupport,
            supportAge: max(0, date.timeIntervalSince(active.supportDate)))
    }
    private func snapshot() -> LenticularEditionPassFrame {
        displayed.frame ?? frame(at: Date())
    }
    private var canAct: Bool { visible && scenePhase == .active && previewTime == nil && session == nil }
    private func chooseView(_ view: Int) {
        guard canAct else { return }
        let captured = snapshot()
        if reduced { reducedTargetView = Double(view) }
        event = .init(date: Date(), from: captured,
                      kind: reduced ? .reduced(view: Double(view), collection: false) : .release(view))
        impact()
    }
    private func collect() {
        guard canAct else { return }
        let captured = snapshot()
        event = .init(date: Date(), from: captured,
                      kind: reduced ? .reduced(view: reducedTargetView, collection: true) : .collect)
        impact()
    }
    private func back() {
        guard canAct else { return }
        clearTransient(); dismiss()
    }
    private func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.6) }
    private func fullView(_ value: LenticularEditionPassFrame) -> Int {
        value.pose.yaw <= -7.5 ? 0 : (value.pose.yaw >= 7.5 ? 1 : -1)
    }
    private func updateHaptic(_ value: Int) {
        guard var active = session else { return }
        if value == -1 { active.hapticArmed = true }
        else if value != active.hapticView && active.hapticArmed {
            active.hapticView = value; active.hapticArmed = false; impact()
        }
        if active.hapticArmed != session?.hapticArmed || active.hapticView != session?.hapticView { session = active }
    }

    private func receiveTouch(_ sample: LenticularEditionPassTouchSample) {
        guard !reduced, previewTime == nil, visible, scenePhase == .active else { return }
        switch sample.phase {
        case .began:
            guard session == nil else { return }
            let captured = snapshot()
            guard LenticularEditionPassGeometry.inverseHit(screen: sample.location, pose: captured.pose) != nil else { return }
            event = nil
            session = .init(id: sample.id, base: captured, anchor: sample.location, lastPoint: sample.location,
                            lastDate: sample.date, supportDate: sample.date,
                            hapticView: LenticularEditionPassGeometry.meanViewB(captured) > 0.5 ? 1 : 0)
        case .moved: move(sample)
        case .ended: finish(sample, cancelled: false)
        case .cancelled: finish(sample, cancelled: true)
        }
    }
    private func move(_ sample: LenticularEditionPassTouchSample) {
        guard var active = session, active.id == sample.id, active.lastPoint != sample.location else { return }
        let dt = max(1.0 / 240, sample.date.timeIntervalSince(active.lastDate))
        let pointer = CGSize(width: (sample.location.x - active.lastPoint.x) / dt,
                             height: (sample.location.y - active.lastPoint.y) / dt)
        let supportAge = max(0, sample.date.timeIntervalSince(active.supportDate))
        active.yawSupport = active.yawSupport.retarget(velocity: pointer.width, elapsed: supportAge)
        active.pitchSupport = active.pitchSupport.retarget(velocity: -pointer.height, elapsed: supportAge)
        active.supportDate = sample.date; active.supportGeneration = UUID(); active.supportActive = true
        active.lastPoint = sample.location; active.lastDate = sample.date; active.pointer = pointer
        active.translation = CGSize(width: sample.location.x - active.anchor.x, height: sample.location.y - active.anchor.y)
        active.changed = true; active.terminal = nil
        session = active
    }
    private func finish(_ sample: LenticularEditionPassTouchSample, cancelled: Bool) {
        guard session?.id == sample.id else { return }
        if !cancelled { move(sample) }
        guard let active = session else { return }
        let now = sample.date
        var captured = heldFrame(active, date: now)
        captured.angularVelocity = LenticularEditionPassMath.releaseVelocity(base: active.base,
            translation: active.translation, pointer: active.pointer,
            candidateAge: max(0, now.timeIntervalSince(active.lastDate)),
            inherited: active.changed ? nil : active.base.angularVelocity,
            yawSupport: active.yawSupport, pitchSupport: active.pitchSupport,
            supportAge: max(0, now.timeIntervalSince(active.supportDate)))
        let aged = 1 - LenticularEditionPassMath.smooth(max(0, now.timeIntervalSince(active.lastDate)) / 0.12)
        let pointer = CGSize(width: LenticularEditionPassMath.clamp(active.pointer.width, -1200, 1200) * aged,
                             height: LenticularEditionPassMath.clamp(active.pointer.height, -1200, 1200) * aged)
        let view = LenticularEditionPassMath.targetView(from: captured, pointer: pointer)
        session = nil
        event = .init(date: now, from: captured, kind: cancelled ? .cancel(active.base) : .release(view))
    }
    private func sleep(until deadline: Date) async throws {
        try await Task.sleep(for: .seconds(max(0, deadline.timeIntervalSinceNow)))
    }
    private func completeSupport() async {
        guard let initial = session, initial.supportActive else { return }
        do { try await sleep(until: initial.supportDate.addingTimeInterval(0.60)) } catch { return }
        guard !Task.isCancelled, var active = session, active.id == initial.id,
              active.supportGeneration == initial.supportGeneration, visible, scenePhase == .active else { return }
        active.terminal = LenticularEditionPassMath.dragged(from: active.base, translation: active.translation,
            yawSupport: active.yawSupport, pitchSupport: active.pitchSupport, supportAge: 0.60)
        active.supportActive = false; session = active
    }
    private func completeEvent() async {
        guard let initial = event else { return }
        do {
            try await sleep(until: initial.date.addingTimeInterval(initial.settleDuration))
            guard !Task.isCancelled, event?.id == initial.id, visible, scenePhase == .active else { return }
            if initial.isReduced && !initial.hasCollection {
                reducedBase = eventFrame(initial, age: 0.18); event = nil; return
            }
            event?.phase = 1
            try await sleep(until: initial.date.addingTimeInterval(2))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            event?.phase = 2
            let end = initial.isReduced ? 2.18 : 2.65
            try await sleep(until: initial.date.addingTimeInterval(end))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            if initial.isReduced { reducedBase = eventFrame(initial, age: end) }
            epoch = initial.date.addingTimeInterval(end); event = nil; displayed.frame = nil
        } catch { return }
    }
    private func clearTransient() {
        session = nil; event = nil; touchGeneration = UUID()
        epoch = Date(); displayed.frame = nil
        reducedBase.collectionMix = 0
        if reduced { reducedBase = .init(pose: .zero, explicitViewB: reducedTargetView) }
    }
}

private struct LenticularEditionPassSession {
    let id: UUID
    let base: LenticularEditionPassFrame
    let anchor: CGPoint
    var lastPoint: CGPoint
    var lastDate: Date
    var supportDate: Date
    var pointer = CGSize.zero
    var translation = CGSize.zero
    var yawSupport = LenticularEditionPassVelocitySupport()
    var pitchSupport = LenticularEditionPassVelocitySupport()
    var supportGeneration = UUID()
    var supportActive = false
    var changed = false
    var terminal: LenticularEditionPassFrame?
    var hapticView: Int
    var hapticArmed = true
}
private struct LenticularEditionPassEvent {
    enum Kind {
        case release(Int)
        case cancel(LenticularEditionPassFrame)
        case collect
        case reduced(view: Double, collection: Bool)
    }
    let id = UUID()
    let date: Date
    let from: LenticularEditionPassFrame
    let kind: Kind
    var phase = 0
    var settleDuration: Double {
        switch kind { case .release: return 0.75; case .cancel: return 0.24; default: return 0.18 }
    }
    var isReduced: Bool { if case .reduced = kind { return true }; return false }
    var hasCollection: Bool {
        if case let .reduced(_, collection) = kind { return collection || from.collectionMix > 0 }
        return false
    }
}
private final class LenticularEditionPassDisplayed {
    var frame: LenticularEditionPassFrame?
    var date = Date()
}
