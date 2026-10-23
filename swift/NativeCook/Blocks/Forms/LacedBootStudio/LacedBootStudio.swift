import SwiftUI
import UIKit

struct LacedBootStudio: View {
    static let loop = LacedBootStudioMath.loop
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @State private var visible = false
    @State private var epoch = Date()
    @State private var session: LacedBootStudioSession?
    @State private var event: LacedBootStudioEvent?
    @State private var generation = UUID()
    @State private var presentation = LacedBootStudioPresentationDriver()
    @State private var selectedLeather = 0
    @State private var selectedSole = 0
    @State private var favorite = false
    @State private var reducedScene = LacedBootStudioMath.final()
    @State private var displayed = LacedBootStudioDisplayed()

    private var paused: Bool {
        if !visible || scenePhase != .active || previewTime != nil { return true }
        if presentation.hasActiveClock { return false }
        if let session { return !session.supportActive }
        if let event { return event.phase == 1 }
        return reduced || !autoPlay
    }
    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                LacedBootStudioResources.color(0xF2EDE8)
                ZStack(alignment: .topLeading) {
                    TimelineView(.animation(paused: paused)) { timeline in
                        let scene = frame(at: timeline.date)
                        ZStack(alignment: .topLeading) {
                            LacedBootStudioArt(scene: scene)
                            controls(scene)
                            Canvas { _, _ in displayed.scene = scene; displayed.date = timeline.date }
                                .frame(width: 1, height: 1).allowsHitTesting(false).accessibilityHidden(true)
                        }.frame(width: 393, height: 852, alignment: .topLeading)
                    }
                    LacedBootStudioTouch(enabled: visible && scenePhase == .active && !reduced && previewTime == nil,
                                         generation: generation, onSample: receive)
                        .frame(width: 347, height: 383).offset(x: 23, y: 114)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
                .scaleEffect(scale, anchor: .center)
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .ignoresSafeArea().preferredColorScheme(.light)
        .onAppear { _ = LacedBootStudioResources.shared; epoch = Date(); visible = true; displayed.scene = nil; displayed.date = nil }
        .onDisappear { visible = false; clearTransient() }
        .onChange(of: scenePhase) { _, phase in if phase != .active { clearTransient() } else { epoch = Date(); displayed.scene = nil; displayed.date = nil } }
        .onChange(of: reduced) { _, _ in clearTransient(); reducedScene = .init(); reducedScene = LacedBootStudioMath.final() }
        .onChange(of: autoPlay) { _, _ in clearTransient() }
        .onChange(of: previewTime) { _, _ in clearTransient() }
        .task(id: session?.supportGeneration) { await completeSupport() }
        .task(id: event?.id) { await completeEvent() }
        .task(id: presentation.change?.id) { await completeMaterial() }
        .task(id: presentation.confirmation?.id) { await completeSave() }
    }
    private func controls(_ scene: LacedBootStudioScene) -> some View {
        let ink = LacedBootStudioResources.color(0x302C27)
        let secondary = LacedBootStudioResources.color(0x79766F)
        return ZStack(alignment: .topLeading) {
            Button { guard canAct else { return }; clearTransient(); dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 16, weight: .regular)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 40, y: 77).accessibilityLabel("Back")
            Text("ATELIER ROWE").font(.system(size: 11, weight: .medium)).tracking(2).foregroundStyle(ink)
                .frame(width: 235, height: 18).position(x: 196.5, y: 77)
            Button(action: save) {
                Image(systemName: "bag").font(.system(size: 18)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 353, y: 77).accessibilityLabel("Save pair")

            Color.clear.frame(width: 149, height: 344).position(x: 272.5, y: 308)
                .accessibilityElement().accessibilityLabel("Alder Lace Boot lacing")
                .accessibilityValue("\(scene.rows) of 6 rows, \(Int((scene.tensions[5] * 100).rounded())) percent tension")
                .accessibilityHint("Pull the lace through each eyelet, then pull upward to tighten.")
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: resolveRows(min(6, snapshot().rows + 1))
                    case .decrement: resolveRows(max(0, snapshot().rows - 1))
                    @unknown default: break
                    }
                }
                .accessibilityAction(named: "Lace next row") { resolveRows(min(6, snapshot().rows + 1)) }
                .allowsHitTesting(false)
            Text("Alder Lace Boot").font(.system(size: 25, weight: .semibold, design: .serif)).foregroundStyle(ink)
                .frame(width: 296, height: 30, alignment: .leading).position(x: 172, y: 538)
            Button { guard canAct else { return }; interrupt(); favorite.toggle(); impact() } label: {
                Image(systemName: favorite ? "heart.fill" : "heart").font(.system(size: 18)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(ink).position(x: 351, y: 538)
                .accessibilityLabel(favorite ? "Remove from favorites" : "Add to favorites")
            Text("Hand-finished leather").font(.system(size: 12)).foregroundStyle(secondary)
                .frame(width: 345, height: 18, alignment: .leading).position(x: 196.5, y: 569)
            Text("LEATHER").font(.system(size: 9, weight: .medium)).tracking(1.5).foregroundStyle(ink)
                .frame(width: 150, height: 16, alignment: .leading).position(x: 99, y: 609)
            Text("SOLE").font(.system(size: 9, weight: .medium)).tracking(1.5).foregroundStyle(ink)
                .frame(width: 151, height: 16, alignment: .leading).position(x: 293.5, y: 609)
            ForEach(0..<3, id: \.self) { index in
                Button { select(leather: index) } label: {
                    Circle().fill(LacedBootStudioResources.color([0xA56D48, 0x493F36, 0x6F4F3A][index]))
                        .frame(width: 24, height: 24)
                        .overlay(Circle().stroke(ink.opacity(selectedLeather == index ? 1 : 0), lineWidth: 1).frame(width: 32, height: 32))
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }.buttonStyle(.plain).position(x: 38 + Double(index) * 40, y: 640)
                    .accessibilityLabel(["Tan leather", "Charcoal leather", "Chestnut leather"][index])
                    .accessibilityAddTraits(selectedLeather == index ? .isSelected : [])
            }
            ForEach(0..<2, id: \.self) { index in
                Button { select(sole: index) } label: {
                    Capsule().fill(LacedBootStudioResources.color(index == 0 ? 0x45352D : 0x98734F))
                        .frame(width: 48, height: 20)
                        .overlay(Capsule().stroke(ink.opacity(selectedSole == index ? 1 : 0), lineWidth: 0.8)
                            .frame(width: 52, height: 24))
                        .frame(width: 52, height: 44).contentShape(Rectangle())
                }.buttonStyle(.plain).position(x: 242 + Double(index) * 58, y: 640)
                    .accessibilityLabel(index == 0 ? "Dark sole" : "Natural sole")
                    .accessibilityAddTraits(selectedSole == index ? .isSelected : [])
            }
            Text("Pull the lace through each eyelet.").font(.system(size: 11)).foregroundStyle(secondary)
                .frame(width: 345, height: 16, alignment: .leading).position(x: 196.5, y: 686)
            Text("\(scene.rows) / 6 ROWS").font(.system(size: 11, weight: .regular, design: .monospaced)).foregroundStyle(ink)
                .frame(width: 345, height: 16, alignment: .leading).position(x: 196.5, y: 714)
                .accessibilityHidden(true)
            Button(action: save) {
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6).fill(LacedBootStudioResources.color(0x608674))
                    HStack {
                        ZStack(alignment: .leading) {
                            Text("Save this pair").opacity(1 - scene.savedMix)
                            Label("Pair saved", systemImage: "checkmark").opacity(scene.savedMix)
                        }.font(.system(size: 15, weight: .semibold))
                        Spacer()
                        Text("$245").font(.system(size: 13))
                    }.padding(.horizontal, 20).foregroundStyle(LacedBootStudioResources.color(0xF2EDE8))
                }.frame(width: 345, height: 54).contentShape(Rectangle()).accessibilityHidden(true)
            }.buttonStyle(.plain).position(x: 196.5, y: 781)
                .accessibilityLabel(scene.savedMix > 0.5 ? "Pair saved, 245 dollars" : "Save this pair, 245 dollars")
        }.frame(width: 393, height: 852, alignment: .topLeading)
    }
    private func frame(at date: Date) -> LacedBootStudioScene {
        var scene: LacedBootStudioScene
        if let previewTime { scene = LacedBootStudioMath.authored(previewTime) }
        else if let session { scene = held(session, date: date) }
        else if let event {
            let age = max(0, date.timeIntervalSince(event.date))
            if let rows = event.rows {
                scene = LacedBootStudioMath.resolving(from: event.from, rows: rows, age: age)
                if !reduced && age >= 2 { scene = LacedBootStudioMath.recovering(from: LacedBootStudioMath.resolving(from: event.from, rows: rows, age: 0.12), age: age - 2) }
            } else if age < 2 { scene = LacedBootStudioMath.released(from: event.from, age: age, downAge: event.downAge) }
            else if age < 2.6 { scene = LacedBootStudioMath.recovering(from: LacedBootStudioMath.released(from: event.from, age: 2, downAge: event.downAge), age: age - 2) }
            else { scene = autoPlay && !reduced ? LacedBootStudioMath.authored(age - 2.6) : LacedBootStudioMath.rest() }
        } else if reduced { scene = reducedScene }
        else { scene = autoPlay ? LacedBootStudioMath.authored(max(0, date.timeIntervalSince(epoch))) : LacedBootStudioMath.rest() }
        let values = presentation.values(at: date.timeIntervalSinceReferenceDate)
        scene.materials = values.materials; scene.soles = values.soles; scene.savedMix = values.savedMix
        return scene
    }
    private func held(_ active: LacedBootStudioSession, date: Date) -> LacedBootStudioScene {
        if let terminal = active.terminal, !active.supportActive { return terminal }
        let age = max(0, date.timeIntervalSince(active.downDate))
        let lag = active.lag * (1 - LacedBootStudioMath.smooth(max(0, date.timeIntervalSince(active.lagDate)) / 0.09))
        var scene = LacedBootStudioMath.held(from: active.from, progress: active.progress, tensions: active.tensions,
                                            age: age, lag: lag, overpull: active.overpull)
        if let passage = active.passageDate, date.timeIntervalSince(passage) < 0.035 { scene.tipAlpha = 0 }
        return scene
    }
    private func snapshot() -> LacedBootStudioScene { displayed.scene ?? frame(at: Date()) }
    private var canAct: Bool { visible && scenePhase == .active && previewTime == nil && session == nil }
    private func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.6) }
    private func interrupt() {
        let captured = snapshot()
        if reduced { reducedScene = captured; event = nil }
        else { event = .init(date: Date(), from: captured, downAge: 0) }
    }
    private func save() {
        guard canAct else { return }; let mix = snapshot().savedMix; interrupt()
        presentation.save(from: mix, at: Date().timeIntervalSinceReferenceDate); impact()
        UIAccessibility.post(notification: .announcement, argument: "Pair saved")
    }
    private func select(leather: Int? = nil, sole: Int? = nil) {
        guard canAct else { return }; let captured = snapshot(); interrupt()
        if let leather { selectedLeather = leather }; if let sole { selectedSole = sole }
        let targetMaterials = (0..<3).map { $0 == selectedLeather ? 1.0 : 0 }
        let targetSoles = (0..<2).map { $0 == selectedSole ? 1.0 : 0 }
        presentation.select(from: captured, materials: targetMaterials, soles: targetSoles, at: Date().timeIntervalSinceReferenceDate)
        impact()
    }
    private func resolveRows(_ rows: Int) {
        guard canAct else { return }
        let captured = snapshot(); guard rows != captured.rows else { return }
        event = .init(date: Date(), from: captured, downAge: 0, rows: rows)
        impact(); UIAccessibility.post(notification: .announcement, argument: "\(rows) of 6 rows laced")
    }
    private func receive(_ sample: LacedBootStudioTouchSample) {
        guard visible, scenePhase == .active, !reduced, previewTime == nil else { return }
        switch sample.phase {
        case .began:
            guard session == nil else { return }
            let captured = snapshot(), local = LacedBootStudioGeometry.local(sample.point)
            presentation.capture(captured, displayedAt: (displayed.date ?? sample.date).timeIntervalSinceReferenceDate)
            event = nil
            session = .init(id: sample.id, from: captured, downDate: sample.date, anchor: local,
                            last: local, lastDate: sample.date, lagDate: sample.date, progress: captured.progress,
                            tensions: captured.tensions, stage: LacedBootStudioGeometry.activeSpan(captured.progress),
                            pullAnchor: captured.progress >= 6 ? local : nil,
                            pullTensions: captured.tensions, lag: captured.velocityLag, overpull: captured.overpull)
        case .moved: move(sample)
        case .ended, .cancelled:
            guard session?.id == sample.id else { return }
            if sample.phase == .ended { move(sample) }
            guard let active = session else { return }
            let current = held(active, date: sample.date)
            presentation.release(materials: (0..<3).map { $0 == selectedLeather ? 1 : 0 },
                                 soles: (0..<2).map { $0 == selectedSole ? 1 : 0 }, at: sample.date.timeIntervalSinceReferenceDate)
            session = nil
            event = .init(date: sample.date, from: current, downAge: max(0, sample.date.timeIntervalSince(active.downDate)))
        }
    }
    private func move(_ sample: LacedBootStudioTouchSample) {
        guard var active = session, active.id == sample.id else { return }
        let local = LacedBootStudioGeometry.local(sample.point)
        guard local != active.last else { return }
        let dt = max(1.0 / 240, sample.date.timeIntervalSince(active.lastDate))
        let speed = hypot(local.x - active.last.x, local.y - active.last.y) / dt
        active.lag = min(3, speed * 0.003); active.lagDate = sample.date
        let previousRows = Int(floor(active.progress + 1e-8))
        if let anchor = active.pullAnchor {
            let distance = anchor.y - local.y
            let delta = 0.85 * distance / 36
            active.tensions = active.pullTensions.map { LacedBootStudioMath.clamp($0 + delta) }
            let raw = active.pullTensions[5] + delta
            let outside = (raw - LacedBootStudioMath.clamp(raw)) * 36 / 0.85
            active.overpull = CGPoint(x: 0, y: -LacedBootStudioMath.clamp(outside / (1 + abs(outside) / 48) * 0.45, -6, 6))
        } else {
            let pointer = CGPoint(x: active.from.tip.x + local.x - active.anchor.x,
                                  y: active.from.tip.y + local.y - active.anchor.y)
            let table = LacedBootStudioGeometry.arcs[active.stage]
            let projection = table.nearest(pointer)
            var candidate = LacedBootStudioGeometry.spanProgress(active.stage, projection.fraction)
            if active.stage > 0 {
                let previous = LacedBootStudioGeometry.arcs[active.stage - 1].nearest(pointer)
                if previous.distance < projection.distance { candidate = LacedBootStudioGeometry.spanProgress(active.stage - 1, previous.fraction) }
            }
            // A tip reaches the next hole only through its incoming half-plane, in order.
            let endpoint = table.points.last!, before = table.points[30]
            let nx = endpoint.x - before.x, ny = endpoint.y - before.y
            let signed = (pointer.x - endpoint.x) * nx + (pointer.y - endpoint.y) * ny
            let distance = hypot(pointer.x - endpoint.x, pointer.y - endpoint.y)
            if distance <= 9 && signed >= 0 && active.lastSigned <= 0 {
                candidate = LacedBootStudioGeometry.spanProgress(active.stage, 1)
                active.passageDate = sample.date
                if active.stage < 10 { active.stage += 1; active.lastSigned = -.infinity }
                else { active.pullAnchor = local; active.pullTensions = active.tensions }
            } else {
                let end = LacedBootStudioGeometry.spanProgress(active.stage, 1)
                candidate = min(candidate, end - 1e-5)
                active.lastSigned = signed
            }
            active.progress = LacedBootStudioMath.clamp(candidate, 0, 6)
        }
        active.last = local; active.lastDate = sample.date; active.supportGeneration = UUID()
        active.supportActive = true; active.terminal = nil
        if Int(floor(active.progress + 1e-8)) > previousRows {
            impact(); UIAccessibility.post(notification: .announcement, argument: "\(Int(floor(active.progress + 1e-8))) of 6 rows laced")
        }
        session = active
    }
    private func sleep(until date: Date) async throws { try await Task.sleep(for: .seconds(max(0, date.timeIntervalSinceNow))) }
    private func completeSupport() async {
        guard let initial = session, initial.supportActive else { return }
        let end = max(initial.downDate.addingTimeInterval(0.43), initial.lagDate.addingTimeInterval(0.09))
        do { try await sleep(until: end) } catch { return }
        guard !Task.isCancelled, var active = session, active.id == initial.id,
              active.supportGeneration == initial.supportGeneration else { return }
        active.terminal = held(active, date: end); active.supportActive = false; session = active
    }
    private func completeEvent() async {
        guard let initial = event else { return }
        do {
            try await sleep(until: initial.date.addingTimeInterval(initial.rows == nil ? 0.43 : 0.12))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            if reduced {
                reducedScene = frame(at: initial.date.addingTimeInterval(0.43)); event = nil; return
            }
            event?.phase = 1
            try await sleep(until: initial.date.addingTimeInterval(2))
            guard !Task.isCancelled, event?.id == initial.id else { return }; event?.phase = 2
            try await sleep(until: initial.date.addingTimeInterval(2.6))
            guard !Task.isCancelled, event?.id == initial.id else { return }
            epoch = initial.date.addingTimeInterval(2.6); event = nil; displayed.scene = nil; displayed.date = nil
        } catch { return }
    }
    private func completeMaterial() async {
        guard let initial = presentation.change else { return }
        do { try await sleep(until: Date(timeIntervalSinceReferenceDate: initial.time + 0.18)) } catch { return }
        guard !Task.isCancelled else { return }; presentation.finishMaterial(initial.id)
    }
    private func completeSave() async {
        guard let initial = presentation.confirmation else { return }
        do { try await sleep(until: Date(timeIntervalSinceReferenceDate: initial.endTime)) } catch { return }
        guard !Task.isCancelled else { return }; presentation.finishConfirmation(initial.id)
    }
    private func clearTransient() {
        session = nil; event = nil; generation = UUID()
        presentation.reset(materials: (0..<3).map { $0 == selectedLeather ? 1 : 0 },
                           soles: (0..<2).map { $0 == selectedSole ? 1 : 0 })
        epoch = Date(); displayed.scene = nil; displayed.date = nil
    }
}

private struct LacedBootStudioSession {
    let id: UUID
    let from: LacedBootStudioScene
    let downDate: Date
    let anchor: CGPoint
    var last: CGPoint
    var lastDate: Date
    var lagDate: Date
    var progress: Double
    var tensions: [Double]
    var stage: Int
    var pullAnchor: CGPoint?
    var pullTensions: [Double]
    var lag: Double
    var overpull: CGPoint
    var lastSigned = -Double.infinity
    var passageDate: Date?
    var supportGeneration = UUID()
    var supportActive = true
    var terminal: LacedBootStudioScene?
}
private struct LacedBootStudioEvent {
    let id = UUID()
    let date: Date
    let from: LacedBootStudioScene
    let downAge: Double
    var rows: Int? = nil
    var phase = 0
}
private final class LacedBootStudioDisplayed {
    var scene: LacedBootStudioScene?
    var date: Date?
}
