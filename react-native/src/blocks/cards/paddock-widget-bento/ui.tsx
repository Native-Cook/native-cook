import React, { type ComponentProps } from 'react';
import { Platform, Text, View, type StyleProp, type TextProps, type TextStyle, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { SymbolView } from 'expo-symbols';

type SymbolProps = ComponentProps<typeof SymbolView>;
export type SymbolSpec = { readonly ios: string; readonly fallback: string; readonly fit: number };

/** An SF Symbol on iOS, the matching Material icon elsewhere. It occupies a `size` square, like SwiftUI's font-sized Image. */
export function Sym({ symbol, size, color, weight, style }: { symbol: SymbolSpec; size: number; color: string; weight?: SymbolProps['weight']; style?: StyleProp<ViewStyle> }) {
  if (Platform.OS === 'ios') {
    return (
      <View
        accessible={false}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }, style]}
      >
        <SymbolView name={symbol.ios as SymbolProps['name']} size={size * symbol.fit} tintColor={color} weight={weight} />
      </View>
    );
  }
  return <MaterialCommunityIcons name={symbol.fallback as keyof typeof MaterialCommunityIcons.glyphMap} size={size} color={color} style={style as StyleProp<TextStyle>} />;
}

/** Fixed-size text, like SwiftUI's `.system(size:)`: it does not follow Dynamic Type. */
export function Txt(props: TextProps) {
  return <Text allowFontScaling={false} {...props} />;
}
