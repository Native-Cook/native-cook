/// The runtime Metal entry points. Uniform arrays avoid Swift/MSL struct-alignment ambiguity.
enum HorizonShader {
  static let source = """
  #include <metal_stdlib>
  using namespace metal;
  struct HorizonScene {
  """ + HorizonSceneShader.source + HorizonFieldShader.source + HorizonGlassShader.source + """
  };
  struct HorizonBar {
  """ + HorizonBarShader.source + """
  };
  struct HorizonFile {
  """ + HorizonFileShader.source + """
  };
  struct HorizonVertex { float4 position [[position]]; };
  vertex HorizonVertex horizonVertex(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    return {float4(p * 2.0 - 1.0, 0.0, 1.0)};
  }
  fragment float4 horizonScene(HorizonVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
    HorizonScene f;
    f.rest=float4(u[0],u[1],u[2],u[3]); f.slab=float4(u[4],u[5],u[6],u[7]);
    f.viewport=float2(u[8],u[9]); f.well=float4(u[10],u[11],u[12],u[13]);
    f.bead=float3(u[14],u[15],u[16]); f.closePress=u[17]; f.planet=float2(u[18],u[19]);
    f.reach=u[20]; f.scale=u[21]; f.slabScale=u[22]; f.progress=u[23]; f.power=u[24];
    f.pulse=u[25]; f.shimmer=u[26]; f.flare=u[27]; f.streak=u[28]; f.settled=u[29];
    f.dawnBreak=u[30]; f.sweep=u[31]; f.sweepStrength=u[32]; f.opaque=u[33];
    return f.main(v.position.xy / f.scale);
  }
  fragment float4 horizonBar(HorizonVertex v [[stage_in]], constant float *u [[buffer(0)]]) {
    HorizonBar f;
    f.uBarWidth=u[0]; f.uProgress=u[1]; f.uPower=u[2]; f.uPulse=u[3]; f.uPaused=u[4];
    f.uCalm=u[5]; f.uShine=u[6]; f.uMotion=u[7]; f.uDone=u[8]; f.uScale=u[9];
    f.uThickness=4.0; f.uBleed=18.0;
    f.uTrackRGB=float3(44.0,56.0,72.0)/255.0;
    f.uFillRGB=float3(253.0,252.0,254.0)/255.0;
    f.uBloomRGB=float3(140.0,166.0,237.0)/255.0;
    f.uDeepRGB=float3(49.0,72.0,146.0)/255.0;
    return f.main(v.position.xy / f.uScale);
  }
  fragment float4 horizonFile(HorizonVertex v [[stage_in]], constant float *u [[buffer(0)]],
      texture2d<float> asset [[texture(0)]]) {
    HorizonFile f;
    f.fAsset=asset; f.fSampler=sampler(coord::normalized, address::clamp_to_zero, filter::linear);
    f.fPower=u[0]; f.fPulse=u[1]; f.fOpaque=u[2]; f.fGlint=u[3]; f.fSheet=u[4];
    f.fSize=float2(u[5],u[6]);
    return f.main(v.position.xy/u[7]);
  }
  """
}
