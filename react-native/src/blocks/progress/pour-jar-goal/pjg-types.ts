import type { ReactNode } from 'react';
import type { AudioSource } from 'expo-audio';
import type { SharedValue } from 'react-native-reanimated';
import type { Vessel } from './pjg-vessel';

export type LiquidGaugeProps = {
  value?: number;
  defaultValue?: number;
  min?: number;
  max: number;
  step?: number;
  onChange?: (value: number) => void;
  onCommit?: (value: number, delta: number) => void;
  level?: SharedValue<number>;
  editable?: boolean;
  allowDecrease?: boolean;
  ticks?: number[];
  colors?: [surface: string, depth: string];
  vessel?: Vessel;
  viscosity?: number;
  bubbles?: number;
  format?: (value: number) => string;
  tokens?: { page?: string; ink?: string; mute?: string; numeralFont?: string; uiFont?: string };
  autoPlay?: boolean;
};
export type LiquidGaugeProProps = LiquidGaugeProps & {
  ambient?: boolean;
  sound?: boolean | { pour: AudioSource; land: AudioSource };
  holdToPour?: { startRate: number; maxRate: number; rampSeconds: number };
  undoSeconds?: number;
  onTick?: (fraction: number) => void;
  children?: ReactNode;
  title?: string;
  onBack?: () => void;
  onOptions?: () => void;
  onAutoChange?: (enabled: boolean) => void;
};
