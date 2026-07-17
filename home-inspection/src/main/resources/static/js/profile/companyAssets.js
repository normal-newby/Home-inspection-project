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
        alert("Please select a key and a file to upload.");
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
        alert("Failed to upload asset. Please try again.");
    }
}

function deleteAsset(key) {
    if (!confirm("Are you sure you want to delete this asset?")) return;

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