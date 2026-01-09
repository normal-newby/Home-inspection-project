import { bookingId } from "./getReport.js";

const saveImagesButton = document.getElementById("save-images-button");
const imagesSection = document.querySelector(".images-section");

saveImagesButton.addEventListener("click", (e) => {
    e.preventDefault();

    console.log("hi");
    const input = document.getElementById("image-input");
    const files = input.files;

    if (files.length == 0){
        alert("Please select image");
        return;
    }

    const formData = new FormData();

    Array.from(files).forEach(image => {
        formData.append("files", image);
        formData.append("descriptions", "hi");
    });

    fetch(`http://localhost:8080/api/images/${bookingId}/upload`, {
        method: "POST",
        body: formData
    })
    .then(response => console.log(response))
    .catch(error => {
        console.log(error);
    });
});

function loadImages(){
    console.log("hi");
    imagesSection.innerHTML = "";
    fetch(`http://localhost:8080/api/images/${bookingId}/get`)
    .then(result => result.json())
    .then(images => {
        images.forEach(image => {
            const img = document.createElement("img");
            img.src = `http://localhost:8080/api/images/file/${image.id}`
            imagesSection.appendChild(img);
        });
    });
}

window.addEventListener("load", () => {
    loadImages();
});