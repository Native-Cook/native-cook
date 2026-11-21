import SwiftUI
import UIKit
import CoreText

/// Tabular rolling columns preserve each card's own value while it lands, including full turns of unchanged digits.
struct TumbleCardRoutineNumber:View {
    let value:String, unit:String
    let reveal:Double, delay:Double
    private var typeface:UIFont { TumbleCardRoutineAssets.font(40,true) }
    private var cell:Double { (0...9).map { ("\($0)" as NSString).size(withAttributes:[.font:typeface]).width }.max()! - 1.2 }
    var body:some View {
        HStack(spacing:0) {
            ForEach(Array(value.enumerated()),id:\.offset) { index,ch in
                let e=TumbleCardRoutineMotion.out(TumbleCardRoutineMotion.seg(reveal-delay,Double(index)*0.045,Double(index)*0.045+0.42))
                let width=ch == "." ? ("." as NSString).size(withAttributes:[.font:typeface]).width-1.2 : cell
                ZStack {
                    if ch == "." { Text(".") }
                    else if let digit=Int(String(ch)) {
                        let q=e*10, k=Int(floor(q)), f=q-floor(q)
                        Text(String((digit+k)%10)).offset(y:-f*40)
                        if e>0 && e<1 { Text(String((digit+k+1)%10)).offset(y:(1-f)*40) }
                    }
                }.font(Font(typeface as CTFont)).frame(width:width,height:42).clipped()
            }
            Text(unit).font(Font(typeface as CTFont)).tracking(-1.2).padding(.leading,8)
        }.fixedSize()
    }
}

/// Fixed 900-point perspective, independent of the card's width and device scale.
struct TumbleCardRoutinePerspective:GeometryEffect {
    var angle:Double
    var animatableData:Double { get { angle } set { angle=newValue } }
    func effectValue(size:CGSize)->ProjectionTransform {
        var transform=CATransform3DIdentity
        transform.m34 = -1.0/900
        transform=CATransform3DRotate(transform,angle*Double.pi/180,1,0,0)
        transform=CATransform3DTranslate(transform,-size.width/2,-size.height/2,0)
        return ProjectionTransform(transform).concatenating(ProjectionTransform(CGAffineTransform(translationX:size.width/2,y:size.height/2)))
    }
}
