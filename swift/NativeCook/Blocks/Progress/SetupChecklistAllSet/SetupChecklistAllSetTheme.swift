enum SetupChecklistAllSetTheme {
  static let REF_W = 402.0, REF_H = 874.0
  enum C {
    static let page = "#F3F3F1"
    static let ink = "#121212"
    static let ink2 = "#2B2B2B"
    static let grey = "#6E6E6A"
    static let mute = "#A8A8A4"
    static let dim = "#000000"
    static let sheet = "#FFFFFF"
    static let purple = "#7C41FF"
    static let badge = "#8022FE"
    static let barFrom = "#2865DA"
    static let barTo = "#7943FD"
    static let track = "#E0E1D9"
    static let green = "#0C7324"
    static let ring = "#DFE0DB"
    static let divider = "#EFEFEC"
    static let close = "#EFEFED"
    static let chip = "#E9E9E6"
    static let lilac = "#EDC2ED"
    static let popper = "#1F1A6B"
    static let cta = "#8129D9"
    static let pro = "#5B2BD1"
    static let confetti = ["#8022FE", "#EDC2ED", "#FFC83D", "#2865DA", "#0C7324", "#FF6FA8"]
  }
  enum COPY {
    static let handle = "alexsmith"
    static let url = "tapline.me/alexsmith"
    static let pro = "Try Pro for free"
    static let chips = ["Products", "Design", "Settings"]
    static let title = "Your setup checklist"
    static let allSet = "You're all set!"
    static let allSetBody = [
      "Now that you’ve shared your Tapline,", "keep that momentum going by sharing it in",
      "even more places.",
    ]
    static let cta = "How to keep growing your traffic"
    static let later = "Maybe later"
    static let skip = "Skip"
    static let ring = ["17%", "set up"]
  }
  enum UI {
    enum sheet {
      static let y = 299.0
      static let r = 34.0
    }
    enum title {
      static let x = 17.0
      static let y = 338.0
    }
    enum badge {
      static let x = 179.0
      static let y = 328.0
      static let w = 37.0
      static let h = 20.0
    }
    enum close {
      static let x = 371.0
      static let y = 338.0
      static let r = 14.0
    }
    enum bar {
      static let x = 16.5
      static let y = 369.0
      static let w = 369.5
      static let h = 11.4
    }
    enum rows {
      static let y0 = 396.5
      static let h = 54.3
      static let open = 115.5
      enum check {
        static let x = 28.3
        static let r = 11.0
      }
      static let textX = 49.4
      static let chevronX = 377.0
    }
    enum desc {
      static let dy = [39.5, 57.8]
    }
    enum action {
      static let dy = 100.5
      static let h = 34.0
      static let padX = 13.0
    }
    static let skipDx = 22.0
    enum page {
      enum avatar {
        static let x = 37.5
        static let y = 97.0
        static let r = 20.0
      }
      enum pro {
        static let x = 76.0
        static let y = 77.0
        static let w = 134.0
        static let h = 40.0
      }
      enum share {
        static let x = 316.0
        static let y = 97.0
      }
      enum bell {
        static let x = 373.0
        static let y = 97.0
      }
      enum handle {
        static let x = 17.0
        static let y = 150.0
      }
      enum url {
        static let x = 17.0
        static let y = 192.0
      }
      enum chips {
        static let y = 249.0
        static let x = [17.0, 145.0, 259.0]
        static let w = [121.0, 107.0, 117.0]
        static let h = 40.0
      }
      enum cards {
        static let y = 296.0
        static let h = 207.0
      }
      enum ring {
        static let x = 41.0
        static let y = 572.0
        static let r = 31.0
      }
    }
    enum allSet {
      static let y = 68.0
      enum icon {
        static let x = 201.0
        static let y = 390.0
        static let r = 51.0
      }
      static let title = 486.5
      static let body = [528.0, 552.0, 576.0]
      enum cta {
        static let x = 16.0
        static let y = 689.0
        static let w = 370.0
        static let h = 51.0
      }
      static let later = 775.5
    }
  }
  struct Item {
    let title: String
    let body: [String]
    let action: String
  }
  static let items = [
    Item(
      title: "Add your name and bio",
      body: ["This will be the first thing visitors see when they", "land on your Tapline."],
      action: "Add name and bio"),
    Item(
      title: "Add your profile image",
      body: ["A friendly face tells visitors they have found", "the right page."],
      action: "Add profile image"),
    Item(
      title: "Add your socials",
      body: ["Link the places people already follow you,", "all in one row."], action: "Add socials"
    ),
    Item(
      title: "Add your other important links",
      body: ["Your shop, your newsletter, your latest video:", "one tap away."], action: "Add links"
    ),
    Item(
      title: "Customize your design",
      body: [
        "Pick a look and feel that perfectly reflects who you",
        "are and how you want to appear to the world.",
      ], action: "Customize design"),
    Item(
      title: "Share your Tapline",
      body: ["Put your Tapline in your bio so every visitor", "finds the rest of you."],
      action: "Copy link"),
  ]
}
