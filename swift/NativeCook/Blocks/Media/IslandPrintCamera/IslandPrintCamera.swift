import CoreText
import SwiftUI
import UIKit

/// Value-for-value port of the approved React Native screen. The creative brief is not the motion source.
struct IslandPrintCamera: View {
    static let loop = 11.0
    var autoPlay = true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver: IslandPrintCameraDriver
    @State private var wake = 0
    @GestureState private var fingerDown = false
    private typealias M = IslandPrintCameraMotion

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
        _driver = State(initialValue: IslandPrintCameraDriver(autoPlay: autoPlay))
        _ = IslandPrintCameraAssets.register
    }

    var body: some View {
        GeometryReader { safe in
            let topInset = safe.safeAreaInsets.top
            GeometryReader { full in
                let s = full.size.width/402
                let h = full.size.height/s
                let islandTop = (topInset >= 54 ? 11 : topInset+4)/s
                TimelineView(.animation(paused: scenePhase != .active || driver.pinned)) { timeline in
                    let _ = wake
                    let f = driver.frame(at: timeline.date, autoPlay: autoPlay, reduced: reduced)
                    scene(f, time: driver.swayTime, islandTop: islandTop, height: h, scale: s)
                        .frame(width: 402, height: h, alignment: .topLeading)
                        .scaleEffect(s, anchor: .topLeading)
                        .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
                        .clipped()
                        .contentShape(Rectangle())
                        .gesture(finger(scale: s))
                }
            }
            .ignoresSafeArea()
        }
        .background(IslandPrintCameraAssets.color(0xE82822).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { driver.stopAudio() }
        .onChange(of: scenePhase) { _, phase in if phase != .active { driver.cancel(reduced: reduced) } }
        .onChange(of: fingerDown) { _, down in
            if !down && driver.gestureActive { driver.cancel(reduced: reduced); wake &+= 1 }
        }
    }

    private func scene(_ f: M.Frame, time: Double, islandTop: Double, height: Double, scale: Double) -> some View {
        let pp = max(f.p, 0)
        let w0 = M.mix(126, 230, f.slot)+6*f.antic
        let bw = M.mix(w0, 260, M.easeOut(min(pp/0.7, 1)))
        let bh = M.mix(37-2*f.antic, 260, pow(min(pp, 1.02), 1.15))
        let br = M.mix(18.5, 64, min(pp, 1))
        let body = CGRect(x: 201-bw/2, y: islandTop, width: bw, height: bh)
        let inset = 35*M.clamp(f.p)
        let lens = CGRect(x: body.minX+inset, y: body.minY+inset, width: max(bw-2*inset, 1), height: max(bh-2*inset, 1))
        let lensR = max(br-inset, 4)
        let slotBottom = islandTop+37+f.slotDip
        return ZStack(alignment: .topLeading) {
            IslandPrintCameraAssets.color(0xE82822)
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)),
                             with: .shader(ShaderLibrary.islandPrintCameraShell(.float(height))))
            }
            .frame(width: 402, height: height)
            ZStack(alignment: .topLeading) {
                bodyView(f, rect: body, radius: br, height: 430)
                liveLens(f, rect: lens, radius: lensR, body: body, time: time)
                dome(f, index: 0, scale: scale)
                dome(f, index: 1, scale: scale)
                dome(f, index: 2, scale: scale)
            }
            .frame(width: 402, height: 430, alignment: .topLeading).clipped()

            IslandPrintCameraIcon(kind: .flip)
                .stroke(IslandPrintCameraAssets.color(0xB81F1C), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                .frame(width: 22, height: 22)
                .opacity(M.clamp(f.flood[2]*1.2)*f.dim)
                .offset(x: 318, y: 351-3*M.clamp(f.rise[2])/scale)
            IslandPrintCameraIcon(kind: .flash)
                .stroke(IslandPrintCameraAssets.color(0x1E240D).opacity(0.7), style: StrokeStyle(lineWidth: 1.25, lineCap: .round, lineJoin: .round))
                .frame(width: 16, height: 16)
                .opacity(driver.flashOn ? 0 : M.clamp(f.flood[0]*1.2)*(f.led == 0 ? 1 : 0.35))
                .offset(x: 65, y: 354-3*M.clamp(f.rise[0])/scale)

            ZStack(alignment: .top) {
                IslandPrintCameraAssets.color(0x2C1C26)
                VStack(spacing: 0) {
                    IslandPrintCameraIcon(kind: .empty)
                        .stroke(IslandPrintCameraAssets.color(0x6E5F68), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                        .frame(width: 48, height: 62)
                    Text("No pics yet")
                        .font(.custom(IslandPrintCameraAssets.fontName, fixedSize: 17))
                        .tracking(-0.22/scale)
                        .foregroundStyle(IslandPrintCameraAssets.color(0xD9CFD5))
                        .padding(.top, 18)
                    Text("Tap logo or drag down\nto open cam")
                        .font(.custom(IslandPrintCameraAssets.fontName, fixedSize: 12))
                        .lineSpacing(18-IslandPrintCameraAssets.fontLineHeight)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(IslandPrintCameraAssets.color(0x8A7A84))
                        .padding(.top, 6)
                }
                .padding(.top, 253).opacity(f.empty)
            }
            .frame(width: 370, height: 660)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .circular))
            .offset(x: 16, y: 165-4*f.antic+346*pp)

            Image(uiImage: IslandPrintCameraAssets.wordmark)
                .resizable().scaledToFit().frame(width: 79, height: 45)
                .scaleEffect(M.mix(1, 0.62, pow(min(pp, 1), 0.8)))
                .offset(x: 161.5, y: M.mix(113, 484, pp)-4*f.antic-22.5)

            printLayer(f, slotBottom: slotBottom, body: body, radius: br, height: height)
            IslandPrintCameraAssets.color(0xE82822)
                .frame(width: 402, height: 49+60/scale)
                .offset(y: 825+346*pp)
        }
        .frame(width: 402, height: height, alignment: .topLeading)
    }

    private func bodyView(_ f: M.Frame, rect: CGRect, radius: Double, height: Double) -> some View {
        Rectangle().fill(.white).frame(width: 402, height: height)
            .colorEffect(ShaderLibrary.islandPrintCameraBody(
                .float4(Float(rect.minX), Float(rect.minY), Float(rect.width), Float(rect.height)),
                .float(radius), .float(f.bloom), .float(1), .float(f.slotDip), .float(0)))
    }

    private func dome(_ f: M.Frame, index: Int, scale: Double) -> some View {
        let x = [73.0, 201, 329][index], r = [24.0, 41, 25][index]
        let extent = index == 1 ? 62.0 : 44.0
        let led: UInt32 = f.led == 2 ? 0xE0301E : f.led == 1 ? 0xA6D11F : driver.flashOn ? 0xF5A623 : 0x2F3815
        let cap = IslandPrintCameraAssets.rgb(index == 0 ? led : index == 1 ? 0xF57602 : 0xE82822)
        let shell = IslandPrintCameraAssets.rgb(0xE82822)
        let halo = IslandPrintCameraAssets.rgb(led)
        return Rectangle().fill(.white).frame(width: extent*2, height: index == 1 ? 130 : 92)
            .colorEffect(ShaderLibrary.islandPrintCameraDome(
                .float2(x-extent, 362-extent), .float2(x, 362), .float(r),
                .float(f.rise[index]), .float(index == 1 ? f.depth : 0), .float(f.flood[index]),
                .float3(cap.0, cap.1, cap.2), .float3(shell.0, shell.1, shell.2),
                .float(f.dim), .float(index == 0 ? f.ledHalo : 0),
                .float3(halo.0, halo.1, halo.2), .float(index == 2 ? 1 : 0), .float(index == 0 ? 1 : 0)))
            .offset(x: x-extent, y: 362-extent)
    }

    private func liveLens(_ f: M.Frame, rect: CGRect, radius: Double, body: CGRect, time: Double) -> some View {
        let sz = 190*1.35*f.focus
        let sx = 5*sin(2 * .pi * 3*time/11)+2*sin(2 * .pi * 5*time/11)
        let sy = 4*sin(2 * .pi * 2*time/11+1.3)
        let a = M.seg(f.p, 0.35, 1)
        let shape = RoundedRectangle(cornerRadius: radius, style: .circular)
        return ZStack(alignment: .topLeading) {
            Image(uiImage: IslandPrintCameraAssets.plate)
                .resizable().frame(width: sz, height: sz).blur(radius: f.blur)
                .offset(x: body.midX-sz/2+sx-rect.minX, y: body.midY-sz/2+sy-rect.minY)
            Color.black.opacity(M.clamp(1-f.exposure)*a)
            Color.white.opacity(M.clamp(f.exposure-1)*1.6)
            // RN's gradient uses global y=0..8; the lens starts below it, so this pass is transparent.
            if f.glint > 0 && f.glint < 1 {
                LinearGradient(colors: [.clear, .white.opacity(0.22), .clear], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 24, height: 400).rotationEffect(.degrees(20))
                    .position(x: M.mix(-40, rect.width+40, f.glint), y: rect.height/2)
            }
            Color.black.opacity(f.black)
            Color.white.opacity(f.flash)
        }
        .frame(width: rect.width, height: rect.height, alignment: .topLeading)
        .clipShape(shape).opacity(a)
        .overlay(shape.stroke(.white.opacity(0.12*a), lineWidth: 1))
        .offset(x: rect.minX, y: rect.minY)
    }

    private func printLayer(_ f: M.Frame, slotBottom: Double, body: CGRect, radius: Double, height: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                printPaper(f).projectionEffect(IslandPrintCameraProjection.matrix(f, slotBottom: slotBottom))
                if f.slot > 0.001 {
                    // Like RN, this gradient's coordinate space is the full canvas (0..10).
                    Color.clear.frame(width: 402, height: 10).offset(y: slotBottom)
                }
            }
            .frame(width: 402, height: height, alignment: .topLeading)
            .clipShape(IslandPrintCameraClip(top: f.printOut ? -100 : slotBottom))
            .opacity(f.printTop < 880 && f.f > 0 ? 1 : 0)
            if f.slot > 0.001 { bodyView(f, rect: body, radius: radius, height: 120) }
        }
        .frame(width: 402, height: height, alignment: .topLeading)
    }

    private func printPaper(_ f: M.Frame) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 3, style: .circular)
                .fill(IslandPrintCameraAssets.color(0x120A10).opacity(0.25*M.mix(0.14, 0.22, f.printShadow)))
                .frame(width: 202, height: 245).blur(radius: M.mix(12, 20, f.printShadow))
                .offset(x: 2, y: 4)
            RoundedRectangle(cornerRadius: 2, style: .circular)
                .fill(IslandPrintCameraAssets.color(0xFCFBFA)).frame(width: 206, height: 247)
            Color.white.opacity(0.9).frame(width: 206, height: 1)
            IslandPrintCameraAssets.color(0xE9E6E2).frame(width: 206, height: 0.75).offset(y: 246.25)
            ZStack(alignment: .topLeading) {
                Image(uiImage: IslandPrintCameraAssets.plate).resizable().frame(width: 180, height: 180)
                    .colorEffect(ShaderLibrary.islandPrintCameraDevelop(.float(f.d), .float2(180, 180)))
                if f.sheen > 0 && f.sheen < 1 {
                    LinearGradient(colors: [.clear, .white.opacity(0.14), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: 40, height: 400).rotationEffect(.degrees(25))
                        .position(x: M.mix(-30, 210, f.sheen), y: 90)
                }
            }
            .frame(width: 180, height: 180, alignment: .topLeading).clipped().offset(x: 13, y: 22)
        }
        .frame(width: 206, height: 247, alignment: .topLeading)
    }

    private func finger(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($fingerDown) { _, state, _ in state = true }
            .onChanged { value in
                driver.change(value, scale: scale, reduced: reduced)
                wake &+= 1
            }
            .onEnded { value in
                driver.end(value, scale: scale, reduced: reduced)
                wake &+= 1
            }
    }
}

private struct IslandPrintCameraClip: Shape {
    var top: Double
    func path(in rect: CGRect) -> Path { Path(CGRect(x: 0, y: top, width: 402, height: 1200)) }
}

/// Flatten T(cx,slot) · perspective(600) · Rx(-tilt) · T(0,top-slot+h/2) · Rz · Sy · T(-w/2,-h/2).
private enum IslandPrintCameraProjection {
    static func matrix(_ f: IslandPrintCameraMotion.Frame, slotBottom: Double) -> ProjectionTransform {
        let z = f.printRot * .pi/180, tilt = f.printTilt * .pi/180
        let c = cos(z), s = sin(z), y = f.printScaleY
        let off = f.printTop-slotBottom+123.5
        let a = sin(tilt)/600
        let cx = 201 + f.dragX, bottom = slotBottom + f.dragY
        let ya = s, yb = c*y, yc = off-s*103-c*y*123.5
        let xa = c, xb = -s*y, xc = -c*103+s*y*123.5
        let wa = a*ya, wb = a*yb, wc = 1+a*yc
        var m = ProjectionTransform()
        m.m11 = xa+cx*wa; m.m21 = xb+cx*wb; m.m31 = xc+cx*wc
        m.m12 = cos(tilt)*ya+bottom*wa
        m.m22 = cos(tilt)*yb+bottom*wb
        m.m32 = cos(tilt)*yc+bottom*wc
        m.m13 = wa; m.m23 = wb; m.m33 = wc
        return m
    }
}

private enum IslandPrintCameraAssets {
    static let register: Void = {
        if let url = Bundle.main.url(forResource: "IslandPrintCamera-Inter-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static let fontName: String = {
        _ = register
        guard let url = Bundle.main.url(forResource: "IslandPrintCamera-Inter-Regular", withExtension: "ttf"),
              let faces = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
              let face = faces.first else { return "Inter-Regular" }
        return CTFontDescriptorCopyAttribute(face, kCTFontNameAttribute) as? String ?? "Inter-Regular"
    }()
    static var fontLineHeight: Double {
        UIFont(name: fontName, size: 12).map { Double($0.lineHeight) } ?? 14.5
    }
    static let plate = image("plate-back", ext: "jpg")
    static let wordmark = image("pomo-wordmark", ext: "png")
    static func image(_ stem: String, ext: String) -> UIImage {
        guard let url = Bundle.main.url(forResource: "IslandPrintCamera-\(stem)", withExtension: ext),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255)
    }
    static func rgb(_ hex: UInt32) -> (Float, Float, Float) {
        (Float((hex >> 16)&255)/255, Float((hex >> 8)&255)/255, Float(hex&255)/255)
    }
}
