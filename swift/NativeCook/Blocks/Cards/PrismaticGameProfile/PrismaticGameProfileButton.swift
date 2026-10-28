import UIKit

@MainActor
final class PrismaticGameProfileButton: UIButton {
    var reduced=false
    var action:(()->Void)?
    private var animator:UIViewPropertyAnimator?
    private var materialIcon:PrismaticGameProfileIcon?
    private var iconSize=0.0,iconGap=0.0
    override var isHighlighted:Bool {didSet {
        animator?.stopAnimation(true)
        let changes={self.transform=self.isHighlighted && !self.reduced ? CGAffineTransform(scaleX:0.97,y:0.97):.identity;self.alpha=self.isEnabled ? (self.isHighlighted && self.reduced ? 0.78:1):0.35}
        animator=UIViewPropertyAnimator(duration:0.12,timingParameters:UICubicTimingParameters(controlPoint1:CGPoint(x:0.23,y:1),controlPoint2:CGPoint(x:0.32,y:1)))
        animator?.addAnimations(changes);animator?.startAnimation()
    }}
    func setMaterialIcon(_ name:String,size:Double,gap:Double=0,color:UIColor){
        configuration=nil;setImage(nil,for:.normal);materialIcon?.removeFromSuperview()
        let icon=PrismaticGameProfileIcon(name,size:size,color:color);materialIcon=icon;iconSize=size;iconGap=gap;addSubview(icon);setNeedsLayout()
    }
    override func layoutSubviews(){
        super.layoutSubviews()
        if accessibilityLabel?.isEmpty == false {for case let label as UILabel in subviews {label.isAccessibilityElement=false;label.accessibilityElementsHidden=true}}
        guard let materialIcon else{return}
        let title=title(for:.normal) ?? "",size=titleLabel?.sizeThatFits(CGSize(width:1000,height:1000)) ?? .zero
        let width=title.isEmpty ? 0:size.width,gap=title.isEmpty ? 0:iconGap,total=iconSize+gap+width,left=(bounds.width-total)/2
        materialIcon.frame=CGRect(x:left,y:(bounds.height-iconSize)/2,width:iconSize,height:iconSize)
        if !title.isEmpty{titleLabel?.frame=CGRect(x:left+iconSize+gap,y:(bounds.height-size.height)/2,width:width,height:size.height)}
    }
    override init(frame:CGRect){super.init(frame:frame);addTarget(self,action:#selector(activate),for:.touchUpInside);accessibilityTraits = .button;isAccessibilityElement=true}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    @objc private func activate(){action?()}
}
