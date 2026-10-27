import SwiftUI

/// The block as the gallery shows it: the guided loop, playing by itself. The launch argument `-previewTime <seconds>`
/// holds the loop at that time instead, so any frame of it can be looked at.
struct FolioFeatureLibraryDemo: View {
    var body: some View {
        FolioFeatureLibrary(autoPlay: true, previewTime: Self.previewTime)
    }

    private static var previewTime: Double? {
        let defaults = UserDefaults.standard
        if let number = defaults.object(forKey: "previewTime") as? Double { return number }
        if let text = defaults.string(forKey: "previewTime") { return Double(text) }
        return nil
    }
}

#Preview {
    FolioFeatureLibraryDemo()
}
