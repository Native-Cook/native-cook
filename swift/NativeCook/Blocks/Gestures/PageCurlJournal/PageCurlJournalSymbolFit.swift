import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// The React Native block draws its two SF Symbols with expo-symbols: the symbol is configured at a point size and
/// an image view then scales it (scaleAspectFit) into a square of the same size. This gives the font size that
/// reproduces that drawing, so the ink is the same size on screen.
enum PageCurlJournalSymbolFit {
    @MainActor
    static func fontSize(_ name: String, pointSize: CGFloat, medium: Bool, box: CGFloat) -> CGFloat {
        #if canImport(UIKit)
        let configuration = UIImage.SymbolConfiguration(pointSize: pointSize, weight: medium ? .medium : .regular)
        if let size = UIImage(systemName: name, withConfiguration: configuration)?.size, size.width > 0, size.height > 0 {
            return pointSize * min(box / size.width, box / size.height)
        }
        #endif
        return pointSize
    }
}
