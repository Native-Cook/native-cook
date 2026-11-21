import SwiftUI
import UIKit
import AVFoundation

struct ElasticStringQuiz:View {
    static let loop=12.0
    var autoPlay=true
    var sound=true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.displayScale) private var pixels
    @StateObject private var driver=ElasticStringQuizDriver()
    @GestureState private var dragging = false
    @State private var interaction: Interaction?

    private enum Interaction { case bead, recap, strum }
    var body:some View {
        GeometryReader { safe in
            GeometryReader { geometry in
                let scale=geometry.size.width/402,dy=(geometry.size.height/scale-874)/2,top = -dy,bottom=geometry.size.height/scale-dy
                let hudY=(max(safe.safeAreaInsets.top,20)+12)/scale-dy
                Canvas { context,_ in
                    ElasticStringQuizArtwork.draw(&context,driver.frame,top:top,bottom:bottom,hudY:hudY,pixels:scale*pixels,matched:driver.engine.matched)
                }
                .frame(width:402,height:geometry.size.height/scale)
                .contentShape(Rectangle())
                .gesture(dragGesture)
                .offset(y:dy).scaleEffect(scale,anchor:.topLeading)
                .frame(width:geometry.size.width,height:geometry.size.height,alignment:.topLeading).clipped()
            }.ignoresSafeArea()
        }.background(Color(red:43/255,green:17/255,blue:44/255).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .accessibilityElement(children:.ignore).accessibilityLabel("Synonym quiz. \(ElasticStringQuizArtwork.words[Int(driver.frame.w)])")
        .accessibilityValue("Choose anchor, gather, or wander")
        .accessibilityAction(named:"Anchor") {driver.answer(0)}
        .accessibilityAction(named:"Gather") {driver.answer(1)}
        .accessibilityAction(named:"Wander") {driver.answer(2)}
        .accessibilityAction(named:"Continue round") {driver.engine.continueRound()}
        .onChange(of: dragging) { _, isActive in
            if !isActive, interaction != nil { finishDrag(cancelled: true) }
        }
        .onAppear { configure() }.onChange(of:phase){_,_ in configure()}.onChange(of:reduced){_,_ in configure()}.onChange(of:autoPlay){_,_ in configure()}.onDisappear { driver.stop() }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($dragging) { _, active, _ in active = true }
            .onChanged { value in
                if interaction == nil { beginDrag(at: value.startLocation) }
                moveDrag(value)
            }
            .onEnded { value in
                moveDrag(value)
                finishDrag(cancelled: false)
            }
    }

    private func beginDrag(at point: CGPoint) {
        if driver.frame.recapA > 0.5 {
            interaction = .recap
        } else if (618...728).contains(point.y),
                  let bead = [81.0, 201.0, 321.0].firstIndex(where: { abs(point.x - $0) <= 44 }) {
            interaction = .bead
            driver.engine.begin(bead)
        } else {
            interaction = .strum
            driver.engine.strumBegin(x: point.x, y: point.y)
        }
        driver.wake()
    }

    private func moveDrag(_ value: DragGesture.Value) {
        switch interaction {
        case .bead:
            driver.engine.move(x: value.translation.width, y: value.translation.height, velocityY: value.velocity.height)
        case .strum:
            driver.engine.strumMove(x: value.location.x, y: value.location.y, velocityX: value.velocity.width)
        case .recap, nil:
            break
        }
    }

    private func finishDrag(cancelled: Bool) {
        switch interaction {
        case .bead: driver.engine.end(cancelled: cancelled)
        case .strum: driver.engine.strumEnd()
        case .recap: if !cancelled { driver.engine.continueRound() }
        case nil: break
        }
        interaction = nil
    }

    private func configure(){driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active,sound:sound)}
}
private final class ElasticStringQuizDisplayTarget:NSObject {
    weak var owner:ElasticStringQuizDriver?
    @objc func tick(_ link:CADisplayLink){owner?.tick(link)}
}
final class ElasticStringQuizDriver:ObservableObject {
    let engine=ElasticStringQuizEngine(script:ElasticStringQuizArtwork.script(),words:ElasticStringQuizArtwork.words)
    @Published var frame=ElasticStringQuizMotion.frameAt(0,ElasticStringQuizArtwork.script())
    private var target:ElasticStringQuizDisplayTarget?,link:CADisplayLink?,previous=0.0,active=true,sound=true
    private var players:[AVAudioPlayer]=[]
    init(){
        for name in ["pluck-c4","pluck-e4","pluck-g4","resolve","snap"] {
            if let url=Bundle.main.url(forResource:"ElasticStringQuiz-"+name,withExtension:"wav"),let player=try? AVAudioPlayer(contentsOf:url){player.prepareToPlay();players.append(player)}
        }
        engine.onHaptic={ kind in switch kind {case 0:UIImpactFeedbackGenerator(style:.soft).impactOccurred();case 1:UIImpactFeedbackGenerator(style:.light).impactOccurred();case 2:UIImpactFeedbackGenerator(style:.rigid).impactOccurred();default:UISelectionFeedbackGenerator().selectionChanged()} }
        engine.onSound={ [weak self] i,level in guard let self,self.sound,self.players.indices.contains(i)else{return};let player=self.players[i];player.currentTime=0;player.volume=Float(max(0.15,min(level,1)));player.play() }
    }
    func configure(autoPlay:Bool,reduced:Bool,active:Bool,sound:Bool){self.active=active;self.sound=sound;engine.configure(autoPlay:autoPlay,reduced:reduced);frame=engine.frame;wake()}
    func wake(){if link==nil {let target=ElasticStringQuizDisplayTarget();target.owner=self;self.target=target;let link=CADisplayLink(target:target,selector:#selector(ElasticStringQuizDisplayTarget.tick(_:)));link.add(to:.main,forMode:.common);self.link=link};previous=0;link?.isPaused = !active || engine.pin != nil}
    func tick(_ link:CADisplayLink){let dt=previous==0 ? 1/60.0 : min(max(link.timestamp-previous,0),0.05);previous=link.timestamp;engine.advance(dt);frame=engine.frame}
    func answer(_ i:Int){engine.begin(i);engine.end();wake()}
    func stop(){link?.invalidate();link=nil;target=nil;previous=0;players.forEach{$0.stop()}}
    deinit{link?.invalidate()}
}
