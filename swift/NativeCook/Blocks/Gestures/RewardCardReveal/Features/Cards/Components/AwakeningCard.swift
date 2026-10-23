import SwiftUI

private let designWidth: CGFloat = 320
private let cardTiltDegrees: CGFloat = -2.55
private let entryDuration: TimeInterval = 0.6
private let flipHoldDuration: TimeInterval = 0.1
private let flipDelay: TimeInterval = entryDuration + flipHoldDuration
private let flipDuration: TimeInterval = 1.3
private let settleDuration: TimeInterval = 0.65
private let planeMotionDuration: TimeInterval = flipDuration + settleDuration
private let swayDelay: TimeInterval = flipDelay + flipDuration
private let swayDuration: TimeInterval = 9
private let swayAngleDegrees: CGFloat = 3.3
private let swayPitchDegrees: CGFloat = 4.2
private let swayYawDegrees: CGFloat = 6.4
private let swayPerspective: CGFloat = 900
private let swayHorizontalRatio: CGFloat = 0.024
private let swayVerticalRatio: CGFloat = 0.032
private let exitSettleDuration: TimeInterval = 0.5
private let exitTranslateY: CGFloat = 2
private let exitScale: CGFloat = 0.985
private let cornerEmblemGlyphs: [String] = Array("mymind·mymind·mymind·").map { String($0) }

/// Values driven by the CSS keyframe animations in the RN source
/// (entry fade/scale, back & front flip rotations, plane bounce).
struct AwakeningIntroState {
    var opacity: Double = 0
    var entryScale: CGFloat = 1.4
    var backRotation: CGFloat = 0
    var frontRotation: CGFloat = -180
    var planeTranslateY: CGFloat = 0
    var planeScale: CGFloat = 1

    static let start = AwakeningIntroState()
    static let resting = AwakeningIntroState(
        opacity: 1,
        entryScale: 1,
        backRotation: 180,
        frontRotation: 0,
        planeTranslateY: 0,
        planeScale: 1
    )
}

// MARK: - Aurora panel

private struct AuroraField: View {
    let height: CGFloat
    let width: CGFloat

    var body: some View {
        let scale = width / 304
        let center = CGPoint(x: 158 * scale, y: 159 * scale)

        Canvas { context, _ in
            context.clip(to: panelPath(scale: scale))

            let fullRect = CGRect(x: 0, y: 0, width: width, height: height)
            context.fill(
                Path(fullRect),
                with: .linearGradient(
                    Gradient(css: ["#f2ad70", "#e5b8dd", "#bcdce0", "#edd9bd", "#ddc2ed"], positions: [0, 0.27, 0.48, 0.73, 1]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: width, y: height)
                )
            )

            var blurred = context
            blurred.addFilter(.blur(radius: 13 * scale))
            blurred.drawLayer { layer in
                func glow(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ colors: [String]) {
                    let c = CGPoint(x: cx * scale, y: cy * scale)
                    let radius = r * scale
                    layer.fill(
                        Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: radius * 2, height: radius * 2)),
                        with: .radialGradient(Gradient(css: colors), center: c, startRadius: 0, endRadius: radius)
                    )
                }
                glow(21, 35, 166, ["rgba(249,163,49,0.9)", "rgba(249,163,49,0)"])
                glow(161, -2, 165, ["rgba(216,45,193,0.88)", "rgba(216,45,193,0)"])
                glow(305, 20, 145, ["rgba(151,211,219,0.9)", "rgba(151,211,219,0)"])
                glow(286, 263, 116, ["rgba(250,222,105,0.82)", "rgba(250,222,105,0)"])

                let sweepRadius = 177 * scale
                let sweepRect = CGRect(x: center.x - sweepRadius, y: center.y - sweepRadius, width: sweepRadius * 2, height: sweepRadius * 2)
                layer.drawGroup(opacity: 0.84) { inner in
                    inner.fill(
                        Path(ellipseIn: sweepRect),
                        with: .conicGradient(
                            Gradient(css: ["#fa7654", "#f4d95d", "#ff805c", "#dc77c7", "#f13570", "#ff1759", "#d931bd", "#9dcede", "#fa7654"]),
                            center: center
                        )
                    )
                }
                layer.fill(
                    Path(ellipseIn: sweepRect),
                    with: .radialGradient(
                        Gradient(
                            css: [
                                "rgba(255, 30, 63, 0)",
                                "rgba(255, 16, 58, 0.68)",
                                "rgba(255, 73, 43, 0.48)",
                                "rgba(255, 120, 65, 0)",
                            ],
                            positions: [0, 0.6, 0.79, 1]
                        ),
                        center: center,
                        startRadius: 0,
                        endRadius: sweepRadius
                    )
                )
            }

            context.fill(
                Path(fullRect),
                with: .linearGradient(
                    Gradient(
                        css: [
                            "rgba(255,255,255,0)",
                            "rgba(255,247,226,0.12)",
                            "rgba(250,232,222,0.58)",
                            "rgba(236,213,244,0.78)",
                        ],
                        positions: [0, 0.53, 0.77, 1]
                    ),
                    startPoint: CGPoint(x: width * 0.42, y: height * 0.25),
                    endPoint: CGPoint(x: width * 0.58, y: height)
                )
            )
        }
        .allowsHitTesting(false)
    }

    private func panelPath(scale: CGFloat) -> Path {
        var path = Path()
        let w = width
        path.move(to: CGPoint(x: 53 * scale, y: 0))
        path.addLine(to: CGPoint(x: w - 8 * scale, y: 0))
        path.addQuadCurve(to: CGPoint(x: w, y: 8 * scale), control: CGPoint(x: w, y: 0))
        path.addLine(to: CGPoint(x: w, y: 377 * scale))
        path.addCurve(
            to: CGPoint(x: w - 9 * scale, y: 386 * scale),
            control1: CGPoint(x: w, y: 382 * scale),
            control2: CGPoint(x: w - 3 * scale, y: 385 * scale)
        )
        path.addCurve(
            to: CGPoint(x: 9 * scale, y: 386 * scale),
            control1: CGPoint(x: w - 64 * scale, y: 398 * scale),
            control2: CGPoint(x: 64 * scale, y: 398 * scale)
        )
        path.addCurve(
            to: CGPoint(x: 0, y: 377 * scale),
            control1: CGPoint(x: 3 * scale, y: 385 * scale),
            control2: CGPoint(x: 0, y: 382 * scale)
        )
        path.addLine(to: CGPoint(x: 0, y: 58 * scale))
        path.addCurve(
            to: CGPoint(x: 10 * scale, y: 48 * scale),
            control1: CGPoint(x: 0, y: 52 * scale),
            control2: CGPoint(x: 4 * scale, y: 48 * scale)
        )
        path.addLine(to: CGPoint(x: 15 * scale, y: 48 * scale))
        path.addCurve(
            to: CGPoint(x: 43 * scale, y: 19 * scale),
            control1: CGPoint(x: 31 * scale, y: 48 * scale),
            control2: CGPoint(x: 43 * scale, y: 35 * scale)
        )
        path.addLine(to: CGPoint(x: 43 * scale, y: 15 * scale))
        path.addCurve(
            to: CGPoint(x: 53 * scale, y: 0),
            control1: CGPoint(x: 43 * scale, y: 7 * scale),
            control2: CGPoint(x: 45 * scale, y: 0)
        )
        path.closeSubpath()
        return path
    }
}

// MARK: - Small ornaments

private struct CornerEmblem: View {
    let scale: CGFloat

    var body: some View {
        let size = 48 * scale
        let center = size / 2
        let orbitRadius = 17.5 * scale
        let glyphBoxSize = 6 * scale

        ZStack(alignment: .topLeading) {
            ForEach(Array(cornerEmblemGlyphs.enumerated()), id: \.offset) { index, glyph in
                let angle = -112 + (Double(index) * 360) / Double(cornerEmblemGlyphs.count)
                let radians = angle * .pi / 180
                Text(glyph)
                    .font(.system(size: (glyph == "·" ? 3.5 : 4.4) * scale, weight: .bold))
                    .foregroundStyle(Color(css: "#aab3b8"))
                    .frame(width: glyphBoxSize, height: glyphBoxSize)
                    .rotationEffect(.degrees(angle + 90))
                    .position(
                        x: center + CGFloat(cos(radians)) * orbitRadius,
                        y: center + CGFloat(sin(radians)) * orbitRadius
                    )
            }

            ZStack(alignment: .topLeading) {
                Circle()
                    .fill(Color(css: "#acb6ba"))
                    .frame(width: 18 * scale, height: 18 * scale)
                    .offset(x: 1 * scale, y: 1 * scale)
                Circle()
                    .fill(Color(css: "#f9fbfb"))
                    .frame(width: 17 * scale, height: 17 * scale)
                    .offset(x: 7 * scale, y: -1 * scale)
                RoundedRectangle(cornerRadius: 4 * scale)
                    .fill(Color(css: "#d8dde0"))
                    .frame(width: 7 * scale, height: 12 * scale)
                    .rotationEffect(.degrees(10))
                    .offset(x: 15 * scale, y: 3 * scale)
            }
            .frame(width: 22 * scale, height: 20 * scale)
            .position(x: center, y: center)
        }
        .frame(width: size, height: size)
    }
}

private struct AchievementMedal: View {
    let scale: CGFloat

    var body: some View {
        let size = 38 * scale
        ZStack {
            Image("ball")
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .scaleEffect(1.34)
                .accessibilityHidden(true)
            Circle()
                .strokeBorder(Color(css: "rgba(255,255,255,0.96)"), lineWidth: 2.5 * scale)
        }
        .frame(width: size, height: size)
    }
}

private struct TinySeal: View {
    let scale: CGFloat

    var body: some View {
        let size = 15 * scale
        Text("✿")
            .font(.system(size: 7.2 * scale, weight: .black))
            .foregroundStyle(Color(css: "#f5f3ff"))
            .frame(width: size, height: size)
            .background(Circle().fill(Color(css: "#9188cf")))
            .overlay(Circle().strokeBorder(Color(css: "#d9d9ee"), lineWidth: 1.5 * scale))
    }
}

// MARK: - Front face

struct AwakeningCardFace: View {
    let width: CGFloat

    var body: some View {
        let s = width / designWidth
        let height = width * cardHeightRatio
        let panelHeight = 400 * s
        let panelWidth = width - 16 * s
        let portraitSize = 220 * s
        let skew = CGFloat(tan(-7 * Double.pi / 180))

        ZStack(alignment: .topLeading) {
            Color(css: "rgba(247, 249, 251, 0.96)")
                .frame(width: width - 16 * s, height: max(height - 394 * s - 7 * s, 0))
                .offset(x: 8 * s, y: 394 * s)

            AuroraField(height: panelHeight, width: panelWidth)
                .frame(width: panelWidth, height: panelHeight)
                .clipShape(RoundedRectangle(cornerRadius: 8 * s))
                .offset(x: 8 * s, y: 8 * s)

            Circle()
                .fill(Color(css: "#fffefd"))
                .frame(width: portraitSize, height: portraitSize)
                .shadow(color: Color(css: "rgba(220, 32, 77, 0.28)"), radius: 8.5 * s, x: 0, y: 7 * s)
                .offset(x: 56 * s, y: 58 * s)

            CornerEmblem(scale: s)
                .offset(x: 4 * s, y: 5 * s)

            Text("001 / 151")
                .font(.system(size: 3.4 * s, weight: .bold))
                .tracking(0.65 * s)
                .foregroundStyle(Color(css: "rgba(108, 111, 116, 0.48)"))
                .fixedSize()
                .rotationEffect(.degrees(90))
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.trailing, 4 * s)
                .offset(y: 226 * s)

            Text("The Awakening")
                .font(.custom("Georgia-Bold", fixedSize: 22.5 * s))
                .foregroundStyle(Color(css: "#fffdf5"))
                .lineLimit(1)
                .multilineTextAlignment(.center)
                .shadow(color: Color(css: "rgba(137, 84, 96, 0.17)"), radius: 1 * s, x: 0, y: 1 * s)
                .frame(width: width - 30 * s, height: 28 * s)
                .offset(x: 18 * s, y: 309 * s)

            Text("You’ve taken the first step in your journey.\nA fresh, new mind awaits you.")
                .font(.system(size: 10.5 * s, weight: .semibold))
                .italic()
                .lineSpacing(2.5 * s)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color(css: "rgba(255, 255, 250, 0.97)"))
                .shadow(color: Color(css: "rgba(142, 91, 102, 0.14)"), radius: 0.75 * s, x: 0, y: 1 * s)
                .frame(width: width - 42 * s)
                .offset(x: 24 * s, y: 347 * s)

            Text("E")
                .font(.system(size: 13 * s, weight: .black))
                .italic()
                .foregroundStyle(Color(css: "#f7f9ff"))
                .frame(width: 22 * s, height: 18 * s)
                .background(RoundedRectangle(cornerRadius: 4 * s).fill(Color(css: "#8790c9")))
                .transformEffect(CGAffineTransform(a: 1, b: 0, c: skew, d: 1, tx: -skew * 9 * s, ty: 0))
                .offset(x: 15 * s, y: 405 * s)

            Text("Epic Milestone")
                .font(.system(size: 7.7 * s, weight: .semibold))
                .foregroundStyle(Color(css: "#747d91"))
                .offset(x: 43 * s, y: 409 * s)

            HStack(spacing: 6 * s) {
                Text("+100 Tranquility")
                    .font(.system(size: 7.7 * s, weight: .semibold))
                    .foregroundStyle(Color(css: "#747d91"))
                TinySeal(scale: s)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.trailing, 12 * s)
            .offset(y: 407 * s)

            AchievementMedal(scale: s)
                .offset(x: (designWidth / 2 - 19) * s, y: 383 * s)

            Text("Art by mymind.com")
                .font(.system(size: 6.2 * s))
                .foregroundStyle(Color(css: "#a0a7b1"))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 14 * s)
                .padding(.bottom, 6 * s)

            Text("✦✦✦✦")
                .font(.system(size: 8 * s, weight: .black))
                .tracking(5 * s)
                .foregroundStyle(Color(css: "#485164"))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 5.5 * s)

            Text("© mymind Inc.")
                .font(.system(size: 6.2 * s))
                .foregroundStyle(Color(css: "#a9afb7"))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, 12 * s)
                .padding(.bottom, 6 * s)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .background(Color(css: "#f8fafb"))
        .clipShape(RoundedRectangle(cornerRadius: 12 * s, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12 * s, style: .continuous)
                .strokeBorder(Color(css: "rgba(255, 255, 255, 0.92)"), lineWidth: 1)
        )
        .shadow(color: Color(css: "rgba(80, 92, 108, 0.2)"), radius: 14 * s, x: 0, y: 12 * s)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - Animated card

struct AwakeningCard: View {
    var exiting: Bool = false
    var onExitReady: (() -> Void)? = nil
    let width: CGFloat

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var introTrigger = false
    @State private var mountDate = Date()
    @State private var isExiting = false
    @State private var exitSway: CGFloat = 0.5
    @State private var exitProgress: CGFloat = 0

    var body: some View {
        let height = width * cardHeightRatio

        Group {
            if reduceMotion {
                cardContent(.resting, height: height)
            } else {
                KeyframeAnimator(initialValue: AwakeningIntroState.start, trigger: introTrigger) { state in
                    cardContent(state, height: height)
                } keyframes: { _ in
                    KeyframeTrack(\.opacity) {
                        LinearKeyframe(0.24, duration: 0.20 * entryDuration)
                        LinearKeyframe(0.58, duration: 0.28 * entryDuration)
                        LinearKeyframe(0.75, duration: 0.24 * entryDuration)
                        LinearKeyframe(0.88, duration: 0.14 * entryDuration)
                        LinearKeyframe(1.0, duration: 0.14 * entryDuration)
                    }
                    KeyframeTrack(\.entryScale) {
                        LinearKeyframe(1.31, duration: 0.20 * entryDuration)
                        LinearKeyframe(1.16, duration: 0.28 * entryDuration)
                        LinearKeyframe(1.07, duration: 0.24 * entryDuration)
                        LinearKeyframe(1.03, duration: 0.14 * entryDuration)
                        LinearKeyframe(1.0, duration: 0.14 * entryDuration)
                    }
                    KeyframeTrack(\.backRotation) {
                        LinearKeyframe(0, duration: flipDelay)
                        LinearKeyframe(60, duration: 0.33 * flipDuration)
                        LinearKeyframe(88, duration: 0.11 * flipDuration)
                        LinearKeyframe(114, duration: 0.06 * flipDuration)
                        LinearKeyframe(138, duration: 0.10 * flipDuration)
                        LinearKeyframe(150, duration: 0.07 * flipDuration)
                        LinearKeyframe(174, duration: 0.15 * flipDuration)
                        LinearKeyframe(180, duration: 0.18 * flipDuration)
                    }
                    KeyframeTrack(\.frontRotation) {
                        LinearKeyframe(-180, duration: flipDelay)
                        LinearKeyframe(-120, duration: 0.33 * flipDuration)
                        LinearKeyframe(-92, duration: 0.11 * flipDuration)
                        LinearKeyframe(-66, duration: 0.06 * flipDuration)
                        LinearKeyframe(-42, duration: 0.10 * flipDuration)
                        LinearKeyframe(-30, duration: 0.07 * flipDuration)
                        LinearKeyframe(-6, duration: 0.15 * flipDuration)
                        LinearKeyframe(0, duration: 0.18 * flipDuration)
                    }
                    KeyframeTrack(\.planeTranslateY) {
                        LinearKeyframe(0, duration: flipDelay)
                        LinearKeyframe(0, duration: 0.55 * planeMotionDuration)
                        LinearKeyframe(5, duration: 0.17 * planeMotionDuration)
                        LinearKeyframe(0, duration: 0.28 * planeMotionDuration)
                    }
                    KeyframeTrack(\.planeScale) {
                        LinearKeyframe(1, duration: flipDelay)
                        LinearKeyframe(1, duration: 0.55 * planeMotionDuration)
                        LinearKeyframe(1.03, duration: 0.17 * planeMotionDuration)
                        LinearKeyframe(1, duration: 0.28 * planeMotionDuration)
                    }
                }
            }
        }
        .frame(width: width, height: height)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("The Awakening, Epic Milestone, cộng 100 Tranquility")
        .accessibilityAddTraits(.isImage)
        .onAppear {
            mountDate = Date()
            introTrigger.toggle()
        }
        .onChange(of: exiting) { _, newValue in
            handleExiting(newValue)
        }
    }

    @ViewBuilder
    private func cardContent(_ state: AwakeningIntroState, height: CGFloat) -> some View {
        TimelineView(.animation(paused: reduceMotion || isExiting)) { timeline in
            let sway = isExiting ? exitSway : liveSway(at: timeline.date)
            let signed = sway * 2 - 1

            ZStack {
                CollectionCardBack(collectorMarkFinish: .holographic, width: width)
                    .frame(width: width, height: height, alignment: .top)
                    .clipShape(RoundedRectangle(cornerRadius: width * 0.045, style: .continuous))
                    .opacity(state.backRotation <= 90 ? 1 : 0)
                    .modifier(CardPerspectiveEffect(rotateY: state.backRotation, perspective: 900))

                AwakeningCardFace(width: width)
                    .opacity(state.frontRotation >= -90 ? 1 : 0)
                    .modifier(CardPerspectiveEffect(rotateY: state.frontRotation, perspective: 900))
            }
            .frame(width: width, height: height)
            .scaleEffect(state.planeScale)
            .offset(y: state.planeTranslateY)
            .modifier(
                CardPerspectiveEffect(
                    translateX: signed * width * swayHorizontalRatio,
                    translateY: signed * signed * width * swayVerticalRatio + exitProgress * exitTranslateY,
                    rotateX: signed * swayPitchDegrees,
                    rotateY: -signed * swayYawDegrees,
                    rotateZ: signed * swayAngleDegrees - cardTiltDegrees,
                    scale: 1 - exitProgress * (1 - exitScale),
                    perspective: swayPerspective
                )
            )
        }
        .frame(width: width, height: height)
        .scaleEffect(state.entryScale)
        .rotationEffect(.degrees(Double(cardTiltDegrees)))
        .opacity(state.opacity)
    }

    /// Mirrors `withDelay(SWAY_DELAY, withRepeat(withTiming(1, { duration, easing: inOut(quad) }), -1, true))`.
    private func liveSway(at date: Date) -> CGFloat {
        if reduceMotion { return 0.5 }
        let elapsed = date.timeIntervalSince(mountDate) - swayDelay
        if elapsed <= 0 { return 0 }
        let cycles = elapsed / swayDuration
        let whole = floor(cycles)
        let fraction = cycles - whole
        let t = Int(whole) % 2 == 0 ? fraction : 1 - fraction
        return CGFloat(easeInOutQuad(t))
    }

    private func easeInOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }

    private func handleExiting(_ exiting: Bool) {
        guard exiting else {
            isExiting = false
            exitProgress = 0
            return
        }

        if reduceMotion {
            isExiting = true
            exitSway = 0.5
            exitProgress = 1
            onExitReady?()
            return
        }

        // Freeze the sway at its live value first so the settle animation starts from
        // where the card currently is (Reanimated's withTiming does the same implicitly).
        var freeze = Transaction()
        freeze.disablesAnimations = true
        withTransaction(freeze) {
            exitSway = liveSway(at: Date())
            isExiting = true
        }

        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: exitSettleDuration)) {
            exitSway = 0.5
            exitProgress = 1
        } completion: {
            onExitReady?()
        }
    }
}

#Preview("Awakening card") {
    AwakeningCard(width: 316)
        .padding(40)
        .background(CollectionCardBackdropPalette.background)
}

#Preview("Awakening face") {
    AwakeningCardFace(width: 320)
        .padding()
}
