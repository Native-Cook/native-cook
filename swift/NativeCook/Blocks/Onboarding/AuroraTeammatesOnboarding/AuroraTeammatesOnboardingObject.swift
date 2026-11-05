import QuartzCore
import SwiftUI

struct AuroraTeammatesOnboardingObject: View {
    typealias GL=AuroraTeammatesOnboardingMotion.GL
    let l:AuroraTeammatesOnboardingTheme.Layout
    let sc:[Double]
    let i:Int
    private var o:Int { i*13 }
    private var dimensions:CGSize {
        if i<2 { let face=(i==0 ? 117.5:111.6)*l.S;return CGSize(width:face,height:face) }
        return CGSize(width:(i<7 ? 218.75:116.5)*l.S+2,height:(i<7 ? 43:75)*l.S+2)
    }
    private func projection(_ size:CGSize)->ProjectionTransform {
        let cs=cos(sc[o+2]),sn=sin(sc[o+2]),cx=cos(sc[o+3]),sx=sin(sc[o+3]),cy=cos(sc[o+9]),sy=sin(sc[o+9]),s=sc[o+4]
        let a=s*cy*cs,b=s*(cx*sn+sx*sy*cs),c = -s*cy*sn,d=s*(cx*cs-sx*sy*sn)
        let g = -s*(sx*sn-cx*sy*cs)/640,h = -s*(sx*cs+cx*sy*sn)/640
        let x=size.width/2,y=size.height/2
        var t=ProjectionTransform()
        t.m11=a+x*g;t.m21=c+x*h;t.m31=x-t.m11*x-t.m21*y
        t.m12=b+y*g;t.m22=d+y*h;t.m32=y-t.m12*x-t.m22*y
        t.m13=g;t.m23=h;t.m33=1-g*x-h*y
        return t
    }
    var body:some View {
        let size=dimensions
        ZStack(alignment:.topLeading) {
            if i<2 {
                if let image=AuroraTeammatesOnboardingResources.shared.images[i==0 ? "ato-icon-purple":"ato-icon-slate"] {
                    image.resizable().interpolation(.low).frame(width:size.width,height:size.height).opacity(sc[o+5])
                }
            } else {
                Canvas(opaque:false,colorMode:.nonLinear) { c,_ in
                    var d=c;d.translateBy(x:size.width/2,y:size.height/2)
                    AuroraTeammatesOnboardingArt(l:l,sc:sc).drawPanel(d,i)
                }.opacity(sc[o+5])
                Canvas(opaque:false,colorMode:.nonLinear) { c,_ in
                    if let content=AuroraTeammatesOnboardingResources.shared.contents[i] { c.draw(content,in:CGRect(origin:.zero,size:size)) }
                }.opacity(sc[o+5]*sc[o+8])
            }
        }
        .frame(width:size.width,height:size.height,alignment:.topLeading)
        .compositingGroup()
        .projectionEffect(projection(size))
        .position(x:sc[o],y:sc[o+1])
    }
}
