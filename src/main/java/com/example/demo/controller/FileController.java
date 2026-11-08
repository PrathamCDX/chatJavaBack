package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.FileDetailsResponse;
import com.example.demo.dto.FileInitiateRequest;
import com.example.demo.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // Safe to retry: 201 the first time, 200 with the same file if it's repeated with the same details
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<FileDetailsResponse>> initiate(@Valid @RequestBody FileInitiateRequest request) {
        FileStorageService.InitiateResult result = fileStorageService.initiate(request);
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("File initiated", result.file()))
                : ResponseEntity.ok(ApiResponse.ok("File already initiated", result.file()));
    }

    // The whole file, streamed part by part. Public for now (see SecurityConfig): anyone with the
    // messageId can download it, so it works in <img src> without a token
    @GetMapping("/{messageId}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID messageId) {
        FileStorageService.FileContent file = fileStorageService.openFile(messageId);
        ContentDisposition disposition = (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Stops browsers guessing a different type than the one sent, e.g. running a "jpg" as html
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.content()));
    }
}
