function formatKb(bytes) {
    return (bytes / 1024).toFixed(2) + " KB";
}
pdfInput.addEventListener("change", () => {
    const file = pdfInput.files[0];
    if (!file) return;

    document.getElementById("uploadText").innerText = file.name;
});

document.getElementById("compressBtn").addEventListener("click", async () => {
    const fileInput = document.getElementById("pdfInput");
    const targetInput = document.getElementById("targetKb");
    const loadingEl = document.getElementById("loading");
    const resultEl = document.getElementById("result");

    const file = fileInput.files[0];
    if (!file) {
        alert("Please select a PDF file.");
        return;
    }

    let targetKb = parseInt(targetInput.value);
    if (isNaN(targetKb) || targetKb < 20) {
        alert("Enter a valid target size.");
        return;
    }

    loadingEl.classList.remove("hidden");
    resultEl.classList.add("hidden");

    try {
        const formData = new FormData();
        formData.append("file", file);

        const res = await fetch(`/api/pdf/compress?targetKb=${targetKb}`, {
            method: "POST",
            body: formData
        });

        if (!res.ok) {
            alert("Error compressing file.");
            loadingEl.classList.add("hidden");
            return;
        }

        const blob = await res.blob();
        const url = URL.createObjectURL(blob);

        const fileName = file.name.replace(/\.pdf$/i, "");
        const finalName = fileName + "_compressed.pdf";

        const downloadLink = document.getElementById("downloadLink");
        downloadLink.href = url;
        downloadLink.download = finalName;

        document.getElementById("origSize").innerText = formatKb(file.size);
        document.getElementById("targetSize").innerText = targetKb + " KB";
        document.getElementById("compressedSize").innerText = formatKb(blob.size);

        loadingEl.classList.add("hidden");
        resultEl.classList.remove("hidden");

    } catch (e) {
        loadingEl.classList.add("hidden");
        alert("Unexpected error.");
    }
});
