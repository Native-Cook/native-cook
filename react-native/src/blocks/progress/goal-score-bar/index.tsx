import { ComponentStage } from '@/shared/component-stage';

import { GoalScoreBar } from './GoalScoreBar';

export default function GoalScoreBarDemo() {
  return (
    <ComponentStage background="#e8e8e8" height={310}>
      <GoalScoreBar autoPlay />
    </ComponentStage>
  );
}
