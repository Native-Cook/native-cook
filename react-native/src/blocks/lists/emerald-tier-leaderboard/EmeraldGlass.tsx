import { useCallback, useEffect, useState } from 'react';
import { LinearGradient } from 'expo-linear-gradient';
import { AccessibilityInfo, AppState, Platform, StyleSheet, View, type LayoutChangeEvent } from 'react-native';

import { EmeraldLightSurface } from './EmeraldLightSurface';

type GlassKind = 'green' | 'smoke' | 'selected' | 'row';
const MATERIAL = {
  green: { solid: '#0A1B18', light: .32 },
  smoke: { solid: '#081513', light: .28 },
  selected: { solid: '#153B2D', light: 0 },
  row: { solid: '#0A1715', light: .13 },
};
/** Both preferences call for an opaque, clearly separated material. */
export function useEmeraldSolidMaterial() {
  const [preference, setPreference] = useState({ transparency: false, contrast: false });
  useEffect(() => {
    let mounted = true;
    let transparencyVersion = 0, contrastVersion = 0;
    const update = (key: 'transparency' | 'contrast', value: boolean) => {
      if (mounted) setPreference(previous => previous[key] === value ? previous : { ...previous, [key]: value });
    };
    const readContrast = Platform.OS === 'ios' ? AccessibilityInfo.isDarkerSystemColorsEnabled
      : Platform.OS === 'android' ? AccessibilityInfo.isHighTextContrastEnabled : () => Promise.resolve(false);
    const refresh = () => {
      const transparencyRequest = ++transparencyVersion, contrastRequest = ++contrastVersion;
      void AccessibilityInfo.isReduceTransparencyEnabled().then(value => {
        if (transparencyRequest === transparencyVersion) update('transparency', value);
      }).catch(() => {});
      void readContrast().then(value => {
        if (contrastRequest === contrastVersion) update('contrast', value);
      }).catch(() => {});
    };
    const transparency = AccessibilityInfo.addEventListener('reduceTransparencyChanged', value => {
      transparencyVersion += 1; update('transparency', value);
    });
    const contrast = Platform.OS === 'ios' || Platform.OS === 'android'
      ? AccessibilityInfo.addEventListener(Platform.OS === 'ios' ? 'darkerSystemColorsChanged' : 'highTextContrastChanged', value => {
        contrastVersion += 1; update('contrast', value);
      }) : undefined;
    const lifecycle = AppState.addEventListener('change', state => { if (state === 'active') refresh(); });
    refresh();
    return () => { mounted = false; transparency.remove(); contrast?.remove(); lifecycle.remove(); };
  }, []);
  return preference.transparency || preference.contrast;
}
/** Layout is measured once; the bounded surface never draws or filters its labels. */
export function EmeraldGlass({ kind, radius, solid = false }: { kind: GlassKind; radius: number; solid?: boolean }) {
  const material = MATERIAL[kind];
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = useCallback(({ nativeEvent: { layout } }: LayoutChangeEvent) => {
    setSize(previous => previous.width === layout.width && previous.height === layout.height ? previous : { width: layout.width, height: layout.height });
  }, []);
  // A selected control is an inset on one glass plane, not another luminous glass layer.
  if (kind === 'selected') return <View pointerEvents="none" accessible={false}
    style={[StyleSheet.absoluteFill, { borderRadius: radius, overflow: 'hidden', backgroundColor: solid ? material.solid : '#67DFA51A',
      borderWidth: StyleSheet.hairlineWidth, borderColor: solid ? '#D6FFEA80' : '#BAF7DB3D', borderTopColor: '#D6FFEA66' }]}>
    {!solid ? <LinearGradient colors={['#D9FFEF0A', '#51D99F0D', '#073C2C26']} style={StyleSheet.absoluteFill} /> : null}
  </View>;
  return <View pointerEvents="none" accessible={false} onLayout={onLayout}
    style={[StyleSheet.absoluteFill, { borderRadius: radius, overflow: 'hidden', backgroundColor: material.solid,
      borderWidth: solid ? StyleSheet.hairlineWidth : 0, borderColor: '#D6FFEA70' }]}>
    {!solid && size.width > 0 && size.height > 0 ? <EmeraldLightSurface {...size} radius={radius} light={material.light} /> : null}
  </View>;
}
