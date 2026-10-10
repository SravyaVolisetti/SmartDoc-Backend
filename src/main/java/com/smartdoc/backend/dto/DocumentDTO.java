
package com.smartdoc.backend.dto;

public class DocumentDTO {

    private String id;
    private String fileName;
    private String fileType;
    private String status;

    public DocumentDTO() {
    }

    public DocumentDTO(String id, String fileName, String fileType, String status) {
        this.id = id;
        this.fileName = fileName;
        this.fileType = fileType;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}