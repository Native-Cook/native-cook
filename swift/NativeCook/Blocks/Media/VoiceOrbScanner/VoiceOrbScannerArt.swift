import SwiftUI

struct VoiceOrbScannerArt: View {
    var size: CGSize
    var top: Double
    var frame: VoiceOrbScannerMotion.Frame
    var reduced: Bool
    var autoPlay: Bool
    var body: some View {
        let w = size.width, cx = w/2, oy = top+270, r = min(118,w*0.29), cw = (w-64)/3, cy = oy+r+100
        let sp = reduced ? (frame.S > 0.5 ? 1.0 : 0.0) : frame.S
        let wave: Double = abs(sin(frame.now * 5.3) * sin(frame.now * 2.1 + 1))
        let vo: Double = reduced ? 0 : (frame.listening ? 0.55 + 0.45 * wave : 0.12)
        let ph = 2*Double.pi*(frame.useLoopPhase ? frame.t : frame.now)/9
        let droplets: [SIMD4<Float>] = (0..<3).map { i in
            let k = reduced ? sp : VoiceOrbScannerMotion.ease((sp-Double(i)*0.08)/0.84)
            let x = VoiceOrbScannerMotion.mix(cx+(Double(i)-1)*r*0.25,24+Double(i)*(cw+8)+26,k)
            let y = VoiceOrbScannerMotion.mix(oy+r*0.3,cy+28,k)-sin(k*Double.pi)*60
            let radius = k <= 0.001 ? 0 : VoiceOrbScannerMotion.mix(r*0.42,17,VoiceOrbScannerMotion.smooth(k*1.2))
            return SIMD4(Float(x),Float(y),Float(radius),0)
        }
        ZStack(alignment:.topLeading) {
            Rectangle().fill(.white).colorEffect(ShaderLibrary.voiceOrbScannerWarm(.float(w),.float(oy)))
            Canvas { context, _ in
                if frame.listening && !reduced {
                    for i in 0..<3 {
                        let k = (frame.now*0.9+Double(i)/3).truncatingRemainder(dividingBy:1)
                        let rr = r*(1.02+0.55*k)
                        var ring = context
                        ring.opacity = 0.35*(1-k)*min(1,frame.L*6)
                        ring.addFilter(.blur(radius:3+6*k))
                        ring.stroke(Path(ellipseIn:CGRect(x:cx-rr,y:oy-rr,width:2*rr,height:2*rr)),with:.color(VoiceOrbScannerResources.color(0xff7ad0)),lineWidth:2+6*(1-k))
                    }
                }
                if frame.L > 0.001 {
                    let rr = r+22
                    var path = Path(); path.addArc(center:CGPoint(x:cx,y:oy),radius:rr,startAngle:.degrees(-90),endAngle:.degrees(-90+360*frame.L),clockwise:false)
                    var ring = context; ring.opacity = 1-VoiceOrbScannerMotion.smooth(sp*3)
                    ring.stroke(path,with:.conicGradient(Gradient(colors:[VoiceOrbScannerResources.color(0xff6fb7),VoiceOrbScannerResources.color(0xffb04a),VoiceOrbScannerResources.color(0xb78cff),VoiceOrbScannerResources.color(0xff6fb7)]),center:CGPoint(x:cx,y:oy),angle:.degrees(0)),style:StrokeStyle(lineWidth:4,lineCap:.round))
                }
            }
            Rectangle().fill(.white).colorEffect(ShaderLibrary.voiceOrbScannerOrb(
                .float(ph),.float4(Float(cx),Float(oy),Float(r*(1-0.14*sp)),Float(vo)),
                .float4(droplets[0].x,droplets[0].y,droplets[0].z,droplets[0].w),.float4(droplets[1].x,droplets[1].y,droplets[1].z,droplets[1].w),.float4(droplets[2].x,droplets[2].y,droplets[2].z,droplets[2].w),.float(frame.listening ? 1 : 0)
            )).mask(alignment:.topLeading) {
                Rectangle().frame(width:w,height:size.height-top-40).offset(y:top+40)
            }
        }.frame(width:size.width,height:size.height,alignment:.topLeading)
    }
}
