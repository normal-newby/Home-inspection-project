import { confirmDialog, notify } from "../ui/dialog.js";
const URI = "http://localhost:8080/api/company-assets";

const assetKeySelect = document.getElementById("assetKey");
const assetFileInput = document.getElementById("assetFile");
const uploadButton = document.getElementById("uploadAssetBtn");
const assetList = document.getElementById("asset-list");

function createAssetItem(asset) {
    const assetItem = document.createElement("div");
    assetItem.classList.add("asset-item");
    assetItem.dataset.key = asset.key;

    const preview = document.createElement("img");
    preview.src = `${URI}/${asset.key}`;
    preview.alt = asset.key;
    preview.classList.add("asset-preview");

    const label = document.createElement("span");
    label.classList.add("asset-label");
    label.textContent = asset.key;

    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.textContent = "✕";
    removeBtn.classList.add("remove-btn");
    removeBtn.addEventListener("click", () => deleteAsset(asset.key));

    assetItem.appendChild(preview);
    assetItem.appendChild(label);
    assetItem.appendChild(removeBtn);
    assetList.appendChild(assetItem);
}

async function fetchAssets() {
    try {
        const response = await fetch(URI);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const assets = await response.json();

        assetList.innerHTML = '';
        assets.forEach(asset => createAssetItem(asset));
    } catch (error) {
        console.error('Error fetching assets:', error);
    }
}

async function uploadAsset() {
    const key = assetKeySelect.value;
    const file = assetFileInput.files[0];

    if (!key || !file) {
        notify("Pick an asset key and a file before uploading.", { error: true });
        return;
    }

    const formData = new FormData();
    formData.append("file", file);

    try {
        const res = await fetch(`${URI}/${key}`, {
            method: 'POST',
            body: formData
        });
        if (!res.ok) {
            throw new Error(`HTTP error! status: ${res.status}`);
        }

        assetFileInput.value = '';
        await fetchAssets();
    } catch (error) {
        console.error('Error uploading asset:', error);
        notify("Could not upload that asset. Please try again.", { error: true });
    }
}

async function deleteAsset(key) {
    const confirmed = await confirmDialog(
        "This image will be removed from every report that uses it.",
        { title: "Delete this asset?", confirmLabel: "Delete asset", danger: true }
    );
    if (!confirmed) return;

    fetch(`${URI}/${key}`, {
        method: 'DELETE'
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        fetchAssets();
    })
    .catch(error => console.error('Error deleting asset:', error));
}

uploadButton.addEventListener("click", uploadAsset);

fetchAssets();