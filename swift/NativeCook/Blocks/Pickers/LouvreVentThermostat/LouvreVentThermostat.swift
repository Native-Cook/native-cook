import SwiftUI
import UIKit
import CoreText

struct LouvreVentThermostat:View {
    static let loop=9.0
    var autoPlay=true
    var onChange:((Double)->Void)?, onCommit:((Double)->Void)?
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var driver=LouvreVentThermostatDriver()
    var body:some View {
        GeometryReader { geometry in
            let scale=min(geometry.size.width/402,geometry.size.height/822)
            let x=(geometry.size.width-402*scale)/2, y=max(0,(geometry.size.height/scale-874)/2)
            ZStack(alignment:.topLeading) {
                Canvas { context,_ in
                    LouvreVentThermostatArtwork.draw(&context,scene:driver.scene,reduced:reduced,bounds:CGRect(x:-x/scale,y:-y,width:geometry.size.width/scale,height:geometry.size.height/scale))
                }.frame(width:402,height:geometry.size.height/scale)
                LouvreVentThermostatTouchSurface(driver:driver).frame(width:402,height:geometry.size.height/scale)
            }.frame(width:402,height:geometry.size.height/scale,alignment:.topLeading)
                .offset(y:y).scaleEffect(scale,anchor:.topLeading).offset(x:x)
                .frame(width:geometry.size.width,height:geometry.size.height,alignment:.topLeading).clipped()
        }.ignoresSafeArea().background(Color(red:13/255,green:1/255,blue:1/255))
        .preferredColorScheme(.dark)
        .accessibilityElement(children:.ignore).accessibilityLabel("Set temperature")
        .accessibilityValue(String(format:"%.1f degrees Celsius",driver.scene.temperature))
        .accessibilityAdjustableAction { direction in driver.adjust(direction == .increment ? 0.5 : -0.5) }
        .onAppear { configure() }
        .onChange(of:phase) { _,_ in configure() }
        .onChange(of:reduced) { _,_ in configure() }
        .onChange(of:autoPlay) { _,_ in configure() }
        .onDisappear { driver.stop() }
    }
    private func configure() { driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active,onChange:onChange,onCommit:onCommit) }
}

private final class LouvreVentThermostatDisplayTarget:NSObject {
    weak var owner:LouvreVentThermostatDriver?
    @objc func tick(_ link:CADisplayLink) { owner?.tick(link) }
}
final class LouvreVentThermostatDriver:ObservableObject {
    @Published var scene=LouvreVentThermostatMotion.scene(0)
    let engine=LouvreVentThermostatEngine()
    private var link:CADisplayLink?, displayTarget:LouvreVentThermostatDisplayTarget?, previous=0.0, active=true
    func configure(autoPlay:Bool,reduced:Bool,active:Bool,onChange:((Double)->Void)?,onCommit:((Double)->Void)?) {
        self.active=active; engine.configure(autoPlay:autoPlay,reduced:reduced); engine.onChange=onChange; engine.onCommit=onCommit
        engine.onHaptic={ kind in
            switch kind { case 0:UISelectionFeedbackGenerator().selectionChanged(); case 1:UIImpactFeedbackGenerator(style:.soft).impactOccurred(); default:UIImpactFeedbackGenerator(style:.rigid).impactOccurred() }
        }
        scene=engine.scene; wake()
    }
    private func wake() {
        if link==nil { let target=LouvreVentThermostatDisplayTarget(); target.owner=self; displayTarget=target; let link=CADisplayLink(target:target,selector:#selector(LouvreVentThermostatDisplayTarget.tick(_:))); link.add(to:.main,forMode:.common); self.link=link }
        previous=0; link?.isPaused = !active || engine.pin != nil
    }
    func stop() { link?.invalidate(); link=nil; displayTarget=nil; previous=0 }
    func tick(_ link:CADisplayLink) { let dt=previous==0 ? 1/60.0 : min(max(link.timestamp-previous,0),0.05); previous=link.timestamp; engine.advance(dt); scene=engine.scene }
    func begin(y:Double) { engine.begin(y:y); wake() }
    func drag(_ translation:Double) { engine.drag(translation:translation) }
    func end(velocity:Double,cancelled:Bool=false) { engine.end(velocity:velocity,cancelled:cancelled); wake() }
    func tap(x:Double,y:Double) { engine.tap(x:x,y:y); wake() }
    func adjust(_ delta:Double) { engine.adjust(delta); wake() }
    deinit { link?.invalidate() }
}

private struct LouvreVentThermostatTouchSurface:UIViewRepresentable {
    let driver:LouvreVentThermostatDriver
    func makeUIView(context:Context)->LouvreVentThermostatTouchView { let view=LouvreVentThermostatTouchView(); view.driver=driver; return view }
    func updateUIView(_ view:LouvreVentThermostatTouchView,context:Context) { view.driver=driver }
}
private final class LouvreVentThermostatTouchView:UIView {
    weak var driver:LouvreVentThermostatDriver?
    private var origin=CGPoint.zero
    private lazy var pan=UIPanGestureRecognizer(target:self,action:#selector(panned(_:)))
    override init(frame:CGRect) { super.init(frame:frame); pan.cancelsTouchesInView=false; addGestureRecognizer(pan) }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) { guard let t=touches.first else { return }; origin=t.location(in:self); driver?.begin(y:origin.y) }
    @objc private func panned(_ recognizer:UIPanGestureRecognizer) {
        switch recognizer.state {
        case .began,.changed:driver?.drag(recognizer.translation(in:self).y)
        case .ended:driver?.end(velocity:recognizer.velocity(in:self).y)
        case .cancelled,.failed:driver?.end(velocity:0,cancelled:true)
        default:break
        }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) { if pan.state == .possible || pan.state == .failed { driver?.tap(x:origin.x,y:origin.y) } }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { driver?.end(velocity:0,cancelled:true) }
}

enum LouvreVentThermostatArtwork {
    typealias M=LouvreVentThermostatMotion
    private static var fontCache:[String:UIFont]=[:], glyphCache:[String:UIImage]=[:]
    static func font(_ size:Double,_ face:String="MartianMono-Regular")->UIFont {
        let key="\(size)/\(face)"; if let f=fontCache[key] { return f }
        let url=Bundle.main.url(forResource:"LouvreVentThermostat-"+face,withExtension:"ttf")!
        let f=CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url:url as CFURL)!)!,size,nil,nil) as UIFont
        fontCache[key]=f; return f
    }
    private static func width(_ text:String,_ f:UIFont)->Double { (text as NSString).size(withAttributes:[.font:f]).width }
    private static func inkWidth(_ text:String,_ f:UIFont)->Double {
        let line=CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:[.font:f])); return CTLineGetBoundsWithOptions(line,.useGlyphPathBounds).width
    }
    private static func glyph(_ text:String,_ f:UIFont)->UIImage {
        let key="\(text)/\(f.pointSize)/\(f.fontDescriptor.symbolicTraits.rawValue)/\(f.fontName)"
        if let image=glyphCache[key] { return image }
        let format=UIGraphicsImageRendererFormat(); format.scale=UIScreen.main.scale
        let image=UIGraphicsImageRenderer(size:CGSize(width:ceil(width(text,f))+6,height:120),format:format).image { _ in
            (text as NSString).draw(at:CGPoint(x:0,y:90-f.ascender),withAttributes:[.font:f,.foregroundColor:UIColor.white])
        }
        glyphCache[key]=image; return image
    }
    private static func color(_ h:Int)->Color { let c=LouvreVentThermostatUniforms.rgb(h); return Color(.sRGB,red:c[0],green:c[1],blue:c[2]) }
    private static func label(_ context:inout GraphicsContext,_ text:String,_ x:Double,_ baseline:Double,_ f:UIFont,_ tint:Color) {
        let image=glyph(text,f); var c=context; c.addFilter(.colorMultiply(tint)); c.draw(Image(uiImage:image),in:CGRect(x:x,y:baseline-90,width:image.size.width,height:image.size.height))
    }
    static func draw(_ context:inout GraphicsContext,scene s:M.Scene,reduced:Bool,bounds:CGRect) {
        let values=LouvreVentThermostatUniforms.values(s,reduced:reduced)
        context.fill(Path(bounds),with:.shader(ShaderLibrary.louvreVentThermostatPage(.floatArray(LouvreVentThermostatUniforms.page(values)))))
        context.fill(Path(CGRect(x:76,y:244,width:250,height:488)),with:.shader(ShaderLibrary.louvreVentThermostatVent(.floatArray(LouvreVentThermostatUniforms.vent(values)))))
        context.drawLayer { glow in glow.addFilter(.blur(radius:4)); glow.opacity=0.55; glow.fill(Path(CGRect(x:69,y:485.5,width:263,height:5)),with:.color(color(0xE0453A))) }
        context.fill(Path(roundedRect:CGRect(x:68,y:486.75,width:264,height:2.5),cornerRadius:1.25),with:.color(color(0xE0453A)))
        var tri=Path(); tri.move(to:CGPoint(x:62-s.nudge,y:484)); tri.addLine(to:CGPoint(x:69-s.nudge,y:488)); tri.addLine(to:CGPoint(x:62-s.nudge,y:492)); tri.closeSubpath()
        context.fill(tri,with:.color(color(0xE0453A))); context.stroke(tri,with:.color(color(0xE0453A)),style:StrokeStyle(lineWidth:1,lineJoin:.round))
        let mono=font(13)
        var scale=context; scale.clip(to:Path(CGRect(x:333,y:252,width:60,height:472)))
        for v in stride(from:16,through:32,by:2) {
            let y=488-(Double(v)-s.temperature)*41.6+4.7, d=abs(y-4.7-488), k=M.smooth(60,200,d), red=1-M.smooth(3,12,d)
            let near=[0.718,0.663,0.655], far=[0.29,0.235,0.227], hot=[0.878,0.271,0.227]
            let rgb=(0..<3).map { M.mix(M.mix(near[$0],far[$0],k),hot[$0],red) }
            label(&scale,"\(v)°",337,y,mono,Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2]))
        }
        let number=font(69,"BigShouldersDisplay-Black"), unit=font(24,"BigShouldersDisplay-ExtraBold")
        let widths=(0...9).map { inkWidth(String($0),number) }, cell=(widths.max() ?? 0)+2, dot=inkWidth(".",number), total=3*cell+dot-2, x0=201-total/2
        var mask=context
        mask.clipToLayer { c in
            drum(&c,pos:s.drums[2],glyphs:(0...9).map(String.init),widths:widths,x:x0,cell:cell,font:number)
            drum(&c,pos:s.drums[1],glyphs:(0...9).map(String.init),widths:widths,x:x0+cell,cell:cell,font:number)
            label(&c,".",x0+2*cell-1,178.2,number,.white)
            drum(&c,pos:s.drums[0],glyphs:["0","5"],widths:[widths[0],widths[5]],x:x0+2*cell+dot-2,cell:cell,font:number)
        }
        let index=min(max(Int(floor(s.s0-s.k0+0.5)),0),25), openness=abs(sin(s.blades[2*index]*Double.pi/180))
        let intensity=values["uI"]![0]/(1+0.25*abs(tanh(s.velocity/260))), heat=values["uHeat"]!, a=min(0.5,intensity*(0.25+0.6*openness)*0.6)
        let ink=[0.984,0.965,0.949], rgb=(0..<3).map { M.mix(ink[$0],heat[$0],a) }
        mask.fill(Path(CGRect(x:80,y:120,width:240,height:62)),with:.linearGradient(Gradient(stops:[.init(color:color(0xFBF6F2),location:0),.init(color:color(0xFBF6F2),location:0.58),.init(color:Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2]),location:1)]),startPoint:CGPoint(x:0,y:123),endPoint:CGPoint(x:0,y:178.2)))
        label(&context,"°C",x0+total+7,142.2,unit,color(0x5A4A48))
        let modeWidth=width("Celsius",mono), modeX=201-(modeWidth+18)/2
        label(&context,"Celsius",modeX,214.7,mono,color(0x8A7C7A)); dots(&context,x:modeX,y:220.5,width:58,tint:color(0x8A7C7A))
        var chevron=Path(); chevron.move(to:CGPoint(x:modeX+modeWidth+6.4,y:206.1)); chevron.addLine(to:CGPoint(x:modeX+modeWidth+10.5,y:210.2)); chevron.addLine(to:CGPoint(x:modeX+modeWidth+14.6,y:206.1))
        context.stroke(chevron,with:.color(color(0x8A7C7A)),style:StrokeStyle(lineWidth:1.25,lineCap:.round,lineJoin:.round))
        footer(&context,mono:mono)
    }
    private static func drum(_ context:inout GraphicsContext,pos:Double,glyphs:[String],widths:[Double],x:Double,cell:Double,font:UIFont) {
        let n=glyphs.count, index=((Int(floor(pos))%n)+n)%n, f=pos-floor(pos)
        var c=context; c.clip(to:Path(CGRect(x:x,y:121.5,width:cell,height:58.2)))
        label(&c,glyphs[index],x+(cell-widths[index])/2,178.2+f*65.2,font,.white)
        label(&c,glyphs[(index+1)%n],x+(cell-widths[(index+1)%n])/2,178.2+f*65.2-65.2,font,.white)
    }
    private static func dots(_ c:inout GraphicsContext,x:Double,y:Double,width:Double,tint:Color) {
        var path=Path(); path.move(to:CGPoint(x:x+0.5,y:y)); path.addLine(to:CGPoint(x:x+width,y:y)); var copy=c; copy.opacity=0.6; copy.stroke(path,with:.color(tint),style:StrokeStyle(lineWidth:1,dash:[1,2]))
    }
    private static func footer(_ c:inout GraphicsContext,mono:UIFont) {
        let tint=color(0xF5EDE4), widths=[24+width("67%",mono),24+width("Schedule",mono),24+width("22°C",mono)], gap=(314-widths.reduce(0,+))/2
        var x=44.0
        for i in 0..<3 {
            var icon=Path()
            if i==0 {
                icon.move(to:CGPoint(x:x+8,y:780.55)); icon.addCurve(to:CGPoint(x:x+3.55,y:788.5),control1:CGPoint(x:x+7.4,y:780.55),control2:CGPoint(x:x+3.55,y:785.75)); icon.addArc(center:CGPoint(x:x+8,y:788.5),radius:4.45,startAngle:.degrees(180),endAngle:.degrees(0),clockwise:true); icon.addCurve(to:CGPoint(x:x+8,y:780.55),control1:CGPoint(x:x+12.45,y:785.75),control2:CGPoint(x:x+8.6,y:780.55)); c.fill(icon,with:.color(tint))
            } else if i==1 {
                c.stroke(Path(ellipseIn:CGRect(x:x+1.8,y:780.8,width:12.4,height:12.4)),with:.color(tint),lineWidth:1.25)
                icon.move(to:CGPoint(x:x+8,y:783.5)); icon.addLine(to:CGPoint(x:x+8,y:787)); icon.addLine(to:CGPoint(x:x+10.35,y:788.6)); c.stroke(icon,with:.color(tint),style:StrokeStyle(lineWidth:1.25,lineCap:.round,lineJoin:.round))
            } else {
                c.fill(Path(roundedRect:CGRect(x:x+6.5,y:780.6,width:3,height:8.6),cornerRadius:1.5),with:.color(tint)); c.fill(Path(ellipseIn:CGRect(x:x+4.4,y:786,width:7.2,height:7.2)),with:.color(tint))
            }
            label(&c,["67%","Schedule","22°C"][i],x+24,791.7,mono,tint)
            if i==1 { dots(&c,x:x+24,y:797.5,width:66,tint:tint) }
            x+=widths[i]+gap
        }
    }
}
