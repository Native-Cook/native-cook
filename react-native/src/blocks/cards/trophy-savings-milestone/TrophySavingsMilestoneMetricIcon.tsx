import { memo } from 'react';
import Svg, { Circle, G, Path } from 'react-native-svg';

type TrophySavingsMilestoneMetricIconProps = {
  kind: 0 | 1 | 2;
  color: string;
};

export const TrophySavingsMilestoneMetricIcon = memo(function TrophySavingsMilestoneMetricIcon({ kind, color }: TrophySavingsMilestoneMetricIconProps) {
  return (
    <Svg width={32} height={32} viewBox="0 0 32 32" accessible={false}>
      <Circle cx={16} cy={16} r={16} fill={color} />
      <G fill="none" stroke="white" strokeWidth={1} strokeLinecap="round" strokeLinejoin="round">
        {kind === 0 ? <>
          <Circle cx={16} cy={16} r={6} />
          <Circle cx={16} cy={16} r={3.5} />
          <Path d="M16 16L22.5 9.5M19.5 9.5H22.5V12.5" />
        </> : kind === 1 ? <>
          <Circle cx={16} cy={16} r={6} />
          <Path d="M16 12.5V16.5L18.5 18" />
        </> : <Path d="M11 13H21M18 10L21 13L18 16M21 19H11M14 16L11 19L14 22" />}
      </G>
    </Svg>
  );
});
