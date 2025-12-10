function formatKb(bytes) {
    return (bytes / 1024).toFixed(2) + " KB";
}

const pdfInput   = document.getElementById("pdfInput");
const dropArea   = document.getElementById("dropArea");
const uploadText = document.getElementById("uploadText");
const fileInfo   = document.getElementById("fileInfo");
const targetInput= document.getElementById("targetKb");
const compressBtn= document.getElementById("compressBtn");
const loadingEl  = document.getElementById("loading");
const resultEl   = document.getElementById("result");
const origSizeEl = document.getElementById("origSize");
const targetSizeEl = document.getElementById("targetSize");
const compressedSizeEl = document.getElementById("compressedSize");
const downloadLink = document.getElementById("downloadLink");

let selectedFile = null;

// File input change
pdfInput.addEventListener("change", () => {
    if (!pdfInput.files || !pdfInput.files[0]) return;
    selectedFile = pdfInput.files[0];
    uploadText.textContent = selectedFile.name;
    fileInfo.textContent = `Selected: ${selectedFile.name} (${formatKb(selectedFile.size)})`;
    fileInfo.classList.remove("hidden");
});

// Drag & drop behavior
["dragenter", "dragover"].forEach(evt => {
    dropArea.addEventListener(evt, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropArea.classList.add("dragover");
    });
});

["dragleave", "drop"].forEach(evt => {
    dropArea.addEventListener(evt, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropArea.classList.remove("dragover");
    });
});

dropArea.addEventListener("drop", (e) => {
    const files = e.dataTransfer.files;
    if (!files || !files[0]) return;
    if (files[0].type !== "application/pdf") {
        alert("Please drop a PDF file.");
        return;
    }
    selectedFile = files[0];
    pdfInput.files = files;
    uploadText.textContent = selectedFile.name;
    fileInfo.textContent = `Selected: ${selectedFile.name} (${formatKb(selectedFile.size)})`;
    fileInfo.classList.remove("hidden");
});

// Compress button
compressBtn.addEventListener("click", async () => {
    if (!selectedFile) {
        alert("Please select a PDF file first.");
        return;
    }

    let targetKb = parseInt(targetInput.value, 10);
    if (isNaN(targetKb) || targetKb < 20) {
        alert("Enter a valid target size (minimum 20 KB).");
        return;
    }

    // UI state
    compressBtn.disabled = true;
    loadingEl.classList.remove("hidden");
    resultEl.classList.add("hidden");

    try {
        const formData = new FormData();
        formData.append("file", selectedFile);

        const response = await fetch(`/api/pdf/compress?targetKb=${targetKb}`, {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            alert("Error compressing PDF. Please try a smaller file or higher target size.");
            return;
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);

        const baseName = selectedFile.name.replace(/\.pdf$/i, "");
        const finalName = `${baseName}_compressed.pdf`;

        downloadLink.href = url;
        downloadLink.download = finalName;

        origSizeEl.textContent = formatKb(selectedFile.size);
        targetSizeEl.textContent = targetKb + " KB";
        compressedSizeEl.textContent = formatKb(blob.size);

        resultEl.classList.remove("hidden");
    } catch (err) {
        console.error(err);
        alert("Unexpected error. Please try again later.");
    } finally {
        loadingEl.classList.add("hidden");
        compressBtn.disabled = false;
    }
});
