import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { FadeOut, useAnimatedStyle, useReducedMotion, useSharedValue, withDelay, withSequence, withSpring, withTiming } from 'react-native-reanimated';

import { Character } from './characters';
import { PRO, advance, allocate, back, chooseBot, focusPro, initialFlow, show, type Flow } from './flow';
import { BackArrow } from './glyphs';
import { Centered, Frame, PrimaryButton, PressScale, Reveal, SFSymbol } from './primitives';
import { AllocationScene } from './scenes/Allocation';
import { ProScene } from './scenes/Pro';
import { ResultsScene } from './scenes/Results';
import { SelectorScene } from './scenes/Selector';
import { SetupScene } from './scenes/Setup';
import { WalletScene } from './scenes/Wallet';
import { DetailSheet, QAHarness, type Detail } from './sheets';
import { CANVAS, EASE_IN_OUT, EASE_OUT, hex, palette, spring, white } from './theme';

/** Seconds between the walkthrough's CTA presses, timed like the reference video. */
const WALKTHROUGH = [2.5, 3.6, 2.4, 3.0, 2.9];

type Props = {
  /** Opens a scene directly (QA and screenshots), like `-tbStep` in the Swift app. */
  startStep?: number;
  /** Keeps the Pro screen on its first card. */
  holdFocus?: boolean;
  /** Plays the walkthrough on launch. */
  autoplay?: boolean;
};

const now = () => performance.now() / 1000;

export function TradingBotsOnboarding({ startStep = 0, holdFocus = false, autoplay = false }: Props) {
  const systemReduced = useReducedMotion();
  const [forceReduced, setForceReduced] = useState(false);
  const reduced = systemReduced || forceReduced;
  const [flow, setFlow] = useState<Flow>(() => initialFlow(startStep));
  const [revision, setRevision] = useState(0);
  const [showQA, setShowQA] = useState(false);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [replayToken, setReplayToken] = useState(autoplay ? 1 : 0);
  const [ctaPressed, setCtaPressed] = useState(false);
  const [focusResponse, setFocusResponse] = useState(0.7);
  const proInteracted = useRef(false);
  const flowRef = useRef(flow);
  useEffect(() => {
    flowRef.current = flow;
  }, [flow]);

  // MARK: Navigation

  const dim = useSharedValue(0);
  const dimCTA = useCallback(() => {
    if (reduced) return;
    dim.set(withSequence(withTiming(1, { duration: 120, easing: EASE_OUT }), withDelay(280, withTiming(0, { duration: 500, easing: EASE_IN_OUT }))));
  }, [dim, reduced]);

  const next = useCallback(() => {
    const moved = advance(flowRef.current, now());
    if (!moved) return;
    proInteracted.current = false;
    setFlow(moved);
    dimCTA();
  }, [dimCTA]);

  const navigate = useCallback((step: number) => {
    setReplayToken(0);
    proInteracted.current = false;
    setFlow((current) => show(current, step));
    setRevision((value) => value + 1);
    dimCTA();
  }, [dimCTA]);

  const goBack = () => {
    setReplayToken(0);
    setFlow((current) => back(current));
    setRevision((value) => value + 1);
    dimCTA();
  };

  const restart = (replay: boolean) => {
    setFlow(initialFlow());
    setRevision((value) => value + 1);
    setReplayToken((token) => (replay ? token + 1 : 0));
  };

  // Pro: purple leads on entry; after 2 s green moves forward unless a card was tapped.
  useEffect(() => {
    if (flow.step !== PRO || reduced || holdFocus) return;
    const timer = setTimeout(() => {
      if (proInteracted.current || flowRef.current.proFocus !== 0) return;
      setFocusResponse(0.75);
      setFlow((current) => focusPro(current, 1));
    }, 2000);
    return () => clearTimeout(timer);
  }, [flow.step, revision, reduced, holdFocus]);

  // The QA walkthrough: five presses of the CTA, each shown pressed for 0.45 s.
  useEffect(() => {
    if (replayToken === 0) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    let at = 0;
    for (const duration of WALKTHROUGH) {
      at += duration;
      timers.push(setTimeout(() => setCtaPressed(true), (at - 0.45) * 1000));
      timers.push(setTimeout(() => { setCtaPressed(false); next(); }, at * 1000));
    }
    return () => {
      timers.forEach(clearTimeout);
      setCtaPressed(false);
    };
  }, [replayToken, next]);

  // MARK: Layout

  const { width, height } = useWindowDimensions();
  const scale = Math.min(width / CANVAS.width, height / CANVAS.height);
  const pro = flow.step === PRO;
  const proBackdrop = useSharedValue(pro ? 1 : 0);
  useEffect(() => {
    proBackdrop.set(withTiming(pro ? 1 : 0, { duration: 400, easing: EASE_IN_OUT }));
  }, [pro, proBackdrop]);
  const proBackdropStyle = useAnimatedStyle(() => ({ opacity: proBackdrop.get() }));

  const title = pro ? 'Start 3 Day Free Trial' : flow.step === 0 ? 'Let’s Get Started' : flow.step === 1 ? 'Choose Your Bots' : 'Continue';
  const ctaY = flow.step === 1 ? 803.8 : pro ? 753.2 : 757;

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <View style={{ width: CANVAS.width, height: CANVAS.height, transform: [{ scale }] }}>
        <LinearGradient colors={['#000', '#000', hex('#131313'), hex('#111111')]} locations={[0, 0.62, 0.93, 1]} style={StyleSheet.absoluteFill} />
        <Animated.View style={[StyleSheet.absoluteFill, proBackdropStyle]}>
          <LinearGradient colors={['#000', hex('#131313'), hex('#111111')]} locations={[0, 0.42, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>

        <Prewarm />

        <Animated.View key={`${flow.step}-${revision}`} style={StyleSheet.absoluteFill} exiting={reduced ? undefined : FadeOut.duration(180)} pointerEvents="box-none">
          {flow.step === 0 && <WalletScene reduced={reduced} />}
          {flow.step === 1 && <SelectorScene selection={flow.selectedBot} choose={(bot) => setFlow((current) => chooseBot(current, bot))} reduced={reduced} />}
          {flow.step === 2 && <SetupScene reduced={reduced} configure={() => navigate(3)} overview={() => setDetail('overview')} />}
          {flow.step === 3 && (
            <AllocationScene
              allocation={flow.allocation}
              setAllocation={(value) => setFlow((current) => allocate(current, value))}
              risk={flow.risk}
              setRisk={(risk) => setFlow((current) => ({ ...current, risk }))}
              autoTrading={flow.autoTrading}
              setAutoTrading={(autoTrading) => setFlow((current) => ({ ...current, autoTrading }))}
              maxLoss={flow.maxDailyLoss}
              setMaxLoss={(maxDailyLoss) => setFlow((current) => ({ ...current, maxDailyLoss }))}
              reduced={reduced}
            />
          )}
          {flow.step === 4 && <ResultsScene reduced={reduced} configure={() => navigate(3)} overview={() => setDetail('overview')} />}
          {pro && (
            <ProScene
              focus={flow.proFocus}
              focusResponse={focusResponse}
              select={(bot) => {
                proInteracted.current = true;
                setFocusResponse(0.7);
                setFlow((current) => focusPro(current, bot));
              }}
              reduced={reduced}
            />
          )}
        </Animated.View>

        <Header step={flow.step} reduced={reduced} goBack={goBack} openQA={() => setShowQA(true)} skip={() => (pro ? setDetail('finish') : navigate(PRO))} />

        <Centered x={200.8} y={ctaY}>
          <PrimaryButton
            title={title}
            pro={pro}
            dim={dim}
            pressed={ctaPressed}
            icon={pro ? <SFSymbol name="chevron.right" size={14} weight="bold" color="#000" /> : undefined}
            onPress={() => {
              setReplayToken(0);
              if (pro) setDetail('trial');
              else next();
            }}
          />
        </Centered>
        {pro ? (
          <Centered x={200} y={820}>
            <Reveal reduced={reduced}>
              <View style={{ flexDirection: 'row', gap: 10 }}>
                <Pressable onPress={() => setDetail('terms')} accessibilityRole="link"><Text style={styles.legal}>Terms of use</Text></Pressable>
                <Text style={[styles.legal, { color: hex('#404040') }]}>|</Text>
                <Pressable onPress={() => setDetail('privacy')} accessibilityRole="link"><Text style={styles.legal}>Privacy policy</Text></Pressable>
              </View>
            </Reveal>
          </Centered>
        ) : flow.step !== 1 ? (
          <Centered x={201} y={819}>
            <PressScale onPress={() => navigate(PRO)} accessibilityLabel="skip and start">
              <Text style={{ fontSize: 16, letterSpacing: -0.2, color: hex('#dedede'), paddingVertical: 8 }}>skip and start</Text>
            </PressScale>
          </Centered>
        ) : null}
      </View>

      <QAHarness
        visible={showQA}
        step={flow.step}
        reduced={forceReduced}
        setReduced={setForceReduced}
        show={(step) => { setShowQA(false); navigate(step); }}
        replay={() => { setShowQA(false); restart(true); }}
        reset={() => { setShowQA(false); restart(false); }}
        close={() => setShowQA(false)}
      />
      <DetailSheet detail={detail} risk={flow.risk} allocation={flow.allocation} close={() => setDetail(null)} replay={() => { setDetail(null); restart(false); }} />
    </View>
  );
}

/** Back (44 pt disc at 20…64), five-step progress from x 84, Skip at the right edge. */
function Header({ step, reduced, goBack, openQA, skip }: { step: number; reduced: boolean; goBack: () => void; openQA: () => void; skip: () => void }) {
  return (
    <>
      <Frame x={42} y={93.3} width={44} height={44}>
        <PressScale onPress={goBack} onLongPress={openQA} accessibilityLabel={step === 0 ? 'Restart wallet introduction' : 'Back'}>
          <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: hex('#0d0d0d'), borderWidth: 1, borderColor: white(0.035), alignItems: 'center', justifyContent: 'center' }}>
            <BackArrow width={17} height={13.5} />
          </View>
        </PressScale>
      </Frame>
      {step !== PRO && (
        <Animated.View exiting={reduced ? undefined : FadeOut.duration(180)} style={{ position: 'absolute', left: 84, top: 93.3 - 10, height: 20, width: 91, justifyContent: 'center' }}>
          <Pressable onLongPress={openQA} delayLongPress={800} accessible accessibilityLabel={`Onboarding step ${step + 1} of 5`}>
            <View style={{ flexDirection: 'row', gap: 4 }}>
              {[0, 1, 2, 3, 4].map((index) => <ProgressPill key={index} active={index === step} reduced={reduced} />)}
            </View>
          </Pressable>
        </Animated.View>
      )}
      <Centered x={356} y={95.3} box={120}>
        <PressScale onPress={skip} accessibilityLabel="Skip">
          <View style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ fontSize: 15.2, letterSpacing: -0.2, color: palette.ink }}>Skip</Text>
          </View>
        </PressScale>
      </Centered>
    </>
  );
}

function ProgressPill({ active, reduced }: { active: boolean; reduced: boolean }) {
  const width = useSharedValue(active ? 43 : 8);
  useEffect(() => {
    width.set(reduced ? (active ? 43 : 8) : withSpring(active ? 43 : 8, spring(0.42, 0.9)));
  }, [active, reduced, width]);
  const style = useAnimatedStyle(() => ({ width: width.get() }));
  return <Animated.View style={[{ height: 5, borderRadius: 2.5, backgroundColor: hex(active ? '#d8d8d8' : '#363636') }, style]} />;
}

/**
 * Draws every shader and image once, invisibly, at launch, so the first visit to a scene does
 * not stall on a shader compile or an image decode mid-transition.
 */
function Prewarm() {
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 4, top: 866, flexDirection: 'row', opacity: 0.011 }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Character kind="orb" size={4} />
      <Character kind="green" size={4} />
      <Character kind="purple" size={4} />
      <Character kind="red" size={4} />
      <Character kind="avatar" size={4} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  legal: { fontSize: 15.6, letterSpacing: -0.2, color: hex('#585858') },
});
