import CoreText
import UIKit

enum FindFriendsInviteAssets {
    private static let prepared: Void = {
        for weight in ["Medium", "SemiBold", "Bold", "ExtraBold"] {
            guard let url = Bundle.main.url(forResource: "FindFriendsInvite-Anybody-\(weight)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func prepare() { _ = prepared }

    private static let images: [String: UIImage] = [
        "cup": load("FindFriendsInvite-cup"),
        "head-1": load("FindFriendsInvite-head-1"),
        "head-2": load("FindFriendsInvite-head-2"),
        "head-3": load("FindFriendsInvite-head-3"),
        "head-5": load("FindFriendsInvite-head-5")
    ]

    static func image(_ key: String) -> UIImage { images[key] ?? UIImage() }

    private static func load(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: "png"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
