import { notify } from "./ui/dialog.js";

export function fetchExisting(URI, textArea){
    fetch(URI)
    .then(response => {
        if (!response.ok) return null; // If no note exists, return null
        return response.text();
    })
    .then(note => {
        if (note) {
            textArea.value = note;
        } else {
            textArea.value = "";
        }
    })
    .catch(error => console.log(error));
}

export function saveFunction(e, textArea){
    e.preventDefault();
    const content = textArea.value;
    const activeFieldButton = document.querySelector(".value-button.selected-button.current-button");
    if (!activeFieldButton) {
        notify("Select an item before saving a note.", { error: true });
        return;
    }
    return saveNote(activeFieldButton.dataset.id, content);
}

export function saveNote(fieldId, content){
    // keepalive lets a save started by clicking a link away from the page still land.
    return fetch(`/api/fields/${fieldId}/note`,
        {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: content,
            keepalive: true
        }
    )
    .then(response => {
        if (!response.ok) throw new Error(response.status);
        notify("Note saved");
    })
    .catch(error => {
        console.error(error);
        notify("Could not save the note.", { error: true });
    });
}