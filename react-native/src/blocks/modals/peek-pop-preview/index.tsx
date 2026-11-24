import { PeekPopPreview } from './PeekPopPreview';

export { LOOP, PeekPopPreview, type PeekPopPreviewProps } from './PeekPopPreview';
export { PeekActions, PeekBackdrop, PeekFrame, PeekScene, PeekTrigger, usePeekPreview } from './PeekPreview';

export default function PeekPopPreviewDemo() {
  return <PeekPopPreview autoPlay />;
}
