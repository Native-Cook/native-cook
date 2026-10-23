import SwiftUI

private let sheetRadius: CGFloat = 32
private let sheetBackdropColors = ["#d69aa8", "#e4b7bd", "#d8a6ae"]

/// RN `Pressable` back button with a 110ms background/scale transition.
private struct BackButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: 42, height: 42)
            .background(
                Circle().fill(
                    configuration.isPressed
                        ? Color(css: "rgba(103, 113, 124, 0.24)")
                        : Color(css: "rgba(255, 255, 255, 0.62)")
                )
            )
            .overlay(Circle().strokeBorder(Color(css: "rgba(105, 115, 132, 0.14)"), lineWidth: 1))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.11), value: configuration.isPressed)
    }
}

struct CardDetailScreen: View {
    let cardId: String?
    var safeAreaInsets = EdgeInsets()
    /// Called once the card has settled and the screen can be popped.
    let onClose: () -> Void

    @State private var isDismissing = false

    var body: some View {
        GeometryReader { geo in
            if getCardById(cardId) != nil {
                content(geo: geo)
            } else {
                missing
            }
        }
    }

    private func content(geo: GeometryProxy) -> some View {
        let insets = safeAreaInsets
        let width = geo.size.width
        let height = geo.size.height
        let cardWidth = min(width - Spacing.xxl * 2, 316)
        let sheetTop = max(insets.top + Spacing.xs, Spacing.xl)
        let sheetHeight = max(height - sheetTop, 1)

        return ZStack(alignment: .top) {
            LinearGradient(
                gradient: Gradient(css: sheetBackdropColors),
                startPoint: UnitPoint(x: 0, y: 0),
                endPoint: UnitPoint(x: 1, y: 0.8)
            )

            ZStack(alignment: .top) {
                CollectionCardBackdropPalette.background
                CollectionCardBackdrop(height: sheetHeight, width: width)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        HStack {
                            Button {
                                isDismissing = true
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(Color(css: "#667080"))
                                    .frame(width: 14, height: 14)
                            }
                            .buttonStyle(BackButtonStyle())
                            .disabled(isDismissing)
                            .accessibilityLabel("Quay lại bộ sưu tập")
                            .accessibilityHint("Lá bài ổn định lại trước khi trở về danh sách")
                            Spacer(minLength: 0)
                        }
                        .padding(.bottom, Spacing.xl)

                        AwakeningCard(exiting: isDismissing, onExitReady: onClose, width: cardWidth)

                        Text("1")
                            .font(.custom("Georgia", fixedSize: TypeScale.label))
                            .foregroundStyle(Color(css: "#84909d"))
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(Color(css: "rgba(255, 255, 255, 0.84)")))
                            .overlay(Circle().strokeBorder(Color(css: "rgba(255, 255, 255, 0.94)"), lineWidth: 1))
                            .shadow(color: Color(css: "rgba(91, 103, 119, 0.14)"), radius: 5.5, x: 0, y: 3)
                            .padding(.top, 54)

                        VStack(spacing: Spacing.md) {
                            Text("The Awakening")
                                .font(.custom("Georgia", fixedSize: 30))
                                .tracking(-1.2)
                                .frame(height: 36)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(Color(css: "#18212b"))
                                .textSelection(.enabled)
                            Text("Congratulations on signing up to mymind.\nWelcome to your beautiful private garden.")
                                .font(.system(size: 15))
                                .lineSpacing(4)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(Color(css: "#68717d"))
                                .lineLimit(2)
                                .minimumScaleFactor(0.82)
                                .frame(maxWidth: 360)
                                .textSelection(.enabled)
                        }
                        .frame(maxWidth: 430)
                        .padding(.top, 50)
                    }
                    .padding(.top, Spacing.md)
                    .padding(.horizontal, Spacing.xl)
                    .padding(.bottom, insets.bottom + Spacing.xxl)
                    .frame(maxWidth: .infinity, minHeight: sheetHeight, alignment: .top)
                }

                Capsule()
                    .fill(Color(css: "rgba(103, 113, 124, 0.62)"))
                    .frame(width: 34, height: 5)
                    .padding(.top, Spacing.sm)
            }
            .frame(width: width, height: sheetHeight)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: sheetRadius,
                    bottomLeadingRadius: 0,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: sheetRadius,
                    style: .continuous
                )
            )
            .padding(.top, sheetTop)
        }
        .background(Color(css: sheetBackdropColors[0]))
    }

    private var missing: some View {
        VStack(spacing: 0) {
            Text("Không tìm thấy thẻ")
                .font(.system(size: TypeScale.title, weight: .heavy))
                .foregroundStyle(AppColors.text)
            Button("Trở về bộ sưu tập", action: onClose)
                .foregroundStyle(AppColors.accent)
                .padding(.top, Spacing.md)
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
    }
}

#Preview("Detail") {
    CardDetailScreen(cardId: "verdant-sproutling") {}
}

#Preview("Missing") {
    CardDetailScreen(cardId: "nope") {}
}
