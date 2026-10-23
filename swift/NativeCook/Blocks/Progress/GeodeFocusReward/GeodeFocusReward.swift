import SwiftUI
import UIKit

/// An earned stone opens to reveal the mineral that was inside all along.
struct GeodeFocusReward: View {
    var autoPlay = false
    var previewTime: Double? = nil
    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduced
    @GestureState private var touching = false
    @State private var epoch = Date.now
    @State private var visible = true
    @State private var capture = GeodeFocusRewardMath.rest
    @State private var pressDate: Date?
    @State private var translation = CGSize.zero
    @State private var yaw = GeodeFocusRewardYaw()
    @State private var event: GeodeFocusRewardEvent?
    @State private var kept = false
    @State private var collapsed = false
    @State private var fractureTicked = false
    @State private var quietReplay: Date?
    @State private var quietDim = false
    @State private var rendered = GeodeFocusRewardRendered()
    private typealias M = GeodeFocusRewardMath
    private var paused: Bool {
        !visible || reduced || previewTime != nil || collapsed || (!autoPlay && pressDate == nil && event == nil)
    }

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / 393
            let height = max(852, geo.size.height / scale)
            let bottom = max(0, geo.safeAreaInsets.bottom / scale - 34)
            ZStack(alignment: .topLeading) {
                backdrop(height: height, bottom: bottom)
                TimelineView(.animation(paused: paused)) { clock in
                    let frame = frame(at: clock.date)
                    reward(frame, date: clock.date, scale: scale, height: height)
                        .onChange(of: frame.hero.p >= 0.62) { _, crossed in
                            if crossed && pressDate != nil && !fractureTicked && !reduced {
                                fractureTicked = true
                                UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
                            }
                        }
                }
                if collapsed {
                    Button(action: reopen) {
                        Label("View stone", systemImage: "hexagon")
                            .font(.system(size: 15, weight: .medium))
                            .frame(width: 176, height: 48)
                            .foregroundStyle(Self.color(0xECEDE5))
                            .background(Self.color(0x232923), in: Capsule())
                    }.buttonStyle(.plain).position(x: 196.5, y: 440)
                }
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
        .background(Self.color(0x07090A)).ignoresSafeArea().preferredColorScheme(.dark)
        .onAppear {
            // Start the authored rest after ordinary artwork has been decoded.
            _ = GeodeFocusRewardResources.shared
            visible = true; epoch = .now
        }
        .onDisappear {
            visible = false; pressDate = nil; event = nil; quietReplay = nil; quietDim = false
            collapsed = false; kept = false; fractureTicked = false
        }
        .onChange(of: touching) { _, active in
            guard !active else { return }
            // Let a normal onEnded complete before handling a GestureState cancellation.
            Task { @MainActor in
                await Task.yield()
                guard visible, !touching, pressDate != nil else { return }
                finish(velocity: 0, cancelled: true)
            }
        }
        .onChange(of: reduced) { _, _ in
            pressDate = nil; event = nil; quietReplay = nil; quietDim = false
            translation = .zero; yaw = .init(); epoch = .now
            collapsed = false; kept = false; fractureTicked = false
        }
        .onChange(of: autoPlay) { _, playing in
            if playing && pressDate == nil && event == nil { epoch = .now }
        }
        .task(id: event?.stamp) {
            guard let stamp = event?.stamp else { return }
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled, event?.stamp == stamp, pressDate == nil else { return }
            withAnimation(.easeOut(duration: reduced ? 0.18 : 0.20)) { collapsed = false }
            try? await Task.sleep(for: .seconds(0.65))
            guard !Task.isCancelled, event?.stamp == stamp, pressDate == nil else { return }
            epoch = stamp.addingTimeInterval(2.65)
            event = nil; kept = false; fractureTicked = false
        }
        .task(id: quietReplay) {
            guard let stamp = quietReplay else { return }
            try? await Task.sleep(for: .seconds(0.09))
            guard !Task.isCancelled, quietReplay == stamp else { return }
            withAnimation(.easeOut(duration: 0.09)) { quietDim = false }
            try? await Task.sleep(for: .seconds(0.09))
            guard !Task.isCancelled, quietReplay == stamp else { return }
            quietReplay = nil
        }
    }

    private func reward(_ frame: GeodeFocusRewardFrame, date: Date, scale: Double, height: Double) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 27)
                .fill(LinearGradient(stops: [.init(color: Self.color(0x08090A), location: 0), .init(color: Self.color(0x232421), location: 0.5), .init(color: Self.color(0x080909), location: 1)], startPoint: .top, endPoint: .bottom))
                .overlay { RoundedRectangle(cornerRadius: 27).stroke(.white.opacity(0.07), lineWidth: 0.7) }
                .frame(width: 361, height: 608).position(x: 196.5, y: 440)
            Canvas { context, _ in
                rendered.frame = frame; rendered.date = date
                context.clip(to: Path(roundedRect: CGRect(x: 16, y: 136, width: 361, height: 608), cornerRadius: 27))
                GeodeFocusRewardArt.draw(&context, pose: frame.hero)
            }.frame(width: 393, height: height, alignment: .topLeading)
                .opacity(quietDim ? 0.78 : 1).allowsHitTesting(false).accessibilityHidden(true)
            restCopy(alpha: frame.copy.restAlpha)
            resultCopy(frame.copy)
            resultActions(frame.copy)
            headerActions
            Color.clear.frame(width: 233, height: 285)
                .contentShape(Rectangle()).position(x: 196.5, y: 452.5)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Mosslight stone")
                .accessibilityValue(reduced ? "Seven sessions completed" : "\(Int(frame.hero.p * 100)) percent revealed")
                .accessibilityHint(reduced ? "Your earned stone." : "Hold or drag upward to reveal your stone.")
                .accessibilityActions {
                    if !reduced && previewTime == nil { Button("Reveal stone", action: reveal) }
                }
                .gesture(drag(scale: scale))
        }
        .foregroundStyle(Self.color(0xECEDE5))
        .frame(width: 393, height: height, alignment: .topLeading)
        .opacity(collapsed ? 0 : 1)
        .allowsHitTesting(!collapsed).accessibilityHidden(collapsed)
        .animation(.easeOut(duration: reduced ? 0.18 : 0.24), value: collapsed)
    }

    private func restCopy(alpha: Double) -> some View {
        Group {
            Text("Stone earned").font(.system(size: 20, weight: .semibold, design: .rounded))
                .frame(width: 313, height: 28).position(x: 196.5, y: 216)
            Text("Hold to reveal your stone").font(.system(size: 10, weight: .semibold)).tracking(0.4)
                .foregroundStyle(Self.color(0x999D96))
                .frame(width: 313, height: 18).position(x: 196.5, y: 663)
        }.opacity(alpha).accessibilityHidden(alpha < 0.5).allowsHitTesting(false)
    }

    private func resultCopy(_ copy: GeodeFocusRewardCopyPose) -> some View {
        Group {
            Group {
                Text("New stone").font(.system(size: 12)).foregroundStyle(Self.color(0x999D96))
                    .frame(width: 313, height: 17).position(x: 196.5, y: 182.5)
                Text("Mosslight").font(.system(size: 22, weight: .semibold, design: .rounded))
                    .frame(width: 313, height: 28).position(x: 196.5, y: 212)
            }.offset(y: copy.resultY[0]).opacity(copy.resultAlpha[0]).accessibilityHidden(copy.resultAlpha[0] < 0.5)
            Text("Seven focused sessions.\nA little more room to think.")
                .font(.system(size: 12.5)).lineSpacing(3).multilineTextAlignment(.center)
                .foregroundStyle(Self.color(0x999D96))
                .frame(width: 313, height: 36).position(x: 196.5, y: 252 + copy.resultY[1])
                .opacity(copy.resultAlpha[1]).accessibilityHidden(copy.resultAlpha[1] < 0.5)
            HStack(spacing: 8) {
                ForEach(0..<7) { _ in Circle().fill(Self.color(0xB6CC55).opacity(0.82)).frame(width: 5, height: 5) }
            }.position(x: 196.5, y: 285 + copy.resultY[2])
                .opacity(copy.resultAlpha[2]).accessibilityHidden(true)
        }.allowsHitTesting(false)
    }

    private func resultActions(_ copy: GeodeFocusRewardCopyPose) -> some View {
        Group {
            Button(action: keep) {
                HStack(spacing: 7) {
                    if kept { Image(systemName: "checkmark").accessibilityHidden(true) }
                    Text(kept ? "Added to collection" : "Keep stone")
                }.font(.system(size: 14, weight: .medium))
                    .frame(width: 329, height: 44)
                    .foregroundStyle(Self.color(0x18250D))
                    .background(LinearGradient(colors: [Self.color(0xD9A849), Self.color(0xB6CC55), Self.color(0x74DD4C)], startPoint: .leading, endPoint: .trailing), in: Capsule())
            }.buttonStyle(.plain).accessibilityLabel(kept ? "Added to collection" : "Keep stone")
                .position(x: 196.5, y: 648)
            Button(action: replay) {
                Text("Replay").font(.system(size: 12.5)).foregroundStyle(Self.color(0x999D96))
                    .frame(width: 125, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).position(x: 196.5, y: 701)
        }
        .offset(y: copy.resultY[3]).opacity(copy.resultAlpha[3])
        .allowsHitTesting(copy.resultAlpha[3] >= 0.8).accessibilityHidden(copy.resultAlpha[3] < 0.5)
    }

    private var headerActions: some View {
        Group {
            Button(action: close) {
                Image(systemName: "xmark").font(.system(size: 13, weight: .medium))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityLabel("Close reward").position(x: 40, y: 160)
            Button(action: close) {
                Text("Skip").font(.system(size: 12.5, weight: .medium))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityLabel("Skip reward").position(x: 340, y: 160)
        }.foregroundStyle(Self.color(0x999D96))
    }

    private func backdrop(height: Double, bottom: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Self.color(0x07090A)
            Text("Stillvale").font(.system(size: 23, weight: .medium, design: .rounded))
                .frame(width: 114, height: 28, alignment: .leading).position(x: 81, y: 90)
            Text("Today").font(.system(size: 19, weight: .medium))
                .frame(width: 313, height: 28, alignment: .leading).position(x: 180.5, y: 220)
            RoundedRectangle(cornerRadius: 20).fill(Self.color(0x59615D).opacity(0.35))
                .frame(width: 345, height: 188).position(x: 196.5, y: 381)
            HStack(alignment: .bottom, spacing: 12) {
                ForEach(0..<7) { i in
                    Capsule().fill(Self.color(0xB6CC55)).frame(width: 18, height: [42.0, 66, 53, 89, 73, 104, 118][i])
                }
            }.frame(width: 250, height: 125, alignment: .bottom).position(x: 196.5, y: 385)
            ForEach(0..<3) { i in
                VStack(spacing: 5) {
                    Image(systemName: ["sun.max", "hexagon", "person.crop.circle"][i]).font(.system(size: 19))
                    Text(["Today", "Stones", "You"][i]).font(.system(size: 10, weight: .medium))
                }.frame(width: 70, height: 44).position(x: [74.0, 196.5, 319][i], y: 794 - bottom)
            }
        }.foregroundStyle(Self.color(0xECEDE5)).opacity(0.10)
            .frame(width: 393, height: height, alignment: .topLeading)
            .allowsHitTesting(false).accessibilityHidden(true)
    }

    private func drag(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating($touching) { _, state, _ in state = true }
            .onChanged { value in
                guard !reduced, previewTime == nil, !collapsed else { return }
                if pressDate == nil {
                    capture = currentVisibleFrame(); pressDate = .now
                    translation = .zero; yaw = .init(); event = nil; fractureTicked = false
                }
                translation = CGSize(width: value.translation.width / scale, height: value.translation.height / scale)
                yaw.follow(min(5, max(-5, value.velocity.width / scale * 0.006)), at: .now)
            }
            .onEnded { value in
                guard let pressDate else { return }
                let pressure = Date.now.timeIntervalSince(pressDate) > 0.12 ? 0.62 : 0
                finish(velocity: min(1.6, max(-1.6, pressure - value.velocity.height / scale / 210)), cancelled: false)
            }
    }

    private func frame(at date: Date) -> GeodeFocusRewardFrame {
        if reduced { return M.reduced }
        if let previewTime { return M.authored(max(0, previewTime)) }
        if let pressDate {
            return M.held(from: capture, elapsed: max(0, date.timeIntervalSince(pressDate)), translation: translation, yawDeflection: yaw.sample(at: date).position)
        }
        if let event {
            let age = max(0, date.timeIntervalSince(event.stamp))
            if age < 2 { return event.sample(age) }
            return M.recovering(from: event.sample(2), elapsed: age - 2)
        }
        return autoPlay ? M.authored(max(0, date.timeIntervalSince(epoch))) : M.rest
    }
    private func currentVisibleFrame() -> GeodeFocusRewardFrame {
        Date.now.timeIntervalSince(rendered.date) < 0.2 ? rendered.frame : frame(at: .now)
    }
    private func finish(velocity: Double, cancelled: Bool) {
        let current = currentVisibleFrame()
        let targetYaw = min(12, max(-12, capture.hero.manualYaw + 0.09 * Double(translation.width)))
        pressDate = nil; translation = .zero; yaw = .init()
        event = .init(stamp: .now, from: current, kind: .released(opens: !cancelled && current.hero.p >= 0.62, velocity: velocity, cancelled: cancelled, yawTarget: targetYaw))
    }
    private func keep() {
        let current = currentVisibleFrame()
        pressDate = nil; kept = true
        event = .init(stamp: .now, from: current, kind: .hold)
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }
    private func replay() {
        let current = currentVisibleFrame()
        pressDate = nil; kept = false; fractureTicked = false
        if reduced {
            quietReplay = .now
            withAnimation(.easeOut(duration: 0.09)) { quietDim = true }
            event = .init(stamp: .now, from: M.reduced, kind: .hold)
        } else { event = .init(stamp: .now, from: current, kind: .replay) }
        UISelectionFeedbackGenerator().selectionChanged()
    }
    private func reveal() {
        guard !reduced, previewTime == nil else { return }
        let current = currentVisibleFrame()
        pressDate = nil; fractureTicked = true
        event = .init(stamp: .now, from: current, kind: .released(opens: true, velocity: 0, cancelled: false, yawTarget: current.hero.manualYaw))
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }
    private func close() {
        let current = currentVisibleFrame()
        pressDate = nil; event = .init(stamp: .now, from: current, kind: .hold)
        collapsed = true
        UISelectionFeedbackGenerator().selectionChanged()
    }
    private func reopen() {
        collapsed = false
        event = .init(stamp: .now, from: currentVisibleFrame(), kind: .hold)
        UISelectionFeedbackGenerator().selectionChanged()
    }
    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255)
    }
}

private struct GeodeFocusRewardEvent {
    enum Kind { case hold, replay, released(opens: Bool, velocity: Double, cancelled: Bool, yawTarget: Double) }
    let stamp: Date
    let from: GeodeFocusRewardFrame
    let kind: Kind
    func sample(_ elapsed: Double) -> GeodeFocusRewardFrame {
        switch kind {
        case .hold: return from
        case .replay: return GeodeFocusRewardMath.replay(from: from, elapsed: elapsed)
        case let .released(opens, velocity, cancelled, yawTarget):
            return GeodeFocusRewardMath.released(from: from, opens: opens, velocity: velocity, elapsed: elapsed, cancelled: cancelled, yawTarget: yawTarget)
        }
    }
}
private final class GeodeFocusRewardRendered {
    var frame = GeodeFocusRewardMath.rest
    var date = Date.distantPast
}

/// A velocity sample drives a continuous spring for 50ms, then releases it to zero.
/// No new pan callbacks are needed while a finger stays still.
private struct GeodeFocusRewardYaw {
    private var position = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var stamp = Date.distantPast

    mutating func follow(_ target: Double, at date: Date) {
        let current = sample(at: date)
        position = current.position; velocity = current.velocity
        self.target = target; stamp = date
    }
    func sample(at date: Date) -> (position: Double, velocity: Double) {
        guard stamp != .distantPast else { return (0, 0) }
        let age = max(0, date.timeIntervalSince(stamp))
        if age <= 0.05 { return spring(position, velocity, target, age) }
        let released = spring(position, velocity, target, 0.05)
        return spring(released.position, released.velocity, 0, age - 0.05)
    }
    private func spring(_ from: Double, _ speed: Double, _ to: Double, _ time: Double) -> (position: Double, velocity: Double) {
        if time <= 0 { return (from, speed) }
        let omega = 2 * Double.pi / 0.4, damping = 0.8 * omega
        let frequency = omega * sqrt(1 - 0.8 * 0.8), displacement = from - to
        let sine = (speed + damping * displacement) / frequency
        let c = cos(frequency * time), s = sin(frequency * time), envelope = exp(-damping * time)
        let offset = displacement * c + sine * s
        return (to + envelope * offset,
                envelope * ((-displacement * frequency * s + sine * frequency * c) - damping * offset))
    }
}
