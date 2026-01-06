(function () {
    const THEME_KEY = 'theme';
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const root = document.documentElement;

    const getStoredTheme = () => {
        const value = localStorage.getItem(THEME_KEY);
        return value === 'light' || value === 'dark' ? value : null;
    };

    const getPreferredTheme = () => {
        const stored = getStoredTheme();
        if (stored) {
            return stored;
        }
        return mediaQuery.matches ? 'dark' : 'light';
    };

    const updateIcon = (theme, iconEl) => {
        if (!iconEl) return;
        const isDark = theme === 'dark';
        iconEl.classList.toggle('bi-moon-stars-fill', !isDark);
        iconEl.classList.toggle('bi-sun-fill', isDark);
    };

    const applyTheme = (theme, options = { persist: false, iconEl: null }) => {
        root.setAttribute('data-bs-theme', theme);
        updateIcon(theme, options.iconEl);
        if (options.persist) {
            localStorage.setItem(THEME_KEY, theme);
        }
    };

    const handleSystemChange = (event, iconEl) => {
        const stored = getStoredTheme();
        if (stored) return;
        const newTheme = event.matches ? 'dark' : 'light';
        applyTheme(newTheme, { persist: false, iconEl });
    };

    document.addEventListener('DOMContentLoaded', () => {
        const fab = document.getElementById('theme-fab');
        const iconEl = document.getElementById('theme-fab-icon');

        const initialTheme = getPreferredTheme();
        const hasStored = Boolean(getStoredTheme());
        applyTheme(initialTheme, { persist: false, iconEl });

        if (!hasStored && mediaQuery.addEventListener) {
            mediaQuery.addEventListener('change', (event) => handleSystemChange(event, iconEl));
        }

        if (fab) {
            fab.addEventListener('click', () => {
                const current = root.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
                const nextTheme = current === 'dark' ? 'light' : 'dark';
                applyTheme(nextTheme, { persist: true, iconEl });
            });
        }
    });
})();
