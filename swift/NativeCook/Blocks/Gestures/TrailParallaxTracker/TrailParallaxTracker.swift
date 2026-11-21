import CoreText
import SwiftUI
import UIKit

/// Multi-plane scenery is GPU-relit; the hiker, hill, odometers and occluders are native vector/bitmap drawing.
struct TrailParallaxTracker:View {
    var autoPlay=true
    static let loop=12.0
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var engine=TrailParallaxTrackerEngine()
    @State private var held=false
    @State private var pulse=0
    @State private var settling=false
    @State private var darkSky = false
    @GestureState private var gestureActive=false
    private var pin:Double? {
        #if DEBUG
        let a=ProcessInfo.processInfo.arguments;if let i=a.firstIndex(of:"-trail-time"),a.indices.contains(i+1){return Double(a[i+1])}
        #endif
        return nil
    }
    private var manual:Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-trail-manual")
        #else
        return false
        #endif
    }
    var body:some View {
        GeometryReader {g in
            let s=g.size.width/402,dy=g.size.height/s-874,scene=530+dy
            TimelineView(.animation(minimumInterval:1.0/120,paused:reduced && !held && !settling)) {clock in
                let now=clock.date.timeIntervalSinceReferenceDate
                let f=engine.frame(now:now,autoPlay:autoPlay,pin:pin,manual:manual,reduced:reduced)
                let sp=TrailParallaxTrackerWorld.split(f.d*24,reduced:reduced),row=TrailParallaxTrackerLight.hour(f.d)
                let pa=TrailParallaxTrackerLight.palette(sp.a,row:row),pb=TrailParallaxTrackerLight.palette(sp.b,row:row)
                ZStack(alignment:.topLeading){
                    Color(red:0.024,green:0.024,blue:0.024)
                    ForEach(0..<(sp.a==sp.b ? 1:2),id:\.self){ side in
                        let palette=side==0 ? pa:pb
                        let x=side==0 ? 0:max(0,min(sp.x,402)),w=side==0 ? max(0,min(sp.x,402)):402-max(0,min(sp.x,402))
                        LinearGradient(stops:[.init(color:color(palette.sky[0]),location:0),.init(color:color(palette.sky[1]),location:0.6),.init(color:color(palette.sky[2]),location:1)],startPoint:.top,endPoint:.bottom)
                            .frame(width:402*s,height:(490+dy)*s)
                            .frame(height:(scene+40)*s,alignment:.top)
                            .background(color(palette.sky[2]))
                            .mask(alignment:.topLeading){Rectangle().frame(width:w*s,height:(scene+40)*s).offset(x:x*s)}
                    }
                    ForEach(0..<3,id:\.self){ depth in
                        ForEach(0..<(sp.a==sp.b ? 1:2),id:\.self){side in
                            let bi=side==0 ? sp.a:sp.b,palette=side==0 ? pa:pb
                            let off=TrailParallaxTrackerWorld.offset(bi,depth:depth,camera:f.d*24,reduced:reduced)
                            let b=TrailParallaxTrackerWorld.biomes[bi],name=TrailParallaxTrackerAssets.strips[bi][depth]
                            let x=side==0 ? 0:max(0,min(sp.x,402)),w=side==0 ? max(0,min(sp.x,402)):402-max(0,min(sp.x,402))
                            ZStack(alignment:.topLeading){
                                Image(uiImage:TrailParallaxTrackerAssets.image(name)).resizable().frame(width:b.width*s,height:440*s).offset(x:-off*s,y:(90+dy)*s)
                                if 90+dy>0 {Image(uiImage:TrailParallaxTrackerAssets.topRow(name)).resizable().frame(width:b.width*s,height:(90+dy)*s).offset(x:-off*s)}
                            }.frame(width:402*s,height:(scene+40)*s,alignment:.topLeading).clipped()
                                .drawingGroup(opaque:false,colorMode:.nonLinear)
                                .colorEffect(ShaderLibrary.trailParallaxTrackerRelight(.floatArray(palette.source),.floatArray(palette.destination),.float(Double(palette.count))))
                                .layerEffect(ShaderLibrary.trailParallaxTrackerHorizontalBlur(.float(depth==0 || reduced ? 0:min(14,abs(f.v)*0.22)*s)),maxSampleOffset:CGSize(width:28*s,height:0))
                                .mask(alignment:.topLeading){Rectangle().frame(width:w*s,height:(scene+40)*s).offset(x:x*s)}
                        }
                        if depth==1 {TrailParallaxTrackerShafts(frame:f,split:sp,dy:dy,scale:s,reduced:reduced).allowsHitTesting(false)}
                    }
                    TrailParallaxTrackerForeground(frame:f,dy:dy,scale:s,a:pa,b:pb,split:sp,reduced:reduced).allowsHitTesting(false)
                    Rectangle().fill(.white).frame(width:g.size.width,height:max(0,scene*s))
                        .colorEffect(ShaderLibrary.trailParallaxTrackerGrain()).allowsHitTesting(false)
                    TrailParallaxTrackerForeground(frame:f,dy:dy,scale:s,a:pa,b:pb,split:sp,reduced:reduced,uiOnly:true).allowsHitTesting(false)
                    Color.clear.contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:0)
                            .updating($gestureActive){_,active,_ in active=true}
                            .onChanged {v in
                                if !held {engine.begin(now:now);held=true}
                                engine.move(v.translation.width/s,now:now)
                            }
                            .onEnded {v in
                                engine.end(v.velocity.width/s,now:now,cancelled:false)
                                if hypot(v.translation.width,v.translation.height)<8 {engine.tap(x:v.location.x/s,y:v.location.y/s,base:500+dy,now:now);pulse += 1}
                                held=false;wakeReduced()
                            })
                        .accessibilityElement().accessibilityLabel("Trail distance")
                        .accessibilityValue(String(format:"%.1f miles walked, %.1f miles remaining",f.d.tptClamp(0,150),825.7-f.d.tptClamp(0,150)))
                        .accessibilityHint("Drag horizontally to scrub the trail. Tap the hiker to wave.")
                        .accessibilityAdjustableAction {direction in engine.adjust(f.d+(direction == .increment ? 1:-1),now:now);pulse += 1;wakeReduced()}
                    Color.clear.frame(width:72*s,height:107*s)
                        .position(x:200*s,y:(TrailParallaxTrackerMath.ground(200+f.d*24,500+dy)-45)*s)
                        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("Hiker")
                        .accessibilityValue("Tap to wave")
                        .accessibilityAction {engine.begin(now:now);engine.tap(x:200,y:TrailParallaxTrackerMath.ground(200+f.d*24,500+dy)-45,base:500+dy,now:now);engine.end(0,now:now,cancelled:true);pulse += 1;wakeReduced()}
                }.frame(width:g.size.width,height:g.size.height,alignment:.topLeading).clipped()
                    .onChange(of: row >= 3, initial: true) { _, dark in
                        darkSky = dark
                    }
            }
        }.ignoresSafeArea().preferredColorScheme(darkSky ? .dark : .light)
        .sensoryFeedback(.selection,trigger:pulse)
        .onChange(of:gestureActive){_,active in if !active && held {engine.end(0,now:Date().timeIntervalSinceReferenceDate,cancelled:true);held=false;wakeReduced()}}
    }
    private func wakeReduced(){if reduced {settling=true;DispatchQueue.main.asyncAfter(deadline:.now()+0.6){settling=false}}}
    private func color(_ rgb:[Double])->Color {Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2],opacity:1)}
}

private struct TrailParallaxTrackerShafts:View {
    let frame:TrailParallaxTrackerFrame,split:(a:Int,b:Int,x:Double),dy:Double,scale:Double,reduced:Bool
    var body:some View {Canvas{context,_ in
        let scene=530+dy,rel=frame.d*24-1408.8
        var region=CGRect.zero
        if split.a==2 && split.b==2 {region=CGRect(x:-50,y:0,width:520,height:scene)}else if split.b==2 {region=CGRect(x:split.x,y:0,width:600,height:scene)}else if split.a==2 {region=CGRect(x:-200,y:0,width:split.x+200,height:scene)}
        context.scaleBy(x:scale,y:scale);context.clip(to:Path(region))
        for pair in [[284.0,56],[372,42],[446,68],[640,50],[800,64],[1010,44]] {let x=pair[0]-rel*(reduced ? 1:0.5),w=pair[1];if x>460 || x < -260 {continue};var p=Path();p.move(to:CGPoint(x:x,y:-4));p.addLine(to:CGPoint(x:x+w*0.62,y:-4));p.addLine(to:CGPoint(x:x+w*0.62+150,y:scene));p.addLine(to:CGPoint(x:x+150-w*0.38,y:scene));p.closeSubpath();context.fill(p,with:.color(Color(red:0.973,green:0.98,blue:0.839).opacity(0.45)))}
    }}
}

@MainActor enum TrailParallaxTrackerAssets {
    static let strips=[["b1-canyon-far-ridge-2x","b1-canyon-mid-snow-2x","b1-canyon-near-rim-2x"],["b2-granite-far-range-2x","b2-granite-main-spires-2x","b2-granite-near-buttress-2x"],["b3-grove-backlight-2x","b3-grove-mid-trunks-2x","b3-grove-near-trunks-2x"],["b4-dusk-far-mountain-2x","b4-dusk-mid-hills-2x","b4-dusk-pine-row-2x"]]
    private static var images:[String:UIImage]=[:],rows:[String:UIImage]=[:],fonts:[String:UIFont]=[:]
    static func image(_ name:String)->UIImage {if let i=images[name]{return i};guard let u=Bundle.main.url(forResource:"TrailParallaxTracker-"+name,withExtension:"png"),let i=UIImage(contentsOfFile:u.path) else{return UIImage()};images[name]=i;return i}
    static func topRow(_ name:String)->UIImage {if let i=rows[name]{return i};guard let cg=image(name).cgImage,let row=cg.cropping(to:CGRect(x:0,y:0,width:cg.width,height:1))else{return UIImage()};let i=UIImage(cgImage:row);rows[name]=i;return i}
    static func font(_ face:String,_ size:Double)->UIFont {
        let key="\(face)/\(size)";if let f=fonts[key]{return f}
        guard let u=Bundle.main.url(forResource:"TrailParallaxTracker-"+face,withExtension:"ttf"),let p=CGDataProvider(url:u as CFURL),let g=CGFont(p)else{return .systemFont(ofSize:size)}
        let f=CTFontCreateWithGraphicsFont(g,size,nil,nil) as UIFont;fonts[key]=f;return f
    }
}
