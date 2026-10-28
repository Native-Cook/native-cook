import UIKit

/// Honest local actions; profile numbers and currency never change.
@MainActor
final class PrismaticGameProfilePanel:UIView {
    enum Kind {case settings,friend,game,home,store}
    let heading=UILabel()
    var onClose:(()->Void)?
    var onReduced:((Bool)->Void)?
    var onAddFriend:((String)->Void)?
    var onSave:(()->Bool)?
    var onPreview:((Int)->Void)?
    private let stack=UIStackView(),sheet=UIView(),scroll=UIScrollView()
    private let username=UITextField(),savedFriends=UILabel()
    private var names:[String]
    private let reduced:Bool
    init(kind:Kind,reduced:Bool,friends:[String],saved:Bool){self.reduced=reduced;names=friends;super.init(frame:CGRect(x:0,y:0,width:393,height:852));backgroundColor=PrismaticGameProfileAssets.color(0x102E4D,0.13);accessibilityViewIsModal=true
        let backdrop=PrismaticGameProfileButton(frame:bounds);backdrop.accessibilityLabel="Close profile panel";backdrop.action={ [weak self] in self?.onClose?() };addSubview(backdrop)
        sheet.frame=CGRect(x:20,y:220,width:353,height:440);sheet.layer.cornerRadius=24;sheet.backgroundColor=PrismaticGameProfileAssets.color(0xF4FBFF);sheet.layer.borderWidth=1;sheet.layer.borderColor=PrismaticGameProfileAssets.color(0xCFDFEC).cgColor;addSubview(sheet)
        heading.frame=CGRect(x:18,y:14,width:285,height:30);heading.font = .systemFont(ofSize:20,weight:.heavy);heading.textColor=PrismaticGameProfileAssets.color(0x102E45);heading.accessibilityTraits = .header
        heading.text=switch kind{case .settings:"Profile settings";case .friend:"Local friends";case .game:"Neon Run";case .home:"Home preview";case .store:"Badge previews"};sheet.addSubview(heading)
        let close=PrismaticGameProfileButton(frame:CGRect(x:305,y:2,width:44,height:48));close.setMaterialIcon("close",size:22,color:PrismaticGameProfileAssets.color(0x1A4B71));close.accessibilityLabel="Close profile panel";close.action={ [weak self] in self?.onClose?() };sheet.addSubview(close)
        scroll.frame=CGRect(x:0,y:56,width:353,height:370);scroll.keyboardDismissMode = .onDrag;sheet.addSubview(scroll)
        stack.axis = .vertical;stack.spacing=14;stack.alignment = .fill;stack.translatesAutoresizingMaskIntoConstraints=false;scroll.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor,constant:16),stack.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor,constant:-16),stack.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor,constant:4),stack.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor,constant:-16),stack.widthAnchor.constraint(equalTo:scroll.frameLayoutGuide.widthAnchor,constant:-32)])
        switch kind {
        case .settings:
            let row=UIStackView();row.axis = .horizontal;let title=text("Reduce motion",16);row.addArrangedSubview(title);let toggle=UISwitch();toggle.isOn=reduced;toggle.accessibilityLabel="Reduce motion for this profile";toggle.addTarget(self,action:#selector(toggleReduced(_:)),for:.valueChanged);row.addArrangedSubview(toggle);stack.addArrangedSubview(row)
            stack.addArrangedSubview(text("This local preference also respects your system’s Reduce Motion setting.",13))
        case .friend:
            username.placeholder="Username";username.accessibilityLabel="Local friend's username";username.borderStyle = .roundedRect;username.autocapitalizationType = .none;username.autocorrectionType = .no;username.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;stack.addArrangedSubview(username)
            addButton("Add locally"){ [weak self] in guard let self else{return};let value=self.username.text?.trimmingCharacters(in:.whitespacesAndNewlines) ?? "";guard !value.isEmpty else{return};self.onAddFriend?(value);if !self.names.contains(value){self.names.append(value)};self.savedFriends.text=self.names.map{"\($0) · Saved locally"}.joined(separator:"\n");self.username.text="";self.endEditing(true) }
            savedFriends.numberOfLines=0;savedFriends.font = .preferredFont(forTextStyle:.body);savedFriends.text=names.map{"\($0) · Saved locally"}.joined(separator:"\n");stack.addArrangedSubview(savedFriends);stack.addArrangedSubview(text("No friend request is sent from this demo.",13))
        case .game:
            let image=UIImageView(image:PrismaticGameProfileAssets.image("game"));image.contentMode = .scaleAspectFit;image.heightAnchor.constraint(equalToConstant:160).isActive=true;stack.addArrangedSubview(image)
            stack.addArrangedSubview(text("87% likes · 315.6K plays",16));stack.addArrangedSubview(text("Local game preview. No external game is launched.",13))
            let b=addButton(saved ? "Saved locally":"Save locally",{});b.action={ [weak self,weak b] in let saved=self?.onSave?() ?? false;b?.setTitle(saved ? "Saved locally":"Save locally",for:.normal) }
        case .store:
            stack.addArrangedSubview(text("Preview an object without spending rubies or coins.",13))
            for (index,name) in ["Crown","Shield","Diamond"].enumerated(){let b=addButton("\(name) · Preview"){[weak self] in self?.onPreview?(index)};b.accessibilityLabel="Preview \(name) badge without a purchase"}
        case .home:
            stack.addArrangedSubview(text("Everen’s local profile",16));stack.addArrangedSubview(text("Skills 1.500 · Ruby balance 72,498 · Coin balance 15,380",13));addButton("View profile"){[weak self] in self?.onClose?()};stack.addArrangedSubview(text("All previews stay on this device. No purchases or external game launch occur.",13))
        }
    }
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    private func text(_ content:String,_ size:Double)->UILabel {let l=UILabel();l.text=content;l.numberOfLines=0;l.font=UIFontMetrics.default.scaledFont(for:.systemFont(ofSize:size));l.adjustsFontForContentSizeCategory=true;l.textColor=PrismaticGameProfileAssets.color(0x536B7D);return l}
    @discardableResult private func addButton(_ title:String,_ action:@escaping()->Void)->PrismaticGameProfileButton {
        let b=PrismaticGameProfileButton();b.reduced=reduced;b.setTitle(title,for:.normal);b.titleLabel?.font = .preferredFont(forTextStyle:.headline);b.backgroundColor=PrismaticGameProfileAssets.color(0x006BC8);b.layer.cornerRadius=14;b.action=action;b.accessibilityLabel=title;b.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;stack.addArrangedSubview(b);return b
    }
    @objc private func toggleReduced(_ toggle:UISwitch){onReduced?(toggle.isOn)}
}
