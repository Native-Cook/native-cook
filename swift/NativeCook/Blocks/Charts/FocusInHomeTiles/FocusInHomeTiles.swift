import SwiftUI
import UIKit

struct FocusInHomeTiles:View{
    static let loop=10.0
    var autoPlay=true
    @Environment(\.accessibilityReduceMotion)private var reduced
    @Environment(\.scenePhase)private var phase
    @Environment(\.displayScale)private var pixels
    @StateObject private var driver=FocusInHomeTilesDriver()
    var body:some View{GeometryReader{geometry in
        let scale=geometry.size.width/402,height=geometry.size.height/scale
        ZStack{Canvas{context,_ in FocusInHomeTilesArtwork.draw(&context,driver.frame,height:height,pixels:scale*pixels,time:driver.engine.clock)};FocusInHomeTilesTouchSurface(driver:driver)}.frame(width:402,height:height).scaleEffect(scale,anchor:.topLeading).frame(width:geometry.size.width,height:geometry.size.height,alignment:.topLeading).clipped()
            .onAppear{driver.viewport(height)}.onChange(of:geometry.size){_,_ in driver.viewport(height)}
    }.ignoresSafeArea().background(Color(red:228/255,green:227/255,blue:211/255)).preferredColorScheme(.light)
        .accessibilityElement(children:.ignore).accessibilityLabel("Smart home tiles")
        .accessibilityValue("Page \(min(max(Int(floor(driver.frame.s/780+0.5))+1,1),3)) of 3")
        .accessibilityAction(named:"Next page"){driver.engine.page(1);driver.wake()}
        .accessibilityAction(named:"Previous page"){driver.engine.page(-1);driver.wake()}
        .accessibilityAction(named:"Focus header"){driver.engine.focus(0);driver.wake()}
        .accessibilityAction(named:"Focus Bedroom"){driver.engine.focus(1);driver.wake()}
        .accessibilityAction(named:"Focus Live Chat"){driver.engine.focus(2);driver.wake()}
        .onAppear{configure()}.onChange(of:phase){_,_ in configure()}.onChange(of:reduced){_,_ in configure()}.onChange(of:autoPlay){_,_ in configure()}.onDisappear{driver.stop()}
    }
    private func configure(){driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active)}
}
private final class FocusInHomeTilesDisplayTarget:NSObject{weak var owner:FocusInHomeTilesDriver?;@objc func tick(_ link:CADisplayLink){owner?.tick(link)}}
final class FocusInHomeTilesDriver:ObservableObject{
    let engine=FocusInHomeTilesEngine()
    @Published var frame=FocusInHomeTilesMotion.frameAt(0,874)
    private var target:FocusInHomeTilesDisplayTarget?,link:CADisplayLink?,previous=0.0,active=true
    init(){engine.onHaptic={kind in switch kind{case 0:UIImpactFeedbackGenerator(style:.soft).impactOccurred();case 1:UIImpactFeedbackGenerator(style:.light).impactOccurred();default:UISelectionFeedbackGenerator().selectionChanged()}}}
    func viewport(_ height:Double){engine.viewport(height);frame=engine.frame}
    func configure(autoPlay:Bool,reduced:Bool,active:Bool){self.active=active;engine.configure(autoPlay:autoPlay,reduced:reduced);frame=engine.frame;wake()}
    func wake(){if link==nil{let target=FocusInHomeTilesDisplayTarget();target.owner=self;self.target=target;let link=CADisplayLink(target:target,selector:#selector(FocusInHomeTilesDisplayTarget.tick(_:)));link.add(to:.main,forMode:.common);self.link=link};previous=0;link?.isPaused = !active || engine.pin != nil}
    func tick(_ link:CADisplayLink){let dt=previous==0 ? 1/60.0 : min(max(link.timestamp-previous,0),0.05);previous=link.timestamp;engine.advance(dt);frame=engine.frame}
    func stop(){link?.invalidate();link=nil;target=nil;previous=0}
    deinit{link?.invalidate()}
}
private struct FocusInHomeTilesTouchSurface:UIViewRepresentable{
    let driver:FocusInHomeTilesDriver
    func makeUIView(context:Context)->FocusInHomeTilesTouchView{let v=FocusInHomeTilesTouchView();v.driver=driver;return v}
    func updateUIView(_ view:FocusInHomeTilesTouchView,context:Context){view.driver=driver}
}
private final class FocusInHomeTilesTouchView:UIView{
    weak var driver:FocusInHomeTilesDriver?
    private lazy var pan=UIPanGestureRecognizer(target:self,action:#selector(panned(_:)))
    override init(frame:CGRect){super.init(frame:frame);pan.cancelsTouchesInView=false;addGestureRecognizer(pan)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?){guard let t=touches.first else{return};let p=t.location(in:self);driver?.engine.begin(x:p.x,y:p.y);driver?.wake()}
    @objc private func panned(_ recognizer:UIPanGestureRecognizer){let p=recognizer.location(in:self),v=recognizer.velocity(in:self)
        switch recognizer.state{case .began,.changed:driver?.engine.move(x:p.x,y:p.y);case .ended:driver?.engine.end(x:p.x,y:p.y,velocityY:v.y);case .cancelled,.failed:driver?.engine.end(x:p.x,y:p.y,velocityY:0,cancelled:true);default:break}
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?){guard pan.state == .possible || pan.state == .failed,let t=touches.first else{return};let p=t.location(in:self);driver?.engine.end(x:p.x,y:p.y,velocityY:0)}
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?){driver?.engine.end(x:0,y:0,velocityY:0,cancelled:true)}
}
