import { bookingId } from "./getReport.js";

const saveImagesButton = document.getElementById("save-images-button");
const bodyDiv = document.querySelector(".body_content");

const progressBox = document.querySelector(".upload-progress");
const progressBar = document.querySelector(".upload-progress-bar");
const progressPercentage = document.querySelector(".upload-progress-percentage");
const progressLabel = progressBox?.querySelector(".upload-progress-label");
const defaultProgressLabelHTML = progressLabel?.innerHTML;

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

function setProgress(hide, percent, labelText) {
    if (!progressBox) return;
    progressBox.hidden = hide;
    progressBar.style.width = percent + "%";
    progressPercentage.textContent = percent;
    if (labelText != null && progressLabel) {
        progressLabel.innerHTML =
            `${labelText} <span class="upload-progress-percentage">${percent}</span> %`;
    }
}

function resetProgress() {
    setProgress(true, 0);
    if (progressLabel && defaultProgressLabelHTML != null) {
        progressLabel.innerHTML = defaultProgressLabelHTML;
    }
}

const UPLOAD_CONCURRENCY = 4;

saveImagesButton.addEventListener("click", async (e) => {
    e.preventDefault();
    resetProgress();

    const input = document.getElementById("image-input");
    const files = Array.from(input.files);
    if (files.length === 0){
        alert("Please select image");
        return;
    }

    const total = files.length;
    let done = 0;
    setProgress(false, 0, "Uploading...");

    // Parallel uploading
    let next = 0;
    async function worker(){
        while (true) {
            const i = next++;
            if (i >= total) return;
            await saveImage(files[i]);
            done++;
            setProgress(false, Math.round((done / total) * 100), "Uploading...");
        }
    }
    await Promise.all(Array.from({length: Math.min(UPLOAD_CONCURRENCY, total)}, worker));

    initialize();
});

// Metadata cache: bookingId -> image list (fetched from /api/images/{bookingId})
const imageCache = new Map();

function thumbUrl(imageId){
    return `http://localhost:8080/api/images/file/${imageId}/thumb`;
}

function fullUrl(imageId){
    return `http://localhost:8080/api/images/file/${imageId}`;
}

function invalidateImageCache(id = bookingId){
    imageCache.delete(id);
}

async function getImages(id = bookingId){
    if (!imageCache.has(id)){
        const response = await fetch(`http://localhost:8080/api/images/${id}`);
        imageCache.set(id, await response.json());
    }
    return imageCache.get(id);
}

function renderImageCounts(images){
    const box = document.querySelector(".image-counts");
    if (!box) return;

    const total = images.length;
    const remaining = images.filter(image => !image.used).length;

    box.querySelector(".image-count-total").textContent =
        `${total} ${total === 1 ? "image" : "images"}`;

    const remainingEl = box.querySelector(".image-count-remaining");
    remainingEl.textContent = remaining === 0 ? "all used" : `${remaining} remaining`;
    remainingEl.classList.toggle("none-left", remaining === 0);
}

export async function refreshImageCounts(){
    invalidateImageCache();
    renderImageCounts(await getImages());
}

// Preloads thumbnails
async function preloadThumbs(images){
    const total = images.length;
    if (total === 0) return;

    let done = 0;
    setProgress(false, 0, "Loading images...");

    await Promise.all(images.map(async (image) => {
        try {
            const img = new Image();
            img.src = thumbUrl(image.id);
            await img.decode().catch(() => {});
        } finally {
            done++;
            const percent = Math.round((done / total) * 100);
            setProgress(false, percent, "Loading images...");
        }
    }));

    resetProgress();
}

export async function initImagesSlider(bookingId, container, getUsedForReport = false, rows = 1){
    const imagesTrack = container.querySelector(".images-track");
    const nextButton = container.querySelector(".next");
    const prevButton = container.querySelector(".prev");

    const imagesPerSlide = rows * 6;

    let currentSlide = 0;
    let totalSlides = 0;

    function loopImages(images){
        imagesTrack.innerHTML = "";

        let imagesToRender = images;
        if (getUsedForReport) {
            imagesToRender = images.filter(image => !image.used);
        }

        for (let i = 0; i < imagesToRender.length; i += imagesPerSlide){
            const slide = document.createElement("div");
            slide.style.gridTemplateRows = `repeat(${rows}, 1fr)`;
            slide.className = "image-slide";

            imagesToRender.slice(i, i + imagesPerSlide).forEach(image => {
                const img = document.createElement("img");
                img.src = thumbUrl(image.id);
                img.dataset.imageId = image.id;
                img.loading = "lazy";
                img.decoding = "async";
                slide.appendChild(img);
            });

            imagesTrack.appendChild(slide);
        }
        totalSlides = Math.ceil(imagesToRender.length/imagesPerSlide);
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

    const images = await getImages(bookingId);
    await preloadThumbs(images);
    loopImages(images);
    renderImageCounts(images);

    return imagesTrack;
}

export { fullUrl as getFullImageUrl, thumbUrl as getThumbImageUrl };

async function initialize(){
    const track = await initImagesSlider(bookingId, bodyDiv, true);
    track.querySelectorAll("img").forEach(img => {
        img.addEventListener("contextmenu", (e) => deleteImage(e, img.dataset.imageId));
    });
}

initialize();

function deleteImage(e, imageId){
    e.preventDefault();
    if (!confirm("Are you sure you want to delete this image?")) return;

    fetch(`http://localhost:8080/api/images/${imageId}`, {
        method: "DELETE"
    })
    .then(res => {
        if (res.ok){
            invalidateImageCache();
            initialize();
        } else console.log("failed");
    });
}
