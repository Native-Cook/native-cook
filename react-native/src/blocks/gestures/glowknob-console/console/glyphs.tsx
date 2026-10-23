import Svg, {
  Circle as SvgCircle,
  Line as SvgLine,
  Path,
} from "react-native-svg";
import type { Channel } from "./use-tactile-control";

// Reference icon silhouettes for the two in-artwork mode selectors.
export function ReferenceGlyph({
  mode,
  size,
  tint,
}: {
  mode: Channel;
  size: number;
  tint: string;
}) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      {mode === 0 ? (
        <>
          <SvgCircle
            cx={13}
            cy={13}
            r={6}
            stroke={tint}
            strokeWidth={1.5}
            fill="none"
          />
          {Array.from({ length: 8 }, (_, i) => {
            const a = (i * Math.PI) / 4;
            return (
              <SvgLine
                key={i}
                x1={13 + Math.sin(a) * 10}
                y1={13 + Math.cos(a) * 10}
                x2={13 + Math.sin(a) * 12}
                y2={13 + Math.cos(a) * 12}
                stroke={tint}
                strokeWidth={1.6}
                strokeLinecap="round"
              />
            );
          })}
        </>
      ) : (
        <Path
          d="M3 9H9L17 3V23L9 17H3Z M22 10V16"
          stroke={tint}
          strokeWidth={1.7}
          strokeLinejoin="round"
          strokeLinecap="round"
          fill="none"
        />
      )}
    </Svg>
  );
}
