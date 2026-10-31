import SwiftUI

struct StormCloudWeather: View {
    static let loop = StormCloudWeatherMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = StormCloudWeatherClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "stormCloudWeatherTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top
            let bottom = proxy.safeAreaInsets.bottom
            let size = CGSize(width: proxy.size.width, height: proxy.size.height+top+bottom)
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                let frame = clock.sample(timeline.date, autoPlay: autoPlay, reduced: reduced, pinned: pinned)
                StormCloudWeatherPage(size: size, top: top, bottom: bottom, frame: frame,
                    pick: { if pinned == nil { clock.pick($0, reduced: reduced) } },
                    touch: { state, x, v in if pinned == nil { clock.touch(state, translation: x, velocity: v) } })
                .allowsHitTesting(pinned == nil)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(y: -top)
        }
        .background(StormCloudWeatherTheme.color(0x0d1b28).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.hidden)
        .onAppear { clock.active(scenePhase == .active && pinned == nil, reduced: reduced) }
        .onDisappear { clock.active(false, reduced: reduced) }
        .onChange(of: scenePhase) { _, value in clock.active(value == .active && pinned == nil, reduced: reduced) }
        .onChange(of: reduced) { _, value in clock.active(scenePhase == .active && pinned == nil, reduced: value) }
    }
}

struct StormCloudWeatherPage: View {
    typealias H = StormCloudWeatherTheme
    typealias M = StormCloudWeatherMotion
    let size: CGSize
    let top, bottom: Double
    let frame: M.Frame
    let pick: (Int) -> Void
    let touch: (UIGestureRecognizer.State, Double, Double) -> Void
    var body: some View {
        let sy = top+78
        let panelY = sy+430
        ZStack(alignment: .topLeading) {
            StormCloudWeatherSky(frame: frame)
            H.color(0xdbe6ff).opacity(frame.to == 0 ? 0.08*frame.flash : 0)
            VStack(spacing: 6) {
                Text("Porto, Portugal").font(H.font("Outfit-Medium",27)).foregroundStyle(.white).accessibilityAddTraits(.isHeader)
                Text("Today 31 Oct, 2026 · \(M.copy[frame.to].sky)").font(H.font("Inter-Regular",14)).foregroundStyle(.white.opacity(0.75))
            }
            .frame(width: size.width).offset(y: top+14)
            StormCloudWeatherScene(weather: frame.weather, width: size.width, height: 350,
                center: CGPoint(x: size.width/2, y: 350*0.47), scale: min(128,size.width*0.32), yaw: frame.yaw, time: frame.time, flash: frame.to == 0 ? frame.flash : 0)
                .overlay { StormCloudWeatherTouchView(touch: touch) }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(M.copy[frame.to].sky), \(frame.number(\.temp)) degrees")
                .accessibilityAddTraits(.isImage)
                .offset(y: sy)
            HStack(alignment: .top, spacing: 0) {
                stat("Wind", "\(frame.number(\.wind))km/h")
                Spacer(minLength: 0)
                stat("Temp", "\(frame.number(\.temp))°")
                Spacer(minLength: 0)
                stat("Humidity", "\(frame.number(\.humidity))%")
            }.frame(width: size.width-60).offset(x: 30,y: sy+344)
            UnevenRoundedRectangle(topLeadingRadius: 34, topTrailingRadius: 34)
                .fill(H.color(0x0a1621)).frame(width: size.width,height: max(0,size.height-panelY)).offset(y: panelY)
            VStack(spacing: 16) {
                HStack {
                    Text("Today").font(H.font("Inter-SemiBold",17)).foregroundStyle(.white)
                    Spacer()
                    Text("Next Week ⌄").font(H.font("Inter-Regular",14)).foregroundStyle(.white.opacity(0.5))
                }
                HStack(spacing: 10) {
                    ForEach(0..<3, id: \.self) { i in
                        StormCloudWeatherHour(i: i, selected: frame.to == i, width: (size.width-68)/3, pick: { pick(i) })
                    }
                }
            }.frame(width: size.width-48).offset(x:24,y:panelY+20)
            StormCloudWeatherTabs().frame(width: size.width-40,height:60)
                .background(H.color(0x13263a),in: RoundedRectangle(cornerRadius:24))
                .offset(x:20,y:size.height-bottom-66)
        }
        .frame(width:size.width,height:size.height,alignment:.topLeading)
        .clipped()
        .dynamicTypeSize(.medium)
    }
    private func stat(_ label: String, _ value: String) -> some View {
        VStack(spacing:4) {
            Text(label).font(H.font("Inter-Regular",15)).foregroundStyle(.white.opacity(0.72))
            Text(value).font(H.font("Inter-SemiBold",25)).monospacedDigit().foregroundStyle(.white)
        }.frame(minWidth:90)
    }
}

struct StormCloudWeatherScene: View {
    let weather: StormCloudWeatherMotion.Weather
    let width, height: Double
    let center: CGPoint
    let scale, yaw, time, flash: Double
    var body: some View {
        Canvas(opaque: false, colorMode: .extendedLinear) { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .shader(
                ShaderLibrary.stormCloudWeatherScene(
                    .float2(center), .float(scale), .float(yaw), .float(time),
                    .float4(Float(weather.cloud[0]),Float(weather.cloud[1]),Float(weather.cloud[2]),1),
                    .float(weather.moon),.float(weather.sun),.float(weather.bolt),.float(weather.rain),.float(flash),.float(weather.puff))))
        }.frame(width:width,height:height)
    }
}

struct StormCloudWeatherHour: View {
    typealias H = StormCloudWeatherTheme
    let i: Int
    let selected: Bool
    let width: Double
    let pick: () -> Void
    var body: some View {
        let cp = StormCloudWeatherMotion.copy[i]
        Button(action: pick) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius:18).fill(H.color(selected ? 0xf5a21f : 0xe8861a))
                let pad = selected ? 14.0 : 12.0
                VStack(alignment:.leading,spacing:0) {
                    Text(cp.label).font(H.font("Inter-Regular",15))
                    Text("\(cp.temp)°").font(H.font("Inter-SemiBold",24))
                }.foregroundStyle(.white).frame(maxHeight:.infinity,alignment:.bottom).padding(pad)
                StormCloudWeatherScene(weather: StormCloudWeatherMotion.states[i], width:56,height:56,
                    center:CGPoint(x:28,y:30),scale:21,yaw:-0.2,time:0.6,flash:0)
                    .offset(x:width-62-(selected ? 2:0),y:selected ? 8:6)
                if selected { RoundedRectangle(cornerRadius:18).strokeBorder(.white.opacity(0.75),lineWidth:2) }
            }.frame(width:width,height:104).compositingGroup().opacity(selected ? 1:0.82)
        }
        .buttonStyle(StormCloudWeatherPressStyle(selected: selected))
        .accessibilityLabel("\(cp.label), \(cp.temp) degrees, \(cp.sky)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
struct StormCloudWeatherPressStyle: ButtonStyle {
    let selected: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97:1)
            .offset(y:configuration.isPressed ? 0 : selected ? -6:0)
    }
}

struct StormCloudWeatherSky: View {
    let frame: StormCloudWeatherMotion.Frame
    var body: some View {
        Canvas { context,size in
            let k = frame.weather
            let sun = k.sun, rain = k.rain*(1-k.bolt)
            func color(_ a:[Double], _ b:[Double], _ c:[Double]) -> Color {
                let v = (0..<3).map { i in round(StormCloudWeatherMotion.mix(StormCloudWeatherMotion.mix(a[i],b[i],rain),c[i],sun)*255)/255 }
                return Color(.sRGB,red:v[0],green:v[1],blue:v[2])
            }
            let top = color([0.05,0.1,0.16],[0.14,0.2,0.27],[0.16,0.3,0.48])
            let bot = color([0.03,0.06,0.1],[0.06,0.1,0.15],[0.05,0.11,0.2])
            let bounds = Path(CGRect(origin:.zero,size:size))
            context.fill(bounds,with:.linearGradient(Gradient(colors:[top,bot]),startPoint:.zero,endPoint:CGPoint(x:0,y:700)))
            if sun > 0.01 {
                var glow = context; glow.opacity = sun
                glow.fill(bounds,with:.radialGradient(Gradient(colors:[StormCloudWeatherTheme.color(0xffaa50,0.42),StormCloudWeatherTheme.color(0xff8c3c,0)]),center:CGPoint(x:size.width*0.75,y:150),startRadius:0,endRadius:size.width*0.8))
            }
            let density = StormCloudWeatherMotion.mix(1,0.35,sun)
            for i in 0..<5 {
                let fi = Double(i)
                let p = fi*0.27+frame.phase/9*(0.6+fi*0.1)
                let x = (p-floor(p))*(size.width+300)-150
                var bank = context
                bank.addFilter(.blur(radius:30))
                bank.opacity = 0.13*density
                bank.fill(Path(ellipseIn:CGRect(x:x-130,y:120+fi*70-30,width:260,height:60)),with:.color(StormCloudWeatherTheme.color(i%2 == 1 ? 0x8fa6c4:0x5f7591)))
            }
        }.allowsHitTesting(false)
    }
}

struct StormCloudWeatherTabs: View {
    // Paths are built once, including the near-complete SVG elliptical arcs.
    static let paths: [Path] = {
        let home = Path { p in
            p.move(to:.init(x:4,y:11)); p.addLine(to:.init(x:12,y:4));p.addLine(to:.init(x:20,y:11));p.addLine(to:.init(x:20,y:19))
            p.addArc(center:.init(x:19,y:19),radius:1,startAngle:.degrees(0),endAngle:.degrees(90),clockwise:false);p.addLine(to:.init(x:15,y:20));p.addLine(to:.init(x:15,y:14));p.addLine(to:.init(x:9,y:14));p.addLine(to:.init(x:9,y:20));p.addLine(to:.init(x:5,y:20));p.addArc(center:.init(x:5,y:19),radius:1,startAngle:.degrees(90),endAngle:.degrees(180),clockwise:false);p.closeSubpath()
        }
        func arc(_ p: inout Path,_ x:Double,_ y:Double,_ r:Double) {
            // SVG a r r 0 1 0 .01 0: starts at the top, ends .01 pt to its right.
            let delta = asin(0.005/r)*180 / Double.pi
            p.move(to:.init(x:x,y:y));p.addArc(center:.init(x:x+0.005,y:y+sqrt(r*r-0.000025)),radius:r,startAngle:.degrees(-90-delta),endAngle:.degrees(-450+delta),clockwise:true)
        }
        let search = Path { p in arc(&p,11,4,7);p.move(to:.init(x:16,y:16));p.addLine(to:.init(x:20.5,y:20.5)) }
        let person = Path { p in arc(&p,12,12,4);p.move(to:.init(x:4.5,y:20.5));p.addCurve(to:.init(x:12,y:15),control1:.init(x:5.7,y:16.9),control2:.init(x:8.5,y:15));p.addCurve(to:.init(x:19.5,y:20.5),control1:.init(x:15.5,y:15),control2:.init(x:18.3,y:16.9)) }
        let settings = Path { p in
            arc(&p,12,9,3)
            for (a,b) in [(CGPoint(x:12,y:2.5),CGPoint(x:12,y:5.5)),(.init(x:12,y:18.5),.init(x:12,y:21.5)),(.init(x:2.5,y:12),.init(x:5.5,y:12)),(.init(x:18.5,y:12),.init(x:21.5,y:12)),(.init(x:5.3,y:5.3),.init(x:7.4,y:7.4)),(.init(x:16.6,y:16.6),.init(x:18.7,y:18.7)),(.init(x:5.3,y:18.7),.init(x:7.4,y:16.6)),(.init(x:16.6,y:7.4),.init(x:18.7,y:5.3))] { p.move(to:a);p.addLine(to:b) }
        }
        return [home,search,person,settings]
    }()
    var body: some View {
        Canvas { context,size in
            let slot = size.width/4
            for i in 0..<4 {
                var c = context
                c.translateBy(x:slot*(Double(i)+0.5)-14,y:16)
                c.scaleBy(x:26/24,y:26/24)
                c.stroke(Self.paths[i],with:.color(.white.opacity(i == 0 ? 1:0.45)),style:StrokeStyle(lineWidth:1.9,lineCap:.round,lineJoin:.round))
            }
        }.accessibilityHidden(true)
    }
}
