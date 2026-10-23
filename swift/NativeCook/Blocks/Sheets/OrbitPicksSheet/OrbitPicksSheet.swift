import SwiftUI

/// Calls `content` with the value as it animates; the closure's own mapping (ramps, lerps) runs
/// on every frame, which SwiftUI's modifiers would otherwise interpolate linearly end to end.
private struct OrbitPicksSheetReader<Value: VectorArithmetic, Content: View>: View, Animatable {
    var value: Value
    @ViewBuilder let content: (Value) -> Content

    var animatableData: Value {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// A discover page with a full-height sheet over it. Tapping the banner raises the sheet while the
/// page recedes behind it; a badge pops in, a ring draws round it and a dozen icon discs spiral in
/// and orbit it, until they are pulled into the badge, which rises and shrinks to become the
/// heading of a card of people to swipe through. One clock, in seconds since the tap, drives all
/// of it, so the loader plays the same way every time.
struct OrbitPicksSheet: View {
    /// The people the sheet goes through; swipe the card sideways for the next one.
    var profiles = M.profiles
    /// Plays the whole interaction by itself, over and over, and gives way to a touch.
    var autoPlay = false
    var title = "Discover"
    var banner = "Today's Top Picks"
    var bannerCaption = "Your personalized daily matches are ready to see!"
    var loadingLabel = "Doing some magic..."
    var resultTitle = "Today's Top Picks"
    var resultCaption = "Three people picked for you today"
    var chatLabel = "Chat"
    var onChat: ((OrbitPicksSheetProfile) -> Void)? = nil
    /// Called with the place of the profile on show whenever a swipe settles.
    var onProfileChange: ((Int) -> Void)? = nil

    private typealias M = OrbitPicksSheetMotion
    private typealias C = OrbitPicksSheetClock

    private enum Axis { case horizontal, vertical }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // 0 hidden, 1 up; the clock in seconds since the tap; the card on show, as a place in the list.
    @State private var sheet = 0.0
    @State private var drag = 0.0
    @State private var clock = 0.0
    @State private var page = 0.0
    @State private var pageFrom = 0.0
    @State private var finger = 0.0
    @State private var bannerScale = 1.0
    @State private var isOpen = false
    @State private var touches = 0
    @State private var axis: Axis? = nil
    @State private var closing: Task<Void, Never>? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            scene(screen: screen, insets: insets)
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(OrbitPicksSheetPalette.indigo.ignoresSafeArea())
        .preferredColorScheme(isOpen ? .dark : .light)
        .onDisappear { closing?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func scene(screen: CGSize, insets: EdgeInsets) -> some View {
        let sheetHeight = screen.height - insets.top
        return OrbitPicksSheetReader(value: AnimatablePair(sheet, drag)) { pair in
            let up = min(1, pair.first)
            let dragged = pair.second
            ZStack(alignment: .topLeading) {
                ZStack {
                    OrbitPicksSheetPageBehind(
                        size: screen, top: insets.top, bottom: insets.bottom,
                        title: title, banner: banner, bannerCaption: bannerCaption, bannerScale: bannerScale,
                        onBanner: {
                            touched()
                            open()
                        },
                        onBannerPress: { down in
                            if down { touched() }
                            tapBanner(down)
                        }
                    )
                    OrbitPicksSheetPalette.indigo
                        .opacity(M.scrim * up * (1 - M.ramp(dragged, 0, sheetHeight)))
                        .allowsHitTesting(false)
                }
                .frame(width: screen.width, height: screen.height)
                .clipShape(RoundedRectangle(cornerRadius: C.lerp(0, M.pageRadius, up)))
                .scaleEffect(C.lerp(1, M.pageScale, up))

                sheetView(screen: screen, sheetHeight: sheetHeight)
                    .offset(y: insets.top + (1 - pair.first) * (sheetHeight + M.overshoot) + dragged)
                    .allowsHitTesting(isOpen)
            }
        }
    }

    private func sheetView(screen: CGSize, sheetHeight: Double) -> some View {
        let centre = screen.width / 2
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: M.sheetRadius)
                .fill(M.edge)
                .frame(width: screen.width - 36, height: 60)
                .offset(x: 18, y: -10)

            OrbitPicksSheetReader(value: clock) { time in
                content(screen: screen, centre: centre, time: time)
            }
            .frame(width: screen.width, height: sheetHeight + M.overshoot, alignment: .topLeading)
            .background(.white)
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: M.sheetRadius, topTrailingRadius: M.sheetRadius))
        }
        .frame(width: screen.width, height: sheetHeight + M.overshoot, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(pan(width: screen.width, sheetHeight: sheetHeight))
    }

    /// Everything inside the sheet, drawn from the clock.
    private func content(screen: CGSize, centre: Double, time: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(Color(hex: 0xe2e2ea))
                .frame(width: 40, height: 5)
                .frame(width: screen.width)
                .offset(y: 8)

            Text(loadingLabel)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color(hex: 0x6b6b7b))
                .frame(width: screen.width)
                .opacity(C.ramp(time, C.captionIn, C.captionIn + 0.4) * (1 - C.ramp(time, C.captionOut, C.captionOut + 0.3)))
                .offset(y: M.captionY)

            let halo = C.ramp(time, C.ringIn - 0.2, C.ringIn + 0.3) * (1 - C.ramp(time, C.ringOut, C.ringOut + 0.4))
            Circle()
                .fill(M.halo)
                .frame(width: M.haloSize, height: M.haloSize)
                .scaleEffect(C.lerp(0.6, 1, halo))
                .opacity(halo)
                .position(x: centre, y: M.loaderY)
                .allowsHitTesting(false)

            ForEach(Array(orbitPicksSheetDiscs.enumerated()), id: \.offset) { index, spec in
                OrbitPicksSheetDisc(spec: spec, index: index, time: time, cx: centre, cy: M.loaderY)
            }
            OrbitPicksSheetRing(time: time, cx: centre, cy: M.loaderY)

            let rise = C.easeOut(C.ramp(time, C.badgeUp, C.badgeUp + C.badgeUpFor))
            OrbitPicksSheetBadge(size: M.badgeSize)
                .scaleEffect(C.pop(C.ramp(time, C.badgeIn, C.badgeIn + C.badgePop)) * C.lerp(1, M.badgeSmall / M.badgeSize, rise))
                .offset(y: C.lerp(0, M.badgeTopY - M.loaderY, rise))
                .position(x: centre, y: M.loaderY)

            let shown = C.easeOut(C.ramp(time, C.resultIn, C.resultIn + C.resultFor))
            result(screen: screen, centre: centre)
                .opacity(shown)
                .offset(y: C.lerp(40, 0, shown))
                .allowsHitTesting(isOpen)
        }
    }

    private func result(screen: CGSize, centre: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Text(resultTitle)
                .font(.system(size: 24, weight: .heavy))
                .tracking(-0.6)
                .foregroundStyle(Color(hex: 0x14141f))
                .frame(width: screen.width, height: 30)
                .offset(y: M.resultTitleY)
            Text(resultCaption)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color(hex: 0x7b7b8c))
                .frame(width: screen.width, height: 20)
                .offset(y: M.resultTitleY + 34)

            OrbitPicksSheetReader(value: page) { place in
                ZStack(alignment: .topLeading) {
                    ForEach(Array(profiles.enumerated()), id: \.offset) { index, profile in
                        OrbitPicksSheetProfileCard(profile: profile, chatLabel: chatLabel) { onChat?(profile) }
                            .frame(width: screen.width, height: M.pagerHeight)
                            .offset(x: (Double(index) - place) * screen.width)
                    }
                    OrbitPicksSheetReader(value: finger) { f in
                        Circle()
                            .fill(.white.opacity(0.7))
                            .overlay(Circle().strokeBorder(Color(hex: 0x14141f).opacity(0.3), lineWidth: 2))
                            .frame(width: M.finger, height: M.finger)
                            .scaleEffect(C.lerp(1, 0.85, C.ramp(f, 0, 0.2)))
                            .opacity(C.ramp(f, 0, 0.12) * (1 - C.ramp(f, 0.8, 1)))
                            .offset(x: centre - M.finger / 2 + C.lerp(70, -60, C.easeOut(f)), y: 220)
                            .allowsHitTesting(false)
                    }
                }
                .frame(width: screen.width, height: M.pagerHeight, alignment: .topLeading)
                .offset(y: M.pagerTop)

                HStack(spacing: M.dotsGap) {
                    ForEach(profiles.indices, id: \.self) { index in
                        let near = 1 - min(1, abs(place - Double(index)))
                        Capsule()
                            .fill(OrbitPicksSheetPalette.indigo)
                            .frame(width: C.lerp(6, 16, near), height: 6)
                            .opacity(C.lerp(0.25, 1, near))
                    }
                }
                .frame(width: screen.width)
                .offset(y: M.pagerTop + M.pagerHeight + 20)

                HStack(spacing: 5) {
                    Image(systemName: "clock").font(.system(size: 12))
                    Text("New picks in 14 h").font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(Color(hex: 0x9a9aab))
                .frame(width: screen.width, height: 17)
                .offset(y: M.pagerTop + M.pagerHeight + 44)
            }
        }
    }

    // MARK: Gestures

    /// One drag for both jobs: sideways over the cards turns the pager, downwards drags the sheet away.
    private func pan(width: Double, sheetHeight: Double) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                if axis == nil {
                    let sideways = abs(value.translation.width) > abs(value.translation.height)
                    let overCards = value.startLocation.y > M.pagerTop && value.startLocation.y < M.pagerTop + M.pagerHeight
                    axis = sideways ? (overCards ? .horizontal : nil) : .vertical
                    if axis == nil { return }
                    pageFrom = page
                    touched()
                }
                switch axis {
                case .horizontal:
                    instantly { page = min(Double(profiles.count - 1), max(0, pageFrom - value.translation.width / width)) }
                case .vertical:
                    instantly { drag = max(0, value.translation.height) }
                case nil:
                    break
                }
            }
            .onEnded { value in
                defer { axis = nil }
                switch axis {
                case .horizontal:
                    // A flick carries the card on to the next one; a slow drag settles on the nearer one.
                    let target = min(Double(profiles.count - 1), max(0, (page - value.velocity.width / width / 2).rounded()))
                    withAnimation(M.cubicOut(M.snapFor)) { page = target }
                    onProfileChange?(Int(target))
                case .vertical:
                    if drag > M.dismissDrag || value.velocity.height > M.dismissVelocity {
                        // From here the sheet is where the finger left it.
                        instantly {
                            sheet = 1 - drag / sheetHeight
                            drag = 0
                        }
                        close()
                    } else {
                        withAnimation(M.riseSpring) { drag = 0 }
                    }
                case nil:
                    break
                }
            }
    }

    // MARK: Stages

    private func instantly(_ change: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, change)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() { touches += 1 }

    private func open() {
        guard !isOpen else { return }
        closing?.cancel()
        isOpen = true
        instantly { clock = 0 }
        if reduceMotion {
            withAnimation(.easeInOut(duration: M.fade)) { sheet = 1 }
            instantly { clock = C.end }
            return
        }
        withAnimation(M.riseSpring) { sheet = 1 }
        withAnimation(.linear(duration: C.end)) { clock = C.end }
    }

    private func close() {
        guard isOpen else { return }
        isOpen = false
        let duration = reduceMotion ? M.fade : M.dropFor
        withAnimation(reduceMotion ? .easeInOut(duration: duration) : M.dropCurve(duration)) { sheet = 0 }
        closing?.cancel()
        closing = Task { @MainActor in
            try? await Task.sleep(for: .seconds(duration), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Out of sight now: the next opening starts from the beginning.
            instantly {
                clock = 0
                page = 0
                drag = 0
            }
        }
    }

    private func tapBanner(_ down: Bool) {
        withAnimation(.easeInOut(duration: down ? 0.08 : 0.14)) { bannerScale = down ? M.pressScale : 1 }
    }

    /// What a finger does by itself: drags the card to the next profile.
    private func swipe() {
        instantly { finger = 0 }
        if !reduceMotion { withAnimation(.linear(duration: M.fingerFor)) { finger = 1 } }
        withAnimation(M.cubicOut(reduceMotion ? M.fade : M.swipeFor)) { page = 1 }
    }

    /// Everything back as it was at the start, with no animation.
    private func rest() {
        closing?.cancel()
        instantly {
            sheet = 0
            drag = 0
            clock = 0
            page = 0
            pageFrom = 0
            finger = 0
            bannerScale = 1
            isOpen = false
        }
    }

    // MARK: autoPlay

    private var steps: [(at: Double, run: () -> Void)] {
        [
            (M.tapAt, { tapBanner(true) }),
            (M.tapAt + M.tapFor, { tapBanner(false); open() }),
            (M.swipeAt, swipe),
            (M.closeAt, close),
            (M.restAt, rest),
        ]
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run()
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

enum OrbitPicksSheetMotion {
    static let profiles = [
        OrbitPicksSheetProfile(name: "Alex", rating: "5.0", details: "32 · he/him", likes: "Rugby", match: 80,
                               skin: Color(hex: 0xf1c7a3), hair: Color(hex: 0x6a3f26), shirt: Color(hex: 0x2f5fb8),
                               photo: portrait("1506794778202-cad84cf45f1d"), tags: ["Coffee", "Hiking", "Jazz"]),
        OrbitPicksSheetProfile(name: "Taylor", rating: "5.0", details: "29 · they/them", likes: "Cycling", match: 76,
                               skin: Color(hex: 0xe8b48a), hair: Color(hex: 0x1c1c24), shirt: Color(hex: 0x7a2430), glasses: true,
                               photo: portrait("1531891437562-4301cf35b7e4"), tags: ["Film", "Vinyl", "Ramen"]),
        OrbitPicksSheetProfile(name: "Morgan", rating: "4.9", details: "27 · she/her", likes: "Climbing", match: 71,
                               skin: Color(hex: 0xc98f66), hair: Color(hex: 0x3a2118), shirt: Color(hex: 0x2b8a6e),
                               photo: portrait("1487412720507-e7ab37603c6f"), tags: ["Snow", "Dogs", "Pop"]),
    ]

    private static func portrait(_ id: String) -> String {
        "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=240&h=240&q=80"
    }

    /// The autoPlay loop, in seconds from its start.
    static let loop = 9.5
    static let tapAt = 0.8
    static let tapFor = 0.15
    static let swipeAt = 7.0
    static let closeAt = 8.1
    static let restAt = 8.7
    /// Seconds a touch keeps the loop away.
    static let userHold = 3.0

    static let scrim = 0.55
    static let edge = Color(hex: 0x8f7bef)
    static let sheetRadius = 28.0
    /// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
    static let overshoot = 40.0
    static let loaderY = 290.0
    static let badgeSize = 72.0
    static let badgeSmall = 40.0
    static let badgeTopY = 48.0
    static let haloSize = 132.0
    static let halo = Color(hex: 0xefebfd)
    static let captionY = loaderY + 88
    static let resultTitleY = 80.0
    static let pagerTop = 148.0
    static let pagerHeight = 432.0
    static let dotsGap = 6.0
    static let finger = 32.0
    static let pageScale = 0.94
    static let pageRadius = 24.0
    static let pressScale = 0.97
    static let dismissDrag = 120.0
    static let dismissVelocity = 900.0

    static let riseSpring = Animation.interpolatingSpring(mass: 1, stiffness: 170, damping: 18)
    static let dropFor = 0.4
    static let swipeFor = 0.48
    static let fingerFor = 0.52
    static let snapFor = 0.25
    static let fade = 0.2

    static func dropCurve(_ duration: Double) -> Animation { .timingCurve(0.5, 0, 1, 1, duration: duration) }
    static func cubicOut(_ duration: Double) -> Animation { .timingCurve(0.215, 0.61, 0.355, 1, duration: duration) }

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { OrbitPicksSheetClock.lerp(from, to, t) }
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double { OrbitPicksSheetClock.ramp(value, from, to) }
}
