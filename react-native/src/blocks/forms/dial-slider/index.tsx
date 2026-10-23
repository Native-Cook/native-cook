import React from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';

import { DialSlider, type DialPreset } from './dial-slider';

function PresetGlyph({
  children,
  selected,
}: {
  children: string;
  selected: boolean;
}) {
  return (
    <Text style={[styles.glyph, selected && styles.selectedGlyph]}>
      {children}
    </Text>
  );
}

const PHOTO_PRESETS: readonly DialPreset[] = [
  {
    id: 'exposure',
    label: 'Exposure',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◐</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'brilliance',
    label: 'Brilliance',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◭</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'highlights',
    label: 'Highlights',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◒</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'shadows',
    label: 'Shadows',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◑</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'contrast',
    label: 'Contrast',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◉</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'brightness',
    label: 'Brightness',
    icon: ({ selected }) => <PresetGlyph selected={selected}>☀</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
  {
    id: 'saturation',
    label: 'Saturation',
    icon: ({ selected }) => <PresetGlyph selected={selected}>◍</PresetGlyph>,
    minValue: -100,
    maxValue: 100,
    initialValue: 0,
  },
];

export default function DialSliderDemo() {
  return (
    <ScrollView
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      style={styles.container}
      contentContainerStyle={styles.content}
    >
      <DialSlider
        presets={PHOTO_PRESETS}
        initialPresetId="highlights"
        backgroundColor="#000000"
        testID="dial-slider"
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
  },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingVertical: 24,
  },
  glyph: {
    color: '#E4E4E7',
    fontSize: 24,
    fontWeight: '500',
  },
  selectedGlyph: {
    color: '#FFFFFF',
  },
});
