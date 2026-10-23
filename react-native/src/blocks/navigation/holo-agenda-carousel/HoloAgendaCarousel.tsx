import { Canvas, RoundedRect, Shader } from '@shopify/react-native-skia';
import { useCallback, useEffect, useRef } from 'react';
import { Platform, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion, useAnimatedStyle, useDerivedValue, useSharedValue,
  withTiming
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AgendaButton } from './AgendaButton';
import { HoloBackdrop } from './HoloBackdrop';
import { HoloCard } from './HoloCard';
import { HoloPager } from './HoloPager';
import { holoEffect, holoUniforms } from './holo-shader';
import { D, holoLayout, sessions, smooth, T, tone, wrap } from './holo-theme';
import { type HoloValues, useHoloPager } from './use-holo-pager';
const mono = Platform.OS === 'ios' ? 'Menlo' : 'monospace';
type Layout = ReturnType<typeof holoLayout>;

function Details({ index, layout, values, selected, onReady }: {
  index: number;
  layout: Layout;
  values: HoloValues;
  selected: boolean;
  onReady: () => void;
}) {
  const { k } = layout;
  const session = sessions[index];
  const { pos } = values;

  const style = useAnimatedStyle(() => {
    const d = wrap(index - pos.get());
    return { opacity: 1 - smooth(0.12, 0.5, Math.abs(d)), transform: [{ translateX: d * 28 * k }] };
  });

  const uniforms = useDerivedValue(() => holoUniforms(index, [D.speakerSize * k, D.speakerSize
    * k], D.speakerSize * k / 2, 0, wrap(index - pos.get())));
  const description = `${session.overline}. ${session.title}. ${session.body} `
    + `${session.name}, ${session.role}.`;
  return <Animated.View pointerEvents="none" accessible={selected}
    accessibilityElementsHidden={!selected} importantForAccessibility={selected ? 'yes'
      : 'no-hide-descendants'}
    accessibilityLabel={description} style={[StyleSheet.absoluteFill, style]}>
    <Text numberOfLines={1} style={[styles.text, {
      top: layout.overline, height: D.overlineHeight * k,
      fontFamily: mono, fontSize: D.type.overline * k, lineHeight: D.overlineHeight * k,
      letterSpacing: D.tracking.overline * k, color: tone.dim
    }]}>{session.overline}</Text>
    <Text numberOfLines={1} style={[styles.text, {
      top: layout.title, height: D.titleHeight * k,
      fontSize: D.type.title * k, lineHeight: D.titleHeight * k,
      letterSpacing: D.tracking.title * k, fontWeight: '700'
    }]}>{session.title}</Text>
    <Text numberOfLines={2} style={[styles.text, {
      top: layout.body, height: D.bodyHeight * k, left: (D.width - D.bodyWidth) * k / 2,
      width: D.bodyWidth * k, fontSize: D.type.body * k, lineHeight: D.bodyHeight * k / 2, color: tone.dim
    }]}>{session.body}</Text>
    <View style={{
      position: 'absolute', top: layout.speaker, width: D.width * k, height: D.speakerSize * k,
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: D.speakerGap * k
    }}>
      <View onLayout={onReady} style={{ width: D.speakerSize * k, height: D.speakerSize * k }}>
        <Canvas accessible={false} style={StyleSheet.absoluteFill}>
          <RoundedRect x={0} y={0} width={D.speakerSize * k} height={D.speakerSize * k}
            r={D.speakerSize * k / 2}>
            <Shader source={holoEffect!} uniforms={uniforms} />
          </RoundedRect>
        </Canvas>
        <Text style={{
          fontSize: D.type.initials * k, lineHeight: D.speakerSize * k,
          fontWeight: '700', color: tone.label, textAlign: 'center',
          includeFontPadding: false
        }}>{session.initials}</Text>
      </View>
      <View><Text style={{
        fontSize: D.type.name * k, lineHeight: 20 * k, fontWeight: '600',
        color: tone.ink
      }}>{session.name}</Text>
        <Text style={{
          fontSize: D.type.role * k, lineHeight: 16 * k,
          color: tone.dim
        }}>{session.role}</Text></View>
    </View>
  </Animated.View>;
}

export function HoloAgendaCarousel({ autoPlay = false }: {
  autoPlay?: boolean;
}) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const layout = holoLayout(width, height, insets.top, insets.bottom);
  const { k } = layout;
  const { values, gesture, observer, selection, reduced, accessibilityMove, toggleAgenda,
    beginTouch, endTouch } = useHoloPager(autoPlay, k);
  const { active } = values;
  const visible = useSharedValue(0);
  const canvases = useRef(new Set<string>());
  const raf = useRef<number | null>(null);

  const ready = useCallback((id: string) => {
    if (canvases.current.has(id))
      return;
    canvases.current.add(id);
    // Four cards, backdrop, pager, four portraits, button rim and icon. Give Skia two paint frames.
    if (canvases.current.size === D.canvasCount)
      raf.current = requestAnimationFrame(() => {
        raf.current = requestAnimationFrame(() => {
          visible.set(withTiming(1, { duration: T.reveal, reduceMotion: ReduceMotion.Never }));
          active.set(true);
        });
      });
  }, [active, visible]);

  useEffect(() => () => {
    if (raf.current !== null)
      cancelAnimationFrame(raf.current);
    active.set(false);
  }, [active]);

  const reveal = useAnimatedStyle(() => ({ opacity: visible.get() }));
  const stageCentre = layout.centre - layout.stage;
  return <GestureDetector gesture={observer}>
    <View collapsable={false} style={styles.root}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, reveal]}>
        <HoloBackdrop k={k} width={width} height={layout.top + layout.title}
          centreY={layout.top + layout.centre} values={values} onReady={() => ready('backdrop')} />
      </Animated.View>
      <Animated.View style={[{
        position: 'absolute', left: layout.left, top: layout.top,
        width: layout.width, height: D.height * k
      }, reveal]}>
        <View style={{
          position: 'absolute', top: layout.header, left: D.inset * k, right: D.inset * k,
          height: D.headerHeight * k, flexDirection: 'row', alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <Text style={{
            fontSize: D.type.header * k, fontWeight: '700',
            letterSpacing: D.tracking.header * k, color: '#FFFFFFEB'
          }}>IRIDIA SUMMIT</Text>
          <Text style={{ fontSize: D.type.date * k, fontFamily: mono, color: tone.dim }}>OCT 14–15</Text>
        </View>
        <GestureDetector gesture={gesture}>
          <View collapsable={false} accessible accessibilityRole="adjustable"
            accessibilityLabel="Agenda"
            accessibilityValue={{
              text: `${sessions[selection.index].title}, ${selection.index + 1} of 4`,
              min: 1, max: 4, now: selection.index + 1
            }} accessibilityActions={[{
              name: 'increment',
              label: 'Next session'
            }, { name: 'decrement', label: 'Previous session' }]}
            onAccessibilityAction={(event) => accessibilityMove(event.nativeEvent.actionName
              === 'increment' ? 1 : -1)} style={{
                position: 'absolute', top: layout.stage,
                width: layout.width, height: D.stageHeight * k
              }}>
            {sessions.map((session, index) => <HoloCard key={session.label} index={index} k={k}
              centreY={stageCentre} values={values} onReady={() => ready(`card-${index}`)} />)}
          </View>
        </GestureDetector>
        <HoloPager k={k} top={layout.pager} values={values} onReady={() => ready('pager')} />
        {sessions.map((session, index) => <Details key={session.label} index={index}
          layout={layout} values={values} selected={selection.index === index} onReady={() =>
            ready(`speaker-${index}`)} />)}
        <AgendaButton k={k} top={layout.button} values={values} reduced={reduced}
          selected={selection.selected} onPress={toggleAgenda} onTouchStart={beginTouch}
          onTouchEnd={endTouch} onReady={(part) => ready(`button-${part}`)} />
      </Animated.View>
    </View>
  </GestureDetector>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tone.page },
  text: {
    position: 'absolute', left: 0, right: 0, textAlign: 'center', color: tone.ink,
    includeFontPadding: false
  }
});
