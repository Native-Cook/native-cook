import SwiftUI
import UIKit

struct TiltPosterParallax: View {
    static let loop: Double = TiltPosterParallaxTokens.loop

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var model: TiltPosterParallaxModel
    @State private var expanded = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        TiltPosterParallaxAssets.registerFonts()
        _model = State(initialValue: TiltPosterParallaxModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let safeTop = max(geometry.safeAreaInsets.top, TiltPosterParallaxSafeArea.current.top)
            let cardWidth = (size.width * TiltPosterParallaxTokens.Layout.cardWidthRatio).rounded()
            let cardHeight = (cardWidth * TiltPosterParallaxTokens.Layout.cardAspect).rounded()
            let art = TiltPosterParallaxArtFactory.make(
                cardWidth: cardWidth,
                cardHeight: cardHeight,
                scale: displayScale
            )
            let scriptedWatchPoint = watchPoint(size: size, cardWidth: cardWidth, cardHeight: cardHeight)

            ZStack(alignment: .topLeading) {
                TiltPosterParallaxTokens.Palette.background

                backdrop(art: art, size: size)
                backdropFade

                topBar
                    .frame(width: size.width - TiltPosterParallaxTokens.Layout.gutter * 2)
                    .offset(
                        x: TiltPosterParallaxTokens.Layout.gutter,
                        y: safeTop + TiltPosterParallaxTokens.Layout.topBar
                    )

                cardStage(width: cardWidth, height: cardHeight, art: art)
                    .offset(
                        x: (size.width - cardWidth) / 2 - TiltPosterParallaxTokens.Layout.cardMargin,
                        y: safeTop + TiltPosterParallaxTokens.Layout.cardTop - TiltPosterParallaxTokens.Layout.cardMargin
                    )

                info(width: size.width - TiltPosterParallaxTokens.Layout.gutter * 2)
                    .offset(
                        x: TiltPosterParallaxTokens.Layout.gutter,
                        y: safeTop + TiltPosterParallaxTokens.Layout.cardTop + cardHeight
                            + TiltPosterParallaxTokens.Layout.titleGap
                    )

                if autoPlay, !reduceMotion {
                    scriptedFinger(size: size, safeTop: safeTop, cardWidth: cardWidth)
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
                    watchPoint: watchPoint(size: newSize, cardWidth: newCardWidth, cardHeight: newCardHeight),
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
        .ignoresSafeArea()
        .background(TiltPosterParallaxTokens.Palette.background)
        .preferredColorScheme(.dark)
    }

    private func backdrop(art: TiltPosterParallaxArt, size: CGSize) -> some View {
        let bleed = TiltPosterParallaxTokens.Layout.backdropBleed
        let height = size.height * TiltPosterParallaxTokens.Layout.backdropShare
        return Image(uiImage: art.backdrop)
            .resizable()
            .scaledToFill()
            .frame(width: size.width + bleed * 2, height: height + bleed * 2)
            .clipped()
            .opacity(0.85)
            .offset(
                x: -bleed + CGFloat(model.tiltX) * TiltPosterParallaxTokens.Parallax.backdrop,
                y: -bleed + CGFloat(model.tiltY) * TiltPosterParallaxTokens.Parallax.backdrop
            )
            .accessibilityHidden(true)
    }

    private var backdropFade: some View {
        LinearGradient(
            stops: [
                .init(color: .clear, location: 0),
                .init(color: TiltPosterParallaxTokens.Palette.background.opacity(0.55), location: 0.35),
                .init(color: TiltPosterParallaxTokens.Palette.background, location: TiltPosterParallaxTokens.Layout.backdropShare),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    private var topBar: some View {
        ZStack {
            HStack {
                roundButton(systemName: "xmark", size: TiltPosterParallaxTokens.Layout.closeIcon, label: "Close")
                Spacer()
                roundButton(systemName: "square.and.arrow.up", size: TiltPosterParallaxTokens.Layout.shareIcon, label: "Share")
            }

            HStack(spacing: TiltPosterParallaxTokens.Layout.awardGap) {
                Image(systemName: "trophy")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.trophy)
                Text(TiltPosterParallaxTokens.Copy.award[0])
                    .font(.custom(TiltPosterParallaxTokens.Typeface.semibold, fixedSize: TiltPosterParallaxTokens.Typeface.awardSize))
                + Text(TiltPosterParallaxTokens.Copy.award[1])
                    .font(.custom(TiltPosterParallaxTokens.Typeface.medium, fixedSize: TiltPosterParallaxTokens.Typeface.awardSize))
            }
            .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
            .frame(height: TiltPosterParallaxTokens.Layout.awardHeight)
            .padding(.horizontal, TiltPosterParallaxTokens.Layout.awardHorizontalPadding)
            .background(TiltPosterParallaxTokens.Palette.glass, in: Capsule())
            .overlay(Capsule().stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("3 festival awards")
        }
        .frame(height: TiltPosterParallaxTokens.Layout.close)
    }

    private func roundButton(systemName: String, size: CGFloat, label: String) -> some View {
        Button(action: {}) {
            Image(systemName: systemName)
                .font(.system(size: size, weight: .regular))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
                .frame(width: TiltPosterParallaxTokens.Layout.close, height: TiltPosterParallaxTokens.Layout.close)
                .background(TiltPosterParallaxTokens.Palette.glass, in: Circle())
                .overlay(Circle().stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func cardStage(width: CGFloat, height: CGFloat, art: TiltPosterParallaxArt) -> some View {
        ZStack(alignment: .topLeading) {
            TiltPosterParallaxPoster(
                width: width,
                height: height,
                art: art,
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
                .accessibilityLabel(TiltPosterParallaxTokens.Copy.posterLabel)
                .accessibilityHint(TiltPosterParallaxTokens.Copy.posterHint)
        }
        .frame(
            width: width + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            height: height * TiltPosterParallaxTokens.Tilt.lift + TiltPosterParallaxTokens.Layout.cardMargin * 2,
            alignment: .topLeading
        )
    }

    private func info(width: CGFloat) -> some View {
        VStack(spacing: 0) {
            Text(TiltPosterParallaxTokens.Copy.title)
                .font(.custom(TiltPosterParallaxTokens.Typeface.serif, fixedSize: TiltPosterParallaxTokens.Typeface.titleSize))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
                .frame(height: TiltPosterParallaxTokens.Typeface.titleLine)
                .accessibilityAddTraits(.isHeader)

            Text(TiltPosterParallaxTokens.Copy.meta)
                .font(.custom(TiltPosterParallaxTokens.Typeface.regular, fixedSize: TiltPosterParallaxTokens.Typeface.metaSize))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.inkFaint)
                .frame(height: TiltPosterParallaxTokens.Typeface.metaLine)
                .padding(.top, TiltPosterParallaxTokens.Layout.infoGap)

            pills
                .padding(.top, TiltPosterParallaxTokens.Layout.pillsMarginTop)
                .padding(.bottom, TiltPosterParallaxTokens.Layout.pillsMarginBottom)

            Text(TiltPosterParallaxTokens.Copy.body)
                .font(.custom(TiltPosterParallaxTokens.Typeface.regular, fixedSize: TiltPosterParallaxTokens.Typeface.bodySize))
                .foregroundStyle(TiltPosterParallaxTokens.Palette.inkSoft)
                .lineSpacing(TiltPosterParallaxTokens.Typeface.bodyLine - TiltPosterParallaxTokens.Typeface.bodySize)
                .lineLimit(expanded ? nil : 3)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                expanded.toggle()
            } label: {
                HStack(spacing: TiltPosterParallaxTokens.Layout.moreGap) {
                    Text(expanded ? TiltPosterParallaxTokens.Copy.less : TiltPosterParallaxTokens.Copy.more)
                        .font(.custom(TiltPosterParallaxTokens.Typeface.semibold, fixedSize: TiltPosterParallaxTokens.Typeface.moreSize))
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                }
                .foregroundStyle(TiltPosterParallaxTokens.Palette.ink)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, TiltPosterParallaxTokens.Layout.infoGap)
            .contentShape(Rectangle())
            .accessibilityLabel(expanded ? TiltPosterParallaxTokens.Copy.less : TiltPosterParallaxTokens.Copy.more)

            formats
                .padding(.top, TiltPosterParallaxTokens.Layout.formatsPaddingTop)
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(TiltPosterParallaxTokens.Palette.glassBorder)
                        .frame(height: 0.5)
                }
                .padding(.top, TiltPosterParallaxTokens.Layout.formatsMarginTop)
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
                    Image(systemName: "play.fill")
                        .font(.system(size: 16, weight: .regular))
                    Text(TiltPosterParallaxTokens.Copy.play)
                        .font(.custom(TiltPosterParallaxTokens.Typeface.semibold, fixedSize: TiltPosterParallaxTokens.Typeface.pillSize))
                }
                .foregroundStyle(TiltPosterParallaxTokens.Palette.playInk)
                .frame(maxWidth: .infinity, minHeight: TiltPosterParallaxTokens.Layout.pillHeight)
                .padding(.horizontal, TiltPosterParallaxTokens.Layout.pillHorizontalPadding)
                .background(TiltPosterParallaxTokens.Palette.play, in: Capsule())
            }
            .buttonStyle(TiltPosterParallaxOpacityButtonStyle())
            .accessibilityLabel(TiltPosterParallaxTokens.Copy.play)
        }
        .frame(maxWidth: .infinity)
    }

    private var watchPill: some View {
        let progress = min(1, max(0, model.savedProgress))
        let morphScale = model.watchPress * (1 + sin(progress * .pi) * 0.04)
        return HStack(spacing: TiltPosterParallaxTokens.Layout.pillContentGap) {
            ZStack {
                Image(systemName: "plus")
                    .font(.system(size: TiltPosterParallaxTokens.Layout.pillIcon, weight: .regular))
                    .opacity(1 - progress)
                    .rotationEffect(.degrees(progress * 90))
                    .scaleEffect(1 - progress * 0.5)
                Image(systemName: "checkmark")
                    .font(.system(size: TiltPosterParallaxTokens.Layout.pillIcon, weight: .regular))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.goldInk)
                    .opacity(progress)
                    .rotationEffect(.degrees((progress - 1) * 90))
                    .scaleEffect(0.5 + progress * 0.5)
            }
            .frame(width: TiltPosterParallaxTokens.Layout.pillIcon, height: TiltPosterParallaxTokens.Layout.pillIcon)

            Text(model.saved ? TiltPosterParallaxTokens.Copy.added : TiltPosterParallaxTokens.Copy.watchlist)
                .font(.custom(TiltPosterParallaxTokens.Typeface.semibold, fixedSize: TiltPosterParallaxTokens.Typeface.pillSize))
                .foregroundStyle(model.saved ? TiltPosterParallaxTokens.Palette.goldInk : TiltPosterParallaxTokens.Palette.ink)
        }
        .frame(maxWidth: .infinity, minHeight: TiltPosterParallaxTokens.Layout.pillHeight)
        .padding(.horizontal, TiltPosterParallaxTokens.Layout.pillHorizontalPadding)
        .background {
            Capsule()
                .fill(TiltPosterParallaxTokens.Palette.glass)
                .overlay(Capsule().fill(TiltPosterParallaxTokens.Palette.pillSaved).opacity(progress))
        }
        .overlay {
            Capsule()
                .stroke(TiltPosterParallaxTokens.Palette.glassBorder, lineWidth: 0.5)
                .overlay(Capsule().stroke(TiltPosterParallaxTokens.Palette.pillSavedBorder, lineWidth: 0.5).opacity(progress))
        }
        .scaleEffect(morphScale)
    }

    private var formats: some View {
        HStack(spacing: 8) {
            ForEach(TiltPosterParallaxTokens.Copy.formats, id: \.self) { format in
                Text(format)
                    .font(.custom(TiltPosterParallaxTokens.Typeface.medium, fixedSize: TiltPosterParallaxTokens.Typeface.chipSize))
                    .foregroundStyle(TiltPosterParallaxTokens.Palette.inkSoft)
                    .frame(height: TiltPosterParallaxTokens.Typeface.chipLine)
                    .padding(.horizontal, TiltPosterParallaxTokens.Layout.chipPadding)
                    .frame(height: TiltPosterParallaxTokens.Layout.chipHeight)
                    .overlay {
                        RoundedRectangle(cornerRadius: TiltPosterParallaxTokens.Layout.chipRadius)
                            .stroke(Color.white.opacity(0.28), lineWidth: 0.5)
                    }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Available in 4K, HDR, Dolby Atmos, with audio description")
    }

    private func scriptedFinger(size: CGSize, safeTop: CGFloat, cardWidth: CGFloat) -> some View {
        let x = model.finger.x + (size.width - cardWidth) / 2
        let y = model.finger.y + safeTop + TiltPosterParallaxTokens.Layout.cardTop
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
            + TiltPosterParallaxTokens.Typeface.metaLine
            + TiltPosterParallaxTokens.Layout.pillsMarginTop
        let y = cardHeight
            + TiltPosterParallaxTokens.Layout.titleGap
            + pillsY
            + TiltPosterParallaxTokens.Layout.pillHeight / 2
        return CGPoint(x: x, y: y)
    }
}

@MainActor
private enum TiltPosterParallaxSafeArea {
    static var current: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }
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
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
    }
}

#Preview {
    TiltPosterParallax(autoPlay: true)
}
