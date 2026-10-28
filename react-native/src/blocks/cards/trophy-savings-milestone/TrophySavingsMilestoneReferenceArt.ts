// Reference-calibrated flat vector. Frame: 176×214 at scene (108,96).
export const TROPHY_REFERENCE_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" width="176" height="214" viewBox="0 0 176 214">
<defs>
  <linearGradient id="lip" gradientUnits="userSpaceOnUse" x1="29.5" y1="8" x2="146.5" y2="8"><stop stop-color="#FFD345"/><stop offset=".55" stop-color="#FFCD41"/><stop offset="1" stop-color="#FFCF43"/></linearGradient>
  <linearGradient id="stem" gradientUnits="userSpaceOnUse" x1="88" y1="126" x2="88" y2="165"><stop stop-color="#FFCF4B"/><stop offset="1" stop-color="#FFCC45"/></linearGradient>
  <linearGradient id="foot" gradientUnits="userSpaceOnUse" x1="88" y1="159" x2="88" y2="171"><stop stop-color="#FFD24D"/><stop offset="1" stop-color="#FFCC45"/></linearGradient>
  <linearGradient id="base" gradientUnits="userSpaceOnUse" x1="88" y1="170" x2="88" y2="214"><stop stop-color="#6F5750"/><stop offset=".25" stop-color="#6C554F"/><stop offset=".8" stop-color="#6C554F"/><stop offset="1" stop-color="#69534D"/></linearGradient>
  <linearGradient id="plaque" gradientUnits="userSpaceOnUse" x1="88" y1="177.5" x2="88" y2="200.5"><stop stop-color="#FFAF28"/><stop offset="1" stop-color="#FFAD27"/></linearGradient>
  <clipPath id="bowlClip"><path d="M31 24C58 9 118 9 144 24C144 61 132 90 106 112C100 119 94 122 87.5 123C81 122 75 120 70 115C42 94 32 60 31 24Z"/></clipPath>
  <clipPath id="lipClip"><path d="M29.5 12.5C29.5 5.7 55.5 .2 87.5 .2C120.1 .2 146.5 5.7 146.5 12.5L145 24C118 9 58 9 30.5 24Z"/></clipPath>
  <clipPath id="baseClip"><rect x="43" y="170" width="91" height="44" rx="5"/></clipPath>
  <clipPath id="plaqueClip"><rect x="59" y="177.5" width="58" height="23" rx="2"/></clipPath>
</defs>
<path d="M29.5 27H3.5C3.3 35 4 44 7.3 53C14.5 70 31.5 81 48.5 88M146.5 27H172.5C172.7 35 172 44 168.7 53C161.5 70 144.5 81 127.5 88" fill="none" stroke="#FFB31C" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M31 24C58 9 118 9 144 24C144 61 132 90 106 112C100 119 94 122 87.5 123C81 122 75 120 70 115C42 94 32 60 31 24Z" fill="#FFE266"/>
<g clip-path="url(#bowlClip)">
  <path d="M51 15C49 55 57 82 64 96C68 105 73 113 77 118C68 99 64 58 66 14Z" fill="#FFF0AB"/>
  <path d="M135.5 20.5L144 24C144 61 132 90 106 112C100 119 94 122 87.5 123C101 114 119 94 128 70C133 51 135 35 135.5 20.5Z" fill="#FFD632"/>
</g>
<path d="M29.5 12.5C29.5 5.7 55.5 .2 87.5 .2C120.1 .2 146.5 5.7 146.5 12.5L145 24C118 9 58 9 30.5 24Z" fill="url(#lip)"/>
<path d="M51 0H67L66 23L50 24Z" fill="white" fill-opacity=".45" clip-path="url(#lipClip)"/>
<path d="M30.5 24C58 9 118 9 145 24" fill="none" stroke="#F2B62F" stroke-width="1.1"/>
<path d="M66 26L68.5 31.5L75.5 34L68.5 36.5L66 42L63.5 36.5L56.5 34L63.5 31.5Z" fill="white" fill-opacity=".88"/>
<path d="M81 126C84 127 92 127 95.5 126V141C96.5 154 108 159 118.5 161V165C103 160 72 160 57 165V161C68 159 81 153 81 141Z" fill="url(#stem)"/>
<rect x="71.5" y="118.5" width="32" height="9.5" rx="3" fill="#F3AC2C"/>
<rect x="73" y="119.5" width="29" height="6.5" rx="2" fill="#FFD24B"/>
<rect x="73.5" y="120" width="28" height="2" rx="1" fill="#FFE477"/>
<path d="M50.5 166C52.5 163 54.5 161.5 58.5 160.5C75 158.5 100 158.5 117 160.5C121 161.5 123.5 163 124.5 166L124 171H50.5Z" fill="url(#foot)"/>
<rect x="43" y="170" width="91" height="44" rx="5" fill="url(#base)"/>
<rect x="43" y="170" width="1.5" height="44" fill="#635149" clip-path="url(#baseClip)"/>
<g clip-path="url(#baseClip)">
  <path d="M85 170H100L66 214H51Z" fill="#FFF6EE" fill-opacity=".35"/>
  <path d="M114 170H121L87 214H80Z" fill="#DED1CF" fill-opacity=".45"/>
</g>
<rect x="59" y="177.5" width="58" height="23" rx="2" fill="url(#plaque)"/>
<g clip-path="url(#plaqueClip)">
  <path d="M85 170H100L66 214H51Z" fill="#FFFF9B" fill-opacity=".7"/>
  <path d="M114 170H121L87 214H80Z" fill="#FFE9C4" fill-opacity=".45"/>
</g>
</svg>`;
