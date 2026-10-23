import type { ReactNode } from 'react';
/**
 * The sheet's content. It lives inside a rounded view that is always inscribed in the black drop,
 * so while the island swells the contacts are already there, seen through the drop.
 */
import { Image, Pressable, StyleSheet, Text, View, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { cubicBezier, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { clamp01, bodyAt, contentClip } from './island-path';
import { AMBIENT, FADE, MAGNET, PRESS } from './motion';
import { COLORS, CONTACTS, FAVOURITES, SHEET, TYPE, type Person, SPACE } from './theme';
import type { Geometry, SendValues } from './use-send-motion';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export function Avatar({ person, size }: { person: Person; size: number }) {
  return <Image source={person.photo} style={{ width: size, height: size, borderRadius: size / 2 }} accessibilityIgnoresInvertColors />;
}

/** How much target i is hovered (0..1.7 while it drinks the card). */
function useTargetAmount(v: SendValues, i: number) {
  return useAnimatedStyle(() => {
    const amt = (v.hoverIdx.get() === i ? v.hoverAmt.get() : 0) + (v.prevIdx.get() === i ? v.prevAmt.get() : 0);
    return { transform: [{ scale: 1 + (MAGNET.targetScale - 1) * amt }] };
  });
}

function Favourite({ person, index, v, onPress }: { person: Person; index: number; v: SendValues; onPress: () => void }) {
  const scale = useTargetAmount(v, index);
  return (
    <PressScale outer={[styles.fav, { transform: [{ translateX: SHEET.favNudge[index] }] }]} style={styles.favInner} onPress={onPress} accessibilityRole="button"
      accessibilityLabel={`Send to ${person.name}`}>
      <Animated.View style={scale}><Avatar person={person} size={SHEET.favSize} /></Animated.View>
      <Text style={styles.favName}>{person.name}</Text>
    </PressScale>
  );
}

function ContactRow({ person, index, v, onPress }: { person: Person; index: number; v: SendValues; onPress: () => void }) {
  const target = FAVOURITES.length + index;
  const scale = useTargetAmount(v, target);
  const fill = useAnimatedStyle(() => {
    const amt = (v.hoverIdx.get() === target ? v.hoverAmt.get() : 0) + (v.prevIdx.get() === target ? v.prevAmt.get() : 0);
    return { opacity: clamp01(amt) };
  });
  return (
    <PressScale style={styles.row} onPress={onPress} accessibilityRole="button" accessibilityLabel={`Send to ${person.name}`}>
      <Animated.View style={[styles.rowFill, fill]} />
      <Animated.View style={scale}><Avatar person={person} size={SHEET.contactSize} /></Animated.View>
      <Text style={styles.contact}>{person.name}</Text>
    </PressScale>
  );
}

/** Payoff: sparks burst out of the person who received the card, riding the progress ring. */
function Spark({ k, geo, v, reduced }: { k: number; geo: Geometry; v: SendValues; reduced: boolean }) {
  const a = (k / AMBIENT.sparks) * Math.PI * 2;
  const style = useAnimatedStyle(() => {
    const i = v.sendIdx.get();
    const r = v.ring.get();
    if (i < 0 || reduced || r <= 0 || r >= 1) return { opacity: 0 };
    const base = (geo.targets.row[i] ? SHEET.contactSize : SHEET.favSize) / 2 + 6;
    const d = base + AMBIENT.sparkReach * Math.sqrt(r);
    return {
      opacity: 1 - r,
      left: geo.targets.x[i] - SHEET.inset + Math.cos(a) * d - 3,
      top: geo.targets.y[i] - SHEET.top + Math.sin(a) * d - 3,
      transform: [{ scale: 1 - 0.6 * r }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.spark, style]} />;
}

type Props = {
  geo: Geometry;
  v: SendValues;
  reduced: boolean;
  interactive: boolean;
  onNext: () => void;
  onTarget: (i: number) => void;
  onClose: () => void;
};

export function SendSheet({ geo, v, reduced, interactive, onTarget, onNext, onClose }: Props) {
  const { width, height } = geo;
  const sheetW = width - SHEET.inset * 2;
  const sheetH = height - SHEET.top - SHEET.bottomGap;
  const ringR = (SHEET.favSize / 2) * MAGNET.targetScale + 6;

  const clip = useAnimatedStyle(() => {
    const g = v.g.get();
    const c = contentClip(g, width, height);
    const fade = reduced ? v.veil.get() : clamp01((g - FADE.contentFrom) / (FADE.contentTo - FADE.contentFrom));
    return { left: c.x, top: c.y, width: c.w, height: c.h, borderRadius: Math.max(0, c.r), opacity: fade };
  });
  // The source's drop shows the contacts but not the Next button; it appears with the sheet.
  const nextStyle = useAnimatedStyle(() => ({ opacity: clamp01((v.g.get() - 1.3) / 0.4) }));
  const inner = useAnimatedStyle(() => {
    const g = v.g.get();
    const c = contentClip(g, width, height);
    const body = bodyAt(g, width, height);
    // As a drop, the whole sheet is shown scaled down to the drop's width, from the favourites row
    // (source f025); as the drop becomes the sheet it grows into place.
    const t = clamp01((g - 1) / 0.6);
    const k = Math.min(1, body.w / sheetW) * (1 - t) + t;
    const dropX = width / 2 - (sheetW * k) / 2 - c.x;
    const dropY = body.y + 12 - (SHEET.favTop - 8) * k - c.y;
    const finalX = SHEET.inset - c.x;
    const finalY = SHEET.top - c.y;
    const x = dropX + (finalX - dropX) * t;
    const y = dropY + (finalY - dropY) * t;
    return {
      left: x - (sheetW * (1 - k)) / 2, top: y - (sheetH * (1 - k)) / 2, transform: [{ scale: k }],
    };
  });
  const ringStyle = useAnimatedStyle(() => {
    const i = v.sendIdx.get();
    if (i < 0) return { opacity: 0 };
    const r = geo.targets.row[i] ? (SHEET.contactSize / 2) * MAGNET.targetScale + 5 : ringR;
    return {
      opacity: 1,
      left: geo.targets.x[i] - SHEET.inset - ringR,
      top: geo.targets.y[i] - SHEET.top - ringR,
      transform: [{ scale: r / ringR }],
    };
  });
  const circumference = 2 * Math.PI * (ringR - 2);
  const ringProps = useAnimatedProps(() => ({ strokeDashoffset: circumference * (1 - v.ring.get()) }));
  const badge = useAnimatedStyle(() => {
    const i = v.sendIdx.get();
    if (i < 0) return { opacity: 0 };
    const r = geo.targets.row[i] ? SHEET.contactSize / 2 : SHEET.favSize / 2;
    return {
      opacity: clamp01(v.check.get() * 3),
      left: geo.targets.x[i] - SHEET.inset + r * 0.72 - 11,
      top: geo.targets.y[i] - SHEET.top + r * 0.72 - 11,
      transform: [{ scale: v.check.get() }],
    };
  });

  return (
    <Animated.View style={[styles.clip, clip]} pointerEvents={interactive ? 'box-none' : 'none'}
      accessibilityElementsHidden={!interactive} importantForAccessibility={interactive ? 'auto' : 'no-hide-descendants'}>
      <Animated.View style={[{ position: 'absolute', width: sheetW, height: sheetH }, inner]} pointerEvents="box-none">
        <View style={styles.titleRow}>
          <View>
            <Text style={[styles.title, { color: COLORS.sheetMute }]}>Send</Text>
            <Text style={styles.title} accessibilityRole="header">Money To</Text>
          </View>
          <PressScale onPress={onClose} hitSlop={4} style={styles.close} accessibilityRole="button" accessibilityLabel="Close">
            <Svg width={16} height={16} viewBox="0 0 16 16">
              <Path d="M3 3 L13 13 M13 3 L3 13" stroke={COLORS.sheetInk} strokeWidth={1.8} strokeLinecap="round" />
            </Svg>
          </PressScale>
        </View>
        <View style={styles.favs}>
          {FAVOURITES.map((p, i) => <Favourite key={p.id} person={p} index={i} v={v} onPress={() => onTarget(i)} />)}
          <View style={[styles.fav, styles.favInner, { transform: [{ translateX: SHEET.favNudge[3] }] }]} accessible={false}>
            <View style={[styles.add, styles.center]}>
              <Svg width={18} height={18} viewBox="0 0 20 20">
                <Path d="M10 3 V17 M3 10 H17" stroke={COLORS.sheetInk} strokeWidth={1.8} strokeLinecap="round" />
              </Svg>
            </View>
            <Text style={styles.favName}>Add New</Text>
          </View>
        </View>
        <View style={styles.section}>
          <Text style={[styles.sectionText, { color: COLORS.sheetMute, fontWeight: '500' }]}>Your</Text>
          <Text style={styles.sectionText} accessibilityRole="header">Contacts</Text>
        </View>
        <View style={styles.rows}>
          {CONTACTS.map((p, i) => <ContactRow key={p.id} person={p} index={i} v={v} onPress={() => onTarget(FAVOURITES.length + i)} />)}
        </View>
        <AnimatedPressable onPress={onNext} style={[styles.next, { left: (sheetW - SHEET.nextWidth) / 2 }, nextStyle]} accessibilityRole="button"
          accessibilityLabel="Next, send to the selected person">
          <Text style={styles.nextText}>Next</Text>
        </AnimatedPressable>
        {Array.from({ length: AMBIENT.sparks }, (_, k) => <Spark key={k} k={k} geo={geo} v={v} reduced={reduced} />)}
        <Animated.View style={[styles.ring, { width: ringR * 2, height: ringR * 2 }, ringStyle]} pointerEvents="none">
          <Svg width={ringR * 2} height={ringR * 2}>
            <AnimatedCircle cx={ringR} cy={ringR} r={ringR - 2} stroke={COLORS.ring} strokeWidth={3} fill="none"
              strokeLinecap="round" strokeDasharray={`${circumference} ${circumference}`} animatedProps={ringProps}
              transform={`rotate(-90 ${ringR} ${ringR})`} />
          </Svg>
        </Animated.View>
        <Animated.View style={[styles.badge, badge]} pointerEvents="none">
          <Svg width={12} height={12} viewBox="0 0 12 12">
            <Path d="M2.5 6.2 L5 8.6 L9.6 3.6" stroke={COLORS.island} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

// Press feedback on the sheet's buttons: scale 0.97 on press-in (100 ms), back on release (160 ms), strong ease-out.
function PressScale({ children, style, outer, ...rest }: PressableProps & { style?: StyleProp<ViewStyle>; outer?: StyleProp<ViewStyle> }) {
  return (
    <Pressable {...rest} style={outer}>
      {({ pressed }) => (
        <Animated.View style={[style, {
          transform: [{ scale: pressed ? PRESS.scale : 1 }],
          transitionProperty: 'transform',
          transitionDuration: pressed ? PRESS.inMs : PRESS.outMs,
          transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
        }]}>
          {children as ReactNode}
        </Animated.View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  clip: { position: 'absolute', overflow: 'hidden' },
  center: { alignItems: 'center', justifyContent: 'center' },
  initials: { color: '#FFFFFF', fontWeight: '700' },
  titleRow: {
    position: 'absolute', left: SHEET.pad, right: SHEET.closeRight, top: SHEET.titleTop,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start',
  },
  title: { color: COLORS.sheetInk, fontSize: SHEET.titleSize, lineHeight: SHEET.titleLine, fontWeight: '700', letterSpacing: -0.8 },
  close: { width: SHEET.close, height: SHEET.close, alignItems: 'center', justifyContent: 'center' },
  favs: { position: 'absolute', left: SHEET.favPad, right: SHEET.favPad, top: SHEET.favTop, flexDirection: 'row' },
  fav: { flex: 1 },
  favInner: { alignItems: 'center' },
  favName: { marginTop: 10, color: COLORS.sheetInk, fontSize: TYPE.favName, fontWeight: '500' },
  add: {
    width: SHEET.favSize, height: SHEET.favSize, borderRadius: SHEET.favSize / 2,
    borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)',
  },
  section: { position: 'absolute', left: SHEET.pad, top: SHEET.sectionTop },
  sectionText: { color: COLORS.sheetInk, fontSize: TYPE.sheetSection, lineHeight: 26, fontWeight: '700', letterSpacing: -0.3 },
  rows: { position: 'absolute', left: SPACE.lg, right: SPACE.lg, top: SHEET.rowsTop },
  row: {
    height: SHEET.rowHeight, flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: SHEET.rowAvatarX - 16 - SHEET.contactSize / 2,
  },
  rowFill: { ...StyleSheet.absoluteFill, top: 6, bottom: 6, borderRadius: SHEET.rowHeight / 2, backgroundColor: COLORS.sheetHover },
  contact: { flex: 1, marginLeft: 14, color: COLORS.sheetInk, fontSize: TYPE.contact - 1, fontWeight: '600' },
  next: {
    position: 'absolute', top: SHEET.nextTop, width: SHEET.nextWidth, height: SHEET.nextHeight, borderRadius: SHEET.nextHeight / 2,
    backgroundColor: COLORS.next, alignItems: 'center', justifyContent: 'center',
  },
  nextText: { color: COLORS.sheetInk, fontSize: 15, fontWeight: '600' },
  ring: { position: 'absolute' },
  spark: { position: 'absolute', width: 6, height: 6, borderRadius: 3, backgroundColor: COLORS.ring },
  badge: {
    position: 'absolute', width: 22, height: 22, borderRadius: 11, backgroundColor: COLORS.success,
    alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: COLORS.island,
  },
});
