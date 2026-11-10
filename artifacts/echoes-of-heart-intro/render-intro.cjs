const fs = require('fs');
const path = require('path');
const http = require('http');
const { spawn } = require('child_process');
const { once } = require('events');
const { chromium } = require('playwright');
const ffmpeg = require('ffmpeg-static');
const root = process.cwd();
const work = path.join(root, 'build/video-work');
const output = path.join(root, 'artifacts/echoes-of-heart-intro');
fs.mkdirSync(work, { recursive: true });
fs.mkdirSync(output, { recursive: true });
const inputText = '바쁘게 달려온 하루, 오늘은 잠시 쉬어가고 싶다.';

function music() {
    const rate = 48000, length = rate * 25;
    const left = new Float64Array(length), right = new Float64Array(length);
    const frequency = midi => 440 * 2 ** ((midi - 69) / 12);
    function note(midi, start, duration, volume, pluck = false) {
        const f = frequency(midi), first = Math.floor(start * rate), count = Math.floor(duration * rate);
        for (let i = 0; i < count && first + i < length; i++) {
            const t = i / rate;
            const attack = Math.min(1, t / (pluck ? .025 : .9));
            const release = Math.min(1, (duration - t) / (pluck ? .7 : 1.7));
            const envelope = attack * release * (pluck ? Math.exp(-t * 1.5) : .7 + .3 * Math.sin(Math.PI * t / duration));
            const sample = (Math.sin(2 * Math.PI * f * t) + .22 * Math.sin(4 * Math.PI * f * t) + .06 * Math.sin(6 * Math.PI * f * t)) * volume * envelope;
            left[first + i] += sample;
            right[first + i] += sample * .93;
            for (const [delay, gain] of [[.23, .16], [.41, .09]]) {
                const position = first + i + Math.floor(delay * rate);
                if (position < length) { left[position] += sample * gain * .8; right[position] += sample * gain; }
            }
        }
    }
    const chords = [[48, 55, 60, 64, 71], [45, 52, 57, 60, 67], [41, 48, 53, 57, 64], [43, 50, 55, 60, 62]];
    chords.forEach((chord, index) => chord.forEach((pitch, voice) => note(pitch, index * 6.25 + voice * .08, 7, .05)));
    [76, 74, 72, 67, 69, 72, 76, 74, 72, 69, 67, 64, 67, 72].forEach((pitch, index) => note(pitch, 1.5 + index * 1.5, 2.4, .075, true));
    const wav = Buffer.alloc(44 + length * 4);
    wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8); wav.writeUInt32LE(16, 16);
    wav.writeUInt16LE(1, 20); wav.writeUInt16LE(2, 22); wav.writeUInt32LE(rate, 24); wav.writeUInt32LE(rate * 4, 28);
    wav.writeUInt16LE(4, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(length * 4, 40);
    let peak = 0;
    for (let i = 0; i < length; i++) peak = Math.max(peak, Math.abs(left[i]), Math.abs(right[i]));
    for (let i = 0; i < length; i++) {
        const fade = Math.min(1, i / rate / .8, (25 - i / rate) / 1.8);
        wav.writeInt16LE(Math.round(left[i] / peak * .6 * fade * 32767), 44 + i * 4);
        wav.writeInt16LE(Math.round(right[i] / peak * .6 * fade * 32767), 46 + i * 4);
    }
    fs.writeFileSync(path.join(work, 'soundtrack.wav'), wav);
}

const html = `<!DOCTYPE html><html lang="ko"><meta charset="utf-8"><style>
*{box-sizing:border-box;margin:0;padding:0}body{width:1920px;height:1080px;overflow:hidden;background:#faf8f5;color:#302d42;font-family:'Malgun Gothic',sans-serif}
.glow{position:absolute;width:1200px;height:1200px;border-radius:50%;background:radial-gradient(circle,#e9e0f3 0,transparent 67%);left:-400px;top:-450px}.ring{position:absolute;width:1050px;height:1050px;border:1px solid #e8e1ee;border-radius:50%;right:-240px;top:110px}
.brand{position:absolute;left:94px;top:64px;display:flex;align-items:center;gap:16px;font-size:25px;font-weight:700}.note{display:grid;place-items:center;background:#eee7f8;color:#7460bd;width:54px;height:54px;border-radius:18px;font-size:32px}.team{position:absolute;right:92px;top:80px;font-size:20px;color:#7460bd;letter-spacing:.05em}
.scene{position:absolute;inset:0;opacity:0}.copy{position:absolute;left:96px;top:294px;width:590px}.eyebrow{color:#7460bd;letter-spacing:.16em;font-size:19px;font-weight:700;margin-bottom:28px}h1{font-size:64px;line-height:1.5;letter-spacing:-.07em;font-weight:700;word-break:keep-all}h1 em{color:#7460bd;font-style:normal}.desc{font-size:24px;line-height:1.9;color:#706c80;margin-top:28px;word-break:keep-all}.tags{display:flex;gap:10px;margin-top:30px}.tag{font-size:16px;border:1px solid #dfd5eb;padding:10px 17px;border-radius:30px;color:#7460bd;background:#ffffff80}
.window{position:absolute;left:768px;top:151px;width:1060px;height:784px;border:1px solid #e3dceb;border-radius:24px;overflow:hidden;background:white;box-shadow:0 26px 80px #48316516}.chrome{height:44px;display:flex;align-items:center;justify-content:center;background:#f4f0f8;border-bottom:1px solid #e5deee;color:#8d849a;font-size:13px;letter-spacing:.04em}.dots{position:absolute;left:20px;top:17px;display:flex;gap:6px}.dots i{width:9px;height:9px;border-radius:50%;background:#c6b9d8}.viewport{height:740px;overflow:hidden;position:relative}.screen{display:block;position:absolute;width:1060px;height:740px;object-fit:cover;left:0;top:0}.cursor{position:absolute;width:27px;height:27px;border-radius:50%;background:#7460bd40;border:3px solid #7460bd;box-shadow:0 0 0 9px #7460bd12;opacity:0;left:1280px;top:800px;z-index:5}
.caption{position:absolute;bottom:64px;left:94px;width:1732px;text-align:center;color:#625477;font-size:23px;letter-spacing:-.025em}.footer{position:absolute;bottom:25px;left:96px;color:#a29aa9;font-size:12px;letter-spacing:.04em}.progress{position:absolute;left:96px;bottom:17px;height:2px;background:#a993cd;width:0;opacity:.65}
.intro-copy{position:absolute;left:140px;top:275px}.intro-copy h1{font-size:87px;line-height:1.5}.intro-copy .desc{font-size:26px}.art{position:absolute;left:1070px;top:240px;width:680px;height:520px}.photo{position:absolute;width:260px;height:360px;background:#fff;padding:14px;border:1px solid #e6dff0;border-radius:24px;box-shadow:0 20px 50px #44335e15}.photo .picture{width:230px;height:270px;border-radius:16px;background-image:url('/images/emotions/calm.png');background-size:300% 300%}.photo p{text-align:center;color:#766b87;font-size:16px;margin-top:16px}.photo:nth-child(1){left:0;top:100px;transform:rotate(-11deg)}.photo:nth-child(2){left:210px;top:10px;transform:rotate(3deg)}.photo:nth-child(3){left:420px;top:130px;transform:rotate(11deg)}.photo:nth-child(1) .picture{background-position:0 0}.photo:nth-child(2) .picture{background-position:50% 50%}.photo:nth-child(3) .picture{background-position:100% 100%}
.end{text-align:center;position:absolute;top:238px;left:0;width:1920px}.end .note{width:88px;height:88px;border-radius:28px;font-size:50px;margin:0 auto 36px}.end h1{font-size:66px}.wordmark{font-family:Georgia,serif;color:#7460bd;font-size:94px;letter-spacing:-.045em;margin:18px 0}.end p{font-size:24px;color:#706c80;line-height:1.8}.wave{display:flex;align-items:center;gap:6px;height:50px;margin-top:32px}.wave i{width:5px;background:#a38bcd;border-radius:4px;height:14px}
</style><div class="glow"></div><div class="ring"></div>
<div class="scene" id="intro"><div class="intro-copy"><div class="eyebrow">A MOMENT FOR YOUR HEART</div><h1>오늘의 마음을,<br><em>시와 음악으로.</em></h1><p class="desc">한 문장에서 시작되는<br>나만의 감성 기록</p></div><div class="art"><div class="photo"><div class="picture"></div><p>마음에 머무는 풍경</p></div><div class="photo"><div class="picture"></div><p>나를 닮은 한 편의 시</p></div><div class="photo"><div class="picture"></div><p>오늘을 위한 음악</p></div></div></div>
<div class="scene" id="input"><div class="copy"><div class="eyebrow">01 / 마음 쓰기</div><h1>한 문장으로<br>시작하는<br><em>마음 기록.</em></h1><p class="desc">길게 쓰지 않아도 괜찮아요.<br>지금 느끼는 감정을 남겨보세요.</p><div class="tags"><span class="tag">감정 입력</span><span class="tag">AI 감정 분석</span></div></div><div class="window"><div class="chrome"><span class="dots"><i></i><i></i><i></i></span>Music AI · 마음 쓰기</div><div class="viewport"><img class="screen" id="input-screen" src="/work/input-00.png"></div></div></div>
<div class="scene" id="mood"><div class="copy"><div class="eyebrow">02 / 분위기 고르기</div><h1>지금 마음에<br>가까운 풍경을<br><em>골라보세요.</em></h1><p class="desc">집, 나무, 사람.<br>추천 그림으로 바로 시작하거나<br>직접 골라도 좋아요.</p><div class="tags"><span class="tag">8가지 분위기</span><span class="tag">미리보기로 바로 분석</span></div></div><div class="window"><div class="chrome"><span class="dots"><i></i><i></i><i></i></span>Music AI · 분위기 고르기</div><div class="viewport"><img class="screen" id="mood-screen" src="/work/mood.png"></div></div></div>
<div class="scene" id="result"><div class="copy"><div class="eyebrow">03 / 시와 음악</div><h1>당신의 이야기,<br><em>시가 되고<br>음악이 됩니다.</em></h1><p class="desc">마음을 담은 시 한 편과<br>그 분위기에 어울리는 음악을<br>천천히 만나보세요.</p><div class="wave">${'<i></i>'.repeat(32)}</div></div><div class="window"><div class="chrome"><span class="dots"><i></i><i></i><i></i></span>Music AI · 당신을 위한 시와 음악</div><div class="viewport"><img class="screen" id="result-screen" src="/work/loading.png"></div></div></div>
<div class="scene" id="history"><div class="copy"><div class="eyebrow">04 / 마음 간직하기</div><h1>다시 꺼내보고<br>싶은 마음은<br><em>기록으로.</em></h1><p class="desc">로그인하고 결과를 저장하세요.<br>날짜별로 시와 음악을<br>다시 만나볼 수 있어요.</p><div class="tags"><span class="tag">결과 저장</span><span class="tag">나의 기록</span></div></div><div class="window"><div class="chrome"><span class="dots"><i></i><i></i><i></i></span>Music AI · 나의 기록</div><div class="viewport"><img class="screen" id="history-screen" src="/work/save.png"></div></div></div>
<div class="scene" id="outro"><div class="end"><div class="note">♫</div><h1>마음의 여운을 담다.</h1><div class="wordmark">echoes of heart</div><p>오늘의 마음을, 시와 음악으로.</p></div></div>
<div class="brand"><span class="note">♫</span>Music AI</div><div class="team">echoes of heart</div><div class="caption" id="caption"></div><div class="footer">예시 데이터로 구성한 프로젝트 시연 화면</div><div class="progress" id="progress"></div><div class="cursor" id="cursor"></div>
<script>
const ranges=[['intro',0,3,'오늘의 마음을, 시와 음악으로'],['input',3,7,'지금 느끼는 감정을 남겨보세요'],['mood',7,12,'마음에 가까운 풍경을 골라보세요'],['result',12,19,'당신의 이야기에 어울리는 시와 음악'],['history',19,22,'다시 꺼내보고 싶은 마음을 기록으로'],['outro',22,25,'마음의 여운을 담다 · echoes of heart']];
const clamp=x=>Math.min(1,Math.max(0,x));const smooth=x=>{x=clamp(x);return x*x*(3-2*x)};
window.renderAt=t=>{const active=ranges.find(r=>t>=r[1]&&t<r[2])||ranges[5];
for(const [id,start,end] of ranges){const el=document.getElementById(id);const a=smooth((t-start)/.38),b=smooth((end-t)/.28);el.style.opacity=t>=start&&t<end?a*b:0;const copy=el.querySelector('.copy,.intro-copy,.end');if(copy)copy.style.transform='translateY('+(1-a)*18+'px)';}
document.getElementById('caption').textContent=active[3];document.getElementById('progress').style.width=(t/25*1732)+'px';
document.getElementById('input-screen').src='/work/input-'+String(Math.min(${inputText.length},Math.floor(clamp((t-3.45)/2.2)*${inputText.length}))).padStart(2,'0')+'.png';
document.getElementById('mood-screen').src=t>11.25?'/work/loading.png':'/work/mood.png';
document.getElementById('result-screen').src=t<13.05?'/work/loading.png':t<16.2?'/work/poem.png':'/work/music.png';
document.getElementById('history-screen').src=t<20?'/work/save.png':'/work/history.png';
document.querySelectorAll('.wave i').forEach((bar,i)=>bar.style.height=(10+32*Math.abs(Math.sin(t*2.5+i*.52))*Math.abs(Math.cos(t+i*.15)))+'px');
const art=document.querySelector('.art');art.style.transform='translateY('+Math.sin(t*.8)*8+'px)';
const cursor=document.getElementById('cursor');const visible=(t>6.15&&t<6.8)||(t>10.65&&t<11.25)||(t>19.25&&t<19.9);cursor.style.opacity=visible?1:0;cursor.style.transform='scale('+(1+.12*Math.sin(t*12))+')';cursor.style.left=t>10&&t<12?'1515px':'1280px';cursor.style.top=t>19?'870px':t>10?'735px':'888px';
const fade=Math.min(smooth(t/.4),smooth((25-t)/.55));document.body.style.opacity=fade;};
</script></html>`;

const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/stage') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); return res.end(html); }
    const fixtureNames = { '/chat': 'chat', '/mood': 'mood-calm', '/result': 'result', '/history': 'history' };
    if (fixtureNames[url.pathname]) {
        const source = fs.readFileSync(path.join(root, 'build/ui-preview', fixtureNames[url.pathname] + '.html'), 'utf8');
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        return res.end(source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ''));
    }
    const base = url.pathname.startsWith('/work/') ? work : path.join(root, 'src/main/resources/static');
    const file = path.resolve(base, '.' + (url.pathname.startsWith('/work/') ? url.pathname.slice(5) : url.pathname));
    if (!file.startsWith(base + path.sep)) { res.statusCode = 403; return res.end(); }
    const types = { '.png': 'image/png', '.css': 'text/css', '.js': 'text/javascript', '.wav': 'audio/wav' };
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
    fs.readFile(file, (error, data) => { res.statusCode = error ? 404 : 200; res.end(error ? '' : data); });
});

(async () => {
    if (!fs.existsSync(ffmpeg)) throw new Error('FFmpeg executable not installed');
    music();
    fs.writeFileSync(path.join(work, 'composition.html'), html);
    console.log('PROGRESS 35: composition and original soundtrack ready');
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = `http://127.0.0.1:${server.address().port}`;
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1060, height: 740 }, deviceScaleFactor: 1 });
        await page.goto(base + '/chat');
        await page.evaluate(() => { scrollTo(0, 95); });
        for (let i = 0; i <= inputText.length; i++) {
            await page.locator('#poem-input').fill(inputText.slice(0, i));
            await page.screenshot({ path: path.join(work, `input-${String(i).padStart(2, '0')}.png`) });
        }
        console.log('PROGRESS 40: typing animation frames captured');
        await page.goto(base + '/mood');
        await page.evaluate(() => scrollTo(0, 100));
        await page.screenshot({ path: path.join(work, 'mood.png') });
        await page.evaluate(() => {
            const dialog = document.getElementById('analysis-loading');
            document.getElementById('analysis-stage').textContent = '당신의 마음을 담은 시를 쓰고 있어요';
            document.getElementById('analysis-percent').textContent = '35%';
            document.getElementById('analysis-progress-fill').style.width = '35%';
            dialog.showModal();
        });
        await page.screenshot({ path: path.join(work, 'loading.png') });
        await page.goto(base + '/result');
        await page.evaluate(() => scrollTo(0, 110));
        await page.screenshot({ path: path.join(work, 'poem.png') });
        await page.evaluate(() => {
            const img = document.querySelector('.thumbnail-img');
            const artwork = document.createElement('div');
            artwork.className = img.className;
            artwork.style.cssText = "margin:0 auto 22px;width:170px;height:170px;background:url('/images/emotions/calm.png') 50% 50%/300% 300%;border-radius:16px";
            img.replaceWith(artwork);
            const audio = document.querySelector('audio');
            audio.src = '/work/soundtrack.wav';
            audio.preload = 'auto';
            document.querySelector('.music-card').scrollIntoView({block:'start'});
        });
        await page.waitForTimeout(300);
        await page.screenshot({ path: path.join(work, 'music.png') });
        await page.evaluate(() => document.querySelector('.save-result').scrollIntoView({ block: 'center' }));
        await page.screenshot({ path: path.join(work, 'save.png') });
        await page.goto(base + '/history');
        await page.evaluate(() => scrollTo(0, 80));
        await page.screenshot({ path: path.join(work, 'history.png') });
        console.log('PROGRESS 45: actual UI scenes and input animation captured');
        await page.setViewportSize({ width: 1920, height: 1080 });
        await page.goto(base + '/stage');
        // Preload every screenshot before deterministic frame rendering.
        await page.evaluate(async names => {
            await Promise.all(names.map(name => new Promise((resolve, reject) => { const img = new Image(); img.onload = resolve; img.onerror = reject; img.src = '/work/' + name; })));
            await document.fonts.ready;
        }, fs.readdirSync(work).filter(name => name.endsWith('.png')));
        await page.evaluate(() => window.renderAt(1.5));
        await page.screenshot({ path: path.join(output, 'poster.png') });
        for (const [name, time] of [['input', 5.3], ['mood', 9], ['poem', 14.5], ['music', 17.4], ['history', 21], ['outro', 23.5]]) {
            await page.evaluate(t => window.renderAt(t), time);
            await page.screenshot({ path: path.join(work, 'review-' + name + '.png') });
        }
        console.log('PROGRESS 50: preview frames ready');
        if (process.argv.includes('--preview')) return;
        const movie = path.join(output, 'echoes-of-heart.mp4');
        const args = ['-y', '-hide_banner', '-loglevel', 'warning', '-f', 'image2pipe', '-framerate', '25', '-c:v', 'mjpeg', '-i', 'pipe:0', '-i', path.join(work, 'soundtrack.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', '25', '-c:a', 'aac', '-b:a', '192k', '-t', '25', '-movflags', '+faststart', '-metadata', 'title=echoes of heart — 오늘의 마음을, 시와 음악으로', movie];
        const encoder = spawn(ffmpeg, args, { windowsHide: true, stdio: ['pipe', 'ignore', 'pipe'] });
        let encoderError = '';
        encoder.stderr.on('data', data => encoderError += data.toString());
        const encoderExit = new Promise((resolve, reject) => { encoder.on('error', reject); encoder.on('exit', code => code === 0 ? resolve() : reject(new Error(encoderError))); });
        let lastProgress = 55;
        for (let frame = 0; frame < 625; frame++) {
            await page.evaluate(t => window.renderAt(t), frame / 25);
            const buffer = await page.screenshot({ type: 'jpeg', quality: 94, animations: 'disabled' });
            if (!encoder.stdin.write(buffer)) await once(encoder.stdin, 'drain');
            const progress = 55 + 5 * Math.floor((frame + 1) / 625 * 7);
            if (progress > lastProgress) { lastProgress = progress; console.log(`PROGRESS ${progress}: encoded ${frame + 1}/625 frames`); }
        }
        encoder.stdin.end();
        await encoderExit;
        fs.copyFileSync(path.join(work, 'soundtrack.wav'), path.join(output, 'original-background-music.wav'));
        const subtitles = [
            ['00:00:00,000', '00:00:03,000', '오늘의 마음을, 시와 음악으로'],
            ['00:00:03,000', '00:00:07,000', '지금 느끼는 감정을 남겨보세요'],
            ['00:00:07,000', '00:00:12,000', '마음에 가까운 풍경을 골라보세요'],
            ['00:00:12,000', '00:00:19,000', '당신의 이야기에 어울리는 시와 음악'],
            ['00:00:19,000', '00:00:22,000', '다시 꺼내보고 싶은 마음을 기록으로'],
            ['00:00:22,000', '00:00:25,000', '마음의 여운을 담다 · echoes of heart']
        ];
        fs.writeFileSync(path.join(output, 'captions.ko.srt'), subtitles.map((row, i) => `${i + 1}\n${row[0]} --> ${row[1]}\n${row[2]}\n`).join('\n'));
        console.log('PROGRESS 95: MP4, captions, and original music exported');
        console.log('VIDEO ' + movie);
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => server.close());
