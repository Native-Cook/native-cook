/**
 * An incoming call answered by lifting a real receiver: an ivory bakelite handset hanging on its coiled cord in a dark,
 * warm room lit from the upper left, above an iOS call screen (the caller, the status, Decline and Accept). One light,
 * no neon: the handset and the cord are shaded by it, the cord's far half-turns fall into shadow. Sizes are points on
 * a 402 pt wide screen, scaled by width / 402; the type is the system face, as on the phone's own call screen.
 */
export const STAGE_WIDTH = 402;

export const COLORS = {
  // The room: near-black, a warm pool of light behind the receiver.
  page: '#0C0A09',
  pageTop: '#120F0D',
  pageBottom: '#070606',
  light: 'rgba(255,214,170,0.20)',
  lightEdge: 'rgba(255,214,170,0)',
  lightConnected: 'rgba(170,255,200,0.12)',
  // Ivory bakelite.
  ivoryHi: '#FFF9EE',
  ivory: '#EDE2CC',
  ivoryMid: '#D8CAAE',
  ivoryLo: '#B6A584',
  ivoryEdge: 'rgba(92,72,44,0.45)',
  cupLo: '#B9A889',
  cupHi: '#F6EEDC',
  hole: '#4A3D2B',
  specular: 'rgba(255,255,255,0.85)',
  shadow: 'rgba(0,0,0,0.55)',
  // The cord: the same plastic, darker where it turns away.
  cordFront: '#E2D6BE',
  cordBack: '#7E7058',
  cordHi: 'rgba(255,250,238,0.75)',
  cordEdge: 'rgba(60,46,28,0.55)',
  ink: '#F5F3F0',
  mute: 'rgba(245,243,240,0.6)',
  faint: 'rgba(245,243,240,0.4)',
  decline: '#FF3B30',
  accept: '#34C759',
  glyph: '#FFFFFF',
  glass: 'rgba(255,255,255,0.14)',
  glassEdge: 'rgba(255,255,255,0.2)',
  controlOn: 'rgba(255,255,255,0.92)',
  controlOnInk: '#16120F',
};

export const HANDSET = {
  // The receiver in its own frame (pt), origin where the cord enters its top cup, y down the handle.
  cupR: 30,
  topCupY: 30,
  bottomCupY: 166,
  waist: 13, // half the handle's width at its middle
  inner: 21, // the recessed face of each cup
  comX: 0, // centre of mass, for the pivot and the touch target
  comY: 98,
  hit: 200,
  restTilt: -0.33, // it hangs leaning a little, the top cup turned out to the right
};

export const CORD = {
  anchorY: -12,
  replicaAnchorDx: -26, // the cord hangs from 26 pt left of the screen's centre
  restRatio: 0.335, // the cord's end at rest, as a share of the height
  links: 14,
  turns: 26,
  wire: 46, // length of wire in one turn: a stretched coil has a longer pitch and a smaller radius
  tube: 4.2, // the wire's thickness
  maxRadius: 6.5,
  samples: 7, // per half-turn
};

export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, xxxl: 40 } as const;

export const LAYOUT = {
  side: 32,
  copyTopRatio: 0.64, // the caller block, below the lowest reach of the swing
  nameSize: 34,
  nameLine: 41,
  metaSize: 15,
  metaLine: 20,
  metaGap: 2,
  statusSize: 15,
  statusLine: 20,
  gap: 12,
  copyToPill: 24,
  buttonsBottom: 40,
  button: 72,
  buttonGap: 104, // from the centre to each button's centre
  labelSize: 13,
  labelGap: 10,
  glyph: 30,
};

export const COPY = {
  name: 'Mara Lindqvist',
  meta: 'mobile',
  ringing: 'Lift the receiver to answer',
  declined: 'Call declined',
  ended: 'Call ended',
  decline: 'Decline',
  accept: 'Accept',
  end: 'End',
  controls: [
    { name: 'mute', label: 'Mute', symbol: 'mic.slash.fill' },
    { name: 'keypad', label: 'Keypad', symbol: 'circle.grid.3x3.fill' },
    { name: 'speaker', label: 'Speaker', symbol: 'speaker.wave.2.fill' },
  ],
  a11yHandset: 'Receiver',
  a11yHint: 'Swipe up to answer, swipe down to decline',
};

export const REPLICA = false;

export const GLASS = {
  pillY: -(36 + 16 + 30), // centre of the control row above the End button's centre
  pillH: 60,
  controlH: 52,
  iconBox: 24,
};
