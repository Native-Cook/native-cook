import SwiftUI
import UIKit

/// The nine stamp illustrations (420 x 420 JPEG), decoded once, plus a half-size copy for the cell
/// stamps (the texture is sampled bilinearly, so a smaller source keeps 44 pt stamps from shimmering).
@MainActor
enum StampDiaryCalendarArt {
    static let size = 420.0
    static let smallSize = 192.0

    private static let full: [Image] = (1...9).map { Image(uiImage: load($0, side: size)) }
    private static let small: [Image] = (1...9).map { Image(uiImage: load($0, side: smallSize)) }

    static func image(_ index: Int) -> Image { full[min(full.count - 1, max(0, index))] }
    static func smallImage(_ index: Int) -> Image { small[min(small.count - 1, max(0, index))] }

    private static func load(_ index: Int, side: Double) -> UIImage {
        guard let path = Bundle.main.path(forResource: "StampDiaryCalendar-stamp-\(String(format: "%02d", index))", ofType: "jpg"),
              let source = UIImage(contentsOfFile: path) else { return UIImage() }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let target = CGSize(width: side, height: side)
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            source.draw(in: CGRect(origin: .zero, size: target))
        }
    }

    /// The postmark texture for a day of May 2026, as an Image.
    static func postmark(day: Int) -> Image {
        if let hit = postmarks[day] { return hit }
        guard let cg = StampDiaryCalendarPostmark.image(day: day, month: 5, year: 2026) else { return blank }
        let image = Image(decorative: cg, scale: 1)
        postmarks[day] = image
        return image
    }

    private static var postmarks: [Int: Image] = [:]

    /// An empty 1 x 1 texture for stamps without a postmark.
    static let blank: Image = Image(uiImage: UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { _ in })
}
