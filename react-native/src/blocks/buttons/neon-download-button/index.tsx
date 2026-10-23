import { ComponentStage } from '@/shared/component-stage';

import { NeonDownloadButton } from './NeonDownloadButton';

export default function NeonDownloadButtonDemo() {
  return (
    <ComponentStage background="#313536" height={260}>
      <NeonDownloadButton autoPlay />
    </ComponentStage>
  );
}
