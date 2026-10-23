import SwiftUI

// The gallery's own look. Blocks bring their colours with them and don't read these.
enum GalleryColors {
    static let background = Color(red: 18 / 255, green: 18 / 255, blue: 16 / 255)
    static let surface = Color(red: 28 / 255, green: 28 / 255, blue: 25 / 255)
    static let surfacePressed = Color(red: 38 / 255, green: 38 / 255, blue: 34 / 255)
    static let text = Color(red: 244 / 255, green: 243 / 255, blue: 236 / 255)
    static let textMuted = Color(red: 154 / 255, green: 153 / 255, blue: 143 / 255)
    static let accent = Color(red: 255 / 255, green: 244 / 255, blue: 141 / 255)
    static let border = text.opacity(0.1)
}
