(() => {
    const forms = [...document.querySelectorAll('form[data-generation-url]')];
    const dialog = document.getElementById('analysis-loading');
    if (!forms.length || !dialog || typeof dialog.showModal !== 'function' || !window.fetch) return;

    const buttons = forms.map(form => form.querySelector('[type="submit"]'));
    let activeButton = buttons[0];
    const stage = document.getElementById('analysis-stage');
    const percent = document.getElementById('analysis-percent');
    const bar = document.getElementById('analysis-progress-bar');
    const fill = document.getElementById('analysis-progress-fill');
    const wait = document.getElementById('analysis-wait');
    const retry = document.getElementById('analysis-retry');
    const steps = [...dialog.querySelectorAll('[data-threshold]')];
    let running = false;
    let timer;
    let currentPercent = 0;
    let activeProgressId;

    function update(value, message) {
        currentPercent = Math.max(currentPercent, Math.min(100, value));
        percent.textContent = `${currentPercent}%`;
        fill.style.width = `${currentPercent}%`;
        bar.setAttribute('aria-valuenow', currentPercent);
        stage.textContent = message;
        steps.forEach(step => {
            const active = currentPercent >= Number(step.dataset.threshold);
            step.classList.toggle('is-active', active);
        });
    }

    function reset() {
        running = false;
        activeProgressId = null;
        clearTimeout(timer);
        buttons.forEach(button => { button.disabled = false; });
        forms.forEach(form => form.removeAttribute('aria-busy'));
        if (dialog.open) dialog.close();
    }

    dialog.addEventListener('cancel', event => { if (running) event.preventDefault(); });
    retry.addEventListener('click', () => { reset(); activeButton.focus(); });
    window.addEventListener('pageshow', reset);

    forms.forEach(form => form.addEventListener('submit', async event => {
        event.preventDefault();
        if (running) return;
        activeButton = event.submitter || form.querySelector('[type="submit"]');
        const params = new URLSearchParams(new FormData(form));
        const progressId = window.crypto?.randomUUID?.()
            || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
        params.set('progressId', progressId);
        const generationUrl = new URL(form.dataset.generationUrl, location.href);
        generationUrl.search = params.toString();
        const progressUrl = new URL(form.dataset.progressUrl, location.href);
        progressUrl.searchParams.set('progressId', progressId);

        running = true;
        activeProgressId = progressId;
        currentPercent = 0;
        buttons.forEach(button => { button.disabled = true; });
        form.setAttribute('aria-busy', 'true');
        retry.hidden = true;
        wait.textContent = '';
        update(0, '분석 요청을 준비하고 있어요');
        dialog.showModal();
        const startedAt = Date.now();

        async function poll() {
            try {
                const response = await fetch(progressUrl, { cache: 'no-store', signal: AbortSignal.timeout(5000) });
                if (!response.ok) throw new Error('Progress unavailable');
                const progress = await response.json();
                if (!running || activeProgressId !== progressId) return;
                if (!progress.failed && Number.isFinite(progress.percent)) update(progress.percent, progress.message);
                wait.textContent = Date.now() - startedAt > 45000
                    ? '평소보다 시간이 걸리고 있어요. 응답을 기다리는 중이에요.' : '';
            } catch {
                if (running && activeProgressId === progressId) wait.textContent = '진행 상황을 다시 확인하고 있어요. 분석 응답을 기다려주세요.';
            }
            if (running && activeProgressId === progressId) timer = setTimeout(poll, 1000);
        }

        try {
            // Start the generation request before polling so it can establish its progress entry.
            const request = fetch(generationUrl, { cache: 'no-store', headers: { Accept: 'application/json' } });
            timer = setTimeout(poll, 500);
            const response = await request;
            const result = await response.json();
            if (!response.ok || !result.resultId) throw new Error(result.error || '분석을 완료하지 못했어요. 다시 시도해주세요.');
            running = false;
            clearTimeout(timer);
            update(100, '완료됐어요! 결과 화면으로 이동할게요');
            const resultUrl = new URL(form.dataset.resultUrl, location.href);
            resultUrl.searchParams.set('resultId', result.resultId);
            location.assign(resultUrl.href);
        } catch (error) {
            running = false;
            clearTimeout(timer);
            form.removeAttribute('aria-busy');
            stage.textContent = error instanceof TypeError || error instanceof SyntaxError
                ? '서버에 연결하지 못했어요. 연결 상태를 확인하고 다시 시도해주세요.' : error.message;
            wait.textContent = '선택한 그림과 입력 내용은 그대로 유지돼요.';
            retry.hidden = false;
            retry.focus();
        }
    }));
})();
