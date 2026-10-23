import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';

import { APP_NAME, PROFILES } from './content';
import { EASE_OUT, PRESS_MS, PRESS_SCALE } from './motion';
import { COLORS, FONT, PICKER } from './theme';

function PickerBackdrop({ width, height }: { width: number; height: number }) {
  // Sampled from the pin: violet sky, a teal glow on the left edge, magenta glows on the right.
  return (
    <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
      <Defs>
        <LinearGradient id="base" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={COLORS.pickerTop} />
          <Stop offset="1" stopColor={COLORS.pickerBottom} />
        </LinearGradient>
        <RadialGradient id="teal" cx={0} cy={height * 0.5} r={width * 0.62} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={COLORS.pickerTeal} stopOpacity={1} />
          <Stop offset="1" stopColor={COLORS.pickerTeal} stopOpacity={0} />
        </RadialGradient>
        <RadialGradient id="magenta" cx={width * 0.74} cy={height * 0.31} r={width * 0.6} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={COLORS.pickerMagenta} stopOpacity={0.95} />
          <Stop offset="1" stopColor={COLORS.pickerMagenta} stopOpacity={0} />
        </RadialGradient>
        <RadialGradient id="plum" cx={width * 0.72} cy={height * 0.76} r={width * 0.5} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={COLORS.pickerPlum} stopOpacity={0.9} />
          <Stop offset="1" stopColor={COLORS.pickerPlum} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Rect x={0} y={0} width={width} height={height} fill="url(#base)" />
      <Rect x={0} y={0} width={width} height={height} fill="url(#teal)" />
      <Rect x={0} y={0} width={width} height={height} fill="url(#magenta)" />
      <Rect x={0} y={0} width={width} height={height} fill="url(#plum)" />
    </Svg>
  );
}

function ProfileButton({ index, row, x, y, autoPress, onPick, children, label }: {
  index: number; row: number; x: number; y: number; autoPress: SharedValue<number>; label: string;
  onPick: (index: number) => void; children: React.ReactNode;
}) {
  const press = useSharedValue(0);
  const style = useAnimatedStyle(() => {
    const auto = index === 0 ? autoPress.get() : 0;
    return { transform: [{ scale: 1 - (1 - PRESS_SCALE) * Math.max(auto, press.get()) }] };
  });
  const size = PICKER.avatar;
  return (
    <Pressable
      accessibilityRole="button" accessibilityLabel={label} pressRetentionOffset={16}
      onPressIn={() => press.set(withTiming(1, { duration: PRESS_MS, easing: EASE_OUT }))}
      onPressOut={() => press.set(withTiming(0, { duration: PRESS_MS, easing: EASE_OUT }))}
      onPress={() => onPick(index)}
      style={[styles.profile, { left: x - size / 2, top: y - size / 2, width: size }]}
    >
      <Animated.View style={[styles.circle, { width: size, height: size, borderRadius: size / 2 }, style]}>{children}</Animated.View>
      <Text style={[styles.name, { top: size / 2 + PICKER.nameGap[row] - 12, transform: [{ translateX: PICKER.nameDx }] }]}>{label}</Text>
    </Pressable>
  );
}

export function ProfilePicker({ width, height, autoPress, onPick }: {
  width: number; height: number; autoPress: SharedValue<number>; onPick: (index: number) => void;
}) {
  const cells = [...PROFILES.map((p) => p.name), 'New Profile'];
  return (
    <View style={StyleSheet.absoluteFill}>
      <PickerBackdrop width={width} height={height} />
      <Text style={[styles.edit, { top: PICKER.editY - 8, right: PICKER.editRight }]} accessibilityRole="button">Edit Profile</Text>
      <Text style={[styles.title, { top: PICKER.titleY - 20, width, transform: [{ translateX: PICKER.titleDx }] }]} accessibilityRole="header">Who’s Watching?</Text>
      {cells.map((name, i) => {
        const x = PICKER.colX[i % 2];
        const y = PICKER.rowY[Math.floor(i / 2)];
        const profile = PROFILES[i];
        return (
          <ProfileButton key={name} index={i} row={Math.floor(i / 2)} x={x} y={y} autoPress={autoPress} onPick={onPick} label={name}>
            {profile ? (
              <Image source={profile.image} style={StyleSheet.absoluteFill} contentFit="cover" />
            ) : (
              <View style={[StyleSheet.absoluteFill, styles.plusDisc]}>
                <View style={[styles.bar, { width: PICKER.plusArm, height: PICKER.plusStroke }]} />
                <View style={[styles.bar, { width: PICKER.plusStroke, height: PICKER.plusArm }]} />
              </View>
            )}
          </ProfileButton>
        );
      })}
      <Text style={[styles.footer, { bottom: PICKER.footerFromBottom - 9, width }]}>{`${APP_NAME}, 2023`}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  edit: { position: 'absolute', fontFamily: FONT.regular, fontSize: PICKER.editSize, lineHeight: 16, color: COLORS.ink },
  title: { position: 'absolute', left: 0, textAlign: 'center', fontFamily: FONT.semibold, fontSize: PICKER.titleSize, lineHeight: 40, color: COLORS.ink },
  profile: { position: 'absolute', alignItems: 'center', height: PICKER.avatar + 70 },
  circle: { overflow: 'hidden', backgroundColor: COLORS.plus },
  plusDisc: { alignItems: 'center', justifyContent: 'center', backgroundColor: COLORS.plus },
  bar: { position: 'absolute', backgroundColor: COLORS.ink, borderRadius: 4 },
  name: { position: 'absolute', left: -30, right: -30, textAlign: 'center', fontFamily: FONT.regular, fontSize: PICKER.nameSize, lineHeight: 24, color: COLORS.ink },
  footer: { position: 'absolute', left: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: PICKER.footerSize, lineHeight: 18, color: COLORS.inkSoft },
});
