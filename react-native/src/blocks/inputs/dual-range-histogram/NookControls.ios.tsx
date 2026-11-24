import { HStack, Host, Picker, Text, Toggle, VStack } from '@expo/ui/swift-ui';
import { font, foregroundStyle, frame, pickerStyle, tag, tint } from '@expo/ui/swift-ui/modifiers';

import { AMENITIES, BEDROOMS, PLACE_TYPES } from './nookData';
import type { NookControlsProps, NookPlacePickerProps } from './NookControls.types';

const ACCENT = '#d9542f';

/** The place type as a real segmented control (iOS). */
export function NookPlacePicker({ width, placeType, onPlaceType }: NookPlacePickerProps) {
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width }}>
      <Picker modifiers={[pickerStyle('segmented')]} onSelectionChange={onPlaceType} selection={placeType}>
        {PLACE_TYPES.map((name, i) => (
          <Text key={name} modifiers={[tag(i)]}>
            {name}
          </Text>
        ))}
      </Picker>
    </Host>
  );
}

/** Bedrooms and the amenities: real SwiftUI controls (iOS). Android gets the rows in NookControls.tsx. */
export function NookControls({ width, bedrooms, amenities, amenityRows, onBedrooms, onAmenity }: NookControlsProps) {
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width }}>
      <VStack spacing={12}>
        <HStack spacing={12}>
          <Text modifiers={[font({ size: 15, weight: 'medium' }), frame({ maxWidth: 10000, alignment: 'leading' })]}>Bedrooms</Text>
          <Picker modifiers={[pickerStyle('segmented'), frame({ width: 200 })]} onSelectionChange={onBedrooms} selection={bedrooms}>
            {BEDROOMS.map((name, i) => (
              <Text key={name} modifiers={[tag(i)]}>
                {name}
              </Text>
            ))}
          </Picker>
        </HStack>
        {AMENITIES.slice(0, amenityRows).map((a, i) => (
          <Toggle isOn={amenities[i]} key={a.label} modifiers={[tint(ACCENT)]} onIsOnChange={(on) => onAmenity(i, on)}>
            <Text modifiers={[font({ size: 15 }), foregroundStyle('#2a2420')]}>{a.label}</Text>
          </Toggle>
        ))}
      </VStack>
    </Host>
  );
}
