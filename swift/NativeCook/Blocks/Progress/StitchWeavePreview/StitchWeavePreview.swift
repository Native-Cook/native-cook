import SwiftUI
import UIKit

/// Feed yarn turns into interlocking courses; the mark belongs to the yarn, not to an overlay.
struct StitchWeavePreview: View {
    var autoPlay = false
    var previewTime: Double? = nil
    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduced
    @GestureState private var touching = false
    @State private var epoch = Date.now
    @State private var visible = true
    @State private var dragging = false
    @State private var captured = StitchWeavePreviewPose.rest
    @State private var finger = StitchWeavePreviewPose.rest
    @State private var action: StitchWeavePreviewAction?
    @State private var pattern = 0
    @State private var yarn = 0
    @State private var materialBase = StitchWeavePreviewMath.initialMaterial
    @State private var materialStarted: Date?
    @State private var materialRows = 3.0
    @State private var saved = false
    @State private var saveStarted: Date?
    @State private var lastDetent = 0
    @State private var lastHaptic = Date.distantPast
    @State private var displayed = StitchWeavePreviewDisplayed()

    private typealias M = StitchWeavePreviewMath
    private var paused: Bool {
        !visible || (materialStarted == nil && (reduced || previewTime != nil || (!autoPlay && !dragging && action == nil)))
    }

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / 393
            let height = max(852, geo.size.height / scale)
            let bottom = max(0, geo.safeAreaInsets.bottom / scale - 34)
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [.init(color: M.color(0x929C9C), location: 0), .init(color: M.color(0x4E595A), location: 0.4), .init(color: M.color(0x20292B), location: 1)], startPoint: .top, endPoint: .bottom)
                RadialGradient(colors: [M.color(0xD9E4DD).opacity(0.10), .clear], center: UnitPoint(x: 0.5, y: 0.43), startRadius: 20, endRadius: 290)
                StitchWeavePreviewResources.shared.grain.resizable(resizingMode: .tile).opacity(0.04).allowsHitTesting(false).accessibilityHidden(true)

                Text("THREAD / STUDIO").font(.system(size: 11, weight: .semibold)).tracking(2)
                    .frame(width: 345, height: 17, alignment: .leading).position(x: 196.5, y: 84.5)
                Text("Make something soft").font(.system(size: 29, weight: .semibold)).tracking(-0.6)
                    .frame(width: 345, height: 36, alignment: .leading).position(x: 196.5, y: 124)
                Text("A small pattern, one row at a time.").font(.system(size: 13)).foregroundStyle(M.color(0xC4CBC7))
                    .frame(width: 345, height: 19, alignment: .leading).position(x: 196.5, y: 159.5)

                TimelineView(.animation(paused: paused)) { clock in
                    let pose = pose(at: clock.date)
                    let materialAge = materialStarted.map { max(0, clock.date.timeIntervalSince($0)) } ?? -1
                    let reset = resetAmount(at: clock.date)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in
                            displayed.pose = pose; displayed.date = clock.date
                            Self.drawHero(context, pose: pose, material: materialBase, pattern: pattern, yarn: yarn,
                                          materialAge: materialAge, materialRows: materialRows, reset: reset, reduced: reduced)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        StitchWeavePreviewReadout(p: pose.p)
                        Color.clear.frame(width: 349, height: 72)
                            .contentShape(Rectangle())
                            .position(x: 196.5, y: min(559, max(220, StitchWeavePreviewResources.shared.project(u: 0.5, v: 1 - (3 + 45 * pose.p) / 48, pose: pose).y)))
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(reduced ? "Pattern preview" : "Knit progress")
                            .accessibilityValue("\(Int(floor((3 + 45 * pose.p) / 48 * 100))) percent")
                            .accessibilityHint(reduced ? "A completed knitted pattern." : "Drag upward to knit, downward to unknit.")
                            .accessibilityAdjustableAction { direction in
                                guard !reduced, previewTime == nil else { return }
                                let current = currentVisiblePose()
                                freezeMaterial(at: .now)
                                let change = direction == .increment ? 5.0 / 45 : -5.0 / 45
                                beginAction(from: current, target: M.clamp(current.p + change), velocity: 0, kick: 0, save: false)
                                UISelectionFeedbackGenerator().selectionChanged()
                            }
                            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .global)
                                .updating($touching) { _, state, _ in state = true }
                                .onChanged { value in
                                    guard !reduced, previewTime == nil else { return }
                                    if !dragging {
                                        captured = currentVisiblePose(); finger = captured
                                        freezeMaterial(at: .now)
                                        dragging = true; action = nil; saved = false; saveStarted = nil
                                        lastDetent = Int((3 + 45 * captured.p) / 6)
                                    }
                                    let raw = captured.p - value.translation.height / (290 * scale)
                                    let p = M.clamp(raw)
                                    var next = captured.moved(to: p)
                                    let over = (raw - p) * 290
                                    next.hem -= max(-35, min(35, over * 0.45 / (1 + abs(over) / 75)))
                                    next.rotation.y += max(-6, min(6, value.translation.width / (18 * scale)))
                                    next.lagRotation.y += max(-6, min(6, value.translation.width / (18 * scale)))
                                    finger = next
                                    thresholdHaptic(p: p)
                                }.onEnded { value in
                                    guard dragging else { return }
                                    let vy = max(-900, min(900, value.velocity.height / scale))
                                    finishDrag(velocity: -vy / 290, cancelled: false)
                                })
                    }.frame(width: 393, height: height, alignment: .topLeading)
                }

                HStack(spacing: 8) {
                    ForEach(0..<3) { index in
                        Button { choose(pattern: index, yarn: yarn) } label: {
                            Text(["Sprig", "Ripple", "Plain"][index]).font(.system(size: 12.5, weight: .medium))
                                .frame(width: 104, height: 35)
                                .foregroundStyle(pattern == index ? M.color(0x263235) : M.color(0xF2F1E7))
                                .background(pattern == index ? M.color(0xEAE8DC) : M.color(0x263235).opacity(0.7), in: Capsule())
                                .frame(width: 104, height: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityAddTraits(pattern == index ? .isSelected : [])
                    }
                }.frame(width: 328, height: 44).position(x: 188, y: 684.5)
                Text("YARN").font(.system(size: 10, weight: .semibold)).tracking(1.5)
                    .frame(width: 100, height: 22, alignment: .leading).position(x: 74, y: 733 - bottom)
                ForEach(0..<3) { index in
                    Button { choose(pattern: pattern, yarn: index) } label: {
                        Circle().fill(M.color(M.yarns[index])).frame(width: 22, height: 22)
                            .overlay { Circle().stroke(M.color(0xF2F1E7).opacity(yarn == index ? 0.85 : 0.12), lineWidth: yarn == index ? 1.5 : 0.8).padding(-4) }
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }.buttonStyle(.plain).position(x: 258 + Double(index) * 44, y: 733 - bottom)
                        .accessibilityLabel(["Cream", "Moss", "Slate"][index] + " yarn")
                        .accessibilityAddTraits(yarn == index ? .isSelected : [])
                }
                Button(action: save) {
                    HStack(spacing: 8) {
                        if saved { Image(systemName: "checkmark").accessibilityHidden(true) }
                        Text(saved ? "Pattern saved" : "Save pattern")
                    }.font(.system(size: 15.5, weight: .semibold))
                        .frame(width: 345, height: 54).foregroundStyle(M.color(0x263234))
                        .background(M.color(0xEAE8DC), in: Capsule())
                }.buttonStyle(.plain).position(x: 196.5, y: 782 - bottom)
            }
            .foregroundStyle(M.color(0xF2F1E7))
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
        .background(M.color(0x20292B)).ignoresSafeArea().preferredColorScheme(.dark)
        .onAppear { visible = true; epoch = .now }
        .onDisappear { visible = false; dragging = false; action = nil; materialStarted = nil }
        .onChange(of: touching) { _, active in
            if !active && dragging { finishDrag(velocity: 0, cancelled: true) }
        }
        .onChange(of: reduced) { _, _ in
            dragging = false; action = nil; materialStarted = nil; saveStarted = nil
            materialBase = M.solidMaterial(pattern: pattern, yarn: yarn); epoch = .now
        }
        .onChange(of: autoPlay) { _, playing in
            if playing && !dragging && action == nil { epoch = .now }
        }
        .task(id: action?.stamp) {
            guard let stamp = action?.stamp else { return }
            try? await Task.sleep(for: .seconds(2.65))
            guard !Task.isCancelled, !dragging, action?.stamp == stamp else { return }
            epoch = stamp.addingTimeInterval(2.65)
            action = nil; pattern = 0; yarn = 0; materialBase = M.initialMaterial
            materialStarted = nil; saved = false; saveStarted = nil
        }
        .task(id: materialStarted) {
            guard let stamp = materialStarted else { return }
            try? await Task.sleep(for: .seconds(reduced ? 0.18 : 0.38))
            guard !Task.isCancelled, materialStarted == stamp else { return }
            materialBase = M.solidMaterial(pattern: pattern, yarn: yarn); materialStarted = nil
        }
        .task(id: saveStarted) {
            guard let stamp = saveStarted else { return }
            try? await Task.sleep(for: .seconds(0.48))
            guard !Task.isCancelled, saveStarted == stamp, !dragging else { return }
            saved = true
        }
    }

    private func pose(at date: Date) -> StitchWeavePreviewPose {
        if reduced { return .finished }
        if let previewTime { return M.authored(max(0, previewTime)) }
        if dragging { return finger }
        if let action { return action.pose(at: date) }
        return autoPlay ? M.authored(max(0, date.timeIntervalSince(epoch))) : .rest
    }
    private func currentVisiblePose() -> StitchWeavePreviewPose {
        Date.now.timeIntervalSince(displayed.date) < 0.2 ? displayed.pose : pose(at: .now)
    }
    private func resetAmount(at date: Date) -> Double {
        guard !reduced, previewTime == nil, !dragging, let action else { return 0 }
        return M.io((date.timeIntervalSince(action.stamp) - 2) / 0.65)
    }
    private func freezeMaterial(at date: Date) {
        let age = materialStarted.map { max(0, date.timeIntervalSince($0)) } ?? -1
        let reset = resetAmount(at: date)
        for row in 0..<48 {
            let wave = M.materialMix(row: row, age: age, rows: materialRows, reduced: reduced)
            for channel in 0..<6 {
                let target = channel == pattern || channel == yarn + 3 ? 1.0 : 0.0
                let value = Double(materialBase[row * 6 + channel]) * (1 - wave) + target * wave
                materialBase[row * 6 + channel] = Float(value * (1 - reset) + (channel == 0 || channel == 3 ? reset : 0))
            }
        }
        materialStarted = nil
    }
    private func beginAction(from pose: StitchWeavePreviewPose, target: Double, velocity: Double, kick: Double, save: Bool) {
        if !save { saved = false; saveStarted = nil }
        action = .init(stamp: .now, from: pose, target: target, velocity: velocity, kick: kick, saving: save)
        dragging = false
    }
    private func finishDrag(velocity: Double, cancelled: Bool) {
        let projected = M.clamp(finger.p + velocity * 0.12)
        let snapped = M.clamp((round(3 + 45 * projected) - 3) / 45)
        beginAction(from: finger, target: snapped, velocity: cancelled ? 0 : velocity,
                    kick: cancelled ? 0 : max(-14, min(14, velocity * 4.5)), save: false)
    }
    private func thresholdHaptic(p: Double) {
        let detent = Int((3 + 45 * p) / 6)
        guard detent != lastDetent, Date.now.timeIntervalSince(lastHaptic) >= 0.125 else { return }
        lastDetent = detent; lastHaptic = .now
        if p >= 1 { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5) }
        else { UISelectionFeedbackGenerator().selectionChanged() }
    }
    private func choose(pattern nextPattern: Int, yarn nextYarn: Int) {
        let current = currentVisiblePose()
        freezeMaterial(at: .now)
        pattern = nextPattern; yarn = nextYarn; materialRows = 3 + 45 * current.p
        materialStarted = .now; saved = false; saveStarted = nil
        if !reduced { beginAction(from: current, target: current.p, velocity: 0, kick: 0, save: false) }
        UISelectionFeedbackGenerator().selectionChanged()
    }
    private func save() {
        freezeMaterial(at: .now)
        if reduced { saved = true }
        else {
            let current = currentVisiblePose()
            beginAction(from: current, target: 1, velocity: 0, kick: 0, save: true)
            saveStarted = action?.stamp
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }

    private static func drawHero(_ context: GraphicsContext, pose: StitchWeavePreviewPose, material: [Float], pattern: Int, yarn: Int, materialAge: Double, materialRows: Double, reset: Double, reduced: Bool) {
        let resources = StitchWeavePreviewResources.shared
        let hero = CGRect(x: 22, y: 184, width: 349, height: 411)
        let full = CGRect(x: 0, y: 0, width: 393, height: 852)
        var ctx = context; ctx.clip(to: Path(hero))
        let hem = resources.project(u: 0.5, v: 1, pose: pose)
        var shadow = ctx; shadow.opacity = 0.16
        shadow.draw(resources.shadow, in: CGRect(x: hem.x - 174, y: hem.y - 13, width: 348, height: 78))
        let canonical = M.canonicalTime(pose.p)
        for i in 0..<52 {
            let formed = M.columnRows(p: pose.p, canonical: canonical, column: i)
            var last = min(47, max(2, Int(ceil(formed)) - 1))
            var formation = last < 3 ? 1 : M.clamp((formed - Double(last + 1) + 0.45) / 0.45)
            if formation <= 0 && last > 2 { last -= 1; formation = 1 }
            let crownY = 572 - Double(last + 1) * 6.8 + 0.5 - 1.8 * (1 - formation)
            let end = resources.project(u: (Double(i) + 0.5) / 52, v: (crownY - 245.6) / 326.4, pose: pose)
            let start = CGPoint(x: 38.4 + Double(i) * 6.2, y: 184)
            var path = Path(); path.move(to: start)
            path.addQuadCurve(to: end, control: CGPoint(x: start.x + (end.x - start.x) * 0.35, y: 184 + (end.y - 184) * 0.56))
            let alpha = 0.18 + 0.24 * M.hash(i + 91)
            ctx.stroke(path, with: .color(M.color(0xE6E9E0).opacity(alpha)), style: StrokeStyle(lineWidth: 0.42 + 0.28 * M.hash(i + 7), lineCap: .round))
        }
        let shader = ShaderLibrary.StitchWeavePreviewMaterial(
            .image(resources.atlas),
            .float4(pose.p, canonical, pose.crownElapsed, pose.topologySpeed),
            .float4(pose.lift, pose.hem, pose.anticipation, pose.lightPosition),
            .float4(pose.light, materialAge, reset, reduced ? 1 : 0),
            .float4(Double(pattern), Double(yarn), materialRows, reduced ? 0.18 : 0.38),
            .floatArray(resources.uniforms(pose: pose)), .floatArray(material),
            .color(M.color(0xDEDCD3)), .color(M.color(0x889A8C)), .color(M.color(0x7D8C92)),
            .color(M.color(0x7F8581)), .color(M.color(0x343D3D)), .color(M.color(0xF4F2E9)))
        ctx.fill(Path(full), with: .shader(shader, bounds: full))
    }
}

private struct StitchWeavePreviewReadout: View {
    let p: Double
    var body: some View {
        let rows = 3 + 45 * p
        ZStack(alignment: .topLeading) {
            Text(String(format: "Row %02d of 48", min(48, Int(floor(rows + 0.000001)))))
                .frame(width: 220, height: 20, alignment: .leading).position(x: 134, y: 620)
            Text("\(Int(floor(rows / 48 * 100 + 0.000001)))%")
                .frame(width: 100, height: 20, alignment: .trailing).position(x: 319, y: 620)
            Capsule().fill(StitchWeavePreviewMath.color(0xC4CBC7).opacity(0.20)).frame(width: 345, height: 3).position(x: 196.5, y: 641.5)
            Capsule().fill(StitchWeavePreviewMath.color(0xEAE8DC)).frame(width: 345 * rows / 48, height: 3)
                .position(x: 24 + 345 * rows / 96, y: 641.5)
        }.font(.system(size: 12, weight: .medium)).monospacedDigit().allowsHitTesting(false).accessibilityHidden(true)
    }
}

private final class StitchWeavePreviewDisplayed {
    var pose = StitchWeavePreviewPose.rest
    var date = Date.distantPast
}
