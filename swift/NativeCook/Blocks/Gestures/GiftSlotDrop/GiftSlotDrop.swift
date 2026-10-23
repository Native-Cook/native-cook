import SwiftUI
import UIKit

struct GiftSlotDrop: View {
    static let loop = GiftSlotDropTokens.loop

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
            let safeArea = GiftSlotDropSafeArea.current
            let layout = GiftSlotDropTokens.ScreenLayout(
                width: proxy.size.width,
                height: proxy.size.height,
                top: proxy.safeAreaInsets.top > 0 ? proxy.safeAreaInsets.top : safeArea.top,
                bottom: proxy.safeAreaInsets.bottom > 0 ? proxy.safeAreaInsets.bottom : safeArea.bottom
            )
            ZStack(alignment: .topLeading) {
                GiftSlotDropScene(model: model, layout: layout)
                GiftSlotDropTouchView(model: model)
                    .frame(width: layout.width, height: layout.height)

                Color.clear
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                    .offset(x: GiftSlotDropTokens.Layout.edge, y: layout.top)
                    .accessibilityElement()
                    .accessibilityLabel(model.showsOffer ? "Done" : "Back")
                    .accessibilityAddTraits(.isButton)
                    .onTapGesture { if model.showsOffer { model.userClose() } }

                Color.clear
                    .frame(width: layout.width - 80, height: 76)
                    .offset(x: 40, y: layout.top + 76)
                    .accessibilityElement()
                    .accessibilityLabel(model.showsOffer ? "Your gift is ready" : "Choose your welcome gift")
                    .accessibilityAddTraits(.isHeader)

                if model.showsOffer {
                    GiftSlotDropActionButton(model: model, layout: layout, onShop: onShop)
                }
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .background(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.page))
            .onAppear {
                GiftSlotDropFonts.register()
                model.configure(layout, reducedMotion: reducedMotion)
            }
            .onChange(of: layout) { _, value in model.configure(value, reducedMotion: reducedMotion) }
            .onChange(of: reducedMotion) { _, value in model.configure(layout, reducedMotion: value) }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

@MainActor
private enum GiftSlotDropSafeArea {
    static var current: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }
}

private struct GiftSlotDropActionButton: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout
    let onShop: ((String) -> Void)?

    var body: some View {
        let progress = model.detailProgress(2)
        Button(model.gift.action) {
            if let onShop { onShop(model.gift.id) } else { model.userClose() }
        }
        .font(GiftSlotDropFonts.font(GiftSlotDropFonts.semibold, size: 16))
        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.buttonInk))
        .frame(width: layout.width - 2 * GiftSlotDropTokens.Layout.edge, height: GiftSlotDropTokens.Layout.buttonH)
        .background(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.button), in: Capsule())
        .buttonStyle(GiftSlotDropButtonStyle())
        .offset(
            x: GiftSlotDropTokens.Layout.edge,
            y: layout.buttonY + (model.reducedMotion ? 0 : 10 * CGFloat(1 - progress))
        )
        .opacity(progress)
        .accessibilityHint("Your code is \(model.gift.code.map(String.init).joined(separator: " "))")
    }
}

private struct GiftSlotDropButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .scaleEffect(configuration.isPressed ? GiftSlotDropTokens.ButtonPress.scale : 1)
            .animation(
                .timingCurve(0.23, 1, 0.32, 1, duration: (configuration.isPressed ? GiftSlotDropTokens.ButtonPress.inMs : GiftSlotDropTokens.ButtonPress.outMs) / 1_000),
                value: configuration.isPressed
            )
    }
}
