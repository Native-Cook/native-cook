import SwiftUI

struct SlideToAnswerCord: View {
    static let loop: TimeInterval = SlideToAnswerCordMotion.loopMS / 1_000

    private let autoPlay: Bool
    @State private var simulation: SlideToAnswerCordSimulation
    @State private var muted = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _simulation = State(initialValue: SlideToAnswerCordSimulation(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { safeArea in
            let safeBottom = safeArea.safeAreaInsets.bottom
            GeometryReader { stage in
                content(size: stage.size, safeBottom: safeBottom)
                    .onAppear {
                        simulation.configure(size: stage.size, reducedMotion: reduceMotion)
                        simulation.start()
                    }
                    .onDisappear { simulation.stop() }
                    .onChange(of: stage.size) { _, size in
                        simulation.configure(size: size, reducedMotion: reduceMotion)
                    }
                    .onChange(of: reduceMotion) { _, reduced in
                        simulation.configure(size: stage.size, reducedMotion: reduced)
                    }
            }
            .ignoresSafeArea()
        }
        .background(SlideToAnswerCordTheme.Colors.page)
        .preferredColorScheme(.dark)
        .statusBarHidden(false)
    }

    private func content(size: CGSize, safeBottom: Double) -> some View {
        let frame = simulation.frame
        let width = size.width
        let height = size.height
        let layout = SlideToAnswerCordTheme.Layout.self
        let buttonsY = height - safeBottom - layout.buttonsBottom - layout.labelSize - layout.labelGap - layout.button / 2
        let pillTop = buttonsY + SlideToAnswerCordTheme.Glass.pillY - SlideToAnswerCordTheme.Glass.pillH / 2
        let copyTop = height * layout.copyTopRatio
        let copyHeight = layout.nameLine + layout.metaGap + layout.metaLine + layout.gap + layout.statusLine
        let lift = max(0, copyTop + copyHeight - (pillTop - layout.copyToPill))
        let connection = frame.connectedMix
        let connected = frame.phase == .connected
        let redEnabled = frame.phase == .ringing || connected

        return ZStack(alignment: .topLeading) {
            SlideToAnswerCordBackdrop()
            SlideToAnswerCordScene(frame: frame)
            SlideToAnswerCordTouchView(simulation: simulation)

            callCopy(frame: frame)
                .frame(width: width - 2 * layout.side, height: copyHeight, alignment: .top)
                .position(x: width / 2, y: copyTop + copyHeight / 2 - lift * connection)

            controls(connection: connection)
                .frame(width: width - 2 * layout.side, height: 76)
                .position(x: width / 2, y: pillTop + SlideToAnswerCordTheme.Glass.pillH / 2 + 12 + 12 * (1 - connection))
                .opacity(connection)
                .allowsHitTesting(connected)

            if frame.acceptGlow >= 0 {
                Circle()
                    .fill(SlideToAnswerCordTheme.Colors.accept.opacity(0.45 * (1 - frame.acceptGlow)))
                    .frame(width: layout.button, height: layout.button)
                    .scaleEffect(1 + 0.5 * frame.acceptGlow)
                    .blur(radius: 10)
                    .position(x: width / 2 + layout.buttonGap, y: buttonsY)
                    .allowsHitTesting(false)
            }

            callButton(
                color: SlideToAnswerCordTheme.Colors.decline,
                symbol: "phone.down.fill",
                label: connected ? SlideToAnswerCordTheme.Copy.end : SlideToAnswerCordTheme.Copy.decline,
                enabled: redEnabled
            ) {
                simulation.command(connected ? .end : .decline)
            }
            .opacity(redEnabled ? 1 : 0.45)
            .position(x: width / 2 - layout.buttonGap + layout.buttonGap * connection, y: buttonsY + 13)

            callButton(
                color: SlideToAnswerCordTheme.Colors.accept,
                symbol: "phone.fill",
                label: SlideToAnswerCordTheme.Copy.accept,
                enabled: frame.phase == .ringing
            ) {
                simulation.command(.answer)
            }
            .opacity(1 - connection)
            .position(x: width / 2 + layout.buttonGap, y: buttonsY + 13)
            .allowsHitTesting(frame.phase == .ringing)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }

    private func callCopy(frame: SlideToAnswerCordSimulation.Frame) -> some View {
        VStack(spacing: 0) {
            Text(SlideToAnswerCordTheme.Copy.name)
                .font(.system(size: SlideToAnswerCordTheme.Layout.nameSize, weight: .regular))
                .tracking(SlideToAnswerCordTheme.Layout.titleTracking)
                .foregroundStyle(SlideToAnswerCordTheme.Colors.ink)
                .frame(height: SlideToAnswerCordTheme.Layout.nameLine)
            Text(SlideToAnswerCordTheme.Copy.meta)
                .font(.system(size: SlideToAnswerCordTheme.Layout.metaSize, weight: .regular))
                .foregroundStyle(SlideToAnswerCordTheme.Colors.mute)
                .frame(height: SlideToAnswerCordTheme.Layout.metaLine)
                .padding(.top, SlideToAnswerCordTheme.Layout.metaGap)
            Text(status(for: frame))
                .font(.system(size: SlideToAnswerCordTheme.Layout.statusSize, weight: .regular).monospacedDigit())
                .foregroundStyle(SlideToAnswerCordTheme.Colors.faint)
                .frame(height: SlideToAnswerCordTheme.Layout.statusLine)
                .padding(.top, SlideToAnswerCordTheme.Layout.gap)
                .contentTransition(.opacity)
                .animation(.easeOut(duration: 0.2).delay(0.12), value: status(for: frame))
        }
        .accessibilityElement(children: .combine)
    }

    private func status(for frame: SlideToAnswerCordSimulation.Frame) -> String {
        switch frame.phase {
        case .ringing: SlideToAnswerCordTheme.Copy.ringing
        case .connected: clock(frame.callElapsed)
        case .declined: SlideToAnswerCordTheme.Copy.declined
        case .ended: SlideToAnswerCordTheme.Copy.ended
        }
    }

    private func clock(_ value: Double) -> String {
        let total = max(0, Int(value))
        return String(format: "%02d:%02d", total / 60, total % 60)
    }

    private func controls(connection: Double) -> some View {
        HStack(spacing: 0) {
            control(symbol: "mic.slash.fill", label: "Mute", selected: muted) { muted.toggle() }
                .frame(maxWidth: .infinity)
            control(symbol: "circle.grid.3x3.fill", label: "Keypad", selected: false) {}
                .frame(maxWidth: .infinity)
            control(symbol: "speaker.wave.2.fill", label: "Speaker", selected: false) {}
                .frame(maxWidth: .infinity)
        }
        .accessibilityHidden(connection < 0.5)
    }

    private func control(symbol: String, label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: SlideToAnswerCordTheme.Layout.controlLabelGap) {
                Circle()
                    .fill(selected ? SlideToAnswerCordTheme.Colors.controlOn : SlideToAnswerCordTheme.Colors.glass)
                    .overlay {
                        Circle().stroke(SlideToAnswerCordTheme.Colors.glassEdge, lineWidth: 0.5)
                    }
                    .overlay {
                        Image(systemName: symbol)
                            .font(.system(size: SlideToAnswerCordTheme.Layout.controlIconSize, weight: .regular))
                            .foregroundStyle(selected ? SlideToAnswerCordTheme.Colors.controlOnInk : SlideToAnswerCordTheme.Colors.glyph)
                    }
                    .frame(width: SlideToAnswerCordTheme.Layout.controlHeight, height: SlideToAnswerCordTheme.Layout.controlHeight)
                Text(label)
                    .font(.system(size: SlideToAnswerCordTheme.Layout.controlLabelSize, weight: .medium))
                    .foregroundStyle(SlideToAnswerCordTheme.Colors.mute)
                    .frame(height: 16)
            }
            .frame(width: SlideToAnswerCordTheme.Layout.controlMinimumWidth)
        }
        .buttonStyle(SlideToAnswerCordPressStyle())
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func callButton(color: Color, symbol: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: SlideToAnswerCordTheme.Layout.labelGap) {
                Circle()
                    .fill(color)
                    .frame(width: SlideToAnswerCordTheme.Layout.button, height: SlideToAnswerCordTheme.Layout.button)
                    .overlay {
                        Image(systemName: symbol)
                            .font(.system(size: SlideToAnswerCordTheme.Layout.glyph, weight: .regular))
                            .foregroundStyle(SlideToAnswerCordTheme.Colors.glyph)
                    }
                Text(label)
                    .font(.system(size: SlideToAnswerCordTheme.Layout.labelSize, weight: .medium))
                    .foregroundStyle(SlideToAnswerCordTheme.Colors.ink)
                    .frame(width: SlideToAnswerCordTheme.Layout.labelWidth, height: 16)
            }
        }
        .buttonStyle(SlideToAnswerCordPressStyle())
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct SlideToAnswerCordPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(reduceMotion ? 1 : (configuration.isPressed ? SlideToAnswerCordMotion.Press.scale : 1))
            .animation(
                .timingCurve(
                    SlideToAnswerCordMotion.Press.control1.x,
                    SlideToAnswerCordMotion.Press.control1.y,
                    SlideToAnswerCordMotion.Press.control2.x,
                    SlideToAnswerCordMotion.Press.control2.y,
                    duration: configuration.isPressed ? SlideToAnswerCordMotion.Press.inDuration : SlideToAnswerCordMotion.Press.outDuration
                ),
                value: configuration.isPressed
            )
    }
}
