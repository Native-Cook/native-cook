import { cardRevealCue, type CardAudioCue, type CardRarity } from './card-rarity';

/** The small surface of an Expo Audio player that the controller relies on. */
export type PackAudioPlayer = {
  readonly isLoaded: boolean;
  readonly currentTime: number;
  loop: boolean;
  prepareToPlay?(): void | Promise<void>;
  play(): void | boolean;
  pause(): void;
  stopImmediately?(): void;
  seekTo(seconds: number): Promise<void>;
  remove(): void;
};

export type PackAudioActions = {
  beginTear(): void;
  setTearing(moving: boolean): void;
  commitCut(): void;
  beginCardAdvance(fromIndex: number, requestedAt?: number): void;
  revealCard(index: number, rarity: CardRarity, requestedAt?: number): void;
  reset(): void;
};

/** Never queue an effect behind loading or let an old seek start a later scene. */
export function createPackAudioController(
  swipe: PackAudioPlayer,
  open: PackAudioPlayer,
  background: PackAudioPlayer,
  now: () => number = Date.now,
  cardPlayers: Partial<Record<CardAudioCue, PackAudioPlayer>> = {},
) {
  let enabled = false;
  let disposed = false;
  let phase: 'idle' | 'tearing' | 'opened' = 'idle';
  let moving = false;
  let freshSwipe = true;
  const channel = (player: PackAudioPlayer) => ({
    player,
    version: 0,
    queue: Promise.resolve(),
  });
  const tearChannel = channel(swipe);
  const openChannel = channel(open);
  const backgroundChannel = channel(background);
  const cardChannels = Object.fromEntries(Object.entries(cardPlayers)
    .map(([cue, player]) => [cue, channel(player)])) as Partial<Record<CardAudioCue, typeof tearChannel>>;
  let lastAdvanceIndex = -1;
  let lastRevealIndex = -1;
  let backgroundPlaying = false;
  let backgroundPending = false;
  let backgroundRefreshRequested = false;
  swipe.loop = true;
  open.loop = false;
  background.loop = true;
  for (const player of Object.values(cardPlayers)) player.loop = false;

  const stop = (target: typeof tearChannel, immediate = false) => {
    target.version += 1;
    // A native player may already have been invalidated by the OS.
    try {
      if (immediate && target.player.stopImmediately) target.player.stopImmediately();
      else target.player.pause();
    } catch { /* Playback must not block the gesture. */ }
  };

  const start = (target: typeof tearChannel, rewind: boolean, requestedAt = now()) => {
    if (!enabled || disposed || !target.player.isLoaded) return;
    const version = ++target.version;
    const current = () => enabled && !disposed && version === target.version &&
      now() - requestedAt <= 120;
    // Serialize seeks: an older seek cannot jump a new attempt's playback head.
    target.queue = target.queue.then(async () => {
      if (!current()) return;
      const preparation = target.player.prepareToPlay?.();
      if (preparation) await preparation;
      if (!current()) return;
      if (rewind && target.player.currentTime !== 0) await target.player.seekTo(0);
      if (!current()) return;
      const played = target.player.play();
      if (target === tearChannel && played !== false) freshSwipe = false;
    }).catch(() => {
      // Missing audio or a platform interruption should leave the animation usable.
    });
  };

  // Music follows screen/app lifecycle only; no gesture path calls this channel.
  const refreshBackground = () => {
    if (!enabled || disposed || backgroundPlaying) return;
    if (backgroundPending) {
      backgroundRefreshRequested = true;
      return;
    }
    if (!background.isLoaded) return;
    const version = ++backgroundChannel.version;
    backgroundPending = true;
    backgroundRefreshRequested = false;
    const current = () => enabled && !disposed && version === backgroundChannel.version;
    backgroundChannel.queue = backgroundChannel.queue.then(async () => {
      if (!current()) return;
      const preparation = background.prepareToPlay?.();
      if (preparation) await preparation;
      if (!current()) return;
      // A legitimate initial load/unlock can take longer than an effect's 120ms window.
      // Resume the existing playback head, including after an app background pause.
      backgroundPlaying = background.play() !== false;
    }).catch(() => {
      // Asset/unlock notifications can retry a failed start while the screen is active.
    }).finally(() => {
      if (version !== backgroundChannel.version) return;
      backgroundPending = false;
      if (backgroundRefreshRequested) refreshBackground();
    });
  };

  const stopBackground = () => {
    backgroundPlaying = false;
    backgroundPending = false;
    backgroundRefreshRequested = false;
    stop(backgroundChannel, true);
  };

  const resetEffects = (immediate = false) => {
    if (disposed) return;
    phase = 'idle';
    moving = false;
    freshSwipe = true;
    stop(tearChannel, immediate);
    stop(openChannel, immediate);
    for (const target of Object.values(cardChannels)) stop(target, immediate);
    lastAdvanceIndex = -1;
    lastRevealIndex = -1;
  };
  const reset = () => resetEffects();

  return {
    beginTear() {
      if (!enabled || disposed) return;
      reset();
      phase = 'tearing';
    },
    setTearing(nextMoving: boolean) {
      if (!enabled || disposed || phase !== 'tearing' || nextMoving === moving) return;
      moving = nextMoving;
      if (moving) start(tearChannel, freshSwipe);
      else stop(tearChannel);
    },
    commitCut() {
      if (!enabled || disposed || phase !== 'tearing') return;
      phase = 'opened';
      moving = false;
      stop(tearChannel);
      start(openChannel, true);
    },
    beginCardAdvance(fromIndex: number, requestedAt = now()) {
      if (!enabled || disposed || !Number.isInteger(fromIndex) || fromIndex < 0 ||
        fromIndex <= lastAdvanceIndex || fromIndex < lastRevealIndex || now() - requestedAt > 120) return;
      lastAdvanceIndex = fromIndex;
      // End the previous card's tail before accepting a new swipe.
      for (const target of Object.values(cardChannels)) stop(target);
      const target = cardChannels['next-card'];
      if (target) start(target, true, requestedAt);
    },
    revealCard(index: number, rarity: CardRarity, requestedAt = now()) {
      if (!enabled || disposed || !Number.isInteger(index) || index < 0 ||
        index <= lastRevealIndex || index <= lastAdvanceIndex || now() - requestedAt > 120) return;
      lastRevealIndex = index;
      for (const [cue, target] of Object.entries(cardChannels)) {
        if (cue !== 'next-card') stop(target);
      }
      const target = cardChannels[cardRevealCue(rarity)];
      if (target) start(target, true, requestedAt);
    },
    reset,
    refreshBackground,
    setEnabled(nextEnabled: boolean) {
      if (disposed) return;
      enabled = nextEnabled;
      if (enabled) refreshBackground();
      else {
        resetEffects(true);
        stopBackground();
      }
    },
    dispose() {
      if (disposed) return;
      resetEffects(true);
      stopBackground();
      disposed = true;
      enabled = false;
      for (const player of [swipe, open, background, ...Object.values(cardPlayers)]) {
        try { player.remove(); } catch { /* Already released. */ }
      }
    },
  };
}

export type PackAudioController = ReturnType<typeof createPackAudioController>;
