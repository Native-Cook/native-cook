import { ComponentStage } from '@/shared/component-stage';

import { ToneRewriteComposer } from './ToneRewriteComposer';

export default function ToneRewriteComposerDemo() {
  return (
    <ComponentStage background="#d3daeb" height={420}>
      <ToneRewriteComposer autoPlay />
    </ComponentStage>
  );
}
