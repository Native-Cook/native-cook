import SwiftUI

/// The photo of the card, warmed to the app's amber and darkened at the foot for its caption.
struct BannerGrowCardScene: View {
    static let width = 252.0
    static let height = 380.0

    /// Peaks above a sea of cloud at first light: the calm the practice promises.
    private static let photo = URL(string: "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=600&h=820&q=80")

    var body: some View {
        Color(hex: 0xb26b3a)
            .overlay {
                AsyncImage(url: Self.photo) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                }
            }
            .overlay(
                LinearGradient(
                    stops: [
                        .init(color: .rgba(232, 140, 62, 0.34), location: 0),
                        .init(color: .rgba(178, 96, 40, 0.18), location: 0.45),
                        .init(color: .rgba(36, 16, 6, 0.78), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
            )
            .frame(width: Self.width, height: Self.height)
            .clipped()
    }
}
