import {
  ACTIVE_RANGES,
  DEFAULT_ORDER,
  KIND,
  LIFT_SCALE,
  LIFT_SHADOW,
  LONG_PRESS,
  LOOP,
  OUTSIDE_CANCEL_DISTANCE,
  QUIET_REFLOW_DURATION,
  REFLOW_DAMPING,
  REFLOW_MASS,
  REFLOW_STIFFNESS,
  RESTART_DELAY,
  REST_SHADOW,
  SETTLE_DAMPING,
  SETTLE_MASS,
  SETTLE_STIFFNESS,
  SLOT_Y,
  SPRING_SETTLE_DURATION,
  dragRotation,
  evaluatePlan,
  isValid,
  nextCandidate,
  restFrame,
  sample,
  sampleTransition,
  slotPositions,
  type Frame,
  type Plan,
  type Transition,
} from './motion';

const CARDS = [0, 1, 2];
const FRAME_INTERVAL = 1 / 60;

/**
 * Authoritative component-lifetime arrangement, press/drag, settling and demo controller (Swift:
 * PaddockWidgetBentoController). It runs on the RN runtime and never touches React: the component pushes a `Plan`
 * to the UI runtime after every event, and the UI evaluates it each frame. Times are seconds on one monotonic clock.
 * The demo order is ephemeral; the committed order changes only on a completed user move.
 */
export class PaddockWidgetBentoController {
  order: number[] = DEFAULT_ORDER.slice();
  isActive = false;
  isVisible = false;
  isQuiet = false;
  autoPlay = false;
  isEditing = false;
  draggedID: number | null = null;
  dragY: number | null = null;
  candidateIndex: number | null = null;

  private timelineAnchor = 0;
  private frozenAt: number | null = null;
  private pressID: number | null = null;
  private pressOwner: number | null = null;
  private pressStartedAt: number | null = null;
  private pressFrame: Frame | null = null;
  private dragOwner: number | null = null;
  private pointerOffset = 0;
  private dragOriginOrder: number[] | null = null;
  private dragVisualOrder: number[] | null = null;
  /** Slot positions the neighbours were last sent to while a card is held (Swift lost them when a transition expired). */
  private dragSlots: number[] | null = null;
  private restartAt: number | null = null;
  private transition: Transition | null = null;

  get isDragging() {
    return this.draggedID !== null;
  }
  get hasPendingPress() {
    return this.pressID !== null && !this.isDragging;
  }
  get hasValidOrder() {
    return isValid(this.order);
  }
  /** The UI thread owns the finger; it hands its last y over before a cancel or release samples the held card. */
  syncDragY(y: number) {
    if (this.draggedID !== null) this.dragY = y;
  }
  /** The finger-to-card offset the UI thread keeps while a card is held. */
  get holdOffset() {
    return this.pointerOffset;
  }

  /** A restored order starts the component over, like Swift's `PaddockWidgetBentoController()` followed by `appear`. */
  reset(autoPlay: boolean, quiet: boolean, now: number) {
    Object.assign(this, new PaddockWidgetBentoController());
    this.appear(autoPlay, quiet, now);
  }

  appear(autoPlay: boolean, quiet: boolean, now: number) {
    this.autoPlay = autoPlay;
    this.isQuiet = quiet;
    this.isActive = true;
    this.isVisible = true;
    this.isEditing = false;
    this.clearGesture();
    this.transition = null;
    this.restartAt = null;
    this.frozenAt = null;
    this.timelineAnchor = now;
  }

  setActive(active: boolean, now: number) {
    if (active === this.isActive) return;
    if (!active) {
      this.cancelImmediately();
      this.freeze(now);
      this.isActive = false;
    } else {
      this.isActive = true;
      this.unfreezeIfEligible(now);
    }
  }

  setVisible(visible: boolean, now: number) {
    if (visible === this.isVisible) return;
    if (!visible) {
      this.cancelImmediately();
      this.freeze(now);
      this.isVisible = false;
    } else {
      this.isVisible = true;
      this.unfreezeIfEligible(now);
    }
  }

  setQuiet(quiet: boolean, now: number) {
    if (quiet === this.isQuiet) return;
    if (quiet) {
      if (this.isDragging) this.retargetDrag(now, true);
      else this.cancelImmediately();
      this.freeze(now);
      this.isQuiet = true;
    } else {
      this.isQuiet = false;
      if (this.isDragging) {
        this.unfreezeIfEligible(now, false);
        this.retargetDrag(now, false);
      } else {
        this.unfreezeIfEligible(now);
      }
    }
  }

  setAutoPlay(enabled: boolean, now: number) {
    if (enabled === this.autoPlay) return;
    this.autoPlay = enabled;
    this.restartAt = null;
    this.timelineAnchor = now;
    this.frozenAt = enabled && (!this.isActive || !this.isVisible || this.isQuiet || this.isEditing) ? now : null;
  }

  setEditing(editing: boolean, now: number) {
    if (editing === this.isEditing) return;
    if (editing) {
      this.pressFrame = null;
      this.clearPress();
      this.freeze(now);
      this.isEditing = true;
    } else {
      this.cancelDrag(now);
      this.isEditing = false;
      this.timelineAnchor = now;
      this.frozenAt = !this.isActive || !this.isVisible || this.isQuiet ? now : null;
      this.restartAt = this.autoPlay ? now + RESTART_DELAY : null;
    }
  }

  /** Starts a touch-owned pending press in public idle or during the demonstration. */
  beginPress(id: number, y: number, owner: number, now: number) {
    if (!this.isActive || !this.isVisible || !this.order.includes(id)) return;
    if (this.pressOwner !== null) {
      if (this.pressOwner === owner) return;
      this.cancelDrag(now);
      return;
    }
    if (this.draggedID !== null) return;
    if (!this.isEditing && this.autoPlay && !this.isQuiet) {
      if (this.restartAt !== null && now < this.restartAt) this.pressFrame = restFrame(this.order);
      else this.pressFrame = this.demoFrame(now);
      this.freeze(now);
    } else {
      this.pressFrame = null;
    }
    this.pressID = id;
    this.pressOwner = owner;
    this.pressStartedAt = now;
    this.dragY = y;
  }

  /**
   * The same touch owner must hold for 320 ms; reaching the threshold enters edit mode. `recognized` says the
   * native long-press recogniser already enforced the 320 ms (Gesture Handler's `activateAfterLongPress`), because the
   * JS arrival times of touch-down and activation do not preserve the interval; `visibleY` is the y the UI thread
   * was showing for the card, so both runtimes share one finger offset.
   */
  beginDrag(id: number, y: number, owner: number, now: number, options?: { recognized?: boolean; visibleY?: number }) {
    if (!this.isActive || !this.isVisible || this.draggedID !== null) return;
    if (this.pressID !== id || this.pressOwner !== owner || this.pressStartedAt === null) return;
    if (!options?.recognized && now - this.pressStartedAt < LONG_PRESS) return;
    if (!this.order.includes(id)) return;
    const visibleFrame = this.pressFrame;
    const visualOrder = visibleFrame ? visibleFrame.order : this.order;
    const index = visualOrder.indexOf(id);
    if (index < 0) return;
    this.isEditing = true;
    this.dragOriginOrder = this.order.slice();
    this.dragVisualOrder = visualOrder.slice();
    this.dragSlots = slotPositions(visualOrder);
    this.draggedID = id;
    this.dragOwner = owner;
    this.candidateIndex = index;
    const visibleY = options?.visibleY ?? (visibleFrame ? visibleFrame.y[id] : SLOT_Y[index]);
    this.pointerOffset = y - visibleY;
    this.dragY = y;
    this.pressFrame = null;
    this.transition = null;
  }

  updateDrag(y: number, owner: number, now: number) {
    if (this.draggedID === null || this.dragOwner !== owner || this.candidateIndex === null) return;
    this.dragY = y;
    const target = nextCandidate(this.candidateIndex, y - this.pointerOffset);
    if (target === this.candidateIndex) return;
    this.candidateIndex = target;
    this.retargetDrag(now, this.isQuiet);
  }

  /** Releases the touch: commits a held card's slot, or finishes a short press. Returns whether the order changed. */
  endDrag(owner: number, now: number, y?: number) {
    if (this.pressOwner === null || this.pressOwner !== owner) return false;
    if (this.draggedID === null || this.dragOwner !== owner || this.candidateIndex === null) {
      this.finishShortPress(now);
      return false;
    }
    if (y !== undefined) this.dragY = y;
    const id = this.draggedID;
    const target = this.candidateIndex;
    const currentPositions = this.sampledPositions(now);
    const selectedRotation = this.currentDragRotation;
    const center = (this.dragY ?? this.pointerOffset) - this.pointerOffset;
    const original = this.dragOriginOrder ?? this.order;
    const visualOrder = this.dragVisualOrder ?? original;
    if (center < SLOT_Y[0] - OUTSIDE_CANCEL_DISTANCE || center > SLOT_Y[2] + 62 + OUTSIDE_CANCEL_DISTANCE) {
      this.order = original;
      this.clearGesture();
      this.beginTransition(currentPositions, slotPositions(original), [], now, true, null, id, this.isQuiet ? 1 : LIFT_SCALE, this.isQuiet ? REST_SHADOW : LIFT_SHADOW, selectedRotation);
      return false;
    }
    const updated = original.slice();
    const visualSource = visualOrder.indexOf(id);
    const source = updated.indexOf(id);
    if (visualSource !== target && source >= 0) {
      updated.splice(source, 1);
      updated.splice(Math.min(target, updated.length), 0, id);
    }
    const changed = updated.some((value, index) => value !== original[index]);
    this.order = updated;
    this.clearGesture();
    this.beginTransition(currentPositions, slotPositions(updated), [], now, true, null, id, this.isQuiet ? 1 : LIFT_SCALE, this.isQuiet ? REST_SHADOW : LIFT_SHADOW, selectedRotation);
    return changed;
  }

  /** Cancels a live drag or short pending press and animates back to committed positions. */
  cancelDrag(now: number) {
    if (!this.hasPendingPress && !this.isDragging) return;
    const current = this.sampledPositions(now);
    const original = this.dragOriginOrder ?? this.order;
    const wasEditing = this.isEditing;
    const selected = this.draggedID;
    const scale = selected === null || this.isQuiet ? 1 : LIFT_SCALE;
    const shadow = selected === null || this.isQuiet ? REST_SHADOW : LIFT_SHADOW;
    const rotation = selected === null ? 0 : this.currentDragRotation;
    this.order = original;
    this.clearGesture();
    if (this.isActive && this.isVisible) {
      this.beginTransition(current, slotPositions(this.order), [], now, true, null, selected ?? -1, scale, shadow, rotation);
    } else {
      this.transition = null;
    }
    if (!wasEditing && this.autoPlay) {
      this.restartAt = now + RESTART_DELAY;
      this.timelineAnchor = now;
      this.frozenAt = null;
    }
  }

  /** Accessibility move action: shifts a card by `offset` slots through the same order reducer a drop uses. */
  move(id: number, offset: number, now: number) {
    if (!this.isEditing || this.isDragging || offset === 0) return false;
    const current = this.order.indexOf(id);
    if (current < 0) return false;
    const from = this.sampledPositions(now);
    const target = Math.min(this.order.length - 1, Math.max(0, current + offset));
    if (target === current) return false;
    const updated = this.order.slice();
    updated.splice(current, 1);
    updated.splice(target, 0, id);
    if (!isValid(updated)) return false;
    this.order = updated;
    this.beginTransition(from, slotPositions(updated), [], now, true);
    return true;
  }

  done(now: number) {
    if (!this.isEditing) return;
    if (this.isDragging || this.hasPendingPress) this.cancelDrag(now);
    this.isEditing = false;
    this.clearPress();
    this.timelineAnchor = now;
    this.frozenAt = !this.isActive || !this.isVisible || this.isQuiet ? now : null;
    this.restartAt = this.autoPlay ? now + RESTART_DELAY : null;
  }

  /**
   * A touch on the pit stop or the sticker: the demo rests until `until` (the touch's own effect plays meanwhile), then
   * starts again from the top RESTART_DELAY later. Arranging cards keeps its own rules.
   */
  holdDemo(until: number) {
    if (this.isEditing) return;
    this.restartAt = this.autoPlay ? until + RESTART_DELAY : null;
  }

  /** What the UI runtime shows from now on; commits deadline and transition bookkeeping like Swift's `frame`. */
  plan(now: number): Plan {
    if (this.isDragging || this.isEditing || this.transition !== null || this.hasPendingPress) {
      let base = restFrame(this.order);
      base.handles = this.isEditing ? 1 : 0;
      if (this.pressFrame && !this.isDragging) base = { ...this.pressFrame, order: this.pressFrame.order.slice(), y: this.pressFrame.y.slice(), opacity: this.pressFrame.opacity.slice() };
      const transition = this.transition;
      let drag: Plan['drag'] = null;
      if (this.draggedID !== null && this.dragY !== null) {
        const candidate = this.candidateOrder();
        // Neighbours stay at the last targets while the card is held: Swift fell back to the committed slots here.
        base = { ...base, order: candidate, y: (this.dragSlots ?? slotPositions(candidate)).slice() };
        drag = { id: this.draggedID, pointerOffset: this.pointerOffset, candidate: this.candidateIndex ?? 0, quiet: this.isQuiet };
      }
      const plan: Plan = { kind: KIND.live, anchor: 0, order: this.order.slice(), base, tr: transition ? cloneTransition(transition) : null, drag };
      if (transition && now - transition.startedAt >= transition.duration) this.transition = null;
      return plan;
    }
    if (!(this.isActive && this.isVisible && !this.isQuiet && this.autoPlay && this.frozenAt === null)) return this.restPlan();
    if (this.restartAt !== null) {
      if (now < this.restartAt) return this.restPlan();
      this.timelineAnchor = now;
      this.restartAt = null;
    }
    return { kind: KIND.demo, anchor: this.timelineAnchor, order: this.order.slice(), base: restFrame(this.order), tr: null, drag: null };
  }

  frame(now: number): Frame {
    return evaluatePlan(this.plan(now), now, this.dragY ?? 0);
  }

  /** Commits deadline/transition bookkeeping at event or scheduled wake boundaries. */
  advance(now: number) {
    this.plan(now);
  }

  /** 60 Hz while moving; otherwise one wake at the next motion boundary or restart. */
  cadence(now: number): number | null {
    if (this.isDragging) return FRAME_INTERVAL;
    if (this.transition) {
      const untilSettle = this.transition.startedAt + this.transition.duration - now;
      return untilSettle > 0 ? Math.min(FRAME_INTERVAL, untilSettle) : null;
    }
    if (!(this.isActive && this.isVisible && !this.isQuiet && this.autoPlay && !this.isEditing && this.frozenAt === null)) return null;
    if (this.restartAt !== null && now < this.restartAt) return Math.max(0.001, this.restartAt - now);
    const elapsed = Math.max(0, now - this.timelineAnchor);
    const time = elapsed % LOOP;
    if (ACTIVE_RANGES.some((range) => time >= range[0] && time < range[1])) return FRAME_INTERVAL;
    const boundaries = ACTIVE_RANGES.flatMap((range) => [range[0], range[1]]);
    const next = boundaries.find((value) => value > time) ?? LOOP + ACTIVE_RANGES[0][0];
    return Math.max(0.001, next - time);
  }

  /** Seconds until the 60 Hz window the demo or a transition is in ends; null when only an event ends it. */
  frameWindowEnd(now: number): number | null {
    if (this.isDragging) return null;
    if (this.transition) return Math.max(0, this.transition.startedAt + this.transition.duration - now);
    const time = Math.max(0, now - this.timelineAnchor) % LOOP;
    const range = ACTIVE_RANGES.find((value) => time >= value[0] && time < value[1]);
    return range ? range[1] - time : null;
  }

  private restPlan(): Plan {
    return { kind: KIND.rest, anchor: 0, order: this.order.slice(), base: restFrame(this.order), tr: null, drag: null };
  }

  private demoFrame(now: number): Frame {
    return sample(Math.max(0, now - this.timelineAnchor), this.order);
  }

  private candidateOrder(): number[] {
    if (this.draggedID === null || this.candidateIndex === null) return this.order;
    const result = (this.dragVisualOrder ?? this.order).slice();
    const source = result.indexOf(this.draggedID);
    if (source < 0) return this.order;
    result.splice(source, 1);
    result.splice(Math.min(this.candidateIndex, result.length), 0, this.draggedID);
    return result;
  }

  private sampledPositions(now: number): number[] {
    let result = this.transition ? sampleTransition(this.transition, now).y : this.isDragging && this.dragSlots ? this.dragSlots.slice() : slotPositions(this.order);
    if (this.pressFrame && !this.isDragging) result = this.pressFrame.y.slice();
    if (this.draggedID !== null && this.dragY !== null) {
      result = result.slice();
      result[this.draggedID] = this.dragY - this.pointerOffset;
    }
    return result;
  }

  private sampledVelocities(now: number): number[] {
    return this.transition ? sampleTransition(this.transition, now).v : [0, 0, 0];
  }

  private retargetDrag(now: number, quiet: boolean) {
    if (this.draggedID === null) return;
    const dragged = this.draggedID;
    const from = this.sampledPositions(now);
    const velocity = this.sampledVelocities(now);
    const targets: (number | null)[] = slotPositions(this.candidateOrder());
    const sources: (number | null)[] = from.slice();
    const velocities: (number | null)[] = velocity.slice();
    targets[dragged] = null;
    sources[dragged] = null;
    velocities[dragged] = null;
    this.beginTransition(sources, targets, velocities, now, false, quiet);
    this.dragSlots = slotPositions(this.candidateOrder());
  }

  private beginTransition(
    from: (number | null)[],
    to: (number | null)[],
    velocity: (number | null)[],
    now: number,
    settling: boolean,
    quiet: boolean | null = null,
    selected = -1,
    selectedScale = 1,
    selectedShadow = REST_SHADOW,
    selectedRotation = 0,
  ) {
    const moving = CARDS.some((id) => Math.abs((from[id] ?? to[id] ?? 55) - (to[id] ?? from[id] ?? 55)) > 0.001);
    const selectedMoves = selected >= 0 && (Math.abs(selectedScale - 1) > 0.001 || Math.abs(selectedShadow - REST_SHADOW) > 0.001 || Math.abs(selectedRotation) > 0.001);
    if (!moving && !selectedMoves) {
      this.transition = null;
      return;
    }
    const useQuiet = quiet ?? this.isQuiet;
    this.transition = {
      from,
      to,
      velocity,
      startedAt: now,
      duration: useQuiet ? QUIET_REFLOW_DURATION : SPRING_SETTLE_DURATION,
      stiffness: settling ? SETTLE_STIFFNESS : REFLOW_STIFFNESS,
      damping: settling ? SETTLE_DAMPING : REFLOW_DAMPING,
      mass: settling ? SETTLE_MASS : REFLOW_MASS,
      quietFade: useQuiet,
      selected,
      scaleFrom: selectedScale,
      shadowFrom: selectedShadow,
      rotationFrom: selectedRotation,
    };
  }

  private finishShortPress(now: number) {
    if (!this.hasPendingPress) return;
    const from = this.sampledPositions(now);
    const source = this.pressFrame;
    this.clearGesture();
    this.beginTransition(from, slotPositions(this.order), [], now, true, null, source ? source.selected : -1, source ? source.scale : 1, source ? source.shadow : REST_SHADOW, source ? source.rotation : 0);
    if (this.autoPlay) {
      this.restartAt = now + RESTART_DELAY;
      this.timelineAnchor = now;
      this.frozenAt = null;
    }
  }

  private cancelImmediately() {
    this.order = this.dragOriginOrder ?? this.order;
    this.clearGesture();
    this.transition = null;
  }

  private clearGesture() {
    this.draggedID = null;
    this.dragY = null;
    this.candidateIndex = null;
    this.dragOwner = null;
    this.dragOriginOrder = null;
    this.dragVisualOrder = null;
    this.dragSlots = null;
    this.pointerOffset = 0;
    this.pressFrame = null;
    this.clearPress();
  }

  private clearPress() {
    this.pressID = null;
    this.pressOwner = null;
    this.pressStartedAt = null;
  }

  private get currentDragRotation() {
    if (this.dragY === null || this.candidateIndex === null) return 0;
    return dragRotation(this.dragY, this.candidateIndex, this.pointerOffset, this.isQuiet);
  }

  private freeze(now: number) {
    if (this.frozenAt !== null) return;
    this.frozenAt = now;
  }

  private unfreeze(now: number, shiftTransition = true) {
    if (this.frozenAt === null) return;
    const pause = Math.max(0, now - this.frozenAt);
    this.timelineAnchor += pause;
    if (this.restartAt !== null) this.restartAt += pause;
    if (shiftTransition && this.transition) this.transition.startedAt += pause;
    this.frozenAt = null;
  }

  private unfreezeIfEligible(now: number, shiftTransition = true) {
    if (!this.isActive || !this.isVisible || this.isQuiet || this.isEditing) return;
    this.unfreeze(now, shiftTransition);
  }
}

function cloneTransition(value: Transition): Transition {
  return { ...value, from: value.from.slice(), to: value.to.slice(), velocity: value.velocity.slice() };
}
