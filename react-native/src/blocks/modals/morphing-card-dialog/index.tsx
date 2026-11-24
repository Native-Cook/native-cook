import { MorphingCardDialog } from './MorphingCardDialog';

export { LOOP, MorphingCardDialog, type MorphingCardDialogProps } from './MorphingCardDialog';
export { MorphingDialog, MorphingLayer, MorphingScene, MorphingTrigger, useMorphingDialog } from './MorphingDialog';

export default function MorphingCardDialogDemo() {
  return <MorphingCardDialog autoPlay />;
}
