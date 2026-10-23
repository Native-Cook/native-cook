import type { RefObject } from 'react';
import { Platform } from 'react-native';
import Svg, {
  Defs,
  G,
  Image as SvgImage,
  LinearGradient,
  Path,
  Rect,
  Stop,
  Text as SvgText,
  TSpan,
} from 'react-native-svg';

import { foilFallback } from './theme';

const SERIF_FONT = Platform.OS === 'android' ? 'serif' : 'Georgia';
const MONO_FONT = Platform.OS === 'android' ? 'monospace' : 'Courier';

const PORTRAIT_STRIP = require('./assets/poster-strip.jpg') as number;

type ArtworkProps = {
  x?: number;
  y?: number;
  scale?: number;
};

export function PosterArtwork({ x = 0, y = 0, scale = 1 }: ArtworkProps) {
  return (
    <G transform={`translate(${x} ${y}) scale(${scale})`}>
      <Rect width={251} height={251} rx={13} fill="#FAF9F7" />
      <SvgText x={38} y={23} fontFamily={SERIF_FONT} fontSize={7} fontWeight="700" letterSpacing={-0.2} fill="#164F3C">FIELD &amp; FELLOW</SvgText>
      <SvgText x={215} y={21} textAnchor="end" fontSize={5.3} fontWeight="800" letterSpacing={0.8} fill="#252A27">
        <TSpan x={215} dy={0}>COMMON</TSpan>
        <TSpan x={215} dy={5.3}>GROUND</TSpan>
      </SvgText>
      <SvgText x={38} y={70} fontSize={22} fontWeight="400" letterSpacing={-0.7} fill="#164F3C">
        <TSpan x={38} dy={0}>PAWS, PEOPLE &amp;</TSpan>
        <TSpan x={38} dy={21}>PURPOSE</TSpan>
      </SvgText>
      <SvgText x={38} y={100.5} fontFamily={MONO_FONT} fontSize={4.5} fontWeight="500" letterSpacing={-0.16} fill="#26322D">
        A COMMUNITY CONVERSATION ON HOW WE SHOW UP FOR SHELTER DOGS
      </SvgText>
      <SvgImage href={PORTRAIT_STRIP} x={38} y={105} width={145} height={29} preserveAspectRatio="xMidYMid slice" />
      {['ARI', 'MAYA', 'JULES', 'NOAH', 'LEA'].map((name, index) => (
        <SvgText key={name} x={38 + index * 29} y={141} fontFamily={MONO_FONT} fontSize={4.2} fontWeight="600" fill="#313632">{name}</SvgText>
      ))}
      <G transform="rotate(-4 92 171)">
        <SvgText x={39} y={174} fontFamily={SERIF_FONT} fontStyle="italic" fontSize={14} fill="#B89965">Saturday July 18</SvgText>
      </G>
      <SvgText x={150} y={164} fontFamily={MONO_FONT} fontSize={4.5} fontWeight="600" fill="#303631">
        <TSpan x={150} dy={0}>PANEL  ·  FIRESIDE CHAT</TSpan>
        <TSpan x={150} dy={5}>TRAINING Q&amp;A</TSpan>
        <TSpan x={150} dy={5}>SILENT AUCTION FUNDRAISER</TSpan>
        <TSpan x={150} dy={5}>LOCAL VENDORS</TSpan>
      </SvgText>
      <SvgText x={38} y={204} fontFamily={MONO_FONT} fontSize={5.5} fontWeight="600" fill="#303631">
        <TSpan x={38} dy={0}>2–4 PM</TSpan>
        <TSpan x={38} dy={8}>GOODFOLK COFFEE, WEST LA</TSpan>
      </SvgText>
      <SvgText x={38} y={233} fontFamily={MONO_FONT} fontSize={3.6} fontWeight="500" fill="#585B57">SUPPORTED BY</SvgText>
      <SvgText x={47} y={246} fontSize={5.2} fontWeight="700" fill="#333A35">GOOD DOGS</SvgText>
      <SvgText x={96} y={246} fontFamily={SERIF_FONT} fontStyle="italic" fontSize={5.5} fill="#333A35">PET KIND</SvgText>
      <Path d="M152 246c5-9 11-9 16 0-6-3-10-3-16 0Zm8-8v10" fill="none" stroke="#333A35" strokeWidth={1} />
      <Rect x={0.25} y={0.25} width={250.5} height={250.5} rx={12.75} fill="none" stroke="rgba(0,0,0,0.035)" strokeWidth={0.5} />
    </G>
  );
}

export function PosterSvg({ width, height }: { width: number; height: number }) {
  return (
    <Svg width={width} height={height} viewBox="0 0 251 251" accessible={false}>
      <PosterArtwork />
    </Svg>
  );
}

export function EventPassExportSvg({ svgRef }: { svgRef: RefObject<Svg | null> }) {
  return (
    <Svg ref={svgRef} width={362} height={541} viewBox="0 0 362 541" accessible={false}>
      <Defs>
        <LinearGradient id="export-stage" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#656565" />
          <Stop offset="1" stopColor="#909090" />
        </LinearGradient>
      </Defs>
      <Rect width={362} height={541} rx={14} fill="url(#export-stage)" />
      <Rect x={46.5} y={80} width={269} height={381} rx={23} fill="#FFFFFF" fillOpacity={0.12} stroke="#FFFFFF" strokeOpacity={0.18} />
      <PosterArtwork x={56} y={89} />
      <SvgText x={64} y={379} fontSize={22} fontWeight="600" letterSpacing={-0.55} textLength={235} lengthAdjust="spacingAndGlyphs" fill="#FFFFFF">Paws, People &amp; Purpose</SvgText>
      <SvgText x={64} y={444} fontSize={14} fontWeight="600" fill="rgba(255,255,255,0.92)">Jul 18  Los Angeles</SvgText>
      <SvgText x={181} y={513} textAnchor="middle" fontSize={20} fontWeight="600" fill="rgba(255,255,255,0.31)">common✦</SvgText>
    </Svg>
  );
}

/** Only the poster's green title, filled with a static spectral gradient: the foil when the shader cannot run. */
export function PosterFoilFallbackSvg({ width, height }: { width: number; height: number }) {
  const g = foilFallback.angle;
  return (
    <Svg width={width} height={height} viewBox="0 0 251 251" accessible={false}>
      <Defs>
        <LinearGradient id="poster-foil-fallback" x1={g.x1} y1={g.y1} x2={g.x2} y2={g.y2}>
          {foilFallback.stops.map((color, i) => (
            <Stop key={color} offset={i / (foilFallback.stops.length - 1)} stopColor={color} />
          ))}
        </LinearGradient>
      </Defs>
      <SvgText x={38} y={70} fontSize={22} fontWeight="400" letterSpacing={-0.7} fill="url(#poster-foil-fallback)">
        <TSpan x={38} dy={0}>PAWS, PEOPLE &amp;</TSpan>
        <TSpan x={38} dy={21}>PURPOSE</TSpan>
      </SvgText>
    </Svg>
  );
}
