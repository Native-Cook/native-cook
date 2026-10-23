import SwiftUI
import UIKit

enum VelvetNapStudioTouchPhase { case began,moved,ended,cancelled }
struct VelvetNapStudioTouchSample { let id:UUID;let phase:VelvetNapStudioTouchPhase;let point:CGPoint;let date:Date }
struct VelvetNapStudioTouch:UIViewRepresentable {
    let enabled:Bool
    let generation:UUID
    let onSample:(VelvetNapStudioTouchSample)->Void
    func makeUIView(context:Context)->VelvetNapStudioTouchView { let v=VelvetNapStudioTouchView();v.backgroundColor = .clear;v.isOpaque=false;v.isMultipleTouchEnabled=true;v.isAccessibilityElement=false;v.accessibilityElementsHidden=true;return v }
    func updateUIView(_ view:VelvetNapStudioTouchView,context:Context) {
        if view.generation != generation || !enabled {view.invalidate(notify:false)}
        view.generation=generation;view.onSample=onSample;view.isUserInteractionEnabled=enabled
    }
    static func dismantleUIView(_ view:VelvetNapStudioTouchView,coordinator:()) {view.invalidate(notify:true);view.onSample=nil}
}
final class VelvetNapStudioTouchView:UIView {
    var generation:UUID?
    var onSample:((VelvetNapStudioTouchSample)->Void)?
    private var owner:UITouch?
    private var id:UUID?
    private var ignored=Set<ObjectIdentifier>()
    private var last=CGPoint.zero
    private let origin=CGPoint(x:31,y:109)
    private func logical(_ t:UITouch)->CGPoint {let p=t.location(in:self);return CGPoint(x:p.x-12,y:p.y-12)}
    override func point(inside point:CGPoint,with event:UIEvent?)->Bool {
        guard super.point(inside:point,with:event) else {return false}
        let p=CGPoint(x:point.x-12,y:point.y-12)
        let stroked=VelvetNapStudioResources.front.copy(strokingWithWidth:24,lineCap:.round,lineJoin:.round,miterLimit:2)
        return VelvetNapStudioResources.front.contains(p) || stroked.contains(p)
    }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard owner==nil else {for t in touches {ignored.insert(ObjectIdentifier(t))};return}
        guard let t=touches.filter({!ignored.contains(ObjectIdentifier($0))}).min(by:{$0.timestamp<$1.timestamp}) else{return}
        owner=t;id=UUID();last=logical(t)
        for other in touches where other !== t {ignored.insert(ObjectIdentifier(other))}
        emit(.began,last)
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let owner,touches.contains(where:{$0===owner}) else{return}
        let p=logical(owner);guard p != last else{return};last=p;emit(.moved,p)
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {finish(touches,false)}
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) {finish(touches,true)}
    private func finish(_ touches:Set<UITouch>,_ cancelled:Bool) {
        if let owner,touches.contains(where:{$0===owner}) {let p=cancelled ? last:logical(owner);emit(cancelled ? .cancelled:.ended,p);self.owner=nil;id=nil}
        for t in touches {ignored.remove(ObjectIdentifier(t))}
    }
    private func emit(_ phase:VelvetNapStudioTouchPhase,_ p:CGPoint) {if let id {onSample?(.init(id:id,phase:phase,point:p,date:Date()))}}
    func invalidate(notify:Bool) {if notify {emit(.cancelled,last)};owner=nil;id=nil;ignored.removeAll()}
    override func willMove(toWindow window:UIWindow?) {if window==nil {invalidate(notify:true)};super.willMove(toWindow:window)}
}
