import {
    BlurMask,
    Canvas,
    Circle,
    Group,
    LinearGradient,
    Path,
    RadialGradient,
    Rect,
    RoundedRect,
    Skia,
    Image as SkiaImage,
    Vertices,
    useImage,
    vec,
    type SkPoint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useIsFocused } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, View, useWindowDimensions, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
    Easing,
    ReduceMotion,
    cancelAnimation,
    useAnimatedReaction,
    useAnimatedStyle,
    useDerivedValue,
    useReducedMotion,
    useSharedValue,
    withDelay,
    withSpring,
    withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BodyMaterial } from './body-material';
import { CARD_CENTERING_DURATION_MS, cardTopAtTime } from './card-motion';
import { PACK_CARD_RARITIES } from './card-reveal-motion';
import { CardRevealScene } from './card-reveal-scene';
import { CollectionCardBackdrop } from './collection-card-backdrop';
import { CutFlash } from './cut-flash';
import { FlyingFoilMaterial } from './foil-material';
import { deformFlyingFoil } from './foil-motion';
import { FoldMaterial } from './fold-material';
import { deformPreviewFoil } from './fold-motion';
import { deriveMouthLighting, type PackLightingPose } from './mouth-lighting';
import { MouthMaterial } from './mouth-material';
import { CardRiseLight, CutBurst, IdleClockDriver, TearSparks } from './opening-effects';
import {
    commitPunchScale,
    idleAuraBreath,
    idleSeamHint,
    idleSheen,
    type IdlePrime,
} from './opening-effects-motion';
import { advanceTearPoints } from './tear-gesture';
import { useAutoPlay } from './use-auto-play';
import { useCardReveal } from './use-card-reveal';
import { usePackAudio } from './use-pack-audio';

import {
    BODY_MESH_INDICES,
    MOUTH_WALL_INDICES,
    MOUTH_WALL_ROWS,
    PREVIEW_MESH_ROWS,
    completeCutPoints,
    completePreviewCutPoints,
    curlMouthPoints,
    expandMouthMesh,
    makeBodyMaterialMesh,
    makeBottomMask,
    makeCavityBottomMask,
    makeCavityFrontEdge,
    makeCollapsedFoilMesh,
    makeCollapsedPreviewFoilMesh,
    makeEmptyPath,
    makeEntryBodyWedge,
    makeFoilMesh,
    makeMouthMaterialMesh,
    makeMouthMeshOutline,
    makeMouthWallMesh,
    makePreviewFoilMesh,
    makeSmoothPath,
    makeTopMask,
    relaxMouthPoints,
    trimTraceEnd,
} from './geometry';
import {
    BODY_KEYFRAMES,
    CARD_KEYFRAMES,
    OPENING_DURATION_MS,
    POST_CUT_DURATION_MS,
    REFERENCE_LAYOUT,
    clamp,
    interpolateKeyframes,
    type Rect as PackRect,
    type Point,
} from './model';

const PACK_IMAGE = require('./assets/booster-pack.webp');
// The card that rises out of the pack is the first of the five.
const CARD_IMAGE = require('./assets/booster-card-bulbasaur.webp');

const BODY_TIMES = BODY_KEYFRAMES.map(({ ms }) => ms);
const BODY_SEAM_Y = BODY_KEYFRAMES.map(({ seamY }) => seamY);
const BODY_SCALE = BODY_KEYFRAMES.map(({ scale }) => scale);

const CARD_TIMES = CARD_KEYFRAMES.map(({ ms }) => ms);
const CARD_OPACITY = CARD_KEYFRAMES.map(({ opacity }) => opacity);

const EMPTY_POINTS: Point[] = [];
const TRACE_THICKNESS_SCALE = 0.85;
const DIM_PROGRESS = [0, 0.1175, 0.44, 0.52, 0.72, 0.915, 1];
const DIM_OPACITY = [0, 0.21, 0.374, 0.456, 0.62, 0.7, 0.7];
const BACKDROP_REVEAL_LEAD_MS = 160;
const BACKDROP_FADE_DURATION_MS = 320;
const BACKDROP_ZOOM_DURATION_MS = 2_600;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function playCutHaptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => undefined);
}

export type BoosterPackOpeningProps = Readonly<{
  /** Plays the block by itself in a loop, silently, until it is touched. */
  autoPlay?: boolean;
  /** Plays no sound. */
  muted?: boolean;
}>;

/**
 * A sealed foil pack that is opened by hand. A swipe across its top tears it
 * along the finger's path, and the torn strip folds and curls as the tear
 * advances. When the cut reaches the far edge there is a flash, the strip
 * flies off, the mouth opens and slides down, and the first card rises and
 * centres. Five cards follow: drag one to tilt it, tap or flick to bring the
 * next, and the two rarest turn through their back. The last card stays until
 * "Open another pack" is pressed.
 *
 * One Skia canvas draws the scene; shared values hold the cut and the two
 * clocks (finger progress, time since the cut), and every mesh, mask and
 * material is a function of those. It fills the view it is given: the layout
 * is measured on a phone screen and scales with that view. Reduced Motion
 * replaces travel with fades.
 *
 * The ambience plays while the screen is focused and the app is in front,
 * the tear sounds while the finger moves, and the opening and each card have
 * a cue; `muted` and `autoPlay` play nothing. `autoPlay` runs the script in
 * use-auto-play.ts, without haptics, until a touch takes over.
 */
export function BoosterPackOpening({ autoPlay = false, muted = false }: BoosterPackOpeningProps) {
  const windowSize = useWindowDimensions();
  // The window stands in until the view has been measured.
  const [viewSize, setViewSize] = useState<Readonly<{ width: number; height: number }> | null>(null);
  const { width, height } = viewSize ?? windowSize;
  const measure = useCallback((event: LayoutChangeEvent) => {
    const { width: nextWidth, height: nextHeight } = event.nativeEvent.layout;
    if (nextWidth <= 0 || nextHeight <= 0) return;
    setViewSize((current) => current &&
      Math.abs(current.width - nextWidth) < 0.5 && Math.abs(current.height - nextHeight) < 0.5
      ? current : { width: nextWidth, height: nextHeight });
  }, []);
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const isFocused = useIsFocused();
  const {
    beginTear: beginTearAudio,
    setTearing: setTearAudioMoving,
    commitCut: playOpenAudio,
    beginCardAdvance: playNextCard,
    revealCard: playCardReveal,
    reset: resetAudio,
  } = usePackAudio(isFocused && !muted && !autoPlay, PACK_CARD_RARITIES);

  const pack = useMemo<PackRect>(() => {
    const packWidth = width * REFERENCE_LAYOUT.pack.width;
    // The reference video applies a small non-uniform viewport scale to the pack.
    // Preserve that measured silhouette instead of stretching the calibrated crop.
    const packHeight =
      packWidth *
      REFERENCE_LAYOUT.pack.sourceAspect *
      REFERENCE_LAYOUT.pack.viewportHeightScale;
    return {
      x: width * REFERENCE_LAYOUT.pack.centerX - packWidth / 2,
      y: height * REFERENCE_LAYOUT.pack.top,
      width: packWidth,
      height: packHeight,
    };
  }, [height, width]);

  const settledBodyTexture = useMemo<PackRect>(() => {
    // The moving body in the reference video settles back toward the package's native
    // aspect ratio after the first separation beat.
    const packWidth = width * REFERENCE_LAYOUT.pack.settledBodyWidth;
    const packHeight = packWidth * REFERENCE_LAYOUT.pack.sourceAspect;
    return {
      x: width * REFERENCE_LAYOUT.pack.centerX - packWidth / 2,
      y: height * REFERENCE_LAYOUT.pack.top,
      width: packWidth,
      height: packHeight,
    };
  }, [height, width]);

  const seamY = height * REFERENCE_LAYOUT.seamY;
  const traceSize = width * TRACE_THICKNESS_SCALE;
  const glowWidth = traceSize * 0.055;
  const traceBodyWidth = traceSize * 0.072;
  const traceFillWidth = traceSize * 0.056;
  const traceCoreWidth = traceSize * 0.046;
  const packImage = useImage(PACK_IMAGE);
  const cardImage = useImage(CARD_IMAGE);

  const points = useSharedValue<Point[]>(EMPTY_POINTS);
  const topMask = useSharedValue(makeEmptyPath());
  const bottomMask = useSharedValue(makeEmptyPath());
  const previewBottomMask = useSharedValue(makeEmptyPath());
  const collapsedMesh = useMemo(() => makeCollapsedFoilMesh(pack), [pack]);
  const collapsedPreviewMesh = useMemo(
    () => makeCollapsedPreviewFoilMesh(pack),
    [pack],
  );
  const previewMeshBase = useSharedValue<SkPoint[]>(
    collapsedPreviewMesh.vertices,
  );
  const previewMeshTextures = useSharedValue<SkPoint[]>(
    collapsedPreviewMesh.textures,
  );
  const peelVisible = useSharedValue(0);
  const meshBase = useSharedValue<SkPoint[]>(collapsedMesh.vertices);
  const meshTextures = useSharedValue<SkPoint[]>(collapsedMesh.textures);
  const split = useSharedValue(0);
  const committed = useSharedValue(0);
  const active = useSharedValue(0);
  const direction = useSharedValue<1 | -1>(1);
  const cutProgress = useSharedValue(0);
  const traceOpacity = useSharedValue(0);
  const dimOpacity = useSharedValue(0);
  const postCutMs = useSharedValue(0);
  const audioMovement = useSharedValue(0);
  const backdropOpacity = useSharedValue(0);
  const backdropZoom = useSharedValue(0);
  const idlePhase = useSharedValue(0);
  const idleMix = useSharedValue(0);
  // The autoPlay script's side of the scene: whether it is driving, how many
  // times it has reset the scene, and where it last restarted the idle cycle.
  const scripted = useSharedValue(autoPlay ? 1 : 0);
  const scriptResets = useSharedValue(0);
  const idlePrime = useSharedValue<IdlePrime>({ count: 0, ms: 0 });
  // The added sparkle travels, so Reduced Motion leaves it out.
  const liveEffects = !reduceMotion;
  const cards = useCardReveal({
    width, height, postCutMs, enabled: isFocused,
    reducedMotion: reduceMotion, scripted,
    playNextCard, playCardReveal,
  });

  const backdropRevealTime = reduceMotion
    ? POST_CUT_DURATION_MS
    : POST_CUT_DURATION_MS + CARD_CENTERING_DURATION_MS - BACKDROP_REVEAL_LEAD_MS;

  useAnimatedReaction(
    () =>
      committed.get() === 1 &&
      postCutMs.get() >= backdropRevealTime,
    (visible, previous) => {
      if (visible === previous) return;

      cancelAnimation(backdropOpacity);
      cancelAnimation(backdropZoom);
      if (!visible) {
        backdropOpacity.set(0);
        backdropZoom.set(0);
        return;
      }

      backdropOpacity.set(
        withTiming(1, {
          duration: reduceMotion ? 180 : BACKDROP_FADE_DURATION_MS,
          easing: EASE_OUT,
        }),
      );
      backdropZoom.set(
        reduceMotion
          ? 1
          : withTiming(1, {
              duration: BACKDROP_ZOOM_DURATION_MS,
              easing: EASE_OUT,
            }),
      );
    },
    [backdropRevealTime, reduceMotion],
  );

  // Only bridge movement START/STOP to the player. Geometry and the 100 ms
  // inactivity detector stay on the UI thread, including during a long hold.
  useAnimatedReaction(
    () => isFocused && active.get() === 1 && audioMovement.get() > 0,
    (moving, previous) => {
      if (moving !== previous) scheduleOnRN(setTearAudioMoving, moving);
    },
    [isFocused, setTearAudioMoving],
  );

  const updatePreviewGeometry = useCallback(
    (sourcePoints: readonly Point[], swipeDirection: 1 | -1) => {
      'worklet';
      const completed = completePreviewCutPoints(
        sourcePoints,
        pack,
        swipeDirection,
        seamY,
      );
      const mesh = makePreviewFoilMesh(completed, pack);
      previewBottomMask.set(makeBottomMask(completed, pack));
      previewMeshBase.set(mesh.vertices);
      previewMeshTextures.set(mesh.textures);
    },
    [pack, previewBottomMask, previewMeshBase, previewMeshTextures, seamY],
  );

  const resetWorklet = () => {
    'worklet';
    cards.reset();
    cancelAnimation(backdropOpacity);
    cancelAnimation(backdropZoom);
    backdropOpacity.set(0);
    backdropZoom.set(0);
    cancelAnimation(audioMovement);
    audioMovement.set(0);
    scheduleOnRN(resetAudio);
    cancelAnimation(postCutMs);
    cancelAnimation(dimOpacity);
    cancelAnimation(traceOpacity);
    cancelAnimation(cutProgress);
    points.set([]);
    topMask.set(makeEmptyPath());
    bottomMask.set(makeEmptyPath());
    previewBottomMask.set(makeEmptyPath());
    previewMeshBase.set(collapsedPreviewMesh.vertices);
    previewMeshTextures.set(collapsedPreviewMesh.textures);
    peelVisible.set(0);
    const hiddenMesh = makeCollapsedFoilMesh(pack);
    meshBase.set(hiddenMesh.vertices);
    meshTextures.set(hiddenMesh.textures);
    split.set(0);
    committed.set(0);
    active.set(0);
    direction.set(1);
    cutProgress.set(0);
    traceOpacity.set(0);
    dimOpacity.set(0);
    postCutMs.set(0);
  };

  const commitCut = (sourcePoints: readonly Point[], swipeDirection: 1 | -1) => {
    'worklet';
    cancelAnimation(audioMovement);
    audioMovement.set(0);
    cancelAnimation(cutProgress);
    const completed = completeCutPoints(sourcePoints, pack, swipeDirection);
    const mesh = makeFoilMesh(completed, pack);
    const previewMesh = makePreviewFoilMesh(completed, pack);

    points.set(completed);
    topMask.set(makeTopMask(completed, pack));
    bottomMask.set(makeBottomMask(completed, pack));
    previewBottomMask.set(makeBottomMask(completed, pack));
    previewMeshBase.set(previewMesh.vertices);
    previewMeshTextures.set(previewMesh.textures);
    meshBase.set(mesh.vertices);
    meshTextures.set(mesh.textures);
    active.set(0);
    cutProgress.set(1);
    committed.set(1);
    // The release is a single pose boundary hidden by the optical burst.
    split.set(1);
    peelVisible.set(0);
    // Keep the last folded pose under the first flash frame. Flattening the
    // cap at this boundary exposes a rectangular corner absent in the reference video.
    dimOpacity.set(0);
    postCutMs.set(0);
    postCutMs.set(
      withTiming(OPENING_DURATION_MS, {
        duration: OPENING_DURATION_MS,
        easing: Easing.linear,
        // This clock also gates replay. Motion is reduced
        // by each visual layer, without skipping the session's elapsed time.
        reduceMotion: ReduceMotion.Never,
      }),
    );
    traceOpacity.set(
      withDelay(
        100,
        withTiming(0, {
          duration: 180,
          easing: Easing.bezier(0.23, 1, 0.32, 1),
        }),
      ),
    );
    if (scripted.get() === 0) scheduleOnRN(playCutHaptic);
    scheduleOnRN(playOpenAudio);
  };

  const updateCut = (position: Point) => {
    'worklet';
    if (active.get() !== 1) return;

    const currentDirection = direction.get();
    const next = advanceTearPoints(points.get(), position, pack, currentDirection);
    if (!next) return;

    const head = next[next.length - 1];
    const progress = clamp(
      currentDirection === 1
        ? (head.x - pack.x) / pack.width
        : (pack.x + pack.width - head.x) / pack.width,
      0,
      1,
    );

    // Finger position drives the whole cut, including the last few millimetres.
    // No timing animation or velocity prediction may advance an unfinished tear.
    const advanced = progress > cutProgress.get();
    points.set(next);
    cutProgress.set(progress);
    const reachedEdge = currentDirection === 1
      ? head.x >= pack.x + pack.width
      : head.x <= pack.x;
    if (reachedEdge) {
      commitCut(next, currentDirection);
      return;
    }

    if (advanced) {
      cancelAnimation(audioMovement);
      audioMovement.set(1);
      audioMovement.set(withDelay(100, withTiming(0, {
        duration: 0,
        reduceMotion: ReduceMotion.Never,
      }), ReduceMotion.Never));
    }

    updatePreviewGeometry(next, currentDirection);
    dimOpacity.set(interpolateKeyframes(progress, DIM_PROGRESS, DIM_OPACITY));
  };

  useEffect(() => {
    resetAudio();
    cancelAnimation(audioMovement);
    audioMovement.set(0);
    cancelAnimation(postCutMs);
    cancelAnimation(dimOpacity);
    cancelAnimation(traceOpacity);
    cancelAnimation(cutProgress);
    points.set([]);
    topMask.set(makeEmptyPath());
    bottomMask.set(makeEmptyPath());
    previewBottomMask.set(makeEmptyPath());
    previewMeshBase.set(collapsedPreviewMesh.vertices);
    previewMeshTextures.set(collapsedPreviewMesh.textures);
    peelVisible.set(0);
    meshBase.set(collapsedMesh.vertices);
    meshTextures.set(collapsedMesh.textures);
    split.set(0);
    committed.set(0);
    active.set(0);
    direction.set(1);
    cutProgress.set(0);
    traceOpacity.set(0);
    dimOpacity.set(0);
    postCutMs.set(0);
  }, [
    active,
    audioMovement,
    bottomMask,
    collapsedMesh,
    collapsedPreviewMesh,
    committed,
    cutProgress,
    dimOpacity,
    direction,
    meshBase,
    meshTextures,
    peelVisible,
    points,
    postCutMs,
    previewBottomMask,
    previewMeshBase,
    previewMeshTextures,
    resetAudio,
    split,
    topMask,
    traceOpacity,
  ]);

  // Shared entry point for the real gesture and the autoPlay script.
  const beginCut = (event: Point) => {
    'worklet';
    const corridorTop = pack.y + pack.height * 0.08;
    const corridorBottom = pack.y + pack.height * 0.27;
    const edgeSlop = pack.width * 0.13;
    const nearLeft =
      event.x >= pack.x - edgeSlop && event.x <= pack.x + edgeSlop;
    const nearRight =
      event.x >= pack.x + pack.width - edgeSlop &&
      event.x <= pack.x + pack.width + edgeSlop;
    const insideCorridor = event.y >= corridorTop && event.y <= corridorBottom;

    if ((!nearLeft && !nearRight) || !insideCorridor) {
      active.set(0);
      return;
    }

    const nextDirection: 1 | -1 = nearLeft ? 1 : -1;
    const y = clamp(event.y, corridorTop, corridorBottom);
    const boundaryX = nextDirection === 1 ? pack.x : pack.x + pack.width;
    const currentX = clamp(event.x, pack.x, pack.x + pack.width);
    const initialPoints = [
      { x: boundaryX, y },
      { x: currentX, y },
    ];

    cancelAnimation(cutProgress);
    cancelAnimation(audioMovement);
    audioMovement.set(0);
    scheduleOnRN(beginTearAudio);
    // A second stroke may begin before the cancelled stroke has faded.
    // Cancel its cleanup callback before it can erase the new cut path.
    cancelAnimation(traceOpacity);
    cancelAnimation(dimOpacity);
    direction.set(nextDirection);
    active.set(1);
    peelVisible.set(1);
    points.set(initialPoints);
    updatePreviewGeometry(initialPoints, nextDirection);
    const initialProgress = Math.abs(currentX - boundaryX) / pack.width;
    cutProgress.set(initialProgress);
    traceOpacity.set(1);
    dimOpacity.set(interpolateKeyframes(initialProgress, DIM_PROGRESS, DIM_OPACITY));
  };

  // A stroke that ends short of the far edge, whether the finger lifts or a
  // touch takes over from the script: the fold springs back and the trace fades.
  const cancelCut = (velocityX: number) => {
    'worklet';
    if (active.get() !== 1) return;
    active.set(0);
    cancelAnimation(audioMovement);
    audioMovement.set(0);
    scheduleOnRN(resetAudio);
    const returnVelocity = -Math.abs(velocityX / pack.width);
    cutProgress.set(
      withSpring(
        0,
        {
          duration: 400,
          dampingRatio: 0.8,
          velocity: returnVelocity,
        },
        (finished) => {
          if (!finished) return;
          peelVisible.set(0);
          previewBottomMask.set(makeEmptyPath());
          previewMeshBase.set(collapsedPreviewMesh.vertices);
          previewMeshTextures.set(collapsedPreviewMesh.textures);
        },
      ),
    );
    dimOpacity.set(
      withTiming(0, {
        duration: 180,
        easing: Easing.bezier(0.23, 1, 0.32, 1),
      }),
    );
    traceOpacity.set(
      withTiming(0, { duration: 150, easing: Easing.linear }, (finished) => {
        if (!finished) return;
        points.set([]);
      }),
    );
  };

  const script = useAutoPlay({
    enabled: autoPlay && isFocused && !!packImage && !!cardImage && cards.assetsReady,
    pack, width, height, scripted, resets: scriptResets, idlePrime,
    beginCut, updateCut, cancelCut, advance: cards.advance, replay: resetWorklet,
  });
  useEffect(() => {
    // Without the script the scene is the user's, haptics included.
    if (!autoPlay) scripted.set(0);
  }, [autoPlay, scripted]);

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(1)
        .maxPointers(1)
        .shouldCancelWhenOutside(false)
        .onBegin((event) => {
          'worklet';
          if (autoPlay) script.touchDown();
          if (split.get() > 0.5) {
            cards.begin(event);
            return;
          }

          beginCut(event);
        })
        .onUpdate((event) => {
          'worklet';
          if (split.get() > 0.5) {
            cards.update(event);
            return;
          }
          updateCut(event);
        })
        .onEnd((event, success) => {
          'worklet';
          if (cards.dragging.get()) {
            cards.end(event, success);
            return;
          }
          // A fast swipe can deliver the edge crossing with its final event.
          // Cancellation must never turn that final coordinate into a cut.
          if (success) updateCut(event);
        })
        .onFinalize((event) => {
          'worklet';
          if (autoPlay) script.touchUp();
          cards.cancel();
          cancelCut(event.velocityX);
        }),
    // Shared values are stable; geometry values must recreate the gesture on rotation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [
      autoPlay,
      beginTearAudio,
      collapsedPreviewMesh,
      height,
      pack,
      playOpenAudio,
      resetAudio,
      seamY,
      updatePreviewGeometry,
      width,
    ],
  );

  const tap = useMemo(
    () =>
      Gesture.Tap()
        .onEnd((event, success) => {
          'worklet';
          const halfWidth = width * REFERENCE_LAYOUT.card.width / 2;
          const halfHeight = height * REFERENCE_LAYOUT.card.height / 2;
          if (success && split.get() > 0.5 &&
            Math.abs(event.x - width / 2) <= halfWidth &&
            Math.abs(event.y - height / 2) <= halfHeight) {
            cards.advance();
          }
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [height, pack, resetAudio, seamY, width],
  );

  const gesture = useMemo(() => Gesture.Exclusive(pan, tap), [pan, tap]);

  // Pack geometry reaches its final pose at 1000 ms. A scalar clamp stops
  // downstream mesh allocations while the card/session clock continues to 2000.
  const geometryMs = useDerivedValue(() => Math.min(postCutMs.get(), POST_CUT_DURATION_MS));

  const backgroundMorph = useDerivedValue(() =>
    clamp(postCutMs.get() / 133.333, 0, 1),
  );
  const previewMix = useDerivedValue(
    () =>
      peelVisible.get() * clamp((cutProgress.get() - 0.14) / 0.08, 0, 1),
  );
  const previewOpacity = useDerivedValue(
    () => (1 - split.get()) * previewMix.get(),
  );
  const intactOpacity = useDerivedValue(
    () => (1 - split.get()) * (1 - previewMix.get()),
  );
  const flatFoilOpacity = useDerivedValue(() => {
    const t = postCutMs.get();
    return split.get() * (1 - clamp(t / 33.333, 0, 1));
  });
  const meshOpacity = useDerivedValue(() => {
    const t = postCutMs.get();
    const enter = clamp(t / 33.333, 0, 1);
    const exit = 1 - clamp((t - 920) / 80, 0, 1);
    return split.get() * enter * exit;
  });
  const bodyTextureRect = useDerivedValue(() => {
    const mix = clamp((geometryMs.get() - 66.667) / 83.333, 0, 1);
    // Interpolate geometry of one opaque image. Crossfading two source-over
    // images would let 25% of the background through at the halfway point.
    return {
      x: pack.x + (settledBodyTexture.x - pack.x) * mix,
      y: pack.y,
      width: pack.width + (settledBodyTexture.width - pack.width) * mix,
      height: pack.height + (settledBodyTexture.height - pack.height) * mix,
    };
  });
  const bodyOpacity = useDerivedValue(() => {
    if (reduceMotion) {
      return split.get() * (1 - clamp((postCutMs.get() - 650) / 200, 0, 1));
    }
    // The reference video ends with a little of the package lip still at the
    // bottom. Clear that remnant while the card moves into its final presentation.
    return split.get() * (1 - clamp((postCutMs.get() - POST_CUT_DURATION_MS) / 180, 0, 1));
  });
  const bodyDeglareStrength = useDerivedValue(() =>
    split.get() * clamp((postCutMs.get() - 16.667) / 100, 0, 1),
  );
  const visibleTraceOpacity = useDerivedValue(() =>
    committed.get() ? 0 : traceOpacity.get(),
  );
  const edgeOpacity = useDerivedValue(() =>
    split.get() * (1 - clamp((postCutMs.get() - 900) / 200, 0, 1)),
  );
  const packAuraOpacity = useDerivedValue(() =>
    0.36 * (1 + 0.3 * idleMix.get() * idleAuraBreath(idlePhase.get())) *
      (1 - split.get() * clamp(postCutMs.get() / 350, 0, 1)),
  );
  const idle = useDerivedValue<number>(() =>
    active.get() === 0 && committed.get() === 0 && peelVisible.get() === 0 ? 1 : 0,
  );
  const sheen = useDerivedValue(() => idleSheen(idlePhase.get(), pack));
  const sheenStart = useDerivedValue(() => sheen.get().start);
  const sheenEnd = useDerivedValue(() => sheen.get().end);
  const sheenOpacity = useDerivedValue(() => idleMix.get() * sheen.get().opacity);
  // An invisible gradient still costs fill; draw nothing between passes.
  const sheenWidth = useDerivedValue(() => sheenOpacity.get() > 0.001 ? pack.width : 0);
  const seamHint = useDerivedValue(() => idleSeamHint(idlePhase.get(), pack));
  const seamHintOpacity = useDerivedValue(() => idleMix.get() * seamHint.get().opacity);
  const seamHintY = seamY - height * 0.0045;
  const seamHintPath = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    if (seamHintOpacity.get() <= 0.001) return builder.build();
    const hint = seamHint.get();
    builder.moveTo(hint.tail, seamHintY);
    builder.lineTo(hint.head, seamHintY);
    return builder.build();
  });
  const seamHintStart = useDerivedValue(() => ({ x: seamHint.get().tail, y: seamHintY }));
  const seamHintEnd = useDerivedValue(() => ({ x: seamHint.get().head, y: seamHintY }));
  const seamHintHead = useDerivedValue(() => seamHint.get().head);
  const punchStyle = useAnimatedStyle(() => ({
    transform: [{
      scale: liveEffects && committed.get() === 1 ? commitPunchScale(postCutMs.get()) : 1,
    }],
  }));
  const mouthDepth = useDerivedValue(() =>
    width * interpolateKeyframes(
      geometryMs.get(),
      [0, 66.667, 100, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0.0065, 0.009, 0.015, 0.025, 0.042, 0.049, 0.052, 0.059, 0.061, 0.063, 0.067],
    ),
  );
  const mouthDepthExponent = useDerivedValue(() =>
    interpolateKeyframes(
      geometryMs.get(),
      [0, 150, 300, 433.333, 550, 633.333, 766.667, 1000],
      [1.3, 1.3, 1.7, 2, 1.55, 1.3, 1.3, 1.3],
    ),
  );
  const mouthEntryOffset = useDerivedValue(() =>
    width * interpolateKeyframes(
      geometryMs.get(),
      [0, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0, 0.002, 0.01, 0.0073, -0.006, 0.002, 0.016, 0.020],
    ),
  );
  const mouthExitOffset = useDerivedValue(() =>
    width * interpolateKeyframes(
      geometryMs.get(),
      [0, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0.035, 0.037, 0.061, 0.061, 0.051, 0.061, 0.084, 0.090],
    ),
  );
  const mouthRelaxationExponent = useDerivedValue(() =>
    interpolateKeyframes(
      geometryMs.get(),
      [0, 300, 433.333, 633.333, 766.667, 833.333, 1000],
      [1.4, 1.65, 1.7, 1.5, 1.3, 1.2, 1.2],
    ),
  );
  const mouthCenterLift = useDerivedValue(() =>
    width * interpolateKeyframes(
      geometryMs.get(),
      [0, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0, -0.008, -0.008, -0.014, -0.010, -0.016, -0.012],
    ),
  );
  const cavityStrength = useDerivedValue(() =>
    interpolateKeyframes(
      geometryMs.get(),
      [0, 66.667, 100, 150],
      [0.12, 0.38, 0.82, 1],
    ),
  );
  const cavityOpacity = useDerivedValue(
    () => bodyOpacity.get() * cavityStrength.get(),
  );
  // The renderer and reflections consume the same pose. Changing motion or
  // pausing it cannot leave an independent lighting animation running.
  const bodyPose = useDerivedValue<PackLightingPose>(() => {
    const t = geometryMs.get();
    if (reduceMotion) {
      return { originX: width*0.5, originY: seamY, translateX: 0,
        translateY: height*0.015*clamp(t/220,0,1), scale: 1, rotation: 0 };
    }
    const desiredSeam =
      interpolateKeyframes(t, BODY_TIMES, BODY_SEAM_Y) * height;
    const scale = interpolateKeyframes(t, BODY_TIMES, BODY_SCALE);
    return { originX: width*0.5, originY: seamY, translateX: 0,
      translateY: desiredSeam-seamY, scale, rotation: 0 };
  });
  const bodyTransform = useDerivedValue(() => {
    const pose = bodyPose.get();
    return [{ translateX: pose.translateX }, { translateY: pose.translateY },
      { rotate: pose.rotation }, { scale: pose.scale }];
  });
  const cardTop = useDerivedValue(() =>
    cardTopAtTime(postCutMs.get(), reduceMotion) * height,
  );
  const cardOpacity = useDerivedValue(() => {
    const t = postCutMs.get();
    if (reduceMotion) return clamp((t - 700) / 200, 0, 1);
    return interpolateKeyframes(t, CARD_TIMES, CARD_OPACITY);
  });
  const cardContactShadowOpacity = useDerivedValue(
    () =>
      bodyOpacity.get() *
      cardOpacity.get() *
      cavityStrength.get() *
      0.36,
  );
  const cardShadowOpacity = useDerivedValue(() => cardOpacity.get() * 0.46);
  const backdropCardOpacity = useDerivedValue<number>(() =>
    backdropOpacity.get() > 0.001 ? 1 : 0,
  );

  const previewFoilVertices = useDerivedValue<SkPoint[]>(() =>
    deformPreviewFoil(previewMeshBase.get(), pack, cutProgress.get(), direction.get()),
  );

  const foilVertices = useDerivedValue<SkPoint[]>(() => {
    const base = meshBase.get();
    if (base.length === 0) return [];
    const t = geometryMs.get();
    if (reduceMotion) return base;

    return deformFlyingFoil(base, pack, width, height, t);
  });

  const flashPacketMask = useDerivedValue(() => makeBottomMask(
    previewFoilVertices.get().filter((_, index) => index % PREVIEW_MESH_ROWS === 0),
    pack,
  ));

  const foilEdgePath = useDerivedValue(() => {
    const vertices = foilVertices.get();
    const builder = Skia.PathBuilder.Make();
    if (vertices.length < 4) return builder.build();
    builder.moveTo(vertices[1].x, vertices[1].y);
    for (let index = 3; index < vertices.length; index += 2) {
      builder.lineTo(vertices[index].x, vertices[index].y);
    }
    return builder.build();
  });
  const mouthRestPoints = useDerivedValue<Point[]>(() => {
    // The mouth/body layers are invisible during tracing. Keeping this source
    // stable prevents a live cut update from rebuilding their entire mesh graph.
    if (committed.get() === 0) return EMPTY_POINTS;
    return relaxMouthPoints(
      points.get(),
      pack,
      mouthEntryOffset.get(),
      mouthExitOffset.get(),
      mouthRelaxationExponent.get(),
      mouthCenterLift.get(),
      direction.get(),
    );
  });
  const relaxedMouthPoints = useDerivedValue<Point[]>(() => {
    const curl = width * interpolateKeyframes(geometryMs.get(),
      [0, 433, 633, 767, 833, 900, 1000], [0, 0, 0.005, 0.015, 0.02, 0.032, 0.035]);
    return curlMouthPoints(mouthRestPoints.get(), pack, curl, direction.get());
  });
  const bodySurface = useDerivedValue(() => {
    const t = geometryMs.get();
    // Registered print landmarks sit 2–4 px lower at f150/f162/f178. Adjust
    // the substrate UVs without moving the already aligned torn silhouette.
    const offset = height * interpolateKeyframes(t,
      [0, 150, 433.333, 633.333, 766.667, 850, 900, 1000],
      [0, 0.0041, 0.005, 0.0049, 0.0113, 0.0113, 0.003, 0]);
    return makeBodyMaterialMesh(relaxedMouthPoints.get(), pack,
      seamY + width * 0.014, clamp((t - 16.667)/100, 0, 1), offset);
  });
  const bodyVertices = useDerivedValue(() => bodySurface.get().vertices);
  const bodyTextures = useDerivedValue(() => bodySurface.get().textures);
  const postCutBottomMask = useDerivedValue(() => {
    if (committed.get() === 0) return makeEmptyPath();
    return makeCavityBottomMask(relaxedMouthPoints.get(), pack);
  });
  const entryBodyWedgePath = useDerivedValue(() => {
    if (committed.get() === 0) return makeEmptyPath();
    return makeEntryBodyWedge(
      relaxedMouthPoints.get(),
      pack,
      direction.get(),
    );
  });
  const mouthFrontEdgePath = useDerivedValue(() => {
    if (committed.get() === 0) return makeEmptyPath();
    return makeCavityFrontEdge(relaxedMouthPoints.get());
  });
  const lipThickness = useDerivedValue(() => width * interpolateKeyframes(
    geometryMs.get(), [0, 150, 433, 767, 900, 1000],
    [0.011, 0.014, 0.019, 0.021, 0.021, 0.020],
  ));
  const mouthSurface = useDerivedValue(() => makeMouthWallMesh(
    relaxedMouthPoints.get(), mouthRestPoints.get(), pack, mouthDepth.get(),
    direction.get(), mouthDepthExponent.get(),
  ));
  const mouthLighting = useDerivedValue(() => deriveMouthLighting({
    vertices: mouthSurface.get().vertices,
    textures: mouthSurface.get().textures,
    pose: bodyPose.get(), opening: mouthDepth.get(), width, height,
    reducedMotion: reduceMotion,
  }));
  const cavitySpecularOpacity = useDerivedValue(() =>
    mouthLighting.get().liningLight*0.94,
  );
  const mouthSurfaceVertices = useDerivedValue(() => expandMouthMesh(
    mouthSurface.get().vertices, MOUTH_WALL_ROWS, width*0.0015,
  ));
  const mouthSurfaceOutline = useDerivedValue(() => makeMouthMeshOutline(
    mouthSurface.get().vertices,MOUTH_WALL_ROWS,
  ));
  const mouthSurfaceTextures = useDerivedValue(() => mouthSurface.get().textures);
  const rimSurface = useDerivedValue(() => makeMouthMaterialMesh(
    relaxedMouthPoints.get(), pack, lipThickness.get(), direction.get(), 1,
    true, width * 0.0015,
  ));
  const rimSurfaceVertices = useDerivedValue(() => expandMouthMesh(
    rimSurface.get().vertices,2,width*0.003,
  ));
  const rimSurfaceOutline = useDerivedValue(() => makeMouthMeshOutline(
    rimSurface.get().vertices,2,width*0.0005,
  ));
  const rimSurfaceTextures = useDerivedValue(() => rimSurface.get().textures);
  const mouthGlowCenter = useDerivedValue(() => {
    const u = mouthLighting.get().liningCenter;
    const x = pack.x + pack.width * (direction.get() === 1 ? u : 1-u);
    const ps = relaxedMouthPoints.get();
    let y = seamY;
    for (let i = 1; i < ps.length; i += 1) {
      if (x <= ps[i].x) {
        const f = clamp((x-ps[i-1].x)/Math.max(ps[i].x-ps[i-1].x,0.001),0,1);
        y = ps[i-1].y + (ps[i].y-ps[i-1].y)*f;
        break;
      }
    }
    return { x, y };
  });
  const traceGradientStart = useDerivedValue(() => ({
    x: direction.get() === 1 ? pack.x : pack.x + pack.width,
    y: seamY,
  }));
  const traceGradientEnd = useDerivedValue(() => ({
    // Keep the material transition fixed to the pack: a stroke covers its
    // whole width.
    x: direction.get() === 1 ? pack.x + pack.width : pack.x,
    y: seamY,
  }));
  const traceCoreGradientEnd = useDerivedValue(() => {
    const extension =
      pack.width *
      interpolateKeyframes(
        cutProgress.get(),
        [0, 0.72, 1],
        [0, 0.014, 0.06],
      );
    return {
      x:
        direction.get() === 1
          ? pack.x + pack.width + extension
          : pack.x - extension,
      y: seamY,
    };
  });
  const traceBodyPath = useDerivedValue(() =>
    makeSmoothPath(
      trimTraceEnd(points.get(), traceBodyWidth * 0.5 - traceSize * 0.003),
    ),
  );
  const traceCorePath = useDerivedValue(() =>
    makeSmoothPath(trimTraceEnd(points.get(), traceCoreWidth * 0.5)),
  );

  const cardWidth = width * REFERENCE_LAYOUT.card.width;
  const cardHeight = height * REFERENCE_LAYOUT.card.height;
  const cardX = width * REFERENCE_LAYOUT.card.centerX - cardWidth / 2;
  const mouthClip = useMemo(
    () => ({
      x: pack.x - width * 0.02,
      y: pack.y - width * 0.01,
      width: pack.width + width * 0.04,
      height: pack.height + width * 0.02,
    }),
    [pack, width],
  );
  const traceClip = useMemo(
    () => ({
      x: pack.x,
      y: pack.y,
      width: pack.width,
      height: pack.height,
    }),
    [pack],
  );
  const emissiveSeamPath = useMemo(() => {
    const builder = Skia.PathBuilder.Make();
    const y = seamY - height * 0.0045;
    builder.moveTo(pack.x, y);
    builder.lineTo(pack.x + pack.width, y);
    return builder.build();
  }, [height, pack, seamY]);

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={measure}>
        <Animated.View style={[StyleSheet.absoluteFill, punchStyle]}>
        <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
          <Rect x={0} y={0} width={width} height={height}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(0, height)}
              colors={['#AAC2E5', '#CADBF8', '#E7FFFF', '#E3F9FD']}
              positions={[0.08, 0.33, 0.69, 0.93]}
            />
          </Rect>
          <Rect
            x={0}
            y={0}
            width={width}
            height={height}
            opacity={backgroundMorph}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(0, height)}
              colors={['#C9D7F9', '#C5D6F7', '#9DB6DD', '#7EA1CE']}
              positions={[0.08, 0.33, 0.69, 0.93]}
            />
          </Rect>

          {/* Dim the background while tearing, keeping the pack and card clear. */}
          <Rect x={0} y={0} width={width} height={height} opacity={dimOpacity}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(0, height)}
              colors={['#050607', '#07090D', '#181827']}
              positions={[0, 0.46, 0.78]}
            />
          </Rect>

          <RoundedRect
            x={pack.x - width * 0.018}
            y={pack.y - width * 0.012}
            width={pack.width + width * 0.036}
            height={pack.height + width * 0.02}
            r={width * 0.035}
            color="#63F8FF"
            opacity={packAuraOpacity}>
            <BlurMask blur={width * 0.055} style="normal" />
          </RoundedRect>

          {/* Sum the two weighted poses on an isolated layer. Source-over
              crossfading would expose 25% of the background at the midpoint. */}
          <Group layer>
            <SkiaImage
              image={packImage}
              x={pack.x}
              y={pack.y}
              width={pack.width}
              height={pack.height}
              fit="fill"
              opacity={intactOpacity}
            />

            <Group blendMode="plus">
              <Group clip={previewBottomMask} opacity={previewOpacity}>
                <SkiaImage
                  image={packImage}
                  x={pack.x}
                  y={pack.y}
                  width={pack.width}
                  height={pack.height}
                  fit="fill"
                />
              </Group>
              {packImage && <FoldMaterial image={packImage} pack={pack}
                vertices={previewFoilVertices} textures={previewMeshTextures}
                direction={direction} opacity={previewOpacity}
                reducedMotion={reduceMotion} />}
            </Group>
            {/* Inside the pack layer, so the sheen keeps the artwork's alpha. */}
            <Rect x={pack.x} y={pack.y} width={sheenWidth} height={pack.height}
              opacity={sheenOpacity} blendMode="srcATop">
              <LinearGradient start={sheenStart} end={sheenEnd}
                colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.1)',
                  'rgba(255,255,255,0.3)', 'rgba(255,255,255,0.62)',
                  'rgba(214,250,255,0.28)', 'rgba(190,246,255,0.1)', 'rgba(255,255,255,0)']}
                positions={[0, 0.3, 0.45, 0.5, 0.55, 0.7, 1]} />
            </Rect>
          </Group>

          {packImage && <FlyingFoilMaterial image={packImage} pack={pack}
            vertices={foilVertices} textures={meshTextures} time={geometryMs}
            opacity={meshOpacity} width={width} height={height}
            reducedMotion={reduceMotion} />}
          <Path
            path={foilEdgePath}
            style="stroke"
            strokeWidth={width * 0.006}
            strokeCap="round"
            color="#48F3F6"
            opacity={meshOpacity}>
            <BlurMask blur={width * 0.009} style="normal" />
          </Path>

          <Group opacity={cavityOpacity} clip={mouthSurfaceOutline}
            origin={vec(width * 0.5, seamY)} transform={bodyTransform}>
            <MouthMaterial vertices={mouthSurfaceVertices}
              textures={mouthSurfaceTextures} lighting={mouthLighting}
              opacity={cavityStrength} indices={MOUTH_WALL_INDICES} />
          </Group>

          <CardRiseLight time={postCutMs} cardTop={cardTop} cardHeight={cardHeight}
            width={width} height={height} enabled={liveEffects} />

          <RoundedRect
            x={cardX - width * 0.004}
            y={cardTop}
            width={cardWidth + width * 0.008}
            height={cardHeight + width * 0.008}
            r={width * 0.02}
            color="#4B5474"
            opacity={cardShadowOpacity}>
            <BlurMask blur={width * 0.01} style="normal" />
          </RoundedRect>
          <SkiaImage
            image={cardImage}
            x={cardX}
            y={cardTop}
            width={cardWidth}
            height={cardHeight}
            fit="fill"
            opacity={cardOpacity}
          />

          <Group
            clip={mouthClip}
            opacity={cardContactShadowOpacity}
            origin={vec(width * 0.5, seamY)}
            transform={bodyTransform}>
            <Path
              path={mouthFrontEdgePath}
              style="stroke"
              strokeWidth={width * 0.012}
              strokeCap="round"
              strokeJoin="round"
              color="#05040B">
              <BlurMask blur={width * 0.006} style="normal" />
            </Path>
          </Group>

          <Group
            clip={postCutBottomMask}
            opacity={bodyOpacity}
            origin={vec(width * 0.5, seamY)}
            transform={bodyTransform}>
            <Path path={entryBodyWedgePath} opacity={cavityStrength}>
              <LinearGradient
                start={vec(0, seamY + width * 0.004)}
                end={vec(0, seamY + width * 0.1)}
                colors={['#B96FE7', '#B52CE8', '#D052EE']}
                positions={[0, 0.42, 1]}
              />
            </Path>
            <Vertices vertices={bodyVertices} textures={bodyTextures}
              indices={BODY_MESH_INDICES} mode="triangles">
              {packImage && <BodyMaterial image={packImage}
                textureRect={bodyTextureRect} strength={bodyDeglareStrength} />}
            </Vertices>
          </Group>

          <Group opacity={bodyOpacity}
            origin={vec(width * 0.5, seamY)} transform={bodyTransform}>
            <Group clip={rimSurfaceOutline}>
              <MouthMaterial vertices={rimSurfaceVertices}
                textures={rimSurfaceTextures} lighting={mouthLighting}
                opacity={edgeOpacity} rim />
            </Group>
            <Path path={mouthFrontEdgePath} style="stroke"
              strokeWidth={width * 0.023} strokeCap="round"
              opacity={cavitySpecularOpacity} blendMode="screen">
              <RadialGradient c={mouthGlowCenter} r={pack.width * 0.30}
                colors={['rgba(150,255,255,0.8)', 'rgba(20,242,255,0.48)',
                  'rgba(0,190,255,0)']} positions={[0, 0.25, 1]} />
              <BlurMask blur={width * 0.013} style="normal" />
            </Path>
          </Group>

          {packImage && <FoldMaterial image={packImage} pack={pack}
            vertices={previewFoilVertices} textures={previewMeshTextures}
            direction={direction} opacity={flatFoilOpacity}
            reducedMotion={reduceMotion} />}

          <CutFlash width={width} height={height} seamY={seamY}
            time={postCutMs} committed={committed}
            packetMask={flashPacketMask}
            reducedMotion={reduceMotion} />
          <CutBurst time={postCutMs} committed={committed} pack={pack}
            cutY={seamY - height * 0.003} width={width} height={height}
            enabled={liveEffects} />

          <Group clip={traceClip}>
            <Path
              path={seamHintPath}
              style="stroke"
              strokeWidth={width * 0.026}
              strokeCap="round"
              opacity={seamHintOpacity}>
              <LinearGradient
                start={seamHintStart}
                end={seamHintEnd}
                colors={['rgba(120,240,255,0)', 'rgba(150,246,255,0.5)', 'rgba(255,255,255,0.95)']}
                positions={[0, 0.65, 1]}
              />
              <BlurMask blur={width * 0.012} style="normal" />
            </Path>
            <Circle cx={seamHintHead} cy={seamHintY} r={width * 0.034}
              color="#9CF8FF" opacity={seamHintOpacity}>
              <BlurMask blur={width * 0.03} style="normal" />
            </Circle>
            <Circle cx={seamHintHead} cy={seamHintY} r={width * 0.017}
              color="#FFFFFF" opacity={seamHintOpacity}>
              <BlurMask blur={width * 0.008} style="normal" />
            </Circle>
            <Path
              path={emissiveSeamPath}
              style="stroke"
              strokeWidth={width * 0.016}
              strokeCap="butt"
              color="rgba(214,38,238,0.55)"
              opacity={visibleTraceOpacity}>
              <BlurMask blur={width * 0.003} style="normal" />
            </Path>
            <Path
              path={emissiveSeamPath}
              style="stroke"
              strokeWidth={width * 0.008}
              strokeCap="butt"
              color="#F006FA"
              opacity={visibleTraceOpacity}
            />
            <Path
              path={traceBodyPath}
              style="stroke"
              strokeWidth={glowWidth}
              strokeCap="round"
              strokeJoin="round"
              color="#E049F1"
              opacity={visibleTraceOpacity}>
              <BlurMask blur={traceSize * 0.032} style="normal" />
            </Path>
            <Path
              path={traceBodyPath}
              style="stroke"
              strokeWidth={traceBodyWidth}
              strokeCap="round"
              strokeJoin="round"
              opacity={visibleTraceOpacity}>
              <LinearGradient
                start={traceGradientStart}
                end={traceGradientEnd}
                colors={[
                  '#CF49F1',
                  '#CF49F1',
                  '#D34AF2',
                  '#D94EF2',
                  '#DB4EF2',
                  '#D54AF1',
                ]}
                positions={[0, 0.62, 0.7, 0.82, 0.96, 1]}
              />
              <BlurMask blur={traceSize * 0.0045} style="normal" />
            </Path>
            <Path
              path={traceBodyPath}
              style="stroke"
              strokeWidth={traceFillWidth}
              strokeCap="round"
              strokeJoin="round"
              opacity={visibleTraceOpacity}>
              <LinearGradient
                start={traceGradientStart}
                end={traceGradientEnd}
                colors={[
                  '#D04AF1',
                  '#D04AF1',
                  '#E66EF6',
                  '#FBC5FD',
                  '#FAC1FD',
                  '#EE7AF6',
                ]}
                positions={[0, 0.62, 0.7, 0.82, 0.96, 1]}
              />
              <BlurMask blur={traceSize * 0.002} style="normal" />
            </Path>
            <Path
              path={traceCorePath}
              style="stroke"
              strokeWidth={traceCoreWidth}
              strokeCap="round"
              strokeJoin="round"
              opacity={visibleTraceOpacity}>
              <LinearGradient
                start={traceGradientStart}
                end={traceCoreGradientEnd}
                colors={[
                  'rgba(255,255,255,1)',
                  'rgba(255,255,255,1)',
                  'rgba(255,252,255,0.98)',
                  'rgba(255,248,255,0.82)',
                  'rgba(255,245,255,0.58)',
                  'rgba(255,240,255,0.32)',
                  'rgba(255,238,255,0.05)',
                  'rgba(255,232,255,0)',
                ]}
                positions={[0, 0.52, 0.58, 0.64, 0.69, 0.735, 0.82, 1]}
              />
              <BlurMask blur={traceSize * 0.004} style="normal" />
            </Path>
          </Group>
          <TearSparks points={points} active={active} direction={direction}
            width={width} enabled={liveEffects && isFocused} />

        </Canvas>
        </Animated.View>
        <IdleClockDriver phase={idlePhase} mix={idleMix} idle={idle}
          enabled={liveEffects && isFocused} prime={idlePrime} />

        <CollectionCardBackdrop
          active={isFocused}
          height={height}
          opacityProgress={backdropOpacity}
          rewind={scriptResets}
          width={width}
          zoomProgress={backdropZoom}
        />
        <CardRevealScene state={cards} width={width} height={height} top={cardTop}
          opacity={backdropCardOpacity} reducedMotion={reduceMotion}
          onReplay={resetWorklet} scripted={scripted} bottomInset={insets.bottom} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    overflow: 'hidden',
  },
});
