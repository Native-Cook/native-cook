/** Approved layout B. HTML content coordinates include the 54pt status inset once. */
export const PROFILE_LAYOUT = {
  stage:{width:393,height:852,centerX:196.5},content:{left:24,width:345},
  header:{canyonHeight:218,whiteTop:190,utilityTop:66,utilitySize:44},
  avatar:{diameter:88,centerX:68,centerY:202,templateLeft:126.5,templateTop:106,templateSize:140},
  name:{left:128,top:186,width:241,height:32,lineHeight:32},
  handle:{left:128,top:222,width:241,height:20,lineHeight:20},
  balances:{left:24,top:262,width:345,height:56,iconSize:32,gap:8,stacked:false},
  collection:{left:24,top:344,height:24,headingTop:334,headingHeight:44,bottom:610},
  statistics:{left:251,top:334,width:118,height:44},
  rail:{left:114.5,top:386,size:164,centerX:196.5,centerY:468,pitch:146,sideLift:0,sideShrink:68,targetTop:386,targetHeight:164},
  arrows:{left:56,right:293,top:560,size:44},
  skills:{left:130.5,top:556,width:132,height:20,lineHeight:20},
  score:{left:130.5,top:576,width:132,height:32,lineHeight:32},
  lastPlayed:{left:24,top:627,width:345,lineHeight:24,separatorTop:610},
  game:{left:24,top:663,width:345,height:64,padding:0,imageSize:64,radius:18},
  tabs:{left:24,top:746,width:345,height:72,padding:0,border:1,radius:0,cellWidth:83.25,cellHeight:64,cellRadius:12},
};

/** The validated card actors retain their native type/image geometry at the peak. */
export const IDENTITY_ACTOR_BASE={
  avatar:{centerX:196.5,centerY:176,templateLeft:126.5,templateTop:106,templateSize:140},
  name:{left:60,top:248,width:273,lineHeight:38,fontSize:32},
  handle:{left:70,top:290,width:253,lineHeight:18,fontSize:13},
  skills:{left:153,top:546,width:87,lineHeight:17,fontSize:13},
  score:{left:133,top:567,width:127,lineHeight:26,fontSize:20},
};

export function sourceBadgeOrigin(rail:number,index:number,layout?:ProfileSourceGeometry){
  'worklet';
  const g=layout?layout.rail:PROFILE_LAYOUT.rail;const distance=Math.min(1,Math.abs(index-rail));
  return {x:g.centerX+g.pitch*(index-rail),y:g.centerY-g.sideLift*distance,size:g.size-g.sideShrink*distance};
}
export function sourceBadgeHit(rail:number,index:number,x:number,y:number,layout?:ProfileSourceGeometry){
  'worklet';
  const pose=sourceBadgeOrigin(rail,index,layout);
  const dx=x-pose.x,dy=y-(pose.y-(layout?layout.rail.targetTop:PROFILE_LAYOUT.rail.targetTop));
  return dx*dx+dy*dy<=(pose.size/2+8)*(pose.size/2+8);
}
export type SourceMetric='nameHeight'|'handleHeight'|'skillsHeight'|'scoreHeight'|'lastHeight'|'sectionHeight'|'gameCopyHeight'|'tabHeight'|'tabWidth'|'statisticsHeight'|'statisticsWidth'|'playHeight'|'playWidth'|'balanceHeight'|'balanceWidth';
export type SourceMetrics=Record<SourceMetric,number>;
export type ProfileSourceGeometry=typeof PROFILE_LAYOUT&{contentBottom:number;overflow:number};
export function estimateSourceMetrics(fontScale:number):SourceMetrics{
  return {nameHeight:32*fontScale,handleHeight:20*fontScale,skillsHeight:20*fontScale,scoreHeight:32*fontScale,
    lastHeight:24*fontScale,sectionHeight:24*fontScale,gameCopyHeight:44*fontScale,tabHeight:16*fontScale,tabWidth:40*fontScale,
    statisticsHeight:18*fontScale,statisticsWidth:58*fontScale,playHeight:20*fontScale,playWidth:28*fontScale,balanceHeight:24*fontScale,balanceWidth:73*fontScale};
}

/** Native text measurements update once per layout/font boundary, never per scene frame. */
export function createSourceGeometry(m:SourceMetrics):ProfileSourceGeometry{
  const ceil4=(n:number)=>Math.ceil(n/4)*4;
  const g:ProfileSourceGeometry={...PROFILE_LAYOUT,header:{...PROFILE_LAYOUT.header},avatar:{...PROFILE_LAYOUT.avatar},name:{...PROFILE_LAYOUT.name},handle:{...PROFILE_LAYOUT.handle},balances:{...PROFILE_LAYOUT.balances},collection:{...PROFILE_LAYOUT.collection},statistics:{...PROFILE_LAYOUT.statistics},rail:{...PROFILE_LAYOUT.rail},arrows:{...PROFILE_LAYOUT.arrows},skills:{...PROFILE_LAYOUT.skills},score:{...PROFILE_LAYOUT.score},lastPlayed:{...PROFILE_LAYOUT.lastPlayed},game:{...PROFILE_LAYOUT.game},tabs:{...PROFILE_LAYOUT.tabs},contentBottom:727,overflow:0};
  let badge=164,group=16;
  const apply=()=>{
    // Extra-large identity text may lead at the portrait's top, without squeezing type.
    g.name.top=186-Math.min(28,Math.max(0,m.nameHeight-32));g.name.height=m.nameHeight;
    g.handle.top=g.name.top+m.nameHeight+4;g.handle.height=m.handleHeight;
    g.header.whiteTop=190-(186-g.name.top);
    g.balances.top=Math.max(246,g.handle.top+m.handleHeight+4)+16;
    g.balances.stacked=m.balanceWidth+40>148;
    g.balances.iconSize=g.balances.stacked?28:32;
    g.balances.height=Math.max(56,ceil4((g.balances.stacked?32:0)+m.balanceHeight+16));
    g.collection.headingTop=g.balances.top+g.balances.height+group;
    g.statistics.height=Math.max(44,ceil4(m.statisticsHeight+16));
    g.statistics.width=Math.max(118,ceil4(m.statisticsWidth+58));g.statistics.left=369-g.statistics.width;
    g.collection.headingHeight=Math.max(44,g.statistics.height,m.sectionHeight);
    g.collection.top=g.collection.headingTop+(g.collection.headingHeight-m.sectionHeight)/2;
    g.statistics.top=g.collection.headingTop+(g.collection.headingHeight-g.statistics.height)/2;
    g.rail.top=g.collection.headingTop+g.collection.headingHeight+8;
    g.rail.size=badge;g.rail.left=196.5-badge/2;g.rail.centerY=g.rail.top+badge/2;
    g.rail.sideShrink=badge-96;g.rail.targetTop=g.rail.top;g.rail.targetHeight=badge;
    const captionTop=g.rail.top+badge+4,captionHeight=Math.max(56,m.skillsHeight+m.scoreHeight+4);
    g.skills.top=captionTop+(captionHeight-m.skillsHeight-m.scoreHeight)/2;g.skills.height=m.skillsHeight;
    g.score.top=g.skills.top+m.skillsHeight;g.score.height=m.scoreHeight;
    g.arrows.top=captionTop+(captionHeight-44)/2;
    g.collection.bottom=captionTop+captionHeight;g.lastPlayed.separatorTop=g.collection.bottom;
    g.lastPlayed.top=g.collection.bottom+17;
    g.game.top=g.lastPlayed.top+m.lastHeight+12;g.game.height=Math.max(64,m.gameCopyHeight,m.playHeight+24);
    g.tabs.height=Math.max(72,ceil4(32+4+m.tabHeight+16));g.tabs.top=818-g.tabs.height;
    g.tabs.cellHeight=g.tabs.height-8;
    g.contentBottom=g.game.top+g.game.height;g.overflow=Math.max(0,g.contentBottom+16-g.tabs.top);
  };
  apply();if(g.overflow>0){badge-=Math.min(68,ceil4(g.overflow));apply();}
  if(g.overflow>0){group=8;apply();}
  return g;
}
