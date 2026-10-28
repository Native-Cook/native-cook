import UIKit

@MainActor
final class PrismaticGameProfileStage:UIView,UIGestureRecognizerDelegate {
    typealias G=PrismaticGameProfileGeometry
    @MainActor private final class TickTarget:NSObject {weak var owner:PrismaticGameProfileStage?;@objc func tick(_ link:CADisplayLink){owner?.tick(link)}}
    private let canvas=UIView(),background=UIView(),white=UIView(),landscape=UIImageView(),balance=UIView(),separator=UIView(),dimmer=UIView()
    private let cardWorld=UIView(),identityWorld=UIView(),portrait=UIView(),portraitImage=UIImageView(),avatarGroup=UIView(),avatarClip=UIView(),avatarImage=UIImageView(),rim=UIView(),innerRim=UIView()
    private let backGradient=CAGradientLayer()
    private let cardMetal=PrismaticGameProfileMetal(card:true),cardFallback=UIView()
    private let name=UILabel(),handle=UILabel(),caption=UILabel(),score=UILabel(),cardTitle=UILabel(),cardSubtitle=UILabel()
    private let collection=UILabel(),last=UILabel(),balanceRuby=UILabel(),balanceCoin=UILabel(),ruby=UIImageView(),coin=UIImageView(),balanceDivider=UIView()
    private let gameImage=UIImageView(),gameName=UILabel(),gameMetadata=UILabel(),nav=UIView()
    private let navSeparator=CALayer()
    private let previous=PrismaticGameProfileButton(),nextBadge=PrismaticGameProfileButton(),statistics=PrismaticGameProfileButton(),play=PrismaticGameProfileButton(),back=PrismaticGameProfileButton()
    private let carousel=PrismaticGameProfileCarousel()
    private let statsShape=CAShapeLayer(),statsContent=UIView(),statsHeading=UILabel(),statsNote=UILabel(),statsClose=PrismaticGameProfileButton()
    private let motion=PrismaticGameProfileMotion()
    private var badges=[PrismaticGameProfileBadge]()
    private var controls=[PrismaticGameProfileButton](),navButtons=[PrismaticGameProfileButton](),statsTiles=[UIView]()
    private let tickTarget=TickTarget()
    private var displayLink:CADisplayLink?
    private weak var ownedPan:UIPanGestureRecognizer?
    private var source=G.Source(),identity=G.Identity()
    private var fontScale=1.0,layoutReady=false,lastSelected = -1
    private var foreground=true,externalReduced=false,localReduced=false,configured=false,autoPlay=false,previewTime:Double?
    private var panStart=CGPoint.zero,axis=0,gestureToken = -1,recognitionX=0.0,recognitionY=0.0,tiltStart=(x:0.0,y:0.0)
    private var observerTokens=[NSObjectProtocol](),panel:PrismaticGameProfilePanel?
    private var friends=[String](),savedGame=false,focusedCard=false
    private let haptics=UIImpactFeedbackGenerator(style:.light)
    private static let names=["Crown","Shield","Diamond"]
    override init(frame:CGRect){super.init(frame:frame);overrideUserInterfaceStyle = .light;backgroundColor = .white;clipsToBounds=true
        build();motion.onChange={ [weak self] in self?.refresh() };motion.onOpen={ [weak self] _ in self?.refresh() };motion.onClose={ [weak self] in self?.didClose() }
        motion.haptic={ [weak self] in guard let self,self.foreground,!self.motion.reduced else{return};self.haptics.impactOccurred() }
        tickTarget.owner=self
        let center=NotificationCenter.default
        observerTokens.append(center.addObserver(forName:UIContentSizeCategory.didChangeNotification,object:nil,queue:.main){[weak self] _ in MainActor.assumeIsolated{self?.layoutSource()}})
        observerTokens.append(center.addObserver(forName:UIAccessibility.reduceMotionStatusDidChangeNotification,object:nil,queue:.main){[weak self] _ in MainActor.assumeIsolated{self?.applyConfiguration()}})
        layoutSource()
    }
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    deinit {for token in observerTokens{NotificationCenter.default.removeObserver(token)}}
    func configure(autoPlay:Bool,previewTime:Double?,reduced:Bool,foreground:Bool){
        let changed = !configured || self.autoPlay != autoPlay || self.previewTime != previewTime || externalReduced != reduced
        configured=true;self.autoPlay=autoPlay;self.previewTime=previewTime;externalReduced=reduced
        if changed{applyConfiguration()}
        self.foreground=foreground;motion.setForeground(foreground && panel==nil,CACurrentMediaTime());refresh()
    }
    private func applyConfiguration(){motion.configure(autoPlay:autoPlay,previewTime:previewTime,reduced:externalReduced || localReduced || UIAccessibility.isReduceMotionEnabled);controls.forEach{$0.reduced=motion.reduced};refresh()}
    func stop(){displayLink?.invalidate();displayLink=nil;motion.setForeground(false,CACurrentMediaTime())}
    override func didMoveToWindow(){super.didMoveToWindow();if window==nil{displayLink?.invalidate();displayLink=nil}else{refresh()}}
    override func layoutSubviews(){super.layoutSubviews();let s=min(bounds.width/393,bounds.height/852);canvas.bounds=CGRect(x:0,y:0,width:393,height:852);canvas.center=CGPoint(x:bounds.midX,y:bounds.midY);canvas.transform=CGAffineTransform(scaleX:s,y:s);refresh()}
    private func startClock(){if motion.wantsClock && foreground && window != nil {if displayLink==nil{let link=CADisplayLink(target:tickTarget,selector:#selector(TickTarget.tick(_:)));link.preferredFrameRateRange=CAFrameRateRange(minimum:30,maximum:60,preferred:60);link.add(to:.main,forMode:.common);displayLink=link}}
        else{displayLink?.invalidate();displayLink=nil}}
    private func tick(_ link:CADisplayLink){motion.advance(link.timestamp);refresh()}
    private func eventTime(){motion.advance(CACurrentMediaTime())}
    private func label(_ label:UILabel,_ text:String,_ size:Double,_ line:Double,_ weight:UIFont.Weight = .regular,_ color:UInt32=0x092D3D,_ rounded:Bool=false){
        let font=rounded ? UIFont(name:"ArialRoundedMTBold",size:size*fontScale) ?? .systemFont(ofSize:size*fontScale,weight:weight):.systemFont(ofSize:size*fontScale,weight:weight)
        let paragraph=NSMutableParagraphStyle();paragraph.minimumLineHeight=line*fontScale;paragraph.maximumLineHeight=line*fontScale;paragraph.alignment=label.textAlignment
        let kerning=label===name ? -0.7:label===balanceRuby || label===balanceCoin ? -0.5:label===collection || label===last ? -0.55:label===gameName ? -0.3:0
        label.attributedText=NSAttributedString(string:text,attributes:[.font:font,.foregroundColor:PrismaticGameProfileAssets.color(color),.paragraphStyle:paragraph,.kern:kerning*fontScale]);label.numberOfLines=1
    }
    private func button(_ button:PrismaticGameProfileButton,_ text:String,_ symbol:String?=nil,_ fill:UInt32?=nil,_ color:UInt32=0xFFFFFF,_ action:@escaping()->Void){
        if let fill{button.backgroundColor=PrismaticGameProfileAssets.color(fill)}
        button.tintColor=PrismaticGameProfileAssets.color(color);button.setTitle(text,for:.normal);button.setTitleColor(PrismaticGameProfileAssets.color(color),for:.normal)
        button.titleLabel?.font = .systemFont(ofSize:13,weight:.bold)
        if let symbol {
            let size=button===statistics ? 18.0:button===play ? 17.0:button===back ? 22.0:button===statsClose ? 21.0:24.0
            let gap=button===statistics ? 8.0:button===play ? 4.0:button===back ? 7.0:0.0
            button.setMaterialIcon(symbol,size:size,gap:gap,color:PrismaticGameProfileAssets.color(color));setButtonFont(button,13)
        }
        button.layer.cornerRadius=16;button.action=action;button.accessibilityLabel=text.isEmpty ? symbol:text;controls.append(button)
    }
    private func setButtonFont(_ button:PrismaticGameProfileButton,_ size:Double){
        let font=button===statistics ? (UIFont(name:"ArialRoundedMTBold",size:size*fontScale) ?? .systemFont(ofSize:size*fontScale,weight:.bold)):.systemFont(ofSize:size*fontScale,weight:.bold)
        button.titleLabel?.font=font
        if var configuration=button.configuration {configuration.titleTextAttributesTransformer=UIConfigurationTextAttributesTransformer{attributes in var attributes=attributes;attributes.font=font;return attributes};button.configuration=configuration}
    }
    private func makeImage(_ image:UIImageView,_ asset:String,_ frame:CGRect,_ parent:UIView){image.image=PrismaticGameProfileAssets.image(asset);image.contentMode = .scaleAspectFit;image.frame=frame;image.isAccessibilityElement=false;parent.addSubview(image)}
    private func build(){
        canvas.frame=CGRect(x:0,y:0,width:393,height:852);addSubview(canvas);background.frame=canvas.bounds;canvas.addSubview(background)
        makeImage(landscape,"landscape",CGRect(x:0,y:0,width:393,height:218),background);landscape.contentMode = .scaleAspectFill;landscape.clipsToBounds=true
        white.backgroundColor = .white;white.layer.cornerRadius=32;white.layer.maskedCorners=[.layerMinXMinYCorner,.layerMaxXMinYCorner];background.addSubview(white)
        for (symbol,x,title) in [("cog",24.0,"Profile settings"),("account-plus",325.0,"Add a local friend")] {
            let b=PrismaticGameProfileButton(frame:CGRect(x:x,y:66,width:44,height:44));button(b,"",symbol,nil,0xFFFFFF){[weak self] in self?.showPanel(title=="Profile settings" ? .settings:.friend)}
            b.accessibilityLabel=title;b.backgroundColor=UIColor.white.withAlphaComponent(0.24);b.layer.cornerRadius=22;b.layer.borderColor=UIColor.white.withAlphaComponent(0.36).cgColor;b.layer.borderWidth=1;background.addSubview(b)
        }
        balance.backgroundColor=PrismaticGameProfileAssets.color(0xF0F7FA);balance.layer.cornerRadius=20;background.addSubview(balance)
        makeImage(ruby,"ruby",.zero,balance);makeImage(coin,"coin",.zero,balance)
        balanceRuby.textAlignment = .center;balanceCoin.textAlignment = .center;balance.addSubview(balanceRuby);balance.addSubview(balanceCoin)
        balanceDivider.backgroundColor=PrismaticGameProfileAssets.color(0xD8E7EE);balance.addSubview(balanceDivider)
        balance.isAccessibilityElement=true;balance.accessibilityLabel="Ruby balance, 72,498. Coin balance, 15,380"
        background.addSubview(collection);background.addSubview(last);collection.accessibilityTraits = .header;last.accessibilityTraits = .header
        button(statistics,"Statistics","chart-donut",0x00BCE9){[weak self] in self?.eventTime();self?.motion.openStatistics()};statistics.accessibilityLabel="Statistics. Open profile statistics";statistics.layer.shadowColor=PrismaticGameProfileAssets.color(0x0CB4E6).cgColor;statistics.layer.shadowOpacity=0.17;statistics.layer.shadowRadius=6;statistics.layer.shadowOffset=CGSize(width:0,height:5);background.addSubview(statistics)
        for (b,symbol,direction) in [(previous,"chevron-left",-1),(nextBadge,"chevron-right",1)] {button(b,"",symbol,0xF0F7FA,0x8BA8B7){[weak self] in guard let self else{return};self.eventTime();self.motion.select(self.motion.selected+direction)};b.layer.cornerRadius=22;b.accessibilityLabel=direction<0 ? "Previous badge":"Next badge";background.addSubview(b)}
        separator.backgroundColor=PrismaticGameProfileAssets.color(0xE7F0F4);background.addSubview(separator)
        makeImage(gameImage,"game",.zero,background);gameImage.layer.cornerRadius=18;gameImage.clipsToBounds=true;background.addSubview(gameName);background.addSubview(gameMetadata)
        button(play,"Play","play",0x00BCE9){[weak self] in self?.showPanel(.game)};play.accessibilityLabel="Play Neon Run local preview";play.layer.shadowColor=PrismaticGameProfileAssets.color(0x00A9E5).cgColor;play.layer.shadowOpacity=0.17;play.layer.shadowRadius=5;play.layer.shadowOffset=CGSize(width:0,height:4);background.addSubview(play)
        background.addSubview(nav);navSeparator.backgroundColor=PrismaticGameProfileAssets.color(0xE7F0F4).cgColor;nav.layer.addSublayer(navSeparator)
        for (i,item) in [("home","Home"),("account","Profile"),("gamepad-variant","Games"),("cart","Store")].enumerated(){
            let b=PrismaticGameProfileButton();button(b,"",nil,nil){[weak self] in guard let self else{return};if i==1{if self.motion.kind>0{self.motion.close()}}else{self.showPanel(i==0 ? .home:i==2 ? .game:.store)}}
            b.accessibilityLabel=i==1 ? "Profile, current tab":"\(item.1), local preview"
            let iconBox=UIView(frame:CGRect(x:17.625,y:0,width:48,height:32));iconBox.tag=21;iconBox.isUserInteractionEnabled=false;iconBox.layer.cornerRadius=12;if i==1{iconBox.backgroundColor=PrismaticGameProfileAssets.color(0xDCF6FC)};let icon=PrismaticGameProfileIcon(item.0,size:24,color:PrismaticGameProfileAssets.color(i==1 ? 0x00ABD9:0x8BA1AD));icon.frame=CGRect(x:12,y:4,width:24,height:24);iconBox.addSubview(icon);b.addSubview(iconBox)
            let text=UILabel();text.tag=20;text.textAlignment = .center;label(text,item.1,12,16,.bold,i==1 ? 0x082F43:0x8BA1AD,true);b.addSubview(text);nav.addSubview(b);navButtons.append(b)
        }
        for i in 0..<3{let badge=PrismaticGameProfileBadge(index:i);badges.append(badge);canvas.addSubview(badge)}
        carousel.increment={ [weak self] in guard let self else{return};self.eventTime();self.motion.select(self.motion.selected+1)};carousel.decrement={ [weak self] in guard let self else{return};self.eventTime();self.motion.select(self.motion.selected-1)};carousel.activate={ [weak self] in self?.eventTime();self?.haptics.prepare();self?.motion.openPlayer()};carousel.accessibilityIdentifier="prismatic-carousel";canvas.addSubview(carousel)
        dimmer.frame=canvas.bounds;dimmer.backgroundColor=PrismaticGameProfileAssets.color(0x161127);dimmer.isUserInteractionEnabled=false;dimmer.layer.zPosition=10;canvas.addSubview(dimmer)
        for (view,z) in [(cardWorld,1000.0),(identityWorld,1001.0)]{view.bounds=canvas.bounds;view.layer.anchorPoint=CGPoint(x:0.5,y:380/852);view.layer.position=CGPoint(x:196.5,y:380);view.layer.zPosition=z;view.isUserInteractionEnabled=false;canvas.addSubview(view)}
        cardMetal.frame=CGRect(x:0,y:40,width:393,height:690);cardWorld.addSubview(cardMetal)
        cardFallback.backgroundColor=PrismaticGameProfileAssets.color(0xFFFBF3);cardFallback.layer.borderWidth=3;cardFallback.layer.borderColor=PrismaticGameProfileAssets.color(0xEACD8A).cgColor;cardFallback.isHidden=cardMetal.available;cardWorld.addSubview(cardFallback)
        portrait.bounds=CGRect(x:0,y:0,width:284,height:270);portrait.clipsToBounds=true;portrait.layer.cornerRadius=28;portrait.layer.borderWidth=1.5;portrait.layer.borderColor=PrismaticGameProfileAssets.color(0xD6AFE5).cgColor;portrait.backgroundColor=PrismaticGameProfileAssets.color(0xA66FC0);identityWorld.addSubview(portrait)
        makeImage(portraitImage,"landscape",portrait.bounds,portrait);portraitImage.contentMode = .scaleAspectFill;portraitImage.clipsToBounds=true
        avatarGroup.bounds=CGRect(x:0,y:0,width:140,height:140);identityWorld.addSubview(avatarGroup)
        rim.frame=avatarGroup.bounds;rim.backgroundColor=PrismaticGameProfileAssets.color(0x60D9EC);rim.layer.cornerRadius=70;avatarGroup.addSubview(rim)
        innerRim.frame=CGRect(x:4.4680851,y:4.4680851,width:131.0638298,height:131.0638298);innerRim.backgroundColor = .white;innerRim.layer.cornerRadius=65.5319149;rim.addSubview(innerRim)
        avatarClip.frame=CGRect(x:11,y:9,width:118,height:124);avatarClip.clipsToBounds=true;avatarGroup.addSubview(avatarClip)
        makeImage(avatarImage,"avatar",CGRect(x:-15,y:-16,width:148,height:156),avatarClip)
        for l in [name,handle,caption,score]{l.textAlignment = .center;identityWorld.addSubview(l)};name.accessibilityTraits = .header
        identityWorld.addSubview(cardTitle);identityWorld.addSubview(cardSubtitle);cardTitle.accessibilityTraits = .header
        button(back,"Back to profile","chevron-down",0x138FD8){[weak self] in self?.eventTime();self?.motion.close()};backGradient.colors=[PrismaticGameProfileAssets.color(0x2DC5EE).cgColor,PrismaticGameProfileAssets.color(0x0875D4).cgColor];backGradient.cornerRadius=24;back.layer.insertSublayer(backGradient,at:0);back.layer.cornerRadius=24;back.layer.borderColor=PrismaticGameProfileAssets.color(0x8EE5FD).cgColor;back.layer.borderWidth=1;back.layer.zPosition=2000;canvas.addSubview(back)
        statsShape.fillColor=PrismaticGameProfileAssets.color(0xF4FBFF).cgColor;statsShape.strokeColor=PrismaticGameProfileAssets.color(0xCFDFEC).cgColor;statsShape.lineWidth=1;statsShape.shadowColor=PrismaticGameProfileAssets.color(0x183F59).cgColor;statsShape.shadowOpacity=0.2;statsShape.shadowRadius=12;statsShape.shadowOffset=CGSize(width:0,height:7);statsShape.zPosition=2001;canvas.layer.addSublayer(statsShape)
        statsContent.frame=CGRect(x:70,y:356,width:253,height:178);statsContent.layer.zPosition=2002;canvas.addSubview(statsContent)
        label(statsHeading,"Your skills",19,24,.heavy,0x123B55);statsHeading.frame=CGRect(x:16,y:9,width:170,height:28);statsHeading.accessibilityTraits = .header;statsContent.addSubview(statsHeading)
        button(statsClose,"","close",nil,0x1A4B71){[weak self] in self?.eventTime();self?.motion.close()};statsClose.frame=CGRect(x:197,y:0,width:44,height:44);statsClose.accessibilityLabel="Close profile details";statsContent.addSubview(statsClose)
        for (i,item) in [("Level","75","stats-level",UInt32(0x0864B8)),("Games","28","stats-games",UInt32(0x9622CF)),("Time","9h23m","stats-time",UInt32(0x587B00))].enumerated(){
            let tile=UIView(frame:CGRect(x:12+Double(i)*78.6667,y:43,width:71.6667,height:110));tile.backgroundColor = .white;tile.layer.cornerRadius=16
            let image=UIImageView();makeImage(image,item.2,CGRect(x:13.833,y:7,width:44,height:44),tile)
            let title=UILabel();title.textAlignment = .center;label(title,item.0,13,17,.medium,0x536B7D);title.frame=CGRect(x:0,y:54,width:71.6667,height:17);tile.addSubview(title)
            let value=UILabel();value.textAlignment = .center;label(value,item.1,18,23,.heavy,item.3);value.frame=CGRect(x:0,y:74,width:71.6667,height:23);tile.addSubview(value);tile.isAccessibilityElement=true;tile.accessibilityLabel="\(item.0), \(item.1)";statsContent.addSubview(tile);statsTiles.append(tile)
        }
        label(statsNote,"Profile statistics · Read-only demo",10,17,.regular,0x536B7D);statsNote.textAlignment = .center;statsNote.frame=CGRect(x:0,y:158,width:253,height:17);statsContent.addSubview(statsNote)
        let pan=UIPanGestureRecognizer(target:self,action:#selector(panGesture(_:)));ownedPan=pan;pan.maximumNumberOfTouches=1;pan.delegate=self;pan.cancelsTouchesInView=true;canvas.addGestureRecognizer(pan)
        let tap=UITapGestureRecognizer(target:self,action:#selector(tapGesture(_:)));tap.delegate=self;tap.require(toFail:pan);canvas.addGestureRecognizer(tap)
    }
    private func setGameMetadata(){
        let color=PrismaticGameProfileAssets.color(0x7C96A4),font=UIFont.systemFont(ofSize:12*fontScale,weight:.semibold)
        let text=NSMutableAttributedString()
        for (symbol,value) in [("thumb-up"," 87%   "),("play"," 315.6K")] {
            text.append(NSAttributedString(string:PrismaticGameProfileIcon.glyph(symbol),attributes:[.font:PrismaticGameProfileIcon.materialFont(12),.foregroundColor:color,.baselineOffset:-1]))
            text.append(NSAttributedString(string:value,attributes:[.font:font,.foregroundColor:color]))
        }
        gameMetadata.attributedText=text;gameMetadata.accessibilityLabel="87% likes, 315.6K plays"
    }
    private func layoutSource(){
        fontScale=Double(UIFontMetrics.default.scaledValue(for:32)/32)
        label(name,"Everen",32,38,.black,0x071E2C,true);label(handle,"@evanoff92",13,18,.semibold,0x5C6A75);label(caption,Self.names[max(0,lastSelected)]=="Shield" ? "Skills":Self.names[max(0,lastSelected)],13,17,.semibold,0x5C6A75);label(score,"1.500",20,26,.heavy,0x071F2B)
        label(collection,"Collection",20,24,.heavy,0x092D3D,true);label(last,"Last played",20,24,.heavy,0x092D3D,true);label(balanceRuby,"72,498",19,24,.heavy,0xD72D7A,true);label(balanceCoin,"15,380",19,24,.heavy,0xA96B00,true);label(gameName,"Neon Run",16,24,.heavy,0x092D3D,true);label(gameMetadata,"87%   315.6K",12,16,.semibold,0x7C96A4);setGameMetadata()
        label(cardTitle,"PLAYER CARD",14,18,.black,0x111C42);label(cardSubtitle,"Profile preview",12,18,.semibold,0x8B718C)
        let balanceWidth=max(balanceRuby.sizeThatFits(CGSize(width:1000,height:1000)).width,balanceCoin.sizeThatFits(CGSize(width:1000,height:1000)).width)
        source=G.source(fontScale,["balanceWidth":Double(balanceWidth)])
        motion.source=source
        func glyph(_ label:UILabel,_ height:Double)->G.Glyph {let size=label.sizeThatFits(CGSize(width:1000,height:1000));return G.Glyph(width:Double(size.width),height:height*fontScale,x:0,y:0,scale:1)}
        var n=glyph(name,38),h=glyph(handle,18),c=glyph(caption,17),s=glyph(score,26)
        let scoreScale=max(1,min(2,180/max(1,s.width))),scoreDefaultTop=max(578,555+c.height*1.15+4),shift=max(0,scoreDefaultTop+s.height*scoreScale-657)
        let skillsTop=555-shift,scoreTop=scoreDefaultTop-shift,handleTop=min(max(486,449+n.height*1.1+4),skillsTop-h.height*1.08-12),nameTop=min(449,handleTop-n.height*1.1-4)
        let headingTop=109-8*G.clamp(fontScale-1),portraitTop=max(163,headingTop+36*fontScale+14),portraitHeight=max(140,nameTop-16-portraitTop),avatarScale=(portraitHeight-15.8)/124
        identity=G.Identity();identity.headingTop=headingTop;identity.portraitTop=portraitTop;identity.portraitHeight=portraitHeight;identity.avatarScale=avatarScale;identity.avatarY=portraitTop+portraitHeight/2+2.05-avatarScale
        n.x=64.5+n.width*1.1/2;n.y=nameTop+n.height*1.1/2;n.scale=1.1
        h.x=64.5+h.width*1.08/2;h.y=handleTop+h.height*1.08/2;h.scale=1.08
        c.x=64.5+c.width*1.15/2;c.y=skillsTop+c.height*1.15/2;c.scale=1.15
        s.x=64.5+s.width*scoreScale/2;s.y=scoreTop+s.height*scoreScale/2;s.scale=scoreScale
        identity.name=n;identity.handle=h;identity.skills=c;identity.score=s
        let right=64.5+s.width*scoreScale;identity.sealScale=min(1.05,max(0.3,(346.5-right-12)/114));identity.sealX=min(346.5-57*identity.sealScale,max(286.5,right+12+57*identity.sealScale))
        white.frame=CGRect(x:0,y:source.whiteTop,width:393,height:852-source.whiteTop)
        balance.frame=source.balances.cg;balanceDivider.frame=CGRect(x:172.5,y:12,width:1,height:source.balances.height-24)
        for (i,pair) in [(ruby,balanceRuby),(coin,balanceCoin)].enumerated(){let center=86.25+Double(i)*172.5,icon=source.balanceIcon,labelWidth=Double(pair.1.sizeThatFits(CGSize(width:1000,height:1000)).width)
            if source.balanceStacked {pair.0.frame=CGRect(x:center-icon/2,y:8,width:icon,height:icon);pair.1.frame=CGRect(x:center-labelWidth/2,y:40,width:labelWidth,height:24*fontScale)}
            else {let total=icon+8+labelWidth;pair.0.frame=CGRect(x:center-total/2,y:(source.balances.height-icon)/2,width:icon,height:icon);pair.1.frame=CGRect(x:center-total/2+icon+8,y:(source.balances.height-24*fontScale)/2,width:labelWidth,height:24*fontScale)}
        }
        collection.frame=source.collection.cg;statistics.frame=source.statistics.cg;setButtonFont(statistics,13)
        previous.frame=CGRect(x:56,y:source.arrowsY,width:44,height:44);nextBadge.frame=CGRect(x:293,y:source.arrowsY,width:44,height:44);separator.frame=CGRect(x:24,y:source.separatorY,width:345,height:1);last.frame=source.last.cg
        gameImage.frame=CGRect(x:24,y:source.game.y+(source.game.height-64)/2,width:64,height:64)
        let playWidth=max(74,ceil(28*fontScale+45)),playHeight=max(44,ceil((20*fontScale+24)/4)*4)
        play.frame=CGRect(x:369-playWidth,y:source.game.y+(source.game.height-playHeight)/2,width:playWidth,height:playHeight);setButtonFont(play,14)
        gameName.frame=CGRect(x:100,y:source.game.midY-22*fontScale,width:260-playWidth,height:24*fontScale);gameMetadata.frame=CGRect(x:100,y:source.game.midY+6*fontScale,width:260-playWidth,height:16*fontScale)
        nav.frame=source.tabs.cg;nav.layer.borderWidth=0;navSeparator.frame=CGRect(x:0,y:0,width:345,height:1)
        for (i,b) in navButtons.enumerated(){b.frame=CGRect(x:Double(i)*87.25,y:4,width:83.25,height:source.tabs.height-8);let text=b.viewWithTag(20) as! UILabel;label(text,["Home","Profile","Games","Store"][i],12,16,.bold,i==1 ? 0x082F43:0x8BA1AD,true);let groupTop=(source.tabs.height-8-(32+4+16*fontScale))/2;b.viewWithTag(21)?.frame=CGRect(x:17.625,y:groupTop,width:48,height:32);text.frame=CGRect(x:-1,y:groupTop+36,width:85.25,height:16*fontScale)}
        carousel.frame=CGRect(x:0,y:source.railTop,width:393,height:source.rail.size)
        let backWidth=min(345,208+max(0,fontScale-1)*137);back.frame=CGRect(x:(393-backWidth)/2,y:704,width:backWidth,height:48);setButtonFont(back,15);backGradient.frame=back.bounds
        portrait.bounds=CGRect(x:0,y:0,width:284,height:portraitHeight);portraitImage.frame=portrait.bounds
        layoutReady=true;refresh()
    }
    private func refresh(){
        guard layoutReady else{return}
        let selected=motion.selected
        if selected != lastSelected{lastSelected=selected;label(caption,selected==1 ? "Skills":Self.names[selected],13,17,.semibold,0x5C6A75);layoutSource();return}
        let f=motion.raw,p=motion.kind==1 ? motion.progress:0,content=motion.kind==1 ? motion.content:0,origin=motion.origin
        CATransaction.begin();CATransaction.setDisableActions(true)
        let transform=G.matrix(p,motion.tiltX,motion.tiltY,motion.reduced);cardWorld.layer.transform=transform;identityWorld.layer.transform=transform
        let worldAlpha=motion.reduced && p>0 ? content:1;cardWorld.alpha=worldAlpha;identityWorld.alpha=worldAlpha
        dimmer.alpha=0.62*p
        let surface=G.surface(p,origin,motion.reduced),avoid=G.avoidance(p,motion.reduced,source,identity)
        var u=PrismaticGameProfileMetal.Uniforms();u.bounds=SIMD4(Float(surface.x),Float(surface.y),Float(surface.width),Float(surface.height));u.shape=SIMD4(Float(surface.radius),Float(348.0/590),Float(G.clamp(p)),motion.reduced ? 1:0)
        u.light=SIMD4(Float(motion.tiltX/6),Float(motion.tiltY/6),0,0);u.avoidance=SIMD4(Float(avoid.x),Float(avoid.y),Float(avoid.width),Float(avoid.height))
        let angle=(G.clamp(motion.tiltX,-6,6)*surface.height*0.072+G.clamp(motion.tiltY,-6,6)*surface.width*0.09)/(surface.height*0.072+surface.width*0.09),engaged=G.step(angle,0.5,2.5),response=engaged*exp(-0.5*pow((angle-4.5)/1.35,2))
        u.optics=SIMD4(Float(motion.opticalCatch),Float(motion.reduced ? 0:max(motion.opticalCatch,0.75*response)),Float(G.formation(p,motion.reduced).curl),0)
        cardMetal.isHidden=p<=0;if p>0{cardMetal.render(u)}
        cardFallback.frame=surface.cg;cardFallback.layer.cornerRadius=surface.radius;cardFallback.alpha=motion.reduced ? p:G.step(p,0.015,0.17)
        let a=G.avatar(p,motion.reduced,source,identity),s=a.width/118,ax=a.x+59*s,ay=a.y+61*s
        avatarGroup.center=CGPoint(x:ax,y:ay);avatarGroup.transform=CGAffineTransform(scaleX:s,y:s)
        let rest=G.sourcePortraitOpacity(p,motion.reduced),phase=1-rest,sourceScale=(source.avatar.size+6)/140,factor=(source.avatar.size-8)*1.08/(148*sourceScale)
        avatarImage.transform=CGAffineTransform(translationX:0,y:4*(1-phase)).scaledBy(x:G.mix(factor,1,phase),y:G.mix(factor,1,phase));avatarClip.layer.cornerRadius=14+48*rest;avatarClip.backgroundColor=PrismaticGameProfileAssets.color(0xB68CD6,rest);rim.alpha=rest
        let island=G.island(p,motion.reduced,source,identity),r=island.width/284
        portrait.center=CGPoint(x:island.midX,y:island.midY);portrait.transform=CGAffineTransform(scaleX:r,y:r);portrait.alpha=(motion.reduced ? (p>0 ? 1:0):G.step(p,0.46,0.62))*G.coverage(surface,island)
        for (key,label) in [("name",name),("handle",handle),("skills",caption),("score",score)] {let pose=G.identityPose(key,p,motion.reduced,source,identity),glyph=identity.glyph(key)
            label.bounds=CGRect(x:0,y:0,width:max(key=="name" ? 273:253,glyph.width),height:glyph.height);label.center=CGPoint(x:pose.x,y:pose.y);label.transform=CGAffineTransform(scaleX:pose.scale,y:pose.scale)
        }
        score.alpha=selected==1 ? 1:0;score.accessibilityElementsHidden=selected != 1;score.isAccessibilityElement=selected==1
        cardTitle.frame=CGRect(x:64.5,y:identity.headingTop+1.5*(1-content),width:270,height:18*fontScale);cardSubtitle.frame=CGRect(x:64.5,y:identity.headingTop+18*fontScale+1.5*(1-content),width:270,height:18*fontScale);cardTitle.alpha=content;cardSubtitle.alpha=content
        back.alpha=content;back.isHidden=motion.kind != 1 || content<=0;back.isEnabled=content>=0.999;cardTitle.isAccessibilityElement=content>=0.999;cardSubtitle.isAccessibilityElement=content>=0.999
        for badge in badges{badge.apply(f,p,selected,origin,content,motion.tiltX,motion.tiltY,source,identity)}
        let hidden=motion.kind>0 || panel != nil;
        // The original recognizer stays on canvas; only its background siblings stop receiving touches.
        background.isUserInteractionEnabled = !hidden;
        background.accessibilityElementsHidden=hidden;carousel.accessibilityElementsHidden=hidden;identityWorld.accessibilityElementsHidden=panel != nil || motion.kind==2
        carousel.accessibilityLabel="\(Self.names[selected]) badge, \(selected+1) of 3";carousel.accessibilityValue="\(selected+1) of 3";carousel.accessibilityHint=motion.reduced ? "Activate to open your player card; Back returns to your profile.":"Swipe left or right to browse. Pull upward to form your player card, or activate it. Pull the card downward to return."
        previous.isEnabled=selected>0;nextBadge.isEnabled=selected<2
        updateStatistics()
        CATransaction.commit()
        if content>=0.999 && !focusedCard && foreground {focusedCard=true;UIAccessibility.post(notification:.screenChanged,argument:name)}else if p<=0{focusedCard=false}
        startClock()
    }
    private func updateStatistics(){
        let p=motion.kind==2 ? motion.progress:0,s=source.statistics
        let b=CGRect(x:G.mix(s.x,70,p),y:G.mix(s.y,356,p),width:G.mix(s.width,253,p),height:G.mix(s.height,178,p))
        statsShape.path=UIBezierPath(roundedRect:b,cornerRadius:G.mix(16,24,p)).cgPath
        let start=PrismaticGameProfileAssets.color(0x00BCE9),end=PrismaticGameProfileAssets.color(0xF4FBFF);var r1:CGFloat=0,g1:CGFloat=0,b1:CGFloat=0,a1:CGFloat=0,r2:CGFloat=0,g2:CGFloat=0,b2:CGFloat=0,a2:CGFloat=0;start.getRed(&r1,green:&g1,blue:&b1,alpha:&a1);end.getRed(&r2,green:&g2,blue:&b2,alpha:&a2)
        statsShape.fillColor=UIColor(red:r1+(r2-r1)*p,green:g1+(g2-g1)*p,blue:b1+(b2-b1)*p,alpha:1).cgColor;statsShape.opacity=p>0 ? 1:0
        statsContent.alpha=motion.kind==2 ? motion.content:0;statsContent.isHidden=statsContent.alpha<=0;statsContent.accessibilityViewIsModal=motion.kind==2 && p>0;statistics.alpha=p>0 ? 0:1
    }
    private func didClose(){focusedCard=false;refresh();if foreground{UIAccessibility.post(notification:.screenChanged,argument:carousel)}}
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldReceive touch:UITouch)->Bool {
        var view=touch.view
        while let current=view,current !== canvas {if current is UIButton || current is UITextField || current is UISwitch || current===panel{return false};view=current.superview}
        return panel==nil
    }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool {
        // DemoScreen's ancestor SwiftUI drag keeps its edge-back rule without cancelling our pan.
        // Same-canvas recognizers (including our tap) and child controls remain exclusive.
        guard gestureRecognizer === ownedPan,otherGestureRecognizer !== ownedPan,
              let ancestor=otherGestureRecognizer.view,ancestor !== canvas,
              !(ancestor is UIControl),canvas.isDescendant(of:ancestor) else{return false}
        return true
    }
    override func gestureRecognizerShouldBegin(_ gestureRecognizer:UIGestureRecognizer)->Bool {
        guard foreground,panel==nil else{return false};let p=gestureRecognizer.location(in:canvas)
        if motion.kind>0{return true}
        return p.y>=source.railTop && p.y<=source.railTop+source.rail.size
    }
    @objc private func tapGesture(_ recognizer:UITapGestureRecognizer){guard recognizer.state == .ended else{return};eventTime();let p=recognizer.location(in:canvas)
        if motion.kind>0 {let b=presentedBounds().insetBy(dx:-16,dy:-16);if !b.contains(p){motion.close()};return}
        let q=motion.raw.rail,index=Int(G.clamp((q+(p.x-196.5)/146).rounded(),0,2)),pose=G.sourceBadge(q,index,source)
        guard hypot(p.x-pose.x,p.y-pose.y)<=pose.size/2+8 else{return}
        haptics.prepare();if abs(q-Double(index))<0.04{motion.openPlayer()}else{motion.select(index)}
    }
    @objc private func panGesture(_ recognizer:UIPanGestureRecognizer){eventTime();let point=recognizer.location(in:canvas),translation=recognizer.translation(in:canvas),velocity=recognizer.velocity(in:canvas)
        switch recognizer.state {
        case .began:
            panStart=CGPoint(x:point.x-translation.x,y:point.y-translation.y);axis=0;gestureToken = -1;recognitionX=translation.x;recognitionY=translation.y;tiltStart=(motion.tiltX,motion.tiltY);haptics.prepare()
            recognizeAxis(translation)
        case .changed:
            if axis==0{recognizeAxis(translation)}
            guard gestureToken==motion.generation else{return}
            if axis==1{motion.updateHorizontal(translation.x)}else if axis==2{motion.updatePull(translation.y)}else if axis==3{motion.updateTilt(tiltStart.x-translation.y/90*6,tiltStart.y+translation.x/90*6)}
        case .ended,.cancelled,.failed:
            guard gestureToken==motion.generation else{axis=0;return};let cancelled=recognizer.state != .ended
            if axis==1{motion.endHorizontal(translation.x,velocity.x,cancelled)}else if axis==2{motion.endPull(velocity.y,cancelled)}else if axis==3{motion.endTilt(cancelled,tiltStart.x,tiltStart.y,velocity.x,velocity.y)};axis=0;gestureToken = -1
        default:break
        }
    }
    private func presentedBounds()->CGRect {
        if motion.kind==2 {let p=motion.progress,s=source.statistics;return CGRect(x:G.mix(s.x,70,p),y:G.mix(s.y,356,p),width:G.mix(s.width,253,p),height:G.mix(s.height,178,p))}
        return G.surface(motion.progress,motion.origin,motion.reduced).cg
    }
    private func recognizeAxis(_ translation:CGPoint){
        guard max(abs(translation.x),abs(translation.y))>=6,!motion.reduced else{return}
        if motion.kind>0 {
            let bounds=presentedBounds().insetBy(dx:-16,dy:-16)
            guard bounds.contains(panStart) else{return}
            if motion.kind==2 || (translation.y>=0 && abs(translation.x)<abs(translation.y)*0.8) || motion.progress<0.97 {axis=2;motion.beginPull(translation.y,false)}
            else{axis=3;motion.beginTilt()}
        } else if abs(translation.x)>=abs(translation.y){axis=1;motion.beginHorizontal(translation.x)}
        else {
            let index=Int(G.clamp(motion.raw.rail.rounded(),0,2)),pose=G.sourceBadge(motion.raw.rail,index,source)
            guard translation.y<0,hypot(panStart.x-pose.x,panStart.y-pose.y)<=pose.size/2+8 else{return};axis=2;motion.beginPull(translation.y,true)
        }
        gestureToken=motion.generation
    }
    private func showPanel(_ kind:PrismaticGameProfilePanel.Kind){eventTime();motion.pauseForOverlay();let panel=PrismaticGameProfilePanel(kind:kind,reduced:motion.reduced,friends:friends,saved:savedGame)
        panel.frame=canvas.bounds;panel.layer.zPosition=3000;panel.onClose={ [weak self] in self?.closePanel() };panel.onReduced={ [weak self] value in self?.localReduced=value;self?.applyConfiguration() }
        panel.onAddFriend={ [weak self] value in guard let self else{return};if !self.friends.contains(value){self.friends.append(value)} }
        panel.onSave={ [weak self] in guard let self else{return false};self.savedGame.toggle();return self.savedGame }
        panel.onPreview={ [weak self] index in guard let self else{return};self.closePanel();self.motion.select(index) }
        self.panel=panel;canvas.addSubview(panel);refresh();UIAccessibility.post(notification:.screenChanged,argument:panel.heading)
    }
    private func closePanel(){panel?.removeFromSuperview();panel=nil;motion.resumeAfterOverlay();refresh();UIAccessibility.post(notification:.screenChanged,argument:carousel)}
}
