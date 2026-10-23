import { Canvas, Circle, Group, LinearGradient, Path, RadialGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Keyboard, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The field's text. Leave it out and the component keeps the text itself. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Types a prompt, picks a context and a skill from the menu, clears and starts over, in a loop. */
  autoPlay?: boolean;
  placeholder?: string;
  /** The chips in the bottom row before anything is chosen. */
  contexts?: string[];
  contextTitle?: string;
  /** Commands that become a chip when chosen. */
  contextCommands?: string[];
  skillTitle?: string;
  /** Commands that stay in the text as a token when chosen. */
  skillCommands?: string[];
  /** Colour of the chips' dots, the skill tokens and the caret, as `#rrggbb`. */
  accent?: string;
  /** Called with the command's name when a row is chosen. */
  onCommand?: (name: string) => void;
  /** Called with the text and the chips when the keyboard's send key is pressed. */
  onSend?: (text: string, chips: string[]) => void;
};

const WIDTH = 350;
// The composer, and the whole box with the menu open under it.
const COMPOSER_HEIGHT = 116;
const HEIGHT = 392;
const RADIUS = 28;
const INSET = 22;
const FONT_SIZE = 18;
// Centre line of the text row.
const TEXT_Y = 35;
const TOKEN_HEIGHT = 27;
// A token's box is this much wider than its letters on each side.
const TOKEN_PAD = 5;

const MAX_CHIPS = 3;
const CHIP_INSET = 18;
const CHIP_Y = 66;
const CHIP_HEIGHT = 34;
const CHIP_GAP = 8;

const MAX_ROWS = 12;
const MENU_Y = 56;
const MENU_WIDTH = 196;
const MENU_PAD = 8;
const ROW_HEIGHT = 30;
const ROW_INSET = 18;
const HEADER_HEIGHT = 26;
// The hairline between the groups, with the space above and below it.
const DIVIDER_HEIGHT = 13;
// Rows fade in this long, each this much after the one before, when the menu opens.
const ROW_FADE = 0.14;
const ROW_STAGGER = 0.03;

// How long a chosen context's name takes to reach the chip row.
const FLY_TIME = 0.42;
const BLINK = 1.1;

// autoPlay period, seconds.
const LOOP = 9.6;
// After a touch, or once the field is left empty, the loop waits this long before it starts over.
const RESUME = 3;

const TAU = Math.PI * 2;

const CONTEXTS = ['Design'];
const CONTEXT_COMMANDS = ['design-file', 'from-brief', 'from-code', 'past-output'];
const SKILL_COMMANDS = ['contrast-check', 'give-variants', 'critique', 'ally-check'];

// A chip's check mark in its 20-point dot, and its cross in a 9-point box.
const CHECK = 'M6 10.4L8.9 13.2L14.2 7.2';
const CROSS = 'M1.5 1.5L7.5 7.5M7.5 1.5L1.5 7.5';

// What a line of the script does.
const TYPE = 0;
const MOVE = 1;
const CHOOSE = 2;
const CLEAR = 3;

type Line = { t: number; act: number; letter: string; by: number };

/** Seconds between letters as the loop types: uneven like a hand, the same every time. */
const TYPING_RHYTHM = [0.09, 0.07, 0.11, 0.06, 0.13, 0.08, 0.06, 0.1, 0.07, 0.12];

function typing(text: string, start: number): Line[] {
  let t = start;
  return Array.from(text).map((letter, index) => {
    const line = { t, act: TYPE, letter, by: 0 };
    t += TYPING_RHYTHM[index % TYPING_RHYTHM.length] + (letter === ' ' ? 0.08 : 0);
    return line;
  });
}

/**
 * The autoPlay loop: type up to the slash, walk the highlight down, filter the list and step back
 * up to a context and pick it, then type a second slash, filter to a skill and pick that, and clear.
 */
const SCRIPT: Line[] = [
  ...typing('I need help with /', 0.8),
  { t: 3.0, act: MOVE, letter: '', by: 1 },
  { t: 3.25, act: MOVE, letter: '', by: 1 },
  ...typing('de', 3.7),
  { t: 4.25, act: MOVE, letter: '', by: -1 },
  { t: 4.7, act: CHOOSE, letter: '', by: 0 },
  ...typing('/cr', 5.5),
  { t: 6.6, act: CHOOSE, letter: '', by: 0 },
  { t: 8.3, act: CLEAR, letter: '', by: 0 },
];

/** Per-frame state: the menu, its rows and everything that eases after them. */
type Sim = {
  time: number;
  clock: number;
  /** The next line of the autoPlay script. */
  step: number;
  looping: boolean;
  /** Seconds the loop still waits after a touch. */
  hold: number;
  /** The text in the field is the loop's, and what that text is. */
  scripted: boolean;
  text: string;
  lastEdit: number;
  open: boolean;
  openAt: number;
  /** Left edge of the menu; it stays where it was while the menu closes. */
  menuX: number;
  /** The highlighted command. */
  highlight: number;
  clearing: boolean;
  bornAt: number[];
  flyAt: number;
  flyFromX: number;
  flyFromY: number;
  flyToX: number;
  flyToY: number;
  // Springs: a position and its velocity.
  menu: number;
  menuV: number;
  box: number;
  boxV: number;
  bar: number;
  barV: number;
  tint: number;
  fade: number;
  rows: number[];
  headers: number[];
  divider: number;
  barAlpha: number;
  // Where the menu's parts are this frame, from its top edge.
  rowY: number[];
  headerY: number[];
  dividerY: number;
  height: number;
};

function rest(): Sim {
  return {
    time: 0,
    clock: 0,
    step: 0,
    looping: false,
    hold: 0,
    scripted: false,
    text: '',
    lastEdit: -10,
    open: false,
    openAt: 0,
    menuX: 0,
    highlight: 0,
    clearing: false,
    bornAt: new Array(MAX_CHIPS).fill(-10),
    flyAt: -10,
    flyFromX: 0,
    flyFromY: 0,
    flyToX: 0,
    flyToY: 0,
    menu: 0,
    menuV: 0,
    box: 1,
    boxV: 0,
    bar: 0,
    barV: 0,
    tint: 1,
    fade: 1,
    rows: new Array(MAX_ROWS).fill(1),
    headers: [1, 1],
    divider: 1,
    barAlpha: 0,
    rowY: new Array(MAX_ROWS).fill(0),
    headerY: [0, 0],
    dividerY: 0,
    height: 0,
  };
}

function touch(s: Sim) {
  'worklet';
  s.hold = RESUME;
}

function firstMatch(matches: boolean[]) {
  'worklet';
  const at = matches.indexOf(true);
  return at < 0 ? 0 : at;
}

function move(s: Sim, by: number, matches: boolean[]) {
  'worklet';
  const shown: number[] = [];
  for (let i = 0; i < matches.length; i++) if (matches[i]) shown.push(i);
  const at = shown.indexOf(s.highlight);
  if (at < 0) return;
  s.highlight = shown[Math.min(Math.max(at + by, 0), shown.length - 1)];
}

/** Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate. */
function approach(x: number, target: number, rate: number, dt: number) {
  'worklet';
  return x + (target - x) * (1 - Math.exp(-rate * dt));
}

/** The new velocity of a spring (mass 1, stiffness omega², damping 2·zeta·omega) after `dt`. */
function sprung(x: number, v: number, target: number, omega: number, zeta: number, dt: number) {
  'worklet';
  return v + (omega * omega * (target - x) - 2 * zeta * omega * v) * dt;
}

/** How far a spring released `t` seconds ago has come from 0 to 1, overshoot included. */
function settle(t: number, omega: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

function advance(
  s: Sim,
  dt: number,
  engaged: boolean,
  open: boolean,
  anchor: number,
  matches: boolean[],
  split: number,
  still: boolean,
  typeText: (text: string) => void,
  choose: (index: number) => void,
  reset: () => void,
) {
  'worklet';
  s.time += dt;

  if (engaged) s.hold = RESUME;
  if (s.hold > 0) {
    s.hold -= dt;
    if (s.hold <= 0 && s.looping) {
      s.clock = 0;
      s.step = 0;
      scheduleOnRN(reset);
    }
  } else if (s.looping) {
    s.clock += dt;
    if (s.clock >= LOOP) {
      s.clock -= LOOP;
      s.step = 0;
    }
    while (s.step < SCRIPT.length && s.clock >= SCRIPT[s.step].t) {
      const line = SCRIPT[s.step];
      if (line.act === TYPE) {
        // The loop keeps its own copy of the text, so a letter is never typed onto a stale one.
        s.text += line.letter;
        s.scripted = true;
        s.lastEdit = s.time;
        scheduleOnRN(typeText, s.text);
      } else if (line.act === MOVE) {
        move(s, line.by, matches);
      } else if (line.act === CHOOSE) {
        scheduleOnRN(choose, s.highlight);
      } else {
        s.clearing = true;
      }
      s.step += 1;
    }
  }

  let first = false;
  let second = false;
  for (let i = 0; i < matches.length; i++) {
    if (matches[i] && i < split) first = true;
    if (matches[i] && i >= split) second = true;
  }
  if (open && !s.open) {
    s.openAt = s.time;
    s.box = still ? 1 : 0.5;
    s.boxV = 0;
    s.highlight = firstMatch(matches);
    // A menu that opens shows its list as it is; only what is typed afterwards folds rows away.
    for (let i = 0; i < matches.length; i++) s.rows[i] = matches[i] ? 1 : 0;
    s.headers[0] = first ? 1 : 0;
    s.headers[1] = second ? 1 : 0;
    s.divider = first && second ? 1 : 0;
  }
  s.open = open;
  if (open) s.menuX = anchor;
  if (open && !(s.highlight < matches.length && matches[s.highlight])) s.highlight = firstMatch(matches);
  if (s.clearing && s.fade < 0.03) {
    s.clearing = false;
    scheduleOnRN(reset);
  }

  if (still) {
    s.menu = approach(s.menu, open ? 1 : 0, 14, dt);
  } else {
    s.menuV = sprung(s.menu, s.menuV, open ? 1 : 0, 20, 0.65, dt);
    s.menu += s.menuV * dt;
  }
  s.boxV = sprung(s.box, s.boxV, 1, 26, 0.45, dt);
  s.box += s.boxV * dt;
  s.tint = approach(s.tint, 1, 9, dt);
  s.fade = approach(s.fade, s.clearing ? 0 : 1, 12, dt);

  // While the menu is closed its rows stay as they were, so it closes in one piece.
  if (open) {
    for (let i = 0; i < matches.length; i++) s.rows[i] = approach(s.rows[i], matches[i] ? 1 : 0, 16, dt);
    s.headers[0] = approach(s.headers[0], first ? 1 : 0, 16, dt);
    s.headers[1] = approach(s.headers[1], second ? 1 : 0, 16, dt);
    s.divider = approach(s.divider, first && second ? 1 : 0, 16, dt);
  }
  let y = MENU_PAD;
  for (let i = 0; i < matches.length; i++) {
    if (i === 0 || i === split) {
      if (i === split) {
        s.dividerY = y;
        y += DIVIDER_HEIGHT * s.divider;
      }
      s.headerY[i === 0 ? 0 : 1] = y;
      y += HEADER_HEIGHT * s.headers[i === 0 ? 0 : 1];
    }
    s.rowY[i] = y;
    y += ROW_HEIGHT * s.rows[i];
  }
  s.height = y + MENU_PAD;

  const target = s.highlight < matches.length ? s.rowY[s.highlight] : 0;
  if (!open || still || s.barAlpha < 0.05) {
    s.bar = target;
    s.barV = 0;
  } else {
    s.barV = sprung(s.bar, s.barV, target, 24, 0.8, dt);
    s.bar += s.barV * dt;
  }
  s.barAlpha = approach(s.barAlpha, open && (first || second) ? 1 : 0, 14, dt);
}

type Parsed = { prefix: string; token: string | null; skills: { before: string; token: string }[] };

/** The token being typed (a word that starts with "/" at the end of the text) and the chosen skills. */
function parse(text: string, skillCommands: string[]): Parsed {
  const parsed: Parsed = { prefix: text, token: null, skills: [] };
  let before = '';
  const words = text.split(' ');
  words.forEach((word, index) => {
    if (word.startsWith('/')) {
      if (index === words.length - 1) {
        parsed.prefix = before;
        parsed.token = word;
      } else if (skillCommands.includes(word.slice(1))) {
        parsed.skills.push({ before, token: word });
      }
    }
    before += `${word} `;
  });
  return parsed;
}

// A piece of the field's text, as the row of boxes sees it.
const PLAIN = 0;
const SKILL = 1;
const ACTIVE = 2;

/** The text cut at its tokens, in order, so a row of invisible copies puts each box behind its letters. */
function pieces(text: string, parsed: Parsed) {
  const list: { text: string; kind: number }[] = [];
  let at = 0;
  for (const skill of parsed.skills) {
    list.push({ text: text.slice(at, skill.before.length), kind: PLAIN });
    list.push({ text: skill.token, kind: SKILL });
    at = skill.before.length + skill.token.length;
  }
  if (parsed.token != null) {
    list.push({ text: text.slice(at, parsed.prefix.length), kind: PLAIN });
    list.push({ text: parsed.token, kind: ACTIVE });
  } else {
    list.push({ text: text.slice(at), kind: PLAIN });
  }
  return list.filter((piece) => piece.text.length > 0);
}

/** A text is measured without the spaces at its end; no-break spaces are as wide and always count. */
function solid(text: string) {
  return text.replace(/ /g, ' ');
}

/** `#rrggbb` with an opacity, as `#rrggbbaa`. */
function withAlpha(color: string, alpha: number) {
  return `${color}${Math.round(alpha * 255)
    .toString(16)
    .padStart(2, '0')}`;
}

/** About how wide a chip is for its label: enough to aim the flying name at the next free place. */
function chipWidth(label: string) {
  return 58 + 8.3 * label.length;
}

function Chip({
  sim,
  index,
  label,
  fixed,
  still,
  accent,
  onRemove,
}: {
  sim: SharedValue<Sim>;
  index: number;
  label: string;
  /** One of the chips the row starts with: it stays when the rest is cleared. */
  fixed: boolean;
  still: boolean;
  accent: string;
  onRemove: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const pop = still ? 1 : settle(s.time - s.bornAt[index], 18, 0.5);
    return {
      opacity: (s.time >= s.bornAt[index] ? Math.min(1, Math.max(0, pop * 2)) : 0) * (fixed ? 1 : s.fade),
      transform: [{ scale: 0.6 + 0.4 * pop }],
    };
  });

  return (
    <Animated.View style={style}>
      <Pressable style={styles.chip} onPress={onRemove} accessibilityRole="button" accessibilityLabel={`Remove ${label}`}>
        <Canvas style={styles.chipDot}>
          <Circle cx={10} cy={10} r={10}>
            <LinearGradient start={vec(4, 0)} end={vec(16, 20)} colors={['#86b1ff', accent, '#3566d6']} />
          </Circle>
          <Path path={CHECK} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color="#ffffff" />
        </Canvas>
        <Text style={styles.chipText} numberOfLines={1}>
          {label}
        </Text>
        <Canvas style={styles.chipCross}>
          <Path path={CROSS} style="stroke" strokeWidth={1.3} strokeCap="round" color="#9aa3b8" />
        </Canvas>
      </Pressable>
    </Animated.View>
  );
}

function Header({ sim, group, title }: { sim: SharedValue<Sim>; group: number; title: string }) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.headers[group] * s.headers[group], transform: [{ translateY: s.headerY[group] }] };
  });

  return (
    <Animated.View style={[styles.header, style]}>
      <Text style={styles.headerText} numberOfLines={1}>
        {title}
      </Text>
    </Animated.View>
  );
}

function Row({
  sim,
  index,
  name,
  order,
  enabled,
  onPress,
}: {
  sim: SharedValue<Sim>;
  index: number;
  name: string;
  /** How many matching rows come before this one: rows fade in one after another as the menu opens. */
  order: number;
  enabled: boolean;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const alpha = s.rows[index] * Math.min(1, Math.max(0, (s.time - s.openAt - order * ROW_STAGGER) / ROW_FADE));
    return {
      // A row that folds away is cut off at its shrinking height, so it never runs into the next one.
      height: ROW_HEIGHT * s.rows[index],
      opacity: alpha * alpha,
      transform: [{ translateY: s.rowY[index] }],
    };
  });

  return (
    <Animated.View style={[styles.row, style]} pointerEvents={enabled ? 'auto' : 'none'}>
      <Pressable style={styles.rowButton} onPress={onPress} accessibilityRole="button">
        <Text style={styles.rowText} numberOfLines={1}>
          {name}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

/**
 * A prompt field with a command menu. Typing "/" boxes the character as a token and opens the menu
 * right under it; more letters filter the list, the rows that don't match folding away while a
 * highlight keeps to the first one that does. Choosing a context flies its name down into the chip
 * row and takes the token out of the text; choosing a skill leaves it in the text as a tinted token.
 */
export function SlashCommandInput({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Ask, or type / for commands',
  contexts = CONTEXTS,
  contextTitle = 'CONTEXT',
  contextCommands = CONTEXT_COMMANDS,
  skillTitle = 'SKILLS',
  skillCommands = SKILL_COMMANDS,
  accent = '#4f8bff',
  onCommand,
  onSend,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [own, setOwn] = useState('');
  const [focused, setFocused] = useState(false);
  const [chips, setChips] = useState(() => contexts.slice(0, MAX_CHIPS));
  const [flyLabel, setFlyLabel] = useState('');
  // Width of the text before the token being typed: the menu hangs from its end.
  const [prefixWidth, setPrefixWidth] = useState(0);
  const current = value ?? own;

  const commands = [...contextCommands, ...skillCommands].slice(0, MAX_ROWS);
  const split = Math.min(contextCommands.length, MAX_ROWS);
  const parsed = parse(current, skillCommands);
  const open = parsed.token != null;
  const query = (parsed.token ?? '/').slice(1).toLowerCase();
  const matches = commands.map((name) => query.length === 0 || name.toLowerCase().includes(query));
  // Left edge of the menu: it hangs from the token, but never leaves the composer.
  const anchor = Math.min(INSET + prefixWidth - 6, WIDTH - MENU_WIDTH - 8);
  const busy = current.length > 0 || chips.length > contexts.length;

  const sim = useSharedValue<Sim>(rest());

  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      return s;
    });
  }, [auto, sim]);

  const store = (text: string) => {
    if (value == null) setOwn(text);
    onChangeText?.(text);
  };

  const buzz = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const reset = () => {
    store('');
    setChips(contexts.slice(0, MAX_CHIPS));
    sim.modify((s) => {
      'worklet';
      s.text = '';
      s.scripted = false;
      s.clearing = false;
      for (let i = 0; i < MAX_CHIPS; i++) s.bornAt[i] = -10;
      return s;
    });
  };

  const choose = (index: number, byUser: boolean) => {
    if (parsed.token == null || index < 0 || index >= commands.length) return;
    const name = commands[index];
    if (index < contextCommands.length) {
      // The row holds what fits: the oldest chosen context makes room for a new one.
      const kept = [...chips];
      let dropped = 0;
      while (
        kept.length > contexts.length &&
        kept.reduce((sum, label) => sum + chipWidth(label) + CHIP_GAP, chipWidth(name)) > WIDTH - CHIP_INSET * 2
      ) {
        kept.splice(contexts.length, 1);
        dropped += 1;
      }
      if (kept.length >= MAX_CHIPS) return;
      const text = parsed.prefix;
      // The name lands about where its chip will be; the chip pops in under it.
      const slot = kept.reduce((sum, label) => sum + chipWidth(label) + CHIP_GAP, CHIP_INSET);
      const oldest = contexts.length;
      const place = kept.length;
      store(text);
      setFlyLabel(name);
      setChips([...kept, name]);
      sim.modify((s) => {
        'worklet';
        for (let i = 0; i < dropped; i++) {
          s.bornAt.splice(oldest, 1);
          s.bornAt.push(-10);
        }
        s.flyFromX = s.menuX + ROW_INSET;
        s.flyFromY = MENU_Y + s.rowY[index] + ROW_HEIGHT / 2;
        s.flyToX = slot + 34;
        s.flyToY = CHIP_Y + CHIP_HEIGHT / 2;
        s.flyAt = s.time;
        s.bornAt[place] = s.time + (reducedMotion ? 0 : FLY_TIME * 0.75);
        s.text = text;
        s.lastEdit = s.time;
        if (byUser) s.scripted = false;
        return s;
      });
    } else {
      const text = `${parsed.prefix}/${name} `;
      store(text);
      sim.modify((s) => {
        'worklet';
        s.tint = 0;
        s.text = text;
        s.lastEdit = s.time;
        if (byUser) s.scripted = false;
        return s;
      });
    }
    if (byUser) {
      buzz();
      onCommand?.(name);
    }
  };

  const typeText = (text: string) => store(text);
  const chooseForLoop = (index: number) => choose(index, false);
  const chooseByHand = (index: number) => choose(index, true);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    sim.modify((s) => {
      'worklet';
      advance(s, dt, focused || (!s.scripted && busy), open, anchor, matches, split, reducedMotion, typeText, chooseForLoop, reset);
      return s;
    });
  });

  // A tap on the field takes it over from the loop: what the loop typed and chose is cleared.
  const takeOver = () => {
    setFocused(true);
    sim.modify((s) => {
      'worklet';
      if (s.scripted) scheduleOnRN(reset);
      return s;
    });
  };

  const typedByHand = (text: string) => {
    store(text);
    sim.modify((s) => {
      'worklet';
      s.text = text;
      s.scripted = false;
      s.lastEdit = s.time;
      return s;
    });
  };

  const pick = (index: number) => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      s.highlight = index;
      return s;
    });
    choose(index, true);
  };

  const remove = (index: number) => {
    setChips(chips.filter((_, at) => at !== index));
    sim.modify((s) => {
      'worklet';
      touch(s);
      s.bornAt.splice(index, 1);
      s.bornAt.push(-10);
      return s;
    });
    buzz();
  };

  // The send key picks the highlighted row while the menu is open, and sends the prompt otherwise.
  const submit = () => {
    if (open) {
      sim.modify((s) => {
        'worklet';
        scheduleOnRN(chooseByHand, s.highlight);
        return s;
      });
      return;
    }
    const sent = current.trim();
    if (sent.length === 0) return;
    Keyboard.dismiss();
    onSend?.(sent, chips);
    sim.modify((s) => {
      'worklet';
      s.clearing = true;
      return s;
    });
  };

  const activeBoxStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: Math.min(1, Math.max(0, s.box * 2 - 1)) * s.fade, transform: [{ scale: s.box }] };
  });

  const skillBoxStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.tint * s.fade };
  });

  const fieldStyle = useAnimatedStyle(() => ({ opacity: sim.get().fade }));

  const caretStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const sinceEdit = s.time - s.lastEdit;
    const blink = sinceEdit < 0.5 ? 1 : Math.min(1, Math.max(0, 0.5 + 3 * Math.sin((TAU * sinceEdit) / BLINK)));
    return { opacity: s.scripted && !focused && current.length > 0 ? blink * s.fade : 0 };
  });

  const menuStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return {
      left: s.menuX,
      height: Math.max(MENU_PAD * 2, s.height),
      opacity: Math.min(1, Math.min(1, Math.max(0, s.menu)) * 1.6),
      // It grows out of the token above its top-left corner.
      transform: [{ scale: reducedMotion ? 1 : 0.7 + 0.3 * s.menu }],
    };
  });

  const barStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.barAlpha, transform: [{ translateY: s.bar }] };
  });

  const dividerStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.divider * s.divider, transform: [{ translateY: s.dividerY + DIVIDER_HEIGHT / 2 }] };
  });

  const flyStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const at = (s.time - s.flyAt) / FLY_TIME;
    const p = Math.min(1, Math.max(0, at));
    // Sideways first, then down: the name swings out of the menu and drops into the row.
    const across = 1 - Math.pow(1 - p, 3);
    const down = p * p * (3 - 2 * p);
    return {
      // Under reduced motion nothing flies: the chip simply appears in the row.
      opacity: reducedMotion || at < 0 || at >= 1 ? 0 : Math.min(1, (1 - at) / 0.25),
      transform: [
        { translateX: s.flyFromX + (s.flyToX - s.flyFromX) * across },
        { translateY: s.flyFromY + (s.flyToY - s.flyFromY) * down - 9 },
      ],
    };
  });

  return (
    <View style={styles.root}>
      {/* A faint blue light under the composer's right corner, as on the reference's backdrop. */}
      <Canvas style={styles.light}>
        <Group transform={[{ scaleY: 0.55 }]}>
          <Circle cx={150} cy={150} r={150}>
            <RadialGradient c={vec(150, 150)} r={150} colors={[withAlpha(accent, 0.15), withAlpha(accent, 0)]} />
          </Circle>
        </Group>
      </Canvas>

      <View style={styles.composer} />

      {/* The boxes behind the tokens: the one being typed, and the skills already chosen. */}
      <View style={styles.ghostRow} pointerEvents="none">
        {pieces(current, parsed).map((piece, index) =>
          piece.kind === PLAIN ? (
            <Text key={index} style={styles.ghost} numberOfLines={1}>
              {solid(piece.text)}
            </Text>
          ) : (
            <View key={index}>
              <Animated.View
                style={[
                  styles.tokenBox,
                  piece.kind === ACTIVE
                    ? styles.activeBox
                    : { backgroundColor: withAlpha(accent, 0.16), borderColor: withAlpha(accent, 0.5) },
                  piece.kind === ACTIVE ? activeBoxStyle : skillBoxStyle,
                ]}
              />
              <Text style={styles.ghost} numberOfLines={1}>
                {solid(piece.text)}
              </Text>
            </View>
          ),
        )}
      </View>
      <Text
        style={[styles.ghost, styles.probe]}
        numberOfLines={1}
        onLayout={(event) => setPrefixWidth(event.nativeEvent.layout.width)}
      >
        {solid(parsed.prefix)}
      </Text>

      <Animated.View style={[styles.field, fieldStyle]}>
        <TextInput
          style={styles.input}
          value={current}
          onChangeText={typedByHand}
          placeholder={placeholder}
          placeholderTextColor="#6f6f6f"
          selectionColor={accent}
          cursorColor={accent}
          autoCorrect={false}
          returnKeyType="send"
          submitBehavior="submit"
          onSubmitEditing={submit}
          onFocus={takeOver}
          onBlur={() => setFocused(false)}
        />
      </Animated.View>

      {/* The loop's own caret, right after the last letter it typed. */}
      <View style={styles.ghostRow} pointerEvents="none">
        <Text style={styles.ghost} numberOfLines={1}>
          {solid(current)}
        </Text>
        <Animated.View style={[styles.caret, { backgroundColor: accent }, caretStyle]} />
      </View>

      <View style={styles.chips}>
        {chips.map((label, index) => (
          <Chip
            key={index}
            sim={sim}
            index={index}
            label={label}
            fixed={index < contexts.length}
            still={reducedMotion}
            accent={accent}
            onRemove={() => remove(index)}
          />
        ))}
      </View>

      {/* The command menu: two titled groups, a hairline between them, and the highlight behind one row. */}
      <Animated.View style={[styles.menu, menuStyle]} pointerEvents={open ? 'box-none' : 'none'}>
        <View style={styles.menuBody}>
          <Animated.View style={[styles.bar, barStyle]} />
          <Header sim={sim} group={0} title={contextTitle} />
          <Header sim={sim} group={1} title={skillTitle} />
          <Animated.View style={[styles.divider, dividerStyle]} />
          {commands.map((name, index) => (
            <Row
              key={index}
              sim={sim}
              index={index}
              name={name}
              order={matches.slice(0, index).filter(Boolean).length}
              enabled={matches[index] && open}
              onPress={() => pick(index)}
            />
          ))}
        </View>
        <View style={styles.menuEdge} pointerEvents="none" />
      </Animated.View>

      {/* The chosen context's name, on its way from the menu to the chip row. */}
      <Animated.View style={[styles.fly, flyStyle]} pointerEvents="none">
        <Text style={styles.flyText} numberOfLines={1}>
          {flyLabel}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  // A 300-point circle squashed to 0.55 of its height, 100 points in and 20 above the box.
  light: {
    position: 'absolute',
    left: 100,
    top: -20 + (300 - 300 * 0.55) / 2,
    width: 300,
    height: 300 * 0.55,
  },
  composer: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: COMPOSER_HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#1b1b1b',
    borderWidth: 1,
    borderColor: '#2c2c2c',
  },
  // An invisible copy of the field's text, laid out where the field draws it.
  ghostRow: {
    position: 'absolute',
    left: INSET,
    top: TEXT_Y - 14,
    height: 28,
    flexDirection: 'row',
    alignItems: 'center',
  },
  ghost: {
    color: 'transparent',
    fontSize: FONT_SIZE,
  },
  probe: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  tokenBox: {
    position: 'absolute',
    left: -TOKEN_PAD,
    right: -TOKEN_PAD,
    top: '50%',
    marginTop: -TOKEN_HEIGHT / 2,
    height: TOKEN_HEIGHT,
    borderRadius: 6,
    borderWidth: 1,
  },
  activeBox: {
    backgroundColor: '#262626',
    borderColor: '#3a3a3a',
  },
  field: {
    position: 'absolute',
    left: INSET,
    top: TEXT_Y - 14,
    width: WIDTH - INSET * 2,
    height: 28,
  },
  input: {
    height: 28,
    padding: 0,
    color: '#e6e6e6',
    fontSize: FONT_SIZE,
  },
  caret: {
    width: 2,
    height: 21,
    marginLeft: 1,
    borderRadius: 1,
  },
  chips: {
    position: 'absolute',
    left: CHIP_INSET,
    top: CHIP_Y,
    width: WIDTH - CHIP_INSET * 2,
    height: CHIP_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: CHIP_GAP,
    overflow: 'hidden',
  },
  // The hairline takes a point of the padding on each side.
  chip: {
    height: CHIP_HEIGHT,
    paddingLeft: 6,
    paddingRight: 11,
    borderRadius: CHIP_HEIGHT / 2,
    backgroundColor: '#232a3a',
    borderWidth: 1,
    borderColor: '#2f3a52',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  chipDot: {
    width: 20,
    height: 20,
  },
  chipText: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  chipCross: {
    width: 9,
    height: 9,
    marginLeft: 3,
  },
  menu: {
    position: 'absolute',
    left: 0,
    top: MENU_Y,
    width: MENU_WIDTH,
    borderRadius: 16,
    transformOrigin: 'left top',
    boxShadow: '0px 12px 36px rgba(0, 0, 0, 0.45)',
  },
  menuBody: {
    flex: 1,
    borderRadius: 16,
    backgroundColor: '#1e1e1e',
    overflow: 'hidden',
  },
  menuEdge: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#333333',
  },
  bar: {
    position: 'absolute',
    left: 6,
    top: 0,
    width: MENU_WIDTH - 12,
    height: ROW_HEIGHT,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  header: {
    position: 'absolute',
    left: ROW_INSET,
    top: 0,
    width: MENU_WIDTH - ROW_INSET * 2,
    height: HEADER_HEIGHT,
    justifyContent: 'center',
  },
  headerText: {
    color: '#777777',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '500',
    letterSpacing: 0.9,
  },
  divider: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: MENU_WIDTH,
    height: 1,
    backgroundColor: '#333333',
  },
  row: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: MENU_WIDTH,
    overflow: 'hidden',
  },
  rowButton: {
    height: ROW_HEIGHT,
    paddingHorizontal: ROW_INSET,
    justifyContent: 'center',
  },
  rowText: {
    color: '#ededed',
    fontSize: 15,
    lineHeight: 20,
  },
  fly: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  flyText: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 18,
    fontWeight: '500',
  },
});
