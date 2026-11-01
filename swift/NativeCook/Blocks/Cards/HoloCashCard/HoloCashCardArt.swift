import SwiftUI

struct HoloCashCardFace: View {
    let l: HoloCashCardLayout
    let s: HoloCashCardMotion.State
    var body: some View {
        let r=HoloCashCardResources.shared
        let tx=s.rx * .pi/180, ty=s.ry * .pi/180
        let word=r.word(l.cw,l.ch)
        let size=Shader.Argument.float2(Float(l.cw),Float(l.ch))
        let tilt=Shader.Argument.float2(Float(tx),Float(ty))
        ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:16).fill(HoloCashCardResources.color(0x0b0b0d))
            RoundedRectangle(cornerRadius:16).fill(.white)
                .colorEffect(ShaderLibrary.holoCashCardLines(size,tilt,.float(Float(s.boostK))))
            Canvas { c,_ in c.stroke(r.rim(l.cw,l.ch),with:.color(.white),lineWidth:1.5) }
                .colorEffect(ShaderLibrary.holoCashCardRim(size,tilt)).opacity(0.5+0.3*s.boostK)
            Canvas { c,_ in c.fill(word.0,with:.color(.white)) }
                .colorEffect(ShaderLibrary.holoCashCardFoil(size,tilt,.float(Float(s.boostK)),.float(Float(word.1))))
            Canvas { c,_ in
                r.baseline(c,"YOUR BANK",14,l.ch-26,10)
                r.baseline(c,"WON'T DO THIS",14,l.ch-14,10)
                let w=r.cardLabel("DEBIT").width
                r.baseline(c,"DEBIT",l.cw-14-w,l.ch-14,10)
            }.colorMultiply(.white)
        }
        .frame(width:l.cw,height:l.ch,alignment:.topLeading)
        .drawingGroup(opaque:false,colorMode:.nonLinear)
        .projectionEffect(projection(tx,ty))
    }
    private func projection(_ x: Double, _ y: Double) -> ProjectionTransform {
        // T(center) · perspective(700) · rotateX · rotateY · T(-center).
        let cx=l.cw/2, cy=l.ch/2, zx = -cos(x)*sin(y), zy=sin(x)
        let a=cos(y), b=0.0, d=sin(x)*sin(y), e=cos(x)
        let w0=1+(zx*cx+zy*cy)/700
        var p=ProjectionTransform(CGAffineTransform.identity)
        p.m11=a-cx*zx/700; p.m21=b-cx*zy/700; p.m31 = -a*cx-b*cy+cx*w0
        p.m12=d-cy*zx/700; p.m22=e-cy*zy/700; p.m32 = -d*cx-e*cy+cy*w0
        p.m13 = -zx/700; p.m23 = -zy/700; p.m33=w0
        return p
    }
}
