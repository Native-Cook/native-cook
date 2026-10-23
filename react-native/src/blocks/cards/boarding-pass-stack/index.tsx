import { ComponentStage } from '@/shared/component-stage';

import { BoardingPassStack } from './BoardingPassStack';

export default function BoardingPassStackDemo() {
  return (
    <ComponentStage background="#05070c" height={620}>
      <BoardingPassStack autoPlay />
    </ComponentStage>
  );
}
