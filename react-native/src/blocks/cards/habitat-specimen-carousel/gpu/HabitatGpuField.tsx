import { Component, useEffect, useRef, useState, type ReactNode } from 'react';
import { Platform, TurboModuleRegistry } from 'react-native';

export type HabitatWebGPU = typeof import('react-native-webgpu');

/**
 * Loads react-native-webgpu only once its native module is known to exist, so the block still runs
 * in Expo Go (on Skia alone). Returns the module, or null while loading or when it is unavailable.
 */
export function useHabitatWebGPU(enabled: boolean, onUnavailable?: () => void) {
  const [api, setApi] = useState<HabitatWebGPU | null>(null);
  const report = useRef(onUnavailable);
  useEffect(() => { report.current = onUnavailable; });
  useEffect(() => {
    if (!enabled) return;
    let live = true;
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (Platform.OS !== 'web' && !TurboModuleRegistry.get('WebGPUModule')) {
          report.current?.();
          return;
        }
        // A native-package value import must never precede the module check.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const native = require('react-native-webgpu') as HabitatWebGPU;
        if (live) setApi(native);
      } catch { if (live) report.current?.(); }
    });
    return () => { live = false; };
  }, [enabled]);
  return enabled ? api : null;
}

/** A render error inside the GPU layers drops them and reports, instead of taking the block down. */
export class HabitatGpuBoundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure(); }
  render() { return this.state.failed ? null : this.props.children; }
}
