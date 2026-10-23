# Mood Orb Jar — Kotlin fidelity matrix

The Kotlin port reads the values below through `Tokens.kt`. Line references are to the approved React Native files and this port.

## Theme and content

| Value | React Native | Kotlin | Match |
|---|---|---|---|
| stage width 402 | `theme.ts:3` | `Tokens.kt:5` | match |
| `REPLICA=false` | `theme.ts:7` | `Tokens.kt:6` | match |
| background `#F4FDFE` | `theme.ts:11` | `Tokens.kt:9` | match |
| background bottom `#E3F0FB` | `theme.ts:12` | `Tokens.kt:10` | match |
| card `#F7FCFF` | `theme.ts:13` | `Tokens.kt:11` | match |
| label `#3A3A3F` | `theme.ts:14` | `Tokens.kt:12` | match |
| secondary `#8F9298` | `theme.ts:15` | `Tokens.kt:13` | match |
| card shadow `rgba(60,100,160,.08)` | `theme.ts:16` | `Tokens.kt:14` | match; API 26–27 uses layered shadow, same tint/radius |
| ink `#16233A` | `theme.ts:17` | `Tokens.kt:15` | match |
| mute `rgba(22,35,58,.55)` | `theme.ts:18` | `Tokens.kt:16` | match |
| faint `rgba(22,35,58,.35)` | `theme.ts:19` | `Tokens.kt:17` | match |
| glass `rgba(255,255,255,.38)` | `theme.ts:20` | `Tokens.kt:18` | match; retained token, dead in shipped `REPLICA=false` scene |
| glass back `rgba(214,230,246,.55)` | `theme.ts:21` | `Tokens.kt:19` | match; retained token, dead in shipped scene |
| glass edge `rgba(255,255,255,.95)` | `theme.ts:22` | `Tokens.kt:20` | match; retained token, dead in shipped scene |
| glass shade `rgba(110,150,200,.28)` | `theme.ts:23` | `Tokens.kt:21` | match; retained token, dead in shipped scene |
| floor shadow `rgba(70,110,170,.18)` | `theme.ts:24` | `Tokens.kt:22` | match; retained token, plate contains it |
| lid colors/stops | `theme.ts:25-26` | `Tokens.kt:23-24` | match; retained fallback tokens, shipped image is identical |
| lid ridge | `theme.ts:27` | `Tokens.kt:25` | match; retained fallback token |
| print `#0E0E10` | `theme.ts:28` | `Tokens.kt:26` | match; `REPLICA=false` does not draw creator caption |
| danger / danger ink | `theme.ts:29-30` | `Tokens.kt:27-28` | match |
| dots background | `theme.ts:31` | `Tokens.kt:29` | match |
| sheet line | `theme.ts:32` | `Tokens.kt:30` | match |
| dots ink | `theme.ts:33` | `Tokens.kt:31` | match |
| aim | `theme.ts:34` | `Tokens.kt:32` | match |
| Awful mood object | `theme.ts:39` | `Tokens.kt:44` | match |
| Bad mood object | `theme.ts:40` | `Tokens.kt:45` | match |
| Neutral mood object | `theme.ts:41` | `Tokens.kt:46` | match |
| Good mood object | `theme.ts:42` | `Tokens.kt:47` | match |
| Great mood object | `theme.ts:43` | `Tokens.kt:48` | match |
| spacing xs 4 | `theme.ts:48` | `Tokens.kt:52` | match |
| spacing sm 8 | `theme.ts:48` | `Tokens.kt:53` | match |
| card pad 12 | `theme.ts:48` | `Tokens.kt:54` | match |
| spacing lg 16 | `theme.ts:48` | `Tokens.kt:55` | match |
| card gap 18 | `theme.ts:48` | `Tokens.kt:56` | match |
| avatar top 25 | `theme.ts:48` | `Tokens.kt:57` | match |
| side 16 | `theme.ts:51` | `Tokens.kt:61` | match |
| header 44 | `theme.ts:52` | `Tokens.kt:62` | match |
| icon 44 | `theme.ts:53` | `Tokens.kt:63` | match |
| lid width 192 | `theme.ts:54` | `Tokens.kt:64` | match |
| lid height 36.7 | `theme.ts:55` | `Tokens.kt:65` | match |
| lid overlap 10 | `theme.ts:56` | `Tokens.kt:66` | match |
| jar top 47 | `theme.ts:57` | `Tokens.kt:67` | match |
| jar image width `280×1.01` | `theme.ts:58` | `Tokens.kt:68` | match |
| jar image height 326.7 | `theme.ts:59` | `Tokens.kt:69` | match |
| jar image mouth y 10 | `theme.ts:60` | `Tokens.kt:70` | match |
| jar width `276×1.01` | `theme.ts:61` | `Tokens.kt:71` | match |
| jar height 300 | `theme.ts:62` | `Tokens.kt:72` | match |
| base 2 | `theme.ts:63` | `Tokens.kt:73` | match |
| wall 6 | `theme.ts:64` | `Tokens.kt:74` | match |
| shoulder 70 | `theme.ts:65` | `Tokens.kt:75` | match |
| corner 46 | `theme.ts:66` | `Tokens.kt:76` | match |
| mouth half `84×1.01` | `theme.ts:67` | `Tokens.kt:77` | match |
| print size/stroke/y | `theme.ts:68-70` | `Tokens.kt:78-80` | match; replica-only dead branch |
| label y .12 | `theme.ts:71` | `Tokens.kt:81` | match |
| picker top 400 | `theme.ts:72` | `Tokens.kt:82` | match |
| picker height 96 | `theme.ts:73` | `Tokens.kt:83` | match |
| picker radius 20.2 | `theme.ts:74` | `Tokens.kt:84` | match |
| picker orb y 41 | `theme.ts:75` | `Tokens.kt:85` | match |
| slot gap 67.33 | `theme.ts:76` | `Tokens.kt:86` | match |
| slot dx .8 | `theme.ts:77` | `Tokens.kt:87` | match |
| slot label height 63 | `theme.ts:78` | `Tokens.kt:88` | match |
| label size 13 | `theme.ts:79` | `Tokens.kt:89` | match |
| card radius 16 | `theme.ts:80` | `Tokens.kt:90` | match |
| entries top 512 | `theme.ts:81` | `Tokens.kt:91` | match |
| entry height 106 | `theme.ts:82` | `Tokens.kt:92` | match |
| section/grouped-list legacy values | `theme.ts:83-85` | `Tokens.kt:93-95` | match; retained tokens, current RN view does not use them |
| entry gap 18 | `theme.ts:86` | `Tokens.kt:96` | match |
| entry orb radius 22 | `theme.ts:87` | `Tokens.kt:97` | match |
| jar orb radius 25 | `theme.ts:88` | `Tokens.kt:98` | match |
| pile moods | `theme.ts:93` | `Tokens.kt:153` | match |
| all copy strings and initial entries | `theme.ts:95-111` | `Tokens.kt:161-176` | match |
| replica plate y/height | `theme.ts:116` | `Tokens.kt:187-188` | match |
| replica cut 2 s | `theme.ts:119` | `Tokens.kt:189` | match; dead while replica flag is off |
| 18 measured pile bodies | `theme.ts:120-124` | `Tokens.kt:190-208` | match |

## View

| Value / component | React Native | Kotlin | Match |
|---|---|---|---|
| vertical page gradient | `MoodScene.tsx:67-69` | `MoodOrbJarScene.kt:162-170` | match |
| picker shadow y 4, blur 10 | `MoodScene.tsx:72-74` | `Tokens.kt:137-138`, `MoodOrbJarScene.kt:179-190` | match; Android API 26–27 layered fallback |
| picker card and five source orbs | `MoodScene.tsx:75-78` | `MoodOrbJarScene.kt:191-211` | match |
| empty source jar plate | `MoodScene.tsx:82-87` | `MoodOrbJarScene.kt:214-217` | match; PNG SHA-256 equals RN asset |
| source pile atlas, 18 slices | `MoodScene.tsx:95-99,150-157` | `MoodOrbJarScene.kt:111-141,272-345` | match; PNG SHA-256 equals RN asset |
| landing ring | `MoodScene.tsx:100-101` | `MoodOrbJarScene.kt:219-231` | match |
| dotted aim arc | `MoodScene.tsx:104-105` | `MoodOrbJarScene.kt:233-251` | match |
| lifted/tilted lid | `MoodScene.tsx:108-111` | `MoodOrbJarScene.kt:253-269` | match; PNG SHA-256 equals RN asset |
| jar knock transform | `MoodScene.tsx:46-49,81` | `MoodOrbJarScene.kt:203-213` | match |
| orb image overscan `144/132` | `MoodScene.tsx:121-125` | `Tokens.kt:139`, `MoodOrbJarScene.kt:201` | match |
| four flight trail copies | `MoodScene.tsx:147-149,163-179` | `MoodOrbJarScene.kt:284-308` | match |
| soft-body sprite transform | `MoodScene.tsx:133-139` | `MoodOrbJarScene.kt:310-344` | match |
| shared ghost above list | `MoodOrbJar.tsx:203-206,268-273` | `MoodOrbJarFrame.kt:69-89`, `MoodOrbJarScene.kt:383-410` | match |
| header inset/icon/chevron/calendar/more | `MoodOrbJar.tsx:220-233,331-337` | `Tokens.kt:102-112`, `MoodOrbJar.kt:273-365` | match |
| five accessible picker targets/labels | `MoodOrbJar.tsx:237-247,338-339` | `MoodOrbJar.kt:367-392` | match |
| month 11/2.2 and count 24/30 | `MoodOrbJar.tsx:249-259,341-344` | `Tokens.kt:115-119`, `MoodOrbJar.kt:394-448` | match |
| individual entry cards, 106 high / 18 gap | `MoodOrbJar.tsx:260-267,345` | `MoodOrbJarEntries.kt:109-202` | match |
| undo underlay, 24 inset, 15 semibold | `MoodOrbJar.tsx:300-305,346-350` | `MoodOrbJarEntries.kt:238-270` | match |
| card grows from avatar, out-cubic | `MoodOrbJar.tsx:284-297` | `MoodOrbJarEntries.kt:295-317` | match; Compose draws equivalent left-origin width rather than transform matrix |
| avatar at pad 12 / top 25, diameter 44 | `MoodOrbJar.tsx:313-315,355,365-366` | `MoodOrbJarEntries.kt:320-342` | match |
| entry text x 16.3, y 27.4 | `MoodOrbJar.tsx:316-320,367-369` | `MoodOrbJarEntries.kt:343-394` | match |
| dots 27, top 14.3, right 11.3, 3 px dots | `MoodOrbJar.tsx:321-323,370-374` | `MoodOrbJarEntries.kt:395-436` | match; border is two physical Android hairlines |
| enter 260 / exit 160 / move 220 ms | `MoodOrbJar.tsx:302` | `Tokens.kt:142-144`, `MoodOrbJarEntries.kt:128-181` | match |
| accessibility add/undo actions | `MoodOrbJar.tsx:221-242,308-311` | `MoodOrbJar.kt:286-381`, `MoodOrbJarEntries.kt:281-294` | match |

## Motion and simulation

| Value / behavior | React Native | Kotlin | Match |
|---|---|---|---|
| fixed step `1/120`, max steps 30 | `motion.ts:3-4` | `Tokens.kt:211-212` | match |
| pile count/seed/settle/home/damping/passes/rest | `motion.ts:6-15` | `Tokens.kt:215-224` | match; live shipped pile count comes from the 18 measured bodies as in RN |
| throw gravity/apex/solve/flick/lift/spin/regrow/max | `motion.ts:17-26` | `Tokens.kt:227-235` | match |
| undo threshold/rise/fly/spring | `motion.ts:28` | `Tokens.kt:238-243` | match |
| lid open/lift/tilt/spring/hold | `motion.ts:31` | `Tokens.kt:246-252` | match |
| six haptic events | `motion.ts:33` | `Tokens.kt:255-261`, `MoodOrbJarDevice.kt:51-60` | platform reason: Android maps equivalent semantic strengths to platform constants |
| soft body spring/thresholds | `motion.ts:36-45` | `Tokens.kt:264-272` | match |
| tilt slosh/glass | `motion.ts:46` | `Tokens.kt:275-277` | match; shipped RN and Kotlin plate do not draw a separate front highlight |
| ghost duration/lift/rise/arc/grow | `motion.ts:47` | `Tokens.kt:280-285` | match |
| ripple duration/radius | `motion.ts:48` | `Tokens.kt:288-290` | match |
| jar knock 1.5/14/9 | `motion.ts:49` | `Tokens.kt:293-296` | match |
| hop every 2, phase 1, speed 70 | `motion.ts:50` | `Tokens.kt:299-302`, `MoodOrbJarSim.kt:641-653` | match |
| aim 14 points / .045 token | `motion.ts:51` | `Tokens.kt:305-307` | match |
| reduced fade .2 | `motion.ts:53` | `Tokens.kt:310-311` | match |
| loop 12,000 ms / `LOOP=12.0` | `motion.ts:55-56` | `Tokens.kt:314`, `MoodOrbJarMotion.kt:24` | match |
| resume after touch | `motion.ts:57` | `Tokens.kt:315`, `MoodOrbJarSim.kt:543-557` | contract override: 2,000 ms required by task; RN token is 3,500 ms |
| script mood/pick/pickBy/release/swipe/swipeBy | `motion.ts:58-65` | `Tokens.kt:317-323`, `MoodOrbJarSim.kt:456-485` | match |
| replica hold -1 | `motion.ts:67` | `Tokens.kt:326` | match |
| QA speed 1 | `motion.ts:69` | `Tokens.kt:327` | match |
| trail count/gap/alphas | `motion.ts:70` | `Tokens.kt:329-332` | match |
| jar geometry | `jar-sim.ts:21-44` | `MoodOrbJarSim.kt:61-90` | match |
| jar boundaries and 3-pass PBD | `jar-sim.ts:88-169` | `MoodOrbJarSim.kt:167-252` | match |
| measured resting pile/home slots | `jar-sim.ts:171-229` | `MoodOrbJarSim.kt:254-287` | match |
| solved ballistic launch | `jar-sim.ts:242-262` | `MoodOrbJarSim.kt:302-321` | match |
| Reduce Motion instant placement | `jar-sim.ts:264-292` | `MoodOrbJarSim.kt:323-349` | match |
| finger/tap/flick/swipe input state machine | `jar-sim.ts:307-391` | `MoodOrbJarSim.kt:363-455` | match |
| 12 s autoplay choreography | `jar-sim.ts:408-445` | `MoodOrbJarSim.kt:456-485` | match |
| exact rest-to-rest seam | `jar-sim.ts:447-465` | `MoodOrbJarSim.kt:488-542` | task-tightened match: Kotlin clears residual springs so `t=12` equals `t=0` exactly |
| touch interruption then eased restoration | `jar-sim.ts:466-474` | `MoodOrbJarSim.kt:543-557` | task-tightened match: after 2 s Kotlin runs the existing undo path, then restarts at rest |
| flight, return and undo kinematics | `jar-sim.ts:476-525` | `MoodOrbJarSim.kt:560-609` | match |
| landing, squash, haptic and ripple | `jar-sim.ts:527-547` | `MoodOrbJarSim.kt:611-639` | match |
| deterministic ambient hop | `jar-sim.ts:548-558` | `MoodOrbJarSim.kt:641-653` | match |
| ghost/card/ripple clocks | `jar-sim.ts:559-570` | `MoodOrbJarSim.kt:654-667` | match |
| settle-to-pile and slot regrow | `jar-sim.ts:571-592` | `MoodOrbJarSim.kt:668-697` | match |
| lid and row springs | `jar-sim.ts:594-604` | `MoodOrbJarSim.kt:687-695` | match |
| wall-clock `withFrameNanos`, delta ≤ .1 s | `MoodOrbJar.tsx:144-156`, task contract | `MoodOrbJar.kt:149-163`, `MoodOrbJarSim.kt:699-706` | platform match; Compose wall clock, explicit .1 s clamp |
| solved dotted aim trajectory | `jar-sim.ts:618-634` | `MoodOrbJarSim.kt:709-723` | match |
