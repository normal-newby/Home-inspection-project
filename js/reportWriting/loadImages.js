import { bookingId } from "./getReport.js";

const saveImagesButton = document.getElementById("save-images-button");

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

const imagesSection = document.querySelector(".images-section");
const imagesTrack = document.querySelector(".images-track");
const nextButton = document.querySelector(".next");
const prevButton = document.querySelector(".prev");

const imageContainer = document.querySelector(".image-container");

let currentSlide = 0;
let totalSlides = 0;
let currentImageId = null;

function loopImages(images){ //appends images by 6s to a container
    for (let i = 0; i < images.length; i += 6){
        const slide = document.createElement("div");
        slide.className = "image-slide";

        images.slice(i, i+6).forEach(image => {
            const img = document.createElement("img");
            img.src = `http://localhost:8080/api/images/file/${image.id}`
            img.addEventListener("dblclick", (e) => imageClickFunction(e));
            slide.appendChild(img);
        });

        imagesTrack.appendChild(slide);
    }
    totalSlides = Math.ceil(images.length/6);
}

function imageClickFunction(e){
    showImagesDiv.hidden = false;
    const img = document.createElement("img");
    img.src = e.target.src;
    imageContainer.innerHTML = "";
    imageContainer.appendChild(img);
    currentImageId = img.src;
}

function loadImages(){
    imagesTrack.innerHTML = "";
    currentSlide = 0;

    fetch(`http://localhost:8080/api/images/${bookingId}/get`)
    .then(result => result.json())
    .then(images => loopImages(images))
    .catch(error => console.log(error));
}

function updateSlider() {
    imagesTrack.style.transform = `translateX(-${currentSlide * 100}%)`;
}

prevButton.addEventListener("click", () => {
    if (!(currentSlide > 0)) return;
    currentSlide--;
    updateSlider();
});

nextButton.addEventListener("click", () => {
    if (!(currentSlide < totalSlides-1)) return;
    currentSlide++;
    updateSlider();
});

const showImagesDiv = document.querySelector(".show-image-box");
const closeButton = document.querySelector(".close-button");

closeButton.addEventListener("click", () => {
    showImagesDiv.hidden = true;
});

document.addEventListener("click", (e) => {
    if (!showImagesDiv.contains(e.target)) {
        showImagesDiv.hidden = true;
    }
});

const deleteImageButton = document.querySelector(".delete-image-button");

deleteImageButton.addEventListener("click", (e) => {
    const parts = currentImageId.split("/");
    const imageUrl = parts[parts.length-1];
    console.log(imageUrl);

    fetch(`http://localhost:8080/api/images/${imageUrl}/delete`, {
        method: "DELETE"
    })
    .then(res => {
        if (res.ok){
            console.log("Deleted");
            showImagesDiv.hidden = true;
            loadImages();
        } else console.log("failed");
    });
});

window.addEventListener("load", () => {
    loadImages();
});