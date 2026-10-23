import { SymbolView } from 'expo-symbols';
import { forwardRef, type ReactNode } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Defs, G, LinearGradient, Path, Rect, Stop } from 'react-native-svg';

import { TWINKLE } from './motion';
import { BRAND, COPY, ICON, INK, LAYOUT, TYPE } from './theme';

type Line = (typeof TYPE)[keyof typeof TYPE];

// Every page is drawn on the 402-pt reference grid and scaled by `s` (screen width / 402).
function lineStyle(l: Line, s: number, color: string): TextStyle {
  return { fontSize: l.size * s, lineHeight: l.line * s, fontWeight: l.weight, color };
}

const Sheet = forwardRef<View, { children: ReactNode; label: string; hidden: boolean }>(function Sheet(
  { children, label, hidden },
  ref,
) {
  return (
    <View
      ref={ref}
      collapsable={false}
      style={styles.sheet}
      accessibilityLabel={label}
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}
    >
      {children}
    </View>
  );
});

// A four-point sparkle; the source tints it with four hues around its centre.
function sparklePath(r: number) {
  const k = r * 0.17;
  return `M0 ${-r} Q${k} ${-k} ${r} 0 Q${k} ${k} 0 ${r} Q${-k} ${k} ${-r} 0 Q${-k} ${-k} 0 ${-r} Z`;
}

function Sparkle({ size, colored }: { size: number; colored?: boolean }) {
  const r = size / 2;
  return (
    <Svg width={size} height={size} viewBox={`${-r} ${-r} ${size} ${size}`}>
      {colored ? (
        <>
          <Defs>
            <LinearGradient id="sv" x1="0" y1={-r} x2="0" y2={r} gradientUnits="userSpaceOnUse">
              <Stop offset="0" stopColor={ICON.sparkle[0]} />
              <Stop offset="0.5" stopColor="#F2A2B8" />
              <Stop offset="1" stopColor={ICON.sparkle[2]} />
            </LinearGradient>
            <LinearGradient id="sh" x1={-r} y1="0" x2={r} y2="0" gradientUnits="userSpaceOnUse">
              <Stop offset="0" stopColor={ICON.sparkle[3]} stopOpacity="0.9" />
              <Stop offset="0.5" stopColor="#FFFFFF" stopOpacity="0" />
              <Stop offset="1" stopColor={ICON.sparkle[1]} stopOpacity="0.95" />
            </LinearGradient>
          </Defs>
          <Path d={sparklePath(r)} fill="url(#sv)" />
          <Path d={sparklePath(r)} fill="url(#sh)" />
        </>
      ) : (
        <Path d={sparklePath(r)} fill={ICON.markInk} />
      )}
    </Svg>
  );
}

// A sparkle that twinkles once when `t` runs 0 → 1: it swells and turns a quarter (a four-point star looks the same
// after 90°), so it rests exactly as drawn. `delay` staggers it, as a fraction of the run.
function Twinkle({ t, delay, children }: { t: SharedValue<number>; delay: number; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const u = Math.max(0, Math.min(1, (t.value - delay) / (1 - delay)));
    const swell = Math.sin(Math.PI * u);
    return { transform: [{ scale: 1 + (TWINKLE.scale - 1) * swell }, { rotate: `${TWINKLE.turnDeg * u}deg` }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

// "Ritualbook" with its i dotted by a sparkle, as the source's wordmark is.
function Wordmark({ s, t }: { s: number; t: SharedValue<number> }) {
  const l = TYPE.brand;
  const st = [lineStyle(l, s, INK.text), { letterSpacing: -0.4 * s }];
  const [head, tail] = [BRAND.name.slice(0, 1), BRAND.name.slice(2)];
  return (
    <View style={[styles.wordRow, { top: l.top * s, height: l.line * s }]} accessible accessibilityRole="header" accessibilityLabel={BRAND.name}>
      <Text style={st}>{head}</Text>
      <View>
        <Text style={st}>{'ı'}</Text>
        <View style={[styles.dot, { top: 4.6 * s, left: 0.3 * s }]}>
          <Twinkle t={t} delay={TWINKLE.staggerMs / TWINKLE.durationMs}>
            <Sparkle size={7 * s} />
          </Twinkle>
        </View>
      </View>
      <Text style={st}>{tail}</Text>
    </View>
  );
}

// The app mark: a white squircle with a heavy monogram and its bottom-left corner curling up like a page.
function Mark({ s, t }: { s: number; t: SharedValue<number> }) {
  const m = LAYOUT.mark;
  const size = m.size * s;
  return (
    <View style={[styles.mark, { left: m.left * s, top: m.top * s, width: size, height: size, borderRadius: m.radius * s }]}>
      <View style={[styles.markLetters, { top: 18 * s }]}>
        <Text style={[styles.markText, { fontSize: 62 * s, lineHeight: 74 * s }]}>R</Text>
        <View>
          <Text style={[styles.markText, { fontSize: 62 * s, lineHeight: 74 * s }]}>{'ı'}</Text>
          <View style={[styles.dot, { top: 4 * s, left: -3 * s }]}>
            <Twinkle t={t} delay={0}>
              <Sparkle size={20 * s} />
            </Twinkle>
          </View>
        </View>
      </View>
      <Svg style={StyleSheet.absoluteFill} viewBox="0 0 100 100">
        <Defs>
          <LinearGradient id="flap" x1="22" y1="78" x2="4" y2="96" gradientUnits="userSpaceOnUse">
            <Stop offset="0" stopColor={ICON.curlDark} />
            <Stop offset="0.45" stopColor={ICON.curlMid} />
            <Stop offset="1" stopColor={ICON.curlLight} />
          </LinearGradient>
          <LinearGradient id="lip" x1="20" y1="80" x2="30" y2="90" gradientUnits="userSpaceOnUse">
            <Stop offset="0" stopColor="#FFFFFF" stopOpacity="0" />
            <Stop offset="1" stopColor="#FFFFFF" stopOpacity="0.95" />
          </LinearGradient>
        </Defs>
        <G>
          <Path d="M0 60 C10 70 30 88 41 100 L0 100 Z" fill="url(#flap)" />
          <Path d="M0 60 C10 70 30 88 41 100 L46 100 C34 88 14 70 0 57 Z" fill="url(#lip)" />
        </G>
      </Svg>
    </View>
  );
}

function CheckBadge({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 28 28">
      <Defs>
        <LinearGradient id="ck" x1="24" y1="3" x2="5" y2="26" gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={ICON.check[0]} />
          <Stop offset="0.55" stopColor={ICON.check[1]} />
          <Stop offset="1" stopColor={ICON.check[2]} />
        </LinearGradient>
      </Defs>
      <Rect x="0" y="0" width="28" height="28" rx="14" fill="url(#ck)" />
      <Path d="M8.2 14.6 L12.4 18.6 L20 9.6" stroke="#FFFFFF" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

function BarsIcon({ size }: { size: number }) {
  const bars = [
    { x: 3, h: 15, o: 0.95 },
    { x: 9.6, h: 20, o: 0.85 },
    { x: 16.2, h: 16, o: 0.7 },
    { x: 22.8, h: 10, o: 0.5 },
  ];
  return (
    <Svg width={size} height={size} viewBox="0 0 30 30">
      <Defs>
        <LinearGradient id="bar" x1="0" y1="3" x2="0" y2="25" gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={ICON.bars[0]} />
          <Stop offset="1" stopColor={ICON.bars[1]} />
        </LinearGradient>
      </Defs>
      {bars.map((b) => (
        <Rect key={b.x} x={b.x} y={24 - b.h} width="4.6" height={b.h} rx="1" fill="url(#bar)" opacity={b.o} />
      ))}
      <Rect x="1" y="26.2" width="28" height="2.2" rx="1.1" fill={ICON.barsBase} />
    </Svg>
  );
}

type PageProps = { s: number; hidden: boolean; twinkle: SharedValue<number> };

export const TitlePage = forwardRef<View, PageProps>(function TitlePage({ s, hidden, twinkle }, ref) {
  return (
    <Sheet ref={ref} label={COPY.pages[0]} hidden={hidden}>
      <Text style={[styles.abs, styles.center, { top: TYPE.eyebrow.top * s }, lineStyle(TYPE.eyebrow, s, INK.eyebrow)]}>{COPY.meet}</Text>
      <Wordmark s={s} t={twinkle} />
      <Mark s={s} t={twinkle} />
      <Text style={[styles.abs, styles.center, { top: TYPE.intro.top * s }, lineStyle(TYPE.intro, s, INK.text)]}>{COPY.intro}</Text>
      <View style={[styles.hintRow, { top: TYPE.hint.top * s, height: TYPE.hint.line * s }]}>
        <Text style={lineStyle(TYPE.hint, s, INK.mute)}>{COPY.hint}</Text>
        <SymbolView
          name="arrow.right"
          size={LAYOUT.hintArrow.size * s}
          weight="medium"
          tintColor={INK.mute}
          style={{ marginLeft: LAYOUT.hintArrow.gap * s, width: LAYOUT.hintArrow.size * s, height: LAYOUT.hintArrow.size * s }}
        />
      </View>
    </Sheet>
  );
});

export const PrefacePage = forwardRef<View, PageProps>(function PrefacePage({ s, hidden }, ref) {
  const r = LAYOUT.rows;
  return (
    <Sheet ref={ref} label={COPY.pages[1]} hidden={hidden}>
      <Text style={[styles.abs, styles.center, { top: TYPE.eyebrow.top * s }, lineStyle(TYPE.eyebrow, s, INK.eyebrow)]}>{COPY.preface}</Text>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: TYPE.title.top * s }, lineStyle(TYPE.title, s, INK.text)]}>{COPY.prefaceTitle}</Text>
      {COPY.rows.map((row, i) => (
        <View key={row.title}>
          <View style={[styles.icon, { left: (r.iconX - r.icon / 2) * s, top: (r.iconY[i] - r.icon / 2) * s }]}>
            {i === 0 ? <CheckBadge size={r.icon * s} /> : <BarsIcon size={r.icon * s} />}
          </View>
          <View style={[styles.abs, { left: r.textLeft * s, top: r.titleTop[i] * s }]}>
            <Text style={lineStyle(TYPE.rowTitle, s, INK.text)}>{row.title}</Text>
            <Text style={[lineStyle(TYPE.rowBody, s, INK.text), { marginTop: r.gap * s }]}>{row.body}</Text>
          </View>
        </View>
      ))}
      <View style={[styles.shield, { top: LAYOUT.shield.top * s }]}>
        <SymbolView name="shield.lefthalf.filled" size={LAYOUT.shield.size * s} tintColor={INK.footnote} />
      </View>
      <Text style={[styles.abs, styles.center, { top: TYPE.footnote.top * s }, lineStyle(TYPE.footnote, s, INK.footnote)]}>{COPY.footnote}</Text>
      <Text style={[styles.abs, styles.center, { top: TYPE.folio.top * s }, lineStyle(TYPE.folio, s, INK.mute)]}>2</Text>
    </Sheet>
  );
});

export const ForewordPage = forwardRef<View, PageProps>(function ForewordPage({ s, hidden }, ref) {
  return (
    <Sheet ref={ref} label={COPY.pages[2]} hidden={hidden}>
      <Text style={[styles.abs, styles.center, { top: TYPE.eyebrow.top * s }, lineStyle(TYPE.eyebrow, s, INK.eyebrow)]}>{COPY.foreword}</Text>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: TYPE.title.top * s }, lineStyle(TYPE.title, s, INK.text)]}>{COPY.forewordTitle}</Text>
      <Text style={[styles.abs, { left: LAYOUT.bodyLeft * s, right: 0, top: TYPE.body.top * s }, lineStyle(TYPE.body, s, INK.text)]}>{COPY.body}</Text>
      <Text style={[styles.abs, styles.center, { top: TYPE.folio.top * s }, lineStyle(TYPE.folio, s, INK.mute)]}>3</Text>
    </Sheet>
  );
});

export const CoverPage = forwardRef<View, PageProps>(function CoverPage({ s, hidden }, ref) {
  const sp = LAYOUT.sparkle;
  return (
    <Sheet ref={ref} label={COPY.pages[3]} hidden={hidden}>
      <View style={[styles.abs, { left: (sp.cx - sp.size / 2) * s, top: (sp.cy - sp.size / 2) * s }]}>
        <Sparkle size={sp.size * s} colored />
      </View>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: TYPE.cover.top * s }, lineStyle(TYPE.cover, s, INK.text)]}>{COPY.cover}</Text>
      <Text style={[styles.abs, styles.center, { top: TYPE.caption.top * s, letterSpacing: 0.3 * s }, lineStyle(TYPE.caption, s, INK.caption)]}>{COPY.caption}</Text>
      <Text style={[styles.abs, styles.center, { top: TYPE.author.top * s }, lineStyle(TYPE.author, s, INK.author)]}>{COPY.author}</Text>
      <Text style={[styles.abs, styles.center, { top: TYPE.folio.top * s }, lineStyle(TYPE.folio, s, INK.mute)]}>4</Text>
    </Sheet>
  );
});

const styles = StyleSheet.create({
  sheet: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: INK.paper, overflow: 'hidden' },
  abs: { position: 'absolute' },
  center: { left: 0, right: 0, textAlign: 'center' },
  wordRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center' },
  dot: { position: 'absolute' },
  mark: {
    position: 'absolute',
    backgroundColor: INK.paper,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: INK.iconLine,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  markLetters: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center' },
  markText: { fontWeight: '900', color: ICON.markInk, letterSpacing: -2 },
  icon: { position: 'absolute' },
  shield: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  hintRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
});
