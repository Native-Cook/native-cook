import SwiftUI
import UIKit

// Each glass pass reads the aurora and the panels already drawn below it, as tgfx does.
// Metal performs the sampling; there are no CPU snapshots or image filters to rebuild.
struct AuroraTeammatesOnboardingLayers: View {
    typealias H=AuroraTeammatesOnboardingTheme
    typealias GL=AuroraTeammatesOnboardingMotion.GL
    let l:H.Layout
    let sc:[Double]
    var body:some View {
        var result=AnyView(ZStack(alignment:.topLeading) {
            Rectangle().fill(.white).colorEffect(ShaderLibrary.auroraTeammatesOnboardingAurora(
                .float2(Float(l.W),Float(l.H)),.float2(Float(sc[GL.coreX]),Float(sc[GL.coreY])),.float(Float(sc[GL.phase])),
                .float3(Float(sc[GL.w0]),Float(sc[GL.w1]),Float(sc[GL.w2])),.float(Float(sc[GL.flare])),.float2(Float(sc[GL.tiltX]*12),Float(sc[GL.tiltY]*8.4))
            ))
            ForEach(0..<2,id:\.self) { i in
                if sc[i*13+5]>0.00001 { AuroraTeammatesOnboardingObject(l:l,sc:sc,i:i) }
            }
        }.frame(width:l.W,height:l.H,alignment:.topLeading))
        for i in 2..<13 where sc[i*13+5]>0.00001 {
            let o=i*13
            let shader=ShaderLibrary.auroraTeammatesOnboardingBackdrop(
                .float2(Float(l.W),Float(l.H)),
                .float4(0,0,Float(sc[o+6]),Float(sc[o+7])),
                .float4(Float(sc[o]),Float(sc[o+1]),Float(sc[o+4]),Float(UIScreen.main.scale)),
                .float4(Float(sc[o+2]),Float(sc[o+3]),Float(sc[o+9]),0),
                .float4(Float((i<7 ? 7:9)*l.S),Float(sc[o+10]),Float(sc[GL.lightAngle]),Float(sc[GL.lightIntensity])),
                .float(Float(sc[o+5]))
            )
            result=AnyView(result.drawingGroup(opaque:true,colorMode:.nonLinear).layerEffect(shader,maxSampleOffset:CGSize(width:60,height:60))
                .overlay(alignment:.topLeading) { AuroraTeammatesOnboardingObject(l:l,sc:sc,i:i) })
        }
        return result
    }
}
