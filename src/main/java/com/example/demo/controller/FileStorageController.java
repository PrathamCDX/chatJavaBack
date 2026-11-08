package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.FilePartResponse;
import com.example.demo.service.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/storage")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    public FileStorageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // The body is the part's raw bytes (e.g. Content-Type: application/octet-stream), streamed to storage.
    // Idempotent, so clients can safely retry: 201 the first time, 200 with the
    // same row after that
    @PutMapping("/{messageId}/parts/{partNo}")
    public ResponseEntity<ApiResponse<FilePartResponse>> putPart(@PathVariable UUID messageId,
            @PathVariable Long partNo, InputStream content) {
        FileStorageService.PutResult result = fileStorageService.putPart(messageId, partNo, content);
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Part stored", result.part()))
                : ResponseEntity.ok(ApiResponse.ok("Part already stored", result.part()));
    }
}
