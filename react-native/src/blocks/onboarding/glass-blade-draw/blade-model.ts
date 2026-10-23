import { BLADE_CARDS, BLADE_HUES, CTA_LIME, type FanLayout } from './blade-contract';

export const BLADE_COUNT = 11;
const RAD = Math.PI / 180;

export interface BladeFrame {
  pos: number;
  charge: number;
  flare: number;
  press: number;
  ctaEnergy: number;
  intro: number;
  still: number;
}

export function bladeClamp(value: number, low = 0, high = 1): number {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

export function bladeSmooth(low: number, high: number, value: number): number {
  'worklet';
  const t = bladeClamp((value - low) / (high - low));
  return t * t * (3 - 2 * t);
}

export function bladeRGB(hex: string): number[] {
  'worklet';
  const rgb = parseInt(hex.slice(1), 16);
  return [(rgb >> 16) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255];
}

export function bladeCard(index: number) {
  'worklet';
  const wrapped = ((index % BLADE_CARDS.length) + BLADE_CARDS.length) % BLADE_CARDS.length;
  const card = BLADE_CARDS[wrapped];
  return { value: card.value, colour: bladeRGB(BLADE_HUES[card.hue]) };
}

/** Continuous at the slot boundaries, including the change of yaw sign. */
export function bladePose(u: number, intro: number, charge: number, seconds = 0, still = 1) {
  'worklet';
  const a = Math.abs(u);
  const near = 1 - bladeSmooth(0.18, 0.85, a);
  const raised = 1 - bladeSmooth(0, 1.08, a);
  const opening = bladeClamp(intro);
  let yawMagnitude = 77;
  if (a > 1 && a <= 2) yawMagnitude = 77 - 22 * bladeSmooth(1, 2, a);
  else if (a > 2 && a <= 3) yawMagnitude = 55 - 13 * bladeSmooth(2, 3, a);
  else if (a > 3) yawMagnitude = 42 - 10 * bladeSmooth(3, 4, a);
  const turn = bladeSmooth(0, 0.48, a);
  const side = u < 0 ? 1 : -1;
  const yaw = -42 * (1 - turn) + side * yawMagnitude * turn;
  const idle = (1 - bladeClamp(still)) * opening;
  const breath = Math.sin(seconds * Math.PI * 2 / 5 + u * 1.13);
  return {
    phi: u * 9.5 * RAD * opening,
    yaw: (yaw * opening + 1.2 * breath * idle) * RAD,
    lift: raised * opening,
    bob: 1.5 * raised * breath * idle,
    depth: (26 * raised + (24 - 52 * a) * (1 - raised)) * opening,
    light: bladeClamp(near * charge, 0, 1.25) * opening,
    opacity: 1 - bladeSmooth(4, 5, a),
  };
}

/** Flat fixed-size arrays are the shared ABI of Skia JSI and the offline CanvasKit renderer. */
export function bladeUniforms(layout: FanLayout, frame: BladeFrame, seconds = 0, pixelSize = 1 / 3) {
  'worklet';
  const uCentre: number[] = [];
  const uAxisX: number[] = [];
  const uAxisY: number[] = [];
  const uNormal: number[] = [];
  const uBounds: number[] = [];
  const uCard: number[] = [];
  const uColour: number[] = [];
  const slots: number[] = [];
  const indices: number[] = [];
  const pivot = Math.round(frame.pos);
  for (let j = 0; j < BLADE_COUNT; j++) {
    indices.push(pivot + j - 5);
    slots.push(frame.pos - (pivot + j - 5));
  }
  // Stable insertion sort: nearest first; no callbacks cross a worklet boundary.
  for (let j = 1; j < BLADE_COUNT; j++) {
    const slot = slots[j];
    const index = indices[j];
    let k = j - 1;
    while (k >= 0 && Math.abs(slots[k]) > Math.abs(slot)) {
      slots[k + 1] = slots[k];
      indices[k + 1] = indices[k];
      k--;
    }
    slots[k + 1] = slot;
    indices[k + 1] = index;
  }
  const focal = 420 * layout.scale;
  const eyeX = layout.width / 2;
  const eyeY = layout.ctaY - layout.cardH * 0.44;
  let fanTop = layout.height;
  const heroColour = [0, 0, 0];
  let heroPower = 0;
  for (let j = 0; j < BLADE_COUNT; j++) {
    const u = slots[j];
    const pose = bladePose(u, frame.intro, frame.charge, seconds, frame.still);
    const card = bladeCard(indices[j]);
    const sp = Math.sin(pose.phi);
    const cp = Math.cos(pose.phi);
    const radius = layout.sideBottomR + (layout.heroBottomR - layout.sideBottomR) * pose.lift
      + layout.cardH / 2 + pose.bob * layout.scale;
    const cx = layout.pivotX + sp * radius;
    const centerY = layout.pivotY - cp * radius;
    const cz = pose.depth * layout.scale;
    // Keep side slots edge-on relative to their sightline with the shorter lens.
    const yaw = pose.yaw - Math.atan((cx - eyeX) / (focal - cz)) * (1 - pose.lift);
    const sy = Math.sin(yaw);
    const cy = Math.cos(yaw);
    const lean = pose.phi + 3 * RAD * pose.lift;
    const sl = Math.sin(lean);
    const cl = Math.cos(lean);
    const ax = [cl * cy, sl * cy, -sy];
    const ay = [-sl, cl, 0];
    const az = [cl * sy, sl * sy, cy];
    uCentre.push(cx, centerY, cz);
    uAxisX.push(ax[0], ax[1], ax[2]);
    uAxisY.push(ay[0], ay[1], ay[2]);
    uNormal.push(az[0], az[1], az[2]);
    let minX = layout.width * 10;
    let minY = layout.height * 10;
    let maxX = -minX;
    let maxY = -minY;
    for (let corner = 0; corner < 8; corner++) {
      const x = (corner & 1 ? 0.5 : -0.5) * layout.cardW;
      const y = (corner & 2 ? 0.5 : -0.5) * layout.cardH;
      const z = (corner & 4 ? 0.5 : -0.5) * layout.cardT;
      const persp = focal / (focal - cz - ax[2] * x - az[2] * z);
      const px = eyeX + (cx + ax[0] * x + ay[0] * y + az[0] * z - eyeX) * persp;
      const py = eyeY + (centerY + ax[1] * x + ay[1] * y + az[1] * z - eyeY) * persp;
      minX = Math.min(minX, px);
      minY = Math.min(minY, py);
      maxX = Math.max(maxX, px);
      maxY = Math.max(maxY, py);
    }
    const margin = (pose.light > 0.01 ? 42 : 8) * layout.scale;
    uBounds.push(minX - margin, minY - margin, maxX + margin, maxY + margin);
    fanTop = Math.min(fanTop, minY - 8);
    uCard.push(card.value, pose.light, pose.opacity, u);
    uColour.push(card.colour[0], card.colour[1], card.colour[2]);
    const energy = pose.light * pose.light;
    for (let c = 0; c < 3; c++) heroColour[c] += card.colour[c] * energy;
    heroPower += energy;
  }
  const heroDivisor = Math.max(heroPower, 0.001);
  for (let c = 0; c < 3; c++) heroColour[c] /= heroDivisor;
  return {
    uResolution: [layout.width, layout.height],
    uEye: [eyeX, eyeY, focal],
    uSize: [layout.cardW / 2, layout.cardH / 2, layout.cardT / 2, layout.cornerR],
    uCentre, uAxisX, uAxisY, uNormal, uBounds, uCard, uColour,
    uHero: [...heroColour, Math.min(heroPower, 1.5)],
    uBar: [layout.ctaX + layout.ctaW / 2, layout.ctaY + layout.ctaH / 2, layout.ctaW / 2, layout.ctaH / 2],
    uBarRadius: layout.ctaR,
    uLime: bladeRGB(CTA_LIME),
    uMotion: [frame.flare, frame.press, frame.ctaEnergy, frame.intro],
    uPixel: pixelSize,
    uScale: layout.scale,
    uFanTop: fanTop,
  };
}
