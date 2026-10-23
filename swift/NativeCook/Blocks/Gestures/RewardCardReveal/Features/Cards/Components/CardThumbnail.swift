import SwiftUI

/// RN `Pressable` opacity feedback (`opacity: pressed ? 0.72 : 1`).
struct PressOpacityButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.72 : 1)
    }
}

struct CardThumbnail: View {
    let item: GalleryItem
    let width: CGFloat
    let onSelect: (String) -> Void

    var body: some View {
        switch item {
        case .locked(let locked):
            VStack {
                CollectionCardBack(markerCount: locked.lockCount, markerVariant: .lock, width: width)
            }
            .frame(width: width)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(locked.lockCount) locked cards")

        case .owned(let owned):
            let height = width * cardHeightRatio
            let borderRadius = 10 * (width / 218)

            Button {
                onSelect(owned.cardId)
            } label: {
                VStack(spacing: 10) {
                    Image(owned.image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: width, height: height)
                        .scaleEffect(1.035)
                        .clipShape(RoundedRectangle(cornerRadius: borderRadius, style: .continuous))
                        .accessibilityLabel(owned.name)

                    Text("\(owned.count)")
                        .font(.custom("Georgia", fixedSize: 10).monospacedDigit())
                        .foregroundStyle(Color(css: "#8a94a1"))
                        .frame(width: 22, height: 22)
                        .background(Color(css: "rgba(255, 255, 255, 0.86)"), in: Circle())
                        .overlay(Circle().strokeBorder(Color(css: "rgba(255, 255, 255, 0.96)"), lineWidth: 1))
                }
                .frame(width: width)
            }
            .buttonStyle(PressOpacityButtonStyle())
            .accessibilityLabel("\(owned.name), \(owned.count) thẻ trong bộ sưu tập")
            .accessibilityHint("Mở màn hình chi tiết và hiệu ứng holographic")
        }
    }
}

#Preview {
    HStack(alignment: .top, spacing: 10) {
        CardThumbnail(item: galleryItems[0], width: 170) { _ in }
        CardThumbnail(item: galleryItems[1], width: 170) { _ in }
    }
    .padding()
    .background(CollectionGalleryPalette.background)
}
