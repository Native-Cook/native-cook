import type { SharedValue } from 'react-native-reanimated';

export type DiamondGpuFrame = {
  /** Seconds from the finite parent scene clock. */
  phase: number;
  progress: number;
  lightX: number;
  lightY: number;
  /** Material ownership survives a paused scene/overlay. */
  visible: boolean;
  /** Advance GPU submissions only while the parent scene clock is running. */
  active: boolean;
  reduced: boolean;
};

export type DiamondGpuProps = {
  frame: SharedValue<DiamondGpuFrame>;
  asset: number | string;
  size: number;
  onReady?: (ready: boolean) => void;
  onUnavailable?: (reason: string) => void;
};
