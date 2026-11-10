// Pearl Shell Premium — the shader's uniforms from the block's state.

export function makeUniforms(g: { w: number; h: number; orb: [number, number, number] }, s: { t: number; spin: number; lift: number; burst: number; glow: number }) {
  'worklet';
  return { uRes: [g.w, g.h], uOrb: g.orb, uT: s.t, uLoop: 10, uSpin: s.spin, uLift: s.lift, uBurst: s.burst, uGlow: s.glow };
}
