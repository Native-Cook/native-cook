import UIKit

@MainActor
enum OwlAlarmRulerAssets {
    static let mascot = image("OwlAlarmRuler-mascot")
    static let pupil = image("OwlAlarmRuler-mascot-pupil")

    private static func image(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: "png"),
              let source = UIImage(contentsOfFile: path) else { return UIImage() }
        return source.preparingForDisplay() ?? source
    }
}
