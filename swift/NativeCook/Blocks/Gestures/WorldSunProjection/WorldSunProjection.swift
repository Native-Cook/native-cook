import SwiftUI
import UIKit

struct WorldSunProjection: View {
    static let loop = WorldSunProjectionMath.loop
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var visible = false
    private let resource = WorldSunProjectionResources.shared
    @State private var epoch = Date()
    @State private var generation = UUID()
    @State private var session: WorldSunProjectionSession?
    @State private var event: WorldSunProjectionEvent?
    @State private var reducedPose = WorldSunProjectionPose.reduced
    @State private var grid = false
    @State private var timeFrozen = false
    @State private var frozenQ = 0.0
    @State private var information = false
    @State private var displayed = WorldSunProjectionDisplayed()

    private var paused: Bool {
        if !visible || scenePhase != .active || previewTime != nil || session != nil { return true }
        if let event { return event.phase == 1 }
        return reduced || !autoPlay
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                WorldSunProjectionResources.color(0x293432)
                ZStack(alignment: .topLeading) {
                    TimelineView(.animation(paused: paused)) { timeline in
                        let pose = frame(at: timeline.date)
                        ZStack(alignment: .topLeading) {
                            WorldSunProjectionArt(image: resource.environment, pose: pose, grid: grid,
                                                  markerAlpha: reduced && event?.target != nil ?
                                                    WorldSunProjectionMath.smooth(timeline.date.timeIntervalSince(event!.date) / 0.12) : 1)
                            Color.clear.frame(width: 315, height: 596).position(x: 165.5, y: 412)
                                .accessibilityElement().accessibilityLabel("Alder Reach projected view")
                                .accessibilityValue(accessiblePose(pose))
                                .accessibilityHint("Drag to look around. Use the right rail to change the planning time.")
                                .accessibilityAdjustableAction { direction in
                                    let delta = direction == .increment ? 15.0 / 350 : -15.0 / 350
                                    selectTime(WorldSunProjectionMath.clamp(snapshot().q + delta))
                                }.allowsHitTesting(false)
                            Color.clear.frame(width: 62, height: 174).position(x: 362, y: 410)
                                .accessibilityElement().accessibilityLabel("Planning time rail")
                                .accessibilityValue(WorldSunProjectionMath.time(pose.q))
                                .accessibilityHint("Drag vertically to move the sun along its projected path.")
                                .accessibilityAdjustableAction { direction in
                                    selectTime(WorldSunProjectionMath.clamp(snapshot().q + (direction == .increment ? 15.0 / 350 : -15.0 / 350)))
                                }.allowsHitTesting(false)
                            Canvas { _, _ in displayed.pose = pose }
                                .frame(width: 1, height: 1).allowsHitTesting(false).accessibilityHidden(true)
                        }
                        .frame(width: 393, height: 852, alignment: .topLeading)
                    }
                    WorldSunProjectionTouch(enabled: canTouch, generation: generation, onSample: receive)
                        .frame(width: 393, height: 621).offset(y: 114)
                    controls
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .scaleEffect(scale, anchor: .center)
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .ignoresSafeArea().preferredColorScheme(.dark)
        .onAppear { visible = true; epoch = Date() }
        .onDisappear { visible = false; clearTransient() }
        .onChange(of: scenePhase) { _, _ in clearTransient() }
        .onChange(of: reduced) { _, _ in clearTransient(); reducedPose = .reduced }
        .onChange(of: autoPlay) { _, _ in clearTransient() }
        .onChange(of: previewTime) { _, _ in clearTransient() }
        .task(id: event?.id) { await completeEvent() }
    }

    private var controls: some View {
        let white = WorldSunProjectionResources.color(0xFAF9F3)
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 6) {
                Image(systemName: "sun.max.fill").font(.system(size: 9)).foregroundStyle(WorldSunProjectionResources.color(0xD5CC47))
                Text("Golden hour").font(.system(size: 9))
                Text("04:52 → 05:33").font(.system(size: 9)).monospacedDigit()
            }.frame(width: 185, height: 28).background(chip).clipShape(Capsule()).position(x: 104.5, y: 72)
            ForEach(0..<2, id: \.self) { index in
                HStack(spacing: 4) {
                    Text(index == 0 ? "Bearing" : "Altitude").font(.system(size: 10))
                    Image(systemName: "lock").font(.system(size: 8))
                }.frame(width: index == 0 ? 85 : 87, height: 28).background(chip).clipShape(Capsule())
                    .position(x: index == 0 ? 245.5 : 337.5, y: 72)
            }
            Button { selectTime(0) } label: {
                Text("Today").font(.system(size: 11, weight: .semibold)).frame(width: 63, height: 26)
                    .background(chip).clipShape(Capsule()).frame(width: 63, height: 44)
            }.buttonStyle(.plain).position(x: 43.5, y: 530).accessibilityLabel("Reset planning time")
            ForEach(0..<3, id: \.self) { index in
                Button { selectTime([0, 0.34, 1][index]) } label: {
                    Text(["Rise", "Now", "Set"][index]).font(.system(size: 10)).frame(width: 37, height: 24)
                        .background(chip).clipShape(Capsule()).frame(width: 44, height: 44)
                }.buttonStyle(.plain).position(x: 362.5, y: [238.0, 270, 524][index])
                    .accessibilityLabel(["Select 05:09", "Select 07:08", "Select 10:59"][index])
            }
            RoundedRectangle(cornerRadius: 21).fill(WorldSunProjectionResources.color(0x303531, alpha: 0.82))
                .overlay(RoundedRectangle(cornerRadius: 21).stroke(.white.opacity(0.10), lineWidth: 0.7))
                .frame(width: 369, height: 83).position(x: 196.5, y: 776.5)
            Text("Alder Reach").font(.system(size: 12, weight: .semibold)).position(x: 196.5, y: 755)
            Text(information ? "OFFLINE SOLAR PLAN" : "EAST COAST · UTC+0")
                .font(.system(size: 8.5)).tracking(0.6).foregroundStyle(WorldSunProjectionResources.color(0xC7CDC7))
                .position(x: 196.5, y: 771)
            Button { guard canAct else { return }; interrupt(); information.toggle(); impact() } label: {
                Image(systemName: "ellipsis").font(.system(size: 12)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).position(x: 34, y: 758).accessibilityLabel("Show planning details")
            Button {
                guard canAct else { return }
                let from = snapshot()
                event = .init(date: Date(), from: from, target: .init(yaw: 0, pitch: -8.5, q: from.q))
                impact()
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right").font(.system(size: 12)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).position(x: 359, y: 758).accessibilityLabel("Reset view direction")
            ForEach(0..<3, id: \.self) { index in
                Button { dock(index) } label: {
                    Image(systemName: ["viewfinder", "cube.transparent", "sun.max"][index]).font(.system(size: 14))
                        .frame(width: 105, height: 24)
                        .background(WorldSunProjectionResources.color(0x5C635C, alpha: dockSelected(index) ? 0.75 : 0.40))
                        .clipShape(Capsule()).frame(width: 105, height: 44)
                }.buttonStyle(.plain).position(x: 76.5 + Double(index) * 120, y: 798)
                    .accessibilityLabel([grid ? "Hide horizon grid" : "Show horizon grid", "Reset view direction", timeFrozen ? "Unfreeze planning time" : "Freeze planning time"][index])
                    .accessibilityAddTraits(dockSelected(index) ? .isSelected : [])
            }
        }.frame(width: 393, height: 852, alignment: .topLeading).foregroundStyle(white)
    }
    private var chip: some View {
        Capsule().fill(WorldSunProjectionResources.color(0x293432, alpha: 0.75))
            .overlay(Capsule().stroke(.white.opacity(0.10), lineWidth: 0.6))
    }
    private var canTouch: Bool { visible && scenePhase == .active && previewTime == nil }
    private var canAct: Bool { canTouch && session == nil }
    private func accessiblePose(_ p: WorldSunProjectionPose) -> String {
        return "\(WorldSunProjectionMath.time(p.q)), heading \(Int(p.yaw.rounded())) degrees, pitch \(Int(p.pitch.rounded())) degrees"
    }
    private func dockSelected(_ index: Int) -> Bool { index == 0 ? grid : index == 1 ? true : timeFrozen }
    private func impact() { UISelectionFeedbackGenerator().selectionChanged() }
    private func snapshot() -> WorldSunProjectionPose { displayed.pose ?? frame(at: Date()) }
    private func frame(at date: Date) -> WorldSunProjectionPose {
        var pose: WorldSunProjectionPose
        if let previewTime { pose = WorldSunProjectionMath.authored(previewTime) }
        else if let session { pose = session.pose }
        else if let event {
            let support = event.target == nil ? 0.6 : 0.35
            let age = event.phase == 1 ? support : max(0, date.timeIntervalSince(event.date))
            if let target = event.target {
                pose = reduced ? target : WorldSunProjectionMath.mix(event.from, target, WorldSunProjectionMath.smooth(age / 0.35))
            } else {
                pose = WorldSunProjectionMath.released(event.from, yawVelocity: event.yawVelocity,
                                                     pitchVelocity: event.pitchVelocity, age: age, reduced: reduced)
            }
            if !reduced && age >= 2 {
                pose = age < 2.6 ? WorldSunProjectionMath.recovery(event.target ?? event.from, age: age - 2) :
                    (autoPlay ? WorldSunProjectionMath.authored(age - 2.6) : .rest)
            }
        } else { pose = reduced ? reducedPose : autoPlay ? WorldSunProjectionMath.authored(max(0, date.timeIntervalSince(epoch))) : .rest }
        if timeFrozen { pose.q = frozenQ }
        return pose
    }
    private func interrupt() {
        let pose = snapshot(); event = .init(date: Date(), from: pose)
        if reduced { reducedPose = pose; event = nil }
    }
    private func selectTime(_ q: Double) {
        guard canAct else { return }
        let from = snapshot(); var target = from; target.q = q
        if timeFrozen { frozenQ = q }
        event = .init(date: Date(), from: from, target: target)
        impact(); UIAccessibility.post(notification: .announcement, argument: WorldSunProjectionMath.time(q))
    }
    private func dock(_ index: Int) {
        guard canAct else { return }
        let from = snapshot()
        if index == 0 { interrupt(); grid.toggle() }
        else if index == 1 { event = .init(date: Date(), from: from, target: .init(yaw: 0, pitch: -8.5, q: from.q)) }
        else { interrupt(); frozenQ = from.q; timeFrozen.toggle() }
        impact()
    }

    private func receive(_ sample: WorldSunProjectionTouchSample) {
        guard canTouch else { return }
        switch sample.phase {
        case .began:
            guard session == nil else { return }
            let from = snapshot(), sun = WorldSunProjectionCamera(from).project(WorldSunProjectionMath.sun(from.q))
            let markerHit = sun.map { hypot(sample.point.x - $0.x, sample.point.y - $0.y) <= 44 } ?? false
            event = nil
            session = .init(id: sample.id, from: from, down: sample.point,
                            timeHit: sample.point.x >= 331 || markerHit, pose: from, last: sample.point,
                            lastDate: sample.date, lastHour: Int(floor((309 + from.q * 350) / 60)))
        case .moved: move(sample)
        case .ended, .cancelled:
            guard session?.id == sample.id else { return }
            if sample.phase == .ended { move(sample) }
            guard let active = session else { return }
            let fresh = sample.phase == .ended && sample.date.timeIntervalSince(active.lastDate) <= 0.12
            session = nil
            if reduced { reducedPose = active.pose; event = nil }
            else {
                event = .init(date: sample.date, from: active.pose,
                              yawVelocity: fresh ? active.yawVelocity : 0,
                              pitchVelocity: fresh ? active.pitchVelocity : 0)
            }
            UIAccessibility.post(notification: .announcement, argument: accessiblePose(active.pose))
        }
    }
    private func move(_ sample: WorldSunProjectionTouchSample) {
        guard var active = session, active.id == sample.id, active.last != sample.point else { return }
        let dx = sample.point.x - active.down.x, dy = sample.point.y - active.down.y
        if active.mode == .pending, hypot(dx, dy) >= 8 { active.mode = active.timeHit ? .time : .camera }
        let old = active.pose
        if active.mode == .time {
            active.pose.q = WorldSunProjectionMath.clamp(active.from.q - dy / 430)
            if timeFrozen { frozenQ = active.pose.q }
        } else if active.mode == .camera {
            active.pose.yaw = WorldSunProjectionMath.clamp(active.from.yaw - dx * 0.1, -22, 22)
            active.pose.pitch = WorldSunProjectionMath.clamp(active.from.pitch - dy * 0.12, -14, 58)
        }
        let dt = max(1.0 / 240, sample.date.timeIntervalSince(active.lastDate))
        active.yawVelocity = WorldSunProjectionMath.clamp((active.pose.yaw - old.yaw) / dt, -180, 180)
        active.pitchVelocity = WorldSunProjectionMath.clamp((active.pose.pitch - old.pitch) / dt, -180, 180)
        let hour = Int(floor((309 + active.pose.q * 350) / 60))
        if hour != active.lastHour, sample.date.timeIntervalSince(active.lastHaptic) >= 1.0 / 6 {
            impact(); active.lastHaptic = sample.date
        }
        active.lastHour = hour; active.last = sample.point; active.lastDate = sample.date; session = active
    }
    private func completeEvent() async {
        guard let initial = event else { return }
        do {
            let support = reduced ? 0.12 : initial.target == nil ? 0.6 : 0.35
            try await Task.sleep(for: .seconds(max(0, initial.date.addingTimeInterval(support).timeIntervalSinceNow)))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            if reduced { reducedPose = initial.target ?? initial.from; event = nil; displayed.pose = nil; return }
            event?.phase = 1
            try await Task.sleep(for: .seconds(max(0, initial.date.addingTimeInterval(2).timeIntervalSinceNow)))
            guard !Task.isCancelled, event?.id == initial.id else { return }; event?.phase = 2
            try await Task.sleep(for: .seconds(max(0, initial.date.addingTimeInterval(2.6).timeIntervalSinceNow)))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            epoch = initial.date.addingTimeInterval(2.6); event = nil; displayed.pose = nil
        } catch { return }
    }
    private func clearTransient() {
        session = nil; event = nil; generation = UUID(); epoch = Date(); displayed.pose = nil
    }
}

private enum WorldSunProjectionMode { case pending, camera, time }
private struct WorldSunProjectionSession {
    let id: UUID
    let from: WorldSunProjectionPose
    let down: CGPoint
    let timeHit: Bool
    var mode = WorldSunProjectionMode.pending
    var pose: WorldSunProjectionPose
    var last: CGPoint
    var lastDate: Date
    var yawVelocity = 0.0
    var pitchVelocity = 0.0
    var lastHour: Int
    var lastHaptic = Date.distantPast
}
private struct WorldSunProjectionEvent {
    let id = UUID()
    let date: Date
    let from: WorldSunProjectionPose
    var target: WorldSunProjectionPose? = nil
    var yawVelocity = 0.0
    var pitchVelocity = 0.0
    var phase = 0
}
private final class WorldSunProjectionDisplayed { var pose: WorldSunProjectionPose? }
