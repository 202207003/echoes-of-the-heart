(() => {
    const forms = [...document.querySelectorAll('form[data-loading-title]')];
    const dialog = document.getElementById('request-loading');
    if (!forms.length || !dialog) return;
    const title = document.getElementById('request-loading-title');
    const description = document.getElementById('request-loading-description');
    const note = dialog.querySelector('.request-loading-note');
    const buttons = forms.flatMap(form => [...form.querySelectorAll('button[type="submit"]')]);
    let running = false;
    let timer;

    function reset() {
        running = false;
        clearTimeout(timer);
        buttons.forEach(button => { button.disabled = false; });
        forms.forEach(form => form.removeAttribute('aria-busy'));
        if (dialog.open && typeof dialog.close === 'function') dialog.close();
        dialog.removeAttribute('open');
    }

    window.addEventListener('pageshow', reset);
    dialog.addEventListener('cancel', event => { if (running) event.preventDefault(); });
    forms.forEach(form => form.addEventListener('submit', event => {
        event.preventDefault();
        if (running) return;
        running = true;
        title.textContent = form.dataset.loadingTitle;
        description.textContent = form.dataset.loadingDescription || '결과를 준비하고 있어요.';
        note.textContent = 'AI 응답에 따라 잠시 시간이 걸릴 수 있어요.';
        buttons.forEach(button => { button.disabled = true; });
        form.setAttribute('aria-busy', 'true');
        if (typeof dialog.showModal === 'function') dialog.showModal();
        else dialog.setAttribute('open', '');
        timer = setTimeout(() => {
            note.textContent = '평소보다 시간이 걸리고 있어요. 응답을 기다리는 중이에요.';
        }, 45000);
        // Let the browser paint the loading window before starting the page request.
        requestAnimationFrame(() => setTimeout(() => HTMLFormElement.prototype.submit.call(form), 80));
    }));
})();
