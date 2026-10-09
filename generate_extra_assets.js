const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

function createDrawerHeaderSvg() {
  return `
<svg width="500" height="260" viewBox="0 0 500 260" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FF1744" />
      <stop offset="35%" stop-color="#80020E" />
      <stop offset="70%" stop-color="#250307" />
      <stop offset="100%" stop-color="#0E121B" />
    </linearGradient>

    <radialGradient id="glowRad" cx="50%" cy="30%" r="50%">
      <stop offset="0%" stop-color="#FF5252" stop-opacity="0.8" />
      <stop offset="60%" stop-color="#FF1744" stop-opacity="0.2" />
      <stop offset="100%" stop-color="#FF1744" stop-opacity="0" />
    </radialGradient>

    <filter id="glow" x="-30%" y="-30%" width="160%" height="160%">
      <feGaussianBlur stdDeviation="6" result="blur" />
      <feMerge>
        <feMergeNode in="blur" />
        <feMergeNode in="SourceGraphic" />
      </feMerge>
    </filter>
  </defs>

  <rect width="500" height="260" fill="url(#bgGrad)" />
  <circle cx="250" cy="80" r="120" fill="url(#glowRad)" />

  <!-- Flowing background mesh curves -->
  <path d="M 0 80 Q 150 160 300 70 T 500 130" fill="none" stroke="#FF1744" stroke-width="2" opacity="0.4" />
  <path d="M 0 110 Q 180 180 320 90 T 500 150" fill="none" stroke="#FF5252" stroke-width="1.5" opacity="0.3" />

  <!-- Concentric Wi-Fi Arcs -->
  <g filter="url(#glow)">
    <path d="M 190 50 A 70 70 0 0 1 310 50" fill="none" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" />
    <path d="M 210 68 A 45 45 0 0 1 290 68" fill="none" stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" />
    <path d="M 230 85 A 22 22 0 0 1 270 85" fill="none" stroke="#FFFFFF" stroke-width="4.5" stroke-linecap="round" />
    <circle cx="250" cy="98" r="4" fill="#FFFFFF" />
  </g>

  <!-- 3D Router on Right/Center -->
  <!-- Antennas -->
  <line x1="200" y1="110" x2="190" y2="45" stroke="#1B202E" stroke-width="6" stroke-linecap="round" />
  <line x1="198" y1="110" x2="188" y2="45" stroke="#FF1744" stroke-width="1.5" />
  <line x1="300" y1="110" x2="310" y2="45" stroke="#1B202E" stroke-width="6" stroke-linecap="round" />
  <line x1="302" y1="110" x2="312" y2="45" stroke="#FF1744" stroke-width="1.5" />

  <!-- Router body -->
  <rect x="180" y="100" width="140" height="42" rx="10" fill="#0A0D15" stroke="#252D3E" stroke-width="1.5" />
  <line x1="180" y1="118" x2="320" y2="118" stroke="#FF1744" stroke-width="1.2" opacity="0.8" />

  <!-- LED status dots -->
  <g filter="url(#glow)">
    <circle cx="210" cy="128" r="2.5" fill="#FF1744" />
    <circle cx="225" cy="128" r="2.5" fill="#FF1744" />
    <circle cx="240" cy="128" r="2.5" fill="#FF1744" />
    <circle cx="255" cy="128" r="2.5" fill="#FF1744" />
    <circle cx="270" cy="128" r="2.5" fill="#FF1744" />
    <circle cx="285" cy="128" r="2.5" fill="#FF1744" />
  </g>

  <!-- Title & Subtitle text -->
  <text x="250" y="185" text-anchor="middle" font-family="'Cairo', sans-serif" font-weight="900" font-size="24" fill="#FFFFFF" letter-spacing="0.5">WiFi Card Master Pro</text>
  <text x="250" y="215" text-anchor="middle" font-family="'Cairo', sans-serif" font-weight="700" font-size="14" fill="#FFA5A5">أداة فحص بطاقات الهوت سبوت</text>
</svg>
`;
}

function createEmptySearchSvg() {
  return `
<svg width="400" height="400" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <radialGradient id="magGlow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#FF1744" stop-opacity="0.3" />
      <stop offset="100%" stop-color="#FF1744" stop-opacity="0" />
    </radialGradient>
    <filter id="neon" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="8" result="blur" />
      <feMerge>
        <feMergeNode in="blur" />
        <feMergeNode in="SourceGraphic" />
      </feMerge>
    </filter>
  </defs>

  <rect width="400" height="400" fill="#0A0E17" rx="24" />
  <circle cx="200" cy="180" r="140" fill="url(#magGlow)" />

  <!-- Document Card Behind -->
  <rect x="130" y="90" width="110" height="150" rx="16" fill="#111624" stroke="#1E273B" stroke-width="2" />
  <rect x="145" y="120" width="80" height="8" rx="4" fill="#FF1744" opacity="0.8" />
  <rect x="145" y="145" width="60" height="6" rx="3" fill="#334155" />
  <rect x="145" y="165" width="70" height="6" rx="3" fill="#334155" />
  <rect x="145" y="185" width="50" height="6" rx="3" fill="#334155" />
  <rect x="145" y="205" width="65" height="6" rx="3" fill="#334155" />

  <!-- Magnifying Glass with Red Neon Rim -->
  <g filter="url(#neon)">
    <circle cx="230" cy="180" r="48" fill="none" stroke="#FF1744" stroke-width="6" />
    <circle cx="230" cy="180" r="42" fill="#FF1744" fill-opacity="0.1" />
    <line x1="264" y1="214" x2="300" y2="250" stroke="#FF1744" stroke-width="10" stroke-linecap="round" />
  </g>
</svg>
`;
}

async function run() {
  const drawerSvg = createDrawerHeaderSvg();
  const emptySvg = createEmptySearchSvg();

  fs.writeFileSync(path.join(__dirname, 'public/drawer_header.svg'), drawerSvg);
  fs.writeFileSync(path.join(__dirname, 'public/empty_search.svg'), emptySvg);

  await sharp(Buffer.from(drawerSvg)).png().toFile(path.join(__dirname, 'public/drawer_header.png'));
  await sharp(Buffer.from(drawerSvg)).jpeg({ quality: 95 }).toFile(path.join(__dirname, 'public/drawer_header.jpg'));

  await sharp(Buffer.from(emptySvg)).png().toFile(path.join(__dirname, 'public/empty_search.png'));
  await sharp(Buffer.from(emptySvg)).jpeg({ quality: 95 }).toFile(path.join(__dirname, 'public/empty_search.jpg'));

  const resDir = path.join(__dirname, 'app/src/main/res/drawable');
  if (fs.existsSync(resDir)) {
    fs.copyFileSync(path.join(__dirname, 'public/drawer_header.png'), path.join(resDir, 'drawer_header.png'));
    fs.copyFileSync(path.join(__dirname, 'public/drawer_header.jpg'), path.join(resDir, 'drawer_header.jpg'));
    fs.copyFileSync(path.join(__dirname, 'public/empty_search.png'), path.join(resDir, 'empty_search.png'));
    fs.copyFileSync(path.join(__dirname, 'public/empty_search.jpg'), path.join(resDir, 'empty_search.jpg'));
  }

  console.log('Successfully generated drawer header and empty search assets!');
}

run().catch(console.error);
