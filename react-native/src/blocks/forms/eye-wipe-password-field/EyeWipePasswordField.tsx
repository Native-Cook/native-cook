import { Canvas, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Keyboard, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FIELD, RADIUS, WipeField } from './WipeField';

type Props = {
  /** Shows the password, holds, and hides it again by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** What the two fields start with. */
  email?: string;
  password?: string;
  /** A password shorter than this cannot be sent. */
  minLength?: number;
  title?: string;
  subtitle?: string;
  emailPlaceholder?: string;
  submitLabel?: string;
  forgotLabel?: string;
  background?: string;
  /** The fields, and the title's ink. */
  fieldColor?: string;
  /** The Sign in button. */
  accent?: string;
  /** Called when the eye is pressed, with whether the password is now readable. */
  onToggle?: (shown: boolean) => void;
  /** Called by Sign in when the email looks like one and the password is long enough. */
  onSubmit?: (email: string, password: string) => void;
  onForgot?: () => void;
};

/** autoPlay: the eye is pressed at SHOW_AT and again at HIDE_AT of every LOOP seconds, each time for PRESS_FOR. */
export const LOOP = 6;
const SHOW_AT = 0.8;
const HIDE_AT = 3.5;
const PRESS_FOR = 0.12;
/** After a touch autoPlay waits this long before it starts over with the fields as they were. */
const USER_HOLD = 3;

const MARGIN = 32;
const MAX_WIDTH = 400;
const GAP = 14;
const BUTTON = 58;
const EMAIL_INK = '#aeb4e6';
const PLACEHOLDER = '#5d648f';

// An envelope in a 24 × 24 box.
const ICON_MAIL = 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z';
const ICON = 21;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A sign-in form on a flat blue screen: an email field, a password field and a button, kept quiet
 * so that the password field's eye is the subject. A press on it floods the dark field white from
 * the eye's disc and resolves the dots into the characters; a second press wipes it back.
 */
export function EyeWipePasswordField({
  autoPlay = false,
  email: firstEmail = 'alex@example.com',
  password: firstPassword = '91u2dhjk',
  minLength = 6,
  title = 'Welcome back',
  subtitle = 'Sign in to pick up where you left off.',
  emailPlaceholder = 'Email',
  submitLabel = 'Sign in',
  forgotLabel = 'Forgot password?',
  background = '#6fa0ee',
  fieldColor = '#121829',
  accent = '#3b3690',
  onToggle,
  onSubmit,
  onForgot,
}: Props) {
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [email, setEmail] = useState(firstEmail);
  const [password, setPassword] = useState(firstPassword);
  const [shown, setShown] = useState(false);
  // The eye held down by the autoPlay loop.
  const [held, setHeld] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const width = Math.min(window.width - MARGIN * 2, MAX_WIDTH);
  const valid = EMAIL.test(email.trim()) && password.length >= minLength;

  const toggle = (byItself: boolean) => {
    if (!byItself) tick();
    setShown((now) => !now);
    onToggle?.(!shown);
  };

  /** The form as it first was. */
  const reset = () => {
    Keyboard.dismiss();
    setEmail(firstEmail);
    setPassword(firstPassword);
    setShown(false);
  };

  // The loop reads the latest handlers when its timers fire.
  const latest = useRef({ toggle, reset });
  useEffect(() => {
    latest.current = { toggle, reset };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, act: () => void) => timers.push(setTimeout(act, ms));
    const cycle = (origin: number) => {
      const press = (seconds: number) => {
        after(origin + seconds * 1000 - Date.now(), () => setHeld(true));
        after(origin + (seconds + PRESS_FOR) * 1000 - Date.now(), () => {
          setHeld(false);
          latest.current.toggle(true);
        });
      };
      press(SHOW_AT);
      press(HIDE_AT);
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        latest.current.reset();
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from the hidden password a while later. */
  const touched = () => {
    setHeld(false);
    setTouches((n) => n + 1);
  };

  return (
    <Pressable
      style={[styles.screen, { backgroundColor: background, paddingTop: insets.top, paddingBottom: insets.bottom + 72 }]}
      accessible={false}
      onPress={() => {
        touched();
        Keyboard.dismiss();
      }}
    >
      <StatusBar style="dark" />
      <View style={{ width }}>
        <Text style={[styles.title, { color: fieldColor }]} numberOfLines={1}>
          {title}
        </Text>
        <Text style={[styles.subtitle, { color: fieldColor }]} numberOfLines={2}>
          {subtitle}
        </Text>

        <View style={[styles.field, { backgroundColor: fieldColor }]}>
          <Canvas style={styles.icon}>
            <Path path={ICON_MAIL} color="#ffffff" transform={[{ scale: ICON / 24 }]} />
          </Canvas>
          <TextInput
            value={email}
            onChangeText={(next) => {
              touched();
              setEmail(next);
            }}
            onPressIn={touched}
            style={styles.email}
            placeholder={emailPlaceholder}
            placeholderTextColor={PLACEHOLDER}
            selectionColor={EMAIL_INK}
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            spellCheck={false}
            accessibilityLabel={emailPlaceholder}
          />
        </View>

        <WipeField
          value={password}
          onChangeText={(next) => {
            touched();
            setPassword(next);
          }}
          shown={shown}
          onToggle={() => toggle(false)}
          onTouch={touched}
          held={held}
          width={width}
          fill={fieldColor}
        />

        <Pressable
          disabled={!valid}
          accessibilityRole="button"
          accessibilityState={{ disabled: !valid }}
          onPress={() => {
            touched();
            tick();
            Keyboard.dismiss();
            onSubmit?.(email.trim(), password);
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.button,
                {
                  backgroundColor: accent,
                  opacity: !valid ? 0.45 : pressed ? 0.8 : 1,
                  transform: [{ scale: pressed ? 0.98 : 1 }],
                  transitionProperty: ['opacity', 'transform'],
                  transitionDuration: 120,
                },
              ]}
            >
              <Text style={styles.buttonLabel} numberOfLines={1}>
                {submitLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>

        <Pressable
          style={styles.forgot}
          hitSlop={10}
          accessibilityRole="button"
          onPress={() => {
            touched();
            onForgot?.();
          }}
        >
          {({ pressed }) => (
            <Text style={[styles.forgotLabel, { color: fieldColor, opacity: pressed ? 0.45 : 0.7 }]} numberOfLines={1}>
              {forgotLabel}
            </Text>
          )}
        </Pressable>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  subtitle: {
    marginTop: 8,
    marginBottom: 32,
    fontSize: 16,
    lineHeight: 22,
    opacity: 0.62,
  },
  field: {
    height: FIELD,
    marginBottom: GAP,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
  },
  icon: {
    width: ICON,
    height: ICON,
    marginLeft: 33 - ICON / 2,
  },
  email: {
    flex: 1,
    height: FIELD,
    marginLeft: 64 - 33 - ICON / 2 - 3,
    paddingRight: 20,
    color: EMAIL_INK,
    fontSize: 17,
  },
  button: {
    height: BUTTON,
    marginTop: GAP + 10,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  forgot: {
    marginTop: 20,
    alignSelf: 'center',
  },
  forgotLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
});
