import SwiftUI
import UIKit

final class BundlePackBoxClock: ObservableObject {
    @Published var running = false
    @Published var pressed = false
    @Published var idle = false
    let driver = BundlePackBoxDriver()
    private var date: Date?
    private var idleDate: Date?
    private let medium = UIImpactFeedbackGenerator(style:.medium)
    private let notification = UINotificationFeedbackGenerator()
    init() { driver.success = { [weak self] in self?.notification.notificationOccurred(.success) } }
    func sample(_ date: Date,_ auto: Bool,_ reduced: Bool,_ pinned: Double?) -> BundlePackBoxMotion.Frame {
        if let pinned { return BundlePackBoxMotion.at(pinned) }
        let wall = min(0.5,max(0,self.date.map { date.timeIntervalSince($0) } ?? 0.016))
        self.date = date
        let frame = driver.step(wall,auto && !reduced,reduced)
        if !driver.pinch && !driver.scripted && abs(driver.p-driver.target)<0.0001 && abs(driver.v)<0.001 && (reduced || driver.p > 0.9999) && !idle {
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.driver.pinch, !self.driver.scripted,
                      abs(self.driver.p-self.driver.target)<0.0001, abs(self.driver.v)<0.001 else { return }
                self.idleDate = self.date
                self.idle = true; self.running = false
            }
        }
        return frame
    }
    func activate() { date = nil }
    func toggle() { resume(); medium.impactOccurred(); driver.toggle() }
    func resume() {
        if idle, let idleDate {
            // The RN clock keeps ticking while the packed art is motionless.
            // Catch up that invisible interval before resuming the per-frame spring.
            let touchDate = Date()
            driver.now += max(0,touchDate.timeIntervalSince(idleDate))
            date = touchDate
        }
        idleDate = nil; idle = false; running = true
    }
    func pinch(_ state: UIGestureRecognizer.State,_ scale: Double,_ velocity: Double) {
        switch state {
        case .began: resume(); driver.begin()
        case .changed: driver.update(scale)
        case .ended,.failed,.cancelled:
            guard driver.pinch else { return }
            driver.end(velocity); medium.impactOccurred()
        default: break
        }
    }
}

struct BundlePackBox: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = BundlePackBoxClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"bundlePackBoxTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
            GeometryReader { proxy in
                let page = BundlePackBoxResources.shared.page(proxy.size,top,bottom)
                TimelineView(.animation(paused:pinned != nil || phase != .active || clock.idle || (reduced && !clock.running))) { time in
                    let f = clock.sample(time.date,autoPlay,reduced,pinned)
                    Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { c,size in
                        BundlePackBoxArt(size:size).draw(c,f,reduced)
                        if f.stamp > 0 {
                            var stamp = c; let ss = page.stampSize
                            stamp.opacity = f.stamp
                            stamp.translateBy(x:size.width/2+30+ss.width/2,y:(size.height*0.29).rounded()-40+ss.height/2)
                            stamp.rotate(by:.degrees(-8)); let scale = 1.4-0.4*f.stamp; stamp.scaleBy(x:scale,y:scale)
                            stamp.draw(Image(uiImage:page.stamp),in:CGRect(x:-ss.width/2,y:-ss.height/2,width:ss.width,height:ss.height))
                        }
                        c.draw(Image(uiImage:page.base),in:CGRect(origin:.zero,size:size))
                        var button = c
                        button.translateBy(x:page.cta.midX,y:page.cta.midY)
                        let scale = clock.pressed ? 0.96 : 1.0; button.scaleBy(x:scale,y:scale)
                        button.draw(Image(uiImage:page.buttons[f.packed ? 1 : 0]),in:CGRect(x:-page.cta.width/2,y:-28,width:page.cta.width,height:56))
                    }
                    .overlay {
                        BundlePackBoxTouchSurface(clock:clock,cta:page.cta,top:top,sheetTop:page.sheet.minY,packed:f.packed,stampVisible:f.stamp > 0.01,labels:page.labels,pinned:pinned != nil)
                    }
                }
            }.ignoresSafeArea()
        }
        .background(BundlePackBoxArt.color(0xF6E9DA).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { clock.activate() }
        .onChange(of:phase) { _,_ in clock.activate() }
        .onChange(of:reduced) { _,_ in clock.idle = false; clock.running = false; clock.activate() }
    }
}

struct BundlePackBoxTouchSurface: UIViewRepresentable {
    let clock: BundlePackBoxClock
    let cta: CGRect
    let top: Double
    let sheetTop: Double
    let packed: Bool
    let stampVisible: Bool
    let labels: [(String,CGRect)]
    let pinned: Bool
    func makeUIView(context: Context) -> BundlePackBoxTouchView { BundlePackBoxTouchView() }
    func updateUIView(_ view: BundlePackBoxTouchView,context: Context) {
        view.clock = pinned ? nil : clock; view.cta = cta; view.top = top; view.sheetTop = sheetTop; view.isUserInteractionEnabled = !pinned
        view.labels = labels
        view.updateAccessibility(packed,stampVisible)
    }
}
final class BundlePackBoxTouchView: UIView, UIGestureRecognizerDelegate {
    weak var clock: BundlePackBoxClock?
    var cta = CGRect.zero
    var top = 0.0
    var sheetTop = 0.0
    var labels: [(String,CGRect)] = []
    private var trackingButton = false
    private var accessibilityKey = ""
    override init(frame:CGRect) {
        super.init(frame:frame)
        backgroundColor = .clear
        let pinch = UIPinchGestureRecognizer(target:self,action:#selector(onPinch(_:))); pinch.delegate = self; addGestureRecognizer(pinch)
        isAccessibilityElement = false
    }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        let y = touch.location(in:self).y
        return y >= top+60 && y <= min(sheetTop,top+60+bounds.height*0.56)
    }
    @objc private func onPinch(_ recognizer:UIPinchGestureRecognizer) {
        let y = recognizer.location(in:self).y
        if recognizer.state == .began && !(y >= top+60 && y <= min(sheetTop,top+60+bounds.height*0.56)) { recognizer.isEnabled = false; recognizer.isEnabled = true; return }
        clock?.pinch(recognizer.state,recognizer.scale,recognizer.velocity)
    }
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
        guard let p = touches.first?.location(in:self) else { return }
        trackingButton = cta.contains(p)
        if trackingButton { clock?.pressed = true }
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
        if trackingButton { clock?.pressed = touches.first.map { cta.contains($0.location(in:self)) } ?? false }
    }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?) {
        if trackingButton && (touches.first.map { cta.contains($0.location(in:self)) } ?? false) { clock?.toggle() }
        trackingButton = false; clock?.pressed = false
    }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?) { trackingButton = false; clock?.pressed = false }
    func updateAccessibility(_ packed:Bool,_ stampVisible:Bool) {
        let key = "\(packed):\(stampVisible):\(bounds):\(cta):\(top)"
        guard key != accessibilityKey else { return }
        accessibilityKey = key
        let back = UIAccessibilityElement(accessibilityContainer:self); back.accessibilityLabel = "Back"; back.accessibilityTraits = .button; back.accessibilityFrameInContainerSpace = CGRect(x:16,y:top+6,width:44,height:44)
        let brand = UIAccessibilityElement(accessibilityContainer:self); brand.accessibilityLabel = "Kitform"; brand.accessibilityFrameInContainerSpace = CGRect(x:bounds.width/2-50,y:top+6,width:100,height:44)
        let bag = UIAccessibilityElement(accessibilityContainer:self); bag.accessibilityLabel = "Bag, 1 item"; bag.accessibilityTraits = .button; bag.accessibilityFrameInContainerSpace = CGRect(x:bounds.width-60,y:top+6,width:44,height:44)
        let image = UIAccessibilityElement(accessibilityContainer:self); image.accessibilityLabel = packed ? "The kit packed and taped in its box" : "The kit floating apart. Pinch to pack it"; image.accessibilityTraits = .image; image.accessibilityFrameInContainerSpace = CGRect(x:0,y:top+60,width:bounds.width,height:bounds.height*0.56)
        let button = BundlePackBoxAccessibilityButton(accessibilityContainer:self); button.clock = clock; button.accessibilityLabel = packed ? "Unpack the bundle" : "Pack the bundle"; button.accessibilityTraits = .button; button.accessibilityFrameInContainerSpace = cta
        var elements: [UIAccessibilityElement] = [back,brand,bag,image]
        if stampVisible {
            let stamp = UIAccessibilityElement(accessibilityContainer:self); stamp.accessibilityLabel = "READY TO SHIP"; stamp.accessibilityFrameInContainerSpace = CGRect(x:bounds.width/2+30,y:(bounds.height*0.29).rounded()-40,width:90,height:44)
            elements.append(stamp)
        }
        for (label,frame) in labels.dropLast() {
            let element = UIAccessibilityElement(accessibilityContainer:self)
            element.accessibilityLabel = label; element.accessibilityFrameInContainerSpace = frame
            elements.append(element)
        }
        elements.append(button)
        if let (label,frame) = labels.last {
            let hint = UIAccessibilityElement(accessibilityContainer:self)
            hint.accessibilityLabel = label; hint.accessibilityFrameInContainerSpace = frame
            elements.append(hint)
        }
        accessibilityElements = elements
    }
}
final class BundlePackBoxAccessibilityButton: UIAccessibilityElement {
    weak var clock: BundlePackBoxClock?
    override func accessibilityActivate() -> Bool { clock?.toggle(); return true }
}
