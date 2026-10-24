import SwiftUI
import UIKit

/// Small primitives adapted from Native Cook's Gleam Line Intro and Thinking Word Shimmer.
/// Both accents read the scene clock; no independent timers or repeating animations.
enum BuzzStreaksTextMotionMath {
    static func clamp(_ x:Double)->Double {min(1,max(0,x))}
    static func smooth(_ x:Double)->Double {let p=clamp(x);return p*p*(3-2*p)}
    static func easeOut(_ x:Double)->Double {let p=clamp(x);return 1-pow(1-p,3)}
    static func word(_ time:Double,_ start:Double,_ index:Int)->(alpha:Double,blur:Double,dy:Double) {
        let p=(time-start-Double(index)*0.035)/0.24
        return (smooth(p/0.6),3*(1-easeOut(p/0.8)),4*(1-easeOut(p)))
    }
    static func sweep(_ time:Double,start:Double)->Double? {
        let p=(time-start)/0.7
        guard p>0,p<1 else {return nil}
        return -0.35+1.7*smooth(p)
    }
    static func gradient(_ center:Double,peak:Double)->LinearGradient {
        LinearGradient(stops:[.init(color:.clear,location:0),.init(color:.clear,location:0.2),
            .init(color:.white.opacity(peak),location:0.5),.init(color:.clear,location:0.8),.init(color:.clear,location:1)],
            startPoint:UnitPoint(x:center-0.5,y:0.5),endPoint:UnitPoint(x:center+0.5,y:0.5))
    }
}

final class BuzzStreaksTextMotion {
    private var clips:[String:[CGRect]]=[:]
    private func wordClips(_ text:String)->[CGRect] {
        if let found=clips[text] {return found}
        guard let font=UIFont(name:"Gilroy-SemiBold",size:19.6) else {
            preconditionFailure("Register the bundled Gilroy font before text motion.")
        }
        let attributes:[NSAttributedString.Key:Any]=[.font:font]
        func width(_ s:String)->Double {Double((s as NSString).size(withAttributes:attributes).width)}
        let full=width(text),left=180-full/2
        let words=text.split(separator:" ").map(String.init)
        var prefix="",rects:[CGRect]=[],boundary=left-6
        for (index,word) in words.enumerated() {
            let end=index==words.count-1 ? left+full+6 : left+width(prefix+word)+width(" ")/2
            rects.append(CGRect(x:boundary,y:-6,width:end-boundary,height:36))
            boundary=end
            prefix+=word+(index<words.count-1 ? " " : "")
        }
        clips[text]=rects
        return rects
    }
    func drawCaption(_ text:String,time:Double,start:Double,animate:Bool,context:inout GraphicsContext) {
        let words=wordClips(text)
        let content=Text(text).font(.custom("Gilroy-SemiBold",size:19.6)).foregroundStyle(.white)
        if !animate || time>=start+Double(words.count-1)*0.035+0.24 {
            context.draw(content,at:CGPoint(x:180,y:12));return
        }
        for (index,clip) in words.enumerated() {
            let look=BuzzStreaksTextMotionMath.word(time,start,index)
            guard look.alpha>0.001 else {continue}
            var c=context
            c.clip(to:Path(clip));c.opacity=look.alpha
            if look.blur>0.01 {c.addFilter(.blur(radius:look.blur))}
            c.translateBy(x:0,y:look.dy)
            c.draw(content,at:CGPoint(x:180,y:12))
        }
    }
}
