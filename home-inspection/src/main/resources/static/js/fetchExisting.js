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
    if (activeFieldButton) {
        const fieldId = activeFieldButton.dataset.id;
        save(`http://localhost:8080/api/fields/${fieldId}/note`, content);
    } else {
        console.log("No active field selected for note.");
    }
}

function save(URI, content){
    fetch(URI,
        {
            method: "POST",
            headers: { "Content-Type": "text/plain" },
            body: content
        }
    );
}