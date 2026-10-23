import SwiftUI

struct SaveToCollection: View {
    // SaveToCollectionTokens.Clock.loop; a literal so the catalog check can read it
    static let loop: Double = 13

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model: SaveToCollectionModel

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = StateObject(wrappedValue: SaveToCollectionModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            SaveToCollectionStage(model: model)
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .overlay {
                    SaveToCollectionTouchView(model: model)
                        .accessibilityHidden(true)
                }
                .onAppear {
                    model.configure(size: proxy.size, reduceMotion: reduceMotion)
                    model.start(autoPlay: autoPlay)
                }
                .onChange(of: proxy.size) { _, size in
                    model.configure(size: size, reduceMotion: reduceMotion)
                }
                .onChange(of: reduceMotion) { _, value in
                    model.configure(size: proxy.size, reduceMotion: value)
                }
        }
        .background(SaveToCollectionTokens.Palette.background)
        .ignoresSafeArea()
    }
}

private struct SaveToCollectionStage: View {
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        let layout = model.layout
        let visible = model.visible
        let drag = model.dragPresentation()

        ZStack(alignment: .topLeading) {
            SaveToCollectionTokens.Palette.background

            ForEach(Array(visible.enumerated()), id: \.element) { order, index in
                SaveToCollectionCardView(
                    card: SaveToCollectionTokens.cards[index],
                    index: index,
                    rect: layout.rects[index],
                    order: order,
                    model: model
                )
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(SaveToCollectionTokens.cards[index].label). Double tap to save")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction {
                    model.accessibilitySave(card: index)
                }
            }

            if let progress = model.streamProgress() {
                SaveToCollectionStream(progress: progress, layout: layout, rise: model.riseProgress())
                    .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
            }

            SaveToCollectionBookmarkView(model: model, layout: layout)
                .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)

            ForEach(model.flights) { flight in
                SaveToCollectionFlightView(flight: flight, model: model, layout: layout)
                    .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
            }

            if let drag {
                SaveToCollectionDragView(presentation: drag, layout: layout)
            }

            if let ghost = model.ghostPresentation() {
                Circle()
                    .fill(SaveToCollectionTokens.Palette.finger)
                    .overlay {
                        Circle().stroke(
                            SaveToCollectionTokens.Palette.fingerEdge,
                            lineWidth: SaveToCollectionTokens.Layout.fingerBorderWidth
                        )
                    }
                    .frame(width: SaveToCollectionTokens.Layout.finger, height: SaveToCollectionTokens.Layout.finger)
                    .scaleEffect(1 - SaveToCollectionTokens.Layout.fingerPressScaleLoss * ghost.press)
                    .opacity(ghost.opacity)
                    .position(ghost.centre)
                    .accessibilityHidden(true)
            }
        }
        .clipped()
    }
}

private struct SaveToCollectionCardView: View {
    let card: SaveToCollectionTokens.CardToken
    let index: Int
    let rect: CGRect
    let order: Int
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        let deal = model.cardDealProgress(order: order)
        let dim = model.cardDim(for: index)
        let phase = Double(index) * SaveToCollectionTokens.CardStyle.phaseStep
        let amplitude = SaveToCollectionTokens.Save.floatAmplitude * (1 + card.blur / SaveToCollectionTokens.CardStyle.blurAmplitudeDivisor)
        let floatY = model.reduceMotion
            ? 0
            : amplitude * sin(2 * .pi * model.clock / SaveToCollectionTokens.Save.floatPeriod + phase)
        let scale = model.reduceMotion
            ? 1
            : SaveToCollectionMath.mix(SaveToCollectionTokens.CardStyle.dealStartScale, 1, deal) *
                (1 - SaveToCollectionTokens.CardStyle.pressScaleLoss * model.cardPress(for: index))
        let pad = SaveToCollectionTokens.CardStyle.softPad * rect.width / card.frame.width

        SaveToCollectionImage.named(card.soft)
            .resizable()
            .interpolation(.high)
            .frame(width: rect.width + 2 * pad, height: rect.height + 2 * pad)
            .position(x: rect.midX, y: rect.midY)
            .offset(y: model.reduceMotion ? 0 : (1 - deal) * SaveToCollectionTokens.Timing.dealDrop + floatY)
            .scaleEffect(scale)
            .opacity(deal * (1 - SaveToCollectionTokens.CardStyle.liftedOpacityLoss * dim))
    }
}

private struct SaveToCollectionDragView: View {
    let presentation: SaveToCollectionDragPresentation
    let layout: SaveToCollectionLayout

    var body: some View {
        let card = SaveToCollectionTokens.cards[presentation.card]
        let rect = layout.rects[presentation.card]

        ZStack {
            SaveToCollectionImage.named(card.art)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: rect.width, height: rect.height)
                .blur(radius: SaveToCollectionTokens.Glow.blur)
                .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Glow.cornerRadius, style: .continuous))
                .opacity(SaveToCollectionTokens.Glow.opacity)
                .scaleEffect(SaveToCollectionTokens.Glow.scale)
                .offset(y: SaveToCollectionTokens.Glow.drop)

            SaveToCollectionImage.named(card.art)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: rect.width, height: rect.height)
                .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.cardRadius, style: .continuous))
                .shadow(
                    color: .black.opacity(SaveToCollectionTokens.CardStyle.dragShadowOpacity),
                    radius: SaveToCollectionTokens.CardStyle.dragShadowRadius,
                    x: 0,
                    y: SaveToCollectionTokens.CardStyle.dragShadowY
                )
        }
        .rotationEffect(.degrees(presentation.tilt))
        .scaleEffect(presentation.scale)
        .position(presentation.centre)
        .accessibilityHidden(true)
    }
}
