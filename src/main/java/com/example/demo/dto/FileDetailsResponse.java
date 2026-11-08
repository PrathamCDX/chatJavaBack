package com.example.demo.dto;

import com.example.demo.model.FileDetails;

import java.util.UUID;

public record FileDetailsResponse(Long id, String extension, Long sizeKb, UUID messageId, Long parts) {

    public static FileDetailsResponse from(FileDetails file) {
        return new FileDetailsResponse(file.getId(), file.getExtension(), file.getSizeKb(), file.getMessageId(),
                file.getParts());
    }
}
