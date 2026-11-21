import CoreText
import SwiftUI
import UIKit

/// The canonical 402 pt selector with a native Metal line curtain and a refracting red glass puck.
struct GearPuckSpeedLine: View {
    var autoPlay = true
    static let loop = 10.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var engine = GearPuckSpeedLineEngine()
    @State private var touching = false
    @State private var pulse = 0
    @GestureState private var active = false
    private let gears = ["P","R","D","S","M"]
    private var pin: Double? {
        #if DEBUG
        let a=ProcessInfo.processInfo.arguments
        if let i=a.firstIndex(of:"-gear-time"), a.indices.contains(i+1) {return Double(a[i+1])}
        #endif
        return nil
    }
    private var manualPin: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-gear-manual")
        #else
        return false
        #endif
    }
    init(autoPlay: Bool = true) {self.autoPlay=autoPlay;GearPuckSpeedLineAssets.register()}
    var body: some View {
        GeometryReader { geometry in
            let size=geometry.size
            let s=min(size.width/402,size.height/732)
            let width=402*s
            TimelineView(.animation(minimumInterval:1.0/120,paused:reduced && !touching)) { timeline in
                let now=timeline.date.timeIntervalSinceReferenceDate
                let f=engine.frame(now:now,autoPlay:autoPlay,reduced:reduced,pin:pin,manualPin:manualPin)
                let L=f.readout
                let color=f.color(at:L.cx)
                let sigmaL=68*(f.lean>0 ? 1+0.3*abs(f.lean):1)
                let sigmaR=68*(f.lean<0 ? 1+0.3*abs(f.lean):1)
                let unitW=GearPuckSpeedLineAssets.unitWidth
                let parked=min(max((1-L.scale)/(1-34.0/76),0),1)*(1-f.drop)
                let ux=GearPuckSpeedLineMath.mix(L.cx-unitW/2,22,parked)
                let top=L.base-53.428*L.scale
                let unitTop=L.base+18*L.scale-8.97
                let below=GearPuckSpeedLineMath.smooth(318,336,top)
                let left=min(ux,GearPuckSpeedLineMath.mix(ux,L.cx-L.width*L.scale/2,below))
                let right=max(ux+unitW,GearPuckSpeedLineMath.mix(ux+unitW,L.cx+L.width*L.scale/2,below))
                let boxY=GearPuckSpeedLineMath.mix(unitTop,top,below)
                ZStack(alignment:.topLeading) {
                    Rectangle().fill(Color.black)
                        .colorEffect(ShaderLibrary.gearPuckSpeedLineScene(
                            .float(s),.float4(f.crest,f.amplitude+f.jitter,sigmaL,sigmaR),
                            .float4(f.pluckX,f.pluck,f.pluckLag,0),.float(f.phase),
                            gearPuckSpeedLineShaderColor(GearPuckSpeedLineFrame.bands[Int(f.color0.rounded()).clampedGear(0,3)]),
                            gearPuckSpeedLineShaderColor(GearPuckSpeedLineFrame.bands[Int(f.color1.rounded()).clampedGear(0,3)]),
                            gearPuckSpeedLineShaderColor(GearPuckSpeedLineFrame.bands[Int(f.color2.rounded()).clampedGear(0,3)]),
                            .float4(f.frontPX,f.frontPR,f.frontLX,f.frontLR),.float2(f.packetX,f.packetGain),
                            .float4(f.curtain,f.whip,f.time,f.sway),.float(-0.08*f.speed),
                            .float4(left-4,boxY-4,right-left+8,L.base+18*L.scale+6-boxY),
                            .float(GearPuckSpeedLineMath.mix(220,f.crest,min(max(f.amplitude/96,0),1))),.floatArray(f.arc)
                        ))
                    Canvas { context,_ in
                        let capsule=Path(roundedRect:CGRect(x:24*s,y:628*s,width:354*s,height:72*s),cornerRadius:36*s)
                        context.stroke(capsule,with:.linearGradient(Gradient(colors:[Color(red:0.667,green:0.69,blue:0.729).opacity(0.62),Color(red:0.604,green:0.627,blue:0.667).opacity(0.34),Color(red:0.604,green:0.627,blue:0.667).opacity(0.5)]),startPoint:CGPoint(x:0,y:628*s),endPoint:CGPoint(x:0,y:700*s)),lineWidth:2*s)
                        for i in 0..<5 {context.draw(Image(uiImage:GearPuckSpeedLineAssets.letters[i]),in:CGRect(x:(36+66*Double(i))*s,y:644*s,width:66*s,height:40*s))}
                    }
                    Image(uiImage:GearPuckSpeedLineAssets.lensAtlas).resizable()
                        .frame(width:width,height:874*s)
                        .layerEffect(ShaderLibrary.gearPuckSpeedLineLens(.float(s),.float2(69+66*f.g,664),.float(f.press),.float(f.flash),gearPuckSpeedLineShaderColor(f.color(at:69+66*f.g))),maxSampleOffset:CGSize(width:64*s,height:64*s))
                        .allowsHitTesting(false)
                    Canvas { context,_ in
                        let drums=f.drums,cells=L.width/47.12
                        let x0=L.cx-L.width*L.scale/2
                        for i in 0..<3 {
                            let value=drums[i],fl=floor(value),roll=value-fl
                            let x=x0+(cells-3+Double(i))*47.12*L.scale
                            let digit=Int(fl)%10,next=(digit+1)%10
                            var halo=context;halo.addFilter(.blur(radius:8*s))
                            halo.addFilter(.colorMultiply(Color(red:Double(color.x),green:Double(color.y),blue:Double(color.z)).opacity(0.5)))
                            func place(_ image:UIImage,_ y:Double,_ alpha:Double,_ glow:Bool) {
                                let rect=CGRect(x:x*s,y:(L.base+(y-90)*L.scale)*s,width:47.12*L.scale*s,height:120*L.scale*s)
                                if glow {var c=halo;c.opacity=alpha;c.draw(Image(uiImage:image),in:rect)}
                                else {var c=context;c.opacity=alpha;c.draw(Image(uiImage:image),in:rect)}
                            }
                            if !(i<2 && fl==0) {for glow in [true,false] {place(GearPuckSpeedLineAssets.digits[digit],-roll*12,1-roll,glow)}}
                            if roll>0.001 {for glow in [true,false] {place(GearPuckSpeedLineAssets.digits[next],(1-roll)*12,roll,glow)}}
                        }
                        context.draw(Image(uiImage:GearPuckSpeedLineAssets.unit),in:CGRect(x:ux*s,y:(L.base+18*L.scale-20)*s,width:unitW*s,height:30*s))
                    }.allowsHitTesting(false)
                    Color.clear.contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:0)
                            .updating($active) {_,state,_ in state=true}
                            .onChanged { g in
                                if !touching {engine.begin(x:g.startLocation.x/s,y:g.startLocation.y/s,now:now,reduced:reduced);touching=true}
                                engine.move(x:g.location.x/s,y:g.location.y/s,dx:g.translation.width/s,dy:g.translation.height/s)
                            }
                            .onEnded { g in
                                engine.end(x:g.location.x/s,dx:g.translation.width/s,dy:g.translation.height/s,velocity:g.velocity.width/s,now:now,cancelled:false)
                                touching=false;pulse += 1
                            })
                    ForEach(0..<5,id:\.self) { i in
                        Color.clear.frame(width:60*s,height:88*s).position(x:(69+66*Double(i))*s,y:664*s)
                            .allowsHitTesting(false)
                            .accessibilityElement().accessibilityLabel("Drive mode \(gears[i])")
                            .accessibilityValue(engine.target==i ? "Selected":"")
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction {engine.select(i,now:now);pulse += 1}
                    }
                    Color.clear.frame(width:200*s,height:140*s).position(x:f.crest*s,y:300*s)
                        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("Speed")
                        .accessibilityValue("\(Int(f.speed.rounded())) miles per hour")
                        .accessibilityAdjustableAction { direction in
                            engine.begin(x:f.crest,y:300,now:now,reduced:reduced)
                            engine.shown.speed=min(max(f.speed+(direction == .increment ? 10:-10),0),[0,0,132,197,197][engine.target])
                            engine.end(x:f.crest,dx:0,dy:0,velocity:0,now:now,cancelled:true);pulse += 1
                        }
                }.frame(width:width,height:size.height,alignment:.topLeading).clipped().position(x:size.width/2,y:size.height/2)
            }
        }.background(Color(red:0.027,green:0.039,blue:0.063)).ignoresSafeArea().preferredColorScheme(.dark)
        .sensoryFeedback(.selection,trigger:pulse)
        .onChange(of:active) {_,value in
            if !value && touching {engine.end(x:69+66*engine.shown.g,dx:0,dy:0,velocity:0,now:Date().timeIntervalSinceReferenceDate,cancelled:true);touching=false}
        }
    }
}

private extension Int {func clampedGear(_ a:Int,_ b:Int)->Int {Swift.min(Swift.max(self,a),b)}}

private enum GearPuckSpeedLineAssets {
    static func register() {_ = registered}
    private static let registered:Bool = {
        for name in ["Exo2Num-Light","Exo2-Regular","SairaCondensed-Medium"] {
            if let url=Bundle.main.url(forResource:"GearPuckSpeedLine-"+name,withExtension:"ttf") {CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)}
        };return true
    }()
    private static func font(_ name:String,_ size:CGFloat)->UIFont {register();return UIFont(name:name,size:size) ?? .systemFont(ofSize:size)}
    static let digits:[UIImage] = (0..<10).map {i in
        let f=font("Exo2Num-Light",76)
        return UIGraphicsImageRenderer(size:CGSize(width:47.12,height:120)).image {_ in
            String(i).draw(at:CGPoint(x:0,y:90-f.ascender),withAttributes:[.font:f,.foregroundColor:UIColor(red:0.957,green:0.969,blue:1,alpha:1)])
        }
    }
    static let letters:[UIImage] = (0..<5).map { i in
        let f=font("Exo2-Regular",20),a:[NSAttributedString.Key:Any]=[.font:f,.foregroundColor:UIColor(red:0.655,green:0.671,blue:0.702,alpha:1)]
        let text=["P","R","D","S","M"][i],w=text.size(withAttributes:a).width
        return UIGraphicsImageRenderer(size:CGSize(width:66,height:40)).image {_ in text.draw(at:CGPoint(x:33-w/2,y:26.9-f.ascender),withAttributes:a)}
    }
    static let unitWidth:Double = {let f=font("SairaCondensed-Medium",13);return "MP/H".size(withAttributes:[.font:f,.kern:1.04]).width}()
    static let unit:UIImage = {
        let f=font("SairaCondensed-Medium",13)
        return UIGraphicsImageRenderer(size:CGSize(width:unitWidth,height:30)).image {_ in "MP/H".draw(at:CGPoint(x:0,y:20-f.ascender),withAttributes:[.font:f,.kern:1.04,.foregroundColor:UIColor(red:0.545,green:0.565,blue:0.604,alpha:1)])}
    }()
    static let lensAtlas:UIImage = {
        let f=font("Exo2-Regular",20)
        return UIGraphicsImageRenderer(size:CGSize(width:402,height:874)).image {r in
            for (i,text) in ["P","R","D","S","M"].enumerated() {
                let a:[NSAttributedString.Key:Any]=[.font:f,.foregroundColor:UIColor.white],w=text.size(withAttributes:a).width
                text.draw(at:CGPoint(x:69+66*Double(i)-w/2,y:670.9-f.ascender),withAttributes:a)
            }
            r.cgContext.setStrokeColor(UIColor.white.withAlphaComponent(0.45).cgColor);r.cgContext.setLineWidth(2)
            r.cgContext.addPath(UIBezierPath(roundedRect:CGRect(x:24,y:628,width:354,height:72),cornerRadius:36).cgPath);r.cgContext.strokePath()
        }
    }()
}

private func gearPuckSpeedLineShaderColor(_ value:SIMD3<Float>)->Shader.Argument {.float3(value.x,value.y,value.z)}
