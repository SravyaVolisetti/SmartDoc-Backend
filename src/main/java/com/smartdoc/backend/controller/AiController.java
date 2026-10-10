
package com.smartdoc.backend.controller;

import com.smartdoc.backend.service.OllamaAiService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final OllamaAiService aiService;

    public AiController(OllamaAiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/rename")
    public ResponseEntity<?> rename(@RequestParam("file") MultipartFile file) {
        try {
            String text = extractText(file);

            String result = aiService.generate(
                    "Suggest one short, descriptive filename for this document. "
                    + "Return only the filename without an extension.",
                    text
            );

            String name = result.split("\\R", 2)[0]
                    .replaceAll("[\\\\/:*?\"<>|]", "")
                    .replaceAll("[^a-zA-Z0-9 _.-]", "")
                    .trim()
                    .replaceAll("\\s+", "-");

            if (name.isBlank()) {
                name = "smartdoc-document";
            }

            Map<String, String> response = new LinkedHashMap<>();
            response.put("suggestedName", name + "." + getExtension(
                    file.getOriginalFilename()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/grammar")
    public ResponseEntity<?> grammar(@RequestParam("file") MultipartFile file) {
        return textOperation(file,
                "Correct grammar, spelling, and punctuation. "
                + "Preserve the original meaning and content.");
    }

    @PostMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam("file") MultipartFile file) {
        return textOperation(file,
                "Summarize this document clearly. Include its main points "
                + "and important details. Do not invent facts.");
    }

    @PostMapping("/format")
    public ResponseEntity<?> format(@RequestParam("file") MultipartFile file) {
        return textOperation(file,
                "Organize and format this text with clear headings, "
                + "paragraphs, and bullet points where appropriate. "
                + "Preserve its meaning.");
    }

    @PostMapping("/edit-pdf")
    public ResponseEntity<?> editPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("findText") String findText,
            @RequestParam("replaceText") String replaceText) {

        try {
            if (!"pdf".equals(getExtension(file.getOriginalFilename()))) {
                throw new IllegalArgumentException(
                        "Please select a PDF file.");
            }

            if (findText == null || findText.isEmpty()) {
                throw new IllegalArgumentException(
                        "Enter the exact text to replace.");
            }

            String text = extractText(file);

            if (!text.contains(findText)) {
                throw new IllegalArgumentException(
                        "The exact text was not found in the PDF.");
            }

            String editedText = text.replace(findText, replaceText);

            byte[] pdf = createPdf(editedText);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"edited-document.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception e) {
            return error(e);
        }
    }

    private ResponseEntity<?> textOperation(
            MultipartFile file, String instruction) {
        try {
            String documentText = extractText(file);
            String result = aiService.generate(instruction, documentText);

            Map<String, String> response = new LinkedHashMap<>();
            response.put("text", result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return error(e);
        }
    }

    private String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a document first.");
        }

        if (file.getSize() > 15L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Please use a document smaller than 15 MB.");
        }

        String extension = getExtension(file.getOriginalFilename());
        byte[] bytes = file.getBytes();

        switch (extension) {
            case "txt":
                return new String(bytes, StandardCharsets.UTF_8);

            case "pdf":
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    String text = new PDFTextStripper().getText(document);

                    if (text.isBlank()) {
                        throw new IllegalArgumentException(
                                "No selectable text found. Scanned PDFs "
                                + "need OCR, which is not included.");
                    }

                    return text;
                }

            case "docx":
                try (XWPFDocument document = new XWPFDocument(
                        new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor =
                        new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }

            default:
                throw new IllegalArgumentException(
                        "AI features support PDF, DOCX, and TXT files.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "txt";
        }

        return filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            float fontSize = 11;
            float margin = 50;
            float leading = 15;
            float pageHeight = PDRectangle.A4.getHeight();
            float maxWidth = PDRectangle.A4.getWidth() - 2 * margin;
            int linesPerPage = (int) (
                    (pageHeight - 2 * margin) / leading);

            List<String> lines = wrapText(
                    text, font, fontSize, maxWidth);

            if (lines.isEmpty()) {
                lines.add("");
            }

            for (int start = 0; start < lines.size();
                    start += linesPerPage) {

                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream stream =
                        new PDPageContentStream(document, page)) {

                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(
                            margin, pageHeight - margin);

                    int end = Math.min(
                            start + linesPerPage, lines.size());

                    for (int i = start; i < end; i++) {
                        String safeLine = lines.get(i).replaceAll(
                                "[^\\x20-\\x7E]", "?");

                        stream.showText(safeLine);
                        stream.newLineAtOffset(0, -leading);
                    }

                    stream.endText();
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private List<String> wrapText(
            String text, PDType1Font font,
            float fontSize, float maxWidth) throws IOException {

        List<String> lines = new ArrayList<>();

        for (String paragraph : text.replace("\r", "").split("\n", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }

            StringBuilder line = new StringBuilder();

            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty()
                        ? word : line + " " + word;

                String safeCandidate = candidate.replaceAll(
                        "[^\\x20-\\x7E]", "?");

                float width = font.getStringWidth(safeCandidate)
                        / 1000 * fontSize;

                if (width > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }

            lines.add(line.toString());
        }

        return lines;
    }

    private ResponseEntity<?> error(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        return ResponseEntity.internalServerError().body(
                "AI processing failed. Check that Ollama is running "
                + "and qwen2.5:3b is available. Details: "
                + e.getMessage());
    }
}
