import { ComponentStage } from '@/shared/component-stage';

import { SlashCommandInput } from './SlashCommandInput';

export default function SlashCommandInputDemo() {
  return (
    <ComponentStage background="#141414" height={440}>
      <SlashCommandInput autoPlay />
    </ComponentStage>
  );
}
