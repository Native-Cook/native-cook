import SwiftUI
import UIKit

enum RaceGlobeCalendarUniforms {
 static func shader(_ f:RaceGlobeCalendarFrame,_ atlas:RaceGlobeCalendarAtlas,_ px:Double)->Shader {
  let m=RaceGlobeCalendarMotion.rows(f.lat,f.lon),r=300*f.zoom,cap=20/(r*max(cos(f.lat * .pi/180),0.3))
  let sizes=atlas.regions.map { [Double($0.width),Double($0.height)] }
  let v:[String:[Double]]=["uP3":[0],"uC":[201,372],"uR":[r],"uDist":[4],"uM0":Array(m[0...2]),"uM1":Array(m[3...5]),"uM2":Array(m[6...8]),"uSun":f.sun,"uLonC":[RaceGlobeCalendarMotion.wrap(atan2(m[6],m[8])*180 / .pi) * .pi/180],"uBlur":[min(abs(f.spin) * .pi/180/120,cap)],"uTwk":[2 * .pi*7*f.t/10.5],"uPx":[px],"uDaySz":sizes[0],"uNightSz":sizes[1],"uMaskSz":sizes[2],"uGlowSz":sizes[3]]
  let names=["uP3", "uC", "uR", "uDist", "uM0", "uM1", "uM2", "uSun", "uLonC", "uBlur", "uTwk", "uPx", "uDaySz", "uNightSz", "uMaskSz", "uGlowSz"]
  var args:[Shader.Argument]=names.map { name in let a=v[name]!;if a.count==1 { return .float(a[0]) };if a.count==2 { return .float2(Float(a[0]),Float(a[1])) };return .float3(Float(a[0]),Float(a[1]),Float(a[2])) }
  let atlasWidth = CGFloat(atlas.image.width), atlasHeight = CGFloat(atlas.image.height)
  for rect in atlas.regions {
   args.append(.float4(Float(rect.minX / atlasWidth), Float(rect.minY / atlasHeight),
                       Float(rect.width / atlasWidth), Float(rect.height / atlasHeight)))
  }
  args.append(.image(Image(decorative: atlas.image, scale: 1, orientation: .up)))
  return Shader(function:ShaderFunction(library:.default,name:"raceGlobeCalendar"),arguments:args)
 }
}
