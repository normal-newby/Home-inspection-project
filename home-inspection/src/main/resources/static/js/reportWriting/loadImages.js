import { bookingId } from "./getReport.js";

const saveImagesButton = document.getElementById("save-images-button");
const bodyDiv = document.querySelector(".body_content");

const showImagesDiv = document.querySelector(".show-image-box");
const imageContainer = document.querySelector(".image-container");

const progressBox = document.querySelector(".upload-progress");
const progressBar = document.querySelector(".upload-progress-bar");
const progressPercentage = document.querySelector(".upload-progress-percentage");

function saveImage(image) {
    const formData = new FormData();
    formData.append("file", image);

    return fetch(`http://localhost:8080/api/images/${bookingId}/upload`, {
        method: "POST",
        body: formData
    })
    .then(response => {
        invalidateImageCache();
        console.log(response);
    })
    .catch(error => {
        console.log(error);
    });
}

function setProgressBar(hide, percent) {
    progressBox.hidden = hide;
    progressBar.style.width = percent + "%";
    progressPercentage.textContent = percent;
}

function resetProgressBar() {
    setProgressBar(true, 0);
}

saveImagesButton.addEventListener("click", async (e) => {
    e.preventDefault();

    resetProgressBar();

    const input = document.getElementById("image-input");
    const files = input.files;

    if (files.length == 0){
        alert("Please select image");
        return;
    }

    const numberOfFiles = files.length;

    for (let i = 0; i < numberOfFiles; i++){
        await saveImage(files[i]);

        const percent = Math.round(((i+1) / numberOfFiles) * 100);
        console.log(percent);
        setProgressBar(false, percent);
    }

    initialize();
});

const imageCache = new Map();

export async function initImagesSlider(bookingId, container, getUsedForReport = false){
    const imagesTrack = container.querySelector(".images-track");
    const nextButton = container.querySelector(".next");
    const prevButton = container.querySelector(".prev");
    console.log("hi");

    let currentSlide = 0;
    let totalSlides = 0;

    function loopImages(images){ //appends images by 6s to a container
        imagesTrack.innerHTML = "";
        
        if (getUsedForReport) images = images.filter(image => !image.used); // If we only want images not used for report, filter them out

        for (let i = 0; i < images.length; i += 6){
            const slide = document.createElement("div");
            slide.className = "image-slide";

            images.slice(i, i+6).forEach(image => {
                const img = document.createElement("img");
                img.src = `http://localhost:8080/api/images/file/${image.id}`
                img.dataset.imageId = image.id;
                slide.appendChild(img);
            });

            imagesTrack.appendChild(slide);
        }
        totalSlides = Math.ceil(images.length/6);
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

    //Use cached images if available, otherwise fetch from server
    if (!imageCache.has(bookingId)){
        const response = await fetch(`http://localhost:8080/api/images/${bookingId}/get`);
        const images = await response.json();
        imageCache.set(bookingId, images);
    }

    loopImages(imageCache.get(bookingId));

    return imagesTrack;
}

export function invalidateImageCache(){
    imageCache.delete(bookingId);
}

let currentImageId = null;

async function initialize(){
    const track = await initImagesSlider(bookingId,bodyDiv);
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("dblclick", (e) => imageClickFunction(e));
    });
}

initialize();

function imageClickFunction(e){
    showImagesDiv.hidden = false;
    const img = document.createElement("img");
    img.src = e.target.src;
    imageContainer.innerHTML = "";
    imageContainer.appendChild(img);
    currentImageId = img.src;
}

const closeButton = document.querySelector(".close-button");

closeButton.addEventListener("click", () => {
    showImagesDiv.hidden = true;
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
            invalidateImageCache();
            console.log("Deleted");
            showImagesDiv.hidden = true;
        } else console.log("failed");
    });
});

document.addEventListener("click", (e) => {
    if (!showImagesDiv.contains(e.target)) {
        showImagesDiv.hidden = true;
    }
});
