import SwiftUI
import UIKit

enum HourScrubSkyUniforms {
 static func shader(_ f:HourScrubSkyFrame,_ c:HourScrubSkyColors,_ ch:Double,_ low:UIImage,_ high:UIImage,_ reduced:Bool,_ hold:Double,_ finger:CGPoint)->Shader {
  let m=HourScrubSkyMotion.self,sun=m.sun(f.h),sway=reduced ? 0 : f.sway,ar=abs(f.rate),deg=Double.pi/180
  let arc=min(15*deg*max(ar-0.9,0)*0.36,40*deg),sl=Double(low.cgImage?.height ?? 1)/ch,sh=Double(high.cgImage?.height ?? 1)/600
  let lw=Double(low.cgImage?.width ?? 1),hw=Double(high.cgImage?.width ?? 1)
  let values:[String:[Double]]=[
   "uCH":[ch],"uD0":c.day[0],"uD1":c.day[1],"uD2":c.day[2],"uD3":c.day[3],"uN0":c.night[0],"uN1":c.night[1],"uN2":c.night[2],"uN3":c.night[3],
   "uYT":[c.front],"uFW":[c.width],"uBandCol":c.bandColor,"uBand":c.band,"uSun":[sun.x,sun.y],"uSunCol":c.sun,
   "uSquash":[m.mix(1,0.95,m.smooth(440,540,sun.y))],"uBloomK":[1+0.25*hold],"uBreath":[reduced ? 0 : 0.08*sin(.pi*sway)],"uFlare":[f.flare],
   "uRimD":c.cloud[0],"uBellyD":c.cloud[1],"uRimN":HourScrubSkyColors.rgb(0x4A5266),"uBellyN":HourScrubSkyColors.rgb(0x2A2F3D),
   "uOff":[f.h/24*lw/sl+(reduced ? 0 : 10*sin(2 * .pi*sway/12)),f.h/24*hw/sh+(reduced ? 0 : 6*sin(2 * .pi*sway/12+1.3))],
   "uScale":[sl,sh],"uPlateW":[lw,hw],"uSmear":[reduced || ar<1.5 ? 0 : min(ar*2.4,10)],"uStarRot":[(f.h*15*deg).truncatingRemainder(dividingBy:2 * .pi)],
   "uArc":[reduced ? 0 : (f.rate<0 ? -1 : f.rate>0 ? 1 : 0)*arc],"uTrailDim":[1/(1+arc/(20*deg))+0.15],"uHold":[finger.x,finger.y,hold]]
  let names=["uCH", "uD0", "uD1", "uD2", "uD3", "uN0", "uN1", "uN2", "uN3", "uYT", "uFW", "uBandCol", "uBand", "uSun", "uSunCol", "uSquash", "uBloomK", "uBreath", "uFlare", "uRimD", "uBellyD", "uRimN", "uBellyN", "uOff", "uScale", "uPlateW", "uSmear", "uStarRot", "uArc", "uTrailDim", "uHold"]
  var args:[Shader.Argument]=names.map { name in
   let v=values[name]!
   if v.count==1 { return .float(v[0]) };if v.count==2 { return .float2(Float(v[0]),Float(v[1])) };return .float3(Float(v[0]),Float(v[1]),Float(v[2]))
  }
  args.append(.image(Image(uiImage:low)));args.append(.image(Image(uiImage:high)))
  return Shader(function:ShaderFunction(library:.default,name:"hourScrubSky"),arguments:args)
 }
}
