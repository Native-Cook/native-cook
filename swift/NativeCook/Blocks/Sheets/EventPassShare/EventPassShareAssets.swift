import SwiftUI
import UIKit

@MainActor
enum EventPassShareAssets {
    static let portraitStrip = loadImage(name: "EventPassShare-poster-strip", fileExtension: "jpg")
    static let qrCode = loadImage(name: "EventPassShare-qr-pass", fileExtension: "png")

    private static func loadImage(name: String, fileExtension: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: fileExtension),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
