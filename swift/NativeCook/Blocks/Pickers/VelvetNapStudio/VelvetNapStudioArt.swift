import SwiftUI

struct VelvetNapStudioArt: View {
    let frame: VelvetNapStudioFrame
    let color: SIMD3<Double>
    private var resource:VelvetNapStudioResources { .shared }
    private func field(_ i:Int)->Data { i<frame.fields.count ? frame.fields[i].bytes:resource.emptyData }
    private func weight(_ i:Int)->Double { i<frame.weights.count ? frame.weights[i]:0 }
    var body:some View {
        ZStack(alignment:.topLeading) {
            Image(decorative:resource.shadow,scale:2).resizable().frame(width:355,height:322).offset(x:-24,y:-24)
            Image(decorative:resource.atlas,scale:2).resizable().interpolation(.high).frame(width:307,height:274)
                .layerEffect(ShaderLibrary.VelvetNapStudioNap(
                    .data(resource.pathData),.data(field(0)),.data(field(1)),.data(field(2)),
                    .float4(Float(frame.time),Float(frame.strength),0,Float(VelvetNapStudioMath.pathLength)),
                    .float4(Float(frame.authorWeight),Float(weight(0)),Float(weight(1)),Float(weight(2))),
                    .float3(Float(color.x),Float(color.y),Float(color.z))),maxSampleOffset:CGSize(width:1,height:1))
        }.frame(width:307,height:274,alignment:.topLeading)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
}
