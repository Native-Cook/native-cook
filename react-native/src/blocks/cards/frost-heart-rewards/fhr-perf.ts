import { createWorkletRuntime, runOnRuntimeSync, runOnUIAsync, scheduleOnRuntime, type WorkletRuntime } from 'react-native-worklets';

// The nine ranges follow sceneAt: claim at .6, spotlight at 1.7, cut at 2.4,
// display at 3.8, magnetic close at 5.4, turn at 6.4, hold at 7.4, return at 8.9.
const PHASES = [
  { name: 'rest', start: 0, end: .6 },
  { name: 'claim', start: .6, end: 1.7 },
  { name: 'spotlight', start: 1.7, end: 2.4 },
  { name: 'cut', start: 2.4, end: 3.8 },
  { name: 'display case', start: 3.8, end: 5.4 },
  { name: 'close', start: 5.4, end: 6.4 },
  { name: 'turn', start: 6.4, end: 7.4 },
  { name: 'hold', start: 7.4, end: 8.9 },
  { name: 'return', start: 8.9, end: 10 },
] as const;
const CAPACITY = 4096;
const GPU_IN_FLIGHT_LIMIT = 4;
const DURATION_MS = 10000;

type Samples = { ms: Float64Array; seconds: Float64Array; count: number; overflow: number };
type RawSamples = { ms: number[]; seconds: number[]; overflow: number };
type GPUSlot = { index: number; started: number; busy: boolean; done: () => void; failed: () => void };
type UIProbe = {
  active: boolean;
  startedAtEpochMs: number;
  elapsedMs: number;
  ui: Samples;
  gpu: Samples;
  gpuSlots: GPUSlot[];
  gpuAvailability: 'unknown' | 'available' | 'unavailable' | 'failed';
  gpuSkippedBusy: number;
  gpuFailed: number;
  gpuPending: number;
};
type PerfGlobal = typeof globalThis & { __fhrPerfUI?: UIProbe };
type CaptureStart = { startedAtEpochMs: number; durationMs: number };
type JSProbe = CaptureStart & {
  active: boolean;
  samples: Samples;
  raf: number;
  previousTimestamp: number;
  previousSeconds: number;
  gpu: Samples;
  gpuDispatch: Samples;
  gpuSlots: GPUSlot[];
  gpuPending: number;
  gpuFailed: number;
  gpuSkippedBusy: number;
  gpuClockErrors: number;
  gpuWorkerForwarded: number;
  acceptingGPU: boolean;
  gpuLateAfterStop: number;
};
type GPUWorkerProbe = CaptureStart & {
  active: boolean;
  generation: number;
  queue: GPUQueue | null;
  gpu: Samples;
  dispatch: Samples;
  slots: GPUSlot[];
  received: number;
  pending: number;
  failed: number;
  skippedBusy: number;
  clockErrors: number;
  rejected: number;
};
type GPUWorkerGlobal = typeof globalThis & { __fhrPerfGPUWorker?: GPUWorkerProbe };

// Null until explicitly enabled; there are no buffers, RAFs or timers while off.
let jsProbe: JSProbe | null = null;
let gpuObserver: { queue: GPUQueue; generation: number; install: () => void } | null = null;
let gpuWorkerRuntime: WorkletRuntime | null = null;
let gpuWorkerError: string | null = null;

/** The RN timer pump is independent of Worklets' RAF-backed UI setTimeout.
 * This observer retains the UI submit epoch and includes dispatch overhead;
 * it does not subtract a frame, infer zero work, or replace the original metric.
 */
export function installFhrGPUObserver(queue: GPUQueue, generation: number, install: () => void) {
  if (__DEV__) gpuObserver = { queue, generation, install };
}
export function removeFhrGPUObserver(generation: number) {
  if (!__DEV__ || gpuObserver?.generation !== generation) return;
  gpuObserver = null;
  if (gpuWorkerRuntime) scheduleOnRuntime(gpuWorkerRuntime, (id: number) => {
    'worklet';
    const probe = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
    if (probe?.generation === id) { probe.active = false; probe.queue = null; }
  }, generation);
}
function makeRNGPUSlot(probe: JSProbe): GPUSlot {
  const slot: GPUSlot = { index: -1, started: 0, busy: false, done: () => {}, failed: () => {} };
  slot.done = () => {
    if (!slot.busy) return;
    const elapsed = Date.now() - slot.started;
    if (elapsed < 0) { probe.gpuClockErrors++; probe.gpuFailed++; }
    else probe.gpu.ms[slot.index] = elapsed;
    slot.busy = false; probe.gpuPending--;
  };
  slot.failed = () => {
    if (!slot.busy) return;
    probe.gpuFailed++; probe.gpuPending--; slot.busy = false;
  };
  return slot;
}
export function recordFhrGPUFromRN(loopSeconds: number, submitStarted: number, generation: number) {
  if (!__DEV__ || !submitStarted || !jsProbe || gpuObserver?.generation !== generation) return;
  const probe = jsProbe;
  // UI→RN notifications can arrive after cancellation or a later capture reset.
  // Keep those out of the new capture and out of worker-forwarded accounting.
  if (submitStarted < probe.startedAtEpochMs) return;
  if (!probe.acceptingGPU) { probe.gpuLateAfterStop++; return; }
  if (gpuWorkerRuntime && !gpuWorkerError) {
    probe.gpuWorkerForwarded++;
    scheduleOnRuntime(gpuWorkerRuntime, recordFhrGPUOnWorker, loopSeconds, submitStarted, generation, probe.startedAtEpochMs);
  }
  const dispatch = Date.now() - submitStarted;
  // Hermes runtimes have different performance.now origins. Epoch milliseconds
  // are shared; retain their 1 ms resolution and never subtract dispatch delay.
  if (dispatch < 0 || dispatch > DURATION_MS) { probe.gpuClockErrors++; return; }
  if (probe.gpu.count >= CAPACITY) { probe.gpu.overflow++; return; }
  const slot = probe.gpuSlots.find(candidate => !candidate.busy);
  if (!slot) { probe.gpuSkippedBusy++; return; }
  append(probe.gpuDispatch, loopSeconds, dispatch);
  slot.index = probe.gpu.count; slot.started = submitStarted; slot.busy = true;
  append(probe.gpu, loopSeconds, -1); probe.gpuPending++;
  try { void gpuObserver.queue.onSubmittedWorkDone().then(slot.done, slot.failed); }
  catch { slot.failed(); }
}

function makeSamples(): Samples {
  'worklet';
  return { ms: new Float64Array(CAPACITY), seconds: new Float64Array(CAPACITY), count: 0, overflow: 0 };
}

function append(samples: Samples, seconds: number, milliseconds: number) {
  'worklet';
  if (samples.count >= CAPACITY) { samples.overflow++; return; }
  samples.seconds[samples.count] = seconds;
  samples.ms[samples.count] = milliseconds;
  samples.count++;
}

function makeGPUWorkerSlot(probe: GPUWorkerProbe): GPUSlot {
  'worklet';
  const slot: GPUSlot = { index: -1, started: 0, busy: false, done: () => {}, failed: () => {} };
  slot.done = () => {
    if (!slot.busy) return;
    const elapsed = Date.now() - slot.started;
    if (elapsed < 0) { probe.clockErrors++; probe.failed++; }
    else probe.gpu.ms[slot.index] = elapsed;
    slot.busy = false;
    probe.pending--;
  };
  slot.failed = () => {
    if (!slot.busy) return;
    probe.failed++;
    probe.pending--;
    slot.busy = false;
  };
  return slot;
}

/** The worker's native event-loop timers are independent of UI RAF and RN JSTimers. */
function prepareFhrGPUWorker(queue: GPUQueue, generation: number, install: () => void) {
  'worklet';
  install();
  const existing = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
  if (existing) {
    if (existing.pending !== 0) throw new Error('The previous GPU worker capture still has pending callbacks');
    existing.active = false;
    existing.queue = queue;
    existing.generation = generation;
    return;
  }
  const probe: GPUWorkerProbe = {
    startedAtEpochMs: 0, durationMs: DURATION_MS, active: false, generation, queue,
    gpu: makeSamples(), dispatch: makeSamples(), slots: [],
    received: 0, pending: 0, failed: 0, skippedBusy: 0, clockErrors: 0, rejected: 0,
  };
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) probe.slots.push(makeGPUWorkerSlot(probe));
  (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker = probe;
}

function startFhrGPUWorker(start: CaptureStart) {
  'worklet';
  const probe = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
  if (!probe?.queue) throw new Error('The GPU worker was not prepared');
  if (probe.pending !== 0) throw new Error('The previous GPU worker capture still has pending callbacks');
  probe.startedAtEpochMs = start.startedAtEpochMs;
  probe.durationMs = start.durationMs;
  probe.gpu.count = 0;
  probe.gpu.overflow = 0;
  probe.dispatch.count = 0;
  probe.dispatch.overflow = 0;
  probe.received = 0;
  probe.failed = 0;
  probe.skippedBusy = 0;
  probe.clockErrors = 0;
  probe.rejected = 0;
  probe.active = true;
}

function recordFhrGPUOnWorker(loopSeconds: number, submitStarted: number, generation: number, capture: number) {
  'worklet';
  const probe = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
  if (!probe?.active || !probe.queue || probe.generation !== generation || probe.startedAtEpochMs !== capture) return;
  probe.received++;
  const dispatch = Date.now() - submitStarted;
  // Different Hermes runtimes have different performance.now origins. Preserve
  // shared epoch milliseconds, their 1 ms resolution, and every dispatch delay.
  if (dispatch < 0 || dispatch > DURATION_MS) { probe.clockErrors++; probe.rejected++; return; }
  if (probe.gpu.count >= CAPACITY) { probe.gpu.overflow++; return; }
  let free = -1;
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) if (!probe.slots[i].busy) { free = i; break; }
  if (free < 0) { probe.skippedBusy++; return; }
  const slot = probe.slots[free];
  append(probe.dispatch, loopSeconds, dispatch);
  slot.index = probe.gpu.count;
  slot.started = submitStarted;
  slot.busy = true;
  append(probe.gpu, loopSeconds, -1);
  probe.pending++;
  try { void probe.queue.onSubmittedWorkDone().then(slot.done, slot.failed); }
  catch { slot.failed(); }
}

// Each callback must close over a separate function activation. A loop-local
// binding can alias after the mobile/worklet transform and strand the first slots.
function makeGPUSlot(probe: UIProbe): GPUSlot {
  'worklet';
  const slot: GPUSlot = { index: -1, started: 0, busy: false, done: () => {}, failed: () => {} };
  slot.done = () => {
    if (!slot.busy) return;
    probe.gpu.ms[slot.index] = performance.now() - slot.started;
    slot.busy = false;
    probe.gpuPending--;
  };
  slot.failed = () => {
    if (!slot.busy) return;
    probe.gpuFailed++;
    probe.gpuPending--;
    probe.gpuAvailability = 'failed';
    slot.busy = false;
  };
  return slot;
}

/** Called once on the UI runtime after the demo has been reset to its resting frame. */
export function startFhrPerfUI(): CaptureStart {
  'worklet';
  const startedAtEpochMs = Date.now();
  if (!__DEV__) return { startedAtEpochMs, durationMs: 0 };
  const probe: UIProbe = {
    active: true, startedAtEpochMs, elapsedMs: 0,
    ui: makeSamples(), gpu: makeSamples(), gpuSlots: [],
    gpuAvailability: 'unknown', gpuSkippedBusy: 0, gpuFailed: 0, gpuPending: 0,
  };
  // Reuse these callbacks for the whole capture. Native WebGPU still creates its
  // completion Promise; there is no per-submit JS closure or cross-thread hop.
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) probe.gpuSlots.push(makeGPUSlot(probe));
  (globalThis as PerfGlobal).__fhrPerfUI = probe;
  return { startedAtEpochMs, durationMs: DURATION_MS };
}

/** No shared-value notification, React state or runOnJS on the measured path. */
export function recordFhrUIFrame(milliseconds: number | null, loopSeconds: number) {
  'worklet';
  if (!__DEV__) return;
  const probe = (globalThis as PerfGlobal).__fhrPerfUI;
  if (!probe?.active || milliseconds === null) return;
  append(probe.ui, loopSeconds, milliseconds);
  probe.elapsedMs += milliseconds;
  if (probe.elapsedMs >= DURATION_MS - .0001) probe.active = false;
}

/** Timestamp immediately before submit; zero when the development probe is off. */
export function fhrPerfSubmitStarted() {
  'worklet';
  return __DEV__ && (globalThis as PerfGlobal).__fhrPerfUI?.active ? performance.now() : 0;
}

/** Queue completion latency includes queueing and callback delivery, not hardware GPU time. */
export function recordFhrGPUFrame(queue: GPUQueue, loopSeconds: number, submitStarted: number) {
  'worklet';
  if (!__DEV__ || submitStarted === 0) return;
  const probe = (globalThis as PerfGlobal).__fhrPerfUI;
  if (!probe?.active || probe.gpuAvailability === 'failed' || probe.gpuAvailability === 'unavailable') return;
  if (typeof queue.onSubmittedWorkDone !== 'function') { probe.gpuAvailability = 'unavailable'; return; }
  probe.gpuAvailability = 'available';
  if (probe.gpu.count >= CAPACITY) { probe.gpu.overflow++; return; }
  let free = -1;
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) if (!probe.gpuSlots[i].busy) { free = i; break; }
  if (free < 0) { probe.gpuSkippedBusy++; return; }
  const slot = probe.gpuSlots[free];
  slot.index = probe.gpu.count;
  slot.started = submitStarted;
  slot.busy = true;
  append(probe.gpu, loopSeconds, -1);
  probe.gpuPending++;
  try { void queue.onSubmittedWorkDone().then(slot.done, slot.failed); }
  catch { slot.failed(); }
}

/** Initialize the optional GPU probe before starting the measured UI loop. */
export function prepareFhrPerfGPU() {
  if (!__DEV__) return false;
  gpuWorkerError = null;
  if (!gpuObserver) { gpuWorkerError = 'FHR GPU observer is unavailable'; return false; }
  try {
    // Call before resetting/unpinning the UI capture. Runtime creation and all
    // worker sample-buffer/callback allocation then stay outside the measurement.
    gpuWorkerRuntime ??= createWorkletRuntime({ name: 'FHR GPU probe', enableEventLoop: true });
    runOnRuntimeSync(gpuWorkerRuntime, prepareFhrGPUWorker, gpuObserver.queue, gpuObserver.generation, gpuObserver.install);
    return true;
  } catch (error) {
    gpuWorkerError = error instanceof Error ? error.message : String(error);
    return false;
  }
}

/** JS RAF timestamps are independent of UI callbacks; phase labels use the UI start wall clock. */
export function startFhrPerfJS(start: CaptureStart) {
  if (!__DEV__) return start;
  if (jsProbe) cancelAnimationFrame(jsProbe.raf);
  const probe: JSProbe = {
    ...start, active: true, samples: makeSamples(), raf: 0,
    previousTimestamp: -1, previousSeconds: -1,
    gpu: makeSamples(), gpuDispatch: makeSamples(), gpuSlots: [],
    gpuPending: 0, gpuFailed: 0, gpuSkippedBusy: 0, gpuClockErrors: 0,
    gpuWorkerForwarded: 0,
    acceptingGPU: true, gpuLateAfterStop: 0,
  };
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) probe.gpuSlots.push(makeRNGPUSlot(probe));
  jsProbe = probe;
  // Older callers still get a valid worker observer, but should call the explicit
  // preflight before UI reset to keep its first runtime creation outside capture.
  if (!gpuWorkerRuntime) prepareFhrPerfGPU();
  if (gpuWorkerRuntime && !gpuWorkerError) {
    try {
      runOnRuntimeSync(gpuWorkerRuntime, startFhrGPUWorker, start);
    } catch (error) { gpuWorkerError = error instanceof Error ? error.message : String(error); }
  }
  const onFrame = (timestamp: number) => {
    if (!probe.active) return;
    const seconds = (Date.now() - probe.startedAtEpochMs) / 1000;
    if (probe.previousTimestamp >= 0 && probe.previousSeconds < 10) {
      append(probe.samples, Math.max(0, probe.previousSeconds), timestamp - probe.previousTimestamp);
    }
    probe.previousTimestamp = timestamp;
    probe.previousSeconds = seconds;
    if (seconds >= 10) { probe.active = false; return; }
    probe.raf = requestAnimationFrame(onFrame);
  };
  probe.raf = requestAnimationFrame(onFrame);
  return start;
}

export function stopFhrPerf() {
  if (!__DEV__) return Promise.resolve();
  const capture = jsProbe?.startedAtEpochMs;
  if (jsProbe) { jsProbe.active = false; jsProbe.acceptingGPU = false; cancelAnimationFrame(jsProbe.raf); }
  if (gpuWorkerRuntime && capture !== undefined) scheduleOnRuntime(gpuWorkerRuntime, (startedAt: number) => {
    'worklet';
    const probe = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
    if (probe?.startedAtEpochMs === startedAt) probe.active = false;
  }, capture);
  return runOnUIAsync(() => {
    'worklet';
    const probe = (globalThis as PerfGlobal).__fhrPerfUI;
    if (probe) probe.active = false;
  });
}

function copySamples(samples: Samples): RawSamples {
  'worklet';
  return {
    ms: Array.from(samples.ms.subarray(0, samples.count)),
    seconds: Array.from(samples.seconds.subarray(0, samples.count)),
    overflow: samples.overflow,
  };
}

function readFhrGPUWorker(capture: number) {
  'worklet';
  const probe = (globalThis as GPUWorkerGlobal).__fhrPerfGPUWorker;
  if (!probe || probe.startedAtEpochMs !== capture) return null;
  let busy = 0, unresolved = 0;
  for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) if (probe.slots[i].busy) busy++;
  for (let i = 0; i < probe.gpu.count; i++) if (probe.gpu.ms[i] < 0) unresolved++;
  return {
    gpu: copySamples(probe.gpu), dispatch: copySamples(probe.dispatch),
    received: probe.received, pending: probe.pending, failed: probe.failed,
    skippedBusy: probe.skippedBusy, clockErrors: probe.clockErrors,
    rejected: probe.rejected, busy, unresolved,
  };
}

function statistics(raw: RawSamples, start = 0, end = 10) {
  const sorted = raw.ms.filter((ms, index) => ms >= 0 && raw.seconds[index] >= start && raw.seconds[index] < end).sort((a, b) => a - b);
  const percentile = (p: number) => sorted.length ? sorted[Math.max(0, Math.ceil(sorted.length * p) - 1)] : null;
  return {
    samples: sorted.length, p50: percentile(.5), p95: percentile(.95), p99: percentile(.99), max: percentile(1),
    over17_5ms: sorted.filter(ms => ms > 17.5).length, over25ms: sorted.filter(ms => ms > 25).length,
  };
}

/** Export only on demand, after capture; copying/sorting never runs in the measured loop. */
export async function fhrPerfResults() {
  if (!__DEV__) return null;
  const result = await runOnUIAsync(() => {
    'worklet';
    const probe = (globalThis as PerfGlobal).__fhrPerfUI;
    if (!probe) return null;
    let busy = 0, unresolved = 0;
    for (let i = 0; i < GPU_IN_FLIGHT_LIMIT; i++) if (probe.gpuSlots[i].busy) busy++;
    for (let i = 0; i < probe.gpu.count; i++) if (probe.gpu.ms[i] < 0) unresolved++;
    return {
      active: probe.active, startedAtEpochMs: probe.startedAtEpochMs, elapsedMs: probe.elapsedMs,
      ui: copySamples(probe.ui), gpu: copySamples(probe.gpu),
      gpuAvailability: probe.gpuAvailability, gpuSkippedBusy: probe.gpuSkippedBusy,
      gpuFailed: probe.gpuFailed, gpuPending: probe.gpuPending,
      gpuBusySlots: busy, gpuUnresolvedSamples: unresolved,
      gpuAccountingValid: busy === probe.gpuPending && unresolved === probe.gpuPending + probe.gpuFailed,
    };
  });
  if (!result) return null;
  const js = jsProbe?.startedAtEpochMs === result.startedAtEpochMs ? copySamples(jsProbe.samples) : { ms: [], seconds: [], overflow: 0 };
  const rn = jsProbe?.startedAtEpochMs === result.startedAtEpochMs ? jsProbe : null;
  const rnGPU = rn ? copySamples(rn.gpu) : { ms: [], seconds: [], overflow: 0 };
  const rnDispatch = rn ? copySamples(rn.gpuDispatch) : { ms: [], seconds: [], overflow: 0 };
  const rnBusy = rn?.gpuSlots.filter(slot => slot.busy).length ?? 0;
  const rnUnresolved = rnGPU.ms.filter(ms => ms < 0).length;
  let worker: ReturnType<typeof readFhrGPUWorker> = null;
  if (gpuWorkerRuntime && !gpuWorkerError) {
    try { worker = runOnRuntimeSync(gpuWorkerRuntime, readFhrGPUWorker, result.startedAtEpochMs); }
    catch (error) { gpuWorkerError = error instanceof Error ? error.message : String(error); }
  }
  const workerGPU = worker?.gpu ?? { ms: [], seconds: [], overflow: 0 };
  const workerDispatch = worker?.dispatch ?? { ms: [], seconds: [], overflow: 0 };
  const workerErrors: string[] = [];
  if (!worker) workerErrors.push(gpuWorkerError ?? 'Worker capture is unavailable');
  else {
    if (worker.clockErrors) workerErrors.push('Epoch clock moved backwards or dispatch exceeded the capture duration');
    if (worker.busy !== worker.pending) workerErrors.push('Busy slots do not match pending requests');
    if (worker.unresolved !== worker.pending + worker.failed) workerErrors.push('Unresolved samples do not match pending and failed requests');
    if (worker.received !== (rn?.gpuWorkerForwarded ?? 0)) workerErrors.push('Worker received count does not match forwarded submissions');
    if (worker.received !== worker.gpu.ms.length + worker.skippedBusy + worker.gpu.overflow + worker.rejected) workerErrors.push('Worker submissions are not fully accounted for');
  }
  return {
    version: 1, durationMs: DURATION_MS,
    startedAtEpochMs: result.startedAtEpochMs,
    active: result.active || !!jsProbe?.active,
    complete: !result.active && result.elapsedMs >= DURATION_MS - .0001,
    uiElapsedMs: result.elapsedMs,
    units: 'milliseconds',
    attribution: 'UI: interval start scene time. GPU: submit scene time. JS: interval start wall time anchored to the UI reset; first RAF has no interval.',
    gpuMetric: 'queue submit-to-onSubmittedWorkDone completion latency, including queue backlog and callback/pump delivery; not a hardware GPU timestamp or presentation time',
    gpuRNMetric: 'UI submit epoch to RN-runtime queue completion callback (Date.now, 1 ms resolution); includes RN dispatch and timer pump, independent of Worklets RAF timer. No hardware timestamps available on this simulator.',
    gpuRNAccounting: { pending: rn?.gpuPending ?? 0, busy: rnBusy, unresolved: rnUnresolved, failed: rn?.gpuFailed ?? 0, skippedBusy: rn?.gpuSkippedBusy ?? 0, clockErrors: rn?.gpuClockErrors ?? 0, valid: !!rn && rn.gpuClockErrors === 0 && rnBusy === rn.gpuPending && rnUnresolved === rn.gpuPending + rn.gpuFailed },
    gpuWorkerMetric: 'UI submit epoch to dedicated worker-runtime queue completion callback (Date.now, 1 ms resolution); includes UI-to-RN-to-worker dispatch and the native event-loop pump. No delay is subtracted. This is not a hardware GPU timestamp or presentation time.',
    gpuWorkerAccounting: {
      forwarded: rn?.gpuWorkerForwarded ?? 0, received: worker?.received ?? 0,
      lateAfterStop: rn?.gpuLateAfterStop ?? 0,
      pending: worker?.pending ?? 0, busy: worker?.busy ?? 0, unresolved: worker?.unresolved ?? 0,
      failed: worker?.failed ?? 0, skippedBusy: worker?.skippedBusy ?? 0,
      clockErrors: worker?.clockErrors ?? 0, rejected: worker?.rejected ?? 0,
      accountingErrors: workerErrors.length, errors: workerErrors, valid: workerErrors.length === 0,
    },
    gpuAvailability: result.gpuAvailability,
    gpuInFlightLimit: GPU_IN_FLIGHT_LIMIT,
    gpuSkippedBusy: result.gpuSkippedBusy,
    gpuFailed: result.gpuFailed,
    gpuPending: result.gpuPending,
    gpuBusySlots: result.gpuBusySlots,
    gpuUnresolvedSamples: result.gpuUnresolvedSamples,
    gpuAccountingValid: result.gpuAccountingValid,
    phases: PHASES.map(phase => ({
      ...phase,
      ui: statistics(result.ui, phase.start, phase.end),
      js: statistics(js, phase.start, phase.end),
      gpuCompletion: statistics(result.gpu, phase.start, phase.end),
      gpuRNCompletion: statistics(rnGPU, phase.start, phase.end),
      gpuRNDispatch: statistics(rnDispatch, phase.start, phase.end),
      gpuWorkerCompletion: statistics(workerGPU, phase.start, phase.end),
      gpuWorkerDispatch: statistics(workerDispatch, phase.start, phase.end),
    })),
    total: { ui: statistics(result.ui), js: statistics(js), gpuCompletion: statistics(result.gpu), gpuRNCompletion: statistics(rnGPU), gpuRNDispatch: statistics(rnDispatch), gpuWorkerCompletion: statistics(workerGPU), gpuWorkerDispatch: statistics(workerDispatch) },
    raw: { ui: result.ui, js, gpuCompletion: result.gpu, gpuRNCompletion: rnGPU, gpuRNDispatch: rnDispatch, gpuWorkerCompletion: workerGPU, gpuWorkerDispatch: workerDispatch },
  };
}
