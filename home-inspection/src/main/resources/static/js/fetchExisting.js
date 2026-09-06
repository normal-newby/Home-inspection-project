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
    save(`http://localhost:8080/api/fields/${activeFieldButton.dataset.id}/note`, content);
}

function save(URI, content){
    fetch(URI,
        {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: content
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