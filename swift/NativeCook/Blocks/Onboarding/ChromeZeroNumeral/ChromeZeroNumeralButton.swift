import UIKit

final class ChromeZeroNumeralButton: ChromeZeroNumeralAccessibleView {
    private let surface = UIView()
    private let fill = CAGradientLayer()
    private let sheen = CAGradientLayer()
    private let chip = UIView()
    private let chipFill = CAGradientLayer()
    private let arrow = UIImageView(image: UIImage(named: "chrome-zero-arrow"))
    private let label = UILabel()
    private let topEdge = CALayer()
    private let chipEdge = CALayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = true
        accessibilityLabel = "Start now"
        accessibilityHint = "Turns the chrome numeral once."
        accessibilityTraits = .button
        surface.layer.cornerRadius = 16
        surface.clipsToBounds = true
        surface.isUserInteractionEnabled = false
        addSubview(surface)
        fill.colors = [colour(0xDCE4FF).cgColor, colour(0xA9BAFF).cgColor]
        surface.layer.addSublayer(fill)
        surface.layer.borderWidth = 1
        surface.layer.borderColor = colour(0xDCE4FF, 0.25).cgColor
        topEdge.backgroundColor = UIColor.white.withAlphaComponent(0.7).cgColor
        surface.layer.addSublayer(topEdge)
        sheen.colors = [UIColor.white.withAlphaComponent(0).cgColor,
                        UIColor.white.withAlphaComponent(0.9).cgColor,
                        UIColor.white.withAlphaComponent(0).cgColor]
        sheen.startPoint = CGPoint(x: 0, y: 0.5)
        sheen.endPoint = CGPoint(x: 1, y: 0.5)
        surface.layer.addSublayer(sheen)
        label.text = "Start now"
        label.textAlignment = .center
        label.textColor = colour(0x0A1038)
        surface.addSubview(label)
        chip.layer.cornerRadius = 12
        chip.clipsToBounds = true
        chip.layer.borderWidth = 1
        chip.layer.borderColor = UIColor.white.withAlphaComponent(0.13).cgColor
        chipFill.colors = [colour(0x8EA2FF).cgColor, colour(0x6E84F5).cgColor]
        chip.layer.addSublayer(chipFill)
        chipEdge.backgroundColor = UIColor.white.withAlphaComponent(0.45).cgColor
        chip.layer.addSublayer(chipEdge)
        arrow.contentMode = .scaleAspectFit
        chip.addSubview(arrow)
        surface.addSubview(chip)
        layer.shadowColor = colour(0x3D4BFF).cgColor
    }
    required init?(coder: NSCoder) { fatalError("Use init(frame:)") }

    func configure(frame f: ChromeZeroNumeralFrame, typeScale: Double, reduced: Bool) {
        surface.frame = bounds
        fill.frame = bounds
        label.frame = bounds
        label.font = .systemFont(ofSize: 17 * typeScale, weight: .semibold)
        topEdge.frame = CGRect(x: 16, y: 0, width: max(0, bounds.width - 32), height: 1)
        let size = bounds.height - 16
        chip.transform = .identity
        chip.frame = CGRect(x: bounds.width - size - 8, y: 8, width: size, height: size)
        chipFill.frame = chip.bounds
        chipEdge.frame = CGRect(x: 12, y: 0, width: max(0, size - 24), height: 1)
        arrow.frame = CGRect(x: (size - 20) / 2, y: (size - 20) / 2, width: 20, height: 20)
        chip.transform = CGAffineTransform(translationX: reduced ? 0 : f.arrow, y: reduced ? 0 : -f.arrow)
        sheen.bounds = CGRect(x: 0, y: 0, width: 56, height: 128)
        sheen.position = CGPoint(x: ((f.sweep + 1) / 2) * (bounds.width + 160) - 100 + 28, y: 32)
        sheen.setAffineTransform(CGAffineTransform(rotationAngle: -22 * .pi / 180))
        sheen.opacity = abs(f.sweep) >= 0.999 ? 0 : 0.46
        layer.shadowOpacity = Float(0.35 - 0.09 * f.buttonPress)
        layer.shadowRadius = 24 - 8 * f.buttonPress
        layer.shadowOffset = CGSize(width: 0, height: 8 - 4 * f.buttonPress)
        layer.shadowPath = UIBezierPath(roundedRect: bounds, cornerRadius: 16).cgPath
    }
    private func colour(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: alpha)
    }
}
