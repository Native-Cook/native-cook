import { useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { clamp01, easeOut } from './motion';

/**
 * A number that rolls digit by digit. Each slot is either a fixed character (`$`, `,`, `.`) or a
 * digit that rolls from `from` to `to` (plus whole `cycles`). Slots that only exist in the funded
 * value (`enter`) grow in from zero width, so everything to their right slides over with them.
 * Slots start right to left, `stagger` seconds apart. Widths are each glyph's real advance,
 * measured once, so the resting text sets exactly like a plain Text.
 */
export type OdometerSlot =
  | { ch: string; enter?: boolean; grey?: boolean }
  | { from: number; to: number; cycles?: number; enter?: boolean; grey?: boolean };

type Props = {
  slots: OdometerSlot[];
  /** 0 = the first value, 1 = the second; linear, the slots ease it themselves. */
  progress: SharedValue<number>;
  /** How long the master progress lasts, in seconds (to turn the stagger into a fraction). */
  span: number;
  stagger: number;
  fontSize: number;
  fontWeight: '400' | '500' | '600';
  letterSpacing: number;
  color: string;
  greyColor?: string;
  /** Pen position of the first slot and the top of the line box, in pt. */
  left: number;
  top: number;
};

const CHARS = '0123456789$,.';

function easeOutBack(p: number) {
  'worklet';
  const x = clamp01(p);
  const c1 = 1.2;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

export function Odometer({ slots, progress, span, stagger, fontSize, fontWeight, letterSpacing, color, greyColor, left, top }: Props) {
  const [adv, setAdv] = useState<Record<string, number> | null>(null);
  const measured = useRef<Record<string, number>>({}).current;
  const lineH = Math.ceil(fontSize * 1.2);
  const font = { fontSize, fontWeight, letterSpacing, color, lineHeight: lineH } as const;

  const onGlyph = (ch: string) => (e: LayoutChangeEvent) => {
    measured[ch] = e.nativeEvent.layout.width;
    if (Object.keys(measured).length === CHARS.length) setAdv({ ...measured });
  };

  const n = slots.length;
  const dur = 1 - ((n - 1) * stagger) / span;

  const geometry = useDerivedValue(() => {
    const xs: number[] = [];
    const vals: number[] = [];
    const shown: number[] = [];
    if (!adv) return { xs, vals, shown };
    const p = progress.get();
    let x = 0;
    for (let i = 0; i < n; i++) {
      const s = slots[i];
      const q = clamp01((p - ((n - 1 - i) * stagger) / span) / dur);
      const presence = s.enter ? easeOutBack(q) : 1;
      let w: number;
      let v = 0;
      if ('ch' in s) {
        w = adv[s.ch] ?? 0;
      } else {
        const total = s.to - s.from + 10 * (s.cycles ?? 0);
        v = s.from + total * easeOut(q);
        const m = ((v % 10) + 10) % 10;
        const d0 = Math.floor(m);
        const f = m - d0;
        w = (adv[String(d0)] ?? 0) * (1 - f) + (adv[String((d0 + 1) % 10)] ?? 0) * f;
      }
      xs.push(x);
      vals.push(v);
      shown.push(s.enter ? clamp01(q * 1.6) : 1);
      x += w * presence;
    }
    return { xs, vals, shown };
  });

  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill]}>
      {/* Glyph advances, measured off screen. */}
      <View style={styles.measure}>
        {CHARS.split('').map((ch) => (
          <Text key={ch} onLayout={onGlyph(ch)} style={[font, styles.glyph]}>
            {ch}
          </Text>
        ))}
      </View>
      {adv &&
        slots.map((s, i) => (
          <Slot
            key={i}
            index={i}
            slot={s}
            geometry={geometry}
            lineH={lineH}
            left={left}
            top={top}
            font={{ ...font, color: s.grey && greyColor ? greyColor : color }}
            maxW={fontSize}
          />
        ))}
    </View>
  );
}

type SlotProps = {
  index: number;
  slot: OdometerSlot;
  geometry: SharedValue<{ xs: number[]; vals: number[]; shown: number[] }>;
  lineH: number;
  left: number;
  top: number;
  font: object;
  maxW: number;
};

function Slot({ index, slot, geometry, lineH, left, top, font, maxW }: SlotProps) {
  const style = useAnimatedStyle(() => {
    const g = geometry.get();
    const x = g.xs[index] ?? 0;
    return { opacity: g.shown[index] ?? 1, transform: [{ translateX: x }] };
  });
  const strip = useAnimatedStyle(() => {
    const v = geometry.get().vals[index] ?? 0;
    const m = ((v % 10) + 10) % 10;
    return { transform: [{ translateY: -m * lineH }] };
  });
  if ('ch' in slot) {
    return (
      <Animated.View style={[styles.slot, { left, top, height: lineH }, style]}>
        <Text style={[font, styles.glyph]}>{slot.ch}</Text>
      </Animated.View>
    );
  }
  return (
    <Animated.View style={[styles.slot, styles.clip, { left, top, height: lineH, width: maxW }, style]}>
      <Animated.View style={strip}>
        {['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'].map((d, k) => (
          <Text key={k} style={[font, styles.glyph]}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  measure: { position: 'absolute', left: -2000, top: 0, flexDirection: 'row', opacity: 0 },
  glyph: { includeFontPadding: false },
  slot: { position: 'absolute' },
  clip: { overflow: 'hidden' },
});
