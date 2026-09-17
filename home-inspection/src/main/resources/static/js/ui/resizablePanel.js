// Used for resizing the inspection image and recommendation popups
const MIN_WIDTH = 360;
const MIN_HEIGHT = 220;
const VIEWPORT_MARGIN = 16;

const panels = []; // { panel, storageKey }

export function makeResizable(panel, storageKey){
    if (!panel || panel.dataset.resizable) return;
    panel.dataset.resizable = "true";

    const handle = document.createElement("div");
    handle.className = "panel-resize-handle";
    handle.title = "Drag to resize — double-click to reset";
    panel.appendChild(handle);

    panels.push({ panel, storageKey });
    restoreSize(panel, storageKey);

    handle.addEventListener("pointerdown", (e) => {
        e.preventDefault();
        e.stopPropagation();

        const { width: startWidth, height: startHeight } = panel.getBoundingClientRect();
        const startX = e.clientX;
        const startY = e.clientY;

        handle.setPointerCapture(e.pointerId);
        panel.classList.add("resizing");

        const onMove = (ev) => applySize(
            panel,
            // The panel is centred with translate(-50%, -50%), so we must double the change
            startWidth + (ev.clientX - startX) * 2,
            startHeight + (ev.clientY - startY) * 2
        );

        const onUp = () => {
            handle.removeEventListener("pointermove", onMove);
            handle.removeEventListener("pointerup", onUp);
            handle.removeEventListener("pointercancel", onUp);
            panel.classList.remove("resizing");
            saveSize(panel, storageKey);
        };

        handle.addEventListener("pointermove", onMove);
        handle.addEventListener("pointerup", onUp);
        handle.addEventListener("pointercancel", onUp);
    });

    handle.addEventListener("dblclick", (e) => {
        e.stopPropagation();
        panel.style.width = "";
        panel.style.height = "";
        panel.style.maxWidth = "";
        panel.style.maxHeight = "";
        panel.classList.remove("is-sized");
        try {
            localStorage.removeItem(storageKey);
        } catch {
        }
    });
}

function applySize(panel, width, height){
    // Centred panels can use the whole viewport, minus a margin so the edges stay visible.
    const maxWidth = window.innerWidth - VIEWPORT_MARGIN;
    const maxHeight = window.innerHeight - VIEWPORT_MARGIN;
    panel.style.width = `${clamp(width, MIN_WIDTH, maxWidth)}px`;
    panel.style.height = `${clamp(height, MIN_HEIGHT, maxHeight)}px`;
    panel.style.maxWidth = "none";
    panel.style.maxHeight = "none";
    panel.classList.add("is-sized");
}

function clamp(value, min, max){
    return Math.min(Math.max(value, min), Math.max(min, max));
}

function saveSize(panel, storageKey){
    try {
        localStorage.setItem(storageKey, JSON.stringify({
            width: parseFloat(panel.style.width),
            height: parseFloat(panel.style.height),
        }));
    } catch {    }
}

function restoreSize(panel, storageKey){
    let stored = null;
    try {
        stored = JSON.parse(localStorage.getItem(storageKey));
    } catch {
        return;
    }
    if (!stored || !Number.isFinite(stored.width) || !Number.isFinite(stored.height)) return;
    applySize(panel, stored.width, stored.height);
}

// A panel sized on a big screen must not hang off a smaller window.
window.addEventListener("resize", () => {
    panels.forEach(({ panel }) => {
        if (!panel.style.width && !panel.style.height) return;
        applySize(panel, parseFloat(panel.style.width), parseFloat(panel.style.height));
    });
});
