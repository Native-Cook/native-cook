import SwiftUI
import UIKit

/// Four independent message previews: concealed ink, gentle inflation, a loud pulse and a sharp slam.
struct SendAmplifyPreview: View {
    static let loop: Double = 8
    var autoPlay = false
    /// Freezes the demonstration at an exact loop time for visual review.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var lastTouch = Date.distantPast
    @State private var capturedPose = SendAmplifyPreviewPose.rest
    @State private var eventStarted = Date.distantPast
    @State private var releaseFocus: Double = 3
    @State private var dragStartFocus: Double = 3
    @State private var manualRow = 3
    @State private var isDragging = false
    @State private var manualActivated = false
    @State private var manualLive = false
    @State private var hasSelection = false
    @State private var lastHaptic = Date.distantPast
    @State private var hapticIndex = 3

    private let names = ["Amplify", "Lift", "Whisper", "Drift"]

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 393, geometry.size.height / 852)
            ZStack {
                SendAmplifyPreviewPalette.background.ignoresSafeArea()
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
            guard !Task.isCancelled, !isDragging else { return }
            manualLive = false
            manualActivated = false
        }
    }

    private var canonicalScreen: some View {
        ZStack(alignment: .topLeading) {
            staticBackground
            frostedConversation
            topControls
            composer
            TimelineView(.animation(minimumInterval: nil, paused: timelinePaused)) { timeline in
                let now = timeline.date
                let pose = currentPose(at: now)
                ZStack(alignment: .topLeading) {
                    effectRail(pose)
                    animatedPreview(pose)
                }
                .frame(width: 393, height: 852, alignment: .topLeading)
            }
            interactionSurface
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .coordinateSpace(name: "SendAmplifyPreviewStage")
        .clipped()
    }

    private var timelinePaused: Bool {
        reduceMotion || previewTime != nil || (!autoPlay && !manualLive)
    }

    private var staticBackground: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: color(0xC3BFC5), location: 0),
                                   .init(color: color(0xE9ECF4), location: 0.35),
                                   .init(color: color(0xDED9D7), location: 0.54),
                                   .init(color: color(0xD0C1BA), location: 0.76),
                                   .init(color: color(0xD6D6E2), location: 1)],
                           startPoint: .top, endPoint: .bottom)
            Ellipse().fill(color(0x8B8580).opacity(0.23))
                .frame(width: 300, height: 170).blur(radius: 40).position(x: 150, y: 164)
            Ellipse().fill(color(0xCBDCF2).opacity(0.33))
                .frame(width: 160, height: 180).blur(radius: 48).position(x: 322, y: 210)
            Ellipse().fill(color(0xF7F6FA).opacity(0.45))
                .frame(width: 310, height: 185).blur(radius: 42).position(x: 151, y: 350)
            Ellipse().fill(color(0xBFAE9B).opacity(0.23))
                .frame(width: 310, height: 150).blur(radius: 40).position(x: 225, y: 640)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private var frostedConversation: some View {
        ZStack(alignment: .topLeading) {
            Image(systemName: "chevron.left").font(.system(size: 15, weight: .medium))
                .foregroundStyle(color(0x66737C)).position(x: 26, y: 97)
            Circle().fill(color(0xBBC3C9)).frame(width: 34, height: 34).position(x: 196.5, y: 88)
            Text("NV").font(.system(size: 10, weight: .semibold))
                .foregroundStyle(color(0x636E76)).position(x: 196.5, y: 88)
            Text("Nori Vale").font(.system(size: 12, weight: .medium))
                .foregroundStyle(color(0x555C61)).position(x: 196.5, y: 117)
            RoundedRectangle(cornerRadius: 18).fill(color(0xF9FAFA))
                .frame(width: 194, height: 61).offset(x: 26, y: 246)
            Text("A number for the\ngood days.")
                .font(.system(size: 14)).foregroundStyle(color(0x637078))
                .frame(width: 165, height: 42, alignment: .leading).offset(x: 40, y: 254)
            RoundedRectangle(cornerRadius: 20).fill(color(0xAABFCB))
                .frame(width: 128, height: 48).offset(x: 239, y: 568)
            Text("Saved that one.").font(.system(size: 13))
                .foregroundStyle(color(0x637887)).position(x: 303, y: 592)
            Capsule().fill(color(0xA8B6BD).opacity(0.65))
                .frame(width: 57, height: 5).offset(x: 41, y: 329)
            Capsule().fill(color(0xB7C1C4).opacity(0.65))
                .frame(width: 105, height: 4).offset(x: 40, y: 343)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .opacity(0.13)
        .blur(radius: 20)
        .accessibilityHidden(true)
    }

    private var topControls: some View {
        ZStack(alignment: .topLeading) {
            Text("Send with feeling").font(.system(size: 17, weight: .regular))
                .foregroundStyle(color(0x777478)).position(x: 196.5, y: 93)
            RoundedRectangle(cornerRadius: 7).fill(color(0x9EA0A9).opacity(0.28))
                .frame(width: 220, height: 29).offset(x: 87, y: 118)
            RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.96))
                .frame(width: 106, height: 25).offset(x: 89, y: 120)
                .shadow(color: .black.opacity(0.06), radius: 1.5, y: 1)
            Text("Bubble").font(.system(size: 11, weight: .semibold))
                .foregroundStyle(color(0x343339)).position(x: 142, y: 132.5)
            Text("Scene").font(.system(size: 11, weight: .medium))
                .foregroundStyle(color(0x343339)).position(x: 252, y: 132.5)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private var composer: some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(.ultraThinMaterial)
                .overlay(Capsule().fill(Color.white.opacity(0.21)))
                .overlay(Capsule().stroke(Color.white.opacity(0.52), lineWidth: 1))
                .frame(width: 345, height: 50).offset(x: 24, y: 714)
                .shadow(color: color(0x5A6570).opacity(0.06), radius: 10, y: 5)
            Circle().fill(color(0x7C8A94).opacity(0.09)).frame(width: 24, height: 24)
                .position(x: 47, y: 739)
            Image(systemName: "plus").font(.system(size: 15, weight: .light))
                .foregroundStyle(color(0x78848C)).position(x: 47, y: 739)
            Text("60").font(.system(size: 16, weight: .medium))
                .foregroundStyle(color(0x4F5A62)).position(x: 84, y: 739)
            Text("Message").font(.system(size: 12))
                .foregroundStyle(color(0x9AA4AB)).position(x: 142, y: 739)
            Circle().fill(color(0x287AE0)).frame(width: 32, height: 32).position(x: 343, y: 739)
                .shadow(color: color(0x3983D8).opacity(0.18), radius: 5, y: 2)
            Image(systemName: "arrow.up").font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white).position(x: 343, y: 739)
            Text("Preview only · no message is sent").font(.system(size: 10))
                .foregroundStyle(color(0x8A9095)).position(x: 196.5, y: 789)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func animatedPreview(_ shown: SendAmplifyPreviewPose) -> some View {
        let pose = reduceMotion ? SendAmplifyPreviewPose.rest : shown
        let scale = pose.scale
        let angle = pose.rotation * .pi / 180
        let sy = scale * pose.stretch
        let width = 270 * scale * cos(angle) + 200 * sy * abs(sin(angle))
        let center = CGPoint(x: pose.right - width / 2 + 9 * scale * cos(angle) + 33 * sy * sin(angle),
                             y: pose.bodyY + 9 * scale * sin(angle) - 33 * sy * cos(angle))
        return ZStack(alignment: .topLeading) {
            // The impact is a soft trailing shadow, not a second sharp copy of the message.
            Image(uiImage: SendAmplifyPreviewArtwork.impact)
                .resizable().interpolation(.high)
                .frame(width: 63 + 47 * pose.impactProgress, height: 76 + 74 * pose.impactProgress)
                .position(x: 302 - 21 * pose.impactProgress, y: 259 + 5 * pose.impactProgress)
                .opacity(pose.impact)
            Image(uiImage: SendAmplifyPreviewArtwork.numeral)
                .resizable().interpolation(.high)
                .frame(width: 296 * scale, height: 300 * scale)
                .scaleEffect(x: 1, y: pose.stretch)
                .rotationEffect(.degrees(pose.rotation))
                .position(center).zIndex(1)
                .opacity(1 - pose.ink)
            Image(uiImage: SendAmplifyPreviewArtwork.ink)
                .resizable().interpolation(.high)
                .frame(width: 52, height: 54)
                .position(x: pose.right - 23, y: pose.bodyY)
                .opacity(pose.ink)
            Text("SEND WITH " + names[shown.selected].uppercased())
                .font(.system(size: 8.2, weight: .semibold)).tracking(0.25)
                .foregroundStyle(color(0x68646E)).opacity(shown.selection * 0.65)
                .frame(width: 136, height: 12, alignment: .trailing)
                .position(x: 251, y: 223 + shown.focus * 52)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func effectRail(_ pose: SendAmplifyPreviewPose) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(.ultraThinMaterial)
                .overlay(Capsule().fill(Color.white.opacity(0.69)))
                .overlay(Capsule().stroke(Color.white.opacity(0.72), lineWidth: 0.7))
                .frame(width: 42, height: 198).offset(x: 338, y: 230)
                .shadow(color: .black.opacity(0.035), radius: 4, y: 2)
            ForEach(0..<4) { index in
                let y = 251 + Double(index) * 52
                Circle().fill(color(0xABA8B4)).frame(width: 8.4, height: 8.4)
                    .position(x: 359, y: y)
                if index != pose.selected || pose.selection < 0.5 {
                    Text(names[index].uppercased()).font(.system(size: 8.2, weight: .semibold))
                        .foregroundStyle(color(0x89838F)).opacity(0.78)
                        .frame(width: 110, height: 16, alignment: .trailing)
                        .position(x: 267, y: y)
                }
            }
            ZStack {
                Circle().fill(color(0x087CFA))
                Image(systemName: "arrow.up").font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
            }
            .frame(width: 24, height: 24)
            .opacity(pose.selection)
            .position(x: 359, y: 251 + pose.railFocus * 52)
            Circle().fill(color(0x64626A)).frame(width: 22, height: 22).position(x: 359, y: 463)
            Image(systemName: "xmark").font(.system(size: 9, weight: .semibold))
                .foregroundStyle(.white).position(x: 359, y: 463)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private var interactionSurface: some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                Spacer().frame(height: 184)
                HStack(spacing: 0) {
                    Spacer().frame(width: 12)
                    Color.clear.frame(width: 369, height: 330)
                        .contentShape(Rectangle())
                        .gesture(previewDrag)
                        .accessibilityElement()
                        .accessibilityLabel("Message effect")
                        .accessibilityValue(names[manualRow])
                        .accessibilityAdjustableAction { direction in
                            let delta: Double = direction == .increment ? 1 : -1
                            selectEffect(min(3, max(0, Double(manualRow) + delta)))
                        }
                    Spacer().frame(width: 12)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
            VStack(spacing: 0) {
                Spacer().frame(height: 717)
                HStack(spacing: 0) {
                    Spacer().frame(width: 321)
                    Button(action: amplify) {
                        Color.clear.frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Preview amplify effect")
                    Spacer().frame(width: 28)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
            VStack(spacing: 0) {
                Spacer().frame(height: 441)
                HStack(spacing: 0) {
                    Spacer().frame(width: 337)
                    Button(action: resetEffect) { Color.clear.frame(width: 44, height: 44).contentShape(Rectangle()) }
                        .buttonStyle(.plain).accessibilityLabel("Reset effect preview")
                    Spacer().frame(width: 12)
                }
                Spacer(minLength: 0)
            }
            .frame(width: 393, height: 852, alignment: .topLeading)
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private var previewDrag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("SendAmplifyPreviewStage"))
            .onChanged { value in
                let now = Date()
                if !isDragging {
                    capturedPose = currentPose(at: now)
                    dragStartFocus = capturedPose.focus
                    releaseFocus = capturedPose.focus.rounded()
                    manualActivated = false
                    isDragging = true
                    manualLive = true
                }
                let direct = value.startLocation.x > 330
                let target = direct ? (value.location.y - 251) / 52 : dragStartFocus + value.translation.height / 52
                releaseFocus = min(3, max(0, target)).rounded()
                let distance = max(abs(value.translation.height), abs(value.translation.width))
                if distance > 6 && (!manualActivated || manualRow != Int(releaseFocus)) {
                    beginEvent(Int(releaseFocus), at: now)
                }
                lastTouch = now
            }
            .onEnded { _ in
                let now = Date()
                if !manualActivated { beginEvent(Int(releaseFocus), at: now) }
                isDragging = false
                lastTouch = now
            }
    }

    private func currentPose(at now: Date) -> SendAmplifyPreviewPose {
        if reduceMotion {
            var pose = SendAmplifyPreviewPose.rest
            pose.focus = Double(manualRow)
            pose.railFocus = pose.focus
            pose.selection = hasSelection ? 1 : 0
            return pose
        }
        if let previewTime { return Self.loopPose(previewTime) }
        if manualLive {
            if isDragging && !manualActivated { return capturedPose }
            let age = max(0, now.timeIntervalSince(eventStarted))
            let target = Self.eventPose(row: manualRow, time: age)
            let shown = Self.mix(capturedPose, target, Self.ease(age / 0.1))
            if isDragging { return shown }
            let elapsed = now.timeIntervalSince(lastTouch)
            if elapsed < 2.6 { return Self.mix(shown, .rest, Self.ease((elapsed - 2) / 0.6)) }
        }
        guard autoPlay else { return .rest }
        let elapsed = now.timeIntervalSince(lastTouch)
        let time = lastTouch == .distantPast ? now.timeIntervalSince(epoch) : elapsed - 2.6
        return Self.loopPose(time)
    }

    private func beginEvent(_ row: Int, at now: Date) {
        let shown = currentPose(at: now)
        capturedPose = shown
        manualRow = row
        hasSelection = true
        releaseFocus = Double(row)
        eventStarted = now
        manualActivated = true
        manualLive = true
        tickIfNeeded(index: row, now: now)
    }

    private func selectEffect(_ focus: Double) {
        let now = Date()
        beginEvent(Int(focus.rounded()), at: now)
        lastTouch = now
    }

    private func amplify() { selectEffect(0) }

    private func resetEffect() {
        capturedPose = .rest
        releaseFocus = 3
        manualRow = 3
        hasSelection = false
        manualActivated = false
        lastTouch = .distantPast
        epoch = Date()
        manualLive = false
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }

    private func tickIfNeeded(index: Int, now: Date) {
        guard index != hapticIndex, now.timeIntervalSince(lastHaptic) > 0.12 else { return }
        hapticIndex = index
        lastHaptic = now
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.55)
    }

    private static func loopPose(_ rawTime: Double) -> SendAmplifyPreviewPose {
        let t = (rawTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        if t < 1.1 || t >= 7 { return .rest }
        if t < 1.35 { return mix(.rest, eventPose(row: 3, time: 0.25), ease((t - 1.1) / 0.25)) }
        if t < 2.95 { return eventPose(row: 3, time: t - 1.1) }
        if t < 4.1 { return eventPose(row: 2, time: t - 2.95) }
        if t < 5.55 { return eventPose(row: 1, time: t - 4.1) }
        if t < 6.65 { return eventPose(row: 0, time: t - 5.55) }
        return mix(eventPose(row: 0, time: 1.10), .rest, ease((t - 6.65) / 0.35))
    }

    /// Each preview restarts at its own tiny seed. Row position never sets message scale.
    private static func eventPose(row: Int, time: Double) -> SendAmplifyPreviewPose {
        let t = max(0, time)
        var pose = SendAmplifyPreviewPose.rest
        pose.focus = Double(row)
        pose.railFocus = pose.focus
        pose.selection = 1
        if row == 3 {
            pose.scale = 0.14
            pose.bodyY = 409.7
            pose.right = 334.5
            pose.ink = ease(t / 0.16)
            return pose
        }
        let frames: [SendAmplifyPreviewFrame]
        if row == 2 {
            frames = [
                .init(0, 0.5, 0.4, 416, 332),
                .init(0.05, 5.8, 4.4, 415.6, 331.9),
                .init(0.25, 34.9, 24.7, 370.4, 331.9),
                .init(0.55, 50.9, 37.8, 362.4, 329),
                .init(1.15, 50.9, 37.8, 362.4, 329)
            ]
        } else if row == 1 {
            frames = [
                .init(0, 0.5, 0.4, 375, 334.8),
                .init(0.0667, 11.6, 8.7, 366.8, 334.8),
                .init(0.30, 74.2, 55.3, 299.8, 327.5),
                .init(0.5667, 93.2, 68.4, 290.4, 324.6),
                .init(0.7333, 93.2, 68.4, 290.4, 324.6),
                .init(1.2833, 40.8, 30.6, 315.1, 330.4),
                .init(1.45, 40.8, 30.6, 315.1, 330.4)
            ]
        } else {
            frames = [
                .init(0, 40.8, 30.6, 315.1, 330.4),
                .init(0.0167, 141.2, 116.4, 333.3, 314.4, 12.1),
                .init(0.05, 189.2, 155.7, 334.1, 309, 11.5),
                .init(0.10, 240.2, 195, 329, 302, 9.5),
                .init(0.1667, 285.3, 225.6, 312.2, 295.5, 5),
                .init(0.20, 260.5, 203.8, 299.8, 299.8, 3),
                .init(0.25, 206.7, 157.2, 283.8, 307, 1.36),
                .init(0.30, 133.9, 101.9, 273.6, 320, -1.1),
                .init(0.3333, 64, 48, 267.1, 329),
                .init(0.35, 37.8, 27.7, 262.7, 331.9),
                .init(0.45, 40.8, 30.6, 264.2, 330.4),
                .init(1.10, 40.8, 30.6, 264.2, 330.4)
            ]
            pose.railFocus = 1 - ease(t / 0.17)
            pose.impact = ease((t - 0.35) / 0.10) * (1 - ease((t - 0.48) / 0.27))
            pose.impactProgress = ease((t - 0.35) / 0.23)
        }
        let values = sample(frames, at: t)
        pose.rotation = values.rotation
        let angle = abs(values.rotation) * .pi / 180
        let c = cos(angle), sn = sin(angle), determinant = max(0.1, c * c - sn * sn)
        let sx = max(0.001, (values.width * c - values.height * sn) / (270 * determinant))
        let sy = max(0.001, (values.height * c - values.width * sn) / (200 * determinant))
        pose.scale = sx
        pose.stretch = sy / sx
        pose.bodyY = values.bodyY
        pose.right = values.right
        return pose
    }

    /// Clamped cubic Hermite interpolation keeps the measured trajectory and continuous velocity.
    private static func sample(_ frames: [SendAmplifyPreviewFrame], at time: Double) -> SendAmplifyPreviewFrame {
        guard let i = (0..<(frames.count - 1)).first(where: { time < frames[$0 + 1].time }) else { return frames.last! }
        let a = frames[i], b = frames[i + 1]
        let previous = frames[max(0, i - 1)], next = frames[min(frames.count - 1, i + 2)]
        let duration = b.time - a.time
        let u = min(1, max(0, (time - a.time) / duration))
        func v(_ key: KeyPath<SendAmplifyPreviewFrame, Double>) -> Double {
            let x = a[keyPath: key], y = b[keyPath: key]
            if x == y { return x }
            let m0 = i == 0 ? 0 : (y - previous[keyPath: key]) / (b.time - previous.time)
            let m1 = i + 2 == frames.count ? 0 : (next[keyPath: key] - x) / (next.time - a.time)
            let value = (2 * u * u * u - 3 * u * u + 1) * x + (u * u * u - 2 * u * u + u) * m0 * duration
                + (-2 * u * u * u + 3 * u * u) * y + (u * u * u - u * u) * m1 * duration
            return min(max(x, y), max(min(x, y), value))
        }
        return .init(time, v(\.width), v(\.height), v(\.bodyY), v(\.right), v(\.rotation))
    }

    private static func mix(_ a: SendAmplifyPreviewPose, _ b: SendAmplifyPreviewPose, _ p: Double) -> SendAmplifyPreviewPose {
        func v(_ x: Double, _ y: Double) -> Double { x + (y - x) * p }
        return .init(scale: v(a.scale, b.scale), right: v(a.right, b.right), bodyY: v(a.bodyY, b.bodyY),
                     focus: v(a.focus, b.focus), railFocus: v(a.railFocus, b.railFocus), selection: v(a.selection, b.selection),
                     rotation: v(a.rotation, b.rotation), stretch: v(a.stretch, b.stretch), ink: v(a.ink, b.ink), impact: v(a.impact, b.impact), impactProgress: v(a.impactProgress, b.impactProgress))
    }

    private static func ease(_ raw: Double) -> Double {
        let u = min(1, max(0, raw))
        return u * u * (3 - 2 * u)
    }

    private func color(_ hex: UInt32) -> Color { SendAmplifyPreviewPalette.color(hex) }
}

private struct SendAmplifyPreviewFrame {
    var time: Double
    var width: Double
    var height: Double
    var bodyY: Double
    var right: Double
    var rotation: Double
    init(_ time: Double, _ width: Double, _ height: Double, _ bodyY: Double, _ right: Double, _ rotation: Double = 0) {
        self.time = time; self.width = width; self.height = height
        self.bodyY = bodyY; self.right = right; self.rotation = rotation
    }
}

private struct SendAmplifyPreviewPose {
    var scale: Double = 0.16
    var right: Double = 324
    var bodyY: Double = 461
    var focus: Double = 3
    var railFocus: Double = 3
    var selection: Double = 0
    var rotation: Double = 0
    var stretch: Double = 1
    var ink: Double = 0
    var impact: Double = 0
    var impactProgress: Double = 0
    var selected: Int { min(3, max(0, Int(focus.rounded()))) }
    static let rest = SendAmplifyPreviewPose()
}

private enum SendAmplifyPreviewPalette {
    static let background = color(0xD6D6E2)
    static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255)
    }
}


/// A cached code-drawn billboard keeps the beveled ink and cloth at one material resolution.
private enum SendAmplifyPreviewArtwork {
    static let ink: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: 52, height: 54), format: format).image { renderer in
            let c = renderer.cgContext
            c.setShadow(offset: .zero, blur: 1.2, color: ui(0x8F8792).withAlphaComponent(0.35).cgColor)
            let shape = UIBezierPath(roundedRect: CGRect(x: 3, y: 3, width: 44, height: 46), cornerRadius: 11)
            ui(0x938488).setFill(); shape.fill()
            c.setShadow(offset: .zero, blur: 0)
            let tail = UIBezierPath()
            tail.move(to: CGPoint(x: 41, y: 41)); tail.addQuadCurve(to: CGPoint(x: 50, y: 50), controlPoint: CGPoint(x: 42, y: 47))
            tail.addQuadCurve(to: CGPoint(x: 36, y: 46), controlPoint: CGPoint(x: 41, y: 50)); tail.close(); tail.fill()
            for i in 0..<320 {
                let x = 6 + hash(i + 39) * 37, y = 6 + hash(i + 477) * 39
                c.setFillColor(ui(i % 2 == 0 ? 0xE6DEE8 : 0x655B68).withAlphaComponent(0.10).cgColor)
                c.fillEllipse(in: CGRect(x: x, y: y, width: 0.65, height: 0.65))
            }
        }
    }()

    static let impact: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(size: CGSize(width: 132, height: 132), format: format).image { renderer in
            let c = renderer.cgContext
            c.translateBy(x: 66, y: 66)
            c.scaleBy(x: 1, y: 1)
            let colors = [ui(0x28212C).withAlphaComponent(0.34), ui(0x302837).withAlphaComponent(0.24), ui(0x302837).withAlphaComponent(0)]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: [0, 0.35, 1])!
            c.drawRadialGradient(gradient, startCenter: .zero, startRadius: 0, endCenter: .zero, endRadius: 62, options: [])
        }
        return image
    }()

    static let numeral: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.scale = 3
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: 296, height: 300), format: format).image { renderer in
            let context = renderer.cgContext
            let glyph = glyphPath(in: CGRect(x: 4, y: 83, width: 270, height: 200))
            context.saveGState()
            context.setShadow(offset: CGSize(width: 2, height: 3), blur: 3, color: ui(0x151319).withAlphaComponent(0.22).cgColor)
            context.addPath(glyph); context.setFillColor(ui(0x121116).cgColor); context.fillPath(using: .evenOdd)
            context.restoreGState()
            for depth in stride(from: 7, through: 1, by: -1) {
                context.saveGState()
                context.translateBy(x: Double(depth) * 0.65, y: Double(depth) * 0.5)
                context.addPath(glyph); context.setFillColor(ui(0x17151A).cgColor); context.fillPath(using: .evenOdd)
                context.restoreGState()
            }
            context.saveGState()
            context.translateBy(x: -1.5, y: -1.4)
            context.addPath(glyph); context.setFillColor(ui(0x6D686C).cgColor); context.fillPath(using: .evenOdd)
            context.restoreGState()
            gradient(context, path: glyph, colors: [ui(0x474347), ui(0x19171B), ui(0x0D0C10), ui(0x211E23)],
                     locations: [0, 0.25, 0.73, 1], start: CGPoint(x: 0, y: 83), end: CGPoint(x: 35, y: 283))
            cap(context)
        }
    }()

    private static func glyphPath(in target: CGRect) -> CGPath {
        // The straight cut horn of the six is essential to the reference silhouette.
        // Both counters are explicit paths so their proportions match on every stack.
        let body = CGMutablePath()
        body.move(to: CGPoint(x: 72, y: 0))
        body.addLine(to: CGPoint(x: 112, y: 0))
        body.addLine(to: CGPoint(x: 57, y: 77))
        body.addCurve(to: CGPoint(x: 83, y: 72), control1: CGPoint(x: 65, y: 73), control2: CGPoint(x: 74, y: 71))
        body.addCurve(to: CGPoint(x: 134, y: 132), control1: CGPoint(x: 115, y: 75), control2: CGPoint(x: 134, y: 99))
        body.addCurve(to: CGPoint(x: 67, y: 200), control1: CGPoint(x: 135, y: 172), control2: CGPoint(x: 108, y: 200))
        body.addCurve(to: CGPoint(x: 0, y: 141), control1: CGPoint(x: 26, y: 200), control2: CGPoint(x: 0, y: 176))
        body.addCurve(to: CGPoint(x: 17, y: 88), control1: CGPoint(x: 0, y: 122), control2: CGPoint(x: 7, y: 104))
        body.addLine(to: CGPoint(x: 72, y: 0)); body.closeSubpath()
        body.addEllipse(in: CGRect(x: 36, y: 111, width: 61, height: 58))
        body.addEllipse(in: CGRect(x: 142, y: 0, width: 128, height: 200))
        body.addEllipse(in: CGRect(x: 179, y: 35, width: 54, height: 130))
        var transform = CGAffineTransform(a: target.width / 270, b: 0, c: 0, d: target.height / 200, tx: target.minX, ty: target.minY)
        return body.copy(using: &transform)!
    }

    private static func cap(_ context: CGContext) {
        let cloth = CGMutablePath()
        cloth.move(to: CGPoint(x: 164, y: 76))
        cloth.addCurve(to: CGPoint(x: 219, y: -3), control1: CGPoint(x: 181, y: 43), control2: CGPoint(x: 203, y: 1))
        cloth.addCurve(to: CGPoint(x: 249, y: 15), control1: CGPoint(x: 231, y: -7), control2: CGPoint(x: 242, y: 4))
        cloth.addCurve(to: CGPoint(x: 272, y: 91), control1: CGPoint(x: 265, y: 38), control2: CGPoint(x: 273, y: 65))
        cloth.addCurve(to: CGPoint(x: 264, y: 102), control1: CGPoint(x: 272, y: 98), control2: CGPoint(x: 269, y: 103))
        cloth.addCurve(to: CGPoint(x: 164, y: 76), control1: CGPoint(x: 230, y: 88), control2: CGPoint(x: 192, y: 73))
        cloth.closeSubpath()
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 2), blur: 2, color: UIColor.black.withAlphaComponent(0.18).cgColor)
        gradient(context, path: cloth, colors: [ui(0x7E0712), ui(0xE42C32), ui(0xBA111D), ui(0x7F0711)],
                 locations: [0, 0.36, 0.71, 1], start: CGPoint(x: 163, y: 29), end: CGPoint(x: 274, y: 89))
        context.restoreGState()
        context.saveGState(); context.addPath(cloth); context.clip()
        let fold = CGMutablePath()
        fold.move(to: CGPoint(x: 219, y: -2))
        fold.addCurve(to: CGPoint(x: 247, y: 84), control1: CGPoint(x: 234, y: 29), control2: CGPoint(x: 241, y: 57))
        context.addPath(fold); context.setStrokeColor(ui(0x57000B).withAlphaComponent(0.12).cgColor)
        context.setLineWidth(2); context.strokePath()
        context.restoreGState()

        let cuff = CGMutablePath()
        cuff.move(to: CGPoint(x: 151, y: 66))
        cuff.addCurve(to: CGPoint(x: 274, y: 96), control1: CGPoint(x: 186, y: 64), control2: CGPoint(x: 245, y: 81))
        cuff.addCurve(to: CGPoint(x: 266, y: 139), control1: CGPoint(x: 277, y: 108), control2: CGPoint(x: 273, y: 129))
        cuff.addCurve(to: CGPoint(x: 138, y: 108), control1: CGPoint(x: 227, y: 122), control2: CGPoint(x: 173, y: 116))
        cuff.addCurve(to: CGPoint(x: 151, y: 66), control1: CGPoint(x: 138, y: 94), control2: CGPoint(x: 144, y: 76))
        cuff.closeSubpath()
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 5), blur: 4, color: UIColor.black.withAlphaComponent(0.30).cgColor)
        gradient(context, path: cuff, colors: [ui(0xFFFDF8), ui(0xE8E7ED), ui(0x9B9BA6)], locations: [0, 0.5, 1],
                 start: CGPoint(x: 188, y: 72), end: CGPoint(x: 208, y: 129))
        context.restoreGState()
        context.saveGState(); context.addPath(cuff); context.clip()
        for index in 0..<180 {
            let x = 138 + hash(index + 11) * 138
            let y = 64 + hash(index + 97) * 79
            context.setFillColor(ui(index % 3 == 0 ? 0x8F8C99 : 0xFFFFFC).withAlphaComponent(index % 3 == 0 ? 0.05 : 0.16).cgColor)
            context.fillEllipse(in: CGRect(x: x, y: y, width: 0.5 + hash(index + 201), height: 2 + hash(index + 33) * 3))
        }
        context.restoreGState()
        // Fine, unequal fibers break the lower contour; the cuff is cloth, not a plastic band.
        for index in 0..<96 {
            let u = Double(index) / 95
            let x = 138 + 128 * u
            let y = 108 + 31 * u - 8 * sin(.pi * u)
            let length = 2 + 8 * hash(index + 317)
            context.setFillColor(ui(index % 4 == 0 ? 0xB9B6C2 : 0xEAE8EF).withAlphaComponent(0.68).cgColor)
            context.fillEllipse(in: CGRect(x: x, y: y - 2, width: 0.7 + hash(index + 99), height: length))
        }
    }

    private static func gradient(_ context: CGContext, path: CGPath, colors: [UIColor], locations: [CGFloat], start: CGPoint, end: CGPoint) {
        context.saveGState(); context.addPath(path); context.clip(using: .evenOdd)
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations)!
        context.drawLinearGradient(gradient, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        context.restoreGState()
    }

    private static func hash(_ index: Int) -> Double {
        let value = sin(Double(index) * 12.9898) * 43758.5453
        return value - floor(value)
    }

    private static func ui(_ hex: UInt32) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255,
                green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: 1)
    }
}
