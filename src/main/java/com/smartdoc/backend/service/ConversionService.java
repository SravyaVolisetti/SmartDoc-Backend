

package com.smartdoc.backend.service;



import com.smartdoc.backend.entity.Document;

import com.smartdoc.backend.repository.DocumentRepository;

import org.apache.pdfbox.Loader;

import org.apache.pdfbox.pdmodel.PDDocument;

import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import org.apache.pdfbox.pdmodel.font.PDType1Font;

import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import org.springframework.stereotype.Service;



import java.awt.Color;

import java.awt.Graphics2D;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;

import java.io.IOException;

import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.Paths;

import java.util.Locale;

import java.util.Map;

import java.util.Set;

import java.util.UUID;



@Service

public class ConversionService {



    private final DocumentRepository documentRepository;

    private final GridFsTemplate gridFsTemplate;



    private static final Set<String> IMAGE_FORMATS =

            Set.of("jpg", "png", "webp");



    public ConversionService(

            DocumentRepository documentRepository,

            GridFsTemplate gridFsTemplate) {

        this.documentRepository = documentRepository;

        this.gridFsTemplate = gridFsTemplate;

    }



    private Document getDocument(String id, String ownerEmail) {
        String normalizedEmail = normalizeEmail(ownerEmail);
        return documentRepository.findByIdAndOwnerEmail(id, normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Path getInputPath(Document document) throws IOException {

        Path inputPath = Paths.get(document.getFilePath())

                .toAbsolutePath()

                .normalize();



        if (!Files.isRegularFile(inputPath)) {

            throw new IOException("Original file not found: " + inputPath);

        }



        return inputPath;

    }



    public byte[] convertTxtToPdf(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"TXT".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only TXT files can be converted to PDF.");

        }



        String content = Files.readString(

                getInputPath(document), StandardCharsets.UTF_8);



        byte[] convertedBytes;



        try (PDDocument pdf = new PDDocument();

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            PDType1Font font = new PDType1Font(

                    Standard14Fonts.FontName.COURIER);



            float fontSize = 12;

            float margin = 50;

            float lineHeight = 20;

            float pageHeight = PDRectangle.A4.getHeight();

            float y = pageHeight - margin - fontSize;



            PDPage page = new PDPage(PDRectangle.A4);

            pdf.addPage(page);

            PDPageContentStream stream = new PDPageContentStream(pdf, page);



            try {

                for (String line : content.split("\\R", -1)) {

                    if (y < margin) {

                        stream.close();

                        page = new PDPage(PDRectangle.A4);

                        pdf.addPage(page);

                        stream = new PDPageContentStream(pdf, page);

                        y = pageHeight - margin - fontSize;

                    }



                    if (!line.isEmpty()) {

                        stream.beginText();

                        stream.setFont(font, fontSize);

                        stream.newLineAtOffset(margin, y);

                        stream.showText(line);

                        stream.endText();

                    }



                    y -= lineHeight;

                }

            } finally {

                stream.close();

            }



            pdf.save(output);

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.pdf", "PDF", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertPdfToTxt(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"PDF".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only PDF files can be converted to TXT.");

        }



        byte[] convertedBytes;



        try (PDDocument pdf = Loader.loadPDF(getInputPath(document).toFile());

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            String text = new PDFTextStripper().getText(pdf);

            output.write(text.getBytes(StandardCharsets.UTF_8));

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.txt", "TXT", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertImageToPdf(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);

        String fileType = document.getFileType();



        if (fileType == null ||

                !(fileType.equalsIgnoreCase("JPG")

                        || fileType.equalsIgnoreCase("JPEG")

                        || fileType.equalsIgnoreCase("PNG")

                        || fileType.equalsIgnoreCase("WEBP"))) {

            throw new IllegalArgumentException(

                    "Only JPG, JPEG, PNG, and WEBP images can be converted to PDF.");

        }



        BufferedImage bufferedImage =

                ImageIO.read(getInputPath(document).toFile());



        if (bufferedImage == null) {

            throw new IOException("Unable to read the image file.");

        }



        byte[] convertedBytes;



        try (PDDocument pdf = new PDDocument();

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            PDPage page = new PDPage(PDRectangle.A4);

            pdf.addPage(page);



            PDImageXObject image =

                    LosslessFactory.createFromImage(pdf, bufferedImage);



            float pageWidth = page.getMediaBox().getWidth();

            float pageHeight = page.getMediaBox().getHeight();

            float margin = 40;



            float scale = Math.min(

                    (pageWidth - 2 * margin) / image.getWidth(),

                    (pageHeight - 2 * margin) / image.getHeight());



            float imageWidth = image.getWidth() * scale;

            float imageHeight = image.getHeight() * scale;

            float x = (pageWidth - imageWidth) / 2;

            float y = (pageHeight - imageHeight) / 2;



            try (PDPageContentStream stream =

                         new PDPageContentStream(pdf, page)) {

                stream.drawImage(image, x, y, imageWidth, imageHeight);

            }



            pdf.save(output);

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.pdf", "PDF", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertDocxToTxt(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"DOCX".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only DOCX files can be converted to TXT.");

        }



        StringBuilder text = new StringBuilder();



        try (InputStream input = Files.newInputStream(getInputPath(document));

             XWPFDocument wordDocument = new XWPFDocument(input)) {



            for (XWPFParagraph paragraph : wordDocument.getParagraphs()) {

                text.append(paragraph.getText())

                        .append(System.lineSeparator());

            }

        }



        byte[] convertedBytes = text.toString().getBytes(StandardCharsets.UTF_8);

        saveConvertedFile(convertedBytes, "converted-document.txt", "TXT", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertTxtToDocx(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"TXT".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only TXT files can be converted to DOCX.");

        }



        String content = Files.readString(

                getInputPath(document), StandardCharsets.UTF_8);



        byte[] convertedBytes;



        try (XWPFDocument wordDocument = new XWPFDocument();

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            for (String line : content.split("\\R", -1)) {

                XWPFParagraph paragraph = wordDocument.createParagraph();

                paragraph.createRun().setText(line);

            }



            wordDocument.write(output);

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.docx", "DOCX", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertDocxToPdf(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"DOCX".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only DOCX files can be converted to PDF.");

        }



        StringBuilder text = new StringBuilder();



        try (InputStream input = Files.newInputStream(getInputPath(document));

             XWPFDocument wordDocument = new XWPFDocument(input)) {



            for (XWPFParagraph paragraph : wordDocument.getParagraphs()) {

                text.append(paragraph.getText()).append("\n");

            }

        }



        byte[] convertedBytes;



        try (PDDocument pdf = new PDDocument();

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            PDType1Font font = new PDType1Font(

                    Standard14Fonts.FontName.HELVETICA);



            float fontSize = 11;

            float margin = 50;

            float lineHeight = 16;

            float pageWidth = PDRectangle.A4.getWidth();

            float pageHeight = PDRectangle.A4.getHeight();

            float maxWidth = pageWidth - 2 * margin;



            PDPage page = new PDPage(PDRectangle.A4);

            pdf.addPage(page);

            PDPageContentStream stream = new PDPageContentStream(pdf, page);

            float y = pageHeight - margin;



            try {

                for (String paragraph : text.toString().split("\\R", -1)) {

                    String[] words = paragraph.split("\\s+");

                    StringBuilder line = new StringBuilder();



                    for (String word : words) {

                        if (word.isEmpty()) {

                            continue;

                        }



                        String candidate = line.isEmpty()

                                ? word : line + " " + word;



                        float width = font.getStringWidth(candidate)

                                / 1000 * fontSize;



                        if (width > maxWidth && !line.isEmpty()) {

                            if (y < margin) {

                                stream.close();

                                page = new PDPage(PDRectangle.A4);

                                pdf.addPage(page);

                                stream = new PDPageContentStream(pdf, page);

                                y = pageHeight - margin;

                            }



                            stream.beginText();

                            stream.setFont(font, fontSize);

                            stream.newLineAtOffset(margin, y);

                            stream.showText(line.toString());

                            stream.endText();



                            y -= lineHeight;

                            line = new StringBuilder(word);

                        } else {

                            line = new StringBuilder(candidate);

                        }

                    }



                    if (y < margin) {

                        stream.close();

                        page = new PDPage(PDRectangle.A4);

                        pdf.addPage(page);

                        stream = new PDPageContentStream(pdf, page);

                        y = pageHeight - margin;

                    }



                    if (!line.isEmpty()) {

                        stream.beginText();

                        stream.setFont(font, fontSize);

                        stream.newLineAtOffset(margin, y);

                        stream.showText(line.toString());

                        stream.endText();

                    }



                    y -= lineHeight;

                }

            } finally {

                stream.close();

            }



            pdf.save(output);

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.pdf", "PDF", ownerEmail);

        return convertedBytes;

    }



    public byte[] convertPdfToDocx(String id, String ownerEmail) throws IOException {

        Document document = getDocument(id, ownerEmail);



        if (!"PDF".equalsIgnoreCase(document.getFileType())) {

            throw new IllegalArgumentException(

                    "Only PDF files can be converted to DOCX.");

        }



        String text;



        try (PDDocument pdf = Loader.loadPDF(getInputPath(document).toFile())) {

            text = new PDFTextStripper().getText(pdf);

        }



        byte[] convertedBytes;



        try (XWPFDocument wordDocument = new XWPFDocument();

             ByteArrayOutputStream output = new ByteArrayOutputStream()) {



            for (String line : text.split("\\R", -1)) {

                XWPFParagraph paragraph = wordDocument.createParagraph();

                paragraph.createRun().setText(line);

            }



            wordDocument.write(output);

            convertedBytes = output.toByteArray();

        }



        saveConvertedFile(convertedBytes, "converted-document.docx", "DOCX", ownerEmail);

        return convertedBytes;

    }



    // JPG <-> PNG and JPG/PNG <-> WEBP image conversions.

    public byte[] convertImageFormat(String id, String ownerEmail, String targetFormat)

            throws IOException {



        Document document = getDocument(id, ownerEmail);



        String sourceFormat = normalizeImageFormat(document.getFileType());

        String outputFormat = normalizeImageFormat(targetFormat);



        if (!IMAGE_FORMATS.contains(sourceFormat)) {

            throw new IllegalArgumentException(

                    "The source must be a JPG, PNG, or WEBP image.");

        }



        if (!IMAGE_FORMATS.contains(outputFormat)) {

            throw new IllegalArgumentException(

                    "The output format must be JPG, PNG, or WEBP.");

        }



        if (sourceFormat.equals(outputFormat)) {

            throw new IllegalArgumentException(

                    "The source and output formats are the same.");

        }



        BufferedImage original =

                ImageIO.read(getInputPath(document).toFile());



        if (original == null) {

            throw new IOException("Unable to read the image file.");

        }



        BufferedImage imageToWrite = original;



        // JPG has no alpha channel; flatten transparent pixels onto white.

        if (outputFormat.equals("jpg")) {

            imageToWrite = new BufferedImage(

                    original.getWidth(),

                    original.getHeight(),

                    BufferedImage.TYPE_INT_RGB);



            Graphics2D graphics = imageToWrite.createGraphics();

            try {

                graphics.setColor(Color.WHITE);

                graphics.fillRect(

                        0, 0, original.getWidth(), original.getHeight());

                graphics.drawImage(original, 0, 0, null);

            } finally {

                graphics.dispose();

            }

        }



        String imageIoFormat =

                outputFormat.equals("jpg") ? "jpeg" : outputFormat;



        if (!ImageIO.getImageWritersByFormatName(imageIoFormat).hasNext()) {

            throw new IOException(

                    "No image writer is available for "

                    + outputFormat.toUpperCase(Locale.ROOT)

                    + ". Verify the WebP dependency and restart the application.");

        }



        byte[] convertedBytes;



        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            boolean written = ImageIO.write(

                    imageToWrite, imageIoFormat, output);



            if (!written) {

                throw new IOException(

                        "Unable to convert image to " + outputFormat);

            }



            convertedBytes = output.toByteArray();

        }



        String extension = outputFormat.equals("jpg") ? "jpg" : outputFormat;

        String contentType = switch (outputFormat) {

            case "jpg" -> "image/jpeg";

            case "png" -> "image/png";

            case "webp" -> "image/webp";

            default -> "application/octet-stream";

        };



        saveConvertedImage(

                convertedBytes,

                "converted-image." + extension,

                outputFormat.toUpperCase(Locale.ROOT),

                contentType,

                ownerEmail);



        return convertedBytes;

    }



    private String normalizeImageFormat(String format) {

        if (format == null || format.isBlank()) {

            throw new IllegalArgumentException("Image format is required.");

        }



        String normalized = format.trim()

                .toLowerCase(Locale.ROOT)

                .replace(".", "");



        if (normalized.equals("jpeg")) {

            return "jpg";

        }



        return normalized;

    }



    private void saveConvertedImage(

            byte[] content,

            String fileName,

            String fileType,

            String contentType,

            String ownerEmail) throws IOException {



        Path uploadsDirectory = Paths.get("uploads")

                .toAbsolutePath()

                .normalize();



        Files.createDirectories(uploadsDirectory);



        String storedFileName = UUID.randomUUID() + "_" + fileName;

        Path outputPath = uploadsDirectory.resolve(storedFileName);

        Files.write(outputPath, content);



        ObjectId gridFsId;



        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {

            gridFsId = gridFsTemplate.store(

                    input,

                    fileName,

                    contentType,

                    Map.of("fileType", fileType, "status", "Converted"));

        }



        Document convertedDocument = new Document();

        convertedDocument.setFileName(fileName);

        convertedDocument.setFileType(fileType);

        convertedDocument.setFilePath(outputPath.toString());

        convertedDocument.setStatus("Converted");

        convertedDocument.setGridFsId(gridFsId.toHexString());
        convertedDocument.setOwnerEmail(normalizeEmail(ownerEmail));



        documentRepository.save(convertedDocument);

    }



    private void saveConvertedFile(

            byte[] content,

            String fileName,

            String fileType,
            String ownerEmail) throws IOException {



        Path uploadsDirectory = Paths.get("uploads")

                .toAbsolutePath()

                .normalize();



        Files.createDirectories(uploadsDirectory);



        String storedFileName = UUID.randomUUID() + "_" + fileName;

        Path outputPath = uploadsDirectory.resolve(storedFileName);

        Files.write(outputPath, content);



        String contentType = switch (fileType.toUpperCase(Locale.ROOT)) {

            case "PDF" -> "application/pdf";

            case "DOCX" ->

                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            case "TXT" -> "text/plain";

            default -> "application/octet-stream";

        };



        ObjectId gridFsId;



        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {

            gridFsId = gridFsTemplate.store(

                    input,

                    fileName,

                    contentType,

                    Map.of("fileType", fileType, "status", "Converted"));

        }



        Document convertedDocument = new Document();

        convertedDocument.setFileName(fileName);

        convertedDocument.setFileType(fileType);

        convertedDocument.setFilePath(outputPath.toString());

        convertedDocument.setStatus("Converted");

        convertedDocument.setGridFsId(gridFsId.toHexString());
        convertedDocument.setOwnerEmail(normalizeEmail(ownerEmail));



        documentRepository.save(convertedDocument);

    }

}
