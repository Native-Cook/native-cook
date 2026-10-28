import { Canvas, ImageSVG, Picture, Skia } from '@shopify/react-native-skia';
import { memo } from 'react';
import { StyleSheet } from 'react-native';

import { TROPHY_REFERENCE_SVG } from './TrophySavingsMilestoneReferenceArt';
import { TROPHY_REFERENCE_PAPER_SVG } from './TrophySavingsMilestoneReferencePaper';

// Static code-native vectors are parsed once and reused by the renderers.
const TROPHY = Skia.SVG.MakeFromString(TROPHY_REFERENCE_SVG);
const PAPER = Skia.SVG.MakeFromString(TROPHY_REFERENCE_PAPER_SVG);

function makeBackdrop() {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 852));
  const paint = Skia.Paint();
  paint.setColor(Skia.Color('#EFEFEF'));
  canvas.drawRect(Skia.XYWHRect(0, 0, 393, 852), paint);
  paint.setAntiAlias(true);
  // Reference dot centres repeat about every 11.5 pt (e.g. x80.5/92/103.5,
  // y260/271.5/283). Quarter-point phase retains their visible ink at device scale.
  // Stop the printed hero field above the body; financial content stays flat #EFEFEF.
  for (let y = 7.25; y < 380; y += 11.5) {
    const fade = Math.max(0, Math.min(1, (380 - y) / 30));
    paint.setColor(Skia.Color(`rgba(0, 0, 0, ${0.09 * fade})`));
    for (let x = 0.25; x < 393; x += 11.5) {
      canvas.drawCircle(x, y, 0.55, paint);
    }
  }
  return recorder.finishRecordingAsPicture();
}

const BACKDROP = makeBackdrop();

export const TrophySavingsMilestoneBackdrop = memo(function TrophySavingsMilestoneBackdrop() {
  return (
    <Canvas style={styles.backdrop} pointerEvents="none" accessible={false}>
      <Picture picture={BACKDROP} />
      <ImageSVG svg={PAPER} width={393} height={350} />
    </Canvas>
  );
});

export const TrophySavingsMilestoneArtwork = memo(function TrophySavingsMilestoneArtwork() {
  return (
    <Canvas style={styles.trophy} pointerEvents="none" accessible={false}>
      <ImageSVG svg={TROPHY} width={176} height={214} />
    </Canvas>
  );
});

const styles = StyleSheet.create({
  backdrop: { position: 'absolute', width: 393, height: 852 },
  trophy: { width: 176, height: 214 },
});
