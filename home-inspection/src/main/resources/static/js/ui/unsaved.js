import { confirmDialog, askToSave, isOverlayClick } from "./dialog.js";

export function askToSaveOnLeave(form, save, { ignore } = {}){
    let dirty = false;
    let saving = null;

    const track = e => {
        if (!ignore || !e.target.closest(ignore)) dirty = true;
    };
    form.addEventListener("input", track);
    form.addEventListener("change", track);

    async function flush(){
        if (!dirty) return true;
        // A second click while saving waits on the same save rather than sending another.
        saving ??= Promise.resolve(save()).catch(() => false).finally(() => saving = null);
        const ok = await saving;
        if (ok) dirty = false;
        return ok;
    }

    async function leave(href){
        if (dirty) {
            const choice = await askToSave("The changes on this page have not been saved.");
            if (!choice) return;
            if (choice === "save" && !await flush()) {
                const leaveAnyway = await confirmDialog("Your changes could not be saved and will be lost.",
                    { title: "Leave without saving?", confirmLabel: "Leave", danger: true });
                if (!leaveAnyway) return;
            }
        }
        dirty = false;
        window.location.href = href;
    }

    document.addEventListener("click", e => {
        if (!dirty || e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
        const link = e.target.closest("a[href]");
        if (!link || link.target === "_blank" || link.getAttribute("href").startsWith("#")) return;
        e.preventDefault();
        leave(link.href);
    });

    // A reload, a closed tab or a typed address cannot wait for an answer, so warn instead.
    window.addEventListener("beforeunload", e => {
        if (dirty) e.preventDefault();
    });

    return {
        flush,
        leave,
        markDirty: () => { dirty = true; },
        markClean: () => { dirty = false; }
    };
}

/**
 * Holds back a click that would leave unsaved work behind, and lets it through once
 * confirm(target) resolves true. leavesWork(target) says whether clicking there leaves it.
 */
export function holdClickAway(leavesWork, confirm){
    document.addEventListener("click", async e => {
        // The replayed click below is untrusted, so it passes straight through.
        if (!e.isTrusted || isOverlayClick(e.target) || !leavesWork(e.target)) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        if (!await confirm(e.target)) return;

        let target = e.target;
        while (target && !(target instanceof HTMLElement)) target = target.parentElement; // e.g. an icon's <svg>
        target?.click();
    }, true);
}
