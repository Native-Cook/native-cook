import { QuickAddMorph } from './QuickAddMorph';

export { LOOP } from './motion';
export { MorphComposer, useComposerMotion, type ComposerDraft, type ComposerGeometry, type ComposerMotion } from './MorphComposer';
export { QuickAddMorph, type QuickAddMorphProps } from './QuickAddMorph';

export default function QuickAddMorphDemo() {
  return <QuickAddMorph autoPlay />;
}
