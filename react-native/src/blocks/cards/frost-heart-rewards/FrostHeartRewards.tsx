import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, State } from 'react-native-gesture-handler';
import { cancelAnimation, Easing, SensorType, useAnimatedSensor, useDerivedValue, useFrameCallback, useSharedValue, withDecay, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { runOnUIAsync, scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';
import { CLAIMS, clamp, ease, LOOP, sceneAt, seg, VALUES } from './fhr-motion';
import { FHR_THEME as T } from './fhr-theme';
import { FhrStage } from './FhrStage';
import { FhrParticles } from './gpu/FhrParticles';
import { type FhrSound, useFhrAudio } from './use-fhr-audio';
import { fhrPerfResults, prepareFhrPerfGPU, recordFhrUIFrame, startFhrPerfJS, startFhrPerfUI, stopFhrPerf } from './fhr-perf';
export { LOOP } from './fhr-motion';
type Props = {
    autoPlay?: boolean;
};
type Cue = FhrSound | 'impact' | 'tick' | 'snap' | 'cutComplete';
const NEVER = -1000;
const SILENT = () => { };
function wallSeconds() { 'worklet'; return Date.now()/1000; }
/** Pearls → jeweller’s cut → suspended crystal → magnetic lock → platinum level. */
export function FrostHeartRewards({ autoPlay = false }: Props) {
    const reduced = useReducedMotionPreference(), insets = useSafeAreaInsets();
    const [size, setSize] = useState({ width: 402, height: 874 });
    const [ready, setReady] = useState(false), [gpu, setGpu] = useState(false);
    const [touchSession, setTouchSession] = useState(false);
    const [active, setActive] = useState(AppState.currentState === 'active');
    const [spoken, setSpoken] = useState({ balance: 1650, level: 3, claimed: [true, true, false, false, false, false, false] });
    const { play, stop, prepare } = useFhrAudio();
    useEffect(() => { if (active) void prepare('cutTing'); }, [active, prepare]);
    const cue = useCallback((name: Cue) => {
        if (name === 'cutComplete') {
            play('cutTing');
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(SILENT);
            return;
        }
        if (name === 'tick' || name === 'snap') {
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(SILENT);
            return;
        }
        if (name === 'impact') {
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(SILENT);
            return;
        }
        play(name);
        if (name.startsWith('claim'))
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(SILENT);
        if (name === 'level')
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(SILENT);
    }, [play]);
    const announce = useCallback((balance: number, level: number, mask: number) => {
        setSpoken({ balance: Math.round(balance), level, claimed: Array.from({ length: 7 }, (_, i) => i < 2 || !!(mask & (1 << (i - 2)))) });
    }, []);
    useEffect(() => {
        const sub = AppState.addEventListener('change', state => { setActive(state === 'active'); if (state !== 'active')
            stop(); });
        return () => sub.remove();
    }, [stop]);
    const time = useSharedValue(0), local = useSharedValue(0);
    const pinned = useSharedValue(false), interactive = useSharedValue(false);
    // 0=demo, 1=manual polish/claim, 2=Atelier sequence, 3=blur return.
    const mode = useSharedValue(0), touchedAt = useSharedValue(-10000);
    const claims = useSharedValue<number[]>([NEVER, NEVER, NEVER, NEVER, NEVER]);
    const finger = useSharedValue(false), moved = useSharedValue(false), holdWall = useSharedValue(0);
    const polishX = useSharedValue(0);
    const breath = useSharedValue(0), breathX = useSharedValue(133), breathY = useSharedValue(258);
    const cutTicks = useSharedValue(0);
    const target = useSharedValue(0), holdStarted = useSharedValue(false), charge = useSharedValue(0);
    const dragX = useSharedValue(0), dragY = useSharedValue(0), ring = useSharedValue(0), lastX = useSharedValue(0);
    const jelly = useSharedValue(0), reset = useSharedValue(0), lastTick = useSharedValue(-1);
    const lowMotionLevel = useSharedValue(0);
    const captureDelay = useSharedValue(-1), captureAt = useSharedValue(-1);
    const releaseCaptureDelay = useSharedValue(-1), captureBreath = useSharedValue(false);
    const holdPeak = useSharedValue(0), bearTaps = useSharedValue(0);
    const captureTilt = useSharedValue({ x: 0, y: 0 });
    const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 32 });
    const tilt = useDerivedValue(() => ({
        x: reduced ? 0 : pinned.get() ? captureTilt.get().x : clamp(dragX.get() + sensor.sensor.get().roll * 5, -8, 8),
        y: reduced ? 0 : pinned.get() ? captureTilt.get().y : clamp(dragY.get() + sensor.sensor.get().pitch * 3, -8, 8),
    }));
    const scene = useDerivedValue(() => {
        const s = sceneAt(time.get());
        if (interactive.get()) {
            let balance = 1650;
            for (let i = 0; i < 5; i++)
                if (claims.get()[i] >= 0) {
                    const age = local.get() - claims.get()[i] - (reduced ? 0 : 0.65);
                    const amount = VALUES[i] * ease(seg(age, 0, reduced ? 0.16 : 0.18));
                    balance += amount;
                }
            s.balance = balance;
            s.remaining = Math.max(0, 65 - (balance - 1650));
            s.polish = reduced ? 0 : charge.get();
            s.polishX = polishX.get();
            s.breath = time.get() < 2.4 ? breath.get() : 0;
            s.breathX = breathX.get(); s.breathY = breathY.get();
            if (mode.get() === 3)
                s.reset = reset.get();
            if (reduced) {
                let from=1650,to=1650,blend=1;
                for(let i=0;i<5;i++)if(claims.get()[i]>=0){
                    const age=local.get()-claims.get()[i];
                    if(age>=0.16){from+=VALUES[i];to+=VALUES[i];}
                    else if(age>=0){to+=VALUES[i];blend=Math.min(blend,ease(clamp(age/0.16)));}
                }
                s.balanceFrom=from;s.balanceTo=to;s.numberFade=blend;s.balance=to;
                s.gem = lowMotionLevel.get();
                s.hue = lowMotionLevel.get();
                s.flip = lowMotionLevel.get();
                s.level = lowMotionLevel.get() > 0.5 ? 4 : 3;
                s.broken = 0;
                s.shake = 0;
                s.charge = 0;
                s.frost = 0;
                s.crack = 0;
                s.rays = 0;
                s.core = 0; s.flash = 0; s.display = 0; s.turn = 0; s.push = 0; s.spotlight = lowMotionLevel.get(); s.cut = lowMotionLevel.get();
            }
        }
        return s;
    });
    // Meaningful accessibility values update only on interaction milestones, never per frame.
    const report = () => {
        const sv = scene.get();
        let mask = 0;
        for (let i = 0; i < 5; i++)
            if (claims.get()[i] >= 0)
                mask |= 1 << i;
        announce(sv.balance, sv.level, mask);
    };
    function takeOver() {
        'worklet';
        pinned.set(false);
        if (__DEV__ && captureDelay.get() >= 0) { captureAt.set(local.get()+captureDelay.get()); captureDelay.set(-1); }
        if (!interactive.get()) {
            const existing = [NEVER, NEVER, NEVER, NEVER, NEVER];
            for (let i = 0; i < 5; i++)
                if (time.get() < 9.9 && time.get() >= CLAIMS[i])
                    existing[i] = local.get() - (time.get() - CLAIMS[i]);
            claims.set(existing);
            mode.set(time.get() >= 1.7 && time.get() < 8.9 ? 2 : 1);
            if (mode.get() === 1)
                time.set(0);
        }
        if (!interactive.get())
            scheduleOnRN(setTouchSession, true);
        if (mode.get() === 3) {
            mode.set(1);
            time.set(0);
            charge.set(0);
        }
        interactive.set(true);
        touchedAt.set(local.get());
        reset.set(0);
    }
    function claimChip(i: number) {
        'worklet';
        takeOver();
        if (claims.get()[i] >= 0)
            return;
        const updated = claims.get().slice();
        updated[i] = local.get();
        claims.set(updated);
        scheduleOnRN(cue, `claim${Math.min(i, 3)}` as FhrSound);
    }
    const frame = useFrameCallback(f => {
        if (!ready || !active || pinned.get() || reduced && !interactive.get())
            return;
        const dt = (f.timeSincePreviousFrame ?? 16.667) / 1000;
        if (__DEV__) recordFhrUIFrame(f.timeSincePreviousFrame, time.get());
        local.set(local.get() + dt);
        if (__DEV__ && captureAt.get() >= 0 && local.get() >= captureAt.get()) {
            captureAt.set(-1); if (captureBreath.get()) { cancelAnimation(breath); captureBreath.set(false); } pinned.set(true); return;
        }
        const before = time.get();
        if (!interactive.get()) {
            if (autoPlay && !reduced)
                time.set((before + dt) % LOOP);
            else
                time.set(0);
            // untouched autoplay stays silent and still: the cut's ting and haptic answer a finger only
            if (!reduced && before < 3.8 && time.get() >= 3.8) cutTicks.set(cutTicks.get() + 1);
            return;
        }
        if (finger.get() && !moved.get() && target.get() === 1 && !reduced) {
            const age = wallSeconds() - holdWall.get() - 0.18;
            if (age > 0) {
                charge.set(ease(clamp(age / 0.8)));
                if (__DEV__) holdPeak.set(Math.max(holdPeak.get(), charge.get()));
            }
        }
        if (mode.get() === 1 && time.get() === 0 && scene.get().remaining <= 0 && !finger.get()) {
            if (reduced) {
                time.set(8.9);
                lowMotionLevel.set(withTiming(1, { duration: 220 }, finished => { if (finished) scheduleOnRN(report); }));
                scheduleOnRN(cue, 'level');
            } else {
                time.set(1.7); mode.set(2); scheduleOnRN(cue, 'frost');
            }
        }
        if (mode.get() === 2) {
            const playingBefore = time.get();
            if (!finger.get()) time.set(Math.min(8.9, playingBefore + dt));
            const after = time.get();
            if (!reduced && playingBefore < 3.8 && after >= 3.8) {
                cutTicks.set(cutTicks.get() + 1); scheduleOnRN(cue, 'cutComplete');
            }
            if (playingBefore < 2.4 && after >= 2.4) scheduleOnRN(cue, 'frost');
            for (let k = 0; k < 12; k++) {
                const at = 5.8 + k * 0.04;
                if (k % 3 === 0 && playingBefore < at && after >= at) scheduleOnRN(cue, 'snap');
            }
            if (playingBefore < 6.95 && after >= 6.95) {
                scheduleOnRN(cue, 'level');
                let mask = 0, value = 1650;
                for (let i = 0; i < 5; i++) if (claims.get()[i] >= 0) {
                    mask |= 1 << i; value += VALUES[i];
                }
                scheduleOnRN(announce, value, 4, mask);
            }
            if (after >= 8.9) mode.set(1);
        }
        for (let i = 0; i < 5; i++) {
            const age = local.get() - claims.get()[i];
            if (claims.get()[i] >= 0 && age >= 0.65 && age - dt < 0.65 && !reduced)
                scheduleOnRN(cue, 'tinkle');
            const settled = reduced ? 0.16 : 0.83;
            if (claims.get()[i] >= 0 && age >= settled && age - dt < settled) {
                let mask = 0;
                let value = 1650;
                for (let j = 0; j < 5; j++)
                    if (claims.get()[j] >= 0) {
                        mask |= 1 << j;
                        value += VALUES[j];
                    }
                scheduleOnRN(announce, value, scene.get().level, mask);
            }
        }
        if (!finger.get() && mode.get() !== 3 && local.get() - touchedAt.get() > 2) {
            mode.set(3);
            reset.set(0);
        }
        if (mode.get() === 3) {
            reset.set(Math.min(1, reset.get() + dt / (reduced ? 0.25 : 1.1)));
            if (reset.get() >= 1) {
                interactive.set(false);
                mode.set(0);
                time.set(0);
                charge.set(0);
                lowMotionLevel.set(0);
                claims.set([NEVER, NEVER, NEVER, NEVER, NEVER]);
                ring.set(0);
                scheduleOnRN(announce, 1650, 3, 0);
                scheduleOnRN(setTouchSession, false);
            }
        }
    }, false);
    useEffect(() => { frame.setActive(ready && active && (!reduced || touchSession)); }, [ready, active, frame, reduced, touchSession]);
    useEffect(() => {
        if (!__DEV__)
            return;
        const hook = {
            tilt: (x = 0, y = 0) => scheduleOnUI(() => { 'worklet'; captureTilt.set({ x: clamp(x, -8, 8), y: clamp(y, -8, 8) }); }),
            perf: (enabled = true) => {
                if (!enabled) return stopFhrPerf();
                if (!ready || !active || reduced || !autoPlay) return Promise.reject(new Error('The performance loop requires a ready, active autoplay screen with reduced motion disabled.'));
                prepareFhrPerfGPU();
                stop();
                return runOnUIAsync(() => {
                    'worklet';
                    cancelAnimation(ring);
                    cancelAnimation(dragX);
                    cancelAnimation(dragY);
                    cancelAnimation(jelly);
                    cancelAnimation(charge);
                    interactive.set(false);
                    mode.set(0);
                    pinned.set(false);
                    ring.set(0);
                    dragX.set(0);
                    dragY.set(0);
                    jelly.set(0);
                    charge.set(0);
                    breath.set(0); releaseCaptureDelay.set(-1); captureBreath.set(false);
                    reset.set(0);
                    finger.set(false);
                    captureAt.set(-1);
                    captureDelay.set(-1);
                    holdPeak.set(0);
                    claims.set([NEVER, NEVER, NEVER, NEVER, NEVER]);
                    local.set(LOOP);
                    time.set(0);
                    return startFhrPerfUI();
                }).then(startFhrPerfJS);
            },
            perfResults: fhrPerfResults,
            armRelease: (seconds:number) => scheduleOnUI(() => { 'worklet'; releaseCaptureDelay.set(Math.max(0,seconds)); }),
            arm: (seconds:number) => scheduleOnUI(() => { 'worklet'; captureDelay.set(Math.max(0,seconds)); }),
            freeze: () => scheduleOnUI(() => { 'worklet'; pinned.set(true); }),
            inspect: () => ({target:target.get(),breath:breath.get(),breathX:breathX.get(),breathY:breathY.get(),cutTicks:cutTicks.get(),holdPeak:holdPeak.get(),bearTaps:bearTaps.get(),time:time.get(),local:local.get(),mode:mode.get(),finger:finger.get(),interactive:interactive.get(),pinned:pinned.get(),charge:charge.get(),reset:reset.get(),ring:ring.get(),jelly:jelly.get(),tilt:tilt.get(),claims:claims.get(),scene:scene.get(),ready,gpu,reduced}),
            targets: () => {
                const scale=size.width/402,dy=Math.max(0,insets.top-70*scale);
                const point=(x:number,y:number)=>({x:x*scale/size.width,y:(y*scale+dy)/size.height});
                return {heart:point(188,282),bear:point(154,176),hero:point(206,260),chips:T.layout.daily.chips.map(c=>point(c.body.x+c.body.width/2,c.body.y+c.body.height/2))};
            },
            at: (t: number) => {
                stop();
                scheduleOnUI(() => {
                    'worklet';
                    cancelAnimation(ring);
                    cancelAnimation(dragX);
                    cancelAnimation(dragY);
                    cancelAnimation(jelly);
                    cancelAnimation(charge);
                    interactive.set(false);
                    mode.set(0);
                    pinned.set(true);
                    ring.set(0);
                    dragX.set(0);
                    dragY.set(0);
                    jelly.set(0);
                    charge.set(0);
                    breath.set(0); releaseCaptureDelay.set(-1); captureBreath.set(false);
                    reset.set(0);
                    finger.set(false);
                    captureAt.set(-1);
                    holdPeak.set(0);
                    claims.set([NEVER, NEVER, NEVER, NEVER, NEVER]);
                    const frameTime=((t % LOOP) + LOOP) % LOOP;
                    local.set(frameTime+LOOP);
                    time.set(frameTime);
                });
            },
            play: () => scheduleOnUI(() => {
                'worklet';
                captureTilt.set({ x: 0, y: 0 });
                pinned.set(false);
                interactive.set(false);
                mode.set(0);
            }),
        };
        const g = globalThis as typeof globalThis & {
            __fhr?: typeof hook;
        };
        Object.defineProperty(g, '__fhr', { value: hook, configurable: true });
        return () => { if (g.__fhr === hook) {
            Reflect.deleteProperty(g, '__fhr');
            void stopFhrPerf();
        } };
    }, [charge, dragX, dragY, interactive, jelly, mode, pinned, ring, stop, time, finger, local, reset, tilt, claims, scene, ready, gpu, reduced, size, insets.top, captureDelay, captureAt, holdPeak, bearTaps, target, captureTilt, active, autoPlay, breath, breathX, breathY, cutTicks, releaseCaptureDelay, captureBreath]);
    const S = size.width / 402, offsetY = Math.max(0, insets.top - 70 * S);
    const pan = Gesture.Pan().minDistance(0)
        .onTouchesMove(e => {
        const touch = e.changedTouches[0];
        if (touch && target.get() === 1 && time.get() < 2.4) {
            breathX.set(touch.absoluteX / S); breathY.set((touch.absoluteY - offsetY) / S);
        }
    })
        .onBegin(e => {
        takeOver();
        finger.set(true);
        moved.set(false);
        holdStarted.set(false);
        lastTick.set(-1);
        holdWall.set(wallSeconds());
        holdPeak.set(0);
        lastX.set(e.x);
        cancelAnimation(ring);
        const x = e.absoluteX / S, y = (e.absoluteY-offsetY) / S;
        polishX.set(clamp((x - 175) / 105, -1.2, 1.2));
        target.set(x > 75 && x < 283 && y > 225 && y < 367 ? 1 : 2);
        if (target.get() === 1 && time.get() < 2.4) {
            breathX.set(x); breathY.set(y);
            breath.set(withTiming(1, { duration: reduced ? 220 : 300, easing: Easing.out(Easing.cubic) }));
        }
    })
        .onUpdate(e => {
        touchedAt.set(local.get());
        if (target.get() === 1 && time.get() < 2.4) {
            breathX.set(e.absoluteX / S); breathY.set((e.absoluteY - offsetY) / S);
        }
        polishX.set(clamp((e.absoluteX / S - 175) / 105, -1.2, 1.2));
        if (Math.hypot(e.translationX, e.translationY) > 8)
            moved.set(true);
        if (!moved.get() || reduced)
            return;
        dragX.set(8 * Math.tanh(e.translationX / 95));
        dragY.set(8 * Math.tanh(e.translationY / 95));
        ring.set(0.61 * Math.tanh(e.translationX / 105));
        lastX.set(e.x);
        if (mode.get() === 1)
            charge.set(withTiming(0, { duration: 400 }));
    })
        .onEnd(e => {
        if (moved.get() && !reduced)
            ring.set(withDecay({ velocity: e.velocityX * 0.003, deceleration: 0.985, clamp: [-0.61, 0.61] }));
    })
        .onFinalize(e => {
        // A stationary tap can finish before Pan activates, so it has no onEnd callback.
        if (e.state !== State.CANCELLED && !moved.get() && (wallSeconds() - holdWall.get() < 0.25)) {
            if (target.get() === 2) {
                bearTaps.set(bearTaps.get()+1);
                jelly.set(1);
                jelly.set(withSpring(0, { damping: 20, stiffness: 230 }));
                scheduleOnRN(cue, 'impact');
            }
            else if (reduced) {
                lowMotionLevel.set(withTiming(lowMotionLevel.get() > 0.5 ? 0 : 1, { duration: 220 }, finished => {if(finished)scheduleOnRN(report);}));
                scheduleOnRN(cue, 'level');
            }
        }
        scheduleOnRN(report);
        finger.set(false);
        breath.set(withTiming(0, { duration: 1200, easing: Easing.out(Easing.cubic) }));
        if (__DEV__ && releaseCaptureDelay.get() >= 0) {
            captureAt.set(local.get() + releaseCaptureDelay.get()); releaseCaptureDelay.set(-1);
            captureBreath.set(true); pinned.set(false);
        }
        touchedAt.set(local.get());
        dragX.set(withSpring(0, { damping: 16, stiffness: 130 }));
        dragY.set(withSpring(0, { damping: 16, stiffness: 130 }));
        charge.set(withTiming(0, { duration: reduced ? 160 : 240 }));
    });
    const a11yClaim = (i: number) => {
        scheduleOnUI(() => {
            'worklet';
            claimChip(i);
        });
        // React labels update once per claim; numerical animation remains entirely on the UI thread.
        setSpoken(old => { if (old.claimed[i + 2])
            return old; const c = old.claimed.slice(); c[i + 2] = true; return { ...old, balance: old.balance + VALUES[i], claimed: c }; });
    };
    const onLayout = (e: LayoutChangeEvent) => { const { width, height } = e.nativeEvent.layout; setSize(old => old.width === width && old.height === height ? old : { width, height }); };
    return <View style={styles.screen} onLayout={onLayout}>
    <StatusBar style="light"/>
    <FhrStage gpuLayer={<FhrParticles pinned={pinned} jelly={jelly} scene={scene} charge={charge} reset={reset} tilt={tilt} time={time} width={size.width} height={size.height} offsetY={offsetY} ring={ring} reducedMotion={reduced} claims={claims} manualTime={local} interactive={interactive} onReady={setGpu}/>} scene={scene} time={time} manualTime={local} claims={claims} interactive={interactive} tilt={tilt} ring={ring} jelly={jelly} width={size.width} height={size.height} offsetY={offsetY} gpu={gpu} reduced={reduced} onReady={setReady}/>

    <GestureDetector gesture={pan}><View testID="fhr-hero" style={{ position: 'absolute', left: 44 * S, top: 130 * S + offsetY, width: 325 * S, height: 259 * S }} accessible={false}>
      <View testID="fhr-bear" accessibilityActions={[{name:'activate',label:'Bounce the bear'}]} onAccessibilityAction={()=>scheduleOnUI(()=>{'worklet';takeOver();bearTaps.set(bearTaps.get()+1);jelly.set(1);jelly.set(withSpring(0,{damping:20,stiffness:230}));scheduleOnRN(cue,'impact');})} accessible accessibilityRole="image" accessibilityLabel="Pink jelly bear. Tap for a jelly bounce." style={{position:'absolute',left:51*S,top:17*S,width:160*S,height:76*S}}/>
      <View testID="fhr-heart" accessibilityActions={[{ name: 'activate', label: 'Reveal the next level' }]} onAccessibilityAction={() => {
            scheduleOnUI(() => {
                'worklet';
                takeOver();
                if (reduced)
                    lowMotionLevel.set(withTiming(1, { duration: 220 }, finished => {if(finished)scheduleOnRN(report);}));
                else {
                    time.set(1.7);
                    mode.set(2);
                }
                scheduleOnRN(cue, 'frost');
            });
        }} accessible accessibilityRole="image" accessibilityLabel="Frosted crystal heart. Rest a finger to breathe through the frost; hold to polish; drag to turn the gem. Tilt to move its fire." style={{position:'absolute',left:31*S,top:95*S,width:208*S,height:142*S}}/>
    </View></GestureDetector>
    {T.layout.daily.chips.map((chip, i) => {
            if (i < 2)
                return <View key={i} accessible accessibilityLabel={`${chip.value} UPs, already claimed`} style={{ position: 'absolute', left: chip.body.x * S, top: chip.body.y * S + offsetY, width: chip.body.width * S, height: chip.body.height * S }}/>;
            const tap = Gesture.Tap().onEnd((_e, ok) => { if (ok) {
                claimChip(i - 2);
                scheduleOnRN(report);
            } });
            return <GestureDetector key={i} gesture={tap}><View testID={`fhr-claim-${chip.value}`} accessible accessibilityRole="button" accessibilityLabel={`Claim ${chip.value} UPs`} accessibilityState={{ disabled: spoken.claimed[i] }} onAccessibilityTap={() => a11yClaim(i - 2)} style={{ position: 'absolute', left: chip.body.x * S, top: chip.body.y * S + offsetY - 4, width: chip.body.width * S, height: 44 * S }}/></GestureDetector>;
        })}
    <Text style={[styles.srOnly, { top: 402 * S + offsetY }]} accessibilityLiveRegion="polite">{`${spoken.balance.toLocaleString('en-US')} points. Level ${spoken.level}.`}</Text>
    <Text style={[styles.srOnly, { top: 75 * S + offsetY }]} accessibilityRole="header">my lumi</Text>
    <Text style={[styles.srOnly, { top: 479 * S + offsetY }]} accessibilityRole="header">UPs every day!</Text>
    <Text style={[styles.srOnly, { top: 647 * S + offsetY }]}>All. Deals. Pharmacy. Beauty. Discover more at lumi: sportswear essentials. Plus 200 UPs. See what&apos;s in our latest collection and choose a new favourite. 2 hours 58 minutes.</Text>
  </View>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: T.palette.background, overflow: 'hidden' }, srOnly: { position: 'absolute', left: 0, width: 1, height: 1, opacity: 0 } });
