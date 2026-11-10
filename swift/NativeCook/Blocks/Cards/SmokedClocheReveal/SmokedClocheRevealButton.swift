import UIKit

final class SmokedClocheRevealButton: UIButton {
  var reduced=false
  var pressScale=0.96
  override var isHighlighted: Bool {
    didSet {
      let curve=UICubicTimingParameters(controlPoint1:CGPoint(x:0.23,y:1),controlPoint2:CGPoint(x:0.32,y:1))
      let a=UIViewPropertyAnimator(duration:0.15,timingParameters:curve)
      a.addAnimations {
        self.transform=self.isHighlighted && !self.reduced ? CGAffineTransform(scaleX:self.pressScale,y:self.pressScale) : .identity
        self.alpha=self.isHighlighted && self.reduced ? 0.72 : 1
      }
      a.startAnimation()
    }
  }
}
