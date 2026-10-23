import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { CONTACTS, FAVOURITES } from './data';
import { type Box, FOLD_MS, OPEN_MS } from './geometry';
import { CloseGlyph, PlusGlyph } from './glyphs';
import { COLOR, FAMILY, FONT, SHEET, SHEET_GROW } from './theme';

type Props = {
  width: number; height: number; oy: number;
  box: SharedValue<Box>; sheet: SharedValue<number>; fade: SharedValue<number>; closing: SharedValue<number>; reduced: boolean;
  onClose: () => void;
};


const PRESS_RETENTION = { top: 16, bottom: 16, left: 16, right: 16 };
const PRESS_SCALE = 0.97;

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/**
 * The sheet's content is laid out at its final place on the screen and revealed through a window
 * that follows the island's bloom, so it never scales or reflows: the black shape uncovers it.
 */
export function SendSheet({ width, height, oy, box, sheet, fade, closing, reduced, onClose }: Props) {
  // The window is itself black: the Skia goo only adds the neck that joins it to the island, so a
  // frame of lag between the two layers can never show the card through the sheet.
  const clip = useAnimatedStyle(() => {
    const b = box.get();
    return { left: b.x, top: b.y, width: b.w, height: b.h, borderRadius: Math.min(b.r, Math.min(b.w, b.h) / 2 - 1), opacity: reduced ? fade.get() : 1 };
  });
  // As in the pin, the content grows out of the island with the bloom: scaled about the island's centre,
  // fading in once the bloom is a third grown.
  const inner = useAnimatedStyle(() => {
    const b = box.get();
    if (reduced) return { left: -b.x, top: -b.y, opacity: 1, transform: [{ translateY: 0 }, { scale: 1 }] };
    const p = sheet.get();
    let scale: number;
    let o: number;
    let originY: number = SHEET_GROW.originY;
    if (closing.get() > 0.5) {
      const t = (1 - p) * FOLD_MS;
      const c = SHEET_GROW.close;
      scale = c.scale[c.scale.length - 1];
      for (let i = 1; i < c.t.length; i++) {
        if (t <= c.t[i]) {
          scale = c.scale[i - 1] + (c.scale[i] - c.scale[i - 1]) * ((t - c.t[i - 1]) / (c.t[i] - c.t[i - 1]));
          break;
        }
      }
      originY = c.originY;
      o = 1 - clamp01((t - c.fadeFrom) / (c.fadeTo - c.fadeFrom));
    } else {
      const t = p * OPEN_MS;
      const g = SHEET_GROW.open;
      const u = clamp01((t - g.scaleStart) / (g.scaleEnd - g.scaleStart));
      scale = g.scaleFrom + (1 - g.scaleFrom) * (1 - (1 - u) * (1 - u));
      o = clamp01((t - g.fadeFrom) / (g.fadeTo - g.fadeFrom));
    }
    const ty = (oy + originY - height / 2) * (1 - scale);
    return { left: -b.x, top: -b.y, opacity: o, transform: [{ translateY: ty }, { scale }] };
  });
  const left = SHEET.titleX;
  return (
    <Animated.View style={[styles.clip, clip]}>
      <Animated.View style={[{ position: 'absolute', width, height }, inner]}>
        <Text style={[styles.title, { left, top: oy + SHEET.titleY }]} accessibilityRole="header">
          <Text style={styles.titleSoft}>{'Send\n'}</Text>
          <Text style={styles.titleStrong}>Money To</Text>
        </Text>
        <Pressable onPress={onClose} accessibilityRole="button" accessibilityLabel="Close"
          hitSlop={8} pressRetentionOffset={PRESS_RETENTION} style={({ pressed }) => [styles.close, { left: width - SHEET.closeX - 22, top: oy + SHEET.closeY - 22, transform: [{ scale: pressed ? PRESS_SCALE : 1 }] }]}>
          <CloseGlyph color={COLOR.white} size={SHEET.closeGlyph} stroke={1.7} />
        </Pressable>
        {FAVOURITES.map((f, i) => (
          <Pressable key={f.name} pressRetentionOffset={PRESS_RETENTION} accessibilityRole="button" accessibilityLabel={`Send to ${f.name}`} 
            style={[styles.favourite, { left: SHEET.avatarX - 40 + i * SHEET.avatarStep, top: oy + SHEET.avatarsY - SHEET.avatar / 2 }]}>
            <Image source={f.photo} style={styles.avatar} contentFit="cover" />
            <Text style={styles.label}>{f.name}</Text>
          </Pressable>
        ))}
        <Pressable pressRetentionOffset={PRESS_RETENTION} accessibilityRole="button" accessibilityLabel="Add new recipient"
          style={({ pressed }) => [styles.favourite, { left: SHEET.addNewX - 40, top: oy + SHEET.avatarsY - SHEET.avatar / 2, transform: [{ scale: pressed ? PRESS_SCALE : 1 }] }]}>
          <View style={[styles.avatar, styles.addNew]}><PlusGlyph color={COLOR.inkSoft} size={18} stroke={1.3} /></View>
          <Text style={styles.label}>Add New</Text>
        </Pressable>
        <Text style={[styles.contactsTitle, { left, top: oy + SHEET.contactsTitleY }]} accessibilityRole="header">
          <Text style={styles.titleSoft}>{'Your\n'}</Text>
          <Text style={styles.titleStrong}>Contacts</Text>
        </Text>
        {CONTACTS.map((c, i) => (
          <Pressable key={c.name} pressRetentionOffset={PRESS_RETENTION} accessibilityRole="button" accessibilityLabel={`Send to ${c.name}`}
            style={({ pressed }) => [styles.contact, { left: SHEET.contactX - SHEET.avatar / 2, top: oy + SHEET.contactsTop + i * SHEET.contactPitch - 22, transform: [{ scale: pressed ? PRESS_SCALE : 1 }] }]}>
            <Image source={c.face} style={styles.face} contentFit="contain" />
            <Text style={styles.contactName}>{c.name}</Text>
          </Pressable>
        ))}
        <Pressable pressRetentionOffset={PRESS_RETENTION} accessibilityRole="button" accessibilityLabel="Next"
          style={({ pressed }) => [styles.next, {
            left: SHEET.buttonX, width: SHEET.buttonWidth,
            top: oy + SHEET.buttonTop,
            transform: [{ scale: pressed ? PRESS_SCALE : 1 }],
          }]}>
          <Text style={styles.nextText}>Next</Text>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  clip: { position: 'absolute', overflow: 'hidden', backgroundColor: COLOR.black },
  title: { position: 'absolute', fontFamily: FAMILY.semibold, fontSize: FONT.sheetTitle, lineHeight: FONT.sheetTitleLine, letterSpacing: -0.6 },
  titleSoft: { color: COLOR.sheetMuted, fontFamily: FAMILY.medium },
  titleStrong: { color: COLOR.white, fontFamily: FAMILY.semibold },
  close: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  favourite: { position: 'absolute', width: 80, alignItems: 'center' },
  avatar: { width: SHEET.avatar, height: SHEET.avatar, borderRadius: SHEET.avatar / 2 },
  addNew: { backgroundColor: COLOR.addNew, alignItems: 'center', justifyContent: 'center' },
  label: { marginTop: 0, fontFamily: FAMILY.regular, fontSize: FONT.label, lineHeight: 17, color: COLOR.white },
  contactsTitle: { position: 'absolute', fontFamily: FAMILY.semibold, fontSize: FONT.contactsTitle, lineHeight: FONT.contactsLine },
  contact: { position: 'absolute', height: 44, flexDirection: 'row', alignItems: 'center', paddingRight: 24 },
  face: { width: SHEET.avatar, height: SHEET.avatar },
  contactName: { marginLeft: SHEET.nameGap, fontFamily: FAMILY.medium, fontSize: FONT.contact, lineHeight: 20, color: COLOR.white },
  next: {
    position: 'absolute', height: SHEET.buttonHeight, borderRadius: SHEET.buttonHeight / 2, backgroundColor: COLOR.blue,
    alignItems: 'center', justifyContent: 'center',
  },
  nextText: { fontFamily: FAMILY.medium, fontSize: 14, lineHeight: 20, color: COLOR.white },
});
