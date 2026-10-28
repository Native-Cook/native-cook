import UIKit

@MainActor
final class PrismaticGameProfileCarousel:UIControl {
    var increment:(()->Void)?;var decrement:(()->Void)?;var activate:(()->Void)?
    override init(frame:CGRect){super.init(frame:frame);isAccessibilityElement=true;accessibilityTraits=[.adjustable,.button];backgroundColor = .clear}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func accessibilityIncrement(){increment?()}
    override func accessibilityDecrement(){decrement?()}
    override func accessibilityActivate()->Bool {activate?();return true}
}
