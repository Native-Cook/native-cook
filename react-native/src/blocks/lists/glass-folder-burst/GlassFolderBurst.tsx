import { Canvas, useCanvasRef, useFont, useImage } from '@shopify/react-native-skia';
import { useEffect, useMemo } from 'react';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, { useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BurstBackground } from './BurstBackground';
import { BurstHeader } from './BurstHeader';
import { BurstRows, COVERS } from './BurstRows';
import { burstLayout, type BurstLayout } from './burst-layout';
import { FolderBack, FolderFront, FolderOrb } from './GlassFolder';
import { PLAYLIST, TRACKS } from './tracks';
import { useBurstMotion } from './use-burst-motion';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
export type GlassFolderBurstProps = { autoPlay?: boolean; glass?: 'backdrop' | 'analytic' };

function BurstScene({ layout, autoPlay = false, glass = 'backdrop' }: GlassFolderBurstProps & { layout: BurstLayout }) {
  const reduced = useReducedMotion();
  const title = useFont(require('./assets/ArchivoExpanded-Black.ttf'), 20);
  const subtitle = useFont(require('./assets/ArchivoExpanded-Regular.ttf'), 12);
  const rowTitle = useFont(require('./assets/ArchivoExpanded-Regular.ttf'), 14);
  const rowSmall = useFont(require('./assets/ArchivoExpanded-Regular.ttf'), 11);
  const covers = useImage(COVERS);
  const canvas = useCanvasRef();
  const motion = useBurstMotion(layout, reduced, autoPlay);
  const { ready, opacity, dock, folder, send, accessible } = motion;
  const { cx, cy, scale } = folder;
  const loaded = !!(title && subtitle && rowTitle && rowSmall && covers);
  // A completed snapshot acknowledges that the mounted tree has drawn. No Canvas onLayout.
  useEffect(() => {
    if (!loaded) return;
    let cancelled = false;
    let frame = 0;
    frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => {
        void canvas.current?.makeImageSnapshotAsync().then(image => {
          image.dispose();
          if (!cancelled) ready.set(true);
        });
      });
    });
    return () => { cancelled = true; cancelAnimationFrame(frame); };
  }, [canvas, loaded, ready]);
  const sceneStyle = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  const folderStyle = useAnimatedStyle(() => ({ position: 'absolute', left: cx.get() - 72 * scale.get() - 12,
    top: cy.get() - 52 * scale.get() - 12, width: 144 * scale.get() + 24, height: 104 * scale.get() + 24 }));
  const headerStyle = useAnimatedStyle(() => ({ position: 'absolute', left: 24 + 52 * dock.get(),
    top: layout.label.title.y + (layout.header.title.y - layout.label.title.y) * dock.get(),
    width: layout.width - 100, height: 24 }));
  return <View style={styles.screen} onTouchStart={() => send('touchStart')} onTouchEnd={() => send('touchEnd')} onTouchCancel={() => send('touchEnd')}>
    <Animated.View style={[StyleSheet.absoluteFill, sceneStyle]} pointerEvents="none">
      <Canvas ref={canvas} style={{ width: layout.width, height: layout.height }}>
        {loaded && <>
          <BurstBackground width={layout.width} height={layout.height} drive={folder} orb={motion.orb}
            bloomX={motion.bloomX} bloomY={motion.bloomY} bloom={motion.bloom} clock={motion.clock} />
          <FolderBack drive={folder} />
          {/* Returning disc stays behind the departing stream. */}
          <FolderOrb orb={motion.orb} clock={motion.clock} />
          <BurstRows layout={layout} rows={motion.rows} folder={folder} selected={motion.selected}
            pressed={motion.pressed} pressAmount={motion.pressAmount} selection={motion.selection} clock={motion.clock}
            fonts={{ title: rowTitle, artist: rowSmall, time: rowSmall }} covers={covers} />
          <FolderFront drive={folder} orb={motion.orb} width={layout.width} height={layout.height} refraction={glass} clock={motion.clock} />
          <BurstHeader layout={layout} title={title} subtitle={subtitle} dock={dock} selection={motion.selection} />
        </>}
      </Canvas>
    </Animated.View>
    {loaded && <>
      <Animated.View accessible accessibilityRole="header" accessibilityLabel={PLAYLIST} style={headerStyle} pointerEvents="none" />
      {TRACKS.slice(0, layout.n).map((track, index) => {
        const slot = layout.slots[index];
        return (accessible.landed & (1 << index)) !== 0 && <Pressable key={track.title}
          accessibilityRole="button" accessibilityLabel={`${track.title}, ${track.artist}, ${Math.floor(track.seconds / 60)} minutes ${track.seconds % 60} seconds`}
          accessibilityState={{ selected: accessible.selected === index }}
          onPressIn={() => send('rowDown', index)} onPressOut={() => send('rowUp', index)} onPress={() => send('select', index)}
          style={{ position: 'absolute', left: slot.x, top: slot.y, width: slot.w, height: slot.h }} />;
      })}
      <AnimatedPressable accessibilityRole="button" accessibilityLabel={`${PLAYLIST}, ${layout.n} tracks`}
        accessibilityState={{ expanded: accessible.expanded }}
        accessibilityHint={accessible.expanded ? 'Puts the tracks away' : 'Shows the tracks'}
        style={folderStyle} onPressIn={() => send('folderDown')} onPressOut={() => send('folderUp')} onPress={() => send('toggle')} />
    </>}
  </View>;
}

export function GlassFolderBurst(props: GlassFolderBurstProps) {
  const { width, height } = useWindowDimensions();
  const { top, bottom } = useSafeAreaInsets();
  const layout = useMemo(() => burstLayout(width, height, top, bottom), [width, height, top, bottom]);
  return <BurstScene key={`${width}:${height}:${top}:${bottom}`} {...props} layout={layout} />;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#000000' } });
