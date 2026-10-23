import SwiftUI
import UIKit

/// The block's pictures are loose files in the app bundle, not an asset catalog,
/// so SwiftUI's Image(name) cannot find them; load them through UIImage instead.
enum SaveToCollectionImage {
    private static var cache: [String: UIImage] = [:]

    static func named(_ name: String) -> Image {
        if let cached = cache[name] { return Image(uiImage: cached) }
        // A name may already carry its extension ("clip.jpg") or leave it off ("art").
        let candidates: [String?] = [nil, "jpg", "png", "jpeg"]
        let image = candidates.lazy
            .compactMap { Bundle.main.path(forResource: name, ofType: $0) }
            .compactMap { UIImage(contentsOfFile: $0) }
            .first ?? UIImage()
        cache[name] = image
        return Image(uiImage: image)
    }
}
