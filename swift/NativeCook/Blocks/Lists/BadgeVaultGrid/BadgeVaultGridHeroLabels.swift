import UIKit

final class BadgeVaultGridHeroLabels: UIView {
    private let title = UILabel()
    private let close = UILabel()
    private let chip = UIView()
    private let chipText = UILabel()
    private let earned = UILabel()
    private let blurb = UILabel()
    private let statValues = (0..<3).map { _ in UILabel() }
    private let statLabels = (0..<3).map { _ in UILabel() }
    private let dividers = (0..<2).map { _ in UIView() }
    private let hint = UILabel()
    private let share = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        isOpaque = false
        for label in [title, close, chipText, earned, blurb, hint, share] + statValues + statLabels {
            label.textAlignment = .center
            label.backgroundColor = .clear
            label.isAccessibilityElement = false
            addSubview(label)
        }
        chip.layer.cornerRadius = 14
        chip.layer.borderWidth = 0.5
        chip.addSubview(chipText)
        addSubview(chip)
        bringSubviewToFront(chip)
        for divider in dividers {
            divider.backgroundColor = UIColor.white.withAlphaComponent(0.12)
            addSubview(divider)
        }
        title.textColor = .white
        title.adjustsFontSizeToFitWidth = true
        title.minimumScaleFactor = 0.7
        close.text = "×"
        close.textColor = UIColor.white.withAlphaComponent(0.92)
        close.font = .systemFont(ofSize: 19, weight: .light)
        earned.textColor = UIColor.white.withAlphaComponent(0.6)
        earned.font = .systemFont(ofSize: 15)
        blurb.textColor = UIColor.white.withAlphaComponent(0.78)
        blurb.font = .systemFont(ofSize: 15)
        blurb.numberOfLines = 2
        hint.textColor = UIColor.white.withAlphaComponent(0.38)
        hint.font = .systemFont(ofSize: 12)
        share.font = .systemFont(ofSize: 17, weight: .semibold)
        for index in 0..<3 {
            statValues[index].textColor = .white
            statValues[index].font = .systemFont(ofSize: 20, weight: .semibold)
            statValues[index].adjustsFontSizeToFitWidth = true
            statValues[index].minimumScaleFactor = 0.8
            statLabels[index].textColor = UIColor.white.withAlphaComponent(0.38)
            statLabels[index].font = .systemFont(ofSize: 12)
        }
        isHidden = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(_ engine: BadgeVaultGridEngine) {
        let hero = engine.hero
        guard hero.index >= 0 else {
            isHidden = true
            return
        }
        isHidden = false
        let layout = engine.layout
        let badge = BadgeVaultGridBadge.all[hero.index]
        title.text = badge.name
        title.font = .systemFont(ofSize: layout.titleSize, weight: .bold)
        title.frame = CGRect(x: 0, y: 0, width: layout.width, height: 38)
        title.center = CGPoint(x: hero.nameX, y: hero.nameY)
        title.transform = CGAffineTransform(scaleX: hero.nameScale, y: hero.nameScale)
        title.alpha = hero.alpha

        position(close, x: 16, y: layout.safeTop + 4, width: 36, height: 36,
                 hero: hero, at: 520, reduced: engine.reduced)
        let accent = UIColor(red: CGFloat(badge.a.x), green: CGFloat(badge.a.y),
                             blue: CGFloat(badge.a.z), alpha: 1)
        chipText.attributedText = NSAttributedString(
            string: "\(badge.rarity)% OF MEMBERS HOLD THIS",
            attributes: [.font: UIFont.systemFont(ofSize: 11, weight: .semibold),
                         .kern: 0.9,
                         .foregroundColor: accent.blended(with: .white, amount: 0.35)])
        let chipWidth = min(layout.width - 48, chipText.intrinsicContentSize.width + 24)
        chip.frame = CGRect(x: (layout.width - chipWidth) / 2, y: layout.chipY,
                            width: chipWidth, height: 28)
        chipText.frame = chip.bounds
        chip.backgroundColor = accent.withAlphaComponent(0.14)
        chip.layer.borderColor = accent.withAlphaComponent(0.35).cgColor
        reveal(chip, hero: hero, at: 520, reduced: engine.reduced,
               baseY: layout.chipY)

        earned.text = "Earned \(badge.earned ?? BadgeVaultGridBadge.unlockDate)"
        position(earned, x: 24, y: layout.earnedY, width: layout.width - 48, height: 20,
                 hero: hero, at: 640, reduced: engine.reduced)
        blurb.text = badge.blurb
        position(blurb, x: (layout.width - 300) / 2, y: layout.blurbY, width: 300, height: 44,
                 hero: hero, at: 700, reduced: engine.reduced)
        for index in 0..<3 {
            let x = 32 + (layout.width - 64) * Double(index) / 3
            let width = (layout.width - 64) / 3
            let stat = badge.stats[index]
            if (badge.id == "iron-month" || badge.id == "crowned") && index < 2 {
                statValues[index].text = String(index == 0 ? engine.cells[hero.index].have :
                    max(0, badge.need - engine.cells[hero.index].have))
            } else {
                statValues[index].text = stat.0
            }
            statLabels[index].text = stat.1
            position(statValues[index], x: x + 4, y: layout.statsY + 6,
                     width: width - 8, height: 26, hero: hero,
                     at: 760 + Double(index) * 40, reduced: engine.reduced)
            position(statLabels[index], x: x + 4, y: layout.statsY + 31,
                     width: width - 8, height: 16, hero: hero,
                     at: 760 + Double(index) * 40, reduced: engine.reduced)
            if index > 0 {
                let divider = dividers[index - 1]
                position(divider, x: x, y: layout.statsY + 6, width: 0.5, height: 44,
                         hero: hero, at: 760 + Double(index) * 40, reduced: engine.reduced)
            }
        }
        hint.text = engine.reduced ? "Swipe down to put it back" :
            "Drag to turn  ·  Swipe down to put it back"
        hint.isHidden = layout.compact
        if !layout.compact {
            position(hint, x: 16, y: 724, width: layout.width - 32, height: 16,
                     hero: hero, at: 900, reduced: engine.reduced)
        }
        share.text = "Share badge"
        share.textColor = enamelTextColor(badge.a)
        position(share, x: 24, y: layout.shareY, width: layout.width - 48, height: 56,
                 hero: hero, at: 960, reduced: engine.reduced)
    }

    private func position(_ view: UIView, x: Double, y: Double, width: Double, height: Double,
                          hero: BadgeVaultGridHero, at: Double, reduced: Bool) {
        view.frame = CGRect(x: x, y: y, width: width, height: height)
        reveal(view, hero: hero, at: at, reduced: reduced, baseY: y)
    }

    private func reveal(_ view: UIView, hero: BadgeVaultGridHero,
                        at: Double, reduced: Bool, baseY: Double) {
        let age = hero.age - (reduced ? 0 : at)
        view.alpha = BadgeVaultGridMath.clamp(age / (reduced ? 200 : 240)) * hero.details
        let offset = reduced ? 0 : 12 * (1 - BadgeVaultGridMath.ease(age / 420))
        view.frame.origin.y = baseY + offset
    }

    private func enamelTextColor(_ rgb: SIMD3<Float>) -> UIColor {
        func linear(_ channel: Float) -> Double {
            let x = Double(channel)
            return x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4)
        }
        let luminance = 0.2126 * linear(rgb.x) + 0.7152 * linear(rgb.y) + 0.0722 * linear(rgb.z)
        return luminance > 0.45 ? UIColor(red: 0.043, green: 0.043, blue: 0.047, alpha: 1) : .white
    }
}

private extension UIColor {
    func blended(with other: UIColor, amount: CGFloat) -> UIColor {
        var r1: CGFloat = 0
        var g1: CGFloat = 0
        var b1: CGFloat = 0
        var a1: CGFloat = 0
        var r2: CGFloat = 0
        var g2: CGFloat = 0
        var b2: CGFloat = 0
        var a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return UIColor(red: r1 + (r2 - r1) * amount,
                       green: g1 + (g2 - g1) * amount,
                       blue: b1 + (b2 - b1) * amount, alpha: 1)
    }
}
