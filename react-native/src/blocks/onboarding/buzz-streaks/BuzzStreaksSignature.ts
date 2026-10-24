/** Pure clock math shared by the optional Signature drawing layers. */
export type BuzzStreaksMotionProfile = 'approved' | 'signature' | 'character';
export type SignatureKind = 'orange' | 'pink';
type SignatureCurve = { start: number; end: number; points: number[][] };
export type SignatureRelay = SignatureCurve;
export type SignaturePortal = SignatureCurve & { id: string; lipX: number };
type SignatureContract = {
  version: number;
  end: number;
  easing: { contact: number[]; travel: number[] };
  contact: Record<SignatureKind, number[]>;
  relay: SignatureRelay[];
  packet: { headRadius: number; coreRadius: number; trailLength: number; trailWidth: number; alpha: number; edgeFade: number };
  reflectionRadius: number;
  reflection: Record<SignatureKind, number[]>;
  gaze: Record<SignatureKind, number[]>;
  rewardLock: { start: number; end: number; ids: string[]; x: number; y: number; reflectionAlpha: number };
  portal: SignaturePortal[];
  lip: { length: number; width: number; alpha: number; duration: number };
  badgeReveal: number[];
  domino: { start: number; stagger: number; duration: number; translation: number; rotation: number; scale: number };
};

export const SIGNATURE: SignatureContract = require('./assets/signature.json');

export function signatureClamp(value: number) {
  'worklet';
  return Math.max(0, Math.min(1, value));
}

/** The contract contains 129 samples; interpolate adjacent values, without a native easing solver. */
export function signatureEase(progress: number, table: number[]) {
  'worklet';
  const f = signatureClamp(progress) * (table.length - 1), i = Math.floor(f);
  const j = Math.min(table.length - 1, i + 1);
  return table[i] + (table[j] - table[i]) * (f - i);
}

export function signaturePulse(time: number, start: number, end: number) {
  'worklet';
  if (time <= start || time >= end) return 0;
  return Math.sin(Math.PI * (time - start) / (end - start));
}

export function signatureContactInset(time: number, kind: SignatureKind, enabled: boolean) {
  'worklet';
  if (!enabled) return 0;
  const [start, peak, end, inset] = SIGNATURE.contact[kind];
  if (time <= start || time >= end) return 0;
  return inset * (time <= peak
    ? signatureEase((time - start) / (peak - start), SIGNATURE.easing.contact)
    : 1 - signatureEase((time - peak) / (end - peak), SIGNATURE.easing.contact));
}

export function signatureReflection(time: number, kind: SignatureKind, enabled: boolean) {
  'worklet';
  if (!enabled) return 0;
  const [start, end, alpha] = SIGNATURE.reflection[kind];
  return alpha * signaturePulse(time, start, end);
}

export function signatureGaze(time: number, kind: SignatureKind, axis: 0 | 1, enabled: boolean) {
  'worklet';
  if (!enabled) return 0;
  const [start, end, dx, dy] = SIGNATURE.gaze[kind];
  return (axis === 0 ? dx : dy) * signaturePulse(time, start, end);
}

export function signatureCubic(points: number[][], progress: number, axis: 0 | 1) {
  'worklet';
  const p = signatureClamp(progress), q = 1 - p;
  return q * q * q * points[0][axis] + 3 * q * q * p * points[1][axis]
    + 3 * q * p * p * points[2][axis] + p * p * p * points[3][axis];
}

export function signatureCubicDerivative(points: number[][], progress: number, axis: 0 | 1) {
  'worklet';
  const p = signatureClamp(progress), q = 1 - p;
  return 3 * q * q * (points[1][axis] - points[0][axis])
    + 6 * q * p * (points[2][axis] - points[1][axis])
    + 3 * p * p * (points[3][axis] - points[2][axis]);
}

export function signatureTravel(time: number, curve: SignatureCurve) {
  'worklet';
  return signatureEase((time - curve.start) / (curve.end - curve.start), SIGNATURE.easing.travel);
}

export function signatureRelayAlpha(time: number, relay: SignatureRelay, enabled: boolean) {
  'worklet';
  if (!enabled || time <= relay.start || time >= relay.end) return 0;
  const { alpha, edgeFade } = SIGNATURE.packet;
  return alpha * Math.min(1, (time - relay.start) / edgeFade, (relay.end - time) / edgeFade);
}

export function signatureLock(time: number, enabled: boolean) {
  'worklet';
  const { start, end } = SIGNATURE.rewardLock;
  if (!enabled || time <= start || time >= end) return 0;
  const p = (time - start) / (end - start);
  return Math.sin(2 * Math.PI * p) * (1 - p) * (1 - p);
}

export function signatureBadgeGate(time: number, badgeID: string, enabled: boolean) {
  'worklet';
  if (!enabled || badgeID !== 'upToDay15') return 1;
  const [start, end] = SIGNATURE.badgeReveal;
  return signatureEase((time - start) / (end - start), SIGNATURE.easing.contact);
}

export function signatureDomino(time: number, day: number, enabled: boolean) {
  'worklet';
  if (!enabled || day < 5 || day > 7) return 0;
  const { start, stagger, duration } = SIGNATURE.domino;
  const begin = start + stagger * (day - 5);
  return signaturePulse(time, begin, begin + duration);
}
