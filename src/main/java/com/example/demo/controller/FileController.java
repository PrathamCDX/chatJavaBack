package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.FileDetailsResponse;
import com.example.demo.dto.FileInitiateRequest;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.FileStorageService.FileContent;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

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

    /**
     * The whole file (200), or the bytes a Range header asks for (206), streamed part by part. Public for now
     * (see SecurityConfig): anyone with the messageId can download it, so it works in <img src> without a token.
     *
     * Range lets browsers resume a download and seek in a video. Only one range is supported; a header with
     * several, or one that can't be parsed, gets the whole file, which the HTTP spec allows.
     * If-Range: the client sends the etag of the copy it has part of. If the file changed since, its bytes
     * wouldn't fit together with the new ones, so the whole file is sent instead.
     *
     * Written to the response directly: given a Resource, Spring handles Range itself, and it can't for a
     * file stored in parts.
     */
    @GetMapping("/{messageId}/content")
    public void content(@PathVariable UUID messageId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
            HttpServletResponse response) throws IOException {
        FileContent file = fileStorageService.openFile(messageId);
        setFileHeaders(response, file);

        ByteRange byteRange = ifRange == null || ifRange.equals(file.etag())
                ? ByteRange.parse(range, file.size())
                : null;
        if (byteRange == null) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentLengthLong(file.size());
            send(file, 0, file.size() - 1, response);
        } else if (byteRange == ByteRange.UNSATISFIABLE) {
            // Tells the client the real size, e.g. after asking to resume past the end
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + file.size());
            response.setContentLength(0);
        } else {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE,
                    "bytes " + byteRange.start() + "-" + byteRange.end() + "/" + file.size());
            response.setContentLengthLong(byteRange.end() - byteRange.start() + 1);
            send(file, byteRange.start(), byteRange.end(), response);
        }
    }

    // The headers a GET of the whole file would have, without the body: lets a client learn the size,
    // that ranges are supported, and the etag before downloading
    @RequestMapping(value = "/{messageId}/content", method = RequestMethod.HEAD)
    public void contentHead(@PathVariable UUID messageId, HttpServletResponse response) {
        FileContent file = fileStorageService.openFile(messageId);
        setFileHeaders(response, file);
        response.setStatus(HttpStatus.OK.value());
        response.setContentLengthLong(file.size());
    }

    private static void setFileHeaders(HttpServletResponse response, FileContent file) {
        ContentDisposition disposition = (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.filename())
                .build();
        response.setContentType(file.contentType().toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.ETAG, file.etag());
        // Stops browsers guessing a different type than the one sent, e.g. running a "jpg" as html
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void send(FileContent file, long start, long end, HttpServletResponse response) {
        try {
            fileStorageService.writeRange(file, start, end, response.getOutputStream());
        } catch (IOException e) {
            // Usually the client closing the connection, e.g. a paused download or a video seeking elsewhere.
            // The status and headers are already sent, so there's no error response to give
            log.debug("Download of {} stopped: {}", file.filename(), e.toString());
        }
    }

    /** Bytes start to end of the file, both inclusive and counted from 0. */
    private record ByteRange(long start, long end) {

        static final ByteRange UNSATISFIABLE = new ByteRange(-1, -1);

        // "bytes=100-199", "bytes=100-" (100 to the end) or "bytes=-100" (the last 100 bytes)
        private static final Pattern SINGLE_RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)", Pattern.CASE_INSENSITIVE);

        /**
         * The range to send, UNSATISFIABLE if it starts past the end of the file, or null to send the whole
         * file: no Range header, or one that isn't a single valid range.
         */
        static ByteRange parse(String header, long size) {
            if (header == null) {
                return null;
            }
            Matcher matcher = SINGLE_RANGE.matcher(header.strip());
            if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) {
                return null;
            }
            try {
                if (matcher.group(1).isEmpty()) {
                    long suffix = Long.parseLong(matcher.group(2));
                    return suffix == 0 || size == 0 ? UNSATISFIABLE : new ByteRange(Math.max(0, size - suffix), size - 1);
                }
                long start = Long.parseLong(matcher.group(1));
                if (start >= size) {
                    return UNSATISFIABLE;
                }
                // An end past the file's end means "to the end"
                long end = matcher.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(matcher.group(2)), size - 1);
                return end < start ? null : new ByteRange(start, end);
            } catch (NumberFormatException e) {
                // A number too big for a long
                return null;
            }
        }
    }
}
