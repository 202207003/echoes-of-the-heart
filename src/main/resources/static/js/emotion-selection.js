(() => {
    const picker = document.getElementById('mood-picker');
    if (!picker) return;
    const positionKey = 'music-ai-mood-position';
    const selectionForm = document.getElementById('analysis-form');
    const summary = document.getElementById('selection-error-summary');
    const dialog = document.getElementById('selection-dialog');
    const useDialog = dialog && typeof dialog.showModal === 'function';

    if (selectionForm && summary) {
        const groups = [...selectionForm.querySelectorAll('.section[role="group"]')];
        function markGroup(group, invalid) {
            group.classList.toggle('is-invalid', invalid);
            group.querySelectorAll('input[type="radio"]').forEach(input => {
                if (invalid) {
                    input.setAttribute('aria-invalid', 'true');
                    input.setAttribute('aria-describedby', useDialog ? 'selection-dialog-description' : summary.id);
                } else {
                    input.removeAttribute('aria-invalid');
                    input.removeAttribute('aria-describedby');
                }
            });
        }
        function updateSummary() {
            const remaining = groups.filter(group => group.classList.contains('is-invalid')).length;
            summary.hidden = useDialog || remaining === 0;
            summary.textContent = remaining
                ? `아직 ${remaining}개 항목을 고르지 않았어요. 그림을 하나씩 고르거나 ‘맞는 그림이 없어요’를 선택해주세요.` : '';
        }
        function missingGroups() {
            return groups.filter(group => !group.querySelector('input[type="radio"]:checked'));
        }
        function focusFirstMissing() {
            const first = missingGroups()[0];
            if (!first) return;
            first.querySelector('input[type="radio"]').focus({ preventScroll: true });
            first.scrollIntoView({ behavior: 'instant', block: 'center' });
        }
        if (useDialog) {
            dialog.querySelector('.selection-dialog-close').addEventListener('click', () => dialog.close());
            document.getElementById('selection-dialog-choose').addEventListener('click', () => {
                dialog.close();
                focusFirstMissing();
            });
            document.getElementById('selection-dialog-skip').addEventListener('click', () => {
                const previewForm = document.getElementById('quick-analysis-form');
                missingGroups().forEach(group => {
                    const radios = [...group.querySelectorAll('input[type="radio"]')];
                    const previewValue = previewForm.elements.namedItem(radios[0].name)?.value;
                    const recommendation = radios.find(input => input.value === previewValue);
                    if (!recommendation) return;
                    recommendation.checked = true;
                    recommendation.dispatchEvent(new Event('change', { bubbles: true }));
                });
                dialog.close();
                selectionForm.requestSubmit();
            });
            window.addEventListener('pageshow', () => { if (dialog.open) dialog.close(); });
        }
        // Validate before the generation handler; native tooltips remain the no-JS fallback.
        selectionForm.addEventListener('submit', event => {
            const missing = missingGroups();
            groups.forEach(group => markGroup(group, missing.includes(group)));
            updateSummary();
            if (!missing.length) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (useDialog) {
                const list = document.getElementById('selection-missing-items');
                list.replaceChildren(...missing.map(group => {
                    const item = document.createElement('li');
                    item.textContent = group.querySelector('h2').textContent.replace('을 선택해주세요', '').trim();
                    return item;
                }));
                if (!dialog.open) dialog.showModal();
            } else focusFirstMissing();
        }, true);
        groups.forEach(group => group.addEventListener('change', () => {
            if (group.querySelector('input[type="radio"]:checked')) markGroup(group, false);
            updateSummary();
        }));
        selectionForm.noValidate = true;
    }

    // A normal form request keeps the server-rendered pictures and submitted IDs in sync.
    picker.addEventListener('change', () => {
        try {
            sessionStorage.setItem(positionKey, JSON.stringify({
                y: window.scrollY, mood: picker.value, savedAt: Date.now()
            }));
        } catch { /* The mood picker also works when storage is unavailable. */ }
        picker.form.requestSubmit();
    });

    window.addEventListener('pageshow', () => {
        try {
            const saved = JSON.parse(sessionStorage.getItem(positionKey));
            sessionStorage.removeItem(positionKey);
            if (saved && saved.mood === picker.value && Date.now() - saved.savedAt < 30000) {
                picker.focus({ preventScroll: true });
                window.scrollTo(0, saved.y);
            }
        } catch { /* Scrolling is optional; the selected mood is already rendered. */ }
    });
})();
