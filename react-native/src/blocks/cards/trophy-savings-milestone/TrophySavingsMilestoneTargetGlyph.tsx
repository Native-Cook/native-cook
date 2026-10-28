import { memo } from 'react';
import Svg, { Defs, Ellipse, G, LinearGradient, Path, Stop } from 'react-native-svg';

// The reference's compact oblique target: roughly 23×22 red ink, 32×26 total ink.
// Code-native geometry avoids platform colour-emoji size and perspective changes.
export const TrophySavingsMilestoneTargetGlyph = memo(function TrophySavingsMilestoneTargetGlyph() {
  return (
    <Svg width={32} height={26} viewBox="0 0 32 26" accessible={false} importantForAccessibility="no" pointerEvents="none">
      <Defs>
        <LinearGradient id="trophyTargetRim" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#F05C60" />
          <Stop offset={0.48} stopColor="#C72E38" />
          <Stop offset="1" stopColor="#86232A" />
        </LinearGradient>
        <LinearGradient id="trophyTargetFace" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#E54D52" />
          <Stop offset={0.55} stopColor="#D1353E" />
          <Stop offset="1" stopColor="#BB2732" />
        </LinearGradient>
        <LinearGradient id="trophyTargetShaft" x1="0" y1="1" x2="1" y2="0">
          <Stop offset="0" stopColor="#2A7380" />
          <Stop offset={0.5} stopColor="#54ADB5" />
          <Stop offset="1" stopColor="#87D1D2" />
        </LinearGradient>
      </Defs>
      <G transform="rotate(-43 11.1 14.5)">
        <Ellipse cx={11.45} cy={15.05} rx={9.25} ry={11.65} fill="url(#trophyTargetRim)" />
        <Ellipse cx={10.85} cy={14.2} rx={8.9} ry={11.05} fill="url(#trophyTargetFace)" />
        <Ellipse cx={10.65} cy={13.95} rx={6.95} ry={8.95} fill="#FFF0E4" />
        <Ellipse cx={10.65} cy={13.95} rx={5.15} ry={6.9} fill="#C52D36" />
        <Ellipse cx={10.5} cy={13.8} rx={3.25} ry={4.7} fill="#FFF0E3" />
        <Ellipse cx={10.5} cy={13.8} rx={1.75} ry={2.65} fill="#B6232D" />
      </G>
      <Path d="M11.25 14.85L12.8 10.7L15.05 12.5Z" fill="#A3672C" />
      <Path d="M13.25 12.25L23.9 2.7L25.55 4.5L14.5 13.6Z" fill="url(#trophyTargetShaft)" />
      <Path d="M14.25 11.8L24.25 2.9" fill="none" stroke="#A0DDDA" strokeWidth={0.65} />
      <Path d="M22.15 3.85L23.55 .55L27.65 .25L26.55 3.75L24.55 5.6Z" fill="#6EBEC1" />
      <Path d="M22.15 3.85L24.55 5.6L26.55 3.75L24.55 2.6Z" fill="#3E939D" />
      <Path d="M24.3 4.55L27.7 3.9L31.65 5.6L31.6 7.25L27.65 7.1L24.1 5.85Z" fill="#2F737D" />
      <Path d="M25.1 4.8L28.15 4.65L31.65 5.6L27.7 5.8Z" fill="#58A4AC" />
      <Path d="M24.15 4.9L27.1 2.2" fill="none" stroke="#276E78" strokeWidth={0.7} strokeLinecap="round" />
    </Svg>
  );
});
