import UIKit

final class MetacardUnifyFanAccessibilityControl: UIAccessibilityElement {
    var activate: (() -> Void)?

    override func accessibilityActivate() -> Bool {
        activate?()
        return true
    }
}
