import SwiftUI
import UIKit

struct SerumDropperRoutine: View {
    static let loop = SerumDropperRoutineMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var driver = SerumDropperRoutineDriver()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"serumDropperRoutineTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    private var windowInsets: UIEdgeInsets {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first(where: { $0.isKeyWindow })?.safeAreaInsets ?? .zero
    }
    var body: some View {
        GeometryReader { proxy in
            SerumDropperRoutineStage(driver:driver,size:proxy.size,safe:proxy.safeAreaInsets,windowInsets:windowInsets,
                                     autoPlay:autoPlay,reduced:reduced,pinned:pinned,background:scenePhase == .background)
        }
        .ignoresSafeArea()
        .background(Color.white)
        .preferredColorScheme(.light)
        .onAppear { driver.reset(autoPlay && !reduced) }
        .onChange(of:scenePhase) { _,_ in driver.active() }
        .onChange(of:reduced) { _,_ in driver.reset(autoPlay && !reduced,preserveTicks:true) }
        .onChange(of:autoPlay) { _,_ in driver.reset(autoPlay && !reduced,preserveTicks:true) }
    }
}


/// The block's screen for one size. A separate view that observes the driver itself, so it redraws
/// whenever the driver's published state changes: read inside GeometryReader's closure, that state
/// was never seen again after the first frame (the timeline stayed paused and the ticks stale).
private struct SerumDropperRoutineStage: View {
    @ObservedObject var driver: SerumDropperRoutineDriver
    let size: CGSize
    let safe: EdgeInsets
    let windowInsets: UIEdgeInsets
    let autoPlay: Bool
    let reduced: Bool
    let pinned: Double?
    let background: Bool
    var body: some View {
        let paused = pinned != nil || background || (!(autoPlay && !reduced && !driver.manualMode) && !driver.running)
        let ticks = driver.ticks
        let insets = windowInsets
        let l = SerumDropperRoutineLayout(w:size.width,h:size.height,top:max(safe.top,insets.top),bottom:max(safe.bottom,insets.bottom))
        let resources = SerumDropperRoutineResources.shared
        let layers = resources.layers(l)
        TimelineView(.animation(paused:paused)) { timeline in
            let f = driver.sample(timeline.date,auto:autoPlay && !reduced,pinned:pinned)
            let tip = l.tip(f.lift)
            ZStack(alignment:.topLeading) {
                Canvas { c,size in
                    c.draw(layers.0,in:CGRect(origin:.zero,size:size))
                }
                SerumDropperRoutineProducts(layout:l,lift:f.lift,squeeze:f.squeeze)
                    .allowsHitTesting(false)
                SerumDropperRoutineMetal(layout:l,frame:f,tip:tip,time:driver.elapsed)
                    .allowsHitTesting(false)
                Canvas { c,size in
                    for (i,b) in SerumDropperRoutineLayout.cards.enumerated() {
                        let x = b.0*l.sx+134*l.sx, y = l.y(b.1)+32
                        // Pinned frames are independent of the driver's live checkbox state.
                        let checked = i == 0 ? f.done > 0.5 : ticks[i]
                        c.draw(checked ? resources.fullRingView : resources.emptyRingView,in:CGRect(x:x-15,y:y-15,width:30,height:30))
                        if i == 0 || checked {
                            let amount = i == 0 ? f.done : 1
                            var mark = c; mark.opacity = amount
                            let s = 0.6+0.4*amount
                            mark.draw(resources.checkView,in:CGRect(x:x-6.5*s,y:y-5*s,width:13*s,height:10*s))
                        }
                    }
                    c.draw(layers.1,in:CGRect(origin:.zero,size:size))
                    if f.finger > 0 {
                        var finger = c; finger.opacity = f.finger
                        let x = f.fingerAt == 0 ? 160*l.sx : l.bx+6
                        let y = f.fingerAt == 0 ? l.y(308)+32 : l.btop+l.bh*0.55
                        let s = 1-0.12*f.press
                        finger.draw(resources.fingerView,in:CGRect(x:x-22*s,y:y-22*s,width:44*s,height:44*s))
                    }
                }
                .allowsHitTesting(false)
                controls(l,f,ticks).allowsHitTesting(pinned == nil)
            }
        }
        .frame(width:size.width,height:size.height,alignment:.topLeading)
        .clipped()
    }
    private func controls(_ l: SerumDropperRoutineLayout,_ f: SerumDropperRoutineMotion.Frame,_ ticks: [Bool]) -> some View {
        ZStack(alignment:.topLeading) {
            SerumDropperRoutineHoldSurface { down in
                if down { driver.begin(reduced) } else { driver.end(reduced) }
            }
                .frame(width:165*l.sx-46,height:210*l.k-40)
                .accessibilityElement().accessibilityAddTraits(.isButton)
                .accessibilityLabel("Fruit oil. Touch and hold to apply it")
                .offset(x:26*l.sx,y:l.y(308)+40)
            ForEach(0..<4,id:\.self) { i in
                let b = SerumDropperRoutineLayout.cards[i]
                Button(action:{ driver.toggle(i) }) { Color.clear.frame(width:44,height:44).contentShape(Rectangle()) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Step \(i+1) done")
                    .accessibilityValue((i == 0 ? f.done > 0.5 : ticks[i]) ? "Checked" : "Unchecked")
                    .accessibilityAddTraits((i == 0 ? f.done > 0.5 : ticks[i]) ? .isSelected : [])
                    .offset(x:(b.0+134)*l.sx-22,y:l.y(b.1)+10)
            }
            ForEach(0..<3,id:\.self) { i in
                SerumDropperRoutinePressButton(bitmap:SerumDropperRoutineResources.shared.buttons[i],reduced:reduced)
                    .frame(width:i == 2 ? 72 : 44,height:i == 2 ? 72 : 44)
                    .accessibilityLabel(["Back","More","Scan your face"][i])
                    .offset(x:i == 2 ? 200*l.sx-36 : (i == 0 ? 46 : 352)*l.sx-22,y:i == 2 ? l.y(795)-36 : l.y(98)-22)
            }
            Text("Daily routine").hidden().accessibilityHidden(false).accessibilityAddTraits(.isHeader).offset(x:80*l.sx,y:l.y(100)-12)
            ForEach(0..<5,id:\.self) { i in
                Color.clear.frame(width:75*l.sx,height:100*l.k).accessibilityElement()
                    .accessibilityLabel("\(["Mon","Tue","Wed","Thu","Fri"][i]) \(i+5)\(i == 1 ? ", today" : "")")
                    .offset(x:[25.0,105,185,264,345][i]*l.sx,y:l.y(150))
            }
            ForEach(0..<4,id:\.self) { i in
                let p = SerumDropperRoutineLayout.labels[i]
                Color.clear.frame(width:165*l.sx,height:64).accessibilityElement()
                    .accessibilityLabel("\(SerumDropperRoutineResources.names[i].replacingOccurrences(of:"\n",with:" ")), Step \(i+1)")
                    .offset(x:p.0*l.sx,y:l.y(p.1)-10)
            }
        }
    }
}

struct SerumDropperRoutinePressButton: View {
    let bitmap: UIImage
    let reduced: Bool
    @State private var pressed = false
    var body: some View {
        Image(uiImage:bitmap).resizable()
            .scaleEffect(pressed && !reduced ? 0.96 : 1)
            .opacity(pressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:pressed)
            .overlay { SerumDropperRoutinePressSurface { pressed = $0 } }
            .accessibilityElement().accessibilityAddTraits(.isButton)
            .accessibilityAction {}
    }
}
struct SerumDropperRoutinePressSurface: UIViewRepresentable {
    var action: (Bool)->Void
    func makeUIView(context: Context) -> SerumDropperRoutinePressView {
        let view = SerumDropperRoutinePressView(); view.action = action; return view
    }
    func updateUIView(_ view: SerumDropperRoutinePressView,context: Context) { view.action = action }
}
final class SerumDropperRoutinePressView: UIView {
    var action: ((Bool)->Void)?
    override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) { action?(true) }
    override func touchesMoved(_ touches: Set<UITouch>,with event: UIEvent?) {
        if let point = touches.first?.location(in:self) { action?(bounds.insetBy(dx:-16,dy:-16).contains(point)) }
    }
    override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) { action?(false) }
    override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) { action?(false) }
}

struct SerumDropperRoutineHoldSurface: UIViewRepresentable {
    var action: (Bool)->Void
    func makeUIView(context: Context) -> SerumDropperRoutineHoldView {
        let view = SerumDropperRoutineHoldView(); view.action = action; return view
    }
    func updateUIView(_ view: SerumDropperRoutineHoldView,context: Context) { view.action = action }
}
final class SerumDropperRoutineHoldView: UIView {
    var action: ((Bool)->Void)?
    private var down = false
    override func touchesBegan(_ touches: Set<UITouch>,with event: UIEvent?) {
        if !down { down = true; action?(true) }
    }
    override func touchesEnded(_ touches: Set<UITouch>,with event: UIEvent?) { finish() }
    override func touchesCancelled(_ touches: Set<UITouch>,with event: UIEvent?) { finish() }
    private func finish() { if down { down = false; action?(false) } }
}
