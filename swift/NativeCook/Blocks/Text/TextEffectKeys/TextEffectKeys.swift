import SwiftUI
import UIKit

struct TextEffectKeys:View {
    static let loop=8.0
    var autoPlay=true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var driver=TextEffectKeysDriver()
    var body:some View {
        GeometryReader{safe in GeometryReader{geometry in
            let scale=geometry.size.width/402,oy=geometry.size.height/scale-874,nav=max(safe.safeAreaInsets.top/scale+4,62+min(oy,0))
            let fit=min(1,max(0.35,(505+oy-nav-56)/(505-85))),conversation=1-(1-fit)*min(1,max(0,(-oy-16)/140))
            ZStack(alignment:.topLeading){Canvas{c,_ in TextEffectKeysArtwork.draw(&c,driver.frame,geo:driver.engine.geo,oy:oy,navY:nav,conversation:conversation,height:geometry.size.height/scale)};TextEffectKeysTouchSurface(driver:driver,oy:oy,conversation:conversation)}.frame(width:402,height:geometry.size.height/scale).scaleEffect(scale,anchor:.topLeading).frame(width:geometry.size.width,height:geometry.size.height,alignment:.topLeading).clipped()
        }.ignoresSafeArea()}.background(Color(red:15/255,green:13/255,blue:11/255).ignoresSafeArea()).preferredColorScheme(.dark)
        .accessibilityElement(children:.ignore).accessibilityLabel("Message to Odile. Text effects")
        .accessibilityValue("Whaaaaat")
        .accessibilityAction(named:"Send message"){driver.engine.send();driver.wake()}
        .accessibilityAction(named:"Big"){driver.engine.choose(0);driver.wake()}
        .accessibilityAction(named:"Small"){driver.engine.choose(1);driver.wake()}
        .accessibilityAction(named:"Shake"){driver.engine.choose(2);driver.wake()}
        .accessibilityAction(named:"Nod"){driver.engine.choose(3);driver.wake()}
        .accessibilityAction(named:"Explode"){driver.engine.choose(4);driver.wake()}
        .accessibilityAction(named:"Ripple"){driver.engine.choose(5);driver.wake()}
        .accessibilityAction(named:"Bloom"){driver.engine.choose(6);driver.wake()}
        .accessibilityAction(named:"Jitter"){driver.engine.choose(7);driver.wake()}
        .onAppear{configure()}.onChange(of:phase){_,_ in configure()}.onChange(of:reduced){_,_ in configure()}.onChange(of:autoPlay){_,_ in configure()}.onDisappear{driver.stop()}
    }
    private func configure(){driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active)}
}
private final class TextEffectKeysDisplayTarget:NSObject{weak var owner:TextEffectKeysDriver?;@objc func tick(_ link:CADisplayLink){owner?.tick(link)}}
final class TextEffectKeysDriver:ObservableObject {
    let engine=TextEffectKeysEngine(geo:TextEffectKeysArtwork.geo())
    @Published var frame=TextEffectKeysMotion.frameAt(0,TextEffectKeysArtwork.geo())
    private var target:TextEffectKeysDisplayTarget?,link:CADisplayLink?,previous=0.0,active=true
    init(){engine.onHaptic={kind in switch kind{case 0:UISelectionFeedbackGenerator().selectionChanged();case 1:UIImpactFeedbackGenerator(style:.light).impactOccurred();case 2:UIImpactFeedbackGenerator(style:.rigid).impactOccurred();default:UIImpactFeedbackGenerator(style:.medium).impactOccurred()}}}
    func configure(autoPlay:Bool,reduced:Bool,active:Bool){self.active=active;engine.configure(autoPlay:autoPlay,reduced:reduced);frame=engine.frame;wake()}
    func wake(){if link==nil{let target=TextEffectKeysDisplayTarget();target.owner=self;self.target=target;let link=CADisplayLink(target:target,selector:#selector(TextEffectKeysDisplayTarget.tick(_:)));link.add(to:.main,forMode:.common);self.link=link};previous=0;link?.isPaused = !active || engine.pin != nil}
    func tick(_ link:CADisplayLink){let dt=previous==0 ? 1/60.0 : min(max(link.timestamp-previous,0),0.05);previous=link.timestamp;engine.advance(dt);frame=engine.frame}
    func stop(){link?.invalidate();link=nil;target=nil;previous=0}
    deinit{link?.invalidate()}
}
private struct TextEffectKeysTouchSurface:UIViewRepresentable{
    let driver:TextEffectKeysDriver;let oy:Double,conversation:Double
    func makeUIView(context:Context)->TextEffectKeysTouchView{let v=TextEffectKeysTouchView();v.driver=driver;return v}
    func updateUIView(_ view:TextEffectKeysTouchView,context:Context){view.driver=driver;view.oy=oy;view.conversation=conversation}
}
private final class TextEffectKeysTouchView:UIView{
    weak var driver:TextEffectKeysDriver?;var oy=0.0,conversation=1.0
    private lazy var pan=UIPanGestureRecognizer(target:self,action:#selector(panned(_:)))
    override init(frame:CGRect){super.init(frame:frame);pan.cancelsTouchesInView=false;addGestureRecognizer(pan)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    private func point(_ p:CGPoint)->CGPoint{let y=p.y-oy;return CGPoint(x:p.x,y:y<505 ? 505+(y-505)/conversation : y)}
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?){guard let t=touches.first else{return};let p=point(t.location(in:self));driver?.engine.begin(x:p.x,y:p.y);driver?.wake()}
    @objc private func panned(_ recognizer:UIPanGestureRecognizer){let raw=recognizer.location(in:self),p=point(raw),v=recognizer.velocity(in:self)
        switch recognizer.state{case .began,.changed:driver?.engine.move(x:p.x,y:p.y,vx:v.x,vy:v.y/(raw.y-oy<505 ? conversation : 1));case .ended:driver?.engine.end(x:p.x,y:p.y);case .cancelled,.failed:driver?.engine.end(x:p.x,y:p.y,cancelled:true);default:break}
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?){guard pan.state == .possible || pan.state == .failed,let t=touches.first else{return};let p=point(t.location(in:self));driver?.engine.end(x:p.x,y:p.y)}
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?){driver?.engine.end(x:0,y:0,cancelled:true)}
}
