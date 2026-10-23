import SwiftUI

struct TiltPosterParallax: View {
    static let loop: Double = TiltPosterParallaxTokens.loop

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model: TiltPosterParallaxModel
    @State private var expanded = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = State(initialValue: TiltPosterParallaxModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let safeTop = geometry.safeAreaInsets.top
            let cardWidth = (size.width * TiltPosterParallaxTokens.Layout.cardWidthRatio).rounded()
            let cardHeight = (cardWidth * TiltPosterParallaxTokens.Layout.cardAspect).rounded()
            let scriptedWatchPoint = watchPoint(size: size, cardWidth: cardWidth, cardHeight: cardHeight)

            ZStack(alignment: .topLeading) {
                TiltPosterParallaxTokens.Palette.background
                backdrop(size: size)
                topBar
                    .frame(width: size.width - TiltPosterParallaxTokens.Layout.topBarSideInset * 2)
                    .offset(
                        x: TiltPosterParallaxTokens.Layout.topBarSideInset,
                        y: safeTop + TiltPosterParallaxTokens.Layout.topBar
                    )
                cardStage(width: cardWidth, height: cardHeight)
                    .offset(
                        x: (size.width - cardWidth) / 2 - TiltPosterParallaxTokens.Layout.cardMargin,
                        y: TiltPosterParallaxTokens.Layout.cardTop - TiltPosterParallaxTokens.Layout.cardMargin
                    )
                info(width: size.width - TiltPosterParallaxTokens.Layout.gutter * 2)
                    .offset(
                        x: TiltPosterParallaxTokens.Layout.gutter,
                        y: TiltPosterParallaxTokens.Layout.cardTop + cardHeight
                            + TiltPosterParallaxTokens.Layout.titleGap
                            + CGFloat(model.lift - 1) * cardHeight
                    )
                if autoPlay {
                    scriptedFinger(size: size, cardWidth: cardWidth)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .onAppear {
                model.configure(
                    cardSize: CGSize(width: cardWidth, height: cardHeight),
                    watchPoint: scriptedWatchPoint,
                    reduceMotion: reduceMotion
                )
                model.start()
            }
            .onDisappear {
                model.stop()
            }
            .onChange(of: size) { _, newSize in
                let newCardWidth = (newSize.width * TiltPosterParallaxTokens.Layout.cardWidthRatio).rounded()
                let newCardHeight = (newCardWidth * TiltPosterParallaxTokens.Layout.cardAspect).rounded()
                model.configure(
                    cardSize: CGSize(width: newCardWidth, height: newCardHeight),
                    watchPoint: self.watchPoint(size: newSize, cardWidth: newCardWidth, cardHeight: newCardHeight),
                    reduceMotion: reduceMotion
                )
            }
            .onChange(of: reduceMotion) { _, newValue in
                model.configure(
                    cardSize: CGSize(width: cardWidth, height: cardHeight),
                    watchPoint: scriptedWatchPoint,
                    reduceMotion: newValue
                )
            }
        }
        .background(TiltPosterParallaxTokens.Palette.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func backdrop(size: CGSize) -> some View {
        let bleed = TiltPosterParallaxTokens.Layout.backdropBleed
        return ZStack(alignment: .topLeading) {
            TiltPosterParallaxAssets.backdrop
                .resizable()
                .scaledToFill()
                .frame(
                    width: size.width + bleed * 2,
                    height: size.width * TiltPosterParallaxTokens.Layout.backdropAspect + bleed
                )
                .offset(
                    x: -bleed + CGFloat(model.tiltX) * TiltPosterParallaxTokens.Parallax.backdrop,
                    y: -bleed + CGFloat(model.tiltY) * TiltPosterParallaxTokens.Parallax.backdrop
                )
            Circle()
                .fill(
                    RadialGradient(
                        colors: [TiltPosterParallaxTokens.Palette.stageLight, .clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: TiltPosterParallaxTokens.Layout.stageLight
                    )
                )
                .frame(
                    width: TiltPosterParallaxTokens.Layout.stageLight * 2,
                    height: TiltPosterParallaxTokens.Layout.stageLight * 2
                )
                .position(x: 0, y: 0)
            LinearGradient(
                colors: [.clear, TiltPosterParallaxTokens.Palette.background.opacity(0.4)],
                startPoint: UnitPoint(x: 0.2, y: 0),
                endPoint: .topTrailing
            )
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: TiltPosterParallaxTokens.Palette.background.opacity(0.35), location: 0.4),
                    .init(color: TiltPosterParallaxTokens.Palette.background, location: 0.62),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private var topBar: some View {
        HStack {
            Button(action: {}) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .regular))
                    .frame(width: TiltPosterParallaxTokens.Layout.close, height: TiltPosterParallaxTokens.Layout.close)
                    .background(TiltPosterParallaxTokens.Palette.glass, in: Circle())
                    .overlay(Circle().stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")

            HStack(spacing: TiltPosterParallaxTokens.Layout.awardGap) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.trophy)
                awardText
                    .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.awardSize).weight(.medium))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.inkFaint)
            }
            .frame(height: TiltPosterParallaxTokens.Layout.awardHeight)
            .padding(.horizontal, TiltPosterParallaxTokens.Layout.awardHorizontalPadding)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("1 nomination, 2 wins")

            Button(action: {}) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 18, weight: .regular))
                    .frame(width: TiltPosterParallaxTokens.Layout.close, height: TiltPosterParallaxTokens.Layout.close)
                    .background(TiltPosterParallaxTokens.Palette.glass, in: Circle())
                    .overlay(Circle().stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More options")
        }
    }

    private var awardText: some View {
        Text(TiltPosterParallaxTokens.Copy.award[0])
            .fontWeight(.heavy)
        + Text(TiltPosterParallaxTokens.Copy.award[1])
        + Text(TiltPosterParallaxTokens.Copy.award[2]).fontWeight(.heavy)
        + Text(TiltPosterParallaxTokens.Copy.award[3])
    }

    private func cardStage(width: CGFloat, height: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            TiltPosterParallaxPoster(
                width: width,
                height: height,
                tiltX: model.tiltX,
                tiltY: model.tiltY,
                lift: model.lift,
                flip: model.flip,
                clock: model.clock,
                reduceMotion: reduceMotion
            )
            TiltPosterParallaxTouchView(model: model)
                .frame(width: width, height: height)
                .offset(
                    x: TiltPosterParallaxTokens.Layout.cardMargin,
                    y: TiltPosterParallaxTokens.Layout.cardMargin
                )
        }
        .frame(
            width: width + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            height: height * CGFloat(TiltPosterParallaxTokens.Tilt.lift) + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            alignment: .topLeading
        )
    }

    private func info(width: CGFloat) -> some View {
        VStack(spacing: TiltPosterParallaxTokens.Layout.infoGap) {
            Text(TiltPosterParallaxTokens.Copy.title)
                .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.titleSize).weight(.medium))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
                .frame(height: TiltPosterParallaxTokens.Typeface.titleLine)
                .accessibilityAddTraits(.isHeader)
            Text(TiltPosterParallaxTokens.Copy.date)
                .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.dateSize).weight(.medium))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.inkFaint)
                .frame(height: TiltPosterParallaxTokens.Typeface.dateLine)
            pills
                .padding(.top, TiltPosterParallaxTokens.Layout.bodyGap - 1)
                .padding(.bottom, TiltPosterParallaxTokens.Layout.bodyGap + 1)
            Text(TiltPosterParallaxTokens.Copy.body)
                .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.bodySize))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
                .lineSpacing(TiltPosterParallaxTokens.Typeface.bodyLine - TiltPosterParallaxTokens.Typeface.bodySize)
                .lineLimit(expanded ? nil : 3)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                expanded.toggle()
            } label: {
                HStack(spacing: TiltPosterParallaxTokens.Layout.moreGap) {
                    Text(expanded ? TiltPosterParallaxTokens.Copy.less : TiltPosterParallaxTokens.Copy.more)
                        .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.moreSize).weight(.semibold))
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                }
                .foregroundStyle(TiltPosterParallaxTokens.Palette.inkFaint)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .accessibilityLabel(expanded ? TiltPosterParallaxTokens.Copy.less : TiltPosterParallaxTokens.Copy.more)
        }
        .frame(width: width, alignment: .top)
    }

    private var pills: some View {
        HStack(spacing: TiltPosterParallaxTokens.Layout.pillGap) {
            Button {
                model.toggleSaved()
            } label: {
                watchPill
            }
            .buttonStyle(TiltPosterParallaxWatchButtonStyle(model: model))
            .accessibilityLabel(model.saved ? "In your watchlist" : "Add to watchlist")
            .accessibilityValue(model.saved ? "Selected" : "Not selected")

            Button(action: {}) {
                HStack(spacing: TiltPosterParallaxTokens.Layout.pillContentGap) {
                    Image(systemName: "eye")
                        .font(.system(size: TiltPosterParallaxTokens.Layout.pillIcon))
                    Text(TiltPosterParallaxTokens.Copy.watched)
                        .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.pillSize).weight(.semibold))
                }
                .foregroundStyle(TiltPosterParallaxTokens.Palette.inkGhost)
                .frame(maxWidth: .infinity, minHeight: TiltPosterParallaxTokens.Layout.pillHeight)
                .padding(.horizontal, TiltPosterParallaxTokens.Layout.pillHorizontalPadding)
                .background(TiltPosterParallaxTokens.Palette.pillGhost, in: Capsule())
            }
            .buttonStyle(TiltPosterParallaxOpacityButtonStyle())
            .accessibilityLabel("Mark as watched")
        }
        .frame(maxWidth: .infinity)
    }

    private var watchPill: some View {
        let progress = min(1, max(0, model.savedProgress))
        let morphScale = model.watchPress * (1 + sin(progress * .pi) * 0.04)
        return HStack(spacing: TiltPosterParallaxTokens.Layout.pillContentGap) {
            ZStack {
                Image(systemName: "plus")
                    .font(.system(size: TiltPosterParallaxTokens.Layout.pillIcon))
                    .opacity(1 - progress)
                    .rotationEffect(.degrees(progress * 90))
                    .scaleEffect(1 - progress * 0.5)
                Image(systemName: "checkmark")
                    .font(.system(size: TiltPosterParallaxTokens.Layout.pillIcon))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.goldInk)
                    .opacity(progress)
                    .rotationEffect(.degrees((progress - 1) * 90))
                    .scaleEffect(0.5 + progress * 0.5)
            }
            .frame(width: TiltPosterParallaxTokens.Layout.pillIcon, height: TiltPosterParallaxTokens.Layout.pillIcon)
            Text(model.saved ? TiltPosterParallaxTokens.Copy.added : TiltPosterParallaxTokens.Copy.watchlist)
                .font(.custom(TiltPosterParallaxTokens.Typeface.ui, fixedSize: TiltPosterParallaxTokens.Typeface.pillSize).weight(.semibold))
                .foregroundStyle(model.saved ? TiltPosterParallaxTokens.Palette.goldInk : TiltPosterParallaxTokens.Palette.ink)
        }
        .frame(maxWidth: .infinity, minHeight: TiltPosterParallaxTokens.Layout.pillHeight)
        .padding(.horizontal, TiltPosterParallaxTokens.Layout.pillHorizontalPadding)
        .background {
            Capsule()
                .fill(TiltPosterParallaxTokens.Palette.pill)
                .overlay(Capsule().fill(TiltPosterParallaxTokens.Palette.pillSaved).opacity(progress))
        }
        .overlay {
            Capsule()
                .stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5)
                .overlay(Capsule().stroke(TiltPosterParallaxTokens.Palette.pillSavedBorder, lineWidth: 0.5).opacity(progress))
        }
        .scaleEffect(morphScale)
    }

    private func scriptedFinger(size: CGSize, cardWidth: CGFloat) -> some View {
        let x = model.finger.x + (size.width - cardWidth) / 2
        let y = model.finger.y + TiltPosterParallaxTokens.Layout.cardTop
        return Circle()
            .fill(TiltPosterParallaxTokens.Palette.finger)
            .frame(
                width: TiltPosterParallaxTokens.Layout.fingerRadius * 2,
                height: TiltPosterParallaxTokens.Layout.fingerRadius * 2
            )
            .overlay {
                Circle().stroke(
                    TiltPosterParallaxTokens.Palette.fingerRing,
                    lineWidth: TiltPosterParallaxTokens.Render.fingerRingWidth
                )
            }
            .opacity(model.finger.alpha)
            .position(x: x, y: y)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    private func watchPoint(size: CGSize, cardWidth: CGFloat, cardHeight: CGFloat) -> CGPoint {
        let available = size.width - TiltPosterParallaxTokens.Layout.gutter * 2
        let x = TiltPosterParallaxTokens.Layout.gutter
            + (available - TiltPosterParallaxTokens.Layout.pillGap) / 4
            - (size.width - cardWidth) / 2
        let pillsY = TiltPosterParallaxTokens.Typeface.titleLine
            + TiltPosterParallaxTokens.Layout.infoGap
            + TiltPosterParallaxTokens.Typeface.dateLine
            + TiltPosterParallaxTokens.Layout.infoGap
            + TiltPosterParallaxTokens.Layout.bodyGap - 1
        let y = cardHeight
            + TiltPosterParallaxTokens.Layout.titleGap
            + pillsY
            + TiltPosterParallaxTokens.Layout.pillHeight / 2
        return CGPoint(x: x, y: y)
    }

    private struct TiltPosterParallaxWatchButtonStyle: ButtonStyle {
        let model: TiltPosterParallaxModel

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .onChange(of: configuration.isPressed) { _, pressed in
                    if pressed {
                        model.watchPressBegan()
                    } else {
                        model.watchPressEnded()
                    }
                }
        }
    }

    private struct TiltPosterParallaxOpacityButtonStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
        }
    }
}

#Preview {
    TiltPosterParallax(autoPlay: true)
}
