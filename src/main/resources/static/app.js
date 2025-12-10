function formatKb(bytes) {
    return (bytes / 1024).toFixed(2) + " KB";
}

const pdfInput            = document.getElementById("pdfInput");
const dropArea            = document.getElementById("dropArea");
const uploadText          = document.getElementById("uploadText");
const fileInfo            = document.getElementById("fileInfo");
const targetInput         = document.getElementById("targetKb");
const compressBtn         = document.getElementById("compressBtn");
const loadingEl           = document.getElementById("loading");
const loadingText         = document.getElementById("loadingText");
const progressBar         = document.getElementById("progressBar");
const resultEl            = document.getElementById("result");
const origSizeEl          = document.getElementById("origSize");
const targetSizeEl        = document.getElementById("targetSize");
const compressedSizeEl    = document.getElementById("compressedSize");
const downloadLink        = document.getElementById("downloadLink");

let selectedFile = null;
let progressInterval = null;

const MAX_BYTES = 8 * 1024 * 1024; // 8 MB limit

// ============================================================
// File Selection Handler
// ============================================================
function selectFile(file) {
    if (file.type !== "application/pdf") {
        alert("Please upload a valid PDF file.");
        return;
    }

    // FRONTEND 8MB LIMIT BLOCKER
    if (file.size > MAX_BYTES) {
        alert("Max file size is 8 MB. Please upload a smaller PDF.");
        pdfInput.value = "";
        selectedFile = null;
        uploadText.textContent = "Click or Drag & Drop PDF";
        fileInfo.classList.add("hidden");
        return;
    }

    // SUCCESS — SET FILE
    selectedFile = file;
    uploadText.textContent = file.name;
    fileInfo.textContent = `Selected: ${file.name} (${formatKb(file.size)})`;
    fileInfo.classList.remove("hidden");
}

// ============================================================
// File Input Change
// ============================================================
pdfInput.addEventListener("change", () => {
    if (!pdfInput.files || !pdfInput.files[0]) return;
    selectFile(pdfInput.files[0]);
});

// ============================================================
// Drag & Drop Events
// ============================================================
["dragenter", "dragover"].forEach(evt => {
    dropArea.addEventListener(evt, (e) => {
        e.preventDefault();
        dropArea.classList.add("dragover");
    });
});

["dragleave", "drop"].forEach(evt => {
    dropArea.addEventListener(evt, (e) => {
        e.preventDefault();
        dropArea.classList.remove("dragover");
    });
});

// Handle dropped file
dropArea.addEventListener("drop", (e) => {
    const files = e.dataTransfer.files;
    if (!files || !files[0]) return;

    const file = files[0];

    // PREVENT LARGE FILES BEFORE BACKEND
    if (file.size > MAX_BYTES) {
        alert("Max file size is 8 MB. Please upload a smaller PDF.");
        return;
    }

    selectFile(file);
});

// ============================================================
// Progress Bar Animation (Fake Progress)
// ============================================================
function startProgressAnimation() {
    progressBar.style.width = "0%";
    loadingText.textContent = "Compressing…";

    let progress = 0;
    progressInterval = setInterval(() => {
        progress += Math.random() * 8; // smooth animated increments
        if (progress > 90) progress = 90;
        progressBar.style.width = progress + "%";
    }, 120);
}

function finishProgressAnimation() {
    clearInterval(progressInterval);
    progressBar.style.width = "100%";
    loadingText.textContent = "Finalizing…";
}

// ============================================================
// Compress Button Handler
// ============================================================
compressBtn.addEventListener("click", async () => {

    if (!selectedFile) {
        alert("Please select a PDF file first.");
        return;
    }

    // FINAL CHECK BEFORE UPLOAD
    if (selectedFile.size > MAX_BYTES) {
        alert("Max file size is 8 MB. Please upload a smaller PDF.");
        return;
    }

    let targetKb = parseInt(targetInput.value, 10);
    if (isNaN(targetKb) || targetKb < 20) {
        alert("Enter a valid target size (minimum 20 KB).");
        return;
    }

    // Set UI loading state
    compressBtn.disabled = true;
    loadingEl.classList.remove("hidden");
    resultEl.classList.add("hidden");

    startProgressAnimation();

    try {
        const formData = new FormData();
        formData.append("file", selectedFile);

        const response = await fetch(`/api/pdf/compress?targetKb=${targetKb}`, {
            method: "POST",
            body: formData
        });

        // BACKEND ERROR HANDLING
        if (!response.ok) {
            finishProgressAnimation();

            if (response.status === 413) {
                alert("File too large. Max allowed is 8 MB.");
            } else if (response.status === 500) {
                alert("Server error during compression. Try increasing target KB.");
            } else {
                alert("Unexpected server error.");
            }

            return;
        }

        // SUCCESS
        finishProgressAnimation();

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);

        const baseName = selectedFile.name.replace(/\.pdf$/i, "");
        const finalName = `${baseName}_compressed.pdf`;

        downloadLink.href = url;
        downloadLink.download = finalName;

        // Show results
        origSizeEl.textContent = formatKb(selectedFile.size);
        targetSizeEl.textContent = `${targetKb} KB`;
        compressedSizeEl.textContent = formatKb(blob.size);

        setTimeout(() => {
            loadingEl.classList.add("hidden");
            resultEl.classList.remove("hidden");
        }, 600);

    } catch (err) {
        console.error("Error:", err);
        alert("Unexpected error occurred. Please try again.");
    }

    compressBtn.disabled = false;
});
