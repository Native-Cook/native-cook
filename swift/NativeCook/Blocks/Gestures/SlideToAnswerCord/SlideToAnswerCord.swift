import SwiftUI

/// The SwiftUI shell preserves React Native's mixed coordinate spaces: the
/// Canvas-like scene is scaled from 402 pt, while its labels and controls stay
/// at their authored point sizes.
struct SlideToAnswerCord: View {
    static let loop: Double = SlideToAnswerCordMotion.loopMS / 1_000

    @State private var simulation: SlideToAnswerCordSimulation
    @State private var muted = false
    @State private var pressRed = 1.0
    @State private var pressGreen = 1.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        _simulation = State(initialValue: SlideToAnswerCordSimulation(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            GeometryReader { stageProxy in
                let size = stageProxy.size
                let frame = simulation.frame
                let buttonsY = size.height - safeAreaProxy.safeAreaInsets.bottom
                    - SlideToAnswerCordTheme.Layout.buttonsBottom
                    - SlideToAnswerCordTheme.Layout.labelSize
                    - SlideToAnswerCordTheme.Layout.labelGap
                    - SlideToAnswerCordTheme.Layout.button / 2

                ZStack(alignment: .topLeading) {
                    SlideToAnswerCordScene(
                        frame: frame,
                        buttonsY: buttonsY,
                        showsControls: false,
                        pressRed: pressRed,
                        pressGreen: pressGreen
                    )

                    if !SlideToAnswerCordTheme.replica {
                        SlideToAnswerCordButtonHalos(
                            frame: frame,
                            buttonsY: buttonsY,
                            size: size,
                            pressRed: pressRed,
                            pressGreen: pressGreen
                        )
                        // RN's BackdropBlur samples the already-rendered scene at
                        // a fixed 18 pt radius. Re-render the same value-only
                        // backdrop plus the source halos, then clip it to the
                        // two glass surfaces.
                        ZStack(alignment: .topLeading) {
                            SlideToAnswerCordScene(
                                frame: frame,
                                buttonsY: buttonsY,
                                showsControls: false,
                                pressRed: pressRed,
                                pressGreen: pressGreen
                            )
                            SlideToAnswerCordButtonHalos(
                                frame: frame,
                                buttonsY: buttonsY,
                                size: size,
                                pressRed: pressRed,
                                pressGreen: pressGreen
                            )
                        }
                        .blur(radius: SlideToAnswerCordTheme.Glass.blur)
                        .mask(SlideToAnswerCordGlassBlurMask(
                            frame: frame,
                            buttonsY: buttonsY,
                            size: size,
                            pressRed: pressRed,
                            pressGreen: pressGreen
                        ))
                        SlideToAnswerCordButtonSurfaces(
                            frame: frame,
                            buttonsY: buttonsY,
                            size: size,
                            pressRed: pressRed,
                            pressGreen: pressGreen
                        )
                    }

                    SlideToAnswerCordTouchView(simulation: simulation)
                        .frame(width: size.width, height: size.height)

                    if !SlideToAnswerCordTheme.replica {
                        SlideToAnswerCordCopy(frame: frame, size: size)
                        SlideToAnswerCordControls(
                            frame: frame,
                            size: size,
                            buttonsY: buttonsY,
                            muted: $muted,
                            pressRed: $pressRed,
                            pressGreen: $pressGreen,
                            command: { simulation.command($0) }
                        )
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(.rect)
                .onAppear {
                    SlideToAnswerCordAssets.assertAllLoaded()
                    simulation.configure(size: size, reducedMotion: reduceMotion)
                    simulation.start()
                }
                .onDisappear(perform: simulation.stop)
                .onChange(of: size) { _, next in
                    simulation.configure(size: next, reducedMotion: reduceMotion)
                }
                .onChange(of: reduceMotion) { _, next in
                    simulation.configure(size: size, reducedMotion: next)
                }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .background(SlideToAnswerCordTheme.Colors.skyTop)
    }
}

private struct SlideToAnswerCordCopy: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let size: CGSize

    var body: some View {
        VStack(spacing: 0) {
            Text(SlideToAnswerCordTheme.Copy.name)
                .font(.system(size: SlideToAnswerCordTheme.Layout.nameSize, weight: .semibold))
                .tracking(SlideToAnswerCordTheme.Layout.titleTracking)
                .foregroundStyle(SlideToAnswerCordTheme.Colors.ink)
                .frame(height: SlideToAnswerCordTheme.Layout.nameLine)
                .accessibilityAddTraits(.isHeader)
            Text(SlideToAnswerCordTheme.Copy.meta)
                .font(.system(size: SlideToAnswerCordTheme.Layout.metaSize))
                .foregroundStyle(SlideToAnswerCordTheme.Colors.rgba(244, 242, 255, 0.5))
                .padding(.top, SlideToAnswerCordTheme.Space.xs)
            SlideToAnswerCordStatus(
                status: status,
                key: frame.phase == .connected ? "timer" : status
            )
            .padding(.top, SlideToAnswerCordTheme.Layout.gap)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, SlideToAnswerCordTheme.Layout.side)
        .frame(maxHeight: .infinity, alignment: .top)
        .offset(y: size.height * SlideToAnswerCordTheme.Layout.copyTopRatio)
        .allowsHitTesting(false)
    }

    private var status: String {
        switch frame.phase {
        case .connected:
            let seconds = max(0, Int(frame.callElapsed))
            return String(format: "%02d:%02d", seconds / 60, seconds % 60)
        case .declined:
            return SlideToAnswerCordTheme.Copy.declined
        case .ended:
            return SlideToAnswerCordTheme.Copy.ended
        case .ringing:
            return SlideToAnswerCordTheme.Copy.ringing
        }
    }
}

/// Reanimated mounts a new status for every phase but keeps the timer's key
/// stable. Keep that identity behaviour while explicitly driving its 200 ms
/// entering and 120 ms exiting opacity transitions in SwiftUI.
private struct SlideToAnswerCordStatus: View {
    let status: String
    let key: String

    @State private var previousStatus: String
    @State private var previousKey: String
    @State private var outgoing: String?
    @State private var incomingOpacity = 1.0
    @State private var outgoingOpacity = 0.0

    init(status: String, key: String) {
        self.status = status
        self.key = key
        _previousStatus = State(initialValue: status)
        _previousKey = State(initialValue: key)
    }

    var body: some View {
        ZStack {
            if let outgoing {
                text(outgoing)
                    .opacity(outgoingOpacity)
            }
            text(status)
                .opacity(incomingOpacity)
        }
        .frame(height: SlideToAnswerCordTheme.Layout.statusLine)
        .onChange(of: status) { _, next in
            defer { previousStatus = next }
            guard key != previousKey else { return }
            let departing = previousStatus
            previousKey = key
            outgoing = departing
            outgoingOpacity = 1
            incomingOpacity = 0
            // Reanimated's FadeIn/FadeOut default to withTiming's exact
            // Easing.inOut(Easing.quad), preserved by this custom animation.
            withAnimation(Animation(SlideToAnswerCordInOutQuad(duration: SlideToAnswerCordMotion.Status.fadeOut))) { outgoingOpacity = 0 }
            withAnimation(Animation(SlideToAnswerCordInOutQuad(duration: SlideToAnswerCordMotion.Status.fadeIn))) { incomingOpacity = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + SlideToAnswerCordMotion.Status.fadeOut) {
                guard outgoing == departing else { return }
                outgoing = nil
            }
        }
    }

    private func text(_ value: String) -> some View {
        Text(value)
            .font(.system(size: SlideToAnswerCordTheme.Layout.statusSize))
            .monospacedDigit()
            .foregroundStyle(SlideToAnswerCordTheme.Colors.mute)
    }
}

/// Reanimated `withTiming` default: Easing.inOut(Easing.quad). `value` is the
/// vector to animate toward, so SwiftUI multiplies it by the same normalized
/// piecewise-quadratic progress used on the React Native side.
private struct SlideToAnswerCordInOutQuad: CustomAnimation {
    let duration: TimeInterval

    nonisolated func animate<V>(
        value: V,
        time: TimeInterval,
        context: inout AnimationContext<V>
    ) -> V? where V: VectorArithmetic {
        guard time <= duration else { return nil }
        let progress = max(0, min(1, time / duration))
        let eased = progress < 0.5
            ? 2 * progress * progress
            : 1 - pow(-2 * progress + 2, 2) / 2
        return value.scaled(by: eased)
    }
}

private struct SlideToAnswerCordControls: View {
    let frame: SlideToAnswerCordSimulation.Frame
    let size: CGSize
    let buttonsY: Double
    @Binding var muted: Bool
    @Binding var pressRed: Double
    @Binding var pressGreen: Double
    let command: (SlideToAnswerCordSimulation.Command) -> Void

    private var isConnected: Bool { frame.phase == .connected }
    private var isRinging: Bool { frame.phase == .ringing }
    private var buttonHalf: Double { SlideToAnswerCordTheme.Layout.button / 2 }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button {
                command(isConnected ? .end : .decline)
            } label: {
                Color.clear
                    .frame(width: SlideToAnswerCordTheme.Layout.button, height: SlideToAnswerCordTheme.Layout.button)
                    .contentShape(.circle)
                    .overlay(alignment: .top) {
                        Text(isConnected ? SlideToAnswerCordTheme.Copy.end : SlideToAnswerCordTheme.Copy.decline)
                            .font(.system(size: SlideToAnswerCordTheme.Layout.labelSize))
                            .foregroundStyle(SlideToAnswerCordTheme.Colors.label)
                            .frame(width: SlideToAnswerCordTheme.Layout.labelWidth)
                            .offset(y: SlideToAnswerCordTheme.Layout.button + SlideToAnswerCordTheme.Layout.labelGap)
                    }
            }
            .buttonStyle(SlideToAnswerCordReportingPressStyle(
                scale: $pressRed,
                reducedMotion: frame.reducedMotion
            ))
            .accessibilityLabel(isConnected ? "End call" : "Decline call")
            .disabled(!isRinging && !isConnected)
            .frame(width: SlideToAnswerCordTheme.Layout.button, height: SlideToAnswerCordTheme.Layout.button, alignment: .top)
            .position(
                x: (isConnected ? size.width / 2 : size.width / 2 - SlideToAnswerCordTheme.Layout.buttonGap * frame.geometry.scale),
                y: buttonsY
            )

            SlideToAnswerCordPillControls(muted: $muted, reducedMotion: frame.reducedMotion)
                .opacity(max(0, (frame.morph - 0.6) / 0.4))
                .scaleEffect(0.95 + 0.05 * frame.morph)
                .allowsHitTesting(isConnected)
                .frame(maxWidth: .infinity)
                .frame(height: SlideToAnswerCordTheme.Glass.pillH)
                .position(x: size.width / 2, y: buttonsY + SlideToAnswerCordTheme.Glass.pillY)

            if !isConnected {
                Button {
                    command(.answer)
                } label: {
                    Color.clear
                        .frame(width: SlideToAnswerCordTheme.Layout.button, height: SlideToAnswerCordTheme.Layout.button)
                        .contentShape(.circle)
                        .overlay(alignment: .top) {
                            Text(SlideToAnswerCordTheme.Copy.accept)
                                .font(.system(size: SlideToAnswerCordTheme.Layout.labelSize))
                                .foregroundStyle(SlideToAnswerCordTheme.Colors.label)
                                .frame(width: SlideToAnswerCordTheme.Layout.labelWidth)
                                .offset(y: SlideToAnswerCordTheme.Layout.button + SlideToAnswerCordTheme.Layout.labelGap)
                        }
                }
                .buttonStyle(SlideToAnswerCordReportingPressStyle(
                    scale: $pressGreen,
                    reducedMotion: frame.reducedMotion
                ))
                .accessibilityLabel("Accept call")
                .disabled(!isRinging)
                .frame(width: SlideToAnswerCordTheme.Layout.button, height: SlideToAnswerCordTheme.Layout.button, alignment: .top)
                .position(
                    x: size.width / 2 + SlideToAnswerCordTheme.Layout.buttonGap * frame.geometry.scale,
                    y: buttonsY
                )
            }
        }
    }
}

private struct SlideToAnswerCordPillControls: View {
    @Binding var muted: Bool
    let reducedMotion: Bool

    var body: some View {
        HStack(spacing: 0) {
            ForEach(SlideToAnswerCordTheme.Copy.controls, id: \.name) { control in
                Button {
                    if control.name == "mute" { muted.toggle() }
                } label: {
                    VStack(spacing: SlideToAnswerCordTheme.Layout.controlLabelGap) {
                        Image(systemName: control.symbol)
                            .font(.system(size: SlideToAnswerCordTheme.Layout.controlIconSize))
                            .foregroundStyle(control.name == "mute" && muted ? SlideToAnswerCordTheme.Layout.controlInk : .white)
                        Text(control.label)
                            .font(.system(size: SlideToAnswerCordTheme.Layout.controlLabelSize, weight: .semibold))
                            .foregroundStyle(control.name == "mute" && muted ? SlideToAnswerCordTheme.Layout.controlInk : SlideToAnswerCordTheme.Layout.controlLabel)
                    }
                    .frame(
                        minWidth: SlideToAnswerCordTheme.Layout.controlMinimumWidth,
                        minHeight: SlideToAnswerCordTheme.Layout.controlHeight
                    )
                    .background(control.name == "mute" && muted ? SlideToAnswerCordTheme.Layout.controlOn : .clear, in: Capsule())
                }
                .buttonStyle(SlideToAnswerCordPressStyle(reducedMotion: reducedMotion))
                .accessibilityLabel(control.label)
                .accessibilityAddTraits(control.name == "mute" && muted ? .isSelected : [])
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, SlideToAnswerCordTheme.Space.lg)
    }
}

private struct SlideToAnswerCordPressStyle: ButtonStyle {
    let reducedMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reducedMotion ? SlideToAnswerCordMotion.Press.scale : 1)
            .animation(
                .timingCurve(
                    SlideToAnswerCordMotion.Press.control1.x,
                    SlideToAnswerCordMotion.Press.control1.y,
                    SlideToAnswerCordMotion.Press.control2.x,
                    SlideToAnswerCordMotion.Press.control2.y,
                    duration: configuration.isPressed
                        ? SlideToAnswerCordMotion.Press.inDuration
                        : SlideToAnswerCordMotion.Press.outDuration
                ),
                value: configuration.isPressed
            )
    }
}

private struct SlideToAnswerCordReportingPressStyle: ButtonStyle {
    @Binding var scale: Double
    let reducedMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed, initial: true) { _, pressed in
                let target = pressed && !reducedMotion ? SlideToAnswerCordMotion.Press.scale : 1
                withAnimation(
                    .timingCurve(
                        SlideToAnswerCordMotion.Press.control1.x,
                        SlideToAnswerCordMotion.Press.control1.y,
                        SlideToAnswerCordMotion.Press.control2.x,
                        SlideToAnswerCordMotion.Press.control2.y,
                        duration: pressed
                            ? SlideToAnswerCordMotion.Press.inDuration
                            : SlideToAnswerCordMotion.Press.outDuration
                    )
                ) {
                    scale = target
                }
            }
    }
}

#Preview {
    SlideToAnswerCord(autoPlay: true)
}
