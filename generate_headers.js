const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

function createSvg(withText = false) {
  // Dot matrix world map coords
  const mapDots = [];
  // Approximate world map continental clusters in range x: 650..1150, y: 35..165
  // North America
  for (let x = 660; x <= 780; x += 14) {
    for (let y = 45; y <= 95; y += 14) {
      if ((x - 720)**2 / 60**2 + (y - 70)**2 / 25**2 <= 1 + Math.sin(x*y)*0.3) {
        mapDots.push({ x, y });
      }
    }
  }
  // South America
  for (let x = 720; x <= 790; x += 14) {
    for (let y = 100; y <= 165; y += 14) {
      if ((x - 750)**2 / 35**2 + (y - 130)**2 / 30**2 <= 1) {
        mapDots.push({ x, y });
      }
    }
  }
  // Europe
  for (let x = 820; x <= 890; x += 14) {
    for (let y = 40; y <= 80; y += 14) {
      if ((x - 855)**2 / 35**2 + (y - 60)**2 / 20**2 <= 1) {
        mapDots.push({ x, y });
      }
    }
  }
  // Africa
  for (let x = 830; x <= 910; x += 14) {
    for (let y = 85; y <= 155; y += 14) {
      if ((x - 870)**2 / 35**2 + (y - 120)**2 / 35**2 <= 1) {
        mapDots.push({ x, y });
      }
    }
  }
  // Asia
  for (let x = 900; x <= 1120; x += 14) {
    for (let y = 45; y <= 110; y += 14) {
      if ((x - 1000)**2 / 90**2 + (y - 75)**2 / 30**2 <= 1) {
        mapDots.push({ x, y });
      }
    }
  }
  // Australia
  for (let x = 1020; x <= 1100; x += 14) {
    for (let y = 125; y <= 165; y += 14) {
      if ((x - 1060)**2 / 35**2 + (y - 145)**2 / 18**2 <= 1) {
        mapDots.push({ x, y });
      }
    }
  }

  const dotsSvg = mapDots.map(d => `<circle cx="${d.x}" cy="${d.y}" r="2" fill="#E50914" opacity="0.35" />`).join('\n');

  return `
<svg width="1200" height="200" viewBox="0 0 1200 200" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <!-- Background Gradient -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#020306" />
      <stop offset="25%" stop-color="#140104" />
      <stop offset="55%" stop-color="#220207" />
      <stop offset="85%" stop-color="#0A0103" />
      <stop offset="100%" stop-color="#020306" />
    </linearGradient>

    <!-- Neon Glow Filter -->
    <filter id="neonGlow" x="-30%" y="-30%" width="160%" height="160%">
      <feGaussianBlur stdDeviation="6" result="blur" />
      <feMerge>
        <feMergeNode in="blur" />
        <feMergeNode in="SourceGraphic" />
      </feMerge>
    </filter>

    <filter id="intenseGlow" x="-50%" y="-50%" width="200%" height="200%">
      <feGaussianBlur stdDeviation="12" result="blur1" />
      <feGaussianBlur stdDeviation="4" result="blur2" />
      <feMerge>
        <feMergeNode in="blur1" />
        <feMergeNode in="blur2" />
        <feMergeNode in="SourceGraphic" />
      </feMerge>
    </filter>

    <!-- Linear Ribbon Gradient -->
    <linearGradient id="ribbonGrad1" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FF1E27" stop-opacity="0" />
      <stop offset="30%" stop-color="#FF1E27" stop-opacity="0.8" />
      <stop offset="70%" stop-color="#E50914" stop-opacity="0.9" />
      <stop offset="100%" stop-color="#990000" stop-opacity="0.1" />
    </linearGradient>
    <linearGradient id="ribbonGrad2" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FF5252" stop-opacity="0" />
      <stop offset="40%" stop-color="#FF1744" stop-opacity="0.9" />
      <stop offset="80%" stop-color="#D50000" stop-opacity="0.6" />
      <stop offset="100%" stop-color="#4A0007" stop-opacity="0" />
    </linearGradient>

    <!-- Router Gradients -->
    <linearGradient id="routerChassis" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#2A2F3D" />
      <stop offset="40%" stop-color="#141822" />
      <stop offset="80%" stop-color="#080A0F" />
      <stop offset="100%" stop-color="#020306" />
    </linearGradient>

    <linearGradient id="antennaGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3A4050" />
      <stop offset="70%" stop-color="#151922" />
      <stop offset="100%" stop-color="#0A0C10" />
    </linearGradient>

    <radialGradient id="wifiRadial" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#FF1744" stop-opacity="0.9" />
      <stop offset="50%" stop-color="#D50000" stop-opacity="0.3" />
      <stop offset="100%" stop-color="#FF1744" stop-opacity="0" />
    </radialGradient>
  </defs>

  <!-- Outer Rounded Banner Frame -->
  <rect x="2" y="2" width="1196" height="196" rx="20" fill="url(#bgGrad)" stroke="#FF1E27" stroke-width="2.5" />

  <!-- Background Ambient Red Radial Glows -->
  <circle cx="230" cy="90" r="140" fill="url(#wifiRadial)" />
  <circle cx="850" cy="100" r="180" fill="url(#wifiRadial)" opacity="0.3" />

  <!-- Dotted World Map Clusters -->
  <g>
    ${dotsSvg}
  </g>

  <!-- World Map Flight / Connection Arcs -->
  <g fill="none" stroke="#FF1744" stroke-width="1.2" opacity="0.6">
    <path d="M 720 70 Q 780 40 855 60" />
    <path d="M 855 60 Q 920 40 1000 75" />
    <path d="M 750 130 Q 810 110 870 120" />
    <path d="M 870 120 Q 940 100 1060 145" />
    <path d="M 720 70 Q 740 100 750 130" />
    <path d="M 855 60 Q 860 90 870 120" />
  </g>
  <!-- Connection Nodes / Pulse Dots -->
  <g fill="#FFFFFF" filter="url(#intenseGlow)">
    <circle cx="720" cy="70" r="3.5" fill="#FFFFFF" />
    <circle cx="855" cy="60" r="3.5" fill="#FFFFFF" />
    <circle cx="1000" cy="75" r="3.5" fill="#FFFFFF" />
    <circle cx="750" cy="130" r="3" fill="#FFFFFF" />
    <circle cx="870" cy="120" r="3" fill="#FFFFFF" />
    <circle cx="1060" cy="145" r="3" fill="#FFFFFF" />
  </g>

  <!-- Flowing 3D Neon Ribbon Curves -->
  <g fill="none">
    <!-- Main sweeping ribbon 1 -->
    <path d="M 0 130 Q 150 10 320 120 T 680 80 T 980 150 T 1200 40" stroke="url(#ribbonGrad1)" stroke-width="24" opacity="0.75" filter="url(#neonGlow)" />
    <path d="M 0 140 Q 160 20 330 130 T 700 90 T 1000 160 T 1200 50" stroke="url(#ribbonGrad2)" stroke-width="12" opacity="0.85" filter="url(#intenseGlow)" />
    <!-- Fine laser strands -->
    <path d="M 50 160 Q 220 30 380 140 T 750 70 T 1050 140 T 1200 80" stroke="#FF5252" stroke-width="2.5" opacity="0.9" />
    <path d="M 20 170 Q 200 50 360 150 T 730 85 T 1030 155 T 1200 95" stroke="#FF1744" stroke-width="1.5" opacity="0.7" />
    <path d="M 0 180 Q 180 70 340 160 T 710 100 T 1010 170 T 1200 110" stroke="#D50000" stroke-width="1" opacity="0.5" />
    <path d="M 200 185 Q 380 110 550 170 T 900 110 T 1200 150" stroke="#FF1744" stroke-width="1.8" opacity="0.6" />
  </g>

  <!-- ================= 3D ROUTER SECTION (LEFT SIDE) ================= -->
  <!-- Circular Glowing Dais / Pedestal Underneath -->
  <g filter="url(#neonGlow)">
    <ellipse cx="230" cy="162" rx="130" ry="24" fill="#120104" stroke="#FF1744" stroke-width="2.5" />
    <ellipse cx="230" cy="160" rx="100" ry="17" fill="#200207" stroke="#FF5252" stroke-width="1.5" />
  </g>

  <!-- Concentric Radiant Wi-Fi Waves Behind Router -->
  <g filter="url(#intenseGlow)">
    <!-- Aura circle -->
    <circle cx="230" cy="80" r="60" fill="none" stroke="#FF1744" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.4" />
    <!-- Wave 3 -->
    <path d="M 175 62 A 65 65 0 0 1 285 62" fill="none" stroke="#FFFFFF" stroke-width="8" stroke-linecap="round" />
    <path d="M 175 62 A 65 65 0 0 1 285 62" fill="none" stroke="#FF1744" stroke-width="12" stroke-linecap="round" opacity="0.8" />
    <!-- Wave 2 -->
    <path d="M 193 78 A 42 42 0 0 1 267 78" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" />
    <path d="M 193 78 A 42 42 0 0 1 267 78" fill="none" stroke="#FF1744" stroke-width="10" stroke-linecap="round" opacity="0.85" />
    <!-- Wave 1 -->
    <path d="M 212 94 A 20 20 0 0 1 248 94" fill="none" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" />
    <path d="M 212 94 A 20 20 0 0 1 248 94" fill="none" stroke="#FF1744" stroke-width="8" stroke-linecap="round" opacity="0.9" />
    <!-- Center Dot -->
    <circle cx="230" cy="104" r="5" fill="#FFFFFF" />
    <circle cx="230" cy="104" r="7" fill="#FF1744" opacity="0.9" />
  </g>

  <!-- Left Antenna -->
  <g>
    <!-- Shadow / Base -->
    <ellipse cx="140" cy="120" rx="8" ry="4" fill="#0A0C10" />
    <!-- Stem angled outward to left -->
    <path d="M 143 120 L 126 28 L 136 28 L 149 120 Z" fill="url(#antennaGrad)" stroke="#111" stroke-width="1" />
    <!-- Antenna glowing tip & red edge highlight -->
    <line x1="128" y1="32" x2="144" y2="120" stroke="#FF1744" stroke-width="1.8" opacity="0.8" />
    <ellipse cx="131" cy="28" rx="5" ry="3" fill="#333" stroke="#FF1744" stroke-width="1" />
  </g>

  <!-- Right Antenna -->
  <g>
    <!-- Base -->
    <ellipse cx="320" cy="120" rx="8" ry="4" fill="#0A0C10" />
    <!-- Stem angled outward to right -->
    <path d="M 317 120 L 334 28 L 324 28 L 311 120 Z" fill="url(#antennaGrad)" stroke="#111" stroke-width="1" />
    <!-- Red edge highlight -->
    <line x1="332" y1="32" x2="316" y2="120" stroke="#FF1744" stroke-width="1.8" opacity="0.8" />
    <ellipse cx="329" cy="28" rx="5" ry="3" fill="#333" stroke="#FF1744" stroke-width="1" />
  </g>

  <!-- Router Chassis 3D Body -->
  <g>
    <!-- Outer base shadow -->
    <rect x="135" y="112" width="190" height="52" rx="14" fill="#000000" opacity="0.9" />
    <!-- Main 3D Box -->
    <rect x="135" y="110" width="190" height="50" rx="14" fill="url(#routerChassis)" stroke="#2C3446" stroke-width="1.5" />
    <!-- Top Carbon Mesh / Pattern Lid -->
    <path d="M 145 112 L 315 112 L 305 130 L 155 130 Z" fill="#0D1017" stroke="#1F2633" stroke-width="0.8" />
    <!-- Front Face Bevel Line -->
    <line x1="145" y1="131" x2="315" y2="131" stroke="#3A445C" stroke-width="1" />

    <!-- Front Panel Power Button & Glowing Red LEDs -->
    <!-- Power Button with Neon Red Ring -->
    <circle cx="175" cy="143" r="10" fill="#0A0D14" stroke="#FF1744" stroke-width="2" filter="url(#neonGlow)" />
    <!-- Power Icon glyph -->
    <path d="M 175 137 L 175 142" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" />
    <path d="M 172 139 A 5 5 0 1 0 178 139" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" />

    <!-- 7 Glowing Red Status LEDs -->
    <g filter="url(#intenseGlow)">
      <circle cx="215" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="215" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="230" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="230" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="245" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="245" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="260" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="260" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="275" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="275" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="290" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="290" cy="143" r="5" fill="#FF1744" opacity="0.85" />

      <circle cx="305" cy="143" r="3.2" fill="#FFFFFF" />
      <circle cx="305" cy="143" r="5" fill="#FF1744" opacity="0.85" />
    </g>

    <!-- Red Bottom Chassis Underglow Line -->
    <line x1="145" y1="160" x2="315" y2="160" stroke="#FF1744" stroke-width="2" opacity="0.9" filter="url(#neonGlow)" />
  </g>

  ${withText ? `
  <!-- ================= HOME HEADER TYPOGRAPHY (RIGHT SIDE) ================= -->
  <!-- 3D White Arabic Title with Red Glow -->
  <g filter="url(#intenseGlow)">
    <text x="890" y="102" text-anchor="middle" font-family="'Cairo', 'Segoe UI', Tahoma, sans-serif" font-weight="900" font-size="58" fill="#FFFFFF" letter-spacing="1">الاختبار</text>
  </g>

  <!-- Glowing Red Pill Capsule Subtitle -->
  <g>
    <!-- Capsule glow shadow -->
    <rect x="690" y="122" width="400" height="42" rx="21" fill="#1C0206" fill-opacity="0.85" stroke="#FF1E27" stroke-width="2.5" filter="url(#neonGlow)" />
    <!-- Subtitle text -->
    <text x="890" y="150" text-anchor="middle" font-family="'Cairo', 'Segoe UI', Tahoma, sans-serif" font-weight="800" font-size="20" fill="#FFFFFF">فحص بطاقات الهوت سبوت</text>
  </g>
  ` : ''}

</svg>
`;
}

async function run() {
  const svgHome = createSvg(true);
  const svgPages = createSvg(false);

  // Write SVGs
  fs.writeFileSync(path.join(__dirname, 'public/header_home.svg'), svgHome);
  fs.writeFileSync(path.join(__dirname, 'public/header_pages.svg'), svgPages);

  // Render to PNG
  await sharp(Buffer.from(svgHome)).png().toFile(path.join(__dirname, 'public/header_home.png'));
  await sharp(Buffer.from(svgPages)).png().toFile(path.join(__dirname, 'public/header_pages.png'));

  // Also replace JPGs so existing references work seamlessly
  await sharp(Buffer.from(svgHome)).jpeg({ quality: 95 }).toFile(path.join(__dirname, 'public/header_home.jpg'));
  await sharp(Buffer.from(svgPages)).jpeg({ quality: 95 }).toFile(path.join(__dirname, 'public/header_pages.jpg'));

  // Copy to Android drawable
  const resDir = path.join(__dirname, 'app/src/main/res/drawable');
  if (fs.existsSync(resDir)) {
    fs.copyFileSync(path.join(__dirname, 'public/header_home.png'), path.join(resDir, 'header_home.png'));
    fs.copyFileSync(path.join(__dirname, 'public/header_pages.png'), path.join(resDir, 'header_pages.png'));
    fs.copyFileSync(path.join(__dirname, 'public/header_home.jpg'), path.join(resDir, 'header_home.jpg'));
    fs.copyFileSync(path.join(__dirname, 'public/header_pages.jpg'), path.join(resDir, 'header_pages.jpg'));
  }

  console.log('Successfully generated headers: home & pages in SVG, PNG, and JPG format!');
}

run().catch(console.error);
