import SwiftUI
import UIKit

struct FirstLootRewardTouch: UIViewRepresentable {
    let top:Double, bottom:Double
    let clock:FirstLootRewardClock
    func makeUIView(context:Context) -> FirstLootRewardTouchSurface { FirstLootRewardTouchSurface() }
    func updateUIView(_ view:FirstLootRewardTouchSurface,context:Context) { view.top=top; view.bottom=bottom; view.clock=clock }
}
final class FirstLootRewardTouchSurface:UIView {
    var top=0.0, bottom=0.0
    weak var clock:FirstLootRewardClock?
    private var target=0, start=CGPoint.zero, began=0.0, exceeded=false
    override init(frame:CGRect) { super.init(frame:frame); isMultipleTouchEnabled=false; backgroundColor = .clear }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch=touches.first else { return }; start=touch.location(in:self); began=touch.timestamp; exceeded=false
        let w=bounds.width, btnY=bounds.height-bottom-68, bx=w/2, by=top+182
        target = abs(start.x-bx)<=110 && abs(start.y-by)<=110 ? 1 : 0
        if start.y >= btnY && start.y <= btnY+52 && start.x >= 20 && start.x <= w-20 {
            let bw=(w-48)/2
            if start.x <= 20+bw { target=2 } else if start.x >= 28+bw { target=3 }
        }
        if target != 0 { clock?.begin(target) }
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch=touches.first else { return }; let p=touch.location(in:self)
        if hypot(p.x-start.x,p.y-start.y)>10 { exceeded=true }
        if target == 1 { clock?.move(p.x-start.x,p.y-start.y) }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let touch=touches.first else { return }
        clock?.end(target,!exceeded && touch.timestamp-began<=2); target=0
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { clock?.end(target,false); target=0 }
}
