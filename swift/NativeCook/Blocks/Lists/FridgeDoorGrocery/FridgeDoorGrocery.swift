import SwiftUI

struct FridgeDoorGrocery: View {
    static let loop = 9.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = FridgeDoorGroceryClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"fridgeDoorGroceryTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let g = FridgeDoorGroceryMotion.Geo(full.size,safe.safeAreaInsets.top)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let f = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                    Canvas(opaque:false,colorMode:.nonLinear) { c,size in
                        FridgeDoorGroceryArt().draw(c,size,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom,f,reduced)
                    }
                    .accessibilityHidden(true)
                    .overlay(alignment:.topLeading) {
                        VStack(alignment:.leading,spacing:0) {
                            Color.clear.frame(width:80,height:19).accessibilityElement().accessibilityLabel("Pantry")
                            Color.clear.frame(width:180,height:38).accessibilityElement().accessibilityLabel("Your fridge")
                        }.offset(x:20,y:safe.safeAreaInsets.top+8).allowsHitTesting(false)
                    }
                    .overlay(alignment:.bottomLeading) {
                        Color.clear.frame(width:full.size.width-40,height:56)
                            .accessibilityElement().accessibilityLabel("2 items running low · tap to restock")
                            .padding(.leading,20).padding(.bottom,safe.safeAreaInsets.bottom+20)
                            .allowsHitTesting(false)
                    }
                    .overlay(alignment:.topLeading) {
                        FridgeDoorGroceryTouchView { state,p,tr,v in
                            guard pinned == nil else { return }
                            clock.touch(state,CGPoint(x:p.x+g.x-30,y:p.y+g.y),tr,v,g)
                        }
                        .frame(width:g.w+80,height:g.h)
                        .offset(x:g.x-30,y:g.y)
                        .accessibilityElement(children:.ignore)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("Fridge. Drag the door to open it, tap an item to add it to the basket. Milk and eggs are running low")
                        .accessibilityAction { if pinned == nil { clock.tap(CGPoint(x:g.x,y:g.y),g) } }
                    }
                    .overlay(alignment:.topTrailing) {
                        Color.clear.frame(width:48,height:48)
                            .accessibilityElement().accessibilityLabel("Basket, \(f.bag) items")
                            .padding(.trailing,20).padding(.top,safe.safeAreaInsets.top+13)
                    }
                }
                .frame(width:full.size.width,height:full.size.height,alignment:.topLeading)
                .background(FridgeDoorGroceryArt.color(0xFBEFE2))
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .persistentSystemOverlays(.hidden)
        .onChange(of:scenePhase) { _,_ in clock.active() }
        .onChange(of:reduced) { _,_ in clock.reset() }
        .onChange(of:autoPlay) { _,_ in clock.reset() }
    }
}
