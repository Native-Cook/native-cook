import Foundation

struct TrailParallaxTrackerBiome {let width:Double;let offsets:[Double];let sky:[UInt32];let fills:[UInt32];let native:Int}
struct TrailParallaxTrackerWaypoint {let at:Double;let name:String;let note:String}
enum TrailParallaxTrackerWorld {
 static let borders=[624.0,1408.8,2900.0]
 static let starts=[0.0,624,1408.8,2900]
 static let ends=[624.0,1408.8,2900,3600]
 static let biomes:[TrailParallaxTrackerBiome]=[
TrailParallaxTrackerBiome(width:1440,offsets:[560, 620, 484],sky:[0x5E9FE6,0x8FC0F0,0xB5D6F5],fills:[0xC9D8EE,0xA9BEDF,0x8AA3CF,0xF1F5FB],native:0),
TrailParallaxTrackerBiome(width:1560,offsets:[490, 516, 430],sky:[0x5E9FE6,0x8FC0F0,0xB5D6F5],fills:[0xA9BEDF,0xC1A6AB,0x9A8AA5,0x6E6C96,0x4B5980],native:1),
TrailParallaxTrackerBiome(width:2800,offsets:[460, 420, 0],sky:[0xE6EFAE,0xEEF3B0,0xF3F6C2],fills:[0xEEF3B0,0xBFE3A0,0x5FAF8F,0x2F7D6B,0x8E2A22,0x5E1A1C],native:2),
TrailParallaxTrackerBiome(width:1400,offsets:[446, 450, -40],sky:[0xF5DFB6,0xFBECC5,0xFCF1D8],fills:[0xB9B7B0,0xD1A696,0xE3B79A,0xC98F78,0x3E3A3C,0x262325],native:3),
 ]
 static let waypoints:[TrailParallaxTrackerWaypoint]=[
TrailParallaxTrackerWaypoint(at:0,name:"Ember Canyon",note:"A deep canyon cut by the Ember River. The trail keeps to the rim for nine miles, then drops to the water."),
TrailParallaxTrackerWaypoint(at:29.7,name:"Mount Hallam",note:"A granite massif of fins and spires. The trail threads the saddle under its north face, with snow into July."),
TrailParallaxTrackerWaypoint(at:52,name:"Needle Col",note:"A narrow notch between two spires and the high point of the section. The wind picks up fast after noon."),
TrailParallaxTrackerWaypoint(at:57.5,name:"Giants Grove",note:"Sequoias older than any map of the range. The trail runs between their trunks for forty miles of shade."),
TrailParallaxTrackerWaypoint(at:105.7,name:"Tarn Meadow",note:"A string of small lakes in a clearing still ringed by the big trees. The best water on the whole stretch."),
TrailParallaxTrackerWaypoint(at:120.7,name:"Chimney Rock",note:"A lone stack of dark rock above the dunes. Hikers leave a stone on the cairn at its foot for luck."),
TrailParallaxTrackerWaypoint(at:131.7,name:"Sawtooth Saddle",note:"A long, sandy ridge between two ranges, warm until late in the evening and cold right after."),
TrailParallaxTrackerWaypoint(at:142.7,name:"Owl Peak",note:"The last summit before the desert. Camp below it and watch the light leave the hills one by one."),
 ]
 static let hours:[[Double]]=[[-0.035, -0.004, -0.024, 0.9], [0, 0, 0, 1], [0.012, 0.006, 0.02, 1.05], [-0.02, 0.016, 0.034, 0.92], [-0.3, -0.004, -0.075, 0.45]]
 static let hourKeys:[[Double]]=[[-20, 0], [0, 0], [26, 1], [58.7, 1.5], [76, 2], [105, 2.3], [120.83333333333333, 3], [148.5, 3], [150, 4]]
 static func split(_ camera:Double,reduced:Bool)->(a:Int,b:Int,x:Double) {
  let rate=reduced ? 1.0:1.7;var k=0
  for (i,c) in borders.enumerated() {if camera>=c {k=i+1}}
  if k<borders.count {let x=201-rate*(camera-borders[k]);if x<662 {return(k,k+1,x)}}
  if k>0 {let x=201-rate*(camera-borders[k-1]);if x > -260 {return(k-1,k,x)}}
  return(k,k,9999)
 }
 static func offset(_ biome:Int,depth:Int,camera:Double,reduced:Bool)->Double {
  let b=biomes[biome],flat=(b.width-402-(ends[biome]-starts[biome]))/2
  return (camera-starts[biome])*(reduced ? 1:[0.12,0.35,0.7][depth])+(reduced ? flat:b.offsets[depth])
 }
}
