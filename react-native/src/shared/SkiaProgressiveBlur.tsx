import MaskedView from '@react-native-masked-view/masked-view';
import { ImageFormat, Skia, TileMode } from '@shopify/react-native-skia';
import { BlurTargetView, BlurView } from 'expo-blur';
import { cloneElement, isValidElement, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, Image, Platform, StyleSheet, View, type LayoutChangeEvent, type ScrollViewProps, type StyleProp, type ViewStyle } from 'react-native';

export type ProgressiveBlurEdge = 'top' | 'bottom' | 'left' | 'right';
export type ProgressiveBlurViewportProps = {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  edges?: readonly ProgressiveBlurEdge[];
  /** Points, clamped to 0–56 and half the viewport dimension. */
  edgeSize?: number;
  tint?: 'light' | 'dark';
  enabled?: boolean;
  /** One native/RNGH/Animated ScrollView child; only its content-size callback is composed. */
  onlyWhenOverflow?: boolean;
};

const DEFAULT_EDGES: readonly ProgressiveBlurEdge[] = ['top', 'bottom'];
const INTENSITIES = [12, 32, 64] as const;
const MASK_SAMPLES = 65;
const MASK_LENGTH = 256;
// At most twelve tiny PNGs: four directions × three fixed blur profiles.
const maskCache = new Map<string, string>();

function smoothstep(value: number, start: number, end: number) {
  const t = Math.max(0, Math.min(1, (value - start) / (end - start)));
  return t * t * (3 - 2 * t);
}

function maskAlpha(depth: number, band: number) {
  const coverage = smoothstep(depth, 0, 0.38);
  const medium = smoothstep(depth, 0.18, 0.65);
  const strong = smoothstep(depth, 0.52, 1);
  const weights = [coverage * (1 - medium), coverage * medium * (1 - strong), coverage * medium * strong];
  // Convert a smooth partition into source-over alpha, ordered low → high.
  // This avoids dark gaps where neighboring profiles crossfade.
  const above = band === 0 ? weights[1] + weights[2] : band === 1 ? weights[2] : 0;
  return 1 - above > 0.00001 ? Math.min(1, weights[band] / (1 - above)) : 0;
}

function makeMask(edge: ProgressiveBlurEdge, band: number) {
  const key = `${edge}:${band}`;
  const cached = maskCache.get(key);
  if (cached) return cached;

  const vertical = edge === 'top' || edge === 'bottom';
  const width = vertical ? 2 : MASK_LENGTH;
  const height = vertical ? MASK_LENGTH : 2;
  // CPU raster surface: no native-view capture, GPU texture, or screen-sized image.
  const surface = Skia.Surface.Make(width, height);
  if (!surface) throw new Error('Unable to allocate the progressive blur mask');
  let paint: ReturnType<typeof Skia.Paint> | null = null;
  let shader: ReturnType<typeof Skia.Shader.MakeLinearGradient> | null = null;
  let snapshot: ReturnType<typeof surface.makeImageSnapshot> | null = null;
  try {
    paint = Skia.Paint();
    const start = edge === 'top' ? { x: 0, y: height } : edge === 'left' ? { x: width, y: 0 } : { x: 0, y: 0 };
    const end = edge === 'bottom' ? { x: 0, y: height } : edge === 'right' ? { x: width, y: 0 } : { x: 0, y: 0 };
    const positions = Array.from({ length: MASK_SAMPLES }, (_, index) => index / (MASK_SAMPLES - 1));
    const colors = positions.map(depth => Skia.Color(`rgba(255,255,255,${maskAlpha(depth, band)})`));
    shader = Skia.Shader.MakeLinearGradient(start, end, colors, positions, TileMode.Clamp);
    paint.setShader(shader);
    const canvas = surface.getCanvas();
    canvas.clear(Skia.Color('transparent'));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
    surface.flush();
    snapshot = surface.makeImageSnapshot();
    const png = snapshot.encodeToBase64(ImageFormat.PNG);
    if (!png) throw new Error('Unable to encode the progressive blur mask');
    const uri = `data:image/png;base64,${png}`;
    maskCache.set(key, uri);
    return uri;
  } finally {
    // The PNG owns its bytes. Native Skia objects do not outlive this one-off job.
    snapshot?.dispose();
    paint?.dispose();
    shader?.dispose();
    surface.dispose();
  }
}

function useReducedTransparency() {
  // A solid accessibility fallback is preferable to flashing blur before the query resolves.
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    let mounted = true;
    let revision = 0;
    const refresh = () => {
      const request = ++revision;
      void AccessibilityInfo.isReduceTransparencyEnabled().then(value => {
        if (mounted && request === revision) setReduced(value);
      }).catch(() => { /* Keep the solid fallback when the preference cannot be read. */ });
    };
    refresh();
    const preference = AccessibilityInfo.addEventListener('reduceTransparencyChanged', value => {
      revision++;
      if (mounted) setReduced(value);
    });
    const app = AppState.addEventListener('change', state => { if (state === 'active') refresh(); });
    return () => { mounted = false; revision++; preference.remove(); app.remove(); };
  }, []);
  return reduced;
}

function edgeFrame(edge: ProgressiveBlurEdge, size: number, width: number, height: number): ViewStyle {
  if (edge === 'top') return { position: 'absolute', top: 0, left: 0, width, height: Math.min(size, height / 2) };
  if (edge === 'bottom') return { position: 'absolute', bottom: 0, left: 0, width, height: Math.min(size, height / 2) };
  if (edge === 'left') return { position: 'absolute', top: 0, left: 0, width: Math.min(size, width / 2), height };
  return { position: 'absolute', top: 0, right: 0, width: Math.min(size, width / 2), height };
}

/**
 * Live native backdrop blur, progressively composed with static Skia alpha masks.
 * This is a three-profile approximation, not a continuously varying Skia blur kernel.
 * A single ScrollView child is cloned only to compose onContentSizeChange. Its ref,
 * key, scroll/gesture handlers and layout props are preserved. This is layout-time
 * measurement, never per-scroll JS. Set onlyWhenOverflow=false for non-scroll content.
 */
export function ProgressiveBlurViewport({ children, style, edges = DEFAULT_EDGES, edgeSize = 40, tint = 'light', enabled = true, onlyWhenOverflow = true }: ProgressiveBlurViewportProps) {
  const target = useRef<View>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const child = isValidElement<ScrollViewProps>(children) ? children : null;
  const childKey = child?.key;
  const childType = child?.type;
  const childHorizontal = !!child?.props.horizontal;
  const originalContentSizeChange = child?.props.onContentSizeChange;
  const [contentSize, setContentSize] = useState({ width: 0, height: 0, key: childKey, type: childType, horizontal: childHorizontal, measured: false });
  const [masks, setMasks] = useState<Record<string, readonly string[]>>({});
  const [maskFailed, setMaskFailed] = useState(false);
  const reduceTransparency = useReducedTransparency();
  const edgeKey = [...new Set(edges)].sort().join(',');
  const activeEdges = useMemo(() => edgeKey ? edgeKey.split(',') as ProgressiveBlurEdge[] : [], [edgeKey]);
  const measuredChild = contentSize.measured && contentSize.key === childKey && contentSize.type === childType && contentSize.horizontal === childHorizontal;
  const overflowsY = measuredChild && contentSize.height > size.height + 4;
  const overflowsX = measuredChild && contentSize.width > size.width + 4;
  const visibleEdges = activeEdges.filter(edge => !onlyWhenOverflow || (edge === 'top' || edge === 'bottom' ? overflowsY : overflowsX));
  const boundedEdge = Number.isFinite(edgeSize) ? Math.min(56, Math.max(0, edgeSize)) : 40;
  const oldAndroid = Platform.OS === 'android' && Number(Platform.Version) < 31;
  const solidFallback = reduceTransparency || oldAndroid || maskFailed;

  useEffect(() => {
    if (!enabled || reduceTransparency || oldAndroid || !activeEdges.length) return;
    let active = true;
    // One resource job after commit, not a scroll/frame callback. Cached masks are
    // independent of viewport size, so text scaling never allocates larger images.
    void Promise.resolve().then(() => {
      if (!active) return;
      try {
        const next: Record<string, readonly string[]> = {};
        for (const edge of activeEdges) next[edge] = INTENSITIES.map((_, index) => makeMask(edge, index));
        setMasks(next); setMaskFailed(false);
      } catch {
        setMaskFailed(true);
      }
    });
    return () => { active = false; };
  }, [activeEdges, enabled, oldAndroid, reduceTransparency]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize(current => current.width === width && current.height === height ? current : { width, height });
  };
  const onContentSizeChange = useCallback((width: number, height: number) => {
    setContentSize(current => current.measured && current.width === width && current.height === height && current.key === childKey && current.type === childType && current.horizontal === childHorizontal ? current : { width, height, key: childKey, type: childType, horizontal: childHorizontal, measured: true });
    originalContentSizeChange?.(width, height);
  }, [childHorizontal, childKey, childType, originalContentSizeChange]);
  const content = onlyWhenOverflow && child ? cloneElement(child, { onContentSizeChange }) : children;
  const ready = enabled && boundedEdge > 0 && size.width > 0 && size.height > 0 && visibleEdges.length > 0;
  return <View onLayout={onLayout} style={[styles.viewport, style]}>
    <BlurTargetView ref={target} style={styles.target}>{content}</BlurTargetView>
    {ready && <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
      {visibleEdges.map(edge => solidFallback ? (
        // Explicit non-blur fallback: a small solid edge, never a fake gradient blur.
        <View key={edge} style={[edgeFrame(edge, 2, size.width, size.height), { backgroundColor: tint === 'dark' ? '#20231F' : '#E5E9DF' }]} />
      ) : masks[edge]?.map((uri, band) => <MaskedView key={`${edge}:${band}`} pointerEvents="none" androidRenderingMode="hardware" style={edgeFrame(edge, boundedEdge, size.width, size.height)} maskElement={<Image source={{ uri }} resizeMode="stretch" fadeDuration={0} style={StyleSheet.absoluteFill} accessible={false} />}>
        <BlurView blurTarget={target} blurMethod="dimezisBlurViewSdk31Plus" tint={tint} intensity={INTENSITIES[band]} style={StyleSheet.absoluteFill} />
      </MaskedView>))}
    </View>}
  </View>;
}

const styles = StyleSheet.create({
  viewport: { flexGrow: 1, flexShrink: 1, minHeight: 0, overflow: 'hidden' },
  target: { flexGrow: 1, flexShrink: 1, minHeight: 0 },
});
