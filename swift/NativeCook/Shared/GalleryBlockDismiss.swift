import SwiftUI

private struct GalleryBlockDismissKey: EnvironmentKey {
    static let defaultValue: (() -> Void)? = nil
}

extension EnvironmentValues {
    var galleryBlockDismiss: (() -> Void)? {
        get { self[GalleryBlockDismissKey.self] }
        set { self[GalleryBlockDismissKey.self] = newValue }
    }
}
