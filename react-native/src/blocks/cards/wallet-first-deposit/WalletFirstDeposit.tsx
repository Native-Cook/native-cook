import { Canvas, createPicture, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useState } from 'react';
import { AccessibilityInfo, AppState, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  Easing,
  ReduceMotion,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { FILL_AT, type Frame, frameAt, FUNDED_AT, LAND_AT, lerp, LOOP, RESUME_AFTER, RETURN_AT, STAGGER } from './motion';
import { Odometer } from './Odometer';
import { drawBackdrop, drawHero, type Geo } from './paint';
import { actionBox, ASCENT, colors, copy, layout, payeeBox, REF_INSET, REF_PT, REF_W, type TextStyleSpec, type as T } from './theme';

export { LOOP };

type Props = {
  /** Plays empty → funded → empty every LOOP seconds. */
  autoPlay?: boolean;
};

const FRAME_KEYS = Object.keys(frameAt(0)) as (keyof Frame)[];

/** Touch modes for the clock: frozen, playing to a stop, looping. */
const IDLE = 0;
const PLAY = 1;
const AUTO = 2;

export function WalletFirstDeposit({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced);
  const [funded, setFunded] = useState(initialReduced);
  const [actionReady, setActionReady] = useState(true);
  const [walletState, setWalletState] = useState(initialReduced ? 'funded' : 'empty');
  const [screenReader, setScreenReader] = useState(false);
  const U = width / REF_W;
  const FS = width / REF_PT;
  const Y0 = insets.top - REF_INSET * U;
  const geo: Geo = { U, Y0 };

  const time = useSharedValue(reduced ? FUNDED_AT : 0);
  const frame = useSharedValue<Frame>(frameAt(reduced ? FUNDED_AT : 0));
  const foreground = useSharedValue(AppState.currentState === 'active');
  const holding = useSharedValue(false);
  const mode = useSharedValue(autoPlay && !reduced ? AUTO : IDLE);
  const stopAt = useSharedValue(FUNDED_AT);
  const speed = useSharedValue(1);
  const thenReturn = useSharedValue(false);
  const byHand = useSharedValue(false);
  const last = useSharedValue(-1);
  const touchedAt = useSharedValue(-1e9);
  const tapX = useSharedValue(0);
  const tapY = useSharedValue(0);
  const tapN = useSharedValue(0);
  const seen = useSharedValue(0);
  const bannerPress = useSharedValue(0);
  const cardPress = useSharedValue(0);
  const fade = useSharedValue(1);

  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((enabled) => { if (mounted) setScreenReader(enabled); });
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    if (screenReader) {
      time.set(FUNDED_AT);
      frame.set(frameAt(FUNDED_AT));
      holding.set(false);
      mode.set(IDLE);
    }
  }, [screenReader, mode, time, frame, holding]);

  useEffect(() => {
    if (reduced) {
      time.set(FUNDED_AT);
      frame.set(frameAt(FUNDED_AT));
      holding.set(false);
      bannerPress.set(0);
      cardPress.set(0);
      mode.set(IDLE);
    }
  }, [reduced, time, frame, mode, holding, bannerPress, cardPress]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      foreground.set(state === 'active');
      if (state !== 'active') {
        holding.set(false);
        bannerPress.set(0);
        cardPress.set(0);
      }
      last.set(-1);
    });
    return () => subscription.remove();
  }, [foreground, last, holding, bannerPress, cardPress]);

  // React receives only semantic state changes, never per-frame values.
  useAnimatedReaction(
    () => time.get() >= 3.3 && time.get() < 9,
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setFunded, next);
    },
  );
  useAnimatedReaction(
    () => {
      const t = time.get();
      return t < 2.75 || t >= 9.7 || (t >= 3.3 && t < 9);
    },
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setActionReady, next);
    },
  );
  useAnimatedReaction(
    () => {
      const t = time.get();
      return t < FILL_AT ? 'empty' : t < FUNDED_AT ? 'activating' : t < RETURN_AT ? 'funded' : 'returning';
    },
    (next, previous) => {
      if (next !== previous) scheduleOnRN(setWalletState, next);
    },
  );

  const land = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Which target a point (in pt) is on at loop time t: 1 = the banner, 2 = the card in the header, 0 = neither. */
  const target = (x: number, y: number, t: number) => {
    'worklet';
    const rx = x / U;
    const ry = (y - Y0) / U;
    if (t < 2.75 || t >= 9.7) {
      const b = layout.banner;
      if (rx >= b.x && rx <= b.x + b.w && ry >= b.y && ry <= b.y + b.h) return 1;
    } else if (t >= 3.3 && t < 9.0) {
      const c = layout.card;
      const hw = (c.w * c.b.s) / 2 + 36;
      const hh = (c.h * c.b.s) / 2 + 36;
      if (Math.abs(rx - c.b.cx) <= hw && Math.abs(ry - c.b.cy) <= hh) return 2;
    }
    return 0;
  };

  useFrameCallback((info) => {
    if (!foreground.get()) return;
    const now = info.timestamp;
    const before = last.get();
    last.set(now);
    const dt = before < 0 || holding.get() ? 0 : (now - before) / 1000;
    let t = time.get();

    // A tap: fund from the banner, empty from the header card; anywhere else it settles and waits.
    if (tapN.get() !== seen.get()) {
      seen.set(tapN.get());
      touchedAt.set(now);
      const hit = target(tapX.get(), tapY.get(), t);
      if (reduced) {
        if (hit !== 0) {
          const to = hit === 1 ? FUNDED_AT : 0;
          fade.set(
            withTiming(0, { duration: 100, reduceMotion: ReduceMotion.Never }, (finished) => {
              if (!finished) return;
              time.set(to);
              fade.set(withTiming(1, { duration: 100, reduceMotion: ReduceMotion.Never }));
            }),
          );
        }
      } else if (hit === 1) {
        t = t >= 9.7 ? FILL_AT : Math.max(t, FILL_AT);
        stopAt.set(FUNDED_AT);
        speed.set(1);
        thenReturn.set(false);
        byHand.set(true);
        mode.set(PLAY);
      } else if (hit === 2) {
        if (t < FUNDED_AT) {
          stopAt.set(FUNDED_AT);
          speed.set(2.5);
          thenReturn.set(true);
        } else {
          t = Math.max(t, RETURN_AT);
          stopAt.set(LOOP);
          speed.set(1);
          thenReturn.set(false);
        }
        byHand.set(false);
        mode.set(PLAY);
      } else if (mode.get() === AUTO) {
        // Stop the loop, but never mid-move: finish into the nearest state.
        if (t > FILL_AT && t < FUNDED_AT) {
          stopAt.set(FUNDED_AT);
          speed.set(1);
          mode.set(PLAY);
        } else if (t > RETURN_AT) {
          stopAt.set(LOOP);
          speed.set(1);
          mode.set(PLAY);
        } else {
          mode.set(IDLE);
        }
        thenReturn.set(false);
        byHand.set(false);
      }
    }

    const m = mode.get();
    if (!reduced && m === AUTO) {
      t += dt;
      t %= LOOP;
    } else if (!reduced && m === PLAY) {
      const prev = t;
      t += dt * speed.get();
      if (byHand.get() && prev < LAND_AT && t >= LAND_AT) scheduleOnRN(land);
      if (t >= stopAt.get()) {
        t = stopAt.get();
        if (thenReturn.get()) {
          thenReturn.set(false);
          t = RETURN_AT;
          stopAt.set(LOOP);
          speed.set(1);
        } else {
          if (t >= LOOP) t = 0;
          mode.set(IDLE);
        }
      }
    } else if (!reduced && !screenReader && !holding.get() && m === IDLE && autoPlay && now - touchedAt.get() >= RESUME_AFTER) {
      mode.set(AUTO);
    }
    if (reduced) t = time.get();
    time.set(t);

    // Publish the frame only when something moved, so nothing redraws during the holds.
    const next = frameAt(t);
    const cur = frame.get();
    for (let i = 0; i < FRAME_KEYS.length; i++) {
      const k = FRAME_KEYS[i];
      if (Math.abs(next[k] - cur[k]) > 1e-4) {
        frame.set(next);
        break;
      }
    }
  });

  const tap = Gesture.Tap()
    .maxDuration(600)
    .onBegin((e) => {
      holding.set(true);
      if (mode.get() === AUTO) mode.set(IDLE);
      touchedAt.set(last.get());
      const hit = target(e.x, e.y, time.get());
      if (!reduced && hit === 1) bannerPress.set(withTiming(1, { duration: 120, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      if (!reduced && hit === 2) cardPress.set(withTiming(1, { duration: 120, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
    })
    .onEnd((e, success) => {
      if (!success) return;
      tapX.set(e.x);
      tapY.set(e.y);
      tapN.set(tapN.get() + 1);
    })
    .onFinalize(() => {
      holding.set(false);
      touchedAt.set(last.get());
      bannerPress.set(withTiming(0, { duration: 100, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      cardPress.set(withTiming(0, { duration: 100, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
    });

  const backdrop = useDerivedValue(() => createPicture((canvas) => drawBackdrop(canvas, frame.get(), geo, bannerPress.get())));
  const hero = useDerivedValue(() => createPicture((canvas) => drawHero(canvas, frame.get(), geo, cardPress.get(), bannerPress.get())));
  const balance = useDerivedValue(() => frame.get().balance);
  const spentValue = useDerivedValue(() => frame.get().spentValue);

  // Text helpers: a pen position and a baseline in ref px.
  const X = (v: number) => {
    'worklet';
    return v * U;
  };
  const top = (base: number, s: TextStyleSpec) => Y0 + base * U - ASCENT * s.size * FS;
  const font = (s: TextStyleSpec) => ({ fontSize: s.size * FS, fontWeight: s.weight, color: s.color, letterSpacing: s.ls * FS });
  const label = (text: ReactNode, x: number, base: number, s: TextStyleSpec, extra?: object) => (
    <Text style={[styles.abs, font(s), { left: X(x), top: top(base, s) }, extra]}>{text}</Text>
  );
  const BOX = 200 * FS;
  const right = (text: string, r: number, base: number, s: TextStyleSpec, extra?: object) => (
    <Text style={[styles.abs, font(s), styles.right, { left: X(r) - BOX, width: BOX, top: top(base, s) }, extra]}>{text}</Text>
  );

  // Animated groups.
  const bn = layout.banner;
  const bannerStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const s = (1 - 0.03 * f.banner) * (1 - 0.03 * bannerPress.get());
    return { opacity: 1 - f.banner, transform: [{ translateY: -X(30) * f.banner }, { scale: s }] };
  });
  const copyOld = useAnimatedStyle(() => ({ opacity: 1 - frame.get().copy }));
  const copyNew = useAnimatedStyle(() => {
    const c = frame.get().copy;
    return { opacity: c, transform: [{ translateY: X(12) * (1 - c) }] };
  });
  const recentStyle = useAnimatedStyle(() => {
    const p = frame.get().reflow;
    const r = layout.recent;
    return { transform: [{ translateX: X(r.b.x - r.a.x) * p }, { translateY: X(r.b.base - r.a.base) * p }] };
  });
  const actionsStyle = useAnimatedStyle(() => {
    const k = frame.get().actionsOut;
    return { opacity: 1 - k, transform: [{ translateY: -X(10) * k }] };
  });
  const namesStyle = useAnimatedStyle(() => {
    const k = frame.get().names;
    return { opacity: k, transform: [{ translateY: X(18) * (1 - k) }] };
  });
  const pillStyle0 = usePill(frame, 0);
  const pillStyle1 = usePill(frame, 1);
  const txHeadStyle = useRise(frame, 'txHead', U);
  const txRow0Style = useRise(frame, 'txRow0', U);
  const txRow1Style = useRise(frame, 'txRow1', U);
  const greenStyle = useAnimatedStyle(() => ({ color: interpolateColor(frame.get().green, [0, 1], [colors.ink, colors.green]) }));
  const analyticsStyle = useAnimatedStyle(() => {
    const p = frame.get().reflow2;
    return { transform: [{ translateY: X(layout.analytics.b.base - layout.analytics.a.base) * p }] };
  });
  const ch = layout.chart;
  const spentStyle = useInChart(frame, ch.spent.a, ch.spent.b, U);
  const valueStyle = useInChart(frame, ch.value.a, ch.value.b, U);
  const labelAStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const cardTop = lerp(ch.a.y, ch.b.y, f.reflow2);
    const dy = cardTop + lerp(ch.label.a.dy, ch.label.b.dy, f.plot) - (ch.a.y + ch.label.a.dy);
    return { opacity: 1 - f.label, transform: [{ translateY: X(dy) - X(16) * f.label }] };
  });
  const labelBStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const cardTop = lerp(ch.a.y, ch.b.y, f.reflow2);
    const dy = cardTop + lerp(ch.label.a.dy, ch.label.b.dy, f.plot) - (ch.b.y + ch.label.b.dy);
    return { opacity: f.label, transform: [{ translateY: X(dy) + X(16) * (1 - f.label) }] };
  });
  const axisStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const cardTop = lerp(ch.a.y, ch.b.y, f.reflow2);
    const dy = cardTop + lerp(ch.axis.a, ch.axis.b, f.plot) - (ch.a.y + ch.axis.a);
    return { transform: [{ translateY: X(dy) }] };
  });
  const rootStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));

  const ac = layout.actions;
  const py = layout.payees;
  const bal = layout.balance;

  return (
    <ScrollView
      style={styles.screen}
      showsVerticalScrollIndicator={false}
      onScrollBeginDrag={() => { mode.set(IDLE); touchedAt.set(last.get()); }}
      onScrollEndDrag={() => { touchedAt.set(last.get()); }}
    >
    <GestureDetector gesture={tap}>
      <Animated.View style={[styles.screen, { flex: 0, height: Math.max(height, Y0 + (ch.b.y + ch.h) * U + insets.bottom + 16) }, rootStyle]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={backdrop} />
        </Canvas>

        <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
          {label(copy.total, layout.total.x, layout.total.base, T.title)}
          <Odometer
            slots={[
              { ch: '$' },
              { from: 0, to: 6, enter: true },
              { ch: ',', enter: true },
              { from: 0, to: 7, enter: true },
              { from: 0, to: 9, enter: true },
              { from: 0, to: 0, cycles: 1 },
              { ch: '.', grey: true },
              { from: 0, to: 2, cycles: 1, grey: true },
              { from: 0, to: 2, cycles: 1, grey: true },
            ]}
            progress={balance}
            span={1}
            stagger={STAGGER.digits}
            fontSize={T.balance.size * FS}
            fontWeight={T.balance.weight}
            letterSpacing={T.balance.ls * FS}
            color={T.balance.color}
            greyColor={colors.decimals}
            left={X(bal.x)}
            top={top(bal.base, T.balance) - (Math.ceil(T.balance.size * FS * 1.2) - 1.193 * T.balance.size * FS) / 2}
          />

          {/* The banner's copy; the banner itself is drawn on the canvas. */}
          <Animated.View style={[styles.abs, { left: X(bn.x), top: Y0 + X(bn.y), width: X(bn.w), height: X(bn.h) }, bannerStyle]}>
            <View style={[StyleSheet.absoluteFill, { left: -X(bn.x), top: -(Y0 + X(bn.y)) }]}>
              <Animated.View style={[StyleSheet.absoluteFill, copyOld]}>
                {label(copy.banner[0], layout.bannerLines[0].x, layout.bannerLines[0].base, T.banner)}
                {label(
                  <>
                    {copy.banner[1]}
                    <Text style={{ color: colors.secondary, fontWeight: '400' }}>{copy.banner[2]}</Text>
                  </>,
                  layout.bannerLines[1].x,
                  layout.bannerLines[1].base,
                  T.banner,
                )}
                {label(copy.banner[3], layout.bannerLines[2].x, layout.bannerLines[2].base, T.bannerGrey)}
              </Animated.View>
              <Animated.View style={[StyleSheet.absoluteFill, copyNew]}>
                {label(copy.verified, layout.bannerLines[0].x, layout.bannerLines[0].base, T.banner)}
              </Animated.View>
            </View>
          </Animated.View>

          {/* Receive / Send labels; their pills are on the canvas. */}
          {layout.pills.map((pl, i) => (
            <Animated.View key={i} style={[styles.abs, { left: X(pl.x), top: Y0 + X(pl.y), width: X(pl.w), height: X(pl.h) }, i === 0 ? pillStyle0 : pillStyle1]}>
              <View style={[StyleSheet.absoluteFill, { left: -X(pl.x), top: -(Y0 + X(pl.y)) }]}>
                {label(i === 0 ? copy.receive : copy.send, pl.text.x, pl.text.base, T.button)}
              </View>
            </Animated.View>
          ))}

          <Animated.View style={[StyleSheet.absoluteFill, txHeadStyle]}>
            {label(copy.transactions, layout.transTitle.x, layout.transTitle.base, T.title)}
            {label(copy.seeAll, layout.seeAll.text.x, layout.seeAll.text.base, T.pill)}
          </Animated.View>
          {layout.txRows.map((row, i) => (
            <Animated.View key={i} style={[StyleSheet.absoluteFill, i === 0 ? txRow0Style : txRow1Style]}>
              {label(copy.tx[i][0], row.title.x, row.title.base, T.merchant)}
              {label(copy.tx[i][1], row.sub.x, row.sub.base, T.bodyGrey)}
              {i === 0 ? right(copy.tx[i][2], row.amount.right, row.amount.base, T.amount) : null}
              {i === 1 ? (
                <Animated.Text style={[styles.abs, font(T.amount), styles.right, { left: X(row.amount.right) - BOX, width: BOX, top: top(row.amount.base, T.amount) }, greenStyle]}>
                  {copy.tx[i][2]}
                </Animated.Text>
              ) : null}
            </Animated.View>
          ))}

          <Animated.View style={[StyleSheet.absoluteFill, recentStyle]}>
            {label(copy.recent, layout.recent.a.x, layout.recent.a.base, T.title)}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, actionsStyle]}>
            {copy.actions.map(([title, sub], i) => {
              const b = actionBox(i);
              return (
                <View key={i} style={StyleSheet.absoluteFill}>
                  {label(title, b.x + ac.title.dx, ac.title.base, T.body)}
                  {label(sub, b.x + ac.sub.dx, ac.sub.base, T.sub)}
                </View>
              );
            })}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, namesStyle]}>
            {copy.payees.map((name, i) => {
              const b = payeeBox(i);
              return (
                <Text key={name} style={[styles.abs, font(T.body), styles.center, { left: X(b.x), width: X(b.w), top: top(py.name.base, T.body) }]}>
                  {name}
                </Text>
              );
            })}
            {label(copy.seeAll, layout.seeAll2.text.x, layout.seeAll2.text.base, T.pill)}
          </Animated.View>

          <Animated.View style={[StyleSheet.absoluteFill, analyticsStyle]}>
            {label(copy.analytics, layout.analytics.a.x, layout.analytics.a.base, T.title)}
            {label(copy.more, layout.more.text.x, layout.more.text.a, T.pill)}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, spentStyle]}>
            {label(copy.spent, ch.spent.a.x, ch.a.y + ch.spent.a.dy, T.spent)}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, valueStyle]}>
            <Odometer
              slots={[{ ch: '$' }, { from: 0, to: 9, enter: true }, { from: 0, to: 8, enter: true }, { from: 0, to: 3 }]}
              progress={spentValue}
              span={0.8}
              stagger={STAGGER.digits}
              fontSize={T.spentValue.size * FS}
              fontWeight={T.spentValue.weight}
              letterSpacing={T.spentValue.ls * FS}
              color={T.spentValue.color}
              left={X(ch.value.a.x)}
              top={top(ch.a.y + ch.value.a.dy, T.spentValue) - (Math.ceil(T.spentValue.size * FS * 1.2) - 1.193 * T.spentValue.size * FS) / 2}
            />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, labelAStyle]}>
            {right(copy.labelA, ch.label.a.right, ch.a.y + ch.label.a.dy, T.labelA)}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, labelBStyle]}>
            {right(copy.labelB, ch.label.b.right, ch.b.y + ch.label.b.dy, T.labelB)}
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, axisStyle]}>
            {copy.axis.map((d, i) => (
              <Text key={d} style={[styles.abs, font(T.axis), styles.center, { left: X(ch.axis.xs[i]) - 20, width: 40, top: top(ch.a.y + ch.axis.a, T.axis) }]}>
                {d}
              </Text>
            ))}
          </Animated.View>
        </View>

        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={hero} />
        </Canvas>
        <View
          accessible
          accessibilityLabel={walletState === 'funded'
            ? 'Wallet. Total balance 6,790 dollars and 22 cents. Transactions: Hearth Coffee, purchase 4 dollars and 85 cents. Dieter E., received 109 dollars and 25 cents. Recent payees: Konstantin, Vasya, Aleksa, Joshua. Spent this week 983 dollars.'
            : walletState === 'empty'
              ? 'Wallet. Total balance zero dollars. Verification pending. No recent payments. Spent this week zero dollars.'
              : walletState === 'activating' ? 'Wallet. Completing the first deposit preview.' : 'Wallet. Returning to the empty preview.'}
          style={[styles.abs, { left: X(45), top: Y0 + X(425), width: X(800), height: X(220) }]}
        />
        {actionReady && <View
          accessible
          accessibilityRole="button"
          accessibilityLabel={funded ? 'Debit card. Return to empty wallet.' : 'Complete verification and preview your first deposit.'}
          accessibilityHint="Double tap to preview the other wallet state. This is a local demonstration."
          onAccessibilityTap={() => {
            tapX.set((funded ? layout.card.b.cx : layout.banner.x + layout.banner.w / 2) * U);
            tapY.set(Y0 + (funded ? layout.card.b.cy : layout.banner.y + layout.banner.h / 2) * U);
            tapN.set(tapN.get() + 1);
          }}
          style={[styles.abs, funded
            ? { left: layout.card.b.cx * U - 40, top: Y0 + layout.card.b.cy * U - 24, width: 80, height: 48 }
            : { left: bn.x * U, top: Y0 + bn.y * U, width: bn.w * U, height: bn.h * U }]}
        />}
      </Animated.View>
    </GestureDetector>
    </ScrollView>
  );
}

function usePill(frame: SharedValue<Frame>, i: 0 | 1) {
  return useAnimatedStyle(() => {
    const k = i === 0 ? frame.get().pill0 : frame.get().pill1;
    return { opacity: Math.min(1, k * 2), transform: [{ scale: 0.9 + 0.1 * k }] };
  });
}

function useRise(frame: SharedValue<Frame>, key: 'txHead' | 'txRow0' | 'txRow1', U: number) {
  return useAnimatedStyle(() => {
    const k = frame.get()[key];
    return { opacity: k, transform: [{ translateY: 48 * U * (1 - k) }] };
  });
}

/** Text inside the chart card: it rides with the card (reflow2) and moves inside it (plot). */
function useInChart(frame: SharedValue<Frame>, a: { x: number; dy: number }, b: { x: number; dy: number }, U: number) {
  const ch = layout.chart;
  return useAnimatedStyle(() => {
    const f = frame.get();
    const cardTop = lerp(ch.a.y, ch.b.y, f.reflow2);
    const y = cardTop + lerp(a.dy, b.dy, f.plot) - (ch.a.y + a.dy);
    return { transform: [{ translateX: (lerp(a.x, b.x, f.plot) - a.x) * U }, { translateY: y * U }] };
  });
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  right: { textAlign: 'right' },
  center: { textAlign: 'center' },
});
