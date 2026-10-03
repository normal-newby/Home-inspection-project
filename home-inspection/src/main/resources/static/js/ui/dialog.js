// Replaces window.confirm and window.alert. Those block the whole page, cannot be styled,
// and in some browsers are suppressed entirely — which silently turned "are you sure?" into
// "yes". These render in the page and resolve a promise instead.

let openDialog = null;

function buildBackdrop(){
    const backdrop = document.createElement("div");
    backdrop.className = "app-dialog-backdrop";
    return backdrop;
}

/**
 * Asks the question in the page and resolves true only if the user confirms.
 * Escape, the Cancel button and a click on the backdrop all resolve false.
 */
export function confirmDialog(message, options = {}){
    const {
        title = "Are you sure?",
        confirmLabel = "Confirm",
        cancelLabel = "Cancel",
        danger = false
    } = options;

    return showDialog(message, title, [
        { label: cancelLabel, style: "secondary", value: false },
        { label: confirmLabel, style: danger ? "danger" : "primary", value: true }
    ], false);
}

// asks before saving
export function askToSave(message, { title = "Save changes?" } = {}){
    return showDialog(message, title, [
        { label: "Keep editing", style: "secondary", value: null },
        { label: "Don't save", style: "secondary", value: "discard" },
        { label: "Save", style: "primary", value: "save" }
    ], null);
}

function showDialog(message, title, choices, dismissValue){
    // A second question while one is open would strand the first promise.
    openDialog?.cancel();

    return new Promise(resolve => {
        const backdrop = buildBackdrop();

        const panel = document.createElement("div");
        panel.className = "app-dialog";
        panel.setAttribute("role", "dialog");
        panel.setAttribute("aria-modal", "true");

        const heading = document.createElement("h2");
        heading.className = "app-dialog-title";
        heading.textContent = title;

        const body = document.createElement("p");
        body.className = "app-dialog-message";
        body.textContent = message;

        const actions = document.createElement("div");
        actions.className = "app-dialog-actions";

        const buttons = choices.map(choice => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = `app-dialog-button ${choice.style}`;
            button.textContent = choice.label;
            button.addEventListener("click", () => close(choice.value));
            return button;
        });

        actions.append(...buttons);
        panel.append(heading, body, actions);
        backdrop.appendChild(panel);

        const previouslyFocused = document.activeElement;

        function close(result){
            document.removeEventListener("keydown", onKeyDown, true);
            backdrop.remove();
            openDialog = null;
            if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
            resolve(result);
        }

        function onKeyDown(e){
            if (e.key === "Escape"){
                e.preventDefault();
                e.stopPropagation();
                close(dismissValue);
                return;
            }
            // Keep tabbing inside the dialog while it is up.
            if (e.key === "Tab"){
                const index = buttons.indexOf(document.activeElement);
                if (index === -1) return;
                e.preventDefault();
                const next = e.shiftKey ? index - 1 : index + 1;
                buttons[(next + buttons.length) % buttons.length].focus();
            }
        }

        backdrop.addEventListener("mousedown", e => {
            if (e.target === backdrop) close(dismissValue);
        });
        document.addEventListener("keydown", onKeyDown, true);

        openDialog = { cancel: () => close(dismissValue) };
        document.body.appendChild(backdrop);
        buttons.at(-1).focus();
    });
}

// Prevents overlay from closing
export function isOverlayClick(target){
    return target instanceof Element && Boolean(target.closest(".app-dialog-backdrop, .app-toasts"));
}

function toastContainer(){
    let container = document.querySelector(".app-toasts");
    if (!container){
        container = document.createElement("div");
        container.className = "app-toasts";
        // Announced without stealing focus, so a background failure is still noticed.
        container.setAttribute("role", "status");
        container.setAttribute("aria-live", "polite");
        document.body.appendChild(container);
    }
    return container;
}

/** A message that does not need an answer. Replaces window.alert. */
export function notify(message, options = {}){
    const { error = false, timeout = 5000 } = options;

    const toast = document.createElement("div");
    toast.className = `app-toast ${error ? "error" : ""}`;
    toast.textContent = message;

    const dismiss = document.createElement("button");
    dismiss.type = "button";
    dismiss.className = "app-toast-dismiss";
    dismiss.setAttribute("aria-label", "Dismiss");
    dismiss.textContent = "×";
    dismiss.addEventListener("click", () => toast.remove());
    toast.appendChild(dismiss);

    toastContainer().appendChild(toast);
    if (timeout > 0) setTimeout(() => toast.remove(), timeout);
    return toast;
}
