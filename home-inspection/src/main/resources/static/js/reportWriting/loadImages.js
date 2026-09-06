import { bookingId } from "./getReport.js";
import { confirmDialog, notify } from "../ui/dialog.js";

const saveImagesButton = document.getElementById("save-images-button");
const imageInput = document.getElementById("image-input");
const fileCount = document.querySelector(".file-input-count");
const bodyDiv = document.querySelector(".body_content");

const progressBox = document.querySelector(".upload-progress");
const progressBar = document.querySelector(".upload-progress-bar");
const progressPercentage = document.querySelector(".upload-progress-percentage");
const progressLabel = progressBox?.querySelector(".upload-progress-label");
const defaultProgressLabelHTML = progressLabel?.innerHTML;

async function saveImage(image) {
    const formData = new FormData();
    formData.append("file", image);

    try {
        const response = await fetch(`http://localhost:8080/api/images/${bookingId}/upload`, {
            method: "POST",
            body: formData
        });
        if (!response.ok) {
            console.error(`Upload failed for ${image.name}: HTTP ${response.status}`);
            return false;
        }
        invalidateImageCache();
        return true;
    } catch (error) {
        console.error(`Upload failed for ${image.name}:`, error);
        return false;
    }
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
    progressBox?.classList.remove("upload-failed");
}

function showUploadFailure(failed, total) {
    if (!progressBox) return;
    progressBox.hidden = false;
    progressBox.classList.add("upload-failed");
    if (progressLabel) {
        progressLabel.textContent =
            `${failed} of ${total} ${total === 1 ? "image" : "images"} failed to upload — nothing was saved for those. Press Save Images to retry.`;
    }
}

// So it is clear how many photos the next Save Images will send.
function renderFileCount(){
    const count = imageInput.files.length;
    fileCount.hidden = count === 0;
    fileCount.textContent = `${count} ${count === 1 ? "image" : "images"} selected`;
}

imageInput.addEventListener("change", renderFileCount);
renderFileCount();

const UPLOAD_CONCURRENCY = 4;

saveImagesButton.addEventListener("click", async (e) => {
    e.preventDefault();
    resetProgress();

    const files = Array.from(imageInput.files);
    if (files.length === 0){
        notify("Pick at least one image first.", { error: true });
        return;
    }

    const total = files.length;
    let done = 0;
    let failed = 0;
    setProgress(false, 0, "Uploading...");

    // Parallel uploading
    let next = 0;
    async function worker(){
        while (true) {
            const i = next++;
            if (i >= total) return;
            if (!await saveImage(files[i])) failed++;
            done++;
            setProgress(false, Math.round((done / total) * 100), "Uploading...");
        }
    }
    await Promise.all(Array.from({length: Math.min(UPLOAD_CONCURRENCY, total)}, worker));

    await initialize();

    if (failed > 0) {
        showUploadFailure(failed, total);
    } else {
        resetProgress();
        imageInput.value = "";
        renderFileCount();
    }
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

// The used/unused split moved, so both the pool slider and its tally are stale.
export async function refreshImagePool(){
    invalidateImageCache();
    await initialize();
}

export async function initImagesGrid(bookingId, container){
    const grid = container.querySelector(".image-picker");
    const hint = container.querySelector(".image-picker-hint");
    const empty = container.querySelector(".image-picker-empty");

    const images = await getImages(bookingId);
    const unused = images.filter(image => !image.used);

    grid.innerHTML = "";
    unused.forEach(image => {
        const img = document.createElement("img");
        img.src = thumbUrl(image.id);
        img.dataset.imageId = image.id;
        img.loading = "lazy";
        img.decoding = "async";
        grid.appendChild(img);
    });

    grid.hidden = unused.length === 0;
    hint.hidden = unused.length === 0;
    empty.hidden = unused.length > 0;

    renderImageCounts(images);
    return grid;
}

const sliderControllers = new WeakMap();

// Only the main page pages through its images now; the field popup scrolls instead.
async function initImagesSlider(bookingId, container, getUsedForReport = false, rows = 1){
    const imagesTrack = container.querySelector(".images-track");
    const nextButton = container.querySelector(".next");
    const prevButton = container.querySelector(".prev");

    sliderControllers.get(container)?.abort();
    const controller = new AbortController();
    sliderControllers.set(container, controller);

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

        currentSlide = 0;
        updateSlider();
    }

    function updateSlider() {
        imagesTrack.style.transform = `translateX(-${currentSlide * 100}%)`;
    }

    prevButton.addEventListener("click", () => {
        if (!(currentSlide > 0)) return;
        currentSlide--;
        updateSlider();
    }, { signal: controller.signal });

    nextButton.addEventListener("click", () => {
        if (!(currentSlide < totalSlides-1)) return;
        currentSlide++;
        updateSlider();
    }, { signal: controller.signal });

    const images = await getImages(bookingId);
    // No preloading, fetches along the way
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

async function deleteImage(e, imageId){
    e.preventDefault();
    const confirmed = await confirmDialog(
        "The photo is deleted from this report for good, along with any annotations on it.",
        { title: "Delete this image?", confirmLabel: "Delete image", danger: true }
    );
    if (!confirmed) return;

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
