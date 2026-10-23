import SwiftUI

/// The photos of the block: one behind the composer per opening, and those of the page behind.
enum BlurBackdropComposerPhotos {
    private static func photo(_ id: String, _ w: Int, _ h: Int) -> URL? {
        URL(string: "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h)&q=80")
    }

    /// A neon street at night, a lagoon, peaks above the clouds.
    static let backdrops = [
        photo("photo-1540959733332-eab4deabeeaf", 600, 1300),
        photo("photo-1573843981267-be1999ff37cd", 600, 1300),
        photo("photo-1506905925346-21bda4d32df4", 600, 1300),
    ]
    /// The welcome card, the collage and the avatar.
    static let hero = photo("photo-1502602898657-3e91760cbb34", 900, 440)
    static let collage = [
        photo("photo-1570077188670-e3a8d69ac5ff", 360, 280),
        photo("photo-1641128324972-af3212f0f6bd", 360, 280),
        photo("photo-1524492412937-b28074a5d7da", 360, 280),
    ]
    static let avatar = photo("photo-1544005313-94ddf0286df2", 120, 120)
}

/// A photo that fills its frame, cropped, with nothing drawn until it has loaded.
struct BlurBackdropComposerPhoto: View {
    let url: URL?

    var body: some View {
        Color.clear
            .overlay {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
            }
            .clipped()
    }
}

/// The photo behind the composer, blurred as the material would blur it. All three stay loaded and
/// only the current one shows, so a new opening never waits for its photo.
struct BlurBackdropComposerBackdrop: View {
    let scene: Int
    let size: CGSize

    // The React Native image blurs 28 photo pixels; the photo is drawn about 0.67 pt a pixel.
    private static let blur = 19.0

    var body: some View {
        ZStack {
            ForEach(BlurBackdropComposerPhotos.backdrops.indices, id: \.self) { s in
                BlurBackdropComposerPhoto(url: BlurBackdropComposerPhotos.backdrops[s])
                    .opacity(s == scene ? 1 : 0)
            }
        }
        .frame(width: size.width, height: size.height)
        .blur(radius: Self.blur, opaque: true)
    }
}
