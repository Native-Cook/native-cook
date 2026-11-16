// <VacuumSeal> — wrap any view in clear film that drapes, vacuum-seals under your finger and dents when pressed: a
// physical on/off state in one shader, with no solver on the phone. The wrapped view is snapshotted with Skia's
// makeImageFromView (retaken when `contentKey` or the size changes) and drawn under the film; until the first
// snapshot exists, and with no Skia, the children simply show.
//
//   <VacuumSeal sealed={frozen} onSealedChange={setFrozen} radius={16}><BankCard /></VacuumSeal>
//
// The film's geometry is the card's width / 354 × the reference numbers (lip 10, ears 14, sheet 34 past the card),
// so a small card gets proportionally small creases. `progress` is the one value everything follows.
import { useEffect, useMemo, useRef, useState } from 'react';
import { type StyleProp, StyleSheet, View, type ViewStyle } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, SensorType, useAnimatedSensor, useFrameCallback } from 'react-native-reanimated';
import { makeImageFromView, type SkImage, useImage } from '@shopify/react-native-skia';

import { CvsFilm, FILM_AVAILABLE } from './CvsFilm';
import { type SealOptions, useVacuumSeal, type VacuumSealHandle } from './cvs-controller';
import { comp, ref } from './cvs-theme';
import { hexToRgb } from './cvs-uniforms';

export type VacuumSealProps = {
  children: React.ReactNode; // the thing to wrap (snapshotted with makeImageFromView)
  contentKey?: string | number; // change it to re-snapshot (fonts loaded, new data)
  sealed?: boolean; // controlled
  defaultSealed?: boolean; // uncontrolled, true
  onSealedChange?: (sealed: boolean) => void;
  progress?: SharedValue<number>; // 0 no film · 0.3 draped · 1 sealed; read it, or drive it while no loop plays
  gesture?: 'squeegee' | 'none'; // finger on the film
  holdMs?: number; // 1250, for the hold on `seal.hold`
  commitAt?: number; // 0.62: a release past this seals, before it lets go
  radius?: number; // corner radius of the wrapped view, pt (16)
  lip?: number; // how far the sealed skin reaches past the card, at a 354 pt card (6)
  ears?: number; // the corner ears' length, at a 354 pt card (14)
  filmTint?: string; // '#C9B8FF'
  light?: number | 'device'; // key azimuth in degrees clockwise from 12 o'clock (315), or the gravity sensor
  creaseSeed?: number; // 0, 1 or 2: which crease layout; left out, every re-seal picks the next one
  haptics?: boolean; // true
  sound?: boolean; // false
  autoPlay?: boolean; // the demo loop
  accessibilityLabel?: string;
  // not in the contract above:
  seal?: VacuumSealHandle; // a controller from useVacuumSeal(), when the screen around the film reads its state
  style?: StyleProp<ViewStyle>;
  onReady?: () => void; // the first content snapshot is available
};

const REF_W = comp.card.w;
const CREASES = require('./assets/cvs-creases.png');

/** the gravity sensor tilts the key light up to ±0.35 rad (120 ms one-pole). Mounted only for light="device". */
function DeviceTilt({ sink }: { sink: SharedValue<number> }) {
  const sensor = useAnimatedSensor(SensorType.GRAVITY, { interval: 'auto' });
  useFrameCallback((frame) => {
    'worklet';
    const g = sensor.sensor.get();
    const target = Math.max(-0.35, Math.min(0.35, g.x * 0.35));
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    sink.set(sink.get() + (target - sink.get()) * (1 - Math.exp(-dt / 0.12)));
  });
  return null;
}

export function VacuumSeal(props: VacuumSealProps) {
  const {
    children,
    contentKey,
    radius = ref.radius.card,
    lip = comp.film.lip,
    ears = comp.film.ear,
    filmTint = ref.color.film,
    light = ref.light.az,
    accessibilityLabel = 'Sealed content',
    style,
    onReady,
  } = props;
  const options: SealOptions = {
    autoPlay: props.autoPlay,
    progress: props.progress,
    sealed: props.sealed,
    defaultSealed: props.defaultSealed,
    onSealedChange: props.onSealedChange,
    gesture: props.gesture,
    holdMs: props.holdMs,
    commitAt: props.commitAt,
    creaseSeed: props.creaseSeed,
    haptics: props.haptics,
    sound: props.sound,
  };
  // one controller per film: the host's (props.seal) or this component's own
  const own = useVacuumSeal(props.seal ? { disabled: true } : options);
  const seal = props.seal ?? own;
  const { film, geom, squeegee } = seal;

  const contentRef = useRef<View>(null);
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  // the snapshot of the children, and the size / contentKey it was taken for (the children show until they match)
  const [snapState, setSnapState] = useState<{ key: string; img: SkImage } | null>(null);
  const key = size ? `${size.w}x${size.h}:${contentKey ?? ''}` : '';
  const snap = snapState?.img ?? null;
  const hidden = snapState !== null && snapState.key === key;
  const crease = useImage(CREASES); // null until decoded: the film draws with the shader's fallback creases meanwhile
  const readyOnce = useRef(false);

  // the snapshot: the children show, two frames later it is taken, then they hide (the film draws the card)
  useEffect(() => {
    if (!key) return;
    let cancelled = false;
    let raf2 = 0;
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        makeImageFromView(contentRef)
          .then((img) => {
            if (cancelled || !img) return;
            setSnapState({ key, img });
            if (!readyOnce.current) {
              readyOnce.current = true;
              onReady?.();
            }
          })
          .catch(() => {});
      });
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
    // onReady is called at most once; a new function must not retake the snapshot
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const S = size ? size.w / REF_W : 1;
  const hw = size ? size.w / S / 2 : REF_W / 2;
  const hh = size ? size.h / S / 2 : comp.card.h / 2;
  useEffect(() => {
    geom.set({ S, hw, hh });
  }, [geom, S, hw, hh]);

  const pad = comp.canvasPad;
  const filmGeom = useMemo(
    () => ({
      S,
      cx: pad.l + hw,
      cy: pad.t + hh,
      hw,
      hh,
      radius: radius / S,
      lip,
      ear: ears,
      mapK: snap && size ? snap.width() / (2 * hw) : 3,
    }),
    [S, hw, hh, radius, lip, ears, snap, size, pad.l, pad.t],
  );
  const tint = useMemo(() => hexToRgb(filmTint), [filmTint]);
  const azimuth = ((typeof light === 'number' ? light : ref.light.az) * Math.PI) / 180;

  const showFilm = !!(FILM_AVAILABLE && size && snap);
  const cw = (pad.l + 2 * hw + pad.r) * S;
  const ch = (pad.t + 2 * hh + pad.b) * S;
  return (
    <View style={[styles.root, style]} collapsable={false}>
      <View
        ref={contentRef}
        collapsable={false}
        style={{ opacity: showFilm && hidden ? 0 : 1 }}
        onLayout={(e) => {
          const w = Math.round(e.nativeEvent.layout.width * 100) / 100;
          const h = Math.round(e.nativeEvent.layout.height * 100) / 100;
          if (w <= 0 || h <= 0) return;
          setSize((prev) => (prev && prev.w === w && prev.h === h ? prev : { w, h }));
        }}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {children}
      </View>
      {showFilm && snap && (
        <View pointerEvents="none" style={{ position: 'absolute', left: -pad.l * S, top: -pad.t * S, width: cw, height: ch }}>
          <CvsFilm geom={filmGeom} width={cw} height={ch} map={snap} crease={crease} film={film} azimuth={azimuth} tint={tint} />
        </View>
      )}
      {light === 'device' && !(props.autoPlay ?? seal.autoPlay) && !seal.reduce && <DeviceTilt sink={film.tilt} />}
      <GestureDetector gesture={squeegee}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole={props.gesture === 'none' ? 'image' : 'switch'}
          accessibilityLabel={accessibilityLabel}
          accessibilityState={props.gesture === 'none' ? undefined : { checked: seal.sealed }}
          accessibilityActions={props.gesture === 'none' ? undefined : [{ name: 'activate' }]}
          onAccessibilityAction={() => {
            if (props.gesture !== 'none') seal.toggle(!seal.sealed);
          }}
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { zIndex: 1 },
});
