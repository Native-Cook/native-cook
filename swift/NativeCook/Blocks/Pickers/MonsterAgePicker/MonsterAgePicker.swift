import SwiftUI
import UIKit

struct MonsterAgePicker: View {
    static let loop = MonsterAgePickerMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @StateObject private var clock = MonsterAgePickerClock()
    @GestureState private var pressed = false
    private let art = MonsterAgePickerArt.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"monsterAgePickerTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let size = CGSize(width:proxy.size.width,height:proxy.size.height+top+bottom)
            TimelineView(.animation(paused:pinned != nil || scene != .active || ((reduced || !autoPlay) && !clock.running))) { timeline in
                let frame = clock.sample(timeline.date,auto:autoPlay,reduced:reduced,pinned:pinned)
                Canvas { context, _ in
                    context.withCGContext { c in art.draw(c,size:size,top:top,bottom:bottom,frame:frame,chosen:clock.chosen,pressed:pressed) }
                }
                .overlay(alignment:.topLeading) {
                    if pinned == nil {
                        MonsterAgePickerTouch(clock:clock,width:size.width)
                            .frame(width:size.width-48,height:size.width*0.5)
                            .offset(x:24,y:(size.height*0.5).rounded()-size.width*0.12)
                            .accessibilityElement().accessibilityLabel("Age")
                            .accessibilityValue("\(clock.age) years old")
                            .accessibilityAdjustableAction { direction in clock.step(direction == .increment ? 1 : -1) }
                    }
                }
                .overlay(alignment:.topLeading) {
                    Button { if pinned == nil { clock.chomp() } } label: { Color.clear }
                        .buttonStyle(.plain)
                        .simultaneousGesture(DragGesture(minimumDistance:0).updating($pressed) { _, value, _ in value = true })
                        .frame(width:size.width-48,height:58)
                        .offset(x:24,y:size.height-bottom-78)
                        .accessibilityLabel("Continue with \(clock.age) years old")
                        .allowsHitTesting(pinned == nil)
                }
                .overlay(alignment:.top) {
                    let selected = pinned.map { MonsterAgePickerMotion.pinnedChosen($0) } ?? clock.chosen
                    VStack(spacing:0) {
                        Color.clear.frame(width:178,height:6)
                            .accessibilityElement().accessibilityLabel("Step 2 of 4")
                            .padding(.bottom,24)
                        Text("How old is Mia?")
                            .font(.custom("TitanOne",fixedSize:32)).foregroundStyle(.white)
                            .offset(x:-1/3,y:1/3)
                            .accessibilityAddTraits(.isHeader)
                        Text(selected >= 0 ? "Yum! \(selected+3) years old" : "Slide along the teeth")
                            .font(.custom("Nunito-Black",fixedSize:16)).foregroundStyle(.white.opacity(0.75))
                            .padding(.top,8)
                    }.padding(.top,top+10)
                }
                .overlay(alignment:.topLeading) {
                    Text("Continue").font(.custom("Nunito-Black",fixedSize:18))
                        .foregroundStyle(Color(red:59/255,green:20/255,blue:96/255))
                        .offset(x:-1/3)
                        .frame(width:size.width-48,height:54)
                        .scaleEffect(pressed ? 0.96 : 1,anchor:UnitPoint(x:0.5,y:29/54))
                        .offset(x:24,y:size.height-bottom-78)
                        .allowsHitTesting(false).accessibilityHidden(true)
                }
                .frame(width:size.width,height:size.height,alignment:.topLeading)
                .offset(y:-top)
            }
        }
        .background(Color(red:110/255,green:61/255,blue:232/255).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of:scene) { _, _ in clock.active() }
        .onChange(of:reduced) { _, _ in clock.active() }
        .onChange(of:autoPlay) { _, _ in clock.active() }
    }
}

struct MonsterAgePickerTouch: UIViewRepresentable {
    let clock: MonsterAgePickerClock
    let width: Double
    func makeCoordinator() -> MonsterAgePickerTouchCoordinator { MonsterAgePickerTouchCoordinator(clock,width) }
    func makeUIView(context:Context) -> UIView {
        let view = UIView()
        let pan = MonsterAgePickerPan(target:context.coordinator,action:#selector(MonsterAgePickerTouchCoordinator.pan(_:)))
        let velocityTracker = UIPanGestureRecognizer()
        pan.velocityTracker = velocityTracker
        view.addGestureRecognizer(velocityTracker)
        let tap = MonsterAgePickerTap(target:context.coordinator,action:#selector(MonsterAgePickerTouchCoordinator.tap(_:)))
        tap.require(toFail:pan)
        view.addGestureRecognizer(pan); view.addGestureRecognizer(tap)
        return view
    }
    func updateUIView(_ view:UIView,context:Context) { context.coordinator.width = width }
}
final class MonsterAgePickerTouchCoordinator: NSObject {
    let clock: MonsterAgePickerClock
    var width: Double
    init(_ clock:MonsterAgePickerClock,_ width:Double) { self.clock = clock; self.width = width }
    @objc func pan(_ gesture:MonsterAgePickerPan) {
        clock.pan(gesture.state,translation:gesture.translation,velocity:Double(gesture.velocityTracker?.velocity(in:gesture.view).x ?? 0))
    }
    @objc func tap(_ gesture:UITapGestureRecognizer) { clock.tap(gesture.location(in:gesture.view).x+24,width:width) }
}

// A horizontal activation range of [-6, 6], independent of vertical travel, as in Gesture.Pan.
final class MonsterAgePickerPan: UIGestureRecognizer {
    private var origin = CGPoint.zero
    weak var velocityTracker: UIPanGestureRecognizer?
    var translation = 0.0
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent) {
        guard touches.count == 1, let touch = touches.first else { state = .failed; return }
        origin = touch.location(in:view)
    }
    private func update(_ touch:UITouch) {
        let p = touch.location(in:view)
        translation = p.x-origin.x
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent) {
        guard let touch = touches.first else { return }
        update(touch)
        if state == .possible {
            if abs(translation)>6 { state = .began }
        } else if state == .began || state == .changed { state = .changed }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent) {
        if let touch = touches.first { update(touch) }
        state = state == .possible ? .failed : .ended
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent) { state = .cancelled }
    override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    override func reset() { super.reset(); translation = 0 }
}

final class MonsterAgePickerTap: UITapGestureRecognizer {
    private var beganAt = 0.0
    private var origin = CGPoint.zero
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent) {
        if let touch = touches.first { beganAt = touch.timestamp; origin = touch.location(in:view) }
        super.touchesBegan(touches,with:event)
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent) {
        if let touch = touches.first {
            let p = touch.location(in:view)
            if hypot(p.x-origin.x,p.y-origin.y)>10 { state = .failed; return }
        }
        super.touchesMoved(touches,with:event)
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent) {
        if let touch = touches.first, touch.timestamp-beganAt>0.5 { state = .failed; return }
        super.touchesEnded(touches,with:event)
    }
}
