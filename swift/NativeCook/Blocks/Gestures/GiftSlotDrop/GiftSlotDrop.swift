import SwiftUI

struct GiftSlotDrop: View {
    static let loop: Double = GiftSlotDropTokens.loop

    let autoPlay: Bool
    let onShop: ((String) -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var model: GiftSlotDropModel

    init(autoPlay: Bool = false, onShop: ((String) -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.onShop = onShop
        _model = State(initialValue: GiftSlotDropModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                GiftSlotDropScene(model: model, size: proxy.size)
                GiftSlotDropTouchView(model: model)
                    .frame(width: proxy.size.width, height: proxy.size.height)
                GiftSlotDropChoosingChrome(model: model, size: proxy.size)
                    .allowsHitTesting(false)
                if model.showsOffer {
                    GiftSlotDropOfferChrome(model: model, size: proxy.size, onShop: onShop)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .background(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.stageEdge))
            .onAppear { model.configure(width: proxy.size.width, reducedMotion: reducedMotion) }
            .onChange(of: proxy.size) { _, size in model.configure(width: size.width, reducedMotion: reducedMotion) }
            .onChange(of: reducedMotion) { _, reduced in model.configure(width: proxy.size.width, reducedMotion: reduced) }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

private struct GiftSlotDropChoosingChrome: View {
    let model: GiftSlotDropModel
    let size: CGSize

    var body: some View {
        let layout = GiftSlotDropTokens.Layout.self
        ZStack(alignment: .topLeading) {
            GiftSlotDropHeader(icon: .back, trailing: "T&Cs")
                .frame(width: size.width - layout.headerLeft - layout.headerRight, height: 44)
                .offset(x: layout.headerLeft, y: layout.headerTop)
                .opacity(model.showsOffer ? 0 : 1)
            GiftSlotDropAsset(name: "GiftSlotDrop-gift-box.png")
                .frame(width: layout.giftSize, height: layout.giftSize)
                .position(x: size.width / 2 + layout.giftDx, y: layout.giftY)
                .opacity(model.showsOffer ? 0 : 1)
            ZStack {
                Text("Choose your\nwelcome gift")
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .opacity(model.isChoosing && !model.armed ? 1 : 0)
                Text("Release to\nactivate offer!")
                    .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow))
                    .opacity(model.isChoosing && model.armed ? 1 : 0)
            }
            .font(.custom("Avenir Next", size: layout.titleSize).weight(.bold))
            .multilineTextAlignment(.center)
            .lineSpacing(layout.titleLine - layout.titleSize)
            .frame(width: size.width, height: layout.titleLine * 2)
            .position(x: size.width / 2, y: layout.titleY)
            .animation(.easeOut(duration: 0.22), value: model.armed)
            VStack(spacing: GiftSlotDropTokens.Space.md) {
                GiftSlotDropAsset(name: "GiftSlotDrop-gift-box.png").frame(width: 32, height: 32)
                Text("Activating the\noffer for you…")
                    .font(.custom("Avenir Next", size: layout.titleSize).weight(.bold))
                    .multilineTextAlignment(.center)
                    .lineSpacing(layout.titleLine - layout.titleSize)
                    .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow))
            }
            .frame(width: size.width)
            .position(x: size.width / 2, y: layout.cardY - 14)
            .opacity(model.isActivating ? 1 : 0)
            .animation(.easeOut(duration: 0.22).delay(model.reducedMotion ? 0 : 0.2), value: model.isActivating)
            Text("Drag down to activate offer")
                .font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.hint).weight(.semibold))
                .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.hint))
                .frame(width: size.width)
                .position(x: size.width / 2, y: layout.hintY)
                .opacity(model.phase == .choosing || model.phase == .swiping ? 1 : 0)
                .animation(.easeOut(duration: 0.22), value: model.phase)
        }
        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.ink))
        .animation(.easeOut(duration: 0.22), value: model.showsOffer)
    }
}

private struct GiftSlotDropOfferChrome: View {
    let model: GiftSlotDropModel
    let size: CGSize
    let onShop: ((String) -> Void)?

    var body: some View {
        let layout = GiftSlotDropTokens.Layout.self
        let gift = model.gift
        let p0 = model.detailProgress(0), p1 = model.detailProgress(1), p2 = model.detailProgress(2), p3 = model.detailProgress(3)
        ZStack(alignment: .topLeading) {
            HStack {
                Button("Done", systemImage: "xmark") { model.userClose() }
                    .labelStyle(.iconOnly)
                    .font(.system(size: 16, weight: .bold))
                    .buttonStyle(GiftSlotDropPressStyle())
                Spacer()
                Text("Help").font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.header).weight(.bold))
            }
            .frame(width: size.width - layout.headerLeft - layout.headerRight, height: 44)
            .offset(x: layout.headerLeft, y: layout.offerHeaderTop)
            .opacity(p0)
            VStack(alignment: .leading, spacing: GiftSlotDropTokens.Space.xl) {
                Text("How to avail cashback?")
                    .font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.howTitle).weight(.bold))
                    .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow))
                    .opacity(p1).offset(y: CGFloat(12 * (1 - p1)))
                HStack(spacing: GiftSlotDropTokens.Space.md) {
                    Text("1")
                        .font(.system(size: GiftSlotDropTokens.TypeStyle.stepNumber, weight: .ultraLight))
                        .frame(width: 34)
                    Text(gift.use)
                        .font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.stepText).weight(.semibold))
                        .kerning(0)
                        .lineSpacing(GiftSlotDropTokens.TypeStyle.stepLine - GiftSlotDropTokens.TypeStyle.stepText)
                        .frame(width: 250, alignment: .leading)
                }
                .opacity(p2).offset(y: CGFloat(12 * (1 - p2)))
            }
            .frame(width: size.width - GiftSlotDropTokens.Space.gutter - GiftSlotDropTokens.Space.xxl, alignment: .leading)
            .offset(x: GiftSlotDropTokens.Space.gutter, y: layout.howTop)
            Button("Order from \(gift.name)") {
                if let onShop { onShop(gift.id) } else { model.userClose() }
            }
            .font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.button).weight(.semibold))
            .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.buttonInk))
            .frame(width: layout.buttonW, height: layout.buttonH)
            .background(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.button), in: Capsule())
            .buttonStyle(GiftSlotDropPressStyle())
            .position(x: size.width / 2, y: layout.buttonTop + layout.buttonH / 2)
            .opacity(p3).offset(y: CGFloat(12 * (1 - p3)))
        }
        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.ink))
    }
}

private struct GiftSlotDropHeader: View {
    enum Icon { case back }
    let icon: Icon
    let trailing: String

    var body: some View {
        HStack {
            Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold))
            Spacer()
            Text(trailing).font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.header).weight(.bold))
        }
    }
}

private struct GiftSlotDropPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .scaleEffect(CGFloat(configuration.isPressed ? GiftSlotDropTokens.ButtonPress.scale : 1))
            .animation(
                .timingCurve(0.23, 1, 0.32, 1, duration: (configuration.isPressed ? GiftSlotDropTokens.ButtonPress.inMs : GiftSlotDropTokens.ButtonPress.outMs) / 1_000),
                value: configuration.isPressed
            )
    }
}

private struct GiftSlotDropAsset: View {
    let name: String
    var body: some View {
        if let image = UIImage(named: name) { Image(uiImage: image).resizable().scaledToFit() }
        else { Color.clear.onAppear { assertionFailure("Missing GiftSlotDrop asset: \(name)") } }
    }
}
