import Foundation

enum LouvreVentThermostatUniforms {
    typealias M=LouvreVentThermostatMotion
    static func rgb(_ h:Int)->[Double] { [Double(h>>16&255)/255,Double(h>>8&255)/255,Double(h&255)/255] }
    static func ramp(_ h:Double,_ stops:[Double],_ values:[[Double]])->[Double] {
        if h<=stops[0] { return values[0] }
        for i in 1..<stops.count where h<=stops[i] { let k=(h-stops[i-1])/(stops[i]-stops[i-1]); return zip(values[i-1],values[i]).map { M.mix($0,$1,k) } }
        return values.last!
    }
    static func colors(_ h:Double,_ stops:[Double],_ hex:[Int])->[Double] { ramp(h,stops,hex.map(rgb)) }
    static func scalar(_ h:Double,_ stops:[Double],_ values:[Double])->Double { ramp(h,stops,values.map { [$0] })[0] }
    static func values(_ s:M.Scene,reduced:Bool)->[String:[Double]] {
        let h=s.heat, hx=[16.0,20,24,28,32], gx=[17.5,24,30]
        let heat=colors(h,hx,[0x3E5568,0x6A2A1C,0xC2410C,0xEB7F02,0xFFB238])
        let intensity=scalar(h,hx,[0.35,0.5,0.72,0.92,1.05])
        return [
            "uP3":[0],"uFrame":[78,246,246,484],"uVent":[86,254,230,468],"uLineY":[488],"uS0":[s.s0],"uK0":[s.k0],"uBl":s.blades,
            "uHeat":heat,"uI":[intensity*(1+0.25*abs(tanh(s.velocity/260)))],"uElem":[M.smooth(19.5,27,h)],"uCore":[M.smooth(25,30,h)],
            "uBarOff":[0.55*(s.temperature-24)*41.6],"uPh":[2*Double.pi*s.time/9],"uPilot":[0.2+0.15*(0.5+0.5*sin(2*Double.pi*s.time/1.5))],
            "uEdgeL":colors(h,gx,[0x0B1218,0x2A0305,0x420709]),"uEdgeR":colors(h,gx,[0x0B1218,0x2F0204,0x4A080A]),
            "uGlowH":[scalar(h,[16,17.5,24,30,32],[110,120,234,380,420])+s.breath],"uG0":colors(h,gx,[0x2E404C,0xEB7F02,0xFFB238]),
            "uG1":colors(h,gx,[0x24323D,0x69230F,0xEB7F02]),"uG2":colors(h,gx,[0x121A20,0x2B0C01,0x69230F]),"uGA":[scalar(h,gx,[0.6,1,1])],
            "uHalo":heat.map { $0*0.22*intensity },"uConv":[s.conv-floor(s.conv)],"uDot":colors(h,gx,[0x27404F,0x3A1418,0x7A2A12]),
            "uDotA":[scalar(h,gx,[0.6,1,1.15])],"uDotAmp":[reduced ? 0 : scalar(h,gx,[0.6,0.5,1])]
        ]
    }
    static func page(_ values:[String:[Double]])->[Float] { ["uP3","uEdgeL","uEdgeR","uGlowH","uG0","uG1","uG2","uGA","uHalo","uFrame","uConv","uDot","uDotA","uDotAmp"].flatMap { values[$0]! }.map(Float.init) }
    static func vent(_ values:[String:[Double]])->[Float] { ["uP3","uFrame","uVent","uLineY","uS0","uK0","uBl","uHeat","uI","uElem","uCore","uBarOff","uPh","uPilot"].flatMap { values[$0]! }.map(Float.init) }
}
