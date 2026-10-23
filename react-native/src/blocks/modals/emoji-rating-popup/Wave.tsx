import { Canvas, Group, LinearGradient, Path, Skia, vec } from '@shopify/react-native-skia';
import { type SharedValue, interpolateColor, useDerivedValue } from 'react-native-reanimated';

export const WAVE_HEIGHT = 108;

// The header's waves, in a box 320 wide: the colour field, a lighter wave over it and a pale crest.
const BASE = Skia.Path.MakeFromSVGString('M0 0H320V80C284 104 248 72 204 86C160 100 124 72 84 86C50 98 22 92 0 82Z')!;
const LIGHT = Skia.Path.MakeFromSVGString('M0 0H320V60C288 50 256 88 212 76C170 64 140 94 96 78C60 66 28 72 0 62Z')!;
const CREST = Skia.Path.MakeFromSVGString('M0 0H320V36C278 48 244 22 198 34C152 46 112 24 66 38C38 46 16 42 0 36Z')!;

type Props = {
  width: number;
  /** Where the dialog's mood is: 0 for none yet, then 1 to 5 stars. */
  mood: SharedValue<number>;
  /** The colour of the field at each mood, and of the wave over it. */
  field: string[];
  wave: string[];
};

const STOPS = [0, 1, 2, 3, 4, 5];

/** The wavy header of the dialog; its colour follows the rating. */
export function Wave({ width, mood, field, wave }: Props) {
  const fieldColor = useDerivedValue(() => interpolateColor(mood.get(), STOPS, field, 'RGB', { gamma: 1 }));
  const waveColor = useDerivedValue(() => interpolateColor(mood.get(), STOPS, wave, 'RGB', { gamma: 1 }));
  return (
    <Canvas style={{ width, height: WAVE_HEIGHT }}>
      <Group transform={[{ scaleX: width / 320 }]}>
        <Path path={BASE} color={fieldColor} />
        <Path path={LIGHT} color={waveColor} />
        <Path path={CREST}>
          <LinearGradient start={vec(0, 0)} end={vec(0, 48)} colors={['rgba(255,255,255,0.42)', 'rgba(255,255,255,0.12)']} />
        </Path>
      </Group>
    </Canvas>
  );
}
