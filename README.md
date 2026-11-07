<!DOCTYPE html>

<html lang="zh-CN">

<head>

<meta charset="UTF-8">

<meta name="viewport" content="width=device-width, initial-scale=1.0">

<title>Ribbon — 让 Minecraft 更好看的 NeoForge 模组</title>

<style>

&#x20; :root {

&#x20;   --bg: #06060b;

&#x20;   --text: #eaeaf2;

&#x20;   --muted: #9a9ab0;

&#x20;   --muted-2: #5f5f75;

&#x20;   --accent: #b06ab3;

&#x20;   --accent-2: #4facfe;



&#x20;   /\* 液态玻璃 token \*/

&#x20;   --glass-bg-1: rgba(255, 255, 255, 0.09);

&#x20;   --glass-bg-2: rgba(255, 255, 255, 0.02);

&#x20;   --glass-border: rgba(255, 255, 255, 0.12);

&#x20;   --glass-highlight: rgba(255, 255, 255, 0.35);

&#x20;   --glass-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);

&#x20;   --glass-inner-top: inset 0 1px 0 rgba(255, 255, 255, 0.18);

&#x20;   --glass-inner-bottom: inset 0 -1px 0 rgba(255, 255, 255, 0.04);

&#x20;   --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);

&#x20; }



&#x20; \* { margin: 0; padding: 0; box-sizing: border-box; }

&#x20; html { scroll-behavior: smooth; }



&#x20; body {

&#x20;   background: var(--bg);

&#x20;   color: var(--text);

&#x20;   font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;

&#x20;   line-height: 1.6;

&#x20;   overflow-x: hidden;

&#x20;   -webkit-font-smoothing: antialiased;

&#x20; }



&#x20; /\* ================= 背景：缓慢飘动的彩色光球 ================= \*/

&#x20; .bg-orbs {

&#x20;   position: fixed;

&#x20;   inset: 0;

&#x20;   z-index: 0;

&#x20;   pointer-events: none;

&#x20;   overflow: hidden;

&#x20; }

&#x20; .orb {

&#x20;   position: absolute;

&#x20;   border-radius: 50%;

&#x20;   filter: blur(90px);

&#x20;   opacity: 0.55;

&#x20;   will-change: transform;

&#x20; }

&#x20; .orb-1 { width: 620px; height: 620px; background: #b06ab3; top: -180px; left: -120px; animation: drift-1 26s ease-in-out infinite; }

&#x20; .orb-2 { width: 540px; height: 540px; background: #4facfe; top: 30%; right: -180px; animation: drift-2 32s ease-in-out infinite; }

&#x20; .orb-3 { width: 480px; height: 480px; background: #47e891; bottom: -140px; left: 20%; animation: drift-3 29s ease-in-out infinite; }

&#x20; .orb-4 { width: 400px; height: 400px; background: #ff5f6d; top: 60%; left: -100px; animation: drift-2 24s ease-in-out infinite reverse; opacity: 0.35; }

&#x20; .orb-5 { width: 460px; height: 460px; background: #ffc371; bottom: 20%; right: 8%; animation: drift-1 34s ease-in-out infinite reverse; opacity: 0.3; }



&#x20; @keyframes drift-1 { 0%, 100% { transform: translate(0, 0) scale(1); } 33% { transform: translate(60px, 40px) scale(1.08); } 66% { transform: translate(-30px, 80px) scale(0.96); } }

&#x20; @keyframes drift-2 { 0%, 100% { transform: translate(0, 0) scale(1); } 50% { transform: translate(-80px, -60px) scale(1.1); } }

&#x20; @keyframes drift-3 { 0%, 100% { transform: translate(0, 0) scale(1); } 40% { transform: translate(50px, -50px) scale(1.06); } 70% { transform: translate(-60px, 30px) scale(0.94); } }



&#x20; body::after {

&#x20;   content: ""; position: fixed; inset: 0;

&#x20;   background: linear-gradient(180deg, rgba(6,6,11,0.55) 0%, transparent 25%, transparent 75%, rgba(6,6,11,0.6) 100%);

&#x20;   z-index: 1; pointer-events: none;

&#x20; }



&#x20; /\* ================= 液态玻璃核心 ================= \*/

&#x20; .glass {

&#x20;   position: relative;

&#x20;   background: linear-gradient(135deg, var(--glass-bg-1), var(--glass-bg-2));

&#x20;   backdrop-filter: blur(24px) saturate(180%);

&#x20;   -webkit-backdrop-filter: blur(24px) saturate(180%);

&#x20;   border: 1px solid var(--glass-border);

&#x20;   border-radius: 24px;

&#x20;   box-shadow: var(--glass-shadow), var(--glass-inner-top), var(--glass-inner-bottom);

&#x20; }

&#x20; .glass::before {

&#x20;   content: ""; position: absolute; inset: 0; border-radius: inherit; padding: 1px;

&#x20;   background: linear-gradient(135deg, var(--glass-highlight) 0%, transparent 28%, transparent 72%, rgba(255, 255, 255, 0.12) 100%);

&#x20;   -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#000 0 0);

&#x20;           mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#000 0 0);

&#x20;   -webkit-mask-composite: xor; mask-composite: exclude; pointer-events: none;

&#x20; }



&#x20; #glow {

&#x20;   position: fixed; width: 520px; height: 520px; border-radius: 50%;

&#x20;   background: radial-gradient(circle, rgba(176, 106, 179, 0.16), transparent 65%);

&#x20;   pointer-events: none; transform: translate(-50%, -50%); z-index: 2;

&#x20;   transition: opacity 0.4s; opacity: 0; mix-blend-mode: screen;

&#x20; }



&#x20; .ripple {

&#x20;   position: fixed; width: 14px; height: 14px; border-radius: 50%;

&#x20;   border: 2px solid; pointer-events: none; transform: translate(-50%, -50%);

&#x20;   animation: ripple-out 0.75s ease-out forwards; z-index: 9999;

&#x20; }

&#x20; @keyframes ripple-out { to { width: 140px; height: 140px; opacity: 0; border-width: 1px; } }



&#x20; .rainbow {

&#x20;   background: linear-gradient(90deg, #ff5f6d, #ffc371, #47e891, #4facfe, #b06ab3, #ff5f6d);

&#x20;   background-size: 300% 100%; -webkit-background-clip: text; background-clip: text;

&#x20;   -webkit-text-fill-color: transparent; animation: rainbow-shift 8s linear infinite;

&#x20; }

&#x20; @keyframes rainbow-shift { 0% { background-position: 0% 50%; } 100% { background-position: 300% 50%; } }



&#x20; nav {

&#x20;   position: fixed; top: 18px; left: 50%; transform: translateX(-50%); z-index: 100;

&#x20;   display: flex; align-items: center; gap: 32px; padding: 12px 26px; border-radius: 100px;

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.10), rgba(255,255,255,0.03));

&#x20;   backdrop-filter: blur(20px) saturate(180%); -webkit-backdrop-filter: blur(20px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.10);

&#x20;   box-shadow: 0 12px 40px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.2), inset 0 -1px 0 rgba(255,255,255,0.04);

&#x20;   transition: padding 0.3s var(--ease-spring), box-shadow 0.3s;

&#x20; }

&#x20; nav.scrolled { box-shadow: 0 20px 50px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.25), inset 0 -1px 0 rgba(255,255,255,0.05); }



&#x20; .logo { font-size: 19px; font-weight: 800; letter-spacing: -0.5px; }

&#x20; nav ul { display: flex; gap: 26px; list-style: none; }

&#x20; nav a { color: var(--muted); text-decoration: none; font-size: 14px; font-weight: 500; transition: color 0.2s; }

&#x20; nav a:hover { color: var(--text); }



&#x20; .hero {

&#x20;   position: relative; z-index: 2; min-height: 100vh; display: flex; flex-direction: column;

&#x20;   align-items: center; justify-content: center; text-align: center; padding: 140px 24px 100px;

&#x20; }

&#x20; .hero .badge {

&#x20;   display: inline-flex; align-items: center; gap: 8px; padding: 7px 16px; border-radius: 100px;

&#x20;   font-size: 13px; font-weight: 600; color: var(--text);

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.10), rgba(255,255,255,0.03));

&#x20;   backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);

&#x20;   border: 1px solid rgba(255,255,255,0.12); box-shadow: inset 0 1px 0 rgba(255,255,255,0.18); margin-bottom: 32px;

&#x20; }

&#x20; .badge .dot { width: 7px; height: 7px; border-radius: 50%; background: #47e891; box-shadow: 0 0 10px #47e891; animation: pulse 2s ease-in-out infinite; }

&#x20; @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }



&#x20; .hero h1 { font-size: clamp(64px, 14vw, 168px); font-weight: 900; letter-spacing: -0.05em; line-height: 0.95; margin-bottom: 28px; }

&#x20; .hero .subtitle { font-size: clamp(17px, 2vw, 21px); color: var(--text); max-width: 620px; margin-bottom: 14px; font-weight: 500; }

&#x20; .hero .tagline { font-size: 14px; color: var(--muted-2); margin-bottom: 44px; letter-spacing: 0.02em; }



&#x20; .btns { display: flex; gap: 14px; flex-wrap: wrap; justify-content: center; }

&#x20; .btn {

&#x20;   display: inline-flex; align-items: center; gap: 9px; padding: 15px 30px; border-radius: 100px;

&#x20;   font-size: 15px; font-weight: 600; text-decoration: none;

&#x20;   transition: transform 0.35s var(--ease-spring), box-shadow 0.3s, background 0.3s, border-color 0.3s;

&#x20;   cursor: pointer; position: relative; overflow: hidden;

&#x20; }

&#x20; .btn-primary {

&#x20;   color: #fff; background: linear-gradient(135deg, rgba(176,106,179,0.85), rgba(79,172,254,0.85));

&#x20;   backdrop-filter: blur(20px) saturate(180%); -webkit-backdrop-filter: blur(20px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.25);

&#x20;   box-shadow: 0 10px 36px rgba(176,106,179,0.35), inset 0 1px 0 rgba(255,255,255,0.35), inset 0 -1px 0 rgba(0,0,0,0.15);

&#x20; }

&#x20; .btn-primary:hover { transform: translateY(-3px) scale(1.02); box-shadow: 0 16px 48px rgba(176,106,179,0.55), inset 0 1px 0 rgba(255,255,255,0.45); }

&#x20; .btn-ghost {

&#x20;   color: var(--text); background: linear-gradient(135deg, rgba(255,255,255,0.09), rgba(255,255,255,0.02));

&#x20;   backdrop-filter: blur(20px) saturate(180%); -webkit-backdrop-filter: blur(20px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.12); box-shadow: inset 0 1px 0 rgba(255,255,255,0.18);

&#x20; }

&#x20; .btn-ghost:hover { background: linear-gradient(135deg, rgba(255,255,255,0.14), rgba(255,255,255,0.04)); transform: translateY(-3px) scale(1.02); }



&#x20; .scroll-hint {

&#x20;   position: absolute; bottom: 40px; left: 50%; transform: translateX(-50%);

&#x20;   color: var(--muted-2); font-size: 11px; letter-spacing: 0.28em; text-transform: uppercase;

&#x20;   animation: float 2.6s ease-in-out infinite;

&#x20; }

&#x20; @keyframes float { 0%, 100% { transform: translate(-50%, 0); opacity: 0.5; } 50% { transform: translate(-50%, 10px); opacity: 0.9; } }



&#x20; section { position: relative; z-index: 2; padding: 100px 40px; max-width: 1200px; margin: 0 auto; }

&#x20; .section-title { font-size: clamp(30px, 4vw, 46px); font-weight: 800; letter-spacing: -0.025em; margin-bottom: 16px; }

&#x20; .section-desc { color: var(--muted); font-size: 16px; max-width: 680px; margin-bottom: 56px; line-height: 1.75; }



&#x20; .intro-panel { padding: 48px; border-radius: 32px; display: grid; grid-template-columns: 1.4fr 1fr; gap: 48px; align-items: center; }

&#x20; .intro-panel h3 { font-size: 22px; font-weight: 700; margin-bottom: 16px; letter-spacing: -0.01em; }

&#x20; .intro-panel p { color: var(--muted); font-size: 15px; line-height: 1.8; margin-bottom: 14px; }

&#x20; .intro-panel p:last-child { margin-bottom: 0; }



&#x20; .stats { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

&#x20; .stat {

&#x20;   padding: 20px 22px; border-radius: 18px; background: linear-gradient(135deg, rgba(255,255,255,0.07), rgba(255,255,255,0.015));

&#x20;   border: 1px solid rgba(255,255,255,0.08); box-shadow: inset 0 1px 0 rgba(255,255,255,0.12);

&#x20; }

&#x20; .stat .num { font-size: 26px; font-weight: 800; letter-spacing: -0.03em; background: linear-gradient(135deg, #b06ab3, #4facfe); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; font-family: ui-monospace, "SF Mono", Menlo, monospace; }

&#x20; .stat .lbl { font-size: 12px; color: var(--muted); margin-top: 4px; letter-spacing: 0.02em; }



&#x20; .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(288px, 1fr)); gap: 18px; }

&#x20; .card {

&#x20;   position: relative; padding: 26px; border-radius: 24px; overflow: hidden;

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.075), rgba(255,255,255,0.015));

&#x20;   backdrop-filter: blur(22px) saturate(180%); -webkit-backdrop-filter: blur(22px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.09);

&#x20;   box-shadow: 0 12px 40px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.14), inset 0 -1px 0 rgba(255,255,255,0.03);

&#x20;   transition: transform 0.45s var(--ease-spring), box-shadow 0.35s, border-color 0.35s, background 0.35s;

&#x20;   opacity: 0; transform: translateY(28px); --mx: 50%; --my: 50%;

&#x20; }

&#x20; .card.in { opacity: 1; transform: translateY(0); }

&#x20; .card:hover { transform: translateY(-6px) scale(1.015); border-color: rgba(255,255,255,0.18); box-shadow: 0 24px 60px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.22), inset 0 -1px 0 rgba(255,255,255,0.05); }

&#x20; .card::before {

&#x20;   content: ""; position: absolute; inset: 0; border-radius: inherit; padding: 1px;

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.4) 0%, transparent 30%, transparent 70%, rgba(255,255,255,0.1) 100%);

&#x20;   -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#000 0 0);

&#x20;           mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#000 0 0);

&#x20;   -webkit-mask-composite: xor; mask-composite: exclude; pointer-events: none; transition: background 0.3s;

&#x20; }

&#x20; .card::after {

&#x20;   content: ""; position: absolute; inset: 0; border-radius: inherit;

&#x20;   background: radial-gradient(380px circle at var(--mx) var(--my), rgba(255,255,255,0.10), transparent 45%);

&#x20;   opacity: 0; transition: opacity 0.3s; pointer-events: none;

&#x20; }

&#x20; .card:hover::after { opacity: 1; }



&#x20; .card .icon {

&#x20;   display: inline-flex; align-items: center; justify-content: center; width: 56px; height: 56px;

&#x20;   border-radius: 17px; font-size: 26px; margin-bottom: 18px;

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.14), rgba(255,255,255,0.03));

&#x20;   border: 1px solid rgba(255,255,255,0.12);

&#x20;   box-shadow: inset 0 1px 0 rgba(255,255,255,0.25), inset 0 -1px 0 rgba(255,255,255,0.04), 0 4px 12px rgba(0,0,0,0.2);

&#x20; }

&#x20; .card h3 { font-size: 17px; font-weight: 700; margin-bottom: 8px; letter-spacing: -0.005em; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

&#x20; .card p { font-size: 14px; color: var(--muted); line-height: 1.7; }



&#x20; .tag { display: inline-block; padding: 2px 8px; border-radius: 100px; font-size: 10px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; vertical-align: middle; }

&#x20; .tag-new { color: #47e891; background: rgba(71,232,145,0.12); border: 1px solid rgba(71,232,145,0.3); box-shadow: 0 0 12px rgba(71,232,145,0.15); }

&#x20; .tag-beta { color: #ffc371; background: rgba(255,195,113,0.10); border: 1px solid rgba(255,195,113,0.28); }

&#x20; .tag-warn { color: #ff5f6d; background: rgba(255,95,109,0.10); border: 1px solid rgba(255,95,109,0.3); }



&#x20; .specs { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }

&#x20; .spec {

&#x20;   padding: 22px 24px; border-radius: 18px; background: linear-gradient(135deg, rgba(255,255,255,0.07), rgba(255,255,255,0.015));

&#x20;   backdrop-filter: blur(18px) saturate(180%); -webkit-backdrop-filter: blur(18px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.09); box-shadow: inset 0 1px 0 rgba(255,255,255,0.14), inset 0 -1px 0 rgba(255,255,255,0.03), 0 8px 24px rgba(0,0,0,0.25);

&#x20;   position: relative; overflow: hidden; transition: transform 0.4s var(--ease-spring), border-color 0.3s;

&#x20; }

&#x20; .spec:hover { transform: translateY(-3px); border-color: rgba(255,255,255,0.16); }

&#x20; .spec .label { font-size: 11px; color: var(--muted-2); text-transform: uppercase; letter-spacing: 0.14em; margin-bottom: 8px; }

&#x20; .spec .value { font-size: 16px; font-weight: 700; letter-spacing: -0.01em; }



&#x20; .cta {

&#x20;   position: relative; z-index: 2; max-width: 1200px; margin: 40px auto; padding: 64px 48px;

&#x20;   border-radius: 32px; text-align: center; overflow: hidden;

&#x20;   background: linear-gradient(135deg, rgba(255,255,255,0.09), rgba(255,255,255,0.02));

&#x20;   backdrop-filter: blur(26px) saturate(180%); -webkit-backdrop-filter: blur(26px) saturate(180%);

&#x20;   border: 1px solid rgba(255,255,255,0.12);

&#x20;   box-shadow: 0 20px 60px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.2), inset 0 -1px 0 rgba(255,255,255,0.04);

&#x20; }

&#x20; .cta::before {

&#x20;   content: ""; position: absolute; inset: -50%;

&#x20;   background: conic-gradient(from 0deg, transparent, rgba(176,106,179,0.15), transparent 30%, rgba(79,172,254,0.12), transparent 60%);

&#x20;   animation: rotate 20s linear infinite; pointer-events: none;

&#x20; }

&#x20; @keyframes rotate { to { transform: rotate(360deg); } }

&#x20; .cta > \* { position: relative; z-index: 1; }

&#x20; .cta h2 { font-size: clamp(26px, 4vw, 40px); font-weight: 800; letter-spacing: -0.02em; margin-bottom: 12px; }

&#x20; .cta p { color: var(--muted); margin-bottom: 30px; font-size: 15px; }



&#x20; footer {

&#x20;   position: relative; z-index: 2; text-align: center; padding: 60px 24px;

&#x20;   border-top: 1px solid rgba(255,255,255,0.06); margin-top: 80px;

&#x20; }

&#x20; footer p { color: var(--muted-2); font-size: 13px; margin-bottom: 8px; }

&#x20; footer a { color: var(--accent); text-decoration: none; transition: color 0.2s; }

&#x20; footer a:hover { color: var(--accent-2); }



&#x20; @media (max-width: 900px) { .intro-panel { grid-template-columns: 1fr; padding: 36px 28px; gap: 32px; } }

&#x20; @media (max-width: 720px) {

&#x20;   nav { padding: 10px 20px; gap: 20px; top: 12px; }

&#x20;   nav ul { gap: 16px; }

&#x20;   nav ul li:nth-child(1) { display: none; }

&#x20;   section { padding: 70px 20px; }

&#x20;   .scroll-hint { display: none; }

&#x20;   .cta { padding: 44px 26px; border-radius: 24px; }

&#x20;   .stats { grid-template-columns: 1fr 1fr; }

&#x20; }

&#x20; @media (prefers-reduced-motion: reduce) {

&#x20;   \*, \*::before, \*::after { animation-duration: 0.001ms !important; animation-iteration-count: 1 !important; transition-duration: 0.001ms !important; }

&#x20;   .orb { opacity: 0.35; }

&#x20; }

</style>

</head>

<body>



<div class="bg-orbs">

&#x20; <div class="orb orb-1"></div>

&#x20; <div class="orb orb-2"></div>

&#x20; <div class="orb orb-3"></div>

&#x20; <div class="orb orb-4"></div>

&#x20; <div class="orb orb-5"></div>

</div>



<div id="glow"></div>



<nav>

&#x20; <div class="logo rainbow">Ribbon</div>

&#x20; <ul>

&#x20;   <li><a href="#intro">关于</a></li>

&#x20;   <li><a href="#features">功能</a></li>

&#x20;   <li><a href="#specs">环境</a></li>

&#x20;   <li><a href="https://github.com/follish0s/ribbon-21.1-neoforge" target="\_blank" rel="noopener">GitHub</a></li>

&#x20; </ul>

</nav>



<div class="hero">

&#x20; <div class="badge">

&#x20;   <span class="dot"></span>

&#x20;   当前版本 0.20.7 · 1.21.1 NeoForge

&#x20; </div>

&#x20; <h1 class="rainbow">Ribbon</h1>

&#x20; <p class="subtitle">在 Embeddium 的性能之上，加一整套视觉、听觉与 HUD 增强</p>

&#x20; <p class="tagline">内置音乐播放器 · 彩虹准星 · 21种点击特效 · 动态光源 · 农历 · 零侵入渲染</p>

&#x20; <div class="btns">

&#x20;   <a class="btn btn-primary" href="https://github.com/follish0s/ribbon-21.1-neoforge/releases" target="\_blank" rel="noopener">

&#x20;     ↓ 下载 0.20.7

&#x20;   </a>

&#x20;   <a class="btn btn-ghost" href="https://github.com/follish0s/ribbon-21.1-neoforge" target="\_blank" rel="noopener">

&#x20;     查看源码

&#x20;   </a>

&#x20; </div>

&#x20; <div class="scroll-hint">向下滚动</div>

</div>



<section id="intro">

&#x20; <div class="intro-panel glass">

&#x20;   <div>

&#x20;     <h3>这是什么</h3>

&#x20;     <p>

&#x20;       Ribbon 是一个基于 <strong>Embeddium 21.1 分支</strong> fork 的客户端增强模组，

&#x20;       专为 <strong>Minecraft 1.21.1 + NeoForge</strong> 打造。

&#x20;     </p>

&#x20;     <p>

&#x20;       它保留了 Embeddium 全部的性能优化——区块渲染、实体与粒子剔除——

&#x20;       同时加入了一整套"让游戏更好看、更好玩"的功能。

&#x20;       从彩虹描边到内置 MP3 播放器，从伤害数字到农历显示，

&#x20;       所有功能都可以在设置里单独开关，互不干扰。

&#x20;     </p>

&#x20;     <p>

&#x20;       与 <strong>Iris 光影</strong>、<strong>Monocle</strong>、<strong>Distant Horizons</strong>

&#x20;       完全兼容，不破坏任何底层渲染管线。

&#x20;     </p>

&#x20;   </div>

&#x20;   <div class="stats">

&#x20;     <div class="stat">

&#x20;       <div class="num">20+</div>

&#x20;       <div class="lbl">独立功能模块</div>

&#x20;     </div>

&#x20;     <div class="stat">

&#x20;       <div class="num">9</div>

&#x20;       <div class="lbl">设置页面</div>

&#x20;     </div>

&#x20;     <div class="stat">

&#x20;       <div class="num">2</div>

&#x20;       <div class="lbl">语言实时切换</div>

&#x20;     </div>

&#x20;     <div class="stat">

&#x20;       <div class="num">0</div>

&#x20;       <div class="lbl">渲染核心修改</div>

&#x20;     </div>

&#x20;   </div>

&#x20; </div>

</section>



<section id="features">

&#x20; <h2 class="section-title">功能一览</h2>

&#x20; <p class="section-desc">

&#x20;   每个功能都有独立开关，可以在 Embeddium 视频设置的 Ribbon 页面里按需启用。

&#x20; </p>



&#x20; <div class="grid">

&#x20;   <div class="card">

&#x20;     <span class="icon">🎵</span>

&#x20;     <h3>内置音乐播放器 <span class="tag tag-new">新</span></h3>

&#x20;     <p>内置 MP3 播放器，无需安装外部依赖。把 <code>.mp3</code> 丢进 <code>.minecraft/ribbon\_music/</code> 即可。独立纯黑 UI，快捷键：<strong>P</strong> 界面 / <strong>K</strong> 播放 / <strong>, .</strong> 切歌。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">⚡</span>

&#x20;     <h3>FSR 1.0 超分辨率 <span class="tag tag-beta">开发中</span></h3>

&#x20;     <p>计划集成 EASU + RCAS 两 pass 渲染方案，在保持画面清晰度的同时大幅提升帧率，正在解决与 Iris 管线的兼容问题。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🎯</span>

&#x20;     <h3>视觉增强</h3>

&#x20;     <p>彩虹准星、彩虹方块描边（3色模式）、彩虹生命值（支持自定义颜色）、物品栏高亮。所有颜色平滑循环。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">💥</span>

&#x20;     <h3>伤害数字与特效</h3>

&#x20;     <p>命中实体飘出伤害数字。高达 <strong>21 种</strong>点击特效，以及丰富的命中粒子与节日粒子。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">📊</span>

&#x20;     <h3>全套 HUD 系统</h3>

&#x20;     <p>FPS/TPS 悬浮窗、光照等级 HUD、农历 HUD（含二十四节气与节日）。支持四角吸附与缩放调节。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🌙</span>

&#x20;     <h3>农历与节日</h3>

&#x20;     <p>在 HUD 上显示农历日期，包含闰月处理。节日当天还会显示专属徽章和倒计时。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🏔️</span>

&#x20;     <h3>体验优化</h3>

&#x20;     <p>OptiFine 风格的放大（按住 C）、RPG 风格生物群系标题、防抖伤害数字。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">💡</span>

&#x20;     <h3>动态光源 <span class="tag tag-beta">实验</span></h3>

&#x20;     <p>手持发光物品时照亮周围实体/粒子。支持平滑淡出、半径调节。注：目前不影响方块渲染以保持稳定。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🎬</span>

&#x20;     <h3>光影兼容检测</h3>

&#x20;     <p>自动识别 Canvas、Vulkan 等不兼容格式的光影包，在加载时拦截提示，防止游戏直接崩溃。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🌐</span>

&#x20;     <h3>中英双语</h3>

&#x20;     <p>整个 Ribbon 设置界面支持中英文实时切换，无需重启游戏。</p>

&#x20;   </div>



&#x20;   <div class="card">

&#x20;     <span class="icon">🛡️</span>

&#x20;     <h3>零侵入设计</h3>

&#x20;     <p>所有功能基于事件与轻量 Mixin 实现，不触碰渲染核心，与 Iris、Monocle、DH 完美共存。</p>

&#x20;   </div>

&#x20; </div>

</section>



<section id="specs">

&#x20; <h2 class="section-title">运行环境与须知</h2>

&#x20; <p class="section-desc">

&#x20;   Ribbon 基于 Embeddium 21.1 分支，请确保你的环境符合要求。

&#x20; </p>



&#x20; <div class="specs" style="margin-bottom: 30px;">

&#x20;   <div class="spec">

&#x20;     <div class="label">Minecraft</div>

&#x20;     <div class="value">1.21.1</div>

&#x20;   </div>

&#x20;   <div class="spec">

&#x20;     <div class="label">加载器</div>

&#x20;     <div class="value">NeoForge 21.1.259</div>

&#x20;   </div>

&#x20;   <div class="spec">

&#x20;     <div class="label">Java</div>

&#x20;     <div class="value">21</div>

&#x20;   </div>

&#x20;   <div class="spec">

&#x20;     <div class="label">光影</div>

&#x20;     <div class="value">Iris 1.8.12 + Monocle</div>

&#x20;   </div>

&#x20; </div>



&#x20; <div class="glass" style="padding: 24px; border-color: rgba(255,95,109,0.3);">

&#x20;   <h3 style="color: #ff5f6d; margin-bottom: 8px; font-size: 18px;">⚠️ 冲突警告</h3>

&#x20;   <p style="color: var(--muted); font-size: 15px;">

&#x20;     <strong>千万不要安装 Radium 模组！</strong> Radium 在 1.21.1 会导致 TrialSpawner 多线程竞态崩溃。请改用官方 Lithium（NeoForge 版）。同时，请不要同时安装 Embeddium 或 Sodium。

&#x20;   </p>

&#x20; </div>

</section>



<div class="cta">

&#x20; <h2 class="rainbow">准备好了吗</h2>

&#x20; <p>下载 Ribbon，把 Minecraft 变成你想要的样子。</p>

&#x20; <div class="btns">

&#x20;   <a class="btn btn-primary" href="https://github.com/follish0s/ribbon-21.1-neoforge/releases" target="\_blank" rel="noopener">

&#x20;     ↓ 下载最新版

&#x20;   </a>

&#x20;   <a class="btn btn-ghost" href="https://github.com/follish0s/ribbon-21.1-neoforge" target="\_blank" rel="noopener">

&#x20;     查看源码

&#x20;   </a>

&#x20; </div>

</div>



<footer>

&#x20; <p>Ribbon · 一个自己看着乐的小项目 · <a href="https://github.com/follish0s/ribbon-21.1-neoforge" target="\_blank" rel="noopener">GitHub</a></p>

&#x20; <p style="font-size: 11px;">基于 Embeddium 分支开发，遵循 LGPL-3.0-or-later 开源协议。</p>

</footer>



<script>

&#x20; const glow = document.getElementById('glow');

&#x20; let glowOn = false;

&#x20; document.addEventListener('mousemove', (e) => {

&#x20;   glow.style.left = e.clientX + 'px';

&#x20;   glow.style.top  = e.clientY + 'px';

&#x20;   if (!glowOn) { glow.style.opacity = '1'; glowOn = true; }

&#x20; });

&#x20; document.addEventListener('mouseleave', () => {

&#x20;   glow.style.opacity = '0'; glowOn = false;

&#x20; });



&#x20; const palette = \['#ff5f6d', '#ffc371', '#47e891', '#4facfe', '#b06ab3'];

&#x20; document.addEventListener('click', (e) => {

&#x20;   const r = document.createElement('div');

&#x20;   r.className = 'ripple';

&#x20;   r.style.left = e.clientX + 'px';

&#x20;   r.style.top  = e.clientY + 'px';

&#x20;   r.style.borderColor = palette\[Math.floor(Math.random() \* palette.length)];

&#x20;   document.body.appendChild(r);

&#x20;   setTimeout(() => r.remove(), 800);

&#x20; });



&#x20; const nav = document.querySelector('nav');

&#x20; window.addEventListener('scroll', () => {

&#x20;   nav.classList.toggle('scrolled', window.scrollY > 20);

&#x20; }, { passive: true });



&#x20; const observer = new IntersectionObserver((entries) => {

&#x20;   entries.forEach((entry, i) => {

&#x20;     if (entry.isIntersecting) {

&#x20;       setTimeout(() => entry.target.classList.add('in'), Math.min(i, 8) \* 55);

&#x20;       observer.unobserve(entry.target);

&#x20;     }

&#x20;   });

&#x20; }, { threshold: 0.08, rootMargin: '0px 0px -40px 0px' });

&#x20; document.querySelectorAll('.card').forEach(c => observer.observe(c));



&#x20; document.querySelectorAll('.card').forEach(card => {

&#x20;   card.addEventListener('mousemove', (e) => {

&#x20;     const r = card.getBoundingClientRect();

&#x20;     card.style.setProperty('--mx', (e.clientX - r.left) + 'px');

&#x20;     card.style.setProperty('--my', (e.clientY - r.top)  + 'px');

&#x20;   });

&#x20; });

</script>

</body>

</html>

