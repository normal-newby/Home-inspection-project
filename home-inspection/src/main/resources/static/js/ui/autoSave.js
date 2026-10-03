import { confirmDialog } from "./dialog.js";

export function autoSaveOnLeave(form, save, { ignore } = {}){
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
        if (!await flush()) {
            const leaveAnyway = await confirmDialog("Your changes could not be saved and will be lost.",
                { title: "Leave without saving?", confirmLabel: "Leave", danger: true });
            if (!leaveAnyway) return;
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

    // A reload, a closed tab or a typed address cannot wait for a save, so warn instead.
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
