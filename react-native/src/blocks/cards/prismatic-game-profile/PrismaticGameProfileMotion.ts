import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { cancelAnimation, Easing, runOnUI, useAnimatedReaction, useDerivedValue, useFrameCallback, useSharedValue, withSequence, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { PROFILE_LAYOUT as L, IDENTITY_ACTOR_BASE as A, sourceBadgeOrigin, sourceBadgeHit, type ProfileSourceGeometry } from './PrismaticGameProfileLayout';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { peelFormation, peelAvatarBounds, peelSurfaceBounds, type PeelSurfaceBounds } from './PrismaticPeelFormation';

export const LOOP = 10;
// Build these sampled worklet functions once, never inside a frame callback.
export const CARD_FORM_EASE = Easing.bezierFn(0.77,0,0.175,1);
export const CARD_CONTENT_EASE = Easing.bezierFn(0.23,1,0.32,1);
export type PrismaticFrame = { rail: number; time: number; material: number; progress: number; power: number; panel: number; panelKind: 0 | 1 | 2; reduced: boolean; active: boolean };
export type BadgeOrigin = { x: number; y: number; size: number };
type MaterialLight = { material: number; progress: number; power: number };
export function clamp(value: number, min = 0, max = 1) { 'worklet'; return Math.max(min, Math.min(max, value)); }
export function smooth(value: number) { 'worklet'; const t = clamp(value); return t * t * (3 - 2 * t); }
export function activeClockAt(timestamp: number, epoch: number, carried: number) { 'worklet'; return carried + Math.max(0, timestamp-epoch)/1000; }
function strength(progress: number) { 'worklet'; return smooth(progress / 0.18) * (1 - smooth((progress - 0.57) / 0.43)); }
export function underFingerLight(rail: number, origin: number, initial: MaterialLight = { material: -1, progress: 0, power: 0 }): MaterialLight {
  'worklet';
  const nearest = clamp(Math.round(rail),0,2);
  const offset = rail-nearest;
  const engagement = smooth(Math.abs(rail-origin)/0.12);
  const focus = 1-smooth(Math.abs(offset)/0.5);
  const trackedPhase = clamp(0.5+offset*0.72+clamp((rail-origin)*0.15,-0.15,0.15),0.12,0.88);
  const carry = initial.material === nearest ? initial : { material: -1, progress: 0, power: 0 };
  return { material: nearest, progress: carry.progress*(1-engagement)+trackedPhase*engagement,
    power: carry.power*(1-engagement)+0.34*engagement*focus };
}
export function finiteMaterialLight(index: number, age: number, duration: number, carry: MaterialLight = { material: -1, progress: 0, power: 0 }): MaterialLight {
  'worklet';
  const t = clamp(age/duration); const handoff = smooth(age/0.12);
  if (carry.power <= 0) return { material: index, progress: t, power: strength(t) };
  return { material: index, progress: carry.progress*(1-handoff)+t*handoff,
    power: carry.power*(1-handoff)+strength(t)*handoff };
}
/** One RN-supported matrix: perspective * foreground lift * XYZ scale * Rx * Ry * Rz. */
export function cardPlaneMatrix(progress: number, tiltX = 0, tiltY = 0, reduced = false) {
  'worklet';
  const p=reduced ? 0 : clamp(progress); const lift=96*p; const s=1-lift/1000;
  const x=tiltX*p*Math.PI/180, y=tiltY*p*Math.PI/180, z=-1.25*p*Math.PI/180;
  const cx=Math.cos(x), sx=Math.sin(x), cy=Math.cos(y), sy=Math.sin(y), cz=Math.cos(z), sz=Math.sin(z);
  const r11=cy*cz, r12=-cy*sz, r13=sy;
  const r21=cx*sz+sx*sy*cz, r22=cx*cz-sx*sy*sz, r23=-sx*cy;
  const r31=sx*sz-cx*sy*cz, r32=sx*cz+cx*sy*sz, r33=cx*cy;
  return [s*r11,s*r21,s*r31,-s*r31/1000, s*r12,s*r22,s*r32,-s*r32/1000,
    s*r13,s*r23,s*r33,-s*r33/1000, 0,0,lift,s];
}
/** A reversible geometric offset, never an independent actor clock. */
export function gatherPhase(progress: number, start: number, reduced = false) {
  'worklet';
  return reduced ? clamp(progress) : clamp((progress-start)/(1-start));
}
export function galleryCatchStrength(seconds: number, reduced = false) {
  'worklet';
  const age=seconds-5.8;
  if (reduced || age<=0 || age>=0.35) return 0;
  return age<=0.09 ? CARD_CONTENT_EASE(clamp(age/0.09)) : 1-CARD_CONTENT_EASE(clamp((age-0.09)/0.26));
}
export function galleryCatchHandoff(seconds: number, reduced = false) {
  'worklet';
  const value=galleryCatchStrength(seconds,reduced);
  return {value,remaining:value>0 ? Math.max(0,(6.15-seconds)*1000) : 0};
}
export function galleryContentOpacity(seconds: number, reduced = false) {
  'worklet';
  if (reduced) return 0;
  return CARD_CONTENT_EASE(clamp((seconds-5.8)/0.09))*(1-CARD_CONTENT_EASE(clamp((seconds-7.51)/0.09)));
}
export function galleryPlayerTilt(seconds: number) {
  'worklet';
  const envelope = smooth((seconds-5.8)/0.3)*(1-smooth((seconds-7.25)/0.35));
  return { x: Math.sin((seconds-5.8)*3.4)*4.5*envelope, y: Math.sin((seconds-5.8)*4.1)*6*envelope };
}

/** A finite rail, with no cloned slots at either end. Product gestures use the same pose. */
export function galleryFrame(seconds: number, reduced = false): PrismaticFrame {
  'worklet';
  if (reduced) return { rail: 1, time: 0, material: -1, progress: 0, power: 0, panel: 0, panelKind: 0, reduced: true, active: false };
  const t = clamp(seconds, 0, LOOP);
  let rail = 1; let material = -1; let progress = 0; let panel = 0;
  let panelKind: 0 | 1 | 2 = 0;
  let movingOrigin = -1; let pulseAge = -1; let pulseDuration = 1; let pulseOrigin = 1;
  if (t >= 0.6 && t < 1) { rail = 1-smooth((t-0.6)/0.4); movingOrigin = 1; }
  else if (t >= 1 && t < 2) { rail = 0; material = 0; pulseAge = t-1; }
  else if (t >= 2 && t < 2.4) { rail = smooth((t-2)/0.4); movingOrigin = 0; }
  else if (t >= 2.4 && t < 3.4) { material = 1; pulseAge = t-2.4; pulseOrigin = 0; }
  else if (t >= 3.4 && t < 3.8) { rail = 1+smooth((t-3.4)/0.4); movingOrigin = 1; }
  else if (t >= 3.8 && t < 4.9) { rail = 2; material = 2; pulseAge = t-3.8; pulseDuration = 1.1; }
  else if (t >= 4.9 && t < 5.2) rail = 2-smooth((t-4.9)/0.3);
  else if (t >= 5.2 && t < 5.8) { panelKind = 1; panel = CARD_FORM_EASE(clamp((t-5.2)/0.6)); }
  else if (t >= 5.8 && t < 7.6) { panelKind = 1; panel = 1; }
  else if (t >= 7.6 && t < 8.4) { panelKind = 1; panel = 1-CARD_FORM_EASE(clamp((t-7.6)/0.8)); }
  const light = movingOrigin >= 0 ? underFingerLight(rail,movingOrigin)
    : pulseAge >= 0 ? finiteMaterialLight(material,pulseAge,pulseDuration,underFingerLight(rail,pulseOrigin)) : { material: -1, progress: 0, power: 0 };
  progress = light.progress;
  return { rail, time: t, material: light.material, progress, power: light.power, panel, panelKind, reduced: false, active: true };
}

export function peelSealLift(progress:number,reduced=false){
  'worklet';
  return reduced?0:12*smooth((progress-0.10)/0.18)*(1-smooth((progress-0.80)/0.17));
}
/** Source border/fill are gone before the fixed portrait backdrop may appear. */
export function peelSourcePortraitOpacity(progress:number,reduced=false){
  'worklet';
  return reduced?(progress>0?0:1):1-smooth((progress-0.37)/0.09);
}
/** Clear the text column before the rigid seal descends into its hover lane. */
export function peelSealChannels(progress:number,reduced=false){
  'worklet';
  if(reduced){const endpoint=progress>0?1:0;return {x:endpoint,y:endpoint,scale:endpoint};}
  const vertical=smooth((progress-0.08)/0.24);
  return {x:smooth((progress-0.10)/0.20),y:vertical,scale:vertical};
}
export type PlayerIdentityGlyph = {width:number;height:number;x:number;y:number;scale:number};
export type PlayerIdentityLayout = {avatarY:number;avatarScale:number;portraitTop:number;portraitHeight:number;name:PlayerIdentityGlyph;handle:PlayerIdentityGlyph;skills:PlayerIdentityGlyph;score:PlayerIdentityGlyph};
export function initialIdentityLayout():PlayerIdentityLayout {
  'worklet';
  return {avatarY:298,avatarScale:2.05,portraitTop:163,portraitHeight:270,name:{width:120,height:38,x:130.5,y:469.9,scale:1.1},handle:{width:99,height:18,x:117.96,y:504.52,scale:1.08},skills:{width:30,height:17,x:81.75,y:564.775,scale:1.15},score:{width:49,height:26,x:113.5,y:604.55,scale:2}};
}
/** One native glyph pose supplies both rendering and material avoidance. */
export function peelIdentityPose(kind:'name'|'handle'|'skills'|'score',progress:number,reduced:boolean,source:ProfileSourceGeometry,layout?:PlayerIdentityLayout){
  'worklet';
  const phases=peelFormation(progress,reduced),phase=kind==='handle'?phases.name:phases[kind],start=source[kind],target=(layout??initialIdentityLayout())[kind];
  const startScale=kind==='name'?30/32:kind==='score'?27/20:14/13;
  const sourceX=kind==='name'||kind==='handle'?start.left+target.width*startScale/2:196.5;
  const sourceY=start.top+start.height/2;
  const vertical=kind==='name'||kind==='handle'?(reduced?(progress>0?1:0):smooth((progress-0.18)/0.25)):phase;
  const footer=smooth((progress-0.08)/0.12);
  const horizontal=kind==='name'||kind==='handle'?1-(1-vertical)*(1-vertical):reduced?(progress>0?1:0):1-(1-footer)*(1-footer)*(1-footer);
  const scale=startScale+(target.scale-startScale)*phase;
  const sourceLeft=sourceX-target.width*startScale/2,targetLeft=target.x-target.width*target.scale/2;
  const x=sourceLeft+(targetLeft-sourceLeft)*horizontal+target.width*scale/2,y=sourceY+(target.y-sourceY)*vertical;
  return {x,y,scale,baseY:A[kind].top+target.height/2,bounds:{x:x-target.width*scale/2,y:y-target.height*scale/2,width:target.width*scale,height:target.height*scale}};
}
/** The one rigid media island rides the actual portrait anchor and uniform scale. */
export function peelPortraitIslandBounds(progress:number,reduced:boolean,source:ProfileSourceGeometry,layout?:PlayerIdentityLayout){
  'worklet';
  const current=layout??initialIdentityLayout();
  const avatar=peelAvatarBounds(progress,source.avatar.centerX,source.avatar.centerY,source.avatar.diameter,current.avatarY,current.avatarScale,reduced);
  const avatarScale=avatar.width/118,ratio=avatarScale/current.avatarScale;
  const x=avatar.x+59*avatarScale,y=avatar.y+61*avatarScale;
  const top=current.portraitTop??163,height=current.portraitHeight??270;
  return {x:x+(54.5-196.5)*ratio,y:y+(top-current.avatarY)*ratio,width:284*ratio,height:height*ratio,radius:28*ratio};
}
/** Actual moving media, portrait clip and native glyphs, including measured large type. */
export function peelIdentityAvoidance(progress:number,reduced:boolean,source:ProfileSourceGeometry,layout?:PlayerIdentityLayout){
  'worklet';
  const current=layout??initialIdentityLayout();
  const avatar=peelAvatarBounds(progress,source.avatar.centerX,source.avatar.centerY,source.avatar.diameter,current.avatarY,current.avatarScale,reduced);
  const name=peelIdentityPose('name',progress,reduced,source,current).bounds;
  const handle=peelIdentityPose('handle',progress,reduced,source,current).bounds;
  // Include the island continuously, even before alpha rises: curl clearance has no threshold jump.
  const island=peelPortraitIslandBounds(progress,reduced,source,current);
  const x=Math.min(avatar.x,name.x,handle.x,island.x),y=Math.min(avatar.y,name.y,handle.y,island.y);
  return {x,y,width:Math.max(avatar.x+avatar.width,name.x+name.width,handle.x+handle.width,island.x+island.width)-x,height:Math.max(avatar.y+avatar.height,name.y+name.height,handle.y+handle.height,island.y+island.height)-y};
}
/** Alpha remains zero until the entire media island fits the rounded substrate. */
export function peelRectCoverage(bounds:PeelSurfaceBounds,x:number,y:number,width:number,height:number){
  'worklet';
  const radius=Math.max(0,Math.min(bounds.radius,bounds.width/2,bounds.height/2));
  const cx=bounds.x+bounds.width/2,cy=bounds.y+bounds.height/2;
  let outside=-100000;
  for(let i=0;i<2;i++)for(let j=0;j<2;j++){
    const qx=Math.abs(x+width*i-cx)-(bounds.width/2-radius),qy=Math.abs(y+height*j-cy)-(bounds.height/2-radius);
    const dx=Math.max(qx,0),dy=Math.max(qy,0);
    outside=Math.max(outside,Math.sqrt(dx*dx+dy*dy)+Math.min(Math.max(qx,qy),0)-radius);
  }
  const t=clamp((-outside-6)/8);
  return t*t*(3-2*t);
}

type Options = { autoPlay?: boolean; previewTime?: number; previewReducedMotion?: boolean; localReduced: boolean; scale: number; sourceGeometry:SharedValue<ProfileSourceGeometry>; onInspect: () => void;
  onInteractiveBegin: (kind: 'object' | 'stats', index: number) => void; onInteractiveClose: (kind: 'object' | 'stats', stillCurrent: () => boolean) => void };
export type PrismaticController = {
  sourceGeometry:SharedValue<ProfileSourceGeometry>; frame: SharedValue<PrismaticFrame>; detail: SharedValue<number>; detailContent: SharedValue<number>; objectOrigin: SharedValue<BadgeOrigin>; selected: number; galleryPanel: boolean; galleryKind: 0 | 1 | 2; reduced: boolean;
  catchStrength: SharedValue<number>; cardTiltX: SharedValue<number>; cardTiltY: SharedValue<number>; foreground: SharedValue<boolean>; detailPresented: SharedValue<boolean>; detailKind: SharedValue<0 | 1>; detailIndex: SharedValue<number>;
  panelGesture: ReturnType<typeof Gesture.Pan>; detailGeneration: SharedValue<number>; detailDragging: SharedValue<boolean>;
  gesture: ReturnType<typeof Gesture.Exclusive>; select: (index: number) => void; open: (compact: boolean, objectIndex?: number) => void; close: (completed: () => void) => void;
};

export function usePrismaticScene({ autoPlay = false, previewTime, previewReducedMotion = false, localReduced, scale, sourceGeometry, onInspect, onInteractiveBegin, onInteractiveClose }: Options): PrismaticController {
  const systemReduced = useReducedMotionPreference();
  const reduced = systemReduced || previewReducedMotion || localReduced;
  const [selected, setSelected] = useState(1);
  const [galleryKind, setGalleryKind] = useState<0 | 1 | 2>(0);
  const interactiveCallbacks = useRef({ begin: onInteractiveBegin, close: onInteractiveClose });
  useEffect(() => { interactiveCallbacks.current = { begin: onInteractiveBegin, close: onInteractiveClose }; }, [onInteractiveBegin, onInteractiveClose]);
  const inspectCallback = useRef(onInspect);
  useEffect(() => { inspectCallback.current = onInspect; }, [onInspect]);
  const inspect = useCallback(() => inspectCallback.current(), []);
  const rail = useSharedValue(1); const seconds = useSharedValue(0); const clock = useSharedValue(0);
  const gallery = useSharedValue(autoPlay || previewTime != null); const fixed = useSharedValue(previewTime != null);
  const isReduced = useSharedValue(reduced); const foreground = useSharedValue(AppState.currentState !== 'background' && AppState.currentState !== 'inactive');
  const paused = useSharedValue(false); const dragging = useSharedValue(false); const origin = useSharedValue(1); const recognitionOrigin = useSharedValue(0);
  const gestureGeneration = useSharedValue(-1);
  const axis = useSharedValue(0); const axisX = useSharedValue(0); const axisY = useSharedValue(0);
  const downX = useSharedValue(0); const downY = useSharedValue(0);
  const material = useSharedValue(-1); const materialStart = useSharedValue(-1); const generation = useSharedValue(0);
  const settling = useSharedValue(false); const settleFade = useSharedValue(1);
  const grabLight = useSharedValue<MaterialLight>({ material: -1, progress: 0, power: 0 }); const releaseLight = useSharedValue<MaterialLight>({ material: -1, progress: 0, power: 0 });
  const frozenFrame = useSharedValue<PrismaticFrame | null>(null);
  const objectOrigin = useSharedValue<BadgeOrigin>(sourceBadgeOrigin(1,1));
  const detail = useSharedValue(0);
  const detailContent = useSharedValue(0);
  // One channel also records eligibility: -2 eligible, -1 suppressed, 0 spent.
  // Positive values are the single finite optical catch; the material sees max(0,x).
  const catchStrength = useSharedValue(-1);
  const detailTarget = useSharedValue(0); const detailOperation = useSharedValue(0);
  const detailKind = useSharedValue<0 | 1>(0); const detailIndex = useSharedValue(1);
  const pullSession = useSharedValue(-1); const pullOriginY = useSharedValue(0); const pullOriginProgress = useSharedValue(0);
  const pullReturnTarget = useSharedValue(0); const pullActive = useSharedValue(false);
  const pullOwner = useSharedValue(0);
  const cardTiltX = useSharedValue(0); const cardTiltY = useSharedValue(0);
  const panelMode = useSharedValue(0); const panelDownAbsoluteX = useSharedValue(0);
  const tiltOriginX = useSharedValue(0); const tiltOriginY = useSharedValue(0);
  const panelDownX = useSharedValue(0); const panelDownY = useSharedValue(0); const panelDownAbsoluteY = useSharedValue(0);
  const epoch = useSharedValue(-1); const carriedClock = useSharedValue(0);
  const galleryStartClock = useSharedValue(0); const galleryStartSeconds = useSharedValue(0);
  const operation = useRef(0);
  const pendingClose = useRef<{ id: number; complete: () => void } | null>(null);
  const mountInteractive = useCallback((kind: 0 | 1, index: number, token: number) => {
    if (token !== generation.get() || !paused.get()) return;
    if (!pullActive.get() && detail.get() <= 0.001 && detailTarget.get() === 0) return;
    operation.current += 1; pendingClose.current = null;
    interactiveCallbacks.current.begin(kind === 1 ? 'object' : 'stats',index);
  }, [generation, paused, pullActive, detail, detailTarget]);
  const finishInteractiveClose = useCallback((token: number, kind: 0 | 1) => {
    if (token !== generation.get() || detailOperation.get() >= 0 || detail.get() > 0.001 || !foreground.get()) return;
    operation.current += 1; pendingClose.current = null;
    paused.set(false); frozenFrame.set(null); material.set(-1);
    interactiveCallbacks.current.close(kind === 1 ? 'object' : 'stats',() => token === generation.get() && !pullActive.get() && foreground.get());
  }, [generation, detailOperation, detail, foreground, paused, frozenFrame, material, pullActive]);
  const completeClose = useCallback((id: number) => {
    const pending = pendingClose.current;
    if (!pending || pending.id !== id || operation.current !== id || detailOperation.get() !== id
      || AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
    pendingClose.current = null;
    paused.set(false);
    pending.complete();
  }, [paused, detailOperation]);
  const pulseCatch = useCallback(() => {
    'worklet';
    if (catchStrength.get() !== -2 || detailKind.get() !== 1 || detailTarget.get() !== 1 || isReduced.get() || !foreground.get()) return;
    // Mark it spent before scheduling. Returning to optical0 cannot re-arm it,
    // and there is no completion callback that can outlive this generation.
    catchStrength.set(0);
    catchStrength.set(withSequence(
      withTiming(1,{duration:90,easing:CARD_CONTENT_EASE}),
      withTiming(0,{duration:260,easing:CARD_CONTENT_EASE}),
    ));
  },[catchStrength,detailKind,detailTarget,isReduced,foreground]);
  const driveDetail = useCallback((target: number) => {
    'worklet';
    detailTarget.set(target);
    cancelAnimation(detail); cancelAnimation(detailContent);
    if (!foreground.get()) return;
    const token = generation.get(); const id = detailOperation.get();
    const finish = (finished?: boolean) => {
      'worklet';
      if (!finished || token !== generation.get() || id !== detailOperation.get() || !foreground.get()) return;
      if (target === 0) {
        if (id < 0) { frozenFrame.set(null); material.set(-1); scheduleOnRN(finishInteractiveClose,token,detailKind.get()); }
        else scheduleOnRN(completeClose,id);
      }
    };
    if (isReduced.get()) {
      if(target===1){detail.set(1);detailContent.set(withTiming(1,{duration:90*(1-detailContent.get()),easing:CARD_CONTENT_EASE}));}
      else detailContent.set(withTiming(0,{duration:70*detailContent.get(),easing:CARD_CONTENT_EASE},faded=>{
        if(!faded||token!==generation.get()||id!==detailOperation.get()||!foreground.get())return;
        detail.set(0);finish(true);
      }));
    }
    else if (target === 1) {
      const opened = (opened?: boolean) => {
        'worklet';
        if (!opened || token !== generation.get() || id !== detailOperation.get() || !foreground.get()) return;
        pulseCatch();
        detailContent.set(withTiming(1, { duration: 90*(1-detailContent.get()), easing: CARD_CONTENT_EASE }));
      };
      detail.set(detailKind.get() === 1 ? withSpring(1,{duration:300,dampingRatio:0.8,velocity:0,overshootClamping:true},opened)
        : withTiming(1,{duration:260*(1-detail.get()),easing:CARD_CONTENT_EASE},opened));
    } else {
      cancelAnimation(catchStrength); catchStrength.set(-1);
      detailContent.set(withTiming(0, { duration: 70*detailContent.get(), easing: CARD_CONTENT_EASE }, faded => {
        if (!faded || token !== generation.get() || id !== detailOperation.get() || !foreground.get()) return;
        detail.set(detailKind.get() === 1 ? withSpring(0,{duration:300,dampingRatio:0.8,velocity:0,overshootClamping:true},finish)
          : withTiming(0,{duration:200*detail.get(),easing:CARD_CONTENT_EASE},finish));
      }));
    }
  }, [detailTarget, foreground, generation, detailOperation, detail, detailContent, completeClose, isReduced, finishInteractiveClose, detailKind, frozenFrame, material, pulseCatch, catchStrength]);

  const frame = useDerivedValue<PrismaticFrame>(() => {
    const snapshot = frozenFrame.get();
    if (paused.get() && snapshot) return { ...snapshot, reduced: isReduced.get(), active: false };
    if (gallery.get()) return galleryFrame(seconds.get(), isReduced.get());
    const id = material.get();
    const duration = id === 0 ? 0.45 : id === 1 ? 0.42 : 0.5;
    const light = dragging.get() || settling.get() ? underFingerLight(rail.get(),origin.get(),grabLight.get())
      : id >= 0 ? finiteMaterialLight(id,clock.get()-materialStart.get(),duration,releaseLight.get()) : { material: -1, progress: 0, power: 0 };
    return { rail: clamp(rail.get(),0,2), time: clock.get(), material: isReduced.get() ? -1 : light.material, progress: light.progress,
      power: isReduced.get() ? 0 : light.power*(settling.get() ? settleFade.get() : 1), panel: 0, panelKind: 0, reduced: isReduced.get(), active: foreground.get() && !paused.get() };
  });

  useAnimatedReaction(()=>({geometry:sourceGeometry.get(),ready:paused.get() && detailKind.get()===1 && detail.get()>=0.999 && !pullActive.get()}),(current,previous)=>{
    if(current.ready && (!previous?.ready || current.geometry!==previous.geometry)) objectOrigin.set(sourceBadgeOrigin(frame.get().rail,detailIndex.get(),current.geometry));
  });

  const ticker = useFrameCallback(info => {
    'worklet';
    if (!foreground.get() || paused.get() || isReduced.get()) return;
    if (epoch.get() < 0) { epoch.set(info.timestamp); carriedClock.set(clock.get()); }
    const elapsed = activeClockAt(info.timestamp, epoch.get(), carriedClock.get());
    clock.set(elapsed);
    if (gallery.get() && !fixed.get()) seconds.set((galleryStartSeconds.get()+elapsed-galleryStartClock.get()) % LOOP);
    else if (!gallery.get() && material.get() >= 0) {
      const duration = material.get() === 0 ? 0.45 : material.get() === 1 ? 0.42 : 0.5;
      if (clock.get() - materialStart.get() >= duration) material.set(-1);
    }
  }, false);

  const setTickerActive = useCallback((active: boolean) => ticker.setActive(active), [ticker]);
  useAnimatedReaction(() => foreground.get() && !paused.get() && !isReduced.get()
    && ((gallery.get() && !fixed.get()) || material.get() >= 0), (active, previous) => {
    if (active !== previous) { epoch.set(-1); scheduleOnRN(setTickerActive, active); }
  });
  useEffect(() => () => {
    ticker.setActive(false); operation.current += 1; pendingClose.current = null;
    runOnUI(() => { 'worklet'; generation.set(generation.get()+1); cancelAnimation(catchStrength); catchStrength.set(-1); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY); cancelAnimation(rail); cancelAnimation(detail); cancelAnimation(detailContent); cancelAnimation(settleFade); })();
  }, [ticker, generation, rail, detail, detailContent, settleFade, cardTiltX, cardTiltY, catchStrength]);
  useEffect(() => {
    const id = ++operation.current; pendingClose.current = null;
    runOnUI(() => { 'worklet';
      generation.set(generation.get() + 1); cancelAnimation(catchStrength); catchStrength.set(-1); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY); cancelAnimation(rail); cancelAnimation(detail); cancelAnimation(detailContent);
      detailOperation.set(id); detailTarget.set(0); epoch.set(-1);
      gallery.set(autoPlay || previewTime != null); fixed.set(previewTime != null);
      seconds.set(Number.isFinite(previewTime) ? clamp(previewTime!, 0, LOOP) : 0);
      galleryStartSeconds.set(seconds.get()); galleryStartClock.set(clock.get());
      rail.set(1); material.set(-1); detail.set(0); detailContent.set(0); paused.set(false); dragging.set(false);
      settling.set(false); frozenFrame.set(null); grabLight.set({ material: -1, progress: 0, power: 0 }); releaseLight.set({ material: -1, progress: 0, power: 0 });
      pullActive.set(false); pullSession.set(-1); detailKind.set(0); cardTiltX.set(0); cardTiltY.set(0);
    })();
  }, [autoPlay, previewTime, detail, detailContent, detailOperation, detailTarget, epoch, fixed, gallery, generation, material, paused, rail, seconds, dragging, galleryStartSeconds, galleryStartClock, clock, settling, frozenFrame, grabLight, releaseLight, pullActive, pullSession, detailKind, cardTiltX, cardTiltY, catchStrength]);
  useEffect(() => {
    runOnUI((value: boolean) => { 'worklet';
      const presented = frame.get().rail;
      if (value) { cardTiltX.set(0); cardTiltY.set(0); }
      isReduced.set(value); generation.set(generation.get()+1); cancelAnimation(catchStrength); catchStrength.set(-1); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY); cancelAnimation(rail); cancelAnimation(detail); cancelAnimation(detailContent);
      material.set(-1); dragging.set(false); cancelAnimation(settleFade);
      settling.set(false);
      pullActive.set(false);
      if (value && frozenFrame.get()) frozenFrame.set({ ...frozenFrame.get()!, material: -1, power: 0, progress: 0 });
      if (value) {
        rail.set(Math.round(presented));
      }
      if (paused.get()) driveDetail(detailTarget.get());
    })(reduced);
  }, [reduced, frame, isReduced, generation, rail, detail, detailContent, material, dragging, paused, driveDetail, detailTarget, settling, frozenFrame, settleFade, pullActive, cardTiltX, cardTiltY, catchStrength]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', state => {
      runOnUI((active: boolean) => { 'worklet';
        foreground.set(active); generation.set(generation.get() + 1);
        epoch.set(-1);
        if (!active) { cancelAnimation(catchStrength); catchStrength.set(-1); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY); cancelAnimation(rail); cancelAnimation(detail); cancelAnimation(detailContent); cancelAnimation(settleFade); dragging.set(false); settling.set(false); pullActive.set(false); material.set(-1); }
        else if (paused.get()) driveDetail(detailTarget.get());
        else if (!gallery.get()) rail.set(isReduced.get() ? Math.round(rail.get()) : withTiming(Math.round(rail.get()), { duration: 200 }));
      })(state === 'active');
    });
    return () => sub.remove();
  }, [foreground, generation, rail, detail, detailContent, dragging, gallery, paused, isReduced, epoch, driveDetail, detailTarget, settleFade, settling, material, pullActive, cardTiltX, cardTiltY, catchStrength]);
  useAnimatedReaction(() => clamp(Math.round(frame.get().rail),0,2), (index, previous) => {
    if (index !== previous) scheduleOnRN(setSelected, index);
  });
  useAnimatedReaction(() => frame.get().panelKind, (kind, previous) => {
    if (kind !== previous) scheduleOnRN(setGalleryKind, kind);
  });
  const feedback = useCallback((token: number) => {
    if (token !== generation.get() || paused.get() || isReduced.get() || !foreground.get()) return;
    void Haptics.selectionAsync().catch(() => {});
  }, [generation, paused, isReduced, foreground]);

  const interrupt = useCallback((preserveCatch = false) => {
    'worklet';
    rail.set(frame.get().rail); gallery.set(false); fixed.set(false); cancelAnimation(rail);
    generation.set(generation.get() + 1); material.set(-1);
    if (!preserveCatch) { cancelAnimation(catchStrength); catchStrength.set(-1); }
    dragging.set(false); settling.set(false); cancelAnimation(settleFade); settleFade.set(1); releaseLight.set({ material: -1, progress: 0, power: 0 });
  }, [rail, frame, gallery, fixed, generation, material, dragging, settling, settleFade, releaseLight, catchStrength]);
  const settle = useCallback((index: number, velocity: number, user: boolean) => {
    'worklet';
    const target = clamp(index, 0, 2); const token = generation.get();
    if (isReduced.get()) { rail.set(target); dragging.set(false); settling.set(false); material.set(-1); return; }
    rail.set(withSpring(target, { duration: 400, dampingRatio: 0.8, velocity, overshootClamping: target === 0 || target === 2 }, finished => {
      if (!finished || token !== generation.get() || paused.get()) return;
      const presented = frame.get();
      releaseLight.set(settling.get() ? { material: target, progress: presented.progress, power: presented.power } : { material: -1, progress: 0, power: 0 });
      settling.set(false);
      if (!user) { material.set(-1); return; }
      material.set(target); materialStart.set(clock.get());
      if (user) scheduleOnRN(feedback, token);
    }));
  }, [generation, isReduced, rail, paused, material, materialStart, clock, feedback, frame, releaseLight, settling, dragging]);
  const select = (index: number) => runOnUI((target: number) => { 'worklet'; interrupt(); settle(target, 0, true); })(index);
  const handoffCatch = useCallback((fromRail: boolean,wasGallery: boolean,kind: 0 | 1,progress: number,time: number) => {
    'worklet';
    const reduced=isReduced.get();
    const preserve=!fromRail && !wasGallery && kind===1 && !reduced;
    const eligible=kind===1 && (fromRail || (progress<0.999 && (wasGallery ? time<5.8 : catchStrength.get()===-2)));
    const carry=galleryCatchHandoff(time,reduced);
    interrupt(preserve);
    if (preserve) {
      // A live/spent manual catch keeps the same channel and animation. An
      // already-full but uncommitted opening cannot be re-armed by a regrab.
      if (progress>=0.999 && catchStrength.get()===-2) catchStrength.set(0);
      return;
    }
    if (wasGallery && kind===1 && carry.value>0 && !reduced) {
      catchStrength.set(carry.value);
      catchStrength.set(withTiming(0,{duration:carry.remaining,easing:CARD_CONTENT_EASE}));
    } else catchStrength.set(eligible && !reduced ? -2 : -1);
  },[isReduced,catchStrength,interrupt]);
  const beginPull = useCallback((fromRail: boolean, translationY: number) => {
    'worklet';
    const presented = frame.get();
    const wasGallery = gallery.get();
    const kind: 0 | 1 = fromRail ? 1 : wasGallery ? (presented.panelKind === 1 ? 1 : 0) : detailKind.get();
    const index = fromRail || wasGallery ? clamp(Math.round(presented.rail),0,2) : detailIndex.get();
    const p = fromRail ? 0 : clamp(wasGallery ? presented.panel : detail.get());
    const returnTarget = fromRail ? 0 : wasGallery ? (p >= 0.5 ? 1 : 0) : detailTarget.get();
    const presentedContent = wasGallery ? galleryContentOpacity(presented.time,presented.reduced) : detailContent.get();
    if (fromRail || (wasGallery && kind === 1)) {
      objectOrigin.set(sourceBadgeOrigin(presented.rail,index,sourceGeometry.get()));
    }
    if (wasGallery) { const tilt = galleryPlayerTilt(presented.time); cardTiltX.set(tilt.x); cardTiltY.set(tilt.y); }
    else if (fromRail) { cardTiltX.set(0); cardTiltY.set(0); }
    handoffCatch(fromRail,wasGallery,kind,p,presented.time);
    cancelAnimation(detail); cancelAnimation(detailContent); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY);
    paused.set(true); frozenFrame.set({ ...presented, panel: 0, panelKind: 0 });
    const presentedProgress = fromRail ? clamp(-translationY/(120*scale)) : p;
    detailKind.set(kind); detailIndex.set(index); detail.set(presentedProgress); detailContent.set(presentedProgress >= 0.999 ? presentedContent : 0); detailTarget.set(returnTarget);
    const token = generation.get(); detailOperation.set(-token); pullSession.set(token); pullActive.set(true); pullOwner.set(fromRail ? 1 : 2);
    pullOriginProgress.set(fromRail ? 0 : p); pullOriginY.set(fromRail ? 0 : translationY); pullReturnTarget.set(returnTarget);
    if (presentedProgress >= 0.999 && detailContent.get() < 1) detailContent.set(withTiming(1,{ duration: 90*(1-detailContent.get()), easing: CARD_CONTENT_EASE }));
    // The permanent actors are UI-owned. Do not attach a new overlay recognizer
    // while the original source pointer is still active.
    if (!fromRail) scheduleOnRN(mountInteractive,kind,index,token);
  }, [frame, gallery, detailKind, detailIndex, detail, detailTarget, objectOrigin, handoffCatch, detailContent, paused, frozenFrame, generation, detailOperation, pullSession, pullActive, pullOriginProgress, pullOriginY, pullReturnTarget, mountInteractive, scale, pullOwner, cardTiltX, cardTiltY,sourceGeometry]);
  const settlePull = useCallback((target: number, velocity: number) => {
    'worklet';
    const token = pullSession.get();
    if (token !== generation.get() || !foreground.get()) return;
    pullActive.set(false); detailTarget.set(target); cancelAnimation(detail); cancelAnimation(detailContent);
    if (target === 0) { cancelAnimation(catchStrength); catchStrength.set(-1); }
    if (target === 0 || detail.get() < 0.999) detailContent.set(0);
    detail.set(withSpring(target, { duration: 300, dampingRatio: 0.8, velocity, overshootClamping: true }, finished => {
      if (!finished || token !== generation.get() || !foreground.get()) return;
      if (target === 1) { pulseCatch(); detailContent.set(withTiming(1,{ duration: 90*(1-detailContent.get()), easing: CARD_CONTENT_EASE })); }
      else { frozenFrame.set(null); material.set(-1); scheduleOnRN(finishInteractiveClose,token,detailKind.get()); }
    }));
  }, [pullSession, generation, foreground, pullActive, detailTarget, detail, detailContent, finishInteractiveClose, detailKind, frozenFrame, material, catchStrength, pulseCatch]);
  const updatePull = useCallback((translationY: number, owner: number) => {
    'worklet';
    if (!pullActive.get() || pullOwner.get() !== owner || pullSession.get() !== generation.get() || !foreground.get() || isReduced.get()) return;
    const previous = detail.get();
    const next = clamp(pullOriginProgress.get()-(translationY-pullOriginY.get())/(120*scale));
    if (next < 0.999) { cancelAnimation(detailContent); detailContent.set(0); }
    else if (previous < 0.999) detailContent.set(withTiming(1,{ duration: 90, easing: CARD_CONTENT_EASE }));
    detail.set(next);
  }, [pullActive, pullSession, generation, foreground, isReduced, detail, pullOriginProgress, pullOriginY, scale, detailContent, pullOwner]);
  const cardFeedback = useCallback((token: number) => {
    if (token !== generation.get() || isReduced.get() || !foreground.get() || detailTarget.get() !== 1) return;
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, [generation,isReduced,foreground,detailTarget]);
  const releasePull = useCallback((velocityY: number, cancelled: boolean, owner: number) => {
    'worklet';
    if (!pullActive.get() || pullOwner.get() !== owner || pullSession.get() !== generation.get() || !foreground.get()) return;
    const velocity = -velocityY/(120*scale);
    const target = cancelled ? pullReturnTarget.get() : detail.get()+velocity*0.18 > 0.5 ? 1 : 0;
    const newlyCommitted = !cancelled && target === 1 && pullReturnTarget.get() === 0;
    settlePull(target,cancelled ? 0 : velocity);
    if (owner === 1 && target === 1 && !cancelled) scheduleOnRN(mountInteractive,detailKind.get(),detailIndex.get(),pullSession.get());
    if (newlyCommitted) scheduleOnRN(cardFeedback,pullSession.get());
  }, [pullActive, pullSession, generation, foreground, scale, pullReturnTarget, detail, settlePull, pullOwner,cardFeedback,mountInteractive,detailKind,detailIndex]);
  const gesture = useMemo(() => {
  const pan = Gesture.Pan().manualActivation(true).maxPointers(1)
    .onTouchesDown(event => {
      const t = event.allTouches[0]; if (!t || event.numberOfTouches !== 1 || paused.get()) return;
      axis.set(0); axisX.set(t.absoluteX); axisY.set(t.absoluteY); downX.set(t.x); downY.set(t.y);
    }).onTouchesMove((event,manager) => {
      const t = event.allTouches[0]; if (!t || paused.get() || !foreground.get()) { manager.fail(); return; }
      const dx = t.absoluteX-axisX.get(); const dy = t.absoluteY-axisY.get();
      if (axis.get() === 0 && Math.max(Math.abs(dx),Math.abs(dy)) >= 6*scale) axis.set(Math.abs(dx) >= Math.abs(dy) ? 1 : 2);
      if (axis.get() === 1) manager.activate(); else if (axis.get() === 2) manager.fail();
    }).onTouchesUp((_event,manager) => { if (axis.get() === 0) manager.fail(); })
    .onStart(event => {
      if (paused.get() || !foreground.get() || frame.get().panel > 0) return;
      const presented = frame.get(); interrupt();
      gestureGeneration.set(generation.get());
      grabLight.set({ material: presented.material, progress: presented.progress, power: presented.power });
      origin.set(rail.get()); recognitionOrigin.set(event.translationX); dragging.set(true);
    })
    .onUpdate(event => {
      if (gestureGeneration.get() !== generation.get() || paused.get() || !foreground.get()) return;
      if (!isReduced.get()) rail.set(clamp(origin.get() - (event.translationX-recognitionOrigin.get()) / (L.rail.pitch * scale), 0, 2));
    })
    .onEnd((event,success) => {
      if (gestureGeneration.get() !== generation.get() || paused.get() || !foreground.get()) return;
      if (!success) { dragging.set(false); settling.set(true); settleFade.set(withTiming(0,{ duration: 160 })); settle(Math.round(rail.get()),0,false); return; }
      dragging.set(false); settling.set(true); settleFade.set(1);
      const velocity = -event.velocityX / (L.rail.pitch * scale);
      const presented = isReduced.get() ? origin.get()-(event.translationX-recognitionOrigin.get())/(L.rail.pitch*scale) : rail.get();
      settle(Math.round(clamp(presented + velocity * 0.12, 0, 2)), velocity, true);
    }).onFinalize((_event, success) => {
      if (gestureGeneration.get() !== generation.get() || paused.get() || !foreground.get()) return;
      if (!success && dragging.get()) { dragging.set(false); settling.set(true); settleFade.set(withTiming(0,{ duration: 160 })); settle(Math.round(rail.get()),0,false); }
      dragging.set(false);
    });
  // This builder registers a UI worklet. The callback ref is read later by scheduleOnRN, never during render.
  // eslint-disable-next-line react-hooks/refs
  const tap = Gesture.Tap().maxDistance(6 * scale).onEnd((event, success) => {
    if (!success || paused.get() || !foreground.get() || frame.get().panel > 0) return;
    const current = frame.get().rail;
    const nearest = clamp(Math.round(current + (event.x - L.stage.centerX) / L.rail.pitch), 0, 2);
    if (!sourceBadgeHit(current,nearest,event.x,event.y,sourceGeometry.get())) return;
    interrupt();
    if (Math.abs(current - nearest) < 0.04) scheduleOnRN(inspect);
    else settle(nearest, 0, true);
  });
  /* eslint-disable react-hooks/refs -- Builders register UI worklets; callback refs are read only later by scheduled RN handlers. */
  const vertical = Gesture.Pan().manualActivation(true).maxPointers(1)
    .onTouchesDown(event => {
      const t = event.allTouches[0]; if (!t || event.numberOfTouches !== 1 || paused.get()) return;
      axis.set(0); axisX.set(t.absoluteX); axisY.set(t.absoluteY); downX.set(t.x); downY.set(t.y);
    }).onTouchesMove((event,manager) => {
      const owned = pullActive.get() && pullOwner.get() === 1 && pullSession.get() === generation.get();
      const t = event.allTouches[0]; if (!t || (!owned && paused.get()) || !foreground.get() || isReduced.get()) { manager.fail(); return; }
      if (owned) return;
      const dx = t.absoluteX-axisX.get(); const dy = t.absoluteY-axisY.get();
      if (axis.get() === 0 && Math.max(Math.abs(dx),Math.abs(dy)) >= 6*scale) axis.set(Math.abs(dx) >= Math.abs(dy) ? 1 : 2);
      if (axis.get() === 2 && dy < 0) manager.activate(); else if (axis.get() === 1 || (axis.get() === 2 && dy >= 0)) manager.fail();
    }).onTouchesUp((_event,manager) => { if (axis.get() === 0) manager.fail(); })
    .onStart(event => {
      const q = frame.get().rail; const index = clamp(Math.round(q),0,2);
      const travel = event.absoluteY-axisY.get();
      const valid = !isReduced.get() && !paused.get() && foreground.get() && travel < 0 && frame.get().panelKind === 0 && sourceBadgeHit(q,index,downX.get(),downY.get(),sourceGeometry.get());
      if (!valid) return;
      beginPull(true,travel);
    }).onUpdate(event => updatePull(event.absoluteY-axisY.get(),1))
    .onEnd((event,success) => releasePull(event.velocityY,!success,1))
    .onFinalize((_event,success) => { if (!success) releasePull(0,true,1); });
  return Gesture.Exclusive(Gesture.Race(pan,vertical),tap);
  }, [scale, interrupt, origin, rail, recognitionOrigin, dragging, settle, frame, inspect, grabLight, settling, settleFade, isReduced, gestureGeneration, generation, paused, foreground, beginPull, updatePull, releasePull, axis, axisX, axisY, downX, downY, pullActive, pullOwner, pullSession,sourceGeometry]);
  const panelGesture = useMemo(() => Gesture.Pan().minDistance(6*scale).maxPointers(1)
    .onTouchesDown(event => {
      const t = event.allTouches[0]; if (!t || event.numberOfTouches !== 1 || pullActive.get()) return;
      panelDownX.set(t.x); panelDownY.set(t.y); panelDownAbsoluteX.set(t.absoluteX); panelDownAbsoluteY.set(t.absoluteY); panelMode.set(0);
    })
    .onStart(event => {
      if (isReduced.get() || !foreground.get() || pullActive.get()) return;
      const f = frame.get(); const fromGallery = gallery.get(); const p = clamp(fromGallery ? f.panel : detail.get());
      const object = fromGallery ? f.panelKind === 1 : detailKind.get() === 1;
      const pose = fromGallery ? sourceBadgeOrigin(1,1,sourceGeometry.get()) : objectOrigin.get();
      const stats=sourceGeometry.get().statistics;
      const w = object ? pose.size : stats.width; const h = object ? pose.size : stats.height;
      const bounds=object?peelSurfaceBounds(p,pose.x,pose.y,pose.size,isReduced.get()):{x:stats.left+(70-stats.left)*p,y:stats.top+(356-stats.top)*p,width:w+(253-w)*p,height:h+(178-h)*p,radius:16+8*p};
      const {x,y,width,height}=bounds;
      if (panelDownX.get() < x-10 || panelDownX.get() > x+width+10 || panelDownY.get() < y-10 || panelDownY.get() > y+height+10) return;
      const dx = event.absoluteX-panelDownAbsoluteX.get(); const dy = event.absoluteY-panelDownAbsoluteY.get();
      beginPull(false,dy);
      panelMode.set(object && p >= 0.97 && (Math.abs(dx) >= Math.abs(dy)*0.8 || dy < 0) ? 2 : 1);
      tiltOriginX.set(cardTiltX.get()); tiltOriginY.set(cardTiltY.get());
    }).onUpdate(event => {
      if (panelMode.get() === 2 && pullActive.get() && pullOwner.get() === 2 && pullSession.get() === generation.get() && foreground.get() && !isReduced.get()) {
        cardTiltX.set(clamp(tiltOriginX.get()-(event.absoluteY-panelDownAbsoluteY.get())/(90*scale)*6,-6,6));
        cardTiltY.set(clamp(tiltOriginY.get()+(event.absoluteX-panelDownAbsoluteX.get())/(90*scale)*6,-6,6));
      } else if (panelMode.get() === 1) updatePull(event.absoluteY-panelDownAbsoluteY.get(),2);
    }).onEnd((event,success) => {
      if (panelMode.get() === 2) {
        if (!pullActive.get() || pullOwner.get() !== 2 || pullSession.get() !== generation.get()) return;
        pullActive.set(false); detailTarget.set(1);
        cardTiltX.set(withSpring(success ? 0 : tiltOriginX.get(),{ duration:400,dampingRatio:0.8,velocity:success ? -event.velocityY/(90*scale)*6 : 0,overshootClamping:true }));
        cardTiltY.set(withSpring(success ? 0 : tiltOriginY.get(),{ duration:400,dampingRatio:0.8,velocity:success ? event.velocityX/(90*scale)*6 : 0,overshootClamping:true }));
      } else if (panelMode.get() === 1) releasePull(event.velocityY,!success,2);
    }).onFinalize((_event,success) => {
      if (!success && panelMode.get() === 1) releasePull(0,true,2);
      else if (!success && panelMode.get() === 2 && pullActive.get() && pullOwner.get() === 2 && pullSession.get() === generation.get() && foreground.get() && !isReduced.get()) { pullActive.set(false); cardTiltX.set(tiltOriginX.get()); cardTiltY.set(tiltOriginY.get()); }
      panelMode.set(0);
    }),
  [scale, isReduced, foreground, frame, gallery, detail, detailKind, objectOrigin, beginPull, updatePull, releasePull, pullActive, panelDownX, panelDownY, panelDownAbsoluteX, panelDownAbsoluteY, panelMode, cardTiltX, cardTiltY, tiltOriginX, tiltOriginY, pullOwner, pullSession, generation, detailTarget,sourceGeometry]);
  /* eslint-enable react-hooks/refs */
  const open = (compact: boolean, objectIndex?: number) => {
    const id = ++operation.current; pendingClose.current = null;
    runOnUI((animate: boolean) => { 'worklet';
    const presented = frame.get();
    if (objectIndex != null) {
      objectOrigin.set(sourceBadgeOrigin(presented.rail,objectIndex,sourceGeometry.get()));
    }
    interrupt(); paused.set(true); detail.set(0); detailContent.set(0); cardTiltX.set(0); cardTiltY.set(0);
    frozenFrame.set({ ...presented, panel: 0, panelKind: 0 });
    catchStrength.set(objectIndex != null && !isReduced.get() ? -2 : -1);
    detailKind.set(objectIndex == null ? 0 : 1); detailIndex.set(objectIndex ?? clamp(Math.round(presented.rail),0,2)); pullActive.set(false);
    detailOperation.set(id); detailTarget.set(1);
    if (!animate) detail.set(1); else driveDetail(1);
    })(compact);
  };
  const close = (completed: () => void) => {
    const id = ++operation.current; pendingClose.current = { id, complete: completed };
    runOnUI(() => { 'worklet';
    if (gallery.get()) {
      const presented = frame.get(); const tilt = galleryPlayerTilt(presented.time); cardTiltX.set(tilt.x); cardTiltY.set(tilt.y);
      if (presented.panelKind === 1) objectOrigin.set(sourceBadgeOrigin(1,1,sourceGeometry.get()));
      interrupt(); detail.set(presented.panel);
      detailKind.set(presented.panelKind === 1 ? 1 : 0); detailIndex.set(clamp(Math.round(presented.rail),0,2));
      detailContent.set(galleryContentOpacity(presented.time,presented.reduced));
    }
    generation.set(generation.get()+1); detailOperation.set(id); paused.set(true); cancelAnimation(detail); cancelAnimation(cardTiltX); cancelAnimation(cardTiltY); cancelAnimation(catchStrength); catchStrength.set(-1);
    pullActive.set(false);
    material.set(-1);
    frozenFrame.set(null);
    driveDetail(0);
    })();
  };
  return { sourceGeometry, frame, detail, detailContent, objectOrigin, catchStrength, cardTiltX, cardTiltY, foreground, detailPresented:paused, detailKind, detailIndex, selected, galleryPanel: galleryKind !== 0, galleryKind, reduced, gesture, panelGesture, detailGeneration: generation, detailDragging: pullActive, select, open, close };
}
