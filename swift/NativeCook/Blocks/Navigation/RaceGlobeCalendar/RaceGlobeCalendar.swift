import SwiftUI
import UIKit

struct RaceGlobeCalendar:View {
 static let loop=10.5
 var autoPlay=true
 @Environment(\.accessibilityReduceMotion) private var reduced
 @Environment(\.scenePhase) private var phase
 @State private var engine=RaceGlobeCalendarEngine()
 @State private var down=false
 @State private var active=false
 @State private var downAt=0.0
 @State private var moved=0.0
 @GestureState private var fingerDown=false
 private let art=RaceGlobeCalendarArtwork.shared,overlay=RaceGlobeCalendarOverlay()
 var body:some View {
  GeometryReader { geo in
   let s=Double(geo.size.width)/402,ch=Double(geo.size.height)/s,top=ch-224
   TimelineView(.animation(minimumInterval:1/60,paused:phase != .active)) { timeline in
    let _=update(timeline.date,top)
    let f=engine.frame,shader=RaceGlobeCalendarUniforms.shader(f,art.atlas,1/(s*UIScreen.main.scale))
    Canvas { c,_ in
     c.fill(Path(CGRect(x:0,y:0,width:402,height:ch)),with:.color(RaceGlobeCalendarArtwork.color(0x020308)))
     overlay.stars(&c,f.t);c.fill(Path(CGRect(x:0,y:0,width:402,height:ch)),with:.shader(shader));overlay.draw(&c,f,top,shader)
    }.frame(width:402,height:ch,alignment:.topLeading).drawingGroup(opaque:true,colorMode:.nonLinear).scaleEffect(s,anchor:.topLeading)
     .frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading).contentShape(Rectangle()).gesture(finger(s))
   }
  }.ignoresSafeArea().background(Color.black).preferredColorScheme(.dark)
   .accessibilityElement(children:.ignore).accessibilityLabel("Race globe calendar. Swipe event cards to fly between venues. Drag Earth to rotate, or hold and slide to change the race hour.")
   .accessibilityAction(named:Text("Next race")) { engine.select(1) }
   .accessibilityAction(named:Text("Previous race")) { engine.select(-1) }
   .accessibilityAction(named:Text("Shuffle race")) { engine.shuffle() }
   .accessibilityAdjustableAction { direction in engine.select(direction == .increment ? 1 : -1) }
   .onChange(of:fingerDown) { _,value in if !value && down { cancel() } }
   .onChange(of:phase) { _,value in if value != .active { cancel() };engine.suspend() }
 }
 private func update(_ date:Date,_ top:Double) {
  engine.configure(autoPlay,reduced);engine.cardTop=top;engine.buzz={ cue in if cue=="selection" { UISelectionFeedbackGenerator().selectionChanged() } else { UIImpactFeedbackGenerator(style:cue=="medium" ? .medium : cue=="soft" ? .soft : .light).impactOccurred() } };engine.step(date.timeIntervalSinceReferenceDate)
  if down && !active && engine.drag==0 && moved<6 && engine.now-downAt>=0.38 { engine.longPress() }
 }
 private func finger(_ s:Double)->some Gesture {
  DragGesture(minimumDistance:0).updating($fingerDown) { _,state,_ in state=true }.onChanged { v in
   if !down { down=true;active=false;moved=0;downAt=engine.now;engine.down(v.startLocation.x/s,v.startLocation.y/s) }
   moved=max(moved,hypot(v.translation.width,v.translation.height))
   if !active && moved>=6 && engine.drag != 3 { active=true;engine.startDrag() }
   if active || engine.drag==3 { engine.move(v.translation.width/s,v.translation.height/s) }
  }.onEnded { v in
   engine.tapPosition(v.location.x/s,v.location.y/s);engine.up(v.velocity.width/s,v.velocity.height/s,true,!active && engine.drag==0 && moved<10);down=false;active=false;moved=0
  }
 }
 private func cancel() { if down { engine.up(0,0,false,false) };down=false;active=false;moved=0 }
}
