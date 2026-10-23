import { ComponentStage } from '@/shared/component-stage';

import { WeatherFanDial } from './WeatherFanDial';

export default function WeatherFanDialDemo() {
  return (
    <ComponentStage background="#2c2d32" height={360}>
      <WeatherFanDial autoPlay />
    </ComponentStage>
  );
}
