import SwiftUI
import UIKit

/// A corner-hinged sample expands in both directions while each plate stays rigid.
struct AuxeticFitLab: View {
    var autoPlay = false
    var previewTime: Double? = nil
    static let loop = 8.0
    private typealias M = AuxeticFitLabMath
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var leftActive = false
    @GestureState private var rightActive = false
    @GestureState private var pinchActive = false
    @State private var epoch = Date.now
    @State private var visible = true
    @State private var generation = 0
    @State private var session: Session?
    @State private var blockedOwners: Set<Owner> = []
    @State private var event: Event?
    @State private var saved = false
    @State private var reducedAngle = 42.0
    @State private var quietSave: Date?
    @State private var previousDetent = AuxeticFitLabDetent.closed
    @State private var hapticZone = AuxeticFitLabDetent.closed
    @State private var lastTick = Date.distantPast
    @State private var rendered = AuxeticFitLabRendered()

    private enum Owner: Hashable { case left, right, pinch }
    private struct Session {
        let owner: Owner
        let generation: Int
        let stamp: Date
        let capture: AuxeticFitLabFrame
        let anchor: Double
        var coordinate: Double
        var input: AuxeticFitLabInput
        var lastMovement: Date?
        var movementInput: AuxeticFitLabInput?
    }
    private struct Event {
        let stamp: Date
        let held: AuxeticFitLabFrame
        let release: AuxeticFitLabRelease?
    }
    private var paused: Bool {
        !visible || reduced || previewTime != nil || session != nil || (!autoPlay && event == nil)
    }

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / 393
            let height = max(852, geo.size.height / scale)
            let bottom = max(0, geo.safeAreaInsets.bottom / scale - 34)
            TimelineView(.animation(paused: paused)) { clock in
                let pose = frame(at: clock.date)
                content(pose, date: clock.date, scale: scale, height: height, bottom: bottom)
                    .frame(width: 393, height: height, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            }
        }
        .background(Self.color(0xF6F8F4)).ignoresSafeArea().preferredColorScheme(.light)
        .onAppear {
            _ = AuxeticFitLabResources.shared
            visible = true; epoch = .now
        }
        .onDisappear { clearInteraction(); visible = false }
        .onChange(of: reduced) { _, _ in clearInteraction(); reducedAngle = 42; epoch = .now }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { visible = true; epoch = .now }
            else { clearInteraction(); visible = false }
        }
        .onChange(of: autoPlay) { _, playing in
            if playing && session == nil && event == nil { epoch = .now }
        }
        .onChange(of: leftActive) { _, active in if !active { cancelled(.left) } }
        .onChange(of: rightActive) { _, active in if !active { cancelled(.right) } }
        .onChange(of: pinchActive) { _, active in if !active { cancelled(.pinch) } }
        .task(id: event?.stamp) {
            guard let stamp = event?.stamp else { return }
            try? await Task.sleep(for: .seconds(2.60))
            guard !Task.isCancelled, event?.stamp == stamp, session == nil else { return }
            event = nil; saved = false; previousDetent = .closed
            epoch = stamp.addingTimeInterval(2.60)
        }
        .task(id: quietSave) {
            guard let stamp = quietSave else { return }
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, quietSave == stamp else { return }
            saved = false; quietSave = nil
        }
    }

    private func content(_ pose: AuxeticFitLabFrame, date: Date, scale: Double, height: Double, bottom: Double) -> some View {
        let metrics = M.metrics(at: pose.angle)
        let handles = M.handles(for: pose)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                rendered.frame = pose; rendered.date = date
                AuxeticFitLabArt.draw(&context, frame: pose, logicalHeight: height)
            }.frame(width: 393, height: height, alignment: .topLeading)
                .allowsHitTesting(false).accessibilityHidden(true)
            copy
            Rectangle().fill(Self.color(0x798885).opacity(0.28))
                .frame(width: 345, height: 0.6).position(x: 196.5, y: 566).accessibilityHidden(true)
            Rectangle().fill(Self.color(0x798885).opacity(0.22))
                .frame(width: 0.6, height: 67).position(x: 196.5, y: 619.5).accessibilityHidden(true)
            metric("Extension", value: metrics.extensionPercent, x: 24)
            metric("Open area", value: metrics.openAreaPercent, x: 213)
            Text("Pull a handle. Both directions grow.")
                .font(.system(size: 12.5)).foregroundStyle(Self.color(0x798885))
                .frame(width: 345, height: 20, alignment: .leading).position(x: 196.5, y: 697)
            Button(action: save) {
                HStack(spacing: 7) {
                    if saved { Image(systemName: "checkmark").accessibilityHidden(true) }
                    Text(saved ? "Fit saved" : "Save fit")
                }.font(.system(size: 15.5, weight: .semibold)).foregroundStyle(Self.color(0xF6F8F4))
                    .frame(width: 345, height: 54).background(Self.color(0x213C3B), in: Capsule())
            }.buttonStyle(.plain)
                .accessibilityLabel(saved ? "Fit saved" : "Save fit").position(x: 196.5, y: 782 - bottom)
            Color.clear.frame(width: handles.pinchHit.width, height: handles.pinchHit.height)
                .contentShape(Rectangle()).position(x: handles.pinchHit.midX, y: handles.pinchHit.midY)
                .accessibilityElement(children: .ignore).accessibilityLabel("Auxetic lattice")
                .accessibilityValue("Extension \(Self.number(metrics.extensionPercent)) percent, open area \(Self.number(metrics.openAreaPercent)) percent")
                .accessibilityHint(reduced ? "Adjust the sample angle." : "Pull a handle or pinch to expand the pattern.")
                .accessibilityAdjustableAction(adjust).gesture(pinch)
            if !reduced {
                handleHit(handles.leftHit, owner: .left, scale: scale)
                handleHit(handles.rightHit, owner: .right, scale: scale)
            }
        }.foregroundStyle(Self.color(0x213C3B))
    }

    private var copy: some View {
        Group {
            Text("HINGE / MATERIAL LAB").font(.system(size: 10.5, weight: .semibold)).tracking(1.8)
                .frame(width: 345, height: 17, alignment: .leading).position(x: 196.5, y: 84.5)
            Text("Room to move").font(.system(size: 30, weight: .semibold)).tracking(-0.6)
                .frame(width: 345, height: 38, alignment: .leading).position(x: 196.5, y: 124)
            Text("A rigid pattern with a softer fit.").font(.system(size: 13)).foregroundStyle(Self.color(0x798885))
                .frame(width: 345, height: 20, alignment: .leading).position(x: 196.5, y: 161)
            Text("SAMPLE 01").font(.system(size: 10.5, weight: .medium, design: .monospaced))
                .foregroundStyle(Self.color(0x798885))
                .frame(width: 145, height: 18, alignment: .leading).position(x: 96.5, y: 241)
            Text("Rotating squares").font(.system(size: 12))
                .frame(width: 170, height: 18, alignment: .trailing).position(x: 284, y: 241)
        }
    }
    private func metric(_ title: String, value: Double, x: Double) -> some View {
        Group {
            Text(title).font(.system(size: 11.5)).foregroundStyle(Self.color(0x798885))
                .frame(width: 156, height: 18, alignment: .leading).position(x: x + 78, y: 595)
            Text(Self.number(value) + "%").font(.system(size: 30, weight: .semibold)).monospacedDigit()
                .frame(width: 156, height: 40, alignment: .leading).position(x: x + 78, y: 631)
        }.accessibilityHidden(true)
    }
    private func handleHit(_ rect: CGRect, owner: Owner, scale: Double) -> some View {
        Color.clear.frame(width: rect.width, height: rect.height).contentShape(Rectangle())
            .position(x: rect.midX, y: rect.midY)
            .accessibilityLabel(owner == .left ? "Left expansion handle" : "Right expansion handle")
            .accessibilityHint("Drag horizontally to expand both dimensions.").gesture(drag(owner, scale: scale))
    }

    private func frame(at date: Date) -> AuxeticFitLabFrame {
        if reduced { return M.staticFrame(angle: reducedAngle) }
        if let previewTime { return M.authored(max(0, previewTime)) }
        if let session { return frozen(session.input.frame) }
        if let event {
            let age = max(0, date.timeIntervalSince(event.stamp))
            let held = event.release.map { M.released($0, elapsed: min(age, 2)) } ?? event.held
            return age < 2 ? held : M.recovering(from: held, elapsed: age - 2)
        }
        return autoPlay ? M.authored(max(0, date.timeIntervalSince(epoch))) : M.rest
    }
    private func displayed(at date: Date) -> AuxeticFitLabFrame {
        if !reduced, previewTime == nil, date.timeIntervalSince(rendered.date) < 0.12 { return rendered.frame }
        return frame(at: date)
    }
    private func frozen(_ frame: AuxeticFitLabFrame) -> AuxeticFitLabFrame {
        var f = frame
        f.angularVelocity = 0; f.left.offsetVelocityX = 0; f.right.offsetVelocityX = 0
        f.left.scaleVelocity = 0; f.right.scaleVelocity = 0; f.arrowGainVelocity = 0
        return f
    }
    private func begin(_ owner: Owner, anchor: Double) {
        guard visible, !reduced, previewTime == nil, session == nil else { return }
        let now = Date.now, current = frame(at: now)
        var capture = displayed(at: now)
        // Keep the displayed values and sample incoming derivatives at this event's time.
        capture.angularVelocity = current.angularVelocity
        capture.left.offsetVelocityX = current.left.offsetVelocityX
        capture.right.offsetVelocityX = current.right.offsetVelocityX
        capture.left.scaleVelocity = current.left.scaleVelocity
        capture.right.scaleVelocity = current.right.scaleVelocity
        capture.arrowGainVelocity = current.arrowGainVelocity
        generation += 1
        session = Session(owner: owner, generation: generation, stamp: now, capture: capture,
                          anchor: anchor, coordinate: anchor, input: M.begin(from: capture))
        event = nil; quietSave = nil
        hapticZone = M.nearestDetent(angle: capture.angle, velocity: 0, previous: previousDetent)
    }
    private func changed(_ owner: Owner, coordinate: Double, velocity: Double) {
        guard var s = session, s.owner == owner else { return }
        guard coordinate != s.coordinate else { return }
        let input: AuxeticFitLabInput
        if owner == .pinch {
            input = M.pinch(from: s.capture, scale: coordinate / s.anchor, scaleVelocity: velocity / s.anchor)
        } else {
            input = M.drag(from: s.capture, side: owner == .left ? .left : .right,
                           translationX: coordinate - s.anchor, velocityX: velocity)
        }
        if coordinate != s.coordinate { s.lastMovement = .now; s.movementInput = input }
        s.coordinate = coordinate; s.input = input; session = s
        tick(angle: input.frame.angle)
    }
    private func finish(_ owner: Owner, cancelled: Bool) {
        guard let s = session, s.owner == owner else { return }
        let now = Date.now
        let velocity: AuxeticFitLabReleaseVelocity
        if let moved = s.lastMovement, let input = s.movementInput {
            velocity = .user(width: input.requestedWidthVelocity, angular: input.actualAngularVelocity,
                             sampleAge: max(0, now.timeIntervalSince(moved)))
        } else { velocity = .inherited(frame: s.capture, heldElapsed: max(0, now.timeIntervalSince(s.stamp))) }
        let release = M.makeRelease(from: s.input, velocity: velocity, previous: previousDetent, cancelled: cancelled)
        if let target = release.target { previousDetent = target }
        event = Event(stamp: now, held: frozen(s.input.frame), release: release); session = nil
    }
    private func cancelled(_ owner: Owner) {
        blockedOwners.remove(owner)
        guard let s = session, s.owner == owner else { return }
        let token = s.generation
        Task { @MainActor in
            await Task.yield()
            guard session?.owner == owner, session?.generation == token else { return }
            finish(owner, cancelled: true)
        }
    }
    private func drag(_ owner: Owner, scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating(owner == .left ? $leftActive : $rightActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !blockedOwners.contains(owner) else { return }
                if let active = session?.owner, active != owner { blockedOwners.insert(owner); return }
                let x = value.translation.width / scale
                if session == nil { begin(owner, anchor: x); return }
                changed(owner, coordinate: x, velocity: value.velocity.width / scale)
            }
            .onEnded { value in
                blockedOwners.remove(owner)
                guard session?.owner == owner else { return }
                changed(owner, coordinate: value.translation.width / scale, velocity: value.velocity.width / scale)
                finish(owner, cancelled: false)
            }
    }
    private var pinch: some Gesture {
        MagnifyGesture().updating($pinchActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !blockedOwners.contains(.pinch) else { return }
                if let active = session?.owner, active != .pinch { blockedOwners.insert(.pinch); return }
                if session == nil { begin(.pinch, anchor: value.magnification); return }
                changed(.pinch, coordinate: value.magnification, velocity: value.velocity)
            }
            .onEnded { value in
                blockedOwners.remove(.pinch)
                guard session?.owner == .pinch else { return }
                changed(.pinch, coordinate: value.magnification, velocity: value.velocity)
                finish(.pinch, cancelled: false)
            }
    }
    private func tick(angle: Double) {
        let zone = M.nearestDetent(angle: angle, velocity: 0, previous: hapticZone)
        guard zone != hapticZone else { return }
        hapticZone = zone
        let now = Date.now
        guard now.timeIntervalSince(lastTick) >= 0.20 else { return }
        lastTick = now; UISelectionFeedbackGenerator().selectionChanged()
    }
    private func save() {
        guard session == nil else { return }
        saved = true; UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
        let now = Date.now
        if reduced { quietSave = now }
        else { event = Event(stamp: now, held: frozen(displayed(at: now)), release: nil) }
    }
    private func adjust(_ direction: AccessibilityAdjustmentDirection) {
        guard session == nil, previewTime == nil else { return }
        let now = Date.now, current = displayed(at: now)
        let delta: Double
        switch direction { case .increment: delta = 5; case .decrement: delta = -5; @unknown default: return }
        let angle = min(42, max(4, current.angle + delta))
        guard angle != current.angle else { return }
        if reduced { reducedAngle = angle }
        else { event = Event(stamp: now, held: M.staticFrame(angle: angle), release: nil) }
        tick(angle: angle)
    }
    private func clearInteraction() {
        generation += 1; session = nil; blockedOwners.removeAll(); event = nil; quietSave = nil; saved = false
        previousDetent = .closed; hapticZone = .closed
    }
    private static func number(_ value: Double) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }
    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
}

private final class AuxeticFitLabRendered {
    var frame = AuxeticFitLabMath.rest
    var date = Date.distantPast
}
