import SwiftUI
import UIKit

struct LiquidArtDeck: View {
  static let loop = 9.0
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scene
  @StateObject private var clock = LiquidArtDeckClock()
  private let art = LiquidArtDeckResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let v = UserDefaults.standard.object(forKey:"liquidArtDeckTime") { return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0 }
    #endif
    return nil
  }
  private func color(_ hex: Int) -> Color { LiquidArtDeckResources.color(hex) }
  private func shader(_ size: CGSize, _ seed: Double, _ sx: Double, _ sy: Double, _ phase: Double, _ offset: CGPoint, _ dim: Double) -> Shader {
    ShaderLibrary.liquidArtDeckArt(.float2(size),.float(seed),.float2(sx,sy),.float(phase),.float2(offset),.float(dim))
  }
  private func chrome(_ w: Double, _ h: Double, _ seed: Double, _ sx: Double, _ sy: Double, _ ph: Double, _ offset: CGPoint, _ dim: Double = 0) -> some View {
    Rectangle().fill(.white).colorEffect(shader(CGSize(width:w,height:h),seed,sx,sy,ph,offset,dim)).frame(width:w,height:h)
  }
  private func card(_ i: Int, _ d: LiquidArtDeckMotion.Deck, _ ph: Double) -> some View {
    let live = i == d.cur || i == d.fly
    let rawDim = i == d.fly ? 0 : 0.55*min(abs(d.ds[i]),1)
    let dim = rawDim > 0.01 ? rawDim : 0
    return ZStack(alignment:.topLeading) {
      Image(uiImage:art.cards[i]).resizable().frame(width:382,height:484)
      RoundedRectangle(cornerRadius:26).fill(color(0x0f0f12).opacity(dim)).frame(width:282,height:384).offset(x:50,y:50)
      chrome(262,290,LiquidArtDeckMotion.seeds[i],live && !reduced ? d.sx : 0,live && !reduced ? d.sy : 0,ph+Double(i),CGPoint(x:-131,y:-182),dim)
        .clipShape(RoundedRectangle(cornerRadius:18)).offset(x:60,y:60)
    }.frame(width:382,height:484,alignment:.topLeading)
  }
  private func icon(_ kind: Int, _ fill: Color? = nil) -> some View {
    Canvas { c,_ in
      let p = LiquidArtDeckResources.icon(kind)
      if let fill { c.fill(p,with:.color(fill)) }
      else { c.stroke(p,with:.color(.white.opacity(0.5)),style:StrokeStyle(lineWidth:1.9*26/24,lineCap:.round,lineJoin:.round)) }
    }.frame(width:26,height:26)
  }
  private func action(_ kind: Int, _ label: String) -> some View {
    Button { if pinned == nil { clock.action(kind,reduced) } } label: {
      ZStack {
        Circle().fill(color(kind == 1 ? 0xe8ff8a : 0x22222a))
        Circle().strokeBorder(.white.opacity(0.12),lineWidth:1/3)
        if kind == 1 { icon(0,LiquidArtDeckResources.canvasColor(0x0f0f12)) }
        else { Text(kind == 2 ? "✕" : "↺").font(.custom("Inter-SemiBold",fixedSize:20)).foregroundStyle(.white).offset(y:kind == 2 ? -1 : 0) }
      }.frame(width:kind == 3 ? 46 : 58,height:kind == 3 ? 46 : 58)
    }.buttonStyle(LiquidArtDeckPressStyle()).accessibilityLabel(label)
  }
  var body: some View {
    GeometryReader { safe in
    GeometryReader { proxy in
      let windowInsets = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }.first { $0.isKeyWindow }?.safeAreaInsets ?? .zero
      let w = proxy.size.width, h = proxy.size.height
      let top = max(safe.safeAreaInsets.top,windowInsets.top), bottom = max(safe.safeAreaInsets.bottom,windowInsets.bottom)
      let cy = top+340, tab = h-bottom-34
      TimelineView(.animation(paused:pinned != nil || scene != .active)) { timeline in
        let sample = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
        let d = sample.0, now = sample.1, ph = 2 * Double.pi * sample.2 / 9
        let poses = LiquidArtDeckMotion.poses(d,now,w,cy,tab)
        ZStack(alignment:.topLeading) {
          color(0x0f0f12)
          HStack(alignment:.center) {
            Text("Favorites").font(.custom("Outfit-Medium",fixedSize:22)).foregroundStyle(.white).accessibilityAddTraits(.isHeader)
            Spacer()
            Text("See all ›").font(.custom("Inter-Medium",fixedSize:14)).foregroundStyle(.white.opacity(0.55)).offset(y:-1/6)
          }.frame(width:w-44).position(x:w/2,y:top+78+14)
          Canvas { context,_ in
            for i in 0..<6 {
              let ccx = 45+Double(i)*(w-90)/5, ccy = top+35
              if let s = context.resolveSymbol(id:100+i) { context.draw(s,at:CGPoint(x:ccx,y:ccy)) }
              context.stroke(Path(ellipseIn:CGRect(x:ccx-25,y:ccy-25,width:50,height:50)),with:.color(i == 0 ? LiquidArtDeckResources.canvasColor(0xe8ff8a) : .white.opacity(0.18)),lineWidth:2)
            }
            for p in poses {
              guard let s = context.resolveSymbol(id:p.i) else { continue }
              var c = context; c.translateBy(x:w/2+p.x,y:cy+p.y); c.rotate(by:.degrees(p.rotation));c.scaleBy(x:p.scale,y:p.scale)
              c.opacity = p.alpha
              c.drawLayer { layer in
                layer.draw(s,at:.zero)
              }
            }
          } symbols: {
            ForEach(0..<6) { i in
              card(i,d,ph).tag(i)
              chrome(46,46,LiquidArtDeckMotion.seeds[i],0,0,ph+Double(i),CGPoint(x:45+Double(i)*(w-90)/5-23,y:top+12)).frame(width:42,height:42).clipShape(Circle()).tag(100+i)
            }
          }.frame(width:w,height:h)
          if pinned == nil {
            LiquidArtDeckTouch { state,tr,vel in clock.touch(state,tr,vel,reduced) }.frame(width:w,height:h)
          }
          Color.clear.frame(width:282,height:384).position(x:w/2,y:cy)
            .accessibilityElement().accessibilityLabel(d.cur < 6 ? "\(LiquidArtDeckMotion.titles[min(d.cur,5)]) by \(LiquidArtDeckMotion.authors[min(d.cur,5)].dropFirst()), \(LiquidArtDeckMotion.prices[min(d.cur,5)]). Swipe right to add to favourites, left to pass." : "No more art")
            .accessibilityAction { if pinned == nil { clock.action(1,reduced) } }
            .accessibilityAction(.escape) { if pinned == nil { clock.action(2,reduced) } }
            .accessibilityAction(named:"Add to favourites") { if pinned == nil { clock.action(1,reduced) } }
            .accessibilityAction(named:"Pass") { if pinned == nil { clock.action(2,reduced) } }.allowsHitTesting(false)
          HStack(spacing:22) { action(2,"Pass");action(3,"Undo");action(1,"Add to favourites") }
            .position(x:w/2,y:cy+192+22+29)
          ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:31).fill(color(0x1b1b21))
            icon(1).position(x:43,y:31)
            icon(2).position(x:121,y:31)
            icon(3).position(x:w-79,y:31)
            let s = now-d.beat
            let k = s >= 0 && s < 0.6 ? sin(min(1,s/0.12) * .pi)*0.35*exp(-s*4)+sin(s*18)*0.08*exp(-s*7) : 0
            ZStack(alignment:.topLeading) {
              icon(0,LiquidArtDeckResources.canvasColor(0xff6ad0)).position(x:20,y:20)
              Text("\(d.count)").font(.custom("Inter-SemiBold",fixedSize:11)).monospacedDigit().foregroundStyle(color(0x0f0f12))
                .padding(.horizontal,4).frame(minWidth:22).frame(height:18).background(color(0xff6ad0),in:Capsule()).offset(x:28-1/3,y:-2)
            }.frame(width:40,height:40).scaleEffect(1+k).position(x:w*0.625-18,y:31)
              .accessibilityElement(children:.ignore).accessibilityLabel("Favourites, \(d.count)")
          }.frame(width:w-36,height:62).position(x:w/2,y:h-bottom-37)
        }.frame(width:w,height:h,alignment:.topLeading).clipped()
      }.frame(width:w,height:h,alignment:.topLeading)
    }.ignoresSafeArea()
    }.preferredColorScheme(.dark)
    .onAppear { clock.load();clock.resetDate() }
    .onChange(of:scene) { _,_ in clock.resetDate() }
  }
}
struct LiquidArtDeckPressStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.95 : 1) }
}
