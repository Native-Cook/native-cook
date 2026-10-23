import React from 'react';
import { Pressable, ScrollView, Switch, Text, TextInput, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { colors, copy, layout, native, radius, space, symbols, type } from './theme';
import { Sym, Txt } from './ui';
import type { Destination } from './StaticViews';

export type PageState = {
  order: number[];
  editing: boolean;
  savedWeekends: string[];
  toggleWeekend: (venue: string, saved: boolean) => void;
  selectedCircuit: string;
  setSelectedCircuit: (value: string) => void;
  lapCount: number;
  logLap: () => void;
  reactionText: string;
  reactionTitle: string;
  reactionAction: () => void;
  latestStats: boolean;
  setLatestStats: (value: boolean) => void;
  searchQuery: string;
  setSearchQuery: (value: string) => void;
  expandedLesson: string;
  setExpandedLesson: (value: string) => void;
  selectedWidget: string;
  setSelectedWidget: (value: string) => void;
  pinned: string[];
  pin: () => void;
  moveCard: (id: number, offset: number) => void;
  toggleEditing: () => void;
  restoreOrder: () => void;
  restoreGuide: () => void;
};

const CARD_NAMES = [copy.cards.laps.name, copy.cards.reflex.name, copy.cards.homeCards.name];

export function widgetSymbol(widget: string) {
  return widget === 'Lap count' ? symbols.laps : widget === 'Race weekend' ? symbols.calendar : symbols.circuits;
}

const body: TextStyle = { fontSize: type.body, color: colors.white };
const secondary: TextStyle = { fontSize: type.body, color: colors.secondary };

function ProminentButton({ title, onPress, disabled, width }: { title: string; onPress: () => void; disabled?: boolean; width?: ViewStyle['width'] }) {
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={{ height: native.buttonHeight, justifyContent: 'center', alignSelf: width ? 'stretch' : 'center' }}>
      <View style={{ height: native.buttonHeight, backgroundColor: colors.accent, borderRadius: native.buttonHeight / 2, paddingHorizontal: native.buttonInsetX, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ ...body, fontWeight: '600' }}>{title}</Text>
      </View>
    </Pressable>
  );
}

function BorderedButton({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ height: native.buttonHeight, justifyContent: 'center', alignSelf: 'flex-start' }}>
      <View style={{ height: native.buttonHeight, backgroundColor: colors.accentTint, borderRadius: native.buttonHeight / 2, paddingHorizontal: native.buttonInsetX, justifyContent: 'center' }}>
        <Text style={{ ...body, color: colors.accent, fontWeight: '600' }}>{title}</Text>
      </View>
    </Pressable>
  );
}

function TextButton({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ minHeight: layout.unscaled.tapTarget, minWidth: layout.unscaled.tapTarget, justifyContent: 'center', alignItems: 'center', paddingHorizontal: native.stackSpacing }}>
      <Text style={{ ...body, color: colors.accent }}>{title}</Text>
    </Pressable>
  );
}

function Row({ children, s, style }: { children: React.ReactNode; s: number; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ backgroundColor: colors.panel08, borderRadius: radius.row * s, padding: space.rowInset * s }, style]}>{children}</View>;
}

/** The outline of a circuit, drawn at 80 x 44 pt like the Swift canvas. */
export function CircuitOutline({ circuit, selected }: { circuit: string; selected: boolean }) {
  const h = layout.unscaled.outlineHeight;
  const d =
    circuit === 'Mesa'
      ? 'M18 5 L58 9 L72 30 L35 40 Z'
      : `M4 ${h * 0.6} C6 8 25 2 40 6 C60 8 77 12 74 28 C55 45 15 42 4 ${h * 0.6}`;
  return (
    <Svg width={layout.unscaled.outlineWidth} height={h} accessible={false}>
      <Path d={d} fill="none" stroke={selected ? colors.selectedCircuit : 'rgba(255,255,255,0.55)'} strokeWidth={selected ? 3 : 2} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

export function CalendarContent({ s, state }: { s: number; state: PageState }) {
  return (
    <View style={{ gap: space.rowGap * s }}>
      {copy.weekends.map((venue) => {
        const saved = state.savedWeekends.includes(venue);
        return (
          <Pressable
            key={venue}
            accessibilityRole="switch"
            accessibilityLabel={`${venue} · Race weekend`}
            accessibilityState={{ checked: saved }}
            onPress={() => state.toggleWeekend(venue, !saved)}
          >
            <Row s={s} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ ...body, flexShrink: 1 }}>{`${venue} · Race weekend`}</Text>
              <Switch accessible={false} value={saved} onValueChange={(value) => state.toggleWeekend(venue, value)} trackColor={{ true: native.switchOn }} />
            </Row>
          </Pressable>
        );
      })}
    </View>
  );
}

export function ResultsContent({ s }: { s: number }) {
  return (
    <View>
      {copy.racers.map((racer, index) => (
        <View
          key={racer[0]}
          style={{ minHeight: space.resultRow * s, flexDirection: 'row', alignItems: 'center', gap: native.stackSpacing, borderBottomWidth: space.hairline, borderBottomColor: colors.hairline }}
        >
          <Text style={secondary}>{index + 1}</Text>
          <Text style={{ ...body, flex: 1 }}>{racer[0]}</Text>
          <Text style={secondary}>{`${racer[1]} pts`}</Text>
        </View>
      ))}
    </View>
  );
}

export function CircuitsContent({ s, state }: { s: number; state: PageState }) {
  return (
    <View style={{ gap: space.circuitGap * s }}>
      {copy.circuits.map((circuit) => {
        const selected = state.selectedCircuit === circuit;
        return (
          <Pressable
            key={circuit}
            accessibilityRole="button"
            accessibilityLabel={`${circuit} circuit`}
            accessibilityState={{ selected }}
            onPress={() => state.setSelectedCircuit(circuit)}
            style={{ minHeight: Math.max(space.circuitRow * s, layout.unscaled.tapTarget), flexDirection: 'row', alignItems: 'center', gap: native.stackSpacing }}
          >
            <CircuitOutline circuit={circuit} selected={selected} />
            <Text style={{ ...body, flex: 1 }}>{circuit}</Text>
            {selected ? <Sym symbol={symbols.check} size={type.body} color={colors.white} /> : null}
          </Pressable>
        );
      })}
      <Text style={secondary}>{`${state.selectedCircuit} Circuit · Local venue guide`}</Text>
    </View>
  );
}

export function CustomizeContent({ s, state }: { s: number; state: PageState }) {
  return (
    <View style={{ gap: space.customizeGap * s, alignItems: 'flex-start' }}>
      <Text style={{ ...body, fontWeight: '600' }}>{copy.customize.heading}</Text>
      {state.order.map((id) => (
        <View
          key={id}
          style={{
            alignSelf: 'stretch', flexDirection: 'row', alignItems: 'center', paddingHorizontal: space.customizeInset * s,
            backgroundColor: colors.panel08, borderRadius: radius.customizeRow * s,
          }}
        >
          <Text style={{ ...body, flex: 1 }}>{CARD_NAMES[id]}</Text>
          <TextButton title={copy.customize.up} onPress={() => state.moveCard(id, -1)} />
          <TextButton title={copy.customize.down} onPress={() => state.moveCard(id, 1)} />
        </View>
      ))}
      <ProminentButton title={state.editing ? copy.customize.done : copy.customize.arrange} onPress={state.toggleEditing} />
      <BorderedButton title={copy.customize.restoreOrder} onPress={state.restoreOrder} />
      <BorderedButton title={copy.customize.restoreGuide} onPress={state.restoreGuide} />
    </View>
  );
}

/** A secondary tab: a bold title and its content, inside the scrolling feed. */
export function SecondaryPage({ tab, s, state }: { tab: Destination; s: number; state: PageState }) {
  return (
    <View style={{ gap: space.pageGap * s, paddingHorizontal: space.pageInset * s, paddingTop: space.pageTop * s, alignItems: 'stretch' }}>
      <Text allowFontScaling={false} style={{ fontSize: type.pageTitle * s, fontWeight: '700', color: colors.white }}>
        {tab.charAt(0).toUpperCase() + tab.slice(1)}
      </Text>
      {tab === 'calendar' ? <CalendarContent s={s} state={state} /> : null}
      {tab === 'results' ? <ResultsContent s={s} /> : null}
      {tab === 'circuits' ? <CircuitsContent s={s} state={state} /> : null}
      {tab === 'customize' ? <CustomizeContent s={s} state={state} /> : null}
    </View>
  );
}

function GroupedList({ header, children }: { header?: string; children: React.ReactNode }) {
  const rows = React.Children.toArray(children);
  return (
    <View style={{ marginHorizontal: native.listInset, marginTop: native.listInset }}>
      {header ? <Text style={{ ...secondary, fontSize: native.smallText, marginLeft: native.listInset, marginBottom: native.stackSpacing }}>{header}</Text> : null}
      <View style={{ backgroundColor: colors.sheetRow, borderRadius: native.listRadius, overflow: 'hidden' }}>
        {rows.map((row, index) => (
          <View
            key={index}
            style={{ minHeight: native.listRowMinHeight, paddingHorizontal: native.listInset, paddingVertical: native.listRowInsetY, justifyContent: 'center', borderTopWidth: index === 0 ? 0 : space.hairline, borderTopColor: colors.sheetSeparator }}
          >
            {row}
          </View>
        ))}
      </View>
    </View>
  );
}

function Glossary({ state }: { state: PageState }) {
  const matches = state.searchQuery === '' ? copy.glossaryTerms : copy.glossaryTerms.filter((term) => term[0].toLowerCase().includes(state.searchQuery.toLowerCase()));
  return (
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ paddingBottom: native.listInset }}>
      <View style={{ marginHorizontal: native.listInset, marginTop: native.stackSpacing, height: native.searchHeight, borderRadius: native.searchRadius, backgroundColor: native.segmentedTrack, flexDirection: 'row', alignItems: 'center', paddingHorizontal: native.stackSpacing, gap: native.searchGap }}>
        <Text style={{ ...secondary, fontSize: native.searchIcon }}>⌕</Text>
        <TextInput
          accessibilityLabel="Search glossary"
          value={state.searchQuery}
          onChangeText={state.setSearchQuery}
          placeholder="Search glossary"
          placeholderTextColor={colors.secondary}
          autoCorrect={false}
          style={{ ...body, flex: 1, paddingVertical: 0 }}
        />
      </View>
      <GroupedList>
        {matches.map((term) => (
          <View key={term[0]}>
            <Text style={{ ...body, fontWeight: '600' }}>{term[0]}</Text>
            <Text style={secondary}>{term[1]}</Text>
          </View>
        ))}
      </GroupedList>
    </ScrollView>
  );
}

function Lessons({ state }: { state: PageState }) {
  return (
    <ScrollView contentContainerStyle={{ paddingBottom: native.listInset }}>
      <GroupedList>
        {copy.lessonOrder.map((lesson) => {
          const open = state.expandedLesson === lesson;
          return (
            <View key={lesson}>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ expanded: open }}
                onPress={() => state.setExpandedLesson(open ? '' : lesson)}
                style={{ minHeight: layout.unscaled.tapTarget, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}
              >
                <Text style={{ ...body, fontWeight: '600' }}>{lesson}</Text>
                <Text style={{ ...secondary, fontWeight: '600' }}>{open ? '⌄' : '›'}</Text>
              </Pressable>
              {open ? <Text style={{ ...body, paddingBottom: native.listRowInsetY }}>{copy.lessons[lesson]}</Text> : null}
            </View>
          );
        })}
      </GroupedList>
    </ScrollView>
  );
}

function Segmented({ latest, onChange }: { latest: boolean; onChange: (value: boolean) => void }) {
  const segment = (label: string, value: boolean) => (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: latest === value }}
      onPress={() => onChange(value)}
      style={{ flex: 1, minHeight: layout.unscaled.tapTarget, justifyContent: 'center', alignItems: 'center' }}
    >
      <View style={{ position: 'absolute', left: 2, right: 2, top: (layout.unscaled.tapTarget - native.segmentedHeight) / 2 + 2, height: native.segmentedHeight - 4, borderRadius: native.segmentedThumbRadius, backgroundColor: latest === value ? native.segmentedThumb : 'transparent' }} />
      <Text style={{ ...body, fontSize: native.smallText, fontWeight: latest === value ? '600' : '400' }}>{label}</Text>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: 'row', alignSelf: 'stretch', backgroundColor: native.segmentedTrack, borderRadius: native.segmentedRadius, height: layout.unscaled.tapTarget, overflow: 'hidden' }}>
      {segment('Latest', true)}
      {segment('Season', false)}
    </View>
  );
}

function WidgetStudio({ state }: { state: PageState }) {
  const pinned = state.pinned.includes(state.selectedWidget);
  return (
    <ScrollView contentContainerStyle={{ padding: space.studioInset, gap: space.studioGap }}>
      <Text style={{ fontSize: type.title2, fontWeight: '700', color: colors.white }}>{copy.studio.heading}</Text>
      <Text style={secondary}>{copy.studio.body}</Text>
      <View style={{ height: layout.unscaled.previewHeight, borderRadius: radius.preview, backgroundColor: colors.panel10, alignItems: 'center', justifyContent: 'center', gap: space.previewGap }}>
        <Sym symbol={widgetSymbol(state.selectedWidget)} size={type.studioIcon} color={colors.white} />
        <Text style={{ fontSize: type.title3, fontWeight: '600', color: colors.white }}>{state.selectedWidget}</Text>
      </View>
      {copy.widgets.map((option) => (
        <Pressable
          key={option}
          accessibilityRole="button"
          accessibilityState={{ selected: state.selectedWidget === option }}
          onPress={() => state.setSelectedWidget(option)}
          style={{ minHeight: layout.unscaled.rowMinHeight, flexDirection: 'row', alignItems: 'center', gap: native.stackSpacing }}
        >
          <Sym symbol={widgetSymbol(option)} size={type.body} color={colors.white} />
          <Text style={{ ...body, flex: 1 }}>{option}</Text>
          {state.selectedWidget === option ? <Sym symbol={symbols.check} size={type.body} color={colors.white} /> : null}
        </Pressable>
      ))}
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: pinned }}
        disabled={pinned}
        onPress={state.pin}
        style={{ minHeight: layout.unscaled.pinHeight, borderRadius: layout.unscaled.pinHeight / 2, alignItems: 'center', justifyContent: 'center', backgroundColor: pinned ? colors.panel12 : colors.white }}
      >
        <Text style={{ ...body, fontWeight: '600', color: pinned ? colors.disabledPin : colors.black }}>{pinned ? copy.studio.pinned : copy.studio.pin}</Text>
      </Pressable>
    </ScrollView>
  );
}

function Centered({ children, gap }: { children: React.ReactNode; gap: number }) {
  return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap }}>{children}</View>;
}

/** Swift's `rawValue.capitalized`: first letter up, the rest down ("homeCards" reads "Homecards"). */
export function sheetTitle(destination: Destination) {
  if (destination === 'widgetStudio' || destination === 'banner') return 'Widget Studio';
  return destination.charAt(0).toUpperCase() + destination.slice(1).toLowerCase();
}

/** Content of the native-style sheet a destination opens (Swift: `destinationContent`). */
export function SheetContent({ destination, state }: { destination: Destination; state: PageState }) {
  switch (destination) {
    case 'laps':
      return (
        <Centered gap={space.lapsGap}>
          <Text style={{ fontSize: type.largeTitle, fontWeight: '700', color: colors.white }}>{copy.laps.title}</Text>
          <Text style={body}>{copy.laps.week}</Text>
          <Text style={body}>{`Today's laps: ${state.lapCount}`}</Text>
          <ProminentButton title={copy.laps.logLap} onPress={state.logLap} />
        </Centered>
      );
    case 'reflex':
      return (
        <Centered gap={space.reactionGap}>
          <Text style={{ fontSize: type.largeTitle, fontWeight: '700', color: colors.white }}>{state.reactionText}</Text>
          <ProminentButton title={state.reactionTitle} onPress={state.reactionAction} />
        </Centered>
      );
    case 'pitcast':
      return (
        <ScrollView>
          <GroupedList header={copy.pitcastHeading}>
            {copy.pitcastNotes.map((note) => (
              <Text key={note} style={body}>{note}</Text>
            ))}
          </GroupedList>
        </ScrollView>
      );
    case 'stats':
      return (
        <View style={{ padding: space.sheetPad, gap: space.statsGap }}>
          <Segmented latest={state.latestStats} onChange={state.setLatestStats} />
          <Txt allowFontScaling style={{ fontSize: type.title2, fontWeight: '700', color: colors.white }}>
            {state.latestStats ? copy.statsSheet.latest : copy.statsSheet.season}
          </Txt>
        </View>
      );
    case 'glossary':
      return <Glossary state={state} />;
    case 'education':
      return <Lessons state={state} />;
    case 'widgetStudio':
    case 'banner':
      return <WidgetStudio state={state} />;
    case 'guide':
      return (
        <ScrollView>
          <GroupedList>
            {copy.guideSteps.map((step) => (
              <Text key={step} style={body}>{step}</Text>
            ))}
          </GroupedList>
        </ScrollView>
      );
    case 'homeCards':
    case 'customize':
      return (
        <ScrollView contentContainerStyle={{ padding: space.sheetPad }}>
          <CustomizeContent s={1} state={state} />
        </ScrollView>
      );
    default:
      return (
        <Centered gap={0}>
          <Text style={body}>{copy.localContent}</Text>
        </Centered>
      );
  }
}
