import { Blur, Canvas, Circle, Fill, Group, LinearGradient, Paint, Path, Picture, RoundedRect, Shader, Skia, Text, useTypeface } from '@shopify/react-native-skia';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { FhrAtelierStage } from './FhrAtelierStage';
import { CLAIMS, ease, out, sceneAt, seg, type Scene } from './fhr-motion';
import { fontFor, type FontSet, makePage, textWidth } from './fhr-page';
import { RAYS_SKSL } from './fhr-shaders';
import { FHR_THEME as T } from './fhr-theme';

const P3 = Platform.OS === 'ios' ? 1 : 0;
const CHECK = 'M-3 0 L-0.5 2.5 L4 -3.5';
type Props = { scene: SharedValue<Scene>; time: SharedValue<number>; manualTime: SharedValue<number>; claims: SharedValue<number[]>; interactive: SharedValue<boolean>; tilt: SharedValue<{ x: number; y: number }>; ring: SharedValue<number>; jelly: SharedValue<number>; width: number; height: number; offsetY: number; gpu: boolean; reduced: boolean; gpuLayer?: ReactNode; onReady: (ready: boolean) => void };

/** Reduced Motion keeps the glyphs in place and fades the old/new balances. */
function ReducedBalance({scene,faces}:{scene:SharedValue<Scene>;faces:FontSet}) {
  const font=useMemo(()=>fontFor(faces,T.fonts.balance),[faces]);
  const oldText=useDerivedValue(()=>`${Math.floor(scene.value.balanceFrom/1000)} ${String(Math.round(scene.value.balanceFrom)%1000).padStart(3,'0')}`);
  const newText=useDerivedValue(()=>`${Math.floor(scene.value.balanceTo/1000)} ${String(Math.round(scene.value.balanceTo)%1000).padStart(3,'0')}`);
  const from=useDerivedValue(()=>1-scene.value.numberFade);
  const to=useDerivedValue(()=>scene.value.numberFade);
  return <Group><Text text={oldText} x={17.67-font.measureText('1').x} y={441.33} font={font} color={T.palette.text} opacity={from}/><Text text={newText} x={17.67-font.measureText('1').x} y={441.33} font={font} color={T.palette.text} opacity={to}/></Group>;
}

function Balance({scene,faces}:{scene:SharedValue<Scene>;faces:FontSet}) {
  const f=useMemo(()=>fontFor(faces,T.fonts.balance),[faces]);
  const widths=useMemo(()=>f.getGlyphWidths(f.getGlyphIDs('1650')),[f]);
  const x=[17.67,17.67+widths[0]+10.5,17.67+widths[0]+widths[1]+12,17.67+widths[0]+widths[1]+widths[2]+13.5];
  return <Group clip={{x:12,y:396,width:160,height:53}}>{[0,1,2,3].map((k)=><Digit key={k} k={k} x={x[k]} scene={scene} font={f}/>)}</Group>;
}
function Digit({k,x,scene,font}:{k:number;x:number;scene:SharedValue<Scene>;font:ReturnType<typeof fontFor>}) {
  const motionBlur=useDerivedValue(()=>({x:0,y:Math.abs(Math.sin((scene.value.balance%1)*Math.PI))*0.75}));
  const shift=useDerivedValue(()=>{
    const value=scene.value.balance, power=Math.pow(10,3-k);
    const integral=Math.floor(value), frac=value-integral;
    // Carry only through 9s, rather than continuously drifting all leading columns.
    const carry=k===3?frac:integral%power===power-1?frac:0;
    return (Math.floor(value/power)%10+carry)*-53;
  });
  const transform=useDerivedValue(()=>[{translateY:shift.value}]);
  return <Group clip={{x:x-1,y:397,width:34,height:51}} layer={<Paint><Blur blur={motionBlur}/></Paint>}><Group transform={transform}>{Array.from({length:11},(_,i)=><Text key={i} text={String(i%10)} x={x-font.measureText(String(i%10)).x} y={441.33+i*53} font={font} color={T.palette.text}/>)}</Group></Group>;
}

function Chip({i,scene,manualTime,claims,interactive,faces,reduced}:{i:number;scene:SharedValue<Scene>;manualTime:SharedValue<number>;claims:SharedValue<number[]>;interactive:SharedValue<boolean>;faces:FontSet;reduced:boolean}) {
  const chip=T.layout.daily.chips[i], b=chip.body, cx=b.x+b.width/2, cy=b.y+b.height/2;
  const font=useMemo(()=>fontFor(faces,chip.font),[faces,chip.font]);
  const tx=cx-textWidth(font,String(chip.value),chip.font.tracking)/2;
  const age=useDerivedValue(()=>i<2?1000:interactive.value?(claims.value[i-2]<0?-1000:manualTime.value-claims.value[i-2]):scene.value.t-(CLAIMS[i-2]));
  const fill=useDerivedValue(()=>i<2||age.value<0?0:reduced?1:ease(seg(age.value,0,0.18)));
  const fillOpacity=useDerivedValue(()=>reduced?ease(seg(age.value,0,0.16)):1);
  const transform=useDerivedValue(()=>reduced?[]:[{translateX:cx},{translateY:cy},{scale:age.value<0?1:age.value<0.12?1-0.03*out(seg(age.value,0,0.12)):0.97+0.03*out(seg(age.value,0.12,0.36))},{translateX:-cx},{translateY:-cy}]);
  const clip=useDerivedValue(()=>({x:b.x,y:b.y,width:b.width*fill.value,height:b.height}));
  const checkOpacity=useDerivedValue(()=>i<2?1:seg(age.value,0.12,0.18));
  const checkScale=useDerivedValue(()=>i<2||reduced?1:0.95+0.05*out(seg(age.value,0.12,0.28)));
  const checkTransform=useDerivedValue(()=>[{translateX:b.x+b.width-8},{translateY:b.y+8},{scale:checkScale.value}]);
  const end=useDerivedValue(()=>i<2||reduced?1:seg(age.value,0.16,0.32));
  return <Group transform={transform}>
    <RoundedRect {...b} r={18.17} color={T.palette.chip}/>
    <Group clip={clip} opacity={fillOpacity}><RoundedRect {...b} r={18.17}><LinearGradient start={{x:b.x,y:b.y}} end={{x:b.x+b.width,y:b.y+b.height}} colors={['#E123D9','#A63BEA']}/></RoundedRect></Group>
    <Text text={String(chip.value)} x={tx} y={chip.text.baseline} font={font} color={T.palette.text}/>
    <Group opacity={checkOpacity} transform={checkTransform}><Circle r={9} color="#161619"/><Circle r={7.4} color="#28272D"/><Path path={CHECK} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color="#FFFFFF" start={0} end={end}/></Group>
  </Group>;
}


/** Only counters and claim state redraw; the page and its navigation keep the pin's colors. */
function PageState({ props, scene, faces, rest = false, noTouch }: { props: Props; scene: SharedValue<Scene>; faces: FontSet; rest?: boolean; noTouch: SharedValue<boolean> }) {
  const reduced = props.reduced;
  const smallFont = useMemo(() => fontFor(faces, T.fonts.remaining), [faces]);
  const remaining = useDerivedValue(() => `${Math.round(scene.value.remaining)} more`);
  const levelUp = useDerivedValue(() => ease(Math.max(0, Math.min(1, (scene.value.balance - 1714) / 6))));
  const moreOpacity = useDerivedValue(() => 1 - levelUp.value);
  const blur = useDerivedValue(() => reduced ? 0 : 4 * Math.sin(Math.PI * levelUp.value));
  return <>
    {props.reduced ? <ReducedBalance scene={scene} faces={faces} /> : <Balance scene={scene} faces={faces} />}
    <Group opacity={moreOpacity}>
      <RoundedRect {...T.layout.points.up} r={8.5} color={T.palette.orange} />
      <Text text="UP" x={279.5} y={406.67} font={fontFor(faces, T.fonts.pill)} color="#FFFFFF" />
      <Text text="of 500" x={310.33} y={410} font={fontFor(faces, T.fonts.fraction)} color={T.palette.muted} />
    </Group>
    <Group opacity={moreOpacity} layer={<Paint><Blur blur={blur} /></Paint>}><Text text={remaining} x={192.33} y={409.67} font={smallFont} color={T.palette.text} /></Group>
    <Group opacity={levelUp} layer={<Paint><Blur blur={blur} /></Paint>}><Text text="Level up ›" x={192.33} y={409.67} font={smallFont} color={T.palette.pink} /></Group>
    {T.layout.daily.chips.map((_, i) => <Chip key={i} i={i} scene={scene} reduced={props.reduced} manualTime={props.manualTime} claims={props.claims} interactive={rest ? noTouch : props.interactive} faces={faces} />)}
  </>;
}

export function FhrStage(props: Props) {
  // Never capture the React props object in a worklet: gpuLayer contains native React fibers.
  const liveScene = props.scene;
  const regular = useTypeface(require('./assets/fonts/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/fonts/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/fonts/Inter-SemiBold.ttf'));
  const bold = useTypeface(require('./assets/fonts/Inter-Bold.ttf'));
  const faces = useMemo(() => regular && medium && semibold && bold ? { regular, medium, semibold, bold } : null, [regular, medium, semibold, bold]);
  const rays = useMemo(() => {
    const effect = Skia.RuntimeEffect.Make(RAYS_SKSL);
    if (!effect) throw new Error('Frost Heart background shader did not compile');
    return effect;
  }, []);
  const page = useMemo(() => faces ? makePage(faces) : null, [faces]);
  const points = useMemo(() => faces ? makePage(faces, 'points') : null, [faces]);
  const restScene = useDerivedValue(() => sceneAt(0));
  const noTouch = useDerivedValue(() => false);
  const lowMotion = props.reduced;
  // The return to rest is one blur-bridged change of the same layer: the level-up state fades and
  // blurs out, the resting state (sceneAt(0)) takes its place at the midpoint and sharpens in, so
  // the last frame of the loop already shows what t = 0 shows.
  const bridgeBlur = useDerivedValue(() => lowMotion ? 0 : Math.sin(liveScene.value.reset * Math.PI) * 5);
  const shownScene = useDerivedValue(() => liveScene.value.reset < 0.5 ? liveScene.value : restScene.value);
  const pageOpacity = useDerivedValue(() => Math.abs(1 - 2 * liveScene.value.reset));
  const [atelierReady, setAtelierReady] = useState(false);
  const ready = !!(faces && page && points && atelierReady);
  const onReady = props.onReady;
  useEffect(() => { onReady(ready); }, [onReady, ready]);
  const S = props.width / 402;
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Canvas style={StyleSheet.absoluteFill}>
      <Fill color={T.palette.background} />
      <Group transform={[{ translateY: props.offsetY }, { scale: S }]}><Fill><Shader source={rays} uniforms={{ p3: P3, time: 0, charge: 0, burst: 0, hue: 0, angle: 0, tilt: [0, 0] }} /></Fill></Group>
    </Canvas>
    <FhrAtelierStage scene={props.scene} tilt={props.tilt} ring={props.ring} jelly={props.jelly} width={props.width} height={props.height} offsetY={props.offsetY} reduced={props.reduced} gpuLayer={props.gpuLayer} gpuAvailable={props.gpu} onReady={setAtelierReady} />
    {faces && page && points && <Canvas style={StyleSheet.absoluteFill}>
      <Group transform={[{ translateY: props.offsetY }, { scale: S }]}>
        <Picture picture={page} /><Picture picture={points} />
        <Group clip={{x:0,y:380,width:402,height:188}}>
        <Group opacity={pageOpacity}><Group layer={<Paint><Blur blur={bridgeBlur} /></Paint>}><PageState props={props} scene={shownScene} faces={faces} noTouch={noTouch} /></Group></Group>
        </Group>
      </Group>
    </Canvas>}
  </View>;
}
