package com.example.demo.dto;

import com.example.demo.model.FileStorage;

public record FilePartResponse(Long id, Long fileId, Long partNo) {

    public static FilePartResponse from(FileStorage part) {
        return new FilePartResponse(part.getId(), part.getFile().getId(), part.getPartNo());
    }
}
