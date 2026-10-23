/**
 * The four pages of the journal, set like a printed book: ivory stock with its grain, the binding's shade on the
 * gutter side, a running head and a folio, Newsreader throughout (roman for the text, italic for the voice, spaced
 * capitals for the furniture). Each page is laid out on the 402 × 874 reference and scaled by `s`; the curl snapshots
 * them as textures, so everything a page shows is in its view.
 */
import { LinearGradient } from 'expo-linear-gradient';
import { forwardRef, type ReactNode } from 'react';
import { Image, StyleSheet, Text, View, type TextStyle } from 'react-native';
import type { SharedValue } from 'react-native-reanimated';
import Svg, { Circle, Line, Path } from 'react-native-svg';

import { BOOK, COPY, PAPER, SERIF } from './theme';

type PageProps = { s: number; hidden: boolean; twinkle: SharedValue<number> };

const PAPER_TILE = require('./assets/paper.jpg');

function serif(size: number, line: number, s: number, color: string = PAPER.ink, italic = false): TextStyle {
  return { fontFamily: italic ? SERIF.italic : SERIF.roman, fontSize: size * s, lineHeight: line * s, color };
}

/** Spaced capitals: the running heads, the labels. */
function caps(size: number, s: number, color: string = PAPER.mute): TextStyle {
  return { fontFamily: SERIF.roman, fontSize: size * s, lineHeight: size * 1.4 * s, letterSpacing: size * 0.22 * s, color };
}

/** Spaced capitals inside a line of text: no line height of their own, so the line keeps the paragraph's. */
function runIn(size: number, s: number, color: string = PAPER.ink): TextStyle {
  return { fontFamily: SERIF.roman, fontSize: size * s, letterSpacing: size * 0.18 * s, color };
}

const Sheet = forwardRef<View, { children: ReactNode; label: string; hidden: boolean; s: number; head?: string; folio?: string }>(
  function Sheet({ children, label, hidden, s, head, folio }, ref) {
    return (
      <View
        ref={ref}
        collapsable={false}
        style={styles.sheet}
        accessibilityLabel={label}
        accessibilityElementsHidden={hidden}
        importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}
      >
        <Image source={PAPER_TILE} resizeMode="repeat" style={StyleSheet.absoluteFill} />
        {/* The page bends into the binding on the left and its fore-edge catches a little light on the right. */}
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(70,48,24,0.24)', 'rgba(70,48,24,0.07)', 'rgba(70,48,24,0)']}
          locations={[0, 0.35, 1]}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
          style={[styles.gutter, { width: BOOK.gutter * s }]}
        />
        <LinearGradient
          pointerEvents="none"
          colors={['rgba(70,48,24,0)', 'rgba(70,48,24,0.06)']}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
          style={[styles.foreEdge, { width: 14 * s }]}
        />
        {head ? <Text style={[styles.abs, styles.center, { top: BOOK.head * s }, caps(9.5, s)]}>{head}</Text> : null}
        {children}
        {folio ? <Text style={[styles.abs, styles.center, { top: BOOK.folio * s }, serif(13, 18, s, PAPER.mute, true)]}>{folio}</Text> : null}
      </View>
    );
  },
);

/** A printer's rule with a lozenge in the middle. */
function Ornament({ s, top, width = 132 }: { s: number; top: number; width?: number }) {
  const w = width * s;
  const h = 10 * s;
  return (
    <View style={[styles.abs, { top: top * s, left: 0, right: 0, alignItems: 'center' }]}>
      <Svg width={w} height={h} viewBox={`0 0 ${width} 10`}>
        <Line x1="0" y1="5" x2={width / 2 - 10} y2="5" stroke={PAPER.rule} strokeWidth="0.8" />
        <Line x1={width / 2 + 10} y1="5" x2={width} y2="5" stroke={PAPER.rule} strokeWidth="0.8" />
        <Path d={`M${width / 2} 1 L${width / 2 + 4} 5 L${width / 2} 9 L${width / 2 - 4} 5 Z`} fill={PAPER.accent} />
        <Circle cx={width / 2 - 7} cy="5" r="1" fill={PAPER.accent} />
        <Circle cx={width / 2 + 7} cy="5" r="1" fill={PAPER.accent} />
      </Svg>
    </View>
  );
}

/** The publisher's seal: a blind-stamped ring with the volume and the year. */
function Seal({ s, top }: { s: number; top: number }) {
  const d = 104 * s;
  return (
    <View style={[styles.abs, { top: top * s, left: 0, right: 0, alignItems: 'center' }]}>
      <View style={[styles.seal, { width: d, height: d, borderRadius: d / 2, borderWidth: 1 * s }]}>
        <View style={[styles.sealInner, { width: d - 10 * s, height: d - 10 * s, borderRadius: (d - 10 * s) / 2, borderWidth: 0.6 * s }]}>
          <Text style={caps(8.5, s, PAPER.accent)}>{COPY.sealTop}</Text>
          <Text style={serif(19, 24, s, PAPER.accent, true)}>{COPY.sealMid}</Text>
          <Text style={caps(8.5, s, PAPER.accent)}>{COPY.sealBottom}</Text>
        </View>
      </View>
    </View>
  );
}

export const TitlePage = forwardRef<View, PageProps>(function TitlePage({ s, hidden }, ref) {
  return (
    <Sheet ref={ref} label="Title page" hidden={hidden} s={s}>
      <Text style={[styles.abs, styles.center, { top: 150 * s }, serif(16, 22, s, PAPER.mute, true)]}>{COPY.presents}</Text>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: 196 * s }, serif(46, 50, s)]}>{COPY.title}</Text>
      <Ornament s={s} top={318} />
      <Text style={[styles.abs, styles.center, { top: 348 * s }, serif(17, 24, s, PAPER.ink, true)]}>{COPY.subtitle}</Text>
      <Seal s={s} top={470} />
      <Text style={[styles.abs, styles.center, { top: 700 * s }, caps(10, s)]}>{COPY.imprint}</Text>
      <Text style={[styles.abs, { right: BOOK.margin * s, top: BOOK.folio * s }, serif(15, 18, s, PAPER.mute, true)]}>{COPY.hint}</Text>
    </Sheet>
  );
});

export const PrefacePage = forwardRef<View, PageProps>(function PrefacePage({ s, hidden }, ref) {
  const block = { left: BOOK.margin * s, right: BOOK.margin * s };
  return (
    <Sheet ref={ref} label="Preface" hidden={hidden} s={s} head={COPY.headPreface} folio="ii">
      <Text style={[styles.abs, styles.center, { top: 150 * s }, caps(11, s, PAPER.accent)]}>{COPY.preface}</Text>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: 176 * s }, serif(32, 38, s)]}>{COPY.prefaceTitle}</Text>
      <Ornament s={s} top={238} width={64} />
      {COPY.rows.map((r, i) => (
        <Text key={r.title} style={[styles.abs, block, { top: (284 + i * 132) * s, textAlign: 'justify' }, serif(16, 24, s)]}>
          <Text style={runIn(12, s)}>{r.title.toUpperCase()}</Text>
          {'  '}
          {r.body}
        </Text>
      ))}
      <View style={[styles.abs, { left: BOOK.margin * s, top: 640 * s, width: 64 * s, height: StyleSheet.hairlineWidth, backgroundColor: PAPER.rule }]} />
      <Text style={[styles.abs, block, { top: 652 * s }, serif(12.5, 18, s, PAPER.mute)]}>{COPY.footnote}</Text>
    </Sheet>
  );
});

export const ForewordPage = forwardRef<View, PageProps>(function ForewordPage({ s, hidden }, ref) {
  const block = { left: BOOK.margin * s, right: BOOK.margin * s };
  return (
    <Sheet ref={ref} label="Foreword" hidden={hidden} s={s} head={COPY.headForeword} folio="iii">
      <Text style={[styles.abs, styles.center, { top: 150 * s }, caps(11, s, PAPER.accent)]}>{COPY.foreword}</Text>
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: 176 * s }, serif(32, 38, s)]}>{COPY.forewordTitle}</Text>
      <Ornament s={s} top={238} width={64} />
      {/* The text as one column: the first words in spaced capitals, the later paragraphs indented, no gaps. */}
      <View style={[styles.abs, block, { top: 284 * s }]}>
        {COPY.body.map((para, i) => (
          <Text key={i} style={[{ textAlign: 'justify' }, serif(16, 24, s)]}>
            {i === 0 ? (
              <>
                <Text style={runIn(12.5, s, PAPER.accent)}>{COPY.bodyLead}</Text>
                {para}
              </>
            ) : `\u2003${para}`}
          </Text>
        ))}
        <Text style={[{ marginTop: 28 * s, textAlign: 'right' }, serif(15, 22, s, PAPER.mute, true)]}>{COPY.signoff}</Text>
      </View>
      <Ornament s={s} top={620} width={64} />
    </Sheet>
  );
});

export const CoverPage = forwardRef<View, PageProps>(function CoverPage({ s, hidden }, ref) {
  return (
    <Sheet ref={ref} label="This book belongs to" hidden={hidden} s={s}>
      {/* The title is foil-blocked: it catches the light (foil-shader.ts) in this band. */}
      <Text accessibilityRole="header" style={[styles.abs, styles.center, { top: 168 * s }, serif(40, 46, s, PAPER.foil)]}>{COPY.title}</Text>
      <Ornament s={s} top={290} />
      <Text style={[styles.abs, styles.center, { top: 384 * s }, caps(10.5, s)]}>{COPY.belongs}</Text>
      <Text style={[styles.abs, styles.center, { top: 414 * s }, serif(34, 42, s, PAPER.ink, true)]}>{COPY.author}</Text>
      <View style={[styles.abs, { left: 72 * s, right: 72 * s, top: 462 * s, height: StyleSheet.hairlineWidth, backgroundColor: PAPER.rule }]} />
      <Text style={[styles.abs, styles.center, { top: 486 * s }, serif(15, 22, s, PAPER.mute, true)]}>{COPY.begun}</Text>
      <Seal s={s} top={600} />
    </Sheet>
  );
});

const styles = StyleSheet.create({
  sheet: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: PAPER.base, overflow: 'hidden' },
  abs: { position: 'absolute' },
  center: { left: 0, right: 0, textAlign: 'center' },
  gutter: { position: 'absolute', left: 0, top: 0, bottom: 0 },
  foreEdge: { position: 'absolute', right: 0, top: 0, bottom: 0 },
  seal: { borderColor: PAPER.rule, alignItems: 'center', justifyContent: 'center' },
  sealInner: { borderColor: PAPER.rule, alignItems: 'center', justifyContent: 'center' },
});
