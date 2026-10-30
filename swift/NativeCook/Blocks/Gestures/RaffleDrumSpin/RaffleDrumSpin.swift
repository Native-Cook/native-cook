import SwiftUI
import UIKit

/// A ten-second raffle story: choose two entries, spin the drum, reveal the winner, and claim it.
struct RaffleDrumSpin: View {
    static let loop = 10.0

    var autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var interaction = RaffleDrumSpinInteraction()
    @State private var crankActive = false
    @State private var stripActive = false
    @State private var ticketActive = false
    @State private var resourcesReady = false
    @State private var resourcesReadyDate: Date?

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        RaffleDrumSpinAssets.registerFonts()
        _ = RaffleDrumSpinMotion.baked
        _ = RaffleDrumSpinAssets.image("golden-ticket")
        _ = RaffleDrumSpinAssets.image("golden-ticket-back")
#if DEBUG
        _ = RaffleDrumSpinRenderTrace.shared
        RaffleDrumSpinDebug.selfTestIfRequested()
#endif
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            stage(size: size)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(red: 0.025, green: 0.013, blue: 0.052).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { _, phase in interaction.setSceneActive(phase == .active) }
    }

    private func stage(size: CGSize) -> some View {
        let pinned = debugTime
        let paused = !resourcesReady || (reduceMotion && !interaction.reducedTransitionActive) || scenePhase != .active || pinned != nil || (!autoPlay && !interaction.isInteracting && !interaction.reducedTransitionActive)
        let ticket = RaffleDrumSpinAssets.image("golden-ticket")
        RaffleDrumSpinText.prewarmTicket(scaleX:size.width/402, displayScale:displayScale)

        return TimelineView(.animation(minimumInterval: 1 / 60, paused: paused)) { timeline in
            let snapshot = pinned.map(interaction.pinned) ?? (!(resourcesReady && timeline.date >= (resourcesReadyDate ?? .distantFuture)) ? interaction.pinned(0) : interaction.snapshot(
                at: timeline.date.timeIntervalSinceReferenceDate,
                autoPlay: autoPlay,
                reducedMotion: reduceMotion
            ))
            let sx = size.width / RaffleDrumSpinMotion.referenceWidth
            let sy = size.height / RaffleDrumSpinMotion.referenceHeight
            let state = snapshot.current
            let frame = RaffleDrumSpinMotion.frameAt(
                state.time, sx: sx, sy: sy, story: state.story,
                manualSpin: state.manualSpin, manualSpeed: state.manualSpeed, idle: state.idle
            )

            ZStack(alignment: .topLeading) {
                if let previous = snapshot.previous, snapshot.fade < 1 {
                    let oldFrame = RaffleDrumSpinMotion.frameAt(previous.time,sx:sx,sy:sy,story:previous.story,manualSpin:previous.manualSpin,manualSpeed:previous.manualSpeed,idle:previous.idle)
                    ZStack(alignment:.topLeading) {
                        underScene(size:size,frame:oldFrame)
                        underScene(size:size,frame:frame).opacity(snapshot.fade)
                        RaffleDrumSpinBackdropBlur(intensity:0.26)
                            .frame(width:size.width,height:size.height)
                            .opacity(oldFrame.focus*(1-snapshot.fade)+frame.focus*snapshot.fade)
                        upperScene(size:size,frame:oldFrame,ticket:ticket).opacity(1-snapshot.fade)
                        upperScene(size:size,frame:frame,ticket:ticket).opacity(snapshot.fade)
                    }
                } else {
                    scene(size:size,frame:frame,ticket:ticket)
                }

                controls(size: size, frame: frame)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .coordinateSpace(name: "raffleDrumSpin")
            .simultaneousGesture(stripGesture(size: size))
        }
        .task(id: size) {
            await RaffleDrumSpinShaderWarmup.prepare(size:size,ticket:ticket)
            if !resourcesReady { resourcesReadyDate = Date(); resourcesReady = true }
        }
    }

    private func underScene(size: CGSize, frame: RaffleDrumSpinMotion.Frame) -> some View {
            ZStack(alignment: .topLeading) {
                RaffleDrumSpinBase(size: size).equatable()
                    .colorEffect(baseGlow(size:size,glow:frame.glow))
                Canvas(rendersAsynchronously:false) { context, _ in
                    RaffleDrumSpinDraw.page(context: &context, size: size, frame: frame, fallbackDrum: reduceMotion)
                }
                Canvas(rendersAsynchronously:false) { context, _ in
                    RaffleDrumSpinDraw.cards(context: &context, size: size, frame: frame)
                }
                // RuntimeShader image filters rasterize their child in local point space.
                .environment(\.displayScale, frame.lens[4] > 0.01 ? 1 : displayScale)
                .layerEffect(
                    ShaderLibrary.raffleDrumSpinGlass(
                        .float4(
                            Float(frame.lens[0] * size.width / 402),
                            Float(frame.lens[1] * size.height / 874),
                            Float(frame.lens[2] * size.width / 402),
                            Float(frame.lens[3] * size.height / 874)
                        ),
                        .float(Float(18 * size.width / 402)),
                        .float(Float(frame.lens[4]))
                    ),
                    maxSampleOffset: CGSize(width: 10 * size.width / 402, height: 10 * size.width / 402),
                    isEnabled: frame.lens[4] > 0.01
                )
                Canvas(rendersAsynchronously:false) { context, _ in
                    RaffleDrumSpinDraw.pageOverlay(context: &context, size: size, frame: frame)
                }
                if !reduceMotion {
                    RaffleDrumSpinGPU(layer: .drum, frame: frame)
                        .frame(width: size.width, height: size.height)
                }
            }
    }

    private func baseGlow(size: CGSize, glow: Double) -> Shader {
        let x = Float(205 * size.width / 402)
        let y = Float(148 * size.height / 874)
        let ink = Color(red:51.0/255,green:31.0/255,blue:107.0/255)
        return ShaderLibrary.raffleDrumSpinBaseGlow(
            .float2(x,y),.float(Float(size.width * 0.62)),.float(Float(glow)),.color(ink)
        )
    }

    private func upperScene(size: CGSize, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) -> some View {
        ZStack(alignment:.topLeading) {
            Canvas(rendersAsynchronously:false) { context, _ in
                RaffleDrumSpinDraw.top(context: &context, size: size, frame: frame, ticket: ticket)
                RaffleDrumSpinDraw.ticketGlow(context: &context, size: size, frame: frame)
            }
            Canvas(rendersAsynchronously:false) { context, _ in
                RaffleDrumSpinDraw.ticket(context: &context, size: size, frame: frame, ticket: ticket)
            }
            .projectionEffect(ticketProjection(frame: frame, size: size))
            Canvas(rendersAsynchronously:false) { context, _ in
                RaffleDrumSpinDraw.ticketExtras(context: &context, size: size, frame: frame, ticket: ticket)
            }
        }
    }

    private func scene(size: CGSize, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) -> some View {
        ZStack(alignment:.topLeading) {
            underScene(size:size,frame:frame)
            RaffleDrumSpinBackdropBlur(intensity:0.26)
                .frame(width:size.width,height:size.height)
                .opacity(frame.focus)
            if !reduceMotion {
                RaffleDrumSpinGPU(layer:.foil,frame:frame).frame(width:size.width,height:size.height)
            }
            upperScene(size:size,frame:frame,ticket:ticket)
        }
        .allowsHitTesting(false)
    }

    private func ticketBob(_ frame: RaffleDrumSpinMotion.Frame) -> Double {
        let held = frame.t - RaffleDrumSpinMotion.T.morph.upperBound
        return held > 0 ? sin(held * 2.1) * 3 * (1 - frame.burn) : 0
    }

    private func ticketProjection(frame: RaffleDrumSpinMotion.Frame, size: CGSize) -> ProjectionTransform {
        let sx = size.width / RaffleDrumSpinMotion.referenceWidth
        let sy = size.height / RaffleDrumSpinMotion.referenceHeight
        let distance = 700 * sx
        let anchorX = 201 * sx
        let anchorY = (432 + ticketBob(frame)) * sy
        var perspective = CATransform3DIdentity
        perspective.m11 = cos(frame.tilt[0])
        perspective.m22 = cos(frame.tilt[1])
        perspective.m14 = sin(frame.tilt[0]) / distance
        perspective.m24 = -sin(frame.tilt[1]) / distance
        var transform = CATransform3DMakeTranslation(anchorX, anchorY, 0)
        transform = CATransform3DConcat(perspective, transform)
        transform = CATransform3DTranslate(transform, -anchorX, -anchorY, 0)
        return ProjectionTransform(transform)
    }

    @ViewBuilder
    private func controls(size: CGSize, frame: RaffleDrumSpinMotion.Frame) -> some View {
        let sx = size.width / RaffleDrumSpinMotion.referenceWidth
        let sy = size.height / RaffleDrumSpinMotion.referenceHeight

        Color.clear
            .contentShape(Rectangle())
            .frame(width: 250 * sx, height: 220 * sy)
            .offset(x: 100 * sx, y: 90 * sy)
            .gesture(
                DragGesture(minimumDistance: 2, coordinateSpace: .named("raffleDrumSpin"))
                    .onChanged { value in
                        guard !reduceMotion else { return }
                        if !crankActive {
                            crankActive = true
                            interaction.crankBegan(y: value.startLocation.y)
                        }
                        interaction.crankChanged(y: value.location.y, sx: sx)
                    }
                    .onEnded { value in
                        guard !reduceMotion else { return }
                        let velocity = value.velocity.height
                        interaction.crankEnded(velocityY: velocity, sx: sx)
                        crankActive = false
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Raffle drum")
            .accessibilityHint("Drag up or down to turn the crank.")
            .accessibilityAddTraits(.allowsDirectInteraction)

        ForEach(0..<3, id: \.self) { card in
            let x = RaffleDrumSpinMotion.Layout.cardX
                + Double(card) * (RaffleDrumSpinMotion.Layout.cardWidth + RaffleDrumSpinMotion.Layout.cardGap)
                + frame.strip
            Color.clear
            .contentShape(RoundedRectangle(cornerRadius: 18 * sx))
            .frame(width: RaffleDrumSpinMotion.Layout.cardWidth * sx, height: RaffleDrumSpinMotion.Layout.cardHeight * sy)
            .offset(x: x * sx, y: RaffleDrumSpinMotion.Layout.cardY * sy)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in interaction.cardPressBegan(card, reducedMotion: reduceMotion) }
                    .onEnded { value in
                        if abs(value.translation.width) < 8 && abs(value.translation.height) < 8 {
                            interaction.cardPressEnded(card, reducedMotion: reduceMotion)
                        } else {
                            interaction.cancelPress()
                        }
                    }
            )
            .accessibilityLabel(RaffleDrumSpinDraw.cardCopy[card].joined(separator: " "))
            .accessibilityValue(card == frame.to ? "Selected" : "Not selected")
            .accessibilityAddTraits(card == frame.to ? .isSelected : [])
        }

        Color.clear
            .contentShape(Rectangle())
            .frame(width: RaffleDrumSpinMotion.Layout.ticket.width * sx, height: RaffleDrumSpinMotion.Layout.ticket.height * sy)
            .offset(x: RaffleDrumSpinMotion.Layout.ticket.minX * sx, y: RaffleDrumSpinMotion.Layout.ticket.minY * sy)
            .gesture(
                DragGesture(minimumDistance: 1, coordinateSpace: .named("raffleDrumSpin"))
                    .onChanged { value in
                        guard !reduceMotion else { return }
                        if !ticketActive { ticketActive = interaction.ticketBegan() }
                        interaction.ticketChanged(dx: value.translation.width / sx, dy: value.translation.height / sy)
                    }
                    .onEnded { value in
                        guard !reduceMotion else { return }
                        let vx = value.velocity.width / sx
                        let vy = value.velocity.height / sy
                        interaction.ticketEnded(vx: vx, vy: vy)
                        ticketActive = false
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Golden ticket")
            .accessibilityHint("Drag to tilt it in the light.")
            .accessibilityAddTraits(.allowsDirectInteraction)

        Color.clear
            .contentShape(Capsule())
            .frame(width: RaffleDrumSpinMotion.Layout.play.width * sx, height: RaffleDrumSpinMotion.Layout.play.height * sy)
            .offset(x: RaffleDrumSpinMotion.Layout.play.minX * sx, y: RaffleDrumSpinMotion.Layout.play.minY * sy)
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in interaction.playPressBegan(x: value.location.x / sx, reducedMotion: reduceMotion) }
                    .onEnded { value in
                        interaction.playPressEnded(x: value.location.x / sx, reducedMotion: reduceMotion)
                    }
            )
            .accessibilityElement()
            .accessibilityLabel(frame.claim > 0.5 ? "Claim ticket" : "Play")
            .accessibilityHint(frame.claim > 0.5 ? "Tears off the stub and enters it with your card." : "Spins the drum and draws a winning ball.")
            .accessibilityAddTraits(.isButton)

        Button { UIImpactFeedbackGenerator(style: .light).impactOccurred() } label: {
            Color.clear.contentShape(Circle())
        }
        .buttonStyle(.plain)
        .frame(width: 48 * sx, height: 48 * sx)
        .offset(x: (RaffleDrumSpinMotion.Layout.close.x - 24) * sx, y: (RaffleDrumSpinMotion.Layout.close.y - 24) * sy)
        .accessibilityLabel("Close")
    }

    private func stripGesture(size: CGSize) -> some Gesture {
        let sx = size.width / RaffleDrumSpinMotion.referenceWidth
        let sy = size.height / RaffleDrumSpinMotion.referenceHeight
        return DragGesture(minimumDistance: 8, coordinateSpace: .named("raffleDrumSpin"))
            .onChanged { value in
                guard !reduceMotion,
                      value.startLocation.y >= RaffleDrumSpinMotion.Layout.cardY * sy,
                      value.startLocation.y <= (RaffleDrumSpinMotion.Layout.cardY + RaffleDrumSpinMotion.Layout.cardHeight) * sy
                else { return }
                if !stripActive { stripActive = true; interaction.stripBegan() }
                interaction.stripChanged(translation: value.translation.width / sx)
            }
            .onEnded { value in
                guard stripActive else { return }
                let velocity = value.velocity.width / sx
                interaction.stripEnded(velocity: velocity)
                stripActive = false
            }
    }

    private var debugTime: Double? {
#if DEBUG
        guard UserDefaults.standard.object(forKey: "raffleT") != nil else { return nil }
        return min(max(UserDefaults.standard.double(forKey: "raffleT"), 0), Self.loop)
#else
        return nil
#endif
    }
}

/// Matches Expo Blur's iOS dark backdrop at intensity 26 without blurring the
/// composited layer itself. Keeping the animator paused makes the effect stable
/// for deterministic frame capture.
private struct RaffleDrumSpinBackdropBlur: UIViewRepresentable {
    let intensity: CGFloat

    func makeUIView(context: Context) -> RaffleDrumSpinBackdropBlurView {
        RaffleDrumSpinBackdropBlurView(intensity: intensity)
    }

    func updateUIView(_ uiView: RaffleDrumSpinBackdropBlurView, context: Context) {
        uiView.setIntensity(intensity)
    }
}

private final class RaffleDrumSpinBackdropBlurView: UIVisualEffectView {
    private let blur = UIBlurEffect(style: .dark)
    private var animator: UIViewPropertyAnimator?
    private var appliedIntensity: CGFloat?

    init(intensity: CGFloat) {
        super.init(effect: nil)
        backgroundColor = .clear
        setIntensity(intensity)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setIntensity(_ value: CGFloat) {
        guard appliedIntensity != value else { return }
        appliedIntensity = value
        animator?.stopAnimation(true)
        effect = nil
        let next = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
            self?.effect = self?.blur
        }
        next.fractionComplete = min(max(value, 0), 1)
        next.pauseAnimation()
        animator = next
    }
}
