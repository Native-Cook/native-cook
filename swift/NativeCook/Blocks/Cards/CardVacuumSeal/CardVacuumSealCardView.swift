import UIKit

/// Native BankCard snapshot, including the same CALayer continuous clipping used by RN.
@MainActor
final class CardVacuumSealCardView: UIView {
    init(pixelScale: Double) {
        super.init(frame: CGRect(x: 0,y: 0,width: 354,height: 223))
        isOpaque = false; clipsToBounds = true
        layer.cornerRadius = 16; layer.cornerCurve = .continuous; layer.contentsScale = pixelScale
        let gradient = CAGradientLayer()
        gradient.frame = bounds
        gradient.colors = [0x171C2E,0x0D111F,0x06080F].map { CardVacuumSealResources.uiColor(UInt32($0)).cgColor }
        gradient.locations = [0,0.5,1]
        gradient.startPoint = CGPoint(x: -0.0089,y: 0.6083)
        gradient.endPoint = CGPoint(x: 1.0088,y: 0.3917)
        layer.addSublayer(gradient)
        for (text,size,tracking,top,alpha) in [("Halden",28.0,-0.02,22.0,0.92),("NORTH.STUDIO",18.0,0.04,181.0,0.8)] {
            let font = CardVacuumSealResources.font(size)
            let label = UILabel(frame: CGRect(x: 20,y: top,width: 334,height: font.lineHeight))
            label.isOpaque = false; label.backgroundColor = .clear; label.font = font
            label.layer.contentsScale = pixelScale
            label.attributedText = NSAttributedString(string: text,attributes: [.font: font,.kern: size*tracking,
                .foregroundColor: CardVacuumSealResources.uiColor(0xE9ECF4,alpha)])
            addSubview(label)
        }
        layoutIfNeeded()
        // This offscreen view never gets a display pass before the atlas snapshot.
        // Layout alone leaves UILabel backing layers empty when CALayer.render reads them.
        for label in subviews {
            label.layer.displayIfNeeded()
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}
