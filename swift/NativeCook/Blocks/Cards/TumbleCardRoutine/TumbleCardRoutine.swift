import SwiftUI
import CoreText
import UIKit

enum TumbleCardRoutineAssets {
    static let register: Void = {
        for name in ["Geist-Regular", "Geist-SemiBold"] {
            if let url = Bundle.main.url(forResource: "TumbleCardRoutine-\(name)", withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
    }()
    static func image(_ name: String) -> UIImage? {
        guard let url = Bundle.main.url(forResource: "TumbleCardRoutine-\(name)", withExtension: "jpg") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
    private static var fonts:[String:UIFont]=[:]
    static func font(_ size:Double,_ bold:Bool=false)->UIFont {
        let key="\(size)/\(bold)"
        if let cached=fonts[key] { return cached }
        let name="TumbleCardRoutine-Geist-"+(bold ? "SemiBold" : "Regular")
        guard let url=Bundle.main.url(forResource:name,withExtension:"ttf"), let provider=CGDataProvider(url:url as CFURL), let graphicsFont=CGFont(provider) else { return .systemFont(ofSize:size,weight:bold ? .semibold : .regular) }
        let font=CTFontCreateWithGraphicsFont(graphicsFont,size,nil,nil) as UIFont
        fonts[key]=font; return font
    }
    static let photos = [image("photo-water"), image("photo-activity"), image("avatar")]
}

struct TumbleCardRoutine: View {
    static let loop = TumbleCardRoutineMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var driver=TumbleCardRoutineDriver()
    @State private var day=0
    init(autoPlay:Bool=false) { self.autoPlay=autoPlay; _=TumbleCardRoutineAssets.register }
    private func color(_ hex: UInt, _ alpha: Double = 1) -> Color { Color(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:alpha) }
    private func font(_ size: Double, _ semibold: Bool = false) -> Font { Font(TumbleCardRoutineAssets.font(size,semibold) as CTFont) }
    var body: some View {
        GeometryReader { safe in
            screen(safeInsets: safe.safeAreaInsets).ignoresSafeArea()
        }
        .statusBarHidden(false)
        .preferredColorScheme(.light)
        .onAppear { driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active) }
        .onChange(of:phase) { _,p in driver.configure(autoPlay:autoPlay,reduced:reduced,active:p == .active) }
        .onChange(of:reduced) { _,_ in driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active) }
        .onChange(of:autoPlay) { _,_ in driver.configure(autoPlay:autoPlay,reduced:reduced,active:phase == .active) }
        .onDisappear { driver.stop() }
    }
    private func screen(safeInsets: EdgeInsets) -> some View {
        GeometryReader { geo in
            let scale=geo.size.width/402, top=safeInsets.top/scale-62, bottom=(geo.size.height-safeInsets.bottom)/scale-840
            let d=driver.deck
            ZStack(alignment:.topLeading) {
                    color(0x0F0F11)
                    ZStack(alignment:.topLeading) {
                        color(0xFBFBFB)
                        Text("Nov 12, 2026").font(font(13)).foregroundStyle(color(0x8E8E93)).position(x:201,y:97+top)
                        ScrollView(.horizontal,showsIndicators:false) {
                            HStack(spacing:52) {
                                ForEach(Array(["Today's routine","Friday","Saturday","Sunday"].enumerated()),id:\.offset) { i,title in
                                    Text(title).font(font(22,true)).tracking(-0.44).foregroundStyle(color(i == day ? 0x111113 : 0xC0C2C2)).onTapGesture { day=i }
                                }
                            }.padding(.leading, 201-85).padding(.trailing,402)
                        }.frame(width:402,height:32).position(x:201,y:133+top)
                        ForEach([2,1,0],id:\.self) { role in
                            ForEach(0..<4,id:\.self) { i in
                                let p=TumbleCardRoutineMotion.pose(i,d,top,bottom)
                                if p.visible && p.role == role {
                                    let size = TumbleCardRoutineMotion.sizes[i]
                                    depth(card(i, p.reveal).frame(width: size.0, height: size.1), pose: p, height: size.1)
                                        .shadow(color:shadow(i).opacity(0.18*(1-p.lift)),radius:2,y:2)
                                        .shadow(color:shadow(i).opacity((0.34-0.1*p.lift)*(role == 0 ? 1 : 0.35)),radius:24+8*p.lift,y:22+10*p.lift)
                                        .modifier(TumbleCardRoutinePerspective(angle:p.rx))
                                        .scaleEffect(p.scale*1.1).rotationEffect(.degrees(p.rotation))
                                        .layerEffect(ShaderLibrary.tumbleCardRoutineStreak(.float(0.35*p.streak),.float2(cos(p.blurAngle),sin(p.blurAngle))),maxSampleOffset:CGSize(width:90,height:90),isEnabled:p.streak>=1)
                                        .opacity(p.alpha)
                                        .position(x:p.x,y:p.y)
                                }
                            }
                            if role == 1 {
                                LinearGradient(colors:[color(0xFBFBFB,0),color(0xFBFBFB,0.9-0.55*d.peek*(1-TumbleCardRoutineMotion.seg(max(d.pos-floor(d.pos+0.000001),0.22*d.peek),0.22,0.5)))],startPoint:.top,endPoint:.bottom)
                                    .frame(width:402,height:119).position(x:201,y:659.5+bottom).allowsHitTesting(false)
                            }
                        }
                        row(bottom)
                        TumbleCardRoutineTouchSurface(driver:driver,bottom:bottom)
                            .frame(width:402,height:719+bottom)
                            .accessibilityLabel("Today's routine. Swipe up or down to change cards.")
                    }.frame(width:402,height:719+bottom,alignment:.topLeading).clipShape(TumbleCardRoutinePanel())
                    field(bottom)
                }.frame(width:402,height:geo.size.height/scale,alignment:.topLeading)
                    .scaleEffect(scale,anchor:.topLeading).frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading)

        }
    }
    @ViewBuilder
    private func depth<Face: View>(_ face: Face, pose: TumbleCardRoutineMotion.Pose, height: Double) -> some View {
        if pose.blur <= 0.3 {
            face
        } else if pose.role == 0 {
            face.blur(radius: pose.blur)
        } else {
            let start = UnitPoint(x: 0.5, y: pose.y0 / height)
            let end = UnitPoint(x: 0.5, y: (pose.y0 + 120) / height)
            ZStack {
                face.mask(LinearGradient(colors: [.white, .clear], startPoint: start, endPoint: end))
                face.blur(radius: pose.blur)
                    .mask(LinearGradient(colors: [.clear, .white], startPoint: start, endPoint: end))
                    .blendMode(.plusLighter)
            }
            .compositingGroup()
        }
    }

    private func shadow(_ i: Int) -> Color { color([0xC2410C,0x12302D,0x0B0C0E,0x2A251B][i]) }
    @ViewBuilder private func photo(_ index: Int) -> some View {
        if let image=TumbleCardRoutineAssets.photos[index] { Image(uiImage:image).resizable().scaledToFill() } else { color(0x182422) }
    }
    private func cardPhoto(_ index: Int, width: Double, height: Double, reveal: Double) -> some View {
        Canvas { context, _ in
            let bounds = CGRect(x: 0, y: 0, width: width, height: height)
            guard let photo = TumbleCardRoutineAssets.photos[index] else {
                context.fill(Path(bounds), with: .color(color(0x182422)))
                return
            }
            let settle = min(1, max(0, TumbleCardRoutineMotion.spring(reveal, 1, 0, 0, 22.1, 157.9)))
            let scale = max(width / photo.size.width, height / photo.size.height) * (1 + 0.12 * settle)
            let imageWidth = photo.size.width * scale, imageHeight = photo.size.height * scale
            let y = min(0, max(height - imageHeight, (height - imageHeight) / 2 + 8 * settle))
            context.clip(to: Path(bounds))
            context.draw(Image(uiImage: photo), in: CGRect(x: (width - imageWidth) / 2, y: y, width: imageWidth, height: imageHeight))
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
    private func card(_ i: Int,_ reveal: Double) -> some View {
        let c=TumbleCardRoutineMotion.sizes[i], white=i != 0
        return ZStack(alignment:.topLeading) {
            if i==0 { color(0xF56419); RoundedRectangle(cornerRadius:8,style:.continuous).fill(color(0xFBFBFB)).frame(width:234,height:242).position(x:125,y:157) }
            else if i==2 { LinearGradient(colors:[color(0x16191D),color(0x111316)],startPoint:.top,endPoint:.bottom) }
            else {
                cardPhoto(i == 1 ? 0 : 1, width: c.0, height: c.1, reveal: reveal)
                LinearGradient(stops: [
                    .init(color: color(i == 1 ? 0x061211 : 0x0A0906, 0.3), location: 0),
                    .init(color: .clear, location: (i == 1 ? 70 : 64) / c.1),
                    .init(color: .clear, location: 0.68),
                    .init(color: color(0x061616, i == 1 ? 0.42 : 0), location: 1),
                ], startPoint: .top, endPoint: .bottom)
            }
            HStack(spacing:4) {
                Image(systemName:["fork.knife","drop.fill","scalemass","bolt.fill"][i]).font(.system(size:15,weight:.semibold)).frame(width:18)
                Text(["Food","Water","Weight","Activity"][i]).font(font(i==0 ? 14 : 15,true))
                Spacer(minLength:0)
                if i==2 { Text("− 1.8 (2.7%)").font(font(11,true)).foregroundStyle(color(0x0D2B14)).padding(.horizontal,8).frame(height:20).background(color(0x6FCC82),in:Capsule()) }
                else { HStack(spacing:3) { ForEach(0..<(i==3 ? 4 : 5),id:\.self) { j in Circle().fill((white ? Color.white : color(0x3A1606)).opacity(j < [3,2,0,4][i] ? 1 : (i==0 ? 0.3 : 0.35))).frame(width:6,height:6).scaleEffect(dotScale(j,i,reveal)) } } }
            }.foregroundStyle(white ? .white : color(0x3A1606)).padding(.horizontal,i==0 ? 11 : 13).frame(height:36).position(x:c.0/2,y:i==0 ? 18 : 22)
            if i==0 {
                label("left 960",12,0x8E8E93).position(x:125,y:61)
                number("1640",unit:"kcal",reveal:reveal,color:0x211D12).position(x:125,y:150)
                (Text("63% of ").font(font(12)).foregroundColor(color(0x8E8E93))+Text("daily goal").font(font(12,true)).foregroundColor(color(0x211D12))).position(x:125,y:235)
                label("2600 kcal",12,0x211D12,true).position(x:125,y:251)
            } else if i==1 {
                number("450",unit:"ml",reveal:reveal).position(x:114,y:151)
                (Text("water drunk of ").font(font(12)).foregroundColor(.white.opacity(0.85))+Text("2000 ml").font(font(12,true)).foregroundColor(.white)).position(x:114,y:244)
                label("your daily goal",12,0xFFFFFF,true).position(x:114,y:260)
            } else if i==2 {
                number("64",unit:"kg",reveal:reveal).position(x:111,y:98)
                HStack(spacing:6) { ForEach(0..<4,id:\.self) { j in RoundedRectangle(cornerRadius:min(9,barHeight(j,reveal)/2),style:.continuous).fill(color(j==3 ? 0x3FD05A : 0x2A2D31)).frame(width:40,height:barHeight(j,reveal)).frame(height:56,alignment:.bottom) } }.position(x:111,y:168)
                label("Your goal",12,0x7C8088).position(x:111,y:225); label("Lose weight",12,0xFFFFFF,true).position(x:111,y:241)
            } else {
                HStack(spacing:4) { Image(systemName:"flame.fill").font(.system(size:14)); label("You burned",12,0xFFFFFF) }.foregroundStyle(.white.opacity(0.85)).position(x:150,y:97)
                number("310",unit:"kcal",reveal:reveal).position(x:150,y:144); number("5.2",unit:"km",reveal:reveal,delay:0.08).position(x:150,y:194)
            }
        }.frame(width:c.0,height:c.1)
            .overlay(alignment:.top) { RoundedRectangle(cornerRadius:c.2,style:.continuous).stroke(LinearGradient(colors:[.white.opacity(i==0 ? 0.3 : 0.14),.clear],startPoint:.top,endPoint:.center),lineWidth:1) }
            .clipShape(RoundedRectangle(cornerRadius:c.2,style:.continuous))
    }
    private func dotScale(_ j:Int,_ i:Int,_ rv:Double)->Double {
        guard j < [3,2,0,4][i], rv>0, rv<0.8 else { return 1 }
        return 1+0.4*sin(Double.pi*TumbleCardRoutineMotion.seg(rv,0.12+0.04*Double(j),0.32+0.04*Double(j)))
    }
    private func barHeight(_ j:Int,_ rv:Double) -> Double {
        guard rv>0 && rv<0.8 else { return 56 }
        if j<3 { return 56*(1-0.7*sin(Double.pi*TumbleCardRoutineMotion.out(TumbleCardRoutineMotion.seg(rv,0.08+0.05*Double(j),0.48+0.05*Double(j))))) }
        return 56*max(0,rv<0.3 ? 1-0.85*sin(Double.pi/2*TumbleCardRoutineMotion.seg(rv,0.08,0.3)) : TumbleCardRoutineMotion.spring(rv-0.3,0.15,1,0,19,195))
    }
    private func label(_ text:String,_ size:Double,_ c:UInt,_ bold:Bool=false) -> some View { Text(text).font(font(size,bold)).foregroundStyle(color(c)) }
    private func number(_ value:String,unit:String,reveal:Double,delay:Double=0,color c:UInt=0xFFFFFF) -> some View {
        TumbleCardRoutineNumber(value:value,unit:unit,reveal:reveal,delay:delay).foregroundStyle(color(c))
    }
    private func row(_ bottom:Double) -> some View {
        ZStack {
            Circle().fill(color(0xF1F1F1)).frame(width:40,height:40).overlay(Image(systemName:"chart.bar.fill").font(.system(size:17))).position(x:41,y:667+bottom)
            Button { driver.begin(); driver.tap(add:true); driver.end(translation:0,velocity:0) } label: { Circle().fill(color(0x0F0F11)).frame(width:64,height:64).overlay(Image(systemName:"plus").font(.system(size:18,weight:.regular)).foregroundStyle(.white)) }.position(x:201,y:667+bottom).accessibilityLabel("Add")
            photo(2).frame(width:38,height:38).clipShape(Circle()).overlay(Circle().stroke(color(0xE6E6E6),lineWidth:1)).position(x:364,y:667+bottom)
        }
    }
    private func field(_ bottom:Double) -> some View {
        ZStack(alignment:.topLeading) {
            HStack { label("Ask me…",15,0x9A9AA0); Spacer(); Image(systemName:"mic").font(.system(size:18)).foregroundStyle(color(0xD0D0D4)) }.padding(.horizontal,16).frame(width:364,height:52).background(color(0x262628),in:RoundedRectangle(cornerRadius:14,style:.continuous)).position(x:201,y:762+bottom)
            ScrollView(.horizontal,showsIndicators:false) { HStack(spacing:8) { ForEach(["Plan a high-protein day","Easy ways to drink more","Recover after a long ride"],id:\.self) { text in label(text,13,0xB0B0B5).padding(.horizontal,12).frame(height:34).background(color(0x262628),in:RoundedRectangle(cornerRadius:10,style:.continuous)) } }.padding(.horizontal,19) }.frame(width:402,height:34).position(x:201,y:817+bottom)
        }
    }
}
private struct TumbleCardRoutinePanel: Shape {
    func path(in rect: CGRect) -> Path { Path(roundedRect:rect,cornerSize:CGSize(width:32,height:32),style:.continuous) }
}
