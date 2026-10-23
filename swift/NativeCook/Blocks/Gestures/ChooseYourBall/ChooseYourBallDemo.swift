import SwiftUI

// Choose Your Ball: the 26-ball belt, one ball at a time on a 3D stage. Swipe sideways (or tap
// the strip) to change ball, drag up or down to look over and under it. The block takes the
// whole screen and handles the safe area itself. Mirrors index.tsx.
//
// Files: Catalog and Editorial (data), Geometry, Spring, Carousel, Tilt and Gestures (motion),
// Stage and StageContract (the RealityKit scene), Backdrop, Wordmark and MorphingText,
// ActionButton, Strip and Graphic (the screen's furniture), and Resources (the model, the 26
// shell paints, the occlusion ramp and the warehouse the ball reflects).

struct ChooseYourBallDemo: View {
    @State private var carousel: ChooseYourBallCarousel
    @State private var tilt: ChooseYourBallTilt
    @State private var router: ChooseYourBallTouchRouter
    /// True while a finger is down. Resets by itself when the system cancels the gesture.
    @GestureState private var touching = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric private var dynamicType: CGFloat = 1

    init() {
        let carousel = ChooseYourBallCarousel(initial: .dusk)
        let tilt = ChooseYourBallTilt()
        _carousel = State(initialValue: carousel)
        _tilt = State(initialValue: tilt)
        _router = State(initialValue: ChooseYourBallTouchRouter(carousel: carousel, tilt: tilt))
    }

    var body: some View {
        // Measured inside the safe area, drawn over all of the screen.
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            screen(Layout(size: size, insets: insets))
                .ignoresSafeArea()
        }
        .background(Color(chooseYourBallHex: ChooseYourBallPalette.stageInk).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func screen(_ layout: Layout) -> some View {
        let spec = carousel.previewBall.spec
        let name = ChooseYourBallEditorial.wordmark(spec.label)
        let typeScale = min(dynamicType, chooseYourBallMaxTypeScale)

        return ZStack(alignment: .topLeading) {
            ChooseYourBallBackdrop(carousel: carousel)

            header(typeScale: typeScale)
                .padding(.horizontal, 18)
                .padding(.top, max(layout.insets.top, 12) + 4)

            ChooseYourBallWordmark(
                hero: name.hero,
                tail: name.tail,
                size: layout.wordmarkSize,
                width: layout.size.width,
                typeScale: typeScale,
                reduceMotion: reduceMotion
            )
            .offset(y: layout.wordmarkTop)

            ChooseYourBallStage(carousel: carousel, tilt: tilt, ball: carousel.previewBall, reduceMotion: reduceMotion)
                .frame(width: layout.stageSize, height: layout.stageSize)
                .position(x: layout.size.width / 2, y: layout.ballCentreY)
                .accessibilityHidden(true)

            foot(spec: spec, typeScale: typeScale)
                .padding(.bottom, layout.footInset)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(touch(layout))
        .onChange(of: touching) { _, isDown in
            // Reached after `onEnded` too, where it finds nothing left to do.
            if !isDown { router.cancelled() }
        }
        .onChange(of: reduceMotion, initial: true) { _, reduced in
            router.tiltEnabled = !reduced
            carousel.reduceMotion = reduced
            tilt.reduceMotion = reduced
        }
    }

    // MARK: Furniture

    private func header(typeScale: CGFloat) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text("YOUR BELT")
                .font(.system(size: 9 * typeScale, weight: .black))
                .kerning(1.9)
                .foregroundStyle(Color(chooseYourBallHex: ChooseYourBallPalette.gold))
            Spacer(minLength: 0)
            Text(String(format: "%02d / %d", carousel.preview + 1, carousel.total))
                .font(.system(size: 10 * typeScale, weight: .heavy).monospacedDigit())
                .kerning(1.2)
                .foregroundStyle(.white.opacity(0.45))
        }
        .accessibilityElement(children: .combine)
    }

    private func foot(spec: ChooseYourBallSpec, typeScale: CGFloat) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 7) {
                ForEach(ChooseYourBallEditorial.facts(spec), id: \.self) { fact in
                    chip(fact, typeScale: typeScale)
                }
            }

            ChooseYourBallActionButton(
                icon: "→",
                label: "EQUIP \(spec.label.uppercased())",
                labelToken: carousel.preview,
                isPressed: router.isButtonPressed,
                typeScale: typeScale,
                action: equip
            )
            .padding(.horizontal, Layout.buttonMargin)
            .padding(.top, Layout.buttonGap)

            ChooseYourBallStrip(carousel: carousel)
                .frame(height: Layout.stripSlot, alignment: .top)
                .padding(.top, Layout.stripGap)
        }
    }

    private func chip(_ text: String, typeScale: CGFloat) -> some View {
        let hairline = 1 / displayScale
        return Text(text)
            .font(.system(size: 9 * typeScale, weight: .heavy))
            .kerning(0.9)
            .foregroundStyle(.white.opacity(0.9))
            .padding(.horizontal, 11 + hairline)
            .padding(.vertical, 5 + hairline)
            .background(.white.opacity(0.09), in: Capsule())
            .overlay(Capsule().strokeBorder(.white.opacity(0.16), lineWidth: hairline))
    }

    /// The reference's button does nothing either: what equipping means belongs to the host app.
    private func equip() {}

    // MARK: Touch

    /// One gesture for the whole screen, from the first contact, so the router sees what the
    /// reference's handlers see. A DragGesture follows one finger: a second one neither starts
    /// it nor moves it, which is the reference's one-pointer limit.
    private func touch(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($touching) { _, isDown, _ in isDown = true }
            .onChanged { drag in
                router.moved(to: drag.location, start: drag.startLocation) { point in
                    layout.target(at: point, offset: carousel.offset, total: carousel.total)
                }
            }
            .onEnded { drag in
                if router.ended(velocity: drag.velocity) { equip() }
            }
    }
}

private extension ChooseYourBallDemo {
    /// Where things stand for one screen size. The numbers are the reference's styles.
    struct Layout {
        static let buttonMargin: CGFloat = 18
        static let buttonGap: CGFloat = 14
        static let stripSlot: CGFloat = 42
        static let stripGap: CGFloat = 14

        let size: CGSize
        let insets: EdgeInsets

        var compact: Bool { size.height < ChooseYourBallGeometry.compactHeight }
        var stageSize: CGFloat { ChooseYourBallGeometry.stageSize(width: size.width, compact: compact) }
        var wordmarkSize: CGFloat { ChooseYourBallGeometry.wordmarkSize(width: size.width, compact: compact) }
        var ballCentreY: CGFloat { size.height * ChooseYourBallGeometry.ballCentreFraction }
        /// High enough that the ball stands in front of the second line and most of the first clears it.
        var wordmarkTop: CGFloat { ballCentreY - stageSize * 0.42 - wordmarkSize * 1.1 }
        var footInset: CGFloat { max(insets.bottom, 14) }

        private var stripTop: CGFloat { size.height - footInset - Self.stripSlot }

        private var buttonFrame: CGRect {
            let height = ChooseYourBallActionButton.height
            return CGRect(
                x: Self.buttonMargin,
                y: stripTop - Self.stripGap - height,
                width: size.width - Self.buttonMargin * 2,
                height: height
            )
        }

        /// What a touch at `point` lands on: the button, a thumbnail that is on show, or nothing.
        func target(at point: CGPoint, offset: Double, total: Int) -> ChooseYourBallTouchRouter.Target? {
            if buttonFrame.contains(point) { return .button }

            let metrics = ChooseYourBallStripMetrics.self
            let centreY = stripTop + metrics.selected / 2
            var best: (slot: Int, distance: CGFloat)?
            for index in 0..<total {
                let slot = ChooseYourBallGeometry.cyclicDelta(index: Double(index), offset: offset, total: total)
                guard metrics.opacity(distance: abs(slot)) >= 0.01 else { continue }
                let reach = (metrics.thumb / 2 + metrics.hitSlop) * metrics.scale(distance: abs(slot))
                let dx = abs(point.x - (size.width / 2 + slot * metrics.stride))
                let dy = abs(point.y - centreY)
                if dx <= reach, dy <= reach, dx < (best?.distance ?? .infinity) {
                    best = (index, dx)
                }
            }
            return best.map { .thumbnail(slot: $0.slot) }
        }
    }
}

#Preview {
    ChooseYourBallDemo()
}
