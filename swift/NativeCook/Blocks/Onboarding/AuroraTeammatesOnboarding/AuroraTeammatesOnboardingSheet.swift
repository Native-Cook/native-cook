import SwiftUI

// ato-sheet.ts: the sheet, title, subtitle and dot poses are pure scene projections.
enum AuroraTeammatesOnboardingSheet {
    typealias M = AuroraTeammatesOnboardingMotion
    typealias GL = M.GL
    typealias L = AuroraTeammatesOnboardingTheme.Layout
    struct TextLayout {
        var width: [Double]
        var x: [[Double]]
        var y: [[Double]]
        var line: [[Int]]
        var subX: [[Double]]
    }
    static func layoutTitle(_ l:L,_ measure:(String,Double,Double,Bool)->Double) -> TextLayout {
        let h=AuroraTeammatesOnboardingTheme.self
        var t=TextLayout(width:h.tokens.map { measure($0,l.titleSize,l.titleTracking,true) },x:Array(repeating:Array(repeating:-1,count:3),count:8),y:Array(repeating:Array(repeating:-1,count:3),count:8),line:Array(repeating:Array(repeating:0,count:3),count:8),subX:[])
        for p in 0..<3 {
            for (line,tokens) in h.lines[p].enumerated() {
                let text=tokens.map { h.tokens[$0] }.joined(separator:" ")
                let left=l.cx-measure(text,l.titleSize,l.titleTracking,true)/2
                var prefix=""
                for i in tokens {
                    t.x[i][p]=left+(prefix.isEmpty ? 0:measure(prefix,l.titleSize,l.titleTracking,true))
                    t.y[i][p]=l.titleTop+l.titleCap+Double(line)*l.titleStep;t.line[i][p]=line
                    prefix+=h.tokens[i]+" "
                }
            }
        }
        t.subX=h.subtitles.map { lines in lines.map { l.cx-measure($0,l.subtitleSize,0,false)/2 } }
        return t
    }
    static func sheetPath(_ top:Double,_ dip:Double,_ cx:Double,_ l:L,_ closed:Bool) -> Path {
        func n(_ v:Double)->Double { (v*100).rounded()/100 }
        func pt(_ x:Double,_ y:Double)->CGPoint { CGPoint(x:n(x),y:n(y)) }
        let r=l.sheetCorner
        var d=Path()
        if closed { d.move(to:pt(0,l.H+2));d.addLine(to:pt(0,top+r)) } else { d.move(to:pt(0,top+r)) }
        d.addQuadCurve(to:pt(r,top),control:pt(0,top));d.addLine(to:pt(cx-l.half,top))
        d.addCurve(to:pt(cx,top+dip),control1:pt(cx-l.half+l.c1,top),control2:pt(cx-l.c2,top+dip))
        d.addCurve(to:pt(cx+l.half,top),control1:pt(cx+l.c2,top+dip),control2:pt(cx+l.half-l.c1,top))
        d.addLine(to:pt(l.W-r,top));d.addQuadCurve(to:pt(l.W,top+r),control:pt(l.W,top))
        if closed { d.addLine(to:pt(l.W,l.H+2));d.closeSubpath() }
        return d
    }
    static func pillPath(_ w:Double,_ h:Double,_ amp:Double,_ ph:Double) -> Path {
        let r=h/2,ls=w-h,per=2*ls+2 * .pi*r
        var d=Path()
        for k in 0..<160 {
            let s=Double(k)/160*per
            var x=0.0,y=0.0,nx=0.0,ny=0.0
            if s<ls { x = -ls/2+s;y = -r;ny = -1 }
            else if s<ls + .pi*r { let a = -.pi/2+(s-ls)/r;nx=cos(a);ny=sin(a);x=ls/2+r*nx;y=r*ny }
            else if s<2*ls + .pi*r { x=ls/2-(s-ls - .pi*r);y=r;ny=1 }
            else { let a = .pi/2+(s-2*ls - .pi*r)/r;nx=cos(a);ny=sin(a);x = -ls/2+r*nx;y=r*ny }
            let t=2 * .pi*s/per,off=amp*(0.62*sin(3*t+ph)+0.38*sin(5*t-1.3*ph))
            let p=CGPoint(x:((x+nx*off)*100).rounded()/100,y:((y+ny*off)*100).rounded()/100)
            if k==0 { d.move(to:p) } else { d.addLine(to:p) }
        }
        d.closeSubpath();return d
    }
    static func tokenState(_ t:TextLayout,_ i:Int,_ sc:[Double],_ reduced:Bool) -> [Double] {
        let from=Int(sc[GL.from]),to=Int(sc[GL.to]),u=sc[GL.u],top=sc[GL.sheetTop],xs=t.x[i],ys=t.y[i]
        let inA=xs[from]>=0,inB=xs[to]>=0
        if u<=0 || (!inA && !inB) { return [inA ? xs[from]:0,inA ? top+ys[from]:0,inA && u<=0 ? 1:0,0] }
        if inA && inB {
            let g=reduced ? M.smooth(0,1,u):M.spring(M.clamp01((u-0.16)/0.6),0.8,10)
            return [M.mix(xs[from],xs[to],g),top+M.mix(ys[from],ys[to],g),1,0]
        }
        if inA {
            let q=reduced ? M.smooth(0,0.45,u):M.smooth(0,0.22,u-0.03*Double(t.line[i][from]))
            return [xs[from],top+ys[from]-(reduced ? 0:10*q),1-q,reduced ? 0:7*q]
        }
        let q=reduced ? M.smooth(0.5,1,u):M.clamp01((u-0.26-0.07*Double(t.line[i][to]))/0.36)
        let e=reduced ? q:M.spring(q,0.8,10)
        return [xs[to],top+ys[to]+(reduced ? 0:12*(1-e)),reduced ? q:M.smooth(0,0.7,q),reduced ? 0:7*(1-M.smooth(0,0.8,q))]
    }
    static func glintState(_ t:TextLayout,_ i:Int,_ sc:[Double],_ reduced:Bool) -> [Double] {
        let u=sc[GL.u],here=t.x[i][Int(sc[GL.to])]>=0 && u>0 && !reduced,g=here ? M.clamp01((u-0.56)/0.4):0
        return [-0.4*t.width[i]+1.8*t.width[i]*g,here ? sin(.pi*g):0]
    }
    static func subtitleState(_ p:Int,_ sc:[Double],_ l:L,_ reduced:Bool) -> [Double] {
        let from=Int(sc[GL.from]),to=Int(sc[GL.to]),u=sc[GL.u],base=sc[GL.sheetTop]+l.subtitleBase[p]
        if p==from {
            let q=u<=0 ? 0:reduced ? M.smooth(0,0.45,u):M.smooth(0.02,0.3,u)
            return [base-(reduced ? 0:6*q),1-q,reduced ? 0:5*q]
        }
        if p==to && u>0 {
            let q=reduced ? M.smooth(0.5,1,u):M.clamp01((u-0.42)/0.38)
            return [base+(reduced ? 0:8*(1-M.spring(q,0.8,10))),reduced ? q:M.smooth(0,0.8,q),reduced ? 0:5*(1-q)]
        }
        return [base,0,0]
    }
    static func dotState(_ i:Int,_ sc:[Double],_ l:L) -> [Double] {
        func weight(_ i:Int)->Double { max(0,1-abs(sc[GL.dotPos]-Double(i))) }
        var x=l.pad
        for k in 0..<i { x+=l.dot+(l.pillW-l.dot)*weight(k)+l.gap }
        let w=weight(i)
        return [x,l.dot+(l.pillW-l.dot)*w,l.dot+(l.pillH-l.dot)*w,w]
    }
    static func trackWidth(_ l:L)->Double { 2*l.pad+4*l.dot+(l.pillW-l.dot)+3*l.gap }
}
