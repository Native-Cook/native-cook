import CoreGraphics

// X(px)=px*402/274; YB(px)=874-(581-px)*402/274.
// These are evaluated RN theme values, preserving every geometry anchor.
enum GoldTicketRewardTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let BENEFIT_B_SCALE = 110.0 / 83.0
  struct Box {
    var x: Double
    var y: Double
    var w: Double
    var h: Double
    var rect: CGRect { CGRect(x: x, y: y, width: w, height: h) }
  }
  enum COPY {
    static let title = "Inspection details"
    static let help = "Get Help"
    static let status = "Expert Assigned"
    static let expert = "Arjun Mehta assigned"
    static let expertRef = "Rajesh Kumar assigned"
    static let verified = "Verified inspector"
    static let progress = "Inspection in Progress"
    static let section = "YOU CAN MAKE A DIFFERENCE"
    static let pros = "Our professionals work hard"
    static let prosSub = "Small gestures keep them going all day"
    static let water = "Offer a glass of water"
    static let thanks = "THANKS FOR YOUR INTEREST!"
    static let callback = "Our Car loan expert team will call you shortly"
    static let win = "Book Test Drive to Win"
    static let prize = "1 Gm Gold Coin"
    static let benefits = ["Benefits upto 10 Lakhs", "200+ Car Models", "Compare on the spot"]
    static let cta = "Check Now"
  }
  enum WIDTH {
    static let title = 146.71532846715328
    static let help = 63.087591240875916
    static let status = 133.5109489051095
    static let expert = 161.38686131386862
    static let verified = 98.29927007299271
    static let progress = 133.5109489051095
    static let section = 143.78102189781023
    static let pros = 196.59854014598542
    static let prosSub = 178.992700729927
    static let water = 112.97080291970804
    static let thanks = 193.66423357664235
    static let callback = 334.5109489051095
    static let winA = 190.7299270072993
    static let prizeA = 192.19708029197082
    static let winB = 159.9197080291971
    static let prizeB = 189.26277372262774
    static let benefitA = 121.77372262773723
    static let ctaA = 95.36496350364965
    static let ctaB = 90.96350364963504
  }
  enum PAGE {
    static let back = (x: 29.34306569343066, y: 79.22627737226279)
    static let title = (x: 61.620437956204384, y: 79.22627737226279)
    static let help = (x: 371.18978102189783, y: 79.22627737226279, icon: 299.2992700729927)
    static let check = (
      x: 199.53284671532847, y: 170.18978102189783, r: 28.609489051094894,
      stroke: 3.3744525547445257
    )
    static let status = (x: 200.26642335766425, y: 227.4087591240876)
    static let card = Box(
      x: 13.204379562043796, y: 261.15328467153284, w: 375.59124087591243, h: 123.2408759124088)
    static let avatar = (x: 44.01459854014599, y: 299.2992700729927, r: 11.737226277372264)
    static let expert = (x: 70.42335766423358, y: 286.0948905109489)
    static let badge = Box(
      x: 79.22627737226279, y: 304.4343065693431, w: 17.605839416058387, h: 10.270072992700705)
    static let verified = (x: 102.7007299270073, y: 310.3029197080292)
    static let phone = (x: 355.05109489051097, y: 300.7664233576643)
    static let divider = 340.37956204379566
    static let rowIcon = (x: 36.67883211678832, y: 363.8540145985402)
    static let progress = (x: 61.620437956204384, y: 363.8540145985402)
    static let chevron = (x: 362.3868613138686, y: 363.8540145985402)
    static let section = (x: 14.67153284671533, y: 424.007299270073)
    static let card2 = Box(
      x: 13.204379562043796, y: 440.1459854014599, w: 375.59124087591243, h: 90.96350364963502)
    static let pros = (x: 26.40875912408759, y: 457.7518248175183)
    static let prosSub = (x: 26.40875912408759, y: 473.89051094890516)
    static let waterIcon = (x: 32.277372262773724, y: 509.10218978102193)
    static let water = (x: 44.01459854014599, y: 509.10218978102193)
  }
  enum A {
    static let glowTop = 268.0656934306569
    static let glowMid = 397.1751824817518
    static let stageTop = 426.51824817518246
    static let stageEdge = 452.92700729927003
    static let stageInset = 54.284671532846716
    static let cars = Box(
      x: 54.284671532846716, y: 373.7007299270073, w: 297.8321167883212, h: 74.82481751824815)
    static let sparkles = [
      (x: 115.9051094890511, y: 332.62043795620434, s: 11.003649635036497, k: 7.0, ph: 0.1),
      (x: 55.751824817518255, y: 389.83941605839414, s: 5.135036496350366, k: 9.0, ph: 0.55),
      (x: 321.3065693430657, y: 379.5693430656934, s: 5.868613138686132, k: 8.0, ph: 0.3),
    ]
    static let thanks = (x: 201.0, y: 473.4671532846715)
    static let callback = (x: 199.53284671532847, y: 501.3430656934306)
    static let divider = (x0: 33.74452554744526, x1: 366.7883211678832, y: 530.6861313868612)
    static let ticket = Box(
      x: 31.543795620437958, y: 587.1715328467153, w: 338.9124087591241, h: 118.83941605839414)
    static let notchR = 7.629197080291972
    static let coin = (x: 200.26642335766425, y: 585.7043795620438, r: 30.810218978102192)
    static let halo = 38.87956204379562
    static let win = (x: 201.0, y: 637.0547445255474)
    static let prize = (x: 200.26642335766425, y: 674.4671532846716)
    static let band = Box(x: 0.0, y: 724.3503649635036, w: 402.0, h: 30.810218978102284)
    static let benefits = [
      (x: 79.95985401459855, y: 739.7554744525547), (x: 204.66788321167886, y: 739.7554744525547),
      (x: 325.7080291970803, y: 739.7554744525547),
    ]
    static let seps = [149.64963503649636, 261.15328467153284]
    static let sepH = 14.67153284671533
    static let button = Box(
      x: 14.67153284671533, y: 787.4379562043796, w: 372.6569343065694, h: 53.55109489051097)
    static let radius = 8.802919708029197
  }
  enum B {
    static let ticket = Box(
      x: 58.68613138686132, y: 579.1021897810219, w: 282.42700729927014, h: 112.2372262773722)
    static let notchR = 7.9226277372262786
    static let coin = (x: 199.53284671532847, y: 565.1642335766423, r: 35.21167883211679)
    static let win = (x: 200.26642335766425, y: 625.3175182481751)
    static let prize = (x: 200.26642335766425, y: 654.6605839416059)
    static let benefits = [
      (x: 124.7080291970803, y: 737.5547445255474), (x: 292.69708029197085, y: 737.5547445255474),
    ]
    static let sep = 217.13868613138686
    static let sepH = 19.072992700729927
    static let button = Box(
      x: 17.605839416058394, y: 791.1058394160584, w: 366.05474452554745, h: 51.35036496350358)
    static let close = (x: 365.7613138686132, y: 477.42846715328466, s: 13.204379562043796)
    static let fadeTop = 538.0218978102189
    static let full = 708.2116788321168
    static let sparkles = [
      (
        x: 39.61313868613139, y: 575.4343065693431, s: 13.204379562043796, gold: true, k: 6.0,
        ph: 0.0
      ),
      (
        x: 362.3868613138686, y: 661.2627737226277, s: 13.204379562043796, gold: true, k: 7.0,
        ph: 0.4
      ),
      (
        x: 360.9197080291971, y: 516.7481751824816, s: 7.335766423357665, gold: true, k: 9.0,
        ph: 0.7
      ),
      (
        x: 289.02919708029196, y: 527.7518248175182, s: 9.536496350364963, gold: false, k: 8.0,
        ph: 0.2
      ),
      (
        x: 256.0182481751825, y: 551.2262773722628, s: 3.814598540145986, gold: false, k: 11.0,
        ph: 0.85
      ),
      (
        x: 35.94525547445256, y: 684.0036496350365, s: 9.536496350364963, gold: false, k: 5.0,
        ph: 0.6
      ),
      (
        x: 122.507299270073, y: 525.551094890511, s: 3.814598540145986, gold: false, k: 10.0,
        ph: 0.15
      ),
    ]
  }
  enum C {
    static let page = 0xf3f3f6
    static let card = 0xffffff
    static let cardLine = 0xe4e4ec
    static let ink = 0x1e1d2b
    static let mute = 0x77768a
    static let violet = 0x3f3ab9
    static let dimA = [31.0, 29.0, 46.0, 0.75]
    static let dimB = [24.0, 23.0, 30.0, 0.85]
    static let glow = 0xd8b464
    static let glowHi = 0xf1e1ac
    static let stageTop = 0xf8efd0
    static let stageMid = 0xfdf8ea
    static let sheet = 0xffffff
    static let band = 0xfbf6e8
    static let bandInk = 0x2a2933
    static let ctaA = 0x5835d1
    static let ctaAText = 0xf6f1ff
    static let ctaB = 0xffffff
    static let ctaBText = 0x4b33b8
    static let winA = 0x1c1b22
    static let prizeA = [0x9c6a18, 0xe3c16e, 0xe6c98a]
    static let purpleFade = [82.0, 51.0, 174.0]
    static let purpleMid = 0x5233ae
    static let purpleLow = 0x2c0e8d
    static let purpleBottom = 0x211060
    static let goldHi = 0xf5d677
    static let gold = 0xe3a23f
    static let goldDeep = 0xd68e33
    static let bronze = 0x7a4a18
    static let winB = 0xfff1d6
    static let prizeB = 0xffffff
    static let starGold = 0xe8b24a
    static let starWhite = 0xffffff
  }
}
