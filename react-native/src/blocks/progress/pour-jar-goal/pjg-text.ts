import { clamp, rollingDigit } from './pjg-motion';
export function money(value: number) {
  'worklet';
  const s = String(Math.max(0, Math.round(value)));
  let out = '';
  for (let i = 0; i < s.length; i++) {
    if (i && (s.length - i) % 3 === 0) out += ',';
    out += s[i];
  }
  return '$' + out;
}
export function percentage(value: number, min: number, max: number) {
  'worklet';
  return Math.floor(clamp((value - min) / Math.max(1, max - min)) * 100);
}
export function digitAt(value: number, place: number) {
  'worklet';
  if (place === 1) return Math.max(0, value) % 10;
  return rollingDigit(value, place);
}
export function amountSlots(max: number, digitWidth: number, dollarWidth: number, commaWidth: number) {
  const length = Math.max(1, String(Math.floor(Math.max(1, max))).length);
  const slots: { char: string; place: number; x: number; width: number }[] = [{ char: '$', place: 0, x: 0, width: dollarWidth }];
  let x = dollarWidth;
  for (let n = length - 1; n >= 0; n--) {
    slots.push({ char: '', place: 10 ** n, x, width: digitWidth });
    x += digitWidth;
    if (n && n % 3 === 0) {
      slots.push({ char: ',', place: 0, x, width: commaWidth });
      x += commaWidth;
    }
  }
  return { slots, width: x };
}
