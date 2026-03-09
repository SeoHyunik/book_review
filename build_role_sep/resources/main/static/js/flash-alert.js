(function () {
    const FADE_DELAY = 5000;
    const REMOVE_DELAY = 900;

    const scheduleFadeOut = (alertEl) => {
        window.setTimeout(() => {
            alertEl.classList.add('fade-out');
            window.setTimeout(() => {
                alertEl.remove();
            }, REMOVE_DELAY);
        }, FADE_DELAY);
    };

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.flash-alert').forEach(scheduleFadeOut);
    });
})();
