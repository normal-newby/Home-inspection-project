import { bookingId } from "./getReport.js";

const loadImagesButton = document.getElementById("load-images-button");

loadImagesButton.addEventListener("click", () => {
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
    .catch(error => {
        console.log(error);
    });
});