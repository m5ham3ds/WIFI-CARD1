const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000; // Dev server must run on port 3000 for Nginx reverse proxy
const HOST = '0.0.0.0';
const APP_DIR = __dirname;
const PUBLIC_DIR = path.join(APP_DIR, 'public');
const APK_CANDIDATES = [
  path.join(APP_DIR, 'app/build/outputs/apk/debug/app-debug.apk'),
  path.join(APP_DIR, 'app/build/outputs/apk/debug/WIFI-CARD.apk'),
  path.join(APP_DIR, '.build-outputs/app-debug.apk')
];
const APK_PATH = APK_CANDIDATES.find(p => fs.existsSync(p)) || APK_CANDIDATES[0];

// In-memory data store matching Android Room Database schema
let testResults = [
  { id: '1', sessionId: '104', cardCode: 'D00102', state: 'Success', durationMs: 42, time: '12:45:10', message: 'تم تسجيل الدخول بنجاح (Code 200)' },
  { id: '2', sessionId: '104', cardCode: 'D00103', state: 'Failed', durationMs: 120, time: '12:45:18', message: 'فشل المصادقة / بطاقة غير صالحة' },
  { id: '3', sessionId: '104', cardCode: 'D00104', state: 'Success', durationMs: 38, time: '12:45:26', message: 'تم التحقق بنجاح (مستعملة بجهازين)' },
  { id: '4', sessionId: '104', cardCode: 'D00105', state: 'Success', durationMs: 45, time: '12:45:34', message: 'تم تسجيل الدخول بنجاح (Code 200)' },
  { id: '5', sessionId: '104', cardCode: 'D00106', state: 'Failed', durationMs: 155, time: '12:45:42', message: 'تم رفض الاتصال من البوابة' },
  { id: '6', sessionId: '104', cardCode: 'D00107', state: 'Success', durationMs: 50, time: '12:45:50', message: 'تم تسجيل الدخول بنجاح (Code 200)' },
  { id: '7', sessionId: '104', cardCode: 'D00108', state: 'Failed', durationMs: 95, time: '12:45:58', message: 'انتهاء وقت الانتظار' }
];

let sessions = [
  { id: '104', date: '2026-10-08 12:45', routerName: 'يلنك (TP-Link)', totalCards: 48, successCount: 38, failureCount: 8, twoDevicesCount: 2, isRunning: false },
  { id: '103', date: '2026-10-08 10:15', routerName: 'Tenda AC1200', totalCards: 30, successCount: 25, failureCount: 4, twoDevicesCount: 1, isRunning: false },
  { id: '102', date: '2026-10-07 18:30', routerName: 'Huawei WS5200', totalCards: 60, successCount: 52, failureCount: 7, twoDevicesCount: 1, isRunning: false },
  { id: '101', date: '2026-10-07 14:00', routerName: 'AlBasha Net Gateway', totalCards: 20, successCount: 16, failureCount: 4, twoDevicesCount: 0, isRunning: false }
];

let routers = [
  { id: 1, name: 'يلنك', ip: 'www.web.com', protocol: 'http', loginPath: '/login', strategy: 'motasem', isDefault: true },
  { id: 2, name: 'Tenda AC1200', ip: '192.168.0.1', protocol: 'http', loginPath: '/login', strategy: 'bello', isDefault: false },
  { id: 3, name: 'Huawei WS5200', ip: '192.168.3.1', protocol: 'http', loginPath: '/login', strategy: 'generic', isDefault: false },
  { id: 4, name: 'MicroTik Cloud Core', ip: '192.168.88.1', protocol: 'http', loginPath: '/login', strategy: 'motasem', isDefault: false }
];

function generateHtml() {
  const apkExists = fs.existsSync(APK_PATH);
  const apkSizeMb = apkExists ? (fs.statSync(APK_PATH).size / (1024 * 1024)).toFixed(1) : '9.3';

  return `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>WIFI-CARD</title>
  <meta name="description" content="Imported from GitHub: m5ham3ds/WIFI-CARD0">
  <meta property="og:title" content="WIFI-CARD">
  <meta property="og:description" content="Imported from GitHub: m5ham3ds/WIFI-CARD0">
  
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
  
  <!-- Font Awesome 6.5.2 -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
  
  <!-- Tailwind CSS -->
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = {
      theme: {
        extend: {
          fontFamily: {
            sans: ['Cairo', 'sans-serif'],
            mono: ['JetBrains Mono', 'monospace'],
          },
          colors: {
            obsidian: '#06070B',
            surfaceDark: '#0E131F',
            surfaceItem: '#070A10',
            surfaceBorder: '#1E283C',
            neonRed: '#FF1E27',
            neonRedDark: '#D50000',
            neonGreen: '#00E676',
            neonBlue: '#2979FF',
            textSec: '#94A3B8',
            textMuted: '#64748B'
          }
        }
      }
    }
  </script>
  
  <style>
    :root {
      --neon-red: #FF1E27;
      --neon-green: #00E676;
      --surface-dark: #0E131F;
      --surface-border: #1E283C;
    }
    body {
      font-family: 'Cairo', sans-serif;
      user-select: none;
      -webkit-font-smoothing: antialiased;
      background-color: #040508;
    }
    .glow-red {
      box-shadow: 0 0 16px -2px rgba(255, 30, 39, 0.45);
    }
    .glow-red-border {
      border-color: #FF1E27 !important;
      box-shadow: 0 0 14px -2px rgba(255, 30, 39, 0.35);
    }
    .glow-green {
      box-shadow: 0 0 14px -2px rgba(0, 230, 118, 0.35);
    }
    .custom-scroll::-webkit-scrollbar {
      width: 4px;
      height: 4px;
    }
    .custom-scroll::-webkit-scrollbar-thumb {
      background: #1E283C;
      border-radius: 4px;
    }
    .pulse-glow {
      animation: pulse-red 2s infinite ease-in-out;
    }
    @keyframes pulse-red {
      0%, 100% { box-shadow: 0 0 12px rgba(255, 30, 39, 0.3); }
      50% { box-shadow: 0 0 24px rgba(255, 30, 39, 0.6); }
    }
  </style>
</head>
<body class="text-white min-h-screen flex flex-col items-center justify-start p-0 md:p-4 selection:bg-red-600 selection:text-white">

  <!-- Mobile Frame Container matching Material 3 phone view -->
  <div class="w-full max-w-md bg-[#06070B] md:rounded-[36px] shadow-2xl overflow-hidden min-h-screen md:min-h-[880px] md:max-h-[920px] flex flex-col border border-[#1E283C] relative">
    
    <!-- Authentic Graphic Header Banner (Standard recommended height 72px) -->
    <header class="relative w-full h-[72px] bg-[#07090E] select-none overflow-hidden shrink-0">
      
      <!-- 1. Home Header (Exact authentic image with ONLY the drawer menu button) -->
      <div id="headerHomeWrapper" class="relative w-full h-full">
        <img src="/header_home.jpg" alt="WiFi Card Master Pro" class="w-full h-full object-cover">
        
        <!-- ONLY Drawer Menu Button placed on top-left (vertically centered in 72px) -->
        <button onclick="toggleDrawer()" class="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-xl bg-black/50 hover:bg-black/75 backdrop-blur-md border border-white/20 flex items-center justify-center text-white active:scale-95 transition shadow-lg z-10" title="القائمة الجانبية">
          <i class="fa-solid fa-bars text-sm"></i>
        </button>
      </div>

      <!-- 2. Sub-pages Header (Exact authentic image with title overlay) -->
      <div id="headerPagesWrapper" class="hidden relative w-full h-full">
        <img src="/header_pages.jpg" alt="Header" class="w-full h-full object-cover">
        
        <!-- Navigation Button (Drawer or Back) on top-left -->
        <button id="headerBackBtn" onclick="onHeaderBackClick()" class="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-xl bg-black/50 hover:bg-black/75 backdrop-blur-md border border-white/20 flex items-center justify-center text-white active:scale-95 transition shadow-lg z-10" title="التنقل">
          <i id="headerBackIcon" class="fa-solid fa-bars text-sm"></i>
        </button>
        
        <!-- Dynamic Page Title Overlay on the right -->
        <div class="absolute right-4 top-1/2 -translate-y-1/2 flex flex-col items-end pointer-events-none text-right">
          <h2 id="headerPageTitle" class="text-base font-black text-white drop-shadow-[0_2px_8px_rgba(255,30,39,0.7)]">فحص الكروت الحية</h2>
          <div class="mt-0.5 px-2 py-0.5 rounded-full bg-[#140306]/90 border border-[#FF1E27]/60">
            <p id="headerPageSubtitle" class="text-[9px] font-bold text-white/90">اختبار تفاعلي على بوابة الهوت سبوت</p>
          </div>
        </div>
      </div>
    </header>

    <!-- App Content Scroll Area -->
    <main id="appMain" class="flex-1 overflow-y-auto custom-scroll p-3.5 space-y-3.5 bg-[#06070B]">

      <!-- ======================================================== -->
      <!-- SCREEN 1: الصفحة الرئيسية (مطبقة 100% كما في الصورة)        -->
      <!-- ======================================================== -->
      <div id="pageHome" class="space-y-3.5">
        
        <!-- 1. Connection Status Card -->
        <div class="bg-[#0E131F] border border-[#00E676]/60 rounded-[18px] p-3.5 flex items-center justify-between shadow-lg">
          <div class="flex items-center gap-3">
            <div class="w-11 h-11 rounded-xl bg-[#00E676]/15 border border-[#00E676]/40 flex items-center justify-center text-[#00E676] text-lg">
              <i class="fa-solid fa-wifi"></i>
            </div>
            <div>
              <h3 class="font-black text-sm text-white">متصل بجهاز الراوتر</h3>
              <p class="text-[11px] font-medium text-[#00E676]">تم الاتصال بنجاح. يمكنك البدء بالاختبار</p>
            </div>
          </div>
          <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
        </div>

        <!-- 2. Target Router Card -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-3">
          <div class="flex items-center justify-end gap-2 text-white">
            <h4 class="font-bold text-xs">اختر ملف الراوتر المستهدف</h4>
            <i class="fa-solid fa-bullseye text-[#FF1E27] text-sm"></i>
          </div>

          <!-- Router Dropdown -->
          <div onclick="openRouterSelectorModal()" class="bg-[#070A10] border border-[#161E2E] rounded-xl px-3.5 py-2.5 flex items-center justify-between cursor-pointer hover:border-[#FF1E27]/50 transition">
            <span class="text-xs text-[#94A3B8]">▾</span>
            <div class="flex items-center gap-2.5">
              <span id="homeRouterName" class="font-bold text-sm text-white">يلنك</span>
              <div class="w-6 h-6 rounded-md bg-[#FF1E27]/20 flex items-center justify-center text-[#FF1E27] text-xs">
                <i class="fa-solid fa-server"></i>
              </div>
            </div>
          </div>

          <!-- IP & Path bar with copy icon -->
          <div class="bg-[#070A10] border border-[#161E2E] rounded-xl px-3 py-2 flex items-center justify-between text-xs font-mono">
            <span id="homeRouterIpPath" class="text-[#CBD5E1] text-[11px]">IP : www.web.com | Path : /login</span>
            <button onclick="copyIpPath()" class="text-[#64748B] hover:text-white" title="نسخ">
              <i class="fa-regular fa-copy text-sm"></i>
            </button>
          </div>
        </div>

        <!-- 3. Hotspot Generation Settings Card -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-3">
          <div class="flex items-center gap-2 text-white pb-1">
            <i class="fa-solid fa-gear text-[#FF1E27] text-sm"></i>
            <h4 class="font-bold text-xs">إعدادات توليد بطاقات الهوت سبوت</h4>
          </div>

          <!-- Prefix Row -->
          <div class="flex items-center justify-between bg-[#070A10] border border-[#161E2E] rounded-xl px-3 py-2">
            <input type="text" id="inputPrefix" value="D" class="bg-transparent text-left font-mono font-bold text-sm text-white w-20 outline-none">
            <div class="flex items-center gap-2 text-[#94A3B8] text-xs">
              <span>بادئة الكرت (Prefix)</span>
              <i class="fa-solid fa-tag text-[#FF1E27] text-xs"></i>
            </div>
          </div>

          <!-- Length Row -->
          <div class="flex items-center justify-between bg-[#070A10] border border-[#161E2E] rounded-xl px-3 py-2">
            <input type="number" id="inputLength" value="6" class="bg-transparent text-left font-mono font-bold text-sm text-white w-20 outline-none">
            <div class="flex items-center gap-2 text-[#94A3B8] text-xs">
              <span>طول الكود (Length)</span>
              <i class="fa-solid fa-hashtag text-[#FF1E27] text-xs"></i>
            </div>
          </div>

          <!-- Charset Row -->
          <div class="flex items-center justify-between bg-[#070A10] border border-[#161E2E] rounded-xl px-3 py-2">
            <input type="text" id="inputCharset" value="0123456789" class="bg-transparent text-left font-mono font-bold text-xs text-white w-28 outline-none">
            <div class="flex items-center gap-2 text-[#94A3B8] text-xs">
              <span>مجموعة أحرف الكود (Charset)</span>
              <i class="fa-solid fa-keyboard text-[#FF1E27] text-xs"></i>
            </div>
          </div>

          <!-- Count Row -->
          <div class="flex items-center justify-between bg-[#070A10] border border-[#161E2E] rounded-xl px-3 py-2">
            <input type="number" id="inputCount" value="50" class="bg-transparent text-left font-mono font-bold text-sm text-white w-20 outline-none">
            <div class="flex items-center gap-2 text-[#94A3B8] text-xs">
              <span>عدد البطاقات المطلوب توليدها (Count)</span>
              <i class="fa-regular fa-clock text-[#FF1E27] text-xs"></i>
            </div>
          </div>
        </div>

        <!-- 4. Metric Statistics Triad -->
        <div class="grid grid-cols-3 gap-2">
          <!-- Failed Cards -->
          <div class="bg-[#0E131F] border border-[#FF1E27]/40 rounded-2xl p-2.5 text-center flex flex-col justify-between">
            <span class="text-[11px] font-bold text-[#FF1E27]">البطاقات الفاشلة</span>
            <div class="text-2xl font-black font-mono text-[#FF1E27] my-1" id="statHomeFailed">8</div>
            <span class="text-[10px] text-[#FF1E27]/80">غير صالحة</span>
          </div>

          <!-- Success Cards -->
          <div class="bg-[#0E131F] border border-[#00E676]/40 rounded-2xl p-2.5 text-center flex flex-col justify-between">
            <span class="text-[11px] font-bold text-[#00E676]">البطاقات الناجحة</span>
            <div class="text-2xl font-black font-mono text-[#00E676] my-1" id="statHomeSuccess">38</div>
            <span class="text-[10px] text-[#00E676]/80" id="statHomeSuccessSub">(36 عادي | 2 جهازين)</span>
          </div>

          <!-- Total Cards -->
          <div class="bg-[#0E131F] border border-[#2979FF]/40 rounded-2xl p-2.5 text-center flex flex-col justify-between">
            <span class="text-[11px] font-bold text-[#2979FF]">إجمالي البطاقات</span>
            <div class="text-2xl font-black font-mono text-[#2979FF] my-1" id="statHomeTotal">48</div>
            <span class="text-[10px] text-[#94A3B8]">تمت تجربتها</span>
          </div>
        </div>

        <!-- 5. Action Controls Row -->
        <div class="grid grid-cols-2 gap-2.5">
          <!-- Stop Test Button -->
          <button onclick="confirmStopTest()" class="bg-[#140306] border border-[#FF1E27] hover:bg-[#20050A] text-[#FF1E27] font-black text-xs py-3.5 px-4 rounded-2xl flex items-center justify-center gap-2 active:scale-95 transition">
            <i class="fa-solid fa-stop text-sm"></i>
            <span>إيقاف الفحص</span>
          </button>

          <!-- Start Test Button -->
          <button onclick="startTestingFromHome()" class="bg-gradient-to-r from-[#FF1E27] to-[#D50000] hover:brightness-110 text-white font-black text-xs py-3.5 px-4 rounded-2xl shadow-lg shadow-red-900/50 flex items-center justify-center gap-2 active:scale-95 transition">
            <i class="fa-solid fa-play text-sm"></i>
            <span>بدء عملية الإختبار</span>
          </button>
        </div>

        <!-- 6. Live Operations Terminal Card -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-3.5 space-y-2">
          <div class="flex items-center justify-between pb-1.5 border-b border-[#1E283C]">
            <div class="flex items-center gap-2">
              <i class="fa-solid fa-tower-broadcast text-[#FF1E27] text-xs"></i>
              <h4 class="font-bold text-xs text-white">سجل العمليات المباشر</h4>
            </div>
            <button onclick="confirmClearTerminal()" class="text-[11px] text-[#64748B] hover:text-white flex items-center gap-1">
              <i class="fa-solid fa-trash-can text-[10px]"></i>
              <span>مسح</span>
            </button>
          </div>
          <div id="homeLogTerminal" class="font-mono text-[11px] text-slate-300 space-y-1.5 max-h-36 overflow-y-auto custom-scroll bg-[#05080E] p-2.5 rounded-xl border border-[#141A28]">
            <div class="text-[#00E676]">[12:45:01] تم الاتصال بالراوتر بنجاح (Gateway: 192.168.1.1)</div>
            <div class="text-[#2979FF]">[12:45:04] تم توليد 50 بطاقة هوت سبوت بالبادئة D</div>
            <div class="text-white">[12:45:10] فحص D00102 -> نجاح (Code 200)</div>
            <div class="text-[#FF1E27]">[12:45:18] فحص D00103 -> فشل المصادقة</div>
            <div class="text-[#FF9100]">[12:45:26] فحص D00104 -> كرت مستعمل بجهازين</div>
          </div>
        </div>

      </div>

      <!-- ======================================================== -->
      <!-- SCREEN 2: صفحة التجربة (مطبقة 100% كما في الصورة)           -->
      <!-- ======================================================== -->
      <div id="pageTest" class="hidden space-y-3.5">
        
        <!-- Top Status Card with Glowing Neon Red Border -->
        <div class="bg-[#0E131F] border-2 border-[#FF1E27] glow-red rounded-[20px] p-4 space-y-3">
          <div class="flex items-center justify-between">
            
            <!-- Left: Document & Magnifier in Red Container -->
            <div class="w-13 h-13 p-3 rounded-2xl bg-[#1F0307] border border-[#FF1E27]/60 flex items-center justify-center text-[#FF1E27] text-2xl">
              <i class="fa-regular fa-file-lines"></i>
            </div>

            <!-- Center: Status Text -->
            <div class="flex-1 px-3 text-right">
              <div class="flex items-center justify-end gap-1.5">
                <span id="testReadyStatus" class="font-black text-sm text-[#00E676]">جاهز</span>
                <span class="font-bold text-sm text-white">حالة الاختيار :</span>
                <i class="fa-solid fa-circle-check text-[#00E676] text-sm"></i>
              </div>
              <p class="text-[10px] text-[#94A3B8] mt-0.5">سيتم فحص البطاقات بشكل مباشر من المتصفح المعزول</p>
            </div>

            <!-- Right: Wi-Fi Speedometer Gauge -->
            <div class="relative w-12 h-12 flex items-center justify-center">
              <svg class="w-12 h-12 -rotate-90" viewBox="0 0 36 36">
                <path class="text-[#1E283C]" stroke-width="3" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
                <path id="gaugePath" class="text-[#FF1E27]" stroke-dasharray="75, 100" stroke-width="3" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
              </svg>
              <i class="fa-solid fa-wifi text-[#FF1E27] text-xs absolute"></i>
            </div>
          </div>

          <!-- Bottom: Progress Track & Info Message -->
          <div class="pt-2 border-t border-[#1E283C]/70 flex items-center justify-between text-[10px]">
            <div class="w-28 bg-[#141A28] rounded-full h-1.5 overflow-hidden">
              <div id="testTrackMarker" class="bg-[#FF1E27] h-full w-[10%]"></div>
            </div>
            <div class="flex items-center gap-1.5 text-[#94A3B8]">
              <span id="testActiveCardInfo">لا توجد بطاقات قيد الفحص حالياً</span>
              <i class="fa-solid fa-circle-info text-xs"></i>
            </div>
          </div>
        </div>

        <!-- WebView Capture View Frame Card (بث الشاشة المباشر للـ WebView - Live Stream View) -->
        <div class="bg-[#04060B] border-2 border-[#FF1E27] glow-red-border rounded-[22px] overflow-hidden flex flex-col min-h-[280px] shadow-2xl">
          <!-- Frame Header Bar -->
          <div class="bg-[#0C101A] border-b border-[#1E283C] px-3.5 py-2.5 flex items-center justify-between">
            <!-- Left: LIVE Pulsing Badge -->
            <div class="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-[#260408] border border-[#FF1E27]/60 text-[10px] font-black text-[#FF1E27]">
              <span class="w-2 h-2 rounded-full bg-[#FF1E27] animate-ping"></span>
              <span>LIVE</span>
            </div>
            <!-- Right: Title -->
            <div class="flex items-center gap-2">
              <span class="text-xs font-bold text-white">بث الشاشة المباشر للـ WebView</span>
              <i class="fa-solid fa-satellite-dish text-[#FF1E27] text-xs"></i>
            </div>
          </div>

          <!-- Live Captive Portal Screen View -->
          <div class="p-3.5 flex-1 flex flex-col justify-between bg-[#030509] space-y-3">
            <!-- Address Bar -->
            <div class="flex items-center justify-between bg-[#0B101C] border border-[#1E283C] rounded-xl px-3 py-1.5 text-[11px] font-mono text-[#94A3B8]">
              <i class="fa-solid fa-lock text-[#00E676] text-xs"></i>
              <span class="text-slate-300">http://192.168.1.1/login</span>
              <i class="fa-solid fa-wifi text-[#FF1E27] text-xs"></i>
            </div>

            <!-- Portal Card Content -->
            <div class="bg-[#070B14] border border-[#FF1E27]/30 rounded-2xl p-4 text-center space-y-2.5 relative overflow-hidden">
              <div class="text-xs font-bold text-white">بوابة تسجيل الدخول إلى الشبكة</div>
              <div class="text-[10px] text-[#64748B]">Hotspot Captive Portal (MikroTik / TP-Link)</div>
              
              <!-- Active Card PIN Input Simulated Field -->
              <div class="bg-[#020408] border-2 border-[#FF1E27] glow-red rounded-xl py-3 px-4 text-center">
                <span id="portalActiveCard" class="text-white font-mono font-black text-xl tracking-widest block">D00102</span>
              </div>

              <!-- Submit button representation -->
              <div id="portalLoginBtn" class="bg-gradient-to-r from-[#FF1E27] to-[#D50000] text-white font-bold text-xs py-2 rounded-xl text-center shadow-lg shadow-red-900/40">
                تسجيل الدخول...
              </div>
            </div>

            <!-- Live Stream Status Text -->
            <div class="flex items-center justify-center gap-2 text-[10px] text-[#94A3B8]">
              <div id="portalLiveDot" class="w-2 h-2 rounded-full bg-[#FF1E27] animate-pulse"></div>
              <span id="portalStreamStatus">في انتظار بدء دورة الفحص...</span>
            </div>
          </div>
        </div>

        <!-- Two Action Buttons -->
        <div class="grid grid-cols-2 gap-2.5 pt-1">
          <!-- Cancel Button -->
          <button onclick="confirmCancelTest()" class="bg-[#140306] border-2 border-[#FF1E27]/70 hover:bg-[#220409] text-[#FF1E27] font-black text-xs py-3.5 px-4 rounded-full flex items-center justify-center gap-2 active:scale-95 transition">
            <span>إلغاء وفصل</span>
            <i class="fa-solid fa-circle-xmark text-sm"></i>
          </button>

          <!-- Pause / Resume Button -->
          <button id="btnTestTogglePause" onclick="toggleTestPauseAction()" class="bg-gradient-to-r from-[#FF1E27] to-[#D50000] hover:brightness-110 text-white font-black text-xs py-3.5 px-4 rounded-full shadow-lg shadow-red-900/50 flex items-center justify-center gap-2 active:scale-95 transition">
            <span id="btnTestPauseText">إيقاف مؤقت</span>
            <i id="btnTestPauseIcon" class="fa-solid fa-circle-stop text-sm"></i>
          </button>
        </div>

      </div>

      <!-- ======================================================== -->
      <!-- SCREEN 3: صفحة السجلات (مطبقة 100% كما في الصورة)           -->
      <!-- ======================================================== -->
      <div id="pageHistory" class="hidden space-y-3.5">
        
        <!-- Search & Filter Row -->
        <div class="flex items-center gap-2">
          <!-- Calendar Filter Button -->
          <button class="bg-[#0E131F] border border-[#1E283C] rounded-xl px-2.5 py-2 text-[11px] font-bold text-white flex items-center gap-1.5 shrink-0">
            <span>الكل</span>
            <i class="fa-regular fa-calendar text-[#FF1E27] text-xs"></i>
          </button>

          <!-- Router Filter Button -->
          <button class="bg-[#0E131F] border border-[#1E283C] rounded-xl px-2.5 py-2 text-[11px] font-bold text-white flex items-center gap-1.5 shrink-0">
            <span>الكل</span>
            <i class="fa-solid fa-server text-[#FF1E27] text-xs"></i>
          </button>

          <!-- Search Input -->
          <div class="flex-1 relative">
            <input type="text" id="historySearchInput" oninput="filterHistoryList()" placeholder="بحث في السجلات..." class="w-full bg-[#0E131F] border border-[#1E283C] rounded-xl px-3 py-2 text-xs text-right text-white placeholder-[#64748B] outline-none focus:border-[#FF1E27]">
            <i class="fa-solid fa-magnifying-glass text-[#64748B] absolute left-3 top-2.5 text-xs"></i>
          </div>
        </div>

        <!-- 4 Stat Cards Row -->
        <div class="grid grid-cols-4 gap-1.5">
          <!-- Failed -->
          <div class="bg-[#0E131F] border border-[#FF1E27]/30 rounded-xl p-2 text-center">
            <span class="text-[9px] font-bold text-[#FF1E27] block">الفاشلة</span>
            <span class="text-sm font-black font-mono text-[#FF1E27] block my-0.5" id="histStatFailed">19</span>
            <i class="fa-regular fa-circle-xmark text-[#FF1E27] text-[10px]"></i>
          </div>

          <!-- Success -->
          <div class="bg-[#0E131F] border border-[#00E676]/30 rounded-xl p-2 text-center">
            <span class="text-[9px] font-bold text-[#00E676] block">الناجحة</span>
            <span class="text-sm font-black font-mono text-[#00E676] block my-0.5" id="histStatSuccess">131</span>
            <i class="fa-regular fa-circle-check text-[#00E676] text-[10px]"></i>
          </div>

          <!-- Total Sessions -->
          <div class="bg-[#0E131F] border border-[#1E283C] rounded-xl p-2 text-center">
            <span class="text-[9px] font-bold text-white block">إجمالي السجلات</span>
            <span class="text-sm font-black font-mono text-white block my-0.5" id="histStatSessions">4</span>
            <i class="fa-regular fa-clock text-[#94A3B8] text-[10px]"></i>
          </div>

          <!-- Last Operation -->
          <div class="bg-[#0E131F] border border-[#1E283C] rounded-xl p-2 text-center">
            <span class="text-[9px] font-bold text-white block">آخر عملية</span>
            <span class="text-[10px] font-bold text-white block my-1">اليوم</span>
            <i class="fa-regular fa-clock text-[#94A3B8] text-[10px]"></i>
          </div>
        </div>

        <!-- Sessions List Feed -->
        <div id="sessionsListFeed" class="space-y-2.5">
          <!-- Populated dynamically -->
        </div>

      </div>

      <!-- ======================================================== -->
      <!-- SCREEN 4: تفاصيل الجلسة (مطبقة 100% كما في الصورة)          -->
      <!-- ======================================================== -->
      <div id="pageSessionDetail" class="hidden space-y-3.5">
        
        <!-- Top Session Summary Card with Red Neon Glowing Border -->
        <div class="bg-[#0E131F] border-2 border-[#FF1E27] glow-red rounded-[20px] p-4 space-y-3">
          
          <div class="flex items-center justify-between">
            <!-- Left: Status Pill and Menu -->
            <div class="flex items-center gap-2">
              <span class="text-[#64748B] text-base font-bold cursor-pointer">⋮</span>
              <div class="flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#00E676]/15 border border-[#00E676]/40 text-[#00E676] text-[11px] font-bold">
                <span>مكتملة</span>
                <i class="fa-solid fa-circle-check text-xs"></i>
              </div>
            </div>

            <!-- Right: Router Info -->
            <div class="flex items-center gap-2.5 text-right">
              <div>
                <div class="flex items-center justify-end gap-1.5">
                  <span class="font-black text-sm text-white" id="sessDetailId">Session #104</span>
                  <span class="w-1.5 h-1.5 rounded-full bg-[#FF1E27]"></span>
                </div>
                <div class="flex items-center justify-end gap-2 text-[10px] font-mono text-[#64748B] mt-0.5">
                  <span id="sessDetailDate">2026-10-08 12:45</span>
                  <span class="text-[#FF1E27] font-bold" id="sessDetailRouter">🌐 يلنك</span>
                </div>
              </div>
              <div class="w-10 h-10 rounded-xl bg-[#1C0306] border border-[#FF1E27]/50 flex items-center justify-center text-[#FF1E27] text-base">
                <i class="fa-solid fa-server"></i>
              </div>
            </div>
          </div>

          <!-- Stat Counters Triad inside Card -->
          <div class="grid grid-cols-3 gap-2 pt-2 border-t border-[#1E283C]/70">
            <!-- Total -->
            <div class="bg-[#070A12] border border-[#1E283C] rounded-xl py-2 text-center">
              <div class="flex items-center justify-center gap-1 text-white font-mono font-black text-sm">
                <i class="fa-solid fa-layer-group text-xs text-[#94A3B8]"></i>
                <span id="sessDetailTotal">48</span>
              </div>
              <span class="text-[9px] text-[#64748B]">إجمالي الكل</span>
            </div>

            <!-- Success -->
            <div class="bg-[#070A12] border border-[#00E676]/30 rounded-xl py-2 text-center">
              <div class="flex items-center justify-center gap-1 text-[#00E676] font-mono font-black text-sm">
                <i class="fa-solid fa-circle-check text-xs"></i>
                <span id="sessDetailSuccess">38</span>
              </div>
              <span class="text-[9px] text-[#00E676]/80">الناجحة</span>
            </div>

            <!-- Failed -->
            <div class="bg-[#070A12] border border-[#FF1E27]/30 rounded-xl py-2 text-center">
              <div class="flex items-center justify-center gap-1 text-[#FF1E27] font-mono font-black text-sm">
                <i class="fa-solid fa-circle-xmark text-xs"></i>
                <span id="sessDetailFailed">8</span>
              </div>
              <span class="text-[9px] text-[#FF1E27]/80">الفاشلة</span>
            </div>
          </div>
        </div>

        <!-- Action Bar: Export Button (Left) & Filter Header (Right) -->
        <div class="flex items-center justify-between">
          <button onclick="exportCurrentSession()" class="bg-[#FF1E27] hover:bg-[#D50000] text-white text-xs font-bold px-4 py-2 rounded-xl flex items-center gap-1.5 shadow-md shadow-red-900/40 active:scale-95 transition">
            <i class="fa-solid fa-cloud-arrow-down text-xs"></i>
            <span>تصدير النتائج</span>
          </button>

          <div class="flex items-center gap-1.5 text-white font-bold text-xs">
            <span>عرض النتائج</span>
            <i class="fa-solid fa-filter text-[#FF1E27]"></i>
          </div>
        </div>

        <!-- Filter Chips Row -->
        <div class="grid grid-cols-4 gap-1.5 text-center text-xs">
          <button onclick="filterDetailResults('all')" id="chipDetailAll" class="bg-[#FF1E27] text-white font-bold py-2 rounded-xl transition">الكل</button>
          <button onclick="filterDetailResults('success')" id="chipDetailSuccess" class="bg-[#0E131F] border border-[#1E283C] text-[#94A3B8] font-bold py-2 rounded-xl transition">الناجحة فقط</button>
          <button onclick="filterDetailResults('failed')" id="chipDetailFailed" class="bg-[#0E131F] border border-[#1E283C] text-[#94A3B8] font-bold py-2 rounded-xl transition">الفاشلة فقط</button>
          <button onclick="filterDetailResults('two_devices')" id="chipDetailTwoDev" class="bg-[#0E131F] border border-[#1E283C] text-[#94A3B8] font-bold py-2 rounded-xl transition">مستعملة بجهازين</button>
        </div>

        <!-- Search & Action Row -->
        <div class="flex items-center gap-2">
          <button class="w-9 h-9 rounded-xl bg-[#0E131F] border border-[#1E283C] flex items-center justify-center text-[#64748B]">
            <i class="fa-solid fa-calendar text-xs"></i>
          </button>
          <button class="w-9 h-9 rounded-xl bg-[#0E131F] border border-[#1E283C] flex items-center justify-center text-[#64748B]">
            <i class="fa-solid fa-arrow-down-wide-short text-xs"></i>
          </button>
          <div class="flex-1 relative">
            <input type="text" id="detailSearchInput" oninput="filterDetailCards()" placeholder="البحث في نتائج هذه الجلسة..." class="w-full bg-[#0E131F] border border-[#1E283C] rounded-xl px-3 py-2 text-xs text-right text-white placeholder-[#64748B] outline-none focus:border-[#FF1E27]">
            <i class="fa-solid fa-magnifying-glass text-[#64748B] absolute left-3 top-2.5 text-xs"></i>
          </div>
        </div>

        <!-- Results List Feed -->
        <div id="detailCardsFeed" class="space-y-2">
          <!-- Populated dynamically -->
        </div>

        <!-- Bottom Info Tip Card -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-xl p-3 flex items-start gap-2 text-right">
          <p class="text-[10px] text-[#94A3B8] leading-4 flex-1">
            البطاقات الناجحة تمنحك إمكانية الولوج لشبكة الإنترنت على بوابة هذا الراوتر بشكل فوري، احرص على حفظها وتصديرها.
          </p>
          <i class="fa-solid fa-circle-info text-[#FF1E27] text-xs mt-0.5"></i>
        </div>

      </div>

      <!-- ======================================================== -->
      <!-- SCREEN 5: الإعدادات (مطبقة 100% كما في الصورة)              -->
      <!-- ======================================================== -->
      <div id="pageSettings" class="hidden space-y-3.5">
        
        <!-- 1. المظهر والتصميم -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-3">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">المظهر والتصميم</h4>
            <i class="fa-solid fa-palette text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 cursor-pointer" onclick="openThemeDialog()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div class="text-right">
              <h5 class="text-xs font-bold text-white">اختيار السمة</h5>
              <p class="text-[10px] text-[#94A3B8]" id="settingsThemeSummary">الوضع الافتراضي للنظام</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 cursor-pointer" onclick="openLanguageDialog()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div class="text-right">
              <h5 class="text-xs font-bold text-white">لغة التطبيق</h5>
              <p class="text-[10px] text-[#94A3B8]" id="settingsLangSummary">العربية (الافتراضية)</p>
            </div>
          </div>
        </div>

        <!-- 2. لون التطبيق -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-3">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">لون التطبيق</h4>
            <i class="fa-solid fa-palette text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 cursor-pointer" onclick="openColorPickerDialog()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div class="flex items-center gap-3">
              <div class="text-right">
                <h5 class="text-xs font-bold text-white">اللون الرئيسي للتطبيق</h5>
                <p class="text-[10px] text-[#94A3B8]" id="settingsColorSummary">أحمر</p>
              </div>
              <div class="w-8 h-8 rounded-xl bg-[#161E2E] flex items-center justify-center">
                <div class="w-5 h-5 rounded-full bg-[#FF1E27]" id="settingsColorDot"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. الإشعارات والتنبيهات -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-3">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">الإشعارات والتنبيهات</h4>
            <i class="fa-solid fa-bell text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1">
            <button onclick="toggleSwitch(this)" class="w-10 h-5 bg-[#FF1E27] rounded-full p-0.5 transition-colors relative">
              <div class="w-4 h-4 bg-white rounded-full transition-transform translate-x-5"></div>
            </button>
            <span class="text-xs font-bold text-white text-right">الاهتزاز عند الحصول على بطاقة صالحة</span>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1">
            <button onclick="toggleSwitch(this)" class="w-10 h-5 bg-[#FF1E27] rounded-full p-0.5 transition-colors relative">
              <div class="w-4 h-4 bg-white rounded-full transition-transform translate-x-5"></div>
            </button>
            <span class="text-xs font-bold text-white text-right">تشغيل صوت للتنبيه</span>
          </div>
        </div>

        <!-- 4. إعدادات سرعة الفحص والانتظار -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-2.5">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">إعدادات سرعة الفحص والانتظار</h4>
            <i class="fa-solid fa-stopwatch text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="openDelayDialog('page_load')">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">مدة انتظار تحميل صفحة الدخول</h5>
              <p class="text-[10px] text-[#94A3B8]" id="settingsPageLoadSummary">2 ثانية (الافتراضي للتحميل)</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="openDelayDialog('card_test')">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">مدة انتظار نتيجة البطاقة</h5>
              <p class="text-[10px] text-[#94A3B8]" id="settingsCardTestSummary">3 ثانية (الافتراضي للنتيجة)</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="openDelayDialog('screenshot')">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">سرعة تحديث البث المباشر</h5>
              <p class="text-[10px] text-[#94A3B8]" id="settingsScreenshotSummary">2 ثانية (الافتراضي)</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="confirmResetDelays()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">إرجاع أوقات الانتظار للوضع الافتراضي</h5>
              <p class="text-[10px] text-[#94A3B8]">إرجاع وقت تحميل للصفحة لـ 2 ثانية، ووقت الانتظار للنتيجة لـ 3 ثانية</p>
            </div>
          </div>
        </div>

        <!-- 5. إعدادات الاختبار والخلفية -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-2.5">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">إعدادات الاختبار والخلفية</h4>
            <i class="fa-solid fa-sliders text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 text-right">
            <button onclick="toggleSwitch(this)" class="w-10 h-5 bg-[#FF1E27] rounded-full p-0.5 transition-colors relative">
              <div class="w-4 h-4 bg-white rounded-full transition-transform translate-x-5"></div>
            </button>
            <div>
              <h5 class="text-xs font-bold text-white">تفعيل التحميل في الخلفية</h5>
              <p class="text-[10px] text-[#94A3B8]">تحميل الصفحات مسبقاً لتسريع عملية الفحص</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="openDelayDialog('threads')">
            <span class="text-xs font-mono font-bold text-[#FF1E27]" id="settingsThreadsCount">5</span>
            <div>
              <h5 class="text-xs font-bold text-white">عدد الصفحات المحملة بالخلفية</h5>
            </div>
          </div>
        </div>

        <!-- 6. إدارة البيانات -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-2.5">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">إدارة البيانات</h4>
            <i class="fa-solid fa-server text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="openRouterSelectorModal()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">إعداد ملفات الراوتر</h5>
              <p class="text-[10px] text-[#94A3B8]">إضافة وتعديل وحذف ملفات الراوتر</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="confirmClearHistory()">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-[#FF1E27]">مسح سجل الجلسات بالكامل</h5>
              <p class="text-[10px] text-[#94A3B8]">حذف جميع بيانات الاختبار السابقة نهائياً</p>
            </div>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer" onclick="showToast('تم حفظ ملف النسخة الاحتياطية JSON بنجاح')">
            <i class="fa-solid fa-chevron-left text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">تصدير نسخة احتياطية من السجلات</h5>
              <p class="text-[10px] text-[#94A3B8]">حفظ السجلات بصيغة JSON</p>
            </div>
          </div>
        </div>

        <!-- 7. حول -->
        <div class="bg-[#0E131F] border border-[#1E283C] rounded-[18px] p-4 space-y-2.5">
          <div class="flex items-center justify-end gap-2 text-[#FF1E27]">
            <h4 class="font-bold text-xs">حول</h4>
            <i class="fa-solid fa-circle-info text-sm"></i>
          </div>

          <div class="flex items-center justify-between py-1 text-right">
            <span class="text-xs font-mono font-bold text-[#00E676]">1.0.0-Stable</span>
            <h5 class="text-xs font-bold text-white">رقم الإصدار الحالي</h5>
          </div>
          <div class="border-t border-[#141A28]"></div>

          <div class="flex items-center justify-between py-1 text-right cursor-pointer">
            <i class="fa-solid fa-arrow-up-right-from-square text-xs text-[#64748B]"></i>
            <div>
              <h5 class="text-xs font-bold text-white">مستودع الكود على جيت هاب</h5>
              <p class="text-[10px] text-[#94A3B8] font-mono">msalah564s/wifi-master-pro</p>
            </div>
          </div>
        </div>

      </div>

    </main>

    <!-- Bottom Navigation Bar (Matching Android BottomNavigationView) -->
    <nav class="bg-[#0A0D16] border-t border-[#1E283C] px-6 py-2.5 flex justify-between items-center relative z-20">
      <button onclick="navigateTo('home')" id="navHome" class="flex flex-col items-center gap-1 text-[#FF1E27] transition">
        <i class="fa-solid fa-house text-base"></i>
        <span class="text-[10px] font-bold">الرئيسية</span>
      </button>

      <button onclick="navigateTo('test')" id="navTest" class="flex flex-col items-center gap-1 text-[#64748B] hover:text-white transition">
        <i class="fa-solid fa-flask-vial text-base"></i>
        <span class="text-[10px] font-bold">الفحص</span>
      </button>

      <button onclick="navigateTo('history')" id="navHistory" class="flex flex-col items-center gap-1 text-[#64748B] hover:text-white transition">
        <i class="fa-solid fa-clock-rotate-left text-base"></i>
        <span class="text-[10px] font-bold">السجلات</span>
      </button>

      <button onclick="navigateTo('settings')" id="navSettings" class="flex flex-col items-center gap-1 text-[#64748B] hover:text-white transition">
        <i class="fa-solid fa-gear text-base"></i>
        <span class="text-[10px] font-bold">الإعدادات</span>
      </button>
    </nav>

    <!-- Slide-Out Navigation Drawer matching 'شكل القائمة الجانبية.jpg' -->
    <div id="drawerOverlay" onclick="toggleDrawer()" class="hidden fixed inset-0 bg-black/70 backdrop-blur-sm z-40 transition-opacity"></div>
    <div id="navDrawer" class="fixed top-0 bottom-0 left-0 w-80 max-w-[85vw] bg-[#07090F] border-r border-[#1E283C] z-50 transform -translate-x-full transition-transform duration-300 flex flex-col shadow-2xl">
      
      <!-- Drawer Header with Authentic Banner Image -->
      <div class="relative w-full h-44 bg-black select-none border-b border-[#1E283C]">
        <img src="/drawer_header.jpg" alt="Drawer Header" class="w-full h-full object-cover">
      </div>

      <!-- Navigation Links -->
      <div class="flex-1 py-3 px-3 space-y-2 overflow-y-auto custom-scroll">
        <!-- 1. الرئيسية -->
        <button onclick="navigateTo('home'); toggleDrawer()" id="drawerHome" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right bg-[#140306] border border-[#FF1E27] text-white">
          <i class="fa-solid fa-chevron-left text-[10px] text-[#FF1E27]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">الرئيسية</h5>
              <p class="text-[10px] text-[#94A3B8]">الصفحة الرئيسية للتطبيق</p>
            </div>
            <i class="fa-solid fa-house text-[#FF1E27] text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- 2. الاختبار -->
        <button onclick="navigateTo('test'); toggleDrawer()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#0E131F] text-[#94A3B8] hover:text-white transition">
          <i class="fa-solid fa-chevron-left text-[10px]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">الاختيار</h5>
              <p class="text-[10px] text-[#64748B]">فحص بطاقات الهوت سبوت</p>
            </div>
            <i class="fa-solid fa-flask-vial text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- 3. السجلات -->
        <button onclick="navigateTo('history'); toggleDrawer()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#0E131F] text-[#94A3B8] hover:text-white transition">
          <i class="fa-solid fa-chevron-left text-[10px]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">السجلات</h5>
              <p class="text-[10px] text-[#64748B]">عرض نتائج الفحص السابقة</p>
            </div>
            <i class="fa-solid fa-clock-rotate-left text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- 4. إدارة أجهزة الراوتر -->
        <button onclick="openRouterSelectorModal(); toggleDrawer()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#0E131F] text-[#94A3B8] hover:text-white transition">
          <i class="fa-solid fa-chevron-left text-[10px]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">إدارة أجهزة الراوتر</h5>
              <p class="text-[10px] text-[#64748B]">إضافة وإدارة الراوترات</p>
            </div>
            <i class="fa-solid fa-server text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- 5. الإعدادات -->
        <button onclick="navigateTo('settings'); toggleDrawer()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#0E131F] text-[#94A3B8] hover:text-white transition">
          <i class="fa-solid fa-chevron-left text-[10px]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">الإعدادات</h5>
              <p class="text-[10px] text-[#64748B]">تخصيص التطبيق</p>
            </div>
            <i class="fa-solid fa-gear text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- خيارات إضافية -->
        <div class="pt-2 text-right pr-2">
          <span class="text-[10px] font-bold text-[#64748B]">خيارات إضافية</span>
        </div>

        <!-- 6. حول التطبيق -->
        <button onclick="openAboutDialog()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#0E131F] text-[#94A3B8] hover:text-white transition">
          <i class="fa-solid fa-chevron-left text-[10px]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">حول التطبيق</h5>
              <p class="text-[10px] text-[#64748B]">معلومات الإصدار والتراخيص</p>
            </div>
            <i class="fa-solid fa-circle-info text-base w-6 text-center"></i>
          </div>
        </button>

        <!-- 7. الخروج -->
        <button onclick="confirmExitApp()" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-right hover:bg-[#1C0407] text-[#FF1E27] transition">
          <i class="fa-solid fa-chevron-left text-[10px] text-[#FF1E27]"></i>
          <div class="flex items-center gap-3">
            <div>
              <h5 class="text-xs font-bold">الخروج</h5>
              <p class="text-[10px] text-[#FF1E27]/70">إغلاق التطبيق</p>
            </div>
            <i class="fa-solid fa-arrow-right-from-bracket text-[#FF1E27] text-base w-6 text-center"></i>
          </div>
        </button>
      </div>

      <!-- Drawer Footer with Tower Glow and Version -->
      <div class="p-4 border-t border-[#1E283C] flex items-center justify-between bg-gradient-to-t from-[#140205] to-transparent">
        <span class="text-[10px] font-bold font-mono text-[#00E676] bg-[#0F1522] border border-[#1E283C] px-2.5 py-1 rounded-full">v1.0.0-Stable</span>
        <i class="fa-solid fa-tower-broadcast text-[#FF1E27]/40 text-2xl"></i>
      </div>
    </div>

    <!-- Router Selector Modal -->
    <div id="routerModalOverlay" onclick="closeRouterModal()" class="hidden fixed inset-0 bg-black/80 backdrop-blur-md z-50 transition-opacity"></div>
    <div id="routerModal" class="hidden fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-84 max-w-[92vw] bg-[#0E131F] border border-[#1E283C] rounded-[24px] p-5 z-50 shadow-2xl space-y-4">
      <div class="flex items-center justify-between border-b border-[#161E2E] pb-3">
        <button onclick="closeRouterModal()" class="text-[#64748B] hover:text-white text-sm">
          <i class="fa-solid fa-xmark"></i>
        </button>
        <div class="flex items-center gap-2 text-right">
          <h4 class="text-sm font-bold text-white">اختيار جهاز الراوتر</h4>
          <div class="w-8 h-8 rounded-xl bg-[#26080B] border border-[#FF1E27]/40 flex items-center justify-center text-[#FF1E27] text-xs">
            <i class="fa-solid fa-server"></i>
          </div>
        </div>
      </div>
      <div id="routerListContainer" class="space-y-2 max-h-64 overflow-y-auto pr-1">
        <!-- Populated dynamically -->
      </div>
      <button onclick="closeRouterModal()" class="w-full py-2.5 rounded-xl bg-[#161E2E] border border-[#1E283C] text-xs font-bold text-[#94A3B8] hover:text-white transition">
        إلغاء
      </button>
    </div>

    <!-- Global Confirmation / Alert Modal -->
    <div id="confirmModalOverlay" onclick="closeConfirmModal()" class="hidden fixed inset-0 bg-black/80 backdrop-blur-md z-50 transition-opacity"></div>
    <div id="confirmModal" class="hidden fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-84 max-w-[92vw] bg-[#0E131F] border border-[#1E283C] rounded-[24px] p-5 z-50 shadow-2xl text-center space-y-4">
      <div id="confirmModalBadge" class="w-16 h-16 rounded-[20px] bg-[#26080B] border-1.5 border-[#FF1E27] flex items-center justify-center mx-auto shadow-lg shadow-red-950/40">
        <i id="confirmModalIcon" class="fa-solid fa-triangle-exclamation text-2xl text-[#FF1E27]"></i>
      </div>
      <div>
        <h4 id="confirmModalTitle" class="text-base font-bold text-white mb-1.5">عنوان الحوار</h4>
        <p id="confirmModalMessage" class="text-xs text-[#94A3B8] leading-relaxed">رسالة التوضيح والتأكيد</p>
      </div>
      <div class="border-t border-[#161E2E] pt-3 flex items-center gap-2.5">
        <button id="confirmModalCancelBtn" onclick="closeConfirmModal()" class="flex-1 py-2.5 rounded-xl bg-[#161E2E] border border-[#1E283C] text-xs font-bold text-[#94A3B8] hover:text-white transition">
          إلغاء
        </button>
        <button id="confirmModalActionBtn" class="flex-1 py-2.5 rounded-xl bg-gradient-to-r from-[#FF1E27] to-[#D50000] text-xs font-bold text-white shadow-lg shadow-red-950/50 hover:brightness-110 active:scale-95 transition">
          تأكيد
        </button>
      </div>
    </div>

    <!-- Global Selection List Modal -->
    <div id="selectionModalOverlay" onclick="closeSelectionModal()" class="hidden fixed inset-0 bg-black/80 backdrop-blur-md z-50 transition-opacity"></div>
    <div id="selectionModal" class="hidden fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-84 max-w-[92vw] bg-[#0E131F] border border-[#1E283C] rounded-[24px] p-5 z-50 shadow-2xl space-y-4">
      <div class="flex items-center justify-between border-b border-[#161E2E] pb-3">
        <button onclick="closeSelectionModal()" class="text-[#64748B] hover:text-white text-sm">
          <i class="fa-solid fa-xmark"></i>
        </button>
        <div class="flex items-center gap-2 text-right">
          <h4 id="selectionModalTitle" class="text-sm font-bold text-white">اختيار الخيار</h4>
          <div class="w-8 h-8 rounded-xl bg-[#26080B] border border-[#FF1E27]/40 flex items-center justify-center text-[#FF1E27] text-xs">
            <i id="selectionModalHeaderIcon" class="fa-solid fa-sliders"></i>
          </div>
        </div>
      </div>
      <div id="selectionModalOptions" class="space-y-2 max-h-64 overflow-y-auto pr-1">
        <!-- Dynamically rendered -->
      </div>
      <button onclick="closeSelectionModal()" class="w-full py-2.5 rounded-xl bg-[#161E2E] border border-[#1E283C] text-xs font-bold text-[#94A3B8] hover:text-white transition">
        إلغاء
      </button>
    </div>

      <!-- Color Picker Modal -->
    <div id="colorPickerModalOverlay" onclick="closeColorPickerModal()" class="hidden fixed inset-0 bg-black/80 backdrop-blur-md z-50 transition-opacity"></div>
    <div id="colorPickerModal" class="hidden fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-84 max-w-[92vw] bg-[#0E131F] border border-[#1E283C] rounded-[24px] p-5 z-50 shadow-2xl space-y-3.5">
      <div class="flex items-center justify-between border-b border-[#161E2E] pb-3">
        <button onclick="closeColorPickerModal()" class="text-[#64748B] hover:text-white text-sm">
          <i class="fa-solid fa-xmark"></i>
        </button>
        <div class="flex items-center gap-2 text-right">
          <h4 class="text-sm font-bold text-white">اللون الرئيسي للتطبيق</h4>
          <div class="w-8 h-8 rounded-xl bg-[#26080B] border border-[#FF1E27]/40 flex items-center justify-center text-[#FF1E27] text-xs">
            <i class="fa-solid fa-palette"></i>
          </div>
        </div>
      </div>

      <!-- Preview Box -->
      <div class="bg-[#090E18] border border-[#1E283C] rounded-2xl p-3 text-center space-y-2">
        <span class="text-xs font-bold text-white block" id="colorPickerPreviewName">اللون المحدد: أحمر (الافتراضي)</span>
        <button id="colorPickerPreviewBtn" class="bg-[#FF1E27] text-white text-[11px] font-bold px-4 py-1.5 rounded-xl pointer-events-none shadow-md">
          معاينة الزر الرئيسي
        </button>
      </div>

      <div class="space-y-2">
        <div onclick="selectAppColor('red', '#FF1E27', 'أحمر')" id="colorCardRed" class="flex items-center justify-between p-3 rounded-xl bg-[#24080B] border border-[#FF1E27] cursor-pointer transition">
          <i class="fa-solid fa-circle-check text-xs text-[#00E676] color-check" id="checkColorRed"></i>
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-white">أحمر (الافتراضي)</span>
            <div class="w-5 h-5 rounded-full bg-[#FF1E27] shadow-sm"></div>
          </div>
        </div>
        <div onclick="selectAppColor('blue', '#2979FF', 'أزرق')" id="colorCardBlue" class="flex items-center justify-between p-3 rounded-xl bg-[#070A10] border border-[#161E2E] hover:border-[#2979FF] cursor-pointer transition">
          <i class="fa-solid fa-circle-check text-xs text-[#00E676] hidden color-check" id="checkColorBlue"></i>
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-white">أزرق</span>
            <div class="w-5 h-5 rounded-full bg-[#2979FF] shadow-sm"></div>
          </div>
        </div>
        <div onclick="selectAppColor('purple', '#9C27B0', 'بنفسجي')" id="colorCardPurple" class="flex items-center justify-between p-3 rounded-xl bg-[#070A10] border border-[#161E2E] hover:border-[#9C27B0] cursor-pointer transition">
          <i class="fa-solid fa-circle-check text-xs text-[#00E676] hidden color-check" id="checkColorPurple"></i>
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-white">بنفسجي</span>
            <div class="w-5 h-5 rounded-full bg-[#9C27B0] shadow-sm"></div>
          </div>
        </div>
        <div onclick="selectAppColor('green', '#00E676', 'أخضر')" id="colorCardGreen" class="flex items-center justify-between p-3 rounded-xl bg-[#070A10] border border-[#161E2E] hover:border-[#00E676] cursor-pointer transition">
          <i class="fa-solid fa-circle-check text-xs text-[#00E676] hidden color-check" id="checkColorGreen"></i>
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-white">أخضر</span>
            <div class="w-5 h-5 rounded-full bg-[#00E676] shadow-sm"></div>
          </div>
        </div>
        <div onclick="selectAppColor('yellow', '#FFD600', 'أصفر')" id="colorCardYellow" class="flex items-center justify-between p-3 rounded-xl bg-[#070A10] border border-[#161E2E] hover:border-[#FFD600] cursor-pointer transition">
          <i class="fa-solid fa-circle-check text-xs text-[#00E676] hidden color-check" id="checkColorYellow"></i>
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-white">أصفر</span>
            <div class="w-5 h-5 rounded-full bg-[#FFD600] shadow-sm"></div>
          </div>
        </div>
      </div>
      <button onclick="closeColorPickerModal()" class="w-full py-2.5 rounded-xl bg-[#161E2E] border border-[#1E283C] text-xs font-bold text-[#94A3B8] hover:text-white transition">
        إغلاق
      </button>
    </div>

  </div>

  <!-- Toast Notification Popup -->
  <div id="toastNotification" class="fixed top-5 left-1/2 -translate-x-1/2 bg-[#0E131F] border border-[#FF1E27] text-white text-xs px-4 py-2.5 rounded-2xl shadow-2xl z-50 transition-opacity duration-300 opacity-0 pointer-events-none flex items-center gap-2">
    <span id="toastIcon" class="text-[#00E676] font-bold">✓</span>
    <span id="toastText">تم بنجاح</span>
  </div>

  <!-- Client-side Logic Script -->
  <script>
    let currentTab = 'home';
    let testActive = false;
    let testPaused = false;
    let testInterval = null;
    let activeCardIndex = 0;
    let activeCardsList = [];

    let mockSessions = ${JSON.stringify(sessions)};
    let mockResults = ${JSON.stringify(testResults)};
    let availableRouters = ${JSON.stringify(routers)};
    let currentRouter = availableRouters[0];
    let activeDetailFilter = 'all';
    let currentDetailSession = mockSessions[0];

    // Navigation function
    function navigateTo(tab) {
      currentTab = tab;
      const pages = ['pageHome', 'pageTest', 'pageHistory', 'pageSessionDetail', 'pageSettings'];
      pages.forEach(p => {
        const el = document.getElementById(p);
        if (el) el.classList.add('hidden');
      });

      // Bottom Nav active state
      ['navHome', 'navTest', 'navHistory', 'navSettings'].forEach(n => {
        const btn = document.getElementById(n);
        if (btn) {
          btn.classList.remove('text-[#FF1E27]');
          btn.classList.add('text-[#64748B]');
        }
      });

      const headerHome = document.getElementById('headerHomeWrapper');
      const headerPages = document.getElementById('headerPagesWrapper');
      const pageTitle = document.getElementById('headerPageTitle');
      const pageSubtitle = document.getElementById('headerPageSubtitle');
      const headerBackIcon = document.getElementById('headerBackIcon');

      if (tab === 'home') {
        document.getElementById('pageHome').classList.remove('hidden');
        document.getElementById('navHome').classList.add('text-[#FF1E27]');
        headerHome.classList.remove('hidden');
        headerPages.classList.add('hidden');
      } else if (tab === 'test') {
        document.getElementById('pageTest').classList.remove('hidden');
        document.getElementById('navTest').classList.add('text-[#FF1E27]');
        headerHome.classList.add('hidden');
        headerPages.classList.remove('hidden');
        pageTitle.textContent = 'فحص الكروت الحية';
        pageSubtitle.textContent = 'اختبار تفاعلي على بوابة الهوت سبوت';
        headerBackIcon.className = 'fa-solid fa-bars text-base';
      } else if (tab === 'history') {
        document.getElementById('pageHistory').classList.remove('hidden');
        document.getElementById('navHistory').classList.add('text-[#FF1E27]');
        headerHome.classList.add('hidden');
        headerPages.classList.remove('hidden');
        pageTitle.textContent = 'سجل التجارب';
        pageSubtitle.textContent = 'عرض نتائج وجلسات الفحص السابقة';
        headerBackIcon.className = 'fa-solid fa-bars text-base';
        renderHistoryFeed();
      } else if (tab === 'session_detail') {
        document.getElementById('pageSessionDetail').classList.remove('hidden');
        document.getElementById('navHistory').classList.add('text-[#FF1E27]');
        headerHome.classList.add('hidden');
        headerPages.classList.remove('hidden');
        pageTitle.textContent = 'تفاصيل الجلسة #' + currentDetailSession.id;
        pageSubtitle.textContent = 'فحص الراوتر: ' + currentDetailSession.routerName;
        headerBackIcon.className = 'fa-solid fa-arrow-right text-base';
        renderSessionDetails();
      } else if (tab === 'settings') {
        document.getElementById('pageSettings').classList.remove('hidden');
        document.getElementById('navSettings').classList.add('text-[#FF1E27]');
        headerHome.classList.add('hidden');
        headerPages.classList.remove('hidden');
        pageTitle.textContent = 'الإعدادات العامة';
        pageSubtitle.textContent = 'تخصيص النظام والمظهر العام';
        headerBackIcon.className = 'fa-solid fa-bars text-base';
      }
    }

    function onHeaderBackClick() {
      if (currentTab === 'session_detail') {
        navigateTo('history');
      } else {
        toggleDrawer();
      }
    }

    function toggleDrawer() {
      const drawer = document.getElementById('navDrawer');
      const overlay = document.getElementById('drawerOverlay');
      if (drawer.classList.contains('-translate-x-full')) {
        drawer.classList.remove('-translate-x-full');
        overlay.classList.remove('hidden');
      } else {
        drawer.classList.add('-translate-x-full');
        overlay.classList.add('hidden');
      }
    }

    function showToast(msg, icon = '✓') {
      const t = document.getElementById('toastNotification');
      document.getElementById('toastText').textContent = msg;
      document.getElementById('toastIcon').textContent = icon;
      t.classList.remove('opacity-0');
      t.classList.add('opacity-100');
      setTimeout(() => {
        t.classList.remove('opacity-100');
        t.classList.add('opacity-0');
      }, 2500);
    }

    function copyIpPath() {
      const text = document.getElementById('homeRouterIpPath').textContent;
      navigator.clipboard.writeText(text).then(() => {
        showToast('تم نسخ عنوان الراوتر والمسار');
      });
    }

    function toggleSwitch(btn) {
      const thumb = btn.firstElementChild;
      if (btn.classList.contains('bg-[#FF1E27]')) {
        btn.classList.remove('bg-[#FF1E27]');
        btn.classList.add('bg-[#1E283C]');
        thumb.classList.remove('translate-x-5');
      } else {
        btn.classList.remove('bg-[#1E283C]');
        btn.classList.add('bg-[#FF1E27]');
        thumb.classList.add('translate-x-5');
      }
    }

    // Router Selector
    function openRouterSelectorModal() {
      const modal = document.getElementById('routerModal');
      const overlay = document.getElementById('routerModalOverlay');
      const container = document.getElementById('routerListContainer');
      container.innerHTML = '';

      availableRouters.forEach(r => {
        const isCurrent = r.id === currentRouter.id;
        const item = document.createElement('div');
        item.className = 'p-3 rounded-2xl border transition cursor-pointer flex items-center justify-between ' + 
          (isCurrent ? 'bg-[#1C0306] border-[#FF1E27]' : 'bg-[#070A10] border-[#161E2E] hover:border-[#1E283C]');
        item.onclick = () => {
          currentRouter = r;
          document.getElementById('homeRouterName').textContent = r.name;
          document.getElementById('homeRouterIpPath').textContent = 'IP : ' + r.ip + ' | Path : ' + r.loginPath;
          closeRouterModal();
          showToast('تم اختيار الراوتر: ' + r.name);
        };

        const deleteBtnHtml = (!isCurrent && availableRouters.length > 1) 
          ? \`<button onclick="event.stopPropagation(); confirmDeleteRouter('\${r.id}', '\${r.name}')" title="حذف الراوتر" class="w-7 h-7 rounded-lg bg-[#26080B] border border-[#FF1E27]/40 text-[#FF1E27] hover:bg-[#FF1E27] hover:text-white flex items-center justify-center text-xs transition active:scale-90"><i class="fa-solid fa-trash-can"></i></button>\` 
          : '';

        item.innerHTML = \`
          <div class="flex items-center gap-2">
            \${isCurrent ? '<span class="text-[10px] font-bold text-[#00E676] bg-[#00E676]/15 border border-[#00E676]/30 px-2 py-0.5 rounded-full">نشط</span>' : ''}
            \${deleteBtnHtml}
          </div>
          <div class="text-right flex-1 pr-2">
            <h5 class="text-xs font-bold text-white">\${r.name}</h5>
            <p class="text-[10px] font-mono text-[#94A3B8]">IP: \${r.ip} · \${r.strategy.toUpperCase()}</p>
          </div>
        \`;
        container.appendChild(item);
      });

      modal.classList.remove('hidden');
      overlay.classList.remove('hidden');
    }

    function confirmDeleteRouter(id, name) {
      openConfirmModal({
        title: 'حذف ملف الراوتر',
        message: 'هل أنت متأكد من رغبتك في حذف "' + name + '" نهائياً من النظام؟ لا يمكن التراجع عن هذه العملية.',
        icon: 'fa-trash-can',
        type: 'danger',
        confirmText: 'حذف نهائي',
        cancelText: 'إلغاء',
        onConfirm: () => {
          availableRouters = availableRouters.filter(r => r.id !== id);
          if (currentRouter.id === id) {
            currentRouter = availableRouters[0];
            document.getElementById('homeRouterName').textContent = currentRouter.name;
            document.getElementById('homeRouterIpPath').textContent = 'IP : ' + currentRouter.ip + ' | Path : ' + currentRouter.loginPath;
          }
          openRouterSelectorModal();
          showToast('تم حذف ملف الراوتر بنجاح');
        }
      });
    }

    function closeRouterModal() {
      document.getElementById('routerModal').classList.add('hidden');
      document.getElementById('routerModalOverlay').classList.add('hidden');
    }

    // Modal and Dialog Management System
    let confirmActionCallback = null;

    function openConfirmModal(config) {
      const overlay = document.getElementById('confirmModalOverlay');
      const modal = document.getElementById('confirmModal');
      const title = document.getElementById('confirmModalTitle');
      const msg = document.getElementById('confirmModalMessage');
      const icon = document.getElementById('confirmModalIcon');
      const badge = document.getElementById('confirmModalBadge');
      const cancelBtn = document.getElementById('confirmModalCancelBtn');
      const actionBtn = document.getElementById('confirmModalActionBtn');

      title.textContent = config.title || 'تأكيد العملية';
      msg.textContent = config.message || '';
      
      const iconCls = config.icon || 'fa-triangle-exclamation';
      icon.className = (iconCls.startsWith('fa-') ? 'fa-solid ' + iconCls : iconCls) + ' text-2xl';

      const type = config.type || 'warning';
      if (type === 'danger' || type === 'error') {
        badge.className = 'w-16 h-16 rounded-[20px] bg-[#24080B] border-1.5 border-[#FF1E27] flex items-center justify-center mx-auto shadow-lg shadow-red-950/40';
        icon.style.color = '#FF1E27';
        actionBtn.className = 'flex-1 py-2.5 rounded-xl bg-gradient-to-r from-[#FF1E27] to-[#D50000] text-xs font-bold text-white shadow-lg shadow-red-950/50 hover:brightness-110 active:scale-95 transition';
      } else if (type === 'success') {
        badge.className = 'w-16 h-16 rounded-[20px] bg-[#072212] border-1.5 border-[#00E676] flex items-center justify-center mx-auto shadow-lg shadow-emerald-950/40';
        icon.style.color = '#00E676';
        actionBtn.className = 'flex-1 py-2.5 rounded-xl bg-gradient-to-r from-[#00E676] to-[#00C853] text-xs font-bold text-black shadow-lg shadow-emerald-950/50 hover:brightness-110 active:scale-95 transition';
      } else if (type === 'info') {
        badge.className = 'w-16 h-16 rounded-[20px] bg-[#081426] border-1.5 border-[#2979FF] flex items-center justify-center mx-auto shadow-lg shadow-blue-950/40';
        icon.style.color = '#2979FF';
        actionBtn.className = 'flex-1 py-2.5 rounded-xl bg-gradient-to-r from-[#2979FF] to-[#1565C0] text-xs font-bold text-white shadow-lg shadow-blue-950/50 hover:brightness-110 active:scale-95 transition';
      } else {
        // warning
        badge.className = 'w-16 h-16 rounded-[20px] bg-[#241605] border-1.5 border-[#FF9800] flex items-center justify-center mx-auto shadow-lg shadow-amber-950/40';
        icon.style.color = '#FF9800';
        actionBtn.className = 'flex-1 py-2.5 rounded-xl bg-gradient-to-r from-[#FF1E27] to-[#D50000] text-xs font-bold text-white shadow-lg shadow-red-950/50 hover:brightness-110 active:scale-95 transition';
      }

      cancelBtn.textContent = config.cancelText || 'إلغاء';
      cancelBtn.style.display = config.hideCancel ? 'none' : 'block';
      actionBtn.textContent = config.confirmText || 'تأكيد';

      confirmActionCallback = config.onConfirm || null;
      actionBtn.onclick = () => {
        closeConfirmModal();
        if (confirmActionCallback) confirmActionCallback();
      };

      overlay.classList.remove('hidden');
      modal.classList.remove('hidden');
    }

    function closeConfirmModal() {
      document.getElementById('confirmModalOverlay').classList.add('hidden');
      document.getElementById('confirmModal').classList.add('hidden');
    }

    function openSelectionModal(config) {
      const overlay = document.getElementById('selectionModalOverlay');
      const modal = document.getElementById('selectionModal');
      const title = document.getElementById('selectionModalTitle');
      const icon = document.getElementById('selectionModalHeaderIcon');
      const container = document.getElementById('selectionModalOptions');

      title.textContent = config.title || 'اختيار';
      if (config.icon) {
        icon.className = (config.icon.startsWith('fa-') ? 'fa-solid ' : '') + config.icon;
      }

      container.innerHTML = '';
      (config.options || []).forEach((opt, idx) => {
        const isSelected = idx === config.selectedIndex;
        const item = document.createElement('div');
        item.className = 'flex items-center justify-between p-3 rounded-xl border transition cursor-pointer ' +
          (isSelected ? 'bg-[#24080B] border-[#FF1E27]' : 'bg-[#070A10] border-[#161E2E] hover:border-[#1E283C]');
        
        item.innerHTML = (isSelected 
          ? '<i class="fa-solid fa-circle-check text-xs text-[#00E676]"></i>' 
          : '<div class="w-2 h-2 rounded-full bg-[#334155]"></div>') + 
          '<span class="text-xs font-bold ' + (isSelected ? 'text-white' : 'text-[#CBD5E1]') + '">' + opt + '</span>';

        item.onclick = () => {
          closeSelectionModal();
          if (config.onSelect) config.onSelect(idx, opt);
        };
        container.appendChild(item);
      });

      overlay.classList.remove('hidden');
      modal.classList.remove('hidden');
    }

    function closeSelectionModal() {
      document.getElementById('selectionModalOverlay').classList.add('hidden');
      document.getElementById('selectionModal').classList.add('hidden');
    }

    function openColorPickerDialog() {
      document.getElementById('colorPickerModalOverlay').classList.remove('hidden');
      document.getElementById('colorPickerModal').classList.remove('hidden');
    }

    function closeColorPickerModal() {
      document.getElementById('colorPickerModalOverlay').classList.add('hidden');
      document.getElementById('colorPickerModal').classList.add('hidden');
    }

    function selectAppColor(key, hex, name) {
      openConfirmModal({
        title: 'تغيير السمة اللونية',
        message: 'هل تريد تطبيق اللون "' + name + '" كلون رئيسي لواجهة التطبيق؟',
        icon: 'fa-palette',
        type: 'info',
        confirmText: 'تطبيق اللون',
        cancelText: 'إلغاء',
        onConfirm: () => {
          document.querySelectorAll('.color-check').forEach(el => el.classList.add('hidden'));
          const activeCheck = document.getElementById('checkColor' + key.charAt(0).toUpperCase() + key.slice(1));
          if (activeCheck) activeCheck.classList.remove('hidden');
          
          ['Red', 'Blue', 'Purple', 'Green', 'Yellow'].forEach(c => {
            const card = document.getElementById('colorCard' + c);
            if (card) {
              if (c.toLowerCase() === key) {
                card.className = 'flex items-center justify-between p-3 rounded-xl bg-[#24080B] border border-[#FF1E27] cursor-pointer transition';
              } else {
                card.className = 'flex items-center justify-between p-3 rounded-xl bg-[#070A10] border border-[#161E2E] hover:border-[#1E283C] cursor-pointer transition';
              }
            }
          });

          const summary = document.getElementById('settingsColorSummary');
          if (summary) summary.textContent = name;
          const dot = document.getElementById('settingsColorDot');
          if (dot) dot.style.backgroundColor = hex;
          
          const previewName = document.getElementById('colorPickerPreviewName');
          if (previewName) previewName.textContent = 'اللون المحدد: ' + name;
          const previewBtn = document.getElementById('colorPickerPreviewBtn');
          if (previewBtn) {
            previewBtn.style.backgroundColor = hex;
            previewBtn.style.color = key === 'yellow' ? '#000000' : '#FFFFFF';
          }

          closeColorPickerModal();
          showToast('تم تغيير اللون الرئيسي إلى ' + name);
        }
      });
    }

    function openThemeDialog() {
      const options = ['الوضع الداكن (Dark)', 'الوضع الفاتح (Light)', 'افتراضي النظام (System)'];
      openSelectionModal({
        title: 'اختيار السمة والمظهر',
        icon: 'fa-palette',
        options: options,
        selectedIndex: 0,
        onSelect: (idx, opt) => {
          document.getElementById('settingsThemeSummary').textContent = opt;
          showToast('تم تفعيل ' + opt);
        }
      });
    }

    function openLanguageDialog() {
      const options = ['العربية (الافتراضية)', 'English', 'افتراضي النظام'];
      openSelectionModal({
        title: 'لغة التطبيق',
        icon: 'fa-language',
        options: options,
        selectedIndex: 0,
        onSelect: (idx, opt) => {
          document.getElementById('settingsLangSummary').textContent = opt;
          showToast('تم ضبط اللغة: ' + opt);
        }
      });
    }

    function openDelayDialog(type) {
      if (type === 'page_load') {
        openSelectionModal({
          title: 'مدة انتظار تحميل صفحة الدخول',
          icon: 'fa-stopwatch',
          options: ['1 ثانية', '2 ثانية (الافتراضي للتحميل)', '3 ثوانٍ', '5 ثوانٍ'],
          selectedIndex: 1,
          onSelect: (idx, opt) => {
            document.getElementById('settingsPageLoadSummary').textContent = opt;
            showToast('تم حفظ مدة الانتظار: ' + opt);
          }
        });
      } else if (type === 'card_test') {
        openSelectionModal({
          title: 'مدة انتظار نتيجة البطاقة',
          icon: 'fa-stopwatch',
          options: ['1 ثانية', '2 ثانية', '3 ثوانٍ (الافتراضي للنتيجة)', '5 ثوانٍ'],
          selectedIndex: 2,
          onSelect: (idx, opt) => {
            document.getElementById('settingsCardTestSummary').textContent = opt;
            showToast('تم حفظ مدة الانتظار: ' + opt);
          }
        });
      } else if (type === 'screenshot') {
        openSelectionModal({
          title: 'سرعة تحديث البث المباشر',
          icon: 'fa-stopwatch',
          options: ['1 ثانية', '2 ثانية (الافتراضي)', '3 ثوانٍ', '4 ثوانٍ'],
          selectedIndex: 1,
          onSelect: (idx, opt) => {
            document.getElementById('settingsScreenshotSummary').textContent = opt;
            showToast('تم حفظ سرعة البث: ' + opt);
          }
        });
      } else if (type === 'threads') {
        openSelectionModal({
          title: 'عدد الصفحات المحملة بالخلفية',
          icon: 'fa-server',
          options: ['1 صفحة (الافتراضي)', '2 صفحتان', '3 صفحات', '5 صفحات'],
          selectedIndex: 3,
          onSelect: (idx, opt) => {
            document.getElementById('settingsThreadsCount').textContent = opt.split(' ')[0];
            showToast('تم حفظ عدد الصفحات: ' + opt);
          }
        });
      }
    }

    function confirmResetDelays() {
      openConfirmModal({
        title: 'إعادة ضبط أوقات الانتظار',
        message: 'هل أنت متأكد من استعادة القيم الافتراضية لأوقات انتظار تحميل الصفحات ونتائج البطاقات؟',
        icon: 'fa-rotate-left',
        type: 'warning',
        confirmText: 'استعادة الافتراضي',
        onConfirm: () => {
          document.getElementById('settingsPageLoadSummary').textContent = '2 ثانية (الافتراضي للتحميل)';
          document.getElementById('settingsCardTestSummary').textContent = '3 ثانية (الافتراضي للنتيجة)';
          document.getElementById('settingsScreenshotSummary').textContent = '2 ثانية (الافتراضي)';
          showToast('تمت استعادة أوقات الانتظار الافتراضية');
        }
      });
    }

    function confirmClearHistory() {
      openConfirmModal({
        title: 'مسح سجل الجلسات بالكامل',
        message: 'هل أنت متأكد من رغبتك في حذف كافة سجلات الفحص والبطاقات المخزنة؟ لا يمكن التراجع عن هذا الإجراء.',
        icon: 'fa-trash-can',
        type: 'danger',
        confirmText: 'تأكيد الحذف النهائي',
        cancelText: 'إلغاء',
        onConfirm: () => {
          mockSessions = [];
          mockResults = [];
          renderHistoryFeed();
          showToast('تم حذف جميع سجلات الفحص بنجاح');
        }
      });
    }

    function confirmExitApp() {
      toggleDrawer();
      openConfirmModal({
        title: 'تأكيد الخروج من التطبيق',
        message: 'هل أنت متأكد من رغبتك في إغلاق تطبيق WiFi Card Master Pro وإنهاء الجلسة؟',
        icon: 'fa-arrow-right-from-bracket',
        type: 'warning',
        confirmText: 'خروج',
        cancelText: 'إلغاء',
        onConfirm: () => {
          showToast('تم إغلاق التطبيق');
        }
      });
    }

    function openAboutDialog() {
      toggleDrawer();
      openConfirmModal({
        title: 'حول التطبيق',
        message: 'WiFi Card Master Pro - الإصدار 1.0.0-Stable.\nمنظومة متقدمة لفحص واختبار بطاقات شبكات المايكروتك وبوابات الهوت سبوت الكابتيف بأعلى سرعة وكفاءة.',
        icon: 'fa-circle-info',
        type: 'info',
        confirmText: 'إغلاق',
        hideCancel: true
      });
    }

    function confirmCancelTest() {
      openConfirmModal({
        title: 'إلغاء وفصل الفحص',
        message: 'هل أنت متأكد من رغبتك في إلغاء عملية الفحص الجارية وفصل الاتصال ببوابة الهوت سبوت؟',
        icon: 'fa-triangle-exclamation',
        type: 'danger',
        confirmText: 'نعم، إلغاء الفحص',
        cancelText: 'متابعة الفحص',
        onConfirm: () => {
          cancelTestAction();
        }
      });
    }

    function confirmClearTerminal() {
      openConfirmModal({
        title: 'مسح سجل العمليات',
        message: 'هل تريد مسح الرسائل الحالية من شاشة سجل العمليات المباشر؟',
        icon: 'fa-trash-can',
        type: 'warning',
        confirmText: 'مسح السجل',
        cancelText: 'إلغاء',
        onConfirm: () => {
          clearHomeLogs();
        }
      });
    }

    // Testing logic & Rapid Live Stream Engine
    let streamSubStep = 0;

    function startTestingFromHome() {
      const count = parseInt(document.getElementById('inputCount').value) || 50;
      const prefix = document.getElementById('inputPrefix').value || 'D';
      
      activeCardsList = [];
      const totalToTest = Math.min(count, 30);
      for (let i = 1; i <= totalToTest; i++) {
        activeCardsList.push(prefix + String(100 + i).padStart(4, '0'));
      }
      activeCardIndex = 0;
      streamSubStep = 0;

      // Navigate immediately to the Test screen
      navigateTo('test');
      startTestExecution();
    }

    function startTestExecution() {
      testActive = true;
      testPaused = false;
      document.getElementById('testReadyStatus').textContent = 'جاري الفحص';
      document.getElementById('testReadyStatus').className = 'font-black text-sm text-[#FF1E27] animate-pulse';
      document.getElementById('btnTestPauseText').textContent = 'إيقاف مؤقت';
      document.getElementById('btnTestPauseIcon').className = 'fa-solid fa-circle-stop text-sm';

      if (activeCardsList.length === 0) {
        const prefix = document.getElementById('inputPrefix') ? document.getElementById('inputPrefix').value || 'D' : 'D';
        activeCardsList = [prefix + '00101', prefix + '00102', prefix + '00103', prefix + '00104', prefix + '00105'];
        activeCardIndex = 0;
      }

      if (testInterval) clearInterval(testInterval);

      // Fast, video-like frame stream (updates every 450ms for realistic rapid WebView playback)
      testInterval = setInterval(() => {
        if (testPaused) return;

        if (activeCardIndex >= activeCardsList.length) {
          clearInterval(testInterval);
          testActive = false;
          document.getElementById('testReadyStatus').textContent = 'اكتمل الفحص';
          document.getElementById('testReadyStatus').className = 'font-black text-sm text-[#00E676]';
          document.getElementById('testActiveCardInfo').textContent = 'تم اكتمال فحص جميع البطاقات بنجاح (' + activeCardsList.length + '/' + activeCardsList.length + ')';
          document.getElementById('portalStreamStatus').textContent = 'تم الانتهاء من فحص كافة كروت الحزمة';
          document.getElementById('portalLoginBtn').textContent = 'اكتمل الفحص';
          document.getElementById('portalLoginBtn').className = 'bg-emerald-600 text-white font-bold text-xs py-2 rounded-xl text-center shadow-lg';
          showToast('اكتمل فحص الجلسة بنجاح!');
          return;
        }

        const card = activeCardsList[activeCardIndex];

        // Simulate multi-stage live browser rendering per card (typing -> clicking -> verifying)
        if (streamSubStep === 0) {
          // Frame 1: Typing card code into captive portal
          document.getElementById('portalActiveCard').textContent = card;
          document.getElementById('portalActiveCard').className = 'text-white font-mono font-black text-xl tracking-widest block animate-pulse';
          document.getElementById('portalStreamStatus').textContent = 'جاري إدخال الكرت ' + card + ' في بوابة الهوت سبوت...';
          document.getElementById('portalLoginBtn').textContent = 'تسجيل الدخول...';
          document.getElementById('portalLoginBtn').className = 'bg-gradient-to-r from-[#FF1E27] to-[#D50000] text-white font-bold text-xs py-2 rounded-xl text-center shadow-lg shadow-red-900/40';
          
          document.getElementById('testActiveCardInfo').textContent = 'الكرت الحالي: ' + card + ' (' + (activeCardIndex + 1) + ' / ' + activeCardsList.length + ')';
          const pct = Math.round(((activeCardIndex + 0.5) / activeCardsList.length) * 100);
          document.getElementById('testTrackMarker').style.width = pct + '%';
          
          streamSubStep = 1;
        } else if (streamSubStep === 1) {
          // Frame 2: Submit credentials & gateway handshake
          document.getElementById('portalStreamStatus').textContent = 'جاري إرسال الاعتماد إلى البوابة (192.168.1.1)...';
          document.getElementById('portalLoginBtn').textContent = 'جاري التحقق...';
          document.getElementById('portalLoginBtn').className = 'bg-amber-600 text-white font-bold text-xs py-2 rounded-xl text-center shadow-lg';
          
          streamSubStep = 2;
        } else {
          // Frame 3: Verification response received
          const isSuccess = Math.random() > 0.35;
          const isTwoDev = isSuccess && Math.random() < 0.25;
          const duration = Math.floor(Math.random() * 60) + 30;
          const timeStr = new Date().toLocaleTimeString();

          if (isSuccess) {
            document.getElementById('portalStreamStatus').textContent = isTwoDev ? '● كرت نشط (مستعمل بجهازين)' : '● تم الاتصال وتسجيل الدخول بنجاح (Code 200)';
            document.getElementById('portalLoginBtn').textContent = isTwoDev ? 'متصل (جهازين)' : 'نجاح الاتصال ✓';
            document.getElementById('portalLoginBtn').className = isTwoDev ? 'bg-amber-500 text-white font-bold text-xs py-2 rounded-xl text-center' : 'bg-emerald-600 text-white font-bold text-xs py-2 rounded-xl text-center';
          } else {
            document.getElementById('portalStreamStatus').textContent = '● فشل المصادقة / كرت غير صالح';
            document.getElementById('portalLoginBtn').textContent = 'فشل الدخول ✗';
            document.getElementById('portalLoginBtn').className = 'bg-rose-700 text-white font-bold text-xs py-2 rounded-xl text-center';
          }

          const result = {
            id: String(Date.now()),
            sessionId: '104',
            cardCode: card,
            state: isSuccess ? (isTwoDev ? 'TwoDevices' : 'Success') : 'Failed',
            durationMs: duration,
            time: timeStr,
            message: isSuccess ? (isTwoDev ? 'الكرت فعال ومستعمل بجهازين' : 'تم تسجيل الدخول بنجاح') : 'فشل المصادقة / بطاقة غير صالحة'
          };
          mockResults.unshift(result);

          // Update Home Dashboard stats
          const succ = mockResults.filter(r => r.state === 'Success' || r.state === 'TwoDevices').length;
          const fail = mockResults.filter(r => r.state === 'Failed').length;
          if (document.getElementById('statHomeSuccess')) document.getElementById('statHomeSuccess').textContent = succ;
          if (document.getElementById('statHomeFailed')) document.getElementById('statHomeFailed').textContent = fail;
          if (document.getElementById('statHomeTotal')) document.getElementById('statHomeTotal').textContent = succ + fail;

          // Add entry to Live Terminal
          const terminal = document.getElementById('homeLogTerminal');
          if (terminal) {
            const log = document.createElement('div');
            log.className = isSuccess ? (isTwoDev ? 'text-[#FF9100]' : 'text-[#00E676]') : 'text-[#FF1E27]';
            log.textContent = '[' + timeStr + '] فحص ' + card + ' -> ' + (isSuccess ? (isTwoDev ? 'جهازين' : 'نجاح') : 'فشل');
            terminal.prepend(log);
          }

          activeCardIndex++;
          const pct = Math.round((activeCardIndex / activeCardsList.length) * 100);
          document.getElementById('testTrackMarker').style.width = pct + '%';
          
          streamSubStep = 0;
        }
      }, 450);
    }

    function toggleTestPauseAction() {
      if (!testActive) {
        startTestExecution();
        return;
      }
      testPaused = !testPaused;
      if (testPaused) {
        document.getElementById('testReadyStatus').textContent = 'موقوف مؤقتاً';
        document.getElementById('testReadyStatus').className = 'font-black text-sm text-[#FF9100]';
        document.getElementById('btnTestPauseText').textContent = 'استئناف';
        document.getElementById('btnTestPauseIcon').className = 'fa-solid fa-play text-sm';
        document.getElementById('portalStreamStatus').textContent = 'تم إيقاف بث الفحص مؤقتاً بواسطة المستخدم';
        showToast('تم إيقاف الفحص مؤقتاً');
      } else {
        document.getElementById('testReadyStatus').textContent = 'جاري الفحص';
        document.getElementById('testReadyStatus').className = 'font-black text-sm text-[#FF1E27] animate-pulse';
        document.getElementById('btnTestPauseText').textContent = 'إيقاف مؤقت';
        document.getElementById('btnTestPauseIcon').className = 'fa-solid fa-circle-stop text-sm';
        document.getElementById('portalStreamStatus').textContent = 'استئناف بث لقطات الشاشة الحية...';
        showToast('تم استئناف الفحص');
      }
    }

    function cancelTestAction() {
      if (testInterval) clearInterval(testInterval);
      testActive = false;
      testPaused = false;
      document.getElementById('testReadyStatus').textContent = 'جاهز';
      document.getElementById('testReadyStatus').className = 'font-black text-sm text-[#00E676]';
      document.getElementById('testActiveCardInfo').textContent = 'تم إلغاء وفصل الجلسة الحالية';
      document.getElementById('testTrackMarker').style.width = '0%';
      document.getElementById('portalStreamStatus').textContent = 'تم فصل الجلسة وإيقاف الفحص';
      showToast('تم إلغاء عملية الفحص');
      navigateTo('home');
    }

    function stopTestingAction() {
      if (testInterval) clearInterval(testInterval);
      testActive = false;
      showToast('تم إيقاف الفحص بنجاح');
    }

    function clearHomeLogs() {
      document.getElementById('homeLogTerminal').innerHTML = '<div class="text-[#64748B]">تم مسح سجل العمليات المباشر.</div>';
      showToast('تم مسح السجل');
    }

    // History Feed
    function renderHistoryFeed() {
      const container = document.getElementById('sessionsListFeed');
      container.innerHTML = '';

      mockSessions.forEach(s => {
        const card = document.createElement('div');
        card.className = 'bg-[#0E131F] border border-[#1E283C] hover:border-[#FF1E27]/50 rounded-[18px] p-3.5 space-y-3 cursor-pointer transition';
        card.onclick = () => {
          currentDetailSession = s;
          navigateTo('session_detail');
        };

        card.innerHTML = \`
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-[#64748B] text-base font-bold">⋮</span>
              <div class="flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-[#00E676]/15 border border-[#00E676]/40 text-[#00E676] text-[10px] font-bold">
                <span>مكتملة</span>
                <i class="fa-solid fa-circle-check text-[10px]"></i>
              </div>
            </div>

            <div class="text-right">
              <div class="flex items-center justify-end gap-1.5">
                <span class="font-black text-sm text-white">Session #\${s.id}</span>
                <span class="w-1.5 h-1.5 rounded-full bg-[#94A3B8]"></span>
              </div>
              <div class="flex items-center justify-end gap-2 text-[10px] font-mono text-[#64748B] mt-0.5">
                <span>\${s.date}</span>
                <span class="text-[#FF1E27] font-bold">🌐 \${s.routerName}</span>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-3 gap-2 pt-2 border-t border-[#1E283C]/70">
            <div class="bg-[#070A12] border border-[#1E283C] rounded-xl py-1.5 text-center">
              <span class="font-mono font-black text-xs text-white block">\${s.totalCards}</span>
              <span class="text-[9px] text-[#64748B]">إجمالي</span>
            </div>
            <div class="bg-[#070A12] border border-[#00E676]/30 rounded-xl py-1.5 text-center">
              <span class="font-mono font-black text-xs text-[#00E676] block">\${s.successCount}</span>
              <span class="text-[9px] text-[#00E676]">ناجحة</span>
            </div>
            <div class="bg-[#070A12] border border-[#FF1E27]/30 rounded-xl py-1.5 text-center">
              <span class="font-mono font-black text-xs text-[#FF1E27] block">\${s.failureCount}</span>
              <span class="text-[9px] text-[#FF1E27]">فاشلة</span>
            </div>
          </div>
        \`;
        container.appendChild(card);
      });
    }

    function filterHistoryList() {
      const q = document.getElementById('historySearchInput').value.toLowerCase();
      const items = document.getElementById('sessionsListFeed').children;
      Array.from(items).forEach(item => {
        const text = item.textContent.toLowerCase();
        item.style.display = text.includes(q) ? 'block' : 'none';
      });
    }

    // Session Details
    function renderSessionDetails() {
      document.getElementById('sessDetailId').textContent = 'Session #' + currentDetailSession.id;
      document.getElementById('sessDetailDate').textContent = currentDetailSession.date;
      document.getElementById('sessDetailRouter').textContent = '🌐 ' + currentDetailSession.routerName;
      document.getElementById('sessDetailTotal').textContent = currentDetailSession.totalCards;
      document.getElementById('sessDetailSuccess').textContent = currentDetailSession.successCount;
      document.getElementById('sessDetailFailed').textContent = currentDetailSession.failureCount;

      renderDetailCards();
    }

    function renderDetailCards() {
      const container = document.getElementById('detailCardsFeed');
      container.innerHTML = '';

      let list = mockResults;
      if (activeDetailFilter === 'success') {
        list = list.filter(r => r.state === 'Success');
      } else if (activeDetailFilter === 'failed') {
        list = list.filter(r => r.state === 'Failed');
      } else if (activeDetailFilter === 'two_devices') {
        list = list.filter(r => r.message.includes('جهازين'));
      }

      if (list.length === 0) {
        container.innerHTML = \`
          <div class="bg-[#0E131F] border border-[#1E283C] rounded-[20px] p-6 text-center space-y-2">
            <img src="/empty_search.jpg" alt="Empty" class="w-20 h-20 mx-auto rounded-xl object-cover opacity-80">
            <h4 class="font-bold text-sm text-white">لا توجد نتائج مطابقة</h4>
            <p class="text-[11px] text-[#64748B]">جرّب تغيير معيار الفلترة أو مسح حقل البحث</p>
          </div>
        \`;
        return;
      }

      list.forEach(r => {
        const isSuccess = r.state === 'Success';
        const card = document.createElement('div');
        card.className = 'bg-[#0E131F] border rounded-xl p-3 flex items-center justify-between ' + 
          (isSuccess ? 'border-[#00E676]/30' : 'border-[#FF1E27]/30');

        card.innerHTML = \`
          <span class="text-[10px] font-mono text-[#64748B]">\${r.durationMs}ms</span>
          <div class="flex items-center gap-2.5 text-right">
            <div>
              <h5 class="text-xs font-mono font-bold text-white">\${r.cardCode}</h5>
              <p class="text-[10px] text-[#94A3B8]">\${r.message}</p>
            </div>
            <div class="w-8 h-8 rounded-lg flex items-center justify-center text-xs \${isSuccess ? 'bg-[#00E676]/15 text-[#00E676]' : 'bg-[#FF1E27]/15 text-[#FF1E27]'}">
              <i class="\${isSuccess ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-xmark'}"></i>
            </div>
          </div>
        \`;
        container.appendChild(card);
      });
    }

    function filterDetailResults(filter) {
      activeDetailFilter = filter;
      ['chipDetailAll', 'chipDetailSuccess', 'chipDetailFailed', 'chipDetailTwoDev'].forEach(id => {
        const btn = document.getElementById(id);
        btn.className = 'bg-[#0E131F] border border-[#1E283C] text-[#94A3B8] font-bold py-2 rounded-xl transition';
      });

      if (filter === 'all') document.getElementById('chipDetailAll').className = 'bg-[#FF1E27] text-white font-bold py-2 rounded-xl transition';
      if (filter === 'success') document.getElementById('chipDetailSuccess').className = 'bg-[#00E676] text-black font-bold py-2 rounded-xl transition';
      if (filter === 'failed') document.getElementById('chipDetailFailed').className = 'bg-[#FF1E27] text-white font-bold py-2 rounded-xl transition';
      if (filter === 'two_devices') document.getElementById('chipDetailTwoDev').className = 'bg-[#2979FF] text-white font-bold py-2 rounded-xl transition';

      renderDetailCards();
    }

    function filterDetailCards() {
      const q = document.getElementById('detailSearchInput').value.toLowerCase();
      const items = document.getElementById('detailCardsFeed').children;
      Array.from(items).forEach(item => {
        const text = item.textContent.toLowerCase();
        item.style.display = text.includes(q) ? 'flex' : 'none';
      });
    }

    function exportCurrentSession() {
      const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(mockResults, null, 2));
      const downloadAnchor = document.createElement('a');
      downloadAnchor.setAttribute("href", dataStr);
      downloadAnchor.setAttribute("download", "session_" + currentDetailSession.id + "_results.json");
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      downloadAnchor.remove();
      showToast('تم تصدير نتائج الجلسة بصيغة JSON');
    }

    // Init Home on load
    navigateTo('home');
  </script>
</body>
</html>`;
}

// HTTP Server
const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost:3000'}`);
  const pathname = parsedUrl.pathname;

  // 1. Download APK Route
  if (pathname === '/download/app-debug.apk' || pathname === '/download/WIFI-CARD.apk') {
    const activeApk = APK_CANDIDATES.find(p => fs.existsSync(p));
    if (activeApk) {
      const stat = fs.statSync(activeApk);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="WIFI-CARD-debug.apk"',
        'Content-Length': stat.size
      });
      const stream = fs.createReadStream(activeApk);
      stream.on('error', (err) => {
        console.error('[WiFi Card Server] Stream error on APK download:', err);
        if (!res.headersSent) {
          res.writeHead(500, { 'Content-Type': 'text/plain' });
          res.end('Error reading APK file');
        }
      });
      stream.pipe(res);
      return;
    } else {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('APK build file not found');
      return;
    }
  }

  // 2. Health check route
  if (pathname === '/health' || pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', time: new Date().toISOString() }));
    return;
  }

  // 3. API sessions route
  if (pathname === '/api/sessions') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(sessions));
    return;
  }

  // 4. API results route
  if (pathname === '/api/results') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(testResults));
    return;
  }

  // 5. Static Images (PNG / JPG / JPEG / SVG)
  if (pathname.endsWith('.png') || pathname.endsWith('.jpg') || pathname.endsWith('.jpeg') || pathname.endsWith('.svg')) {
    const filename = path.basename(pathname);
    const candidatePaths = [
      path.join(PUBLIC_DIR, filename),
      path.join(APP_DIR, filename),
      path.join(APP_DIR, 'app/src/main/res/drawable', filename)
    ];

    // If .png was requested but .jpg exists (authentic files), fallback to .jpg
    if (filename.endsWith('.png')) {
      const jpgName = filename.replace(/\.png$/, '.jpg');
      candidatePaths.push(path.join(PUBLIC_DIR, jpgName));
      candidatePaths.push(path.join(APP_DIR, 'app/src/main/res/drawable', jpgName));
    }

    for (const p of candidatePaths) {
      if (fs.existsSync(p)) {
        const ext = path.extname(p).toLowerCase();
        const contentType = ext === '.png' ? 'image/png' : (ext === '.svg' ? 'image/svg+xml' : 'image/jpeg');
        res.writeHead(200, {
          'Content-Type': contentType,
          'Cache-Control': 'public, max-age=86400'
        });
        const stream = fs.createReadStream(p);
        stream.on('error', (err) => {
          console.error('[WiFi Card Server] Stream error on image:', err);
        });
        stream.pipe(res);
        return;
      }
    }
  }

  // 6. Default: Serve rich interactive Android Web Simulator
  const html = generateHtml();
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-cache, no-store, must-revalidate'
  });
  res.end(html);
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`[WiFi Card Master Pro] Port ${PORT} already in use; waiting or running on existing process.`);
  } else {
    console.error('[WiFi Card Master Pro] Server error:', err);
  }
});

server.listen(PORT, HOST, () => {
  console.log(`[WiFi Card Master Pro] Web Server running at http://${HOST}:${PORT}`);
  console.log(`[WiFi Card Master Pro] Serving Android APK from: ${APK_PATH}`);
});

process.on('uncaughtException', (err) => {
  console.error('[WiFi Card Master Pro] Uncaught exception:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('[WiFi Card Master Pro] Unhandled rejection:', reason);
});

process.on('SIGTERM', () => {
  console.log('Received SIGTERM, shutting down...');
  server.close(() => process.exit(0));
});

process.on('SIGINT', () => {
  console.log('Received SIGINT, shutting down...');
  server.close(() => process.exit(0));
});
