import SwiftUI
import UIKit

/// Four heater modes share one continuously deformed, seven-layer body of light.
struct HeatShapePicker: View {
    var autoPlay = false
    static let loop = 8.0
    private typealias M = HeatShapePickerModel
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date.timeIntervalSinceReferenceDate
    @State private var interaction: HeatShapePickerTransition?
    @State private var heldFrame: HeatShapePickerFrame?
    @State private var dragging = false
    @State private var live = false
    @State private var active = true
    @State private var dragX = HeatShapePickerSpring()
    @State private var dragY = HeatShapePickerSpring()
    @State private var grip = HeatShapePickerSpring()
    @State private var direction = 0.0
    @State private var resume: Task<Void, Never>?

    var body: some View {
        GeometryReader { proxy in
            let safe = proxy.safeAreaInsets
            let layout = HeatShapePickerLayout(width: proxy.size.width + safe.leading + safe.trailing,
                height: proxy.size.height + safe.top + safe.bottom, top: safe.top, bottom: safe.bottom)
            TimelineView(.animation(paused: !active || (!autoPlay && !live))) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = frame(at: now)
                ZStack(alignment: .topLeading) {
                    Color.black
                    Canvas { context, _ in
                        HeatShapePickerArt.draw(context, frame: frame, layout: layout, reduced: reduced,
                            dx: dragX.state(now).0, dy: dragY.state(now).0,
                            grip: grip.state(now).0, direction: direction)
                    }
                    .allowsHitTesting(false)
                    texts(frame, layout)
                    ring(frame, layout)
                    ForEach(0..<4) { i in chip(i, frame, layout) }
                    Color.clear
                        .frame(width: 2.8 * 110 * layout.sx, height: 2.8 * 110 * layout.sx)
                        .contentShape(Circle())
                        .position(layout.center)
                        .gesture(bodyTouch())
                        .accessibilityHidden(true)
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .offset(x: -safe.leading, y: -safe.top)
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { active = true; reset() }
        .onDisappear { active = false; resume?.cancel() }
        .onChange(of: reduced) { _, _ in reset() }
        .onChange(of: autoPlay) { _, _ in reset() }
        .onChange(of: scenePhase) { _, phase in
            active = phase == .active
            if active { reset() } else { resume?.cancel() }
        }
    }

    private func frame(at now: Double) -> HeatShapePickerFrame {
        if let heldFrame { return heldFrame }
        if let interaction { return interaction.frame(now, reduced: reduced) }
        return .automatic(autoPlay ? max(0, now - epoch) : 0, reduced: reduced)
    }

    @ViewBuilder private func texts(_ f: HeatShapePickerFrame, _ l: HeatShapePickerLayout) -> some View {
        Text("LIVING ROOM · 21°")
            .font(.system(size: 12, weight: .semibold)).tracking(1.8)
            .foregroundStyle(.white.opacity(0.55))
            .frame(width: l.width, alignment: .top).offset(y: l.y(78))
        Text("Heater mode").font(.system(size: 30, weight: .semibold)).foregroundStyle(.white)
            .frame(width: l.width, alignment: .top).offset(y: l.y(102))
        ForEach(0..<4) { i in
            Text(M.names[i]).font(.system(size: 36, weight: .bold)).foregroundStyle(.white)
                .frame(width: l.width, alignment: .top).offset(y: l.y(548) + f.labelY[i])
                .opacity(f.labels[i]).accessibilityHidden(f.labels[i] < 0.5)
            Text(M.captions[i]).font(.system(size: 15)).foregroundStyle(.white.opacity(0.55))
                .frame(width: l.width, alignment: .top).offset(y: l.y(598) + f.labelY[i])
                .opacity(f.labels[i]).accessibilityHidden(f.labels[i] < 0.5)
        }
    }

    private func ring(_ f: HeatShapePickerFrame, _ l: HeatShapePickerLayout) -> some View {
        let gradient = LinearGradient(colors: [M.color(0xFF2A12), M.color(0x5546FF)],
                                      startPoint: .topLeading, endPoint: .bottomTrailing)
        return ZStack {
            RoundedRectangle(cornerRadius: 24).stroke(gradient, lineWidth: 6).blur(radius: 8).opacity(0.6)
            RoundedRectangle(cornerRadius: 24).stroke(gradient, lineWidth: 1.6)
        }
        .frame(width: 76, height: 76)
        .offset(x: l.x(28 + 87 * f.ring), y: l.y(676))
        .allowsHitTesting(false)
    }

    private func chip(_ i: Int, _ f: HeatShapePickerFrame, _ l: HeatShapePickerLayout) -> some View {
        Button { select(i, at: Date.timeIntervalSinceReferenceDate) } label: {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 24).fill(.white.opacity(0.05))
                RoundedRectangle(cornerRadius: 24).stroke(.white.opacity(0.1), lineWidth: 1)
                Canvas { c, _ in HeatShapePickerArt.glyph(c, index: i, selected: f.chips[i]) }
                Text(M.names[i]).font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white.opacity(0.55 + 0.45 * f.chips[i]))
                    .frame(width: 76).offset(y: 56)
            }
            .frame(width: 76, height: 76)
            .padding(6).contentShape(RoundedRectangle(cornerRadius: 30))
        }
        .buttonStyle(.plain)
        .offset(x: l.x(28 + 87 * Double(i)) - 6, y: l.y(676) - 6)
        .accessibilityLabel(M.names[i]).accessibilityHint(M.captions[i])
        .accessibilityAddTraits(f.selected == i ? [.isSelected] : [])
    }

    private func select(_ index: Int, at now: Double, haptic: Bool = true) {
        resume?.cancel()
        interaction = HeatShapePickerTransition(source: frame(at: now), target: index, at: now)
        live = true
        if haptic { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7) }
        if !dragging { scheduleResume() }
    }

    private func bodyTouch() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.timeIntervalSinceReferenceDate
                if !dragging {
                    resume?.cancel(); dragging = true; live = true
                    let f = frame(at: now)
                    heldFrame = f
                    grip.retarget(1, now)
                    UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.65)
                }
                let x = Double(value.translation.width), y = Double(value.translation.height)
                let length = hypot(x, y), follow = length > 0 ? min(26, length * 0.22) / length : 0
                dragX.retarget(x * follow, now); dragY.retarget(y * follow, now)
                if length > 1 { direction = atan2(x, -y) }
            }
            .onEnded { value in
                guard dragging else { return }
                let now = Date.timeIntervalSinceReferenceDate
                let source = frame(at: now)
                heldFrame = nil
                interaction = HeatShapePickerTransition(source: source, target: source.selected, at: now, settling: true)
                dragging = false
                grip.retarget(0, now, releasing: true)
                dragX.retarget(0, now, releasing: true); dragY.retarget(0, now, releasing: true)
                if abs(value.translation.width) > 44 {
                    let i = frame(at: now).selected, step = value.translation.width < 0 ? 1 : 3
                    select((i + step) % 4, at: now, haptic: false)
                    UISelectionFeedbackGenerator().selectionChanged()
                }
                scheduleResume()
            }
    }

    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            do {
                try await Task.sleep(for: .seconds(2))
                guard !dragging else { return }
                let now = Date.timeIntervalSinceReferenceDate, selected = interaction?.target ?? 0
                // The manual frame already rests at this phase, including the screen-fixed wobble.
                if autoPlay { epoch = (interaction?.at ?? now - 2) + 2 - Double(selected) * 2; interaction = nil }
                live = !autoPlay && !reduced
            } catch { /* A newer touch owns the resume deadline. */ }
        }
    }

    private func reset() {
        resume?.cancel(); epoch = Date.timeIntervalSinceReferenceDate
        interaction = nil; heldFrame = nil; dragging = false; live = false
        dragX = HeatShapePickerSpring(); dragY = HeatShapePickerSpring(); grip = HeatShapePickerSpring()
    }
}
