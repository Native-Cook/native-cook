/**
 * Offline for the flight. A flight card on top and a podcast's new episodes as the flight's timeline: hold an episode
 * and drag, and every episode you pass takes its place on the timeline with the time you would start it, while the
 * route on the card fills toward JFK. Past the flight's length a "Lands" marker drops into the timeline. Download, and
 * the plane flies the route as the episodes land; a boarding pass says what is on the phone.
 *
 * Layers, back to front: page, the rows (timeline), the landing marker, the header with the flight card, the bottom
 * figures and button, the boarding pass.
 */
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { FadeIn, FadeOut, useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BoardingPass } from './BoardingPass';
import { EPISODES, FLIGHT, MINUTES, formatSize } from './episodes';
import { EpisodeRow } from './EpisodeRow';
import { FlightPanel, shortMinutes } from './FlightPanel';
import { Header } from './Header';
import { PHASE } from './model';
import { TIMELINE } from './motion';
import { COLOR, FONT, LAYOUT, SEGMENT, TYPE, makeLayout, type Layout } from './theme';
import { useRangeSelect, type Ui } from './use-range-select';

export type DragMultiSelectProps = { autoPlay?: boolean };

export function DragMultiSelect({ autoPlay = false }: DragMultiSelectProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded, fontsFailed] = useFonts({
    [FONT.light]: require('./assets/Inter-Light.ttf'),
    [FONT.regular]: require('./assets/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/Inter-SemiBold.ttf'),
    [FONT.dots]: require('./assets/Doto-Black.ttf'),
  });
  if (!fontsLoaded && !fontsFailed) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  // The model's geometry and Reduce Motion are fixed per mount; under Reduce Motion there is no demo, a finger still selects.
  return <Screen key={`${width}x${height}-${reduced}`} lay={lay} reduced={reduced} autoPlay={autoPlay && !reduced} />;
}

/** The flight leaves at 9:05 AM; an episode starts when the ones above it on the timeline have played. */
const DEPART = 9 * 60 + 5;

function clock(min: number): [string, string] {
  const h24 = Math.floor(min / 60) % 24;
  const m = min % 60;
  const h = h24 % 12 || 12;
  return [`${h}:${String(m).padStart(2, '0')}`, h24 >= 12 ? 'PM' : 'AM'];
}

/** Each row's start time and colour on the timeline, and the row after which the flight lands (−1: not yet). */
function plan(ui: Ui) {
  const on = new Set([...ui.rows, ...ui.done]);
  const times: ([string, string] | null)[] = [];
  const colors: string[] = [];
  let acc = 0;
  let k = 0;
  let landsAfter = -1;
  for (let i = 0; i < EPISODES.length; i++) {
    if (on.has(i)) {
      times.push(clock(DEPART + acc));
      colors.push(SEGMENT[k % 2]);
      acc += MINUTES[i];
      k++;
      if (landsAfter < 0 && acc >= FLIGHT.minutes) landsAfter = i;
    } else {
      times.push(null);
      colors.push(SEGMENT[k % 2]);
    }
  }
  return { times, colors, landsAfter, minutes: acc };
}

/** The page fades out over the last 24 pt above the bottom figures, so rows never meet their edge hard. */
const FOOT = 24;

function Screen({ lay, reduced, autoPlay }: { lay: Layout; reduced: boolean; autoPlay: boolean }) {
  const {
    listRef, onScroll, scrollProps, gesture, model, scrollY, ui, select, download, activateRow,
    touchStart, touchEnd, setViewport, setContent,
  } = useRangeSelect({ lay, autoPlay, reduced });
  // Closing the pass closes it for this summary of the selection; the next one (the loop's reset) starts fresh.
  const [closedFor, setClosedFor] = useState<Ui | null>(null);

  const contentHeight = lay.listTop + EPISODES.length * LAYOUT.row + LAYOUT.panelHeight + lay.panelBottom + 32;
  const ghost = useAnimatedStyle(() => {
    const m = model.get();
    return {
      opacity: m.ghostO,
      transform: [
        { translateX: m.ghostX - LAYOUT.finger / 2 },
        { translateY: m.ghostY - LAYOUT.finger / 2 },
        { scale: 1 - 0.12 * m.ghostP },
      ],
    };
  });

  const selecting = ui.phase === PHASE.selecting || ui.phase === PHASE.downloading || ui.phase === PHASE.done;
  const selected = new Set(ui.rows);
  const downloadedRows = new Set(ui.done);
  const { times, colors, landsAfter, minutes } = plan(ui);
  // The pass shows once the download has settled, until it is closed; the next loop's reset takes it away too.
  const passOpen = ui.phase === PHASE.ready && closedFor !== ui;

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <GestureDetector gesture={gesture}>
          <View style={styles.root} collapsable={false} onLayout={(e) => setViewport(e.nativeEvent.layout.height)}>
            <Animated.ScrollView
              ref={listRef}
              animatedProps={scrollProps}
              onScroll={onScroll}
              scrollEventThrottle={16}
              showsVerticalScrollIndicator={false}
              indicatorStyle="white"
              onContentSizeChange={(_w, h) => setContent(h)}
              contentContainerStyle={{ height: contentHeight }}
            >
              <View style={{ marginTop: lay.listTop }}>
                {EPISODES.map((ep, i) => (
                  <EpisodeRow
                    key={ep.id}
                    ep={ep}
                    index={i}
                    model={model}
                    selecting={selecting}
                    selected={selected.has(i)}
                    downloaded={downloadedRows.has(i)}
                    time={times[i]}
                    color={colors[i]}
                    onActivate={activateRow}
                  />
                ))}
              </View>
              {landsAfter >= 0 && (
                <Animated.View
                  key={`land-${landsAfter}`}
                  entering={FadeIn.duration(TIMELINE.land)}
                  exiting={FadeOut.duration(TIMELINE.land)}
                  style={[styles.land, { top: lay.listTop + (landsAfter + 1) * LAYOUT.row - 8 }]}
                  pointerEvents="none"
                  accessible
                  accessibilityLabel="Lands at New York 5:30 PM"
                >
                  <View style={styles.landRule} />
                  <View style={styles.landDot} />
                  <Text style={styles.landText}>Lands JFK 5:30 PM</Text>
                </Animated.View>
              )}
            </Animated.ScrollView>
          </View>
        </GestureDetector>
        <Header lay={lay} selecting={ui.phase === PHASE.selecting} busy={ui.phase === PHASE.downloading || ui.phase === PHASE.done}
          model={model} scrollY={scrollY} reduced={reduced} onSelect={select} />
        <LinearGradient
          pointerEvents="none"
          colors={[COLOR.pageClear, COLOR.page, COLOR.page]}
          locations={[0, FOOT / (lay.height - lay.panelTop + FOOT), 1]}
          style={[styles.foot, { top: lay.panelTop - FOOT, height: lay.height - lay.panelTop + FOOT }]}
        />
        <FlightPanel lay={lay} ui={ui} model={model} reduced={reduced} onDownload={download} />
        {autoPlay && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
        <BoardingPass
          lay={lay}
          open={passOpen}
          episodes={ui.done.length}
          minutes={shortMinutes(minutes)}
          size={formatSize(minutes)}
          reduced={reduced}
          onClose={() => setClosedFor(ui)}
        />
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  foot: { position: 'absolute', left: 0, right: 0 },
  // A 16 pt band on the boundary between two rows: a green rule from the rail to the edge, a dot on the rail, the label.
  land: { position: 'absolute', left: LAYOUT.railX - 3, right: LAYOUT.edge, height: 16, flexDirection: 'row', alignItems: 'center' },
  landRule: { position: 'absolute', left: 10, right: 0, top: 8, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(61,220,132,0.45)' },
  landDot: { width: 10, height: 10, borderRadius: 5, borderWidth: 2, borderColor: COLOR.green, backgroundColor: COLOR.page },
  landText: { ...TYPE.land, color: COLOR.green, marginLeft: 'auto', backgroundColor: COLOR.page, paddingLeft: 8 },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
