import { Component, useEffect, useRef, useState, type ReactNode } from 'react';
import { Platform, TurboModuleRegistry } from 'react-native';

import { NativeDiamondGpu } from './NativeDiamondGpu';
import type { DiamondGpuProps } from './types';
export type { DiamondGpuFrame, DiamondGpuProps } from './types';

type WebGPU = typeof import('react-native-webgpu');

/** Optional native material. Merely importing this module is safe in Expo Go. */
export function DiamondGpu(props: DiamondGpuProps) {
  const [api, setApi] = useState<WebGPU | null>(null);
  const [failed, setFailed] = useState(false);
  const callbacks = useRef(props);
  useEffect(() => { callbacks.current = props; });
  useEffect(() => {
    let live = true;
    const unavailable = (reason: string) => {
      if (!live) return;
      setFailed(true);
      try { callbacks.current.onReady?.(false); callbacks.current.onUnavailable?.(reason); } catch { /* Keep the baseline usable. */ }
    };
    void Promise.resolve().then(() => {
      if (!live) return;
      try {
        if (Platform.OS !== 'web' && !TurboModuleRegistry.get('WebGPUModule')) {
          unavailable('Native WebGPU is not included in this app binary.');
          return;
        }
        // No native-package value import may precede the module guard.
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const native = require('react-native-webgpu') as WebGPU;
        if (live) setApi(native);
      } catch (error) { unavailable(error instanceof Error ? error.message : String(error)); }
    });
    return () => { live = false; };
  }, []);
  if (!api || failed) return null;
  return <DiamondBoundary onFailure={() => {
    setFailed(true); props.onReady?.(false); props.onUnavailable?.('Native diamond surface could not be mounted.');
  }}><NativeDiamondGpu {...props} webgpu={api} /></DiamondBoundary>;
}

class DiamondBoundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure(); }
  render() { return this.state.failed ? null : this.props.children; }
}
