import SwiftUI

/// Resolve the same shader graphs before the autoplay clock starts; uniforms remain unchanged.
enum RaffleDrumSpinShaderWarmup {
    @MainActor
    static func prepare(size: CGSize, ticket: Image?) async {
        guard #available(iOS 18, *), let ticket else { return }
        let shape: Shader.UsageType = .shapeStyle
        let glowX = Float(205 * size.width / 402)
        let glowY = Float(148 * size.height / 874)
        let glowInk = Color(red:51.0/255,green:31.0/255,blue:107.0/255)
        let glow = ShaderLibrary.raffleDrumSpinBaseGlow(.float2(glowX,glowY),.float(Float(size.width*0.62)),.float(0),.color(glowInk))
        let shaders: [(Shader, Shader.UsageType)] = [
            (glow,.colorEffect),
            (ShaderLibrary.raffleDrumSpinBackground(.float2(size.width,size.height), .float2(205*size.width/402,148*size.height/874), .float(0), .float(0)), .colorEffect),
            (ShaderLibrary.raffleDrumSpinRays(.float2(0,0),.float(0),.float(0)), shape),
            (ShaderLibrary.raffleDrumSpinBall(.float2(0,0),.float(1),.float3(0,0,0),.float(0),.float(0)), shape),
            (ShaderLibrary.raffleDrumSpinMorph(.image(ticket),.float2(0,0),.float(1),.float3(0,0,0),.float(0),.float(0),.float2(201,432),.float2(300,111),.float(0.729),.float2(1200,444),.float(12),.float(0),.float(1)), shape),
            (ShaderLibrary.raffleDrumSpinFoil(.image(ticket),.float2(0,0),.float2(300,111),.float2(1200,444),.float2(0,0),.float(0),.float(0),.float(0),.float(0),.float(0.729)), shape),
            (ShaderLibrary.raffleDrumSpinLight(.float2(size.width,size.height),.float2(0,0),.float(0),.float2(201,432),.float(0),.float(0),.float2(201,432),.float(1),.float(0),.float2(201,432),.float(0),.float(0),.float(1)), shape),
            (ShaderLibrary.raffleDrumSpinLiquid(.float2(0,0),.float2(368,48),.float(0),.float(0),.float2(0,0),.float(0)), shape),
            (ShaderLibrary.raffleDrumSpinGlass(.float4(0,0,167,202),.float(18),.float(0)), .layerEffect)
        ]
        var failures: [String] = []
        for (index,item) in shaders.enumerated() {
            do { try await item.0.compile(as:item.1) }
            catch { failures.append("shader \(index): \(error)") }
        }
        _ = failures.count
#if DEBUG
        if let output=UserDefaults.standard.string(forKey:"rafflePerfOutput") {
            let url=URL(fileURLWithPath:output).deletingLastPathComponent().appendingPathComponent("shader-warmup.json")
            if let data=try? JSONSerialization.data(withJSONObject:["prepared":shaders.count-failures.count,"failures":failures],options:[.prettyPrinted,.sortedKeys]) { try? data.write(to:url,options:.atomic) }
        }
#endif
    }
}
