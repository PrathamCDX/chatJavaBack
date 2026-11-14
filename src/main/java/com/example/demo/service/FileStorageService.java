package com.example.demo.service;

import com.example.demo.dto.FileDetailsResponse;
import com.example.demo.dto.FileInitiateRequest;
import com.example.demo.dto.FilePartResponse;
import com.example.demo.dto.FilePartsResponse;
import com.example.demo.model.FileDetails;
import com.example.demo.repository.FileDetailsRepository;
import com.example.demo.repository.FileStorageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // Images browsers can display. No svg: it can contain scripts
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final FileStorageRepository fileStorageRepository;
    private final FileDetailsRepository fileDetailsRepository;
    // Parts are saved as {storageDir}/{fileId}/{partNo}; lost on redeploy on hosts with a temporary disk
    private final Path storageDir;

    public FileStorageService(FileStorageRepository fileStorageRepository,
            FileDetailsRepository fileDetailsRepository, @Value("${storage.dir}") Path storageDir) {
        this.fileStorageRepository = fileStorageRepository;
        this.fileDetailsRepository = fileDetailsRepository;
        this.storageDir = storageDir;
    }

    /** created is false when the message already had this file; the row is the same either way. */
    public record InitiateResult(FileDetailsResponse file, boolean created) {
    }

    /**
     * Creates the file's details, which its parts are then stored under. Idempotent: repeating it with
     * the same details returns the same file; different details for the same message are a conflict.
     */
    @Transactional
    public InitiateResult initiate(FileInitiateRequest request) {
        // ".JPG" and "jpg" are the same extension
        String extension = request.extension().replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
        // Any extension: documents can be anything. Only the image message type limits it (checkImageReady).
        // Safe to serve because openFile only shows images, video and audio in the browser; the rest are downloads
        boolean created = fileDetailsRepository.insertIfAbsent(extension, request.sizeKb(), request.messageId(),
                request.parts()) == 1;
        FileDetails file = fileDetailsRepository.findByMessageId(request.messageId()).orElseThrow();
        if (!created && !(file.getExtension().equals(extension) && file.getSizeKb().equals(request.sizeKb())
                && file.getParts().equals(request.parts()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This message already has a different file");
        }
        return new InitiateResult(FileDetailsResponse.from(file), created);
    }

    /** created is false when the part was already stored; the row is the same either way. */
    public record PutResult(FilePartResponse part, boolean created) {
    }

    /**
     * Saves a part of the message's file. Idempotent: repeating it replaces the part's content and
     * leaves one row. The file's details must exist first.
     *
     * Not @Transactional on purpose: the upload can be slow, and a transaction would hold a database
     * connection the whole time. The bytes are saved first, then the row is added, so a failed
     * save leaves no row.
     */
    public PutResult putPart(UUID messageId, Long partNo, InputStream content) {
        if (partNo < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partNo must not be negative");
        }
        FileDetails file = fileDetailsRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        try {
            writePart(file.getId(), partNo, content);
        } catch (IOException e) {
            // Also happens when the client disconnects mid-upload; then nobody receives this response
            log.warn("Failed to save part {} of file {}", partNo, file.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file part", e);
        }
        boolean created = fileStorageRepository.insertIfAbsent(file.getId(), partNo) == 1;
        FilePartResponse part = fileStorageRepository.findByFileIdAndPartNo(file.getId(), partNo)
                .map(FilePartResponse::from)
                .orElseThrow();
        return new PutResult(part, created);
    }

    /** Which parts of the message's file are uploaded and which are still needed, both ascending. */
    @Transactional(readOnly = true)
    public FilePartsResponse listParts(UUID messageId) {
        FileDetails file = fileDetailsRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        List<Long> uploaded = fileStorageRepository.findPartNosByFileId(file.getId());
        Set<Long> stored = new HashSet<>(uploaded);
        List<Long> missing = new ArrayList<>();
        for (long partNo = 0; partNo < file.getParts(); partNo++) {
            if (!stored.contains(partNo)) {
                missing.add(partNo);
            }
        }
        return new FilePartsResponse(file.getId(), file.getParts(), uploaded, missing);
    }

    /**
     * Saves the part, replacing it if it's already saved. Reads the content as it arrives instead of
     * holding it in memory. A failed write leaves no partial part behind.
     */
    private void writePart(Long fileId, Long partNo, InputStream content) throws IOException {
        Path part = partPath(fileId, partNo);
        Path dir = Files.createDirectories(part.getParent());
        // Written under a temporary name, then renamed in one step: readers never see half a part,
        // and two uploads of the same part each write their own temp file
        Path temp = dir.resolve(partNo + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(content, temp);
            Files.move(temp, part, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path partPath(Long fileId, Long partNo) {
        return storageDir.resolve(fileId.toString()).resolve(partNo.toString());
    }

    /**
     * A whole file, ready to send with writeRange: its parts in order and each one's size. Nothing is open yet.
     * inline is false for types a browser could run as a page (like html or svg): those are only
     * offered as downloads, so a file served from this server can't run scripts on it.
     * etag identifies this version of the file; it changes when a part is uploaded again.
     */
    public record FileContent(MediaType contentType, boolean inline, long size, String filename, String etag,
            List<Path> parts, List<Long> partSizes) {
    }

    /** Where the message's file can be downloaded, relative to the API's address. */
    public static String contentPath(UUID messageId) {
        return "/file/" + messageId + "/content";
    }

    /** Throws unless the message's file is an image with every part uploaded, ready to be sent in chat. */
    public void checkImageReady(UUID messageId) {
        FileDetails file = fileDetailsRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        // Only checks the name: the uploaded bytes could still be something else
        if (!IMAGE_EXTENSIONS.contains(file.getExtension())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File is not an image: " + String.join(", ", IMAGE_EXTENSIONS.stream().sorted().toList()));
        }
        completeParts(file);
    }

    /** Like checkImageReady, for a document, which can have any extension. Returns its details for the chat event. */
    public FileDetailsResponse checkDocumentReady(UUID messageId) {
        FileDetails file = fileDetailsRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        completeParts(file);
        return FileDetailsResponse.from(file);
    }

    /** The message's file, with its parts in order. Fails if any part hasn't been uploaded. */
    public FileContent openFile(UUID messageId) {
        FileDetails file = fileDetailsRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        List<Path> parts = completeParts(file);
        List<Long> partSizes = new ArrayList<>();
        long size = 0;
        long lastModified = 0;
        for (Path part : parts) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(part, BasicFileAttributes.class);
                partSizes.add(attributes.size());
                size += attributes.size();
                lastModified = Math.max(lastModified, attributes.lastModifiedTime().toMillis());
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
            }
        }
        // Uploading a part again writes a new file, so its modified time changes and so does the etag
        String etag = "\"" + messageId + "-" + size + "-" + lastModified + "\"";

        MediaType contentType = MediaTypeFactory.getMediaType("file." + file.getExtension())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        boolean inline = (contentType.getType().equals("image") && !contentType.getSubtype().contains("svg"))
                || contentType.getType().equals("video") || contentType.getType().equals("audio");
        return new FileContent(contentType, inline, size, messageId + "." + file.getExtension(), etag, parts,
                partSizes);
    }

    /**
     * Writes bytes start to end (both inclusive, counted from 0) of the file to out. Only the parts holding
     * those bytes are opened, one at a time, and the start of the first one is skipped without being read.
     */
    public void writeRange(FileContent file, long start, long end, OutputStream out) throws IOException {
        long partStart = 0;
        for (int i = 0; i < file.parts().size() && partStart <= end; i++) {
            long partEnd = partStart + file.partSizes().get(i) - 1;
            if (partEnd >= start) {
                try (InputStream in = Files.newInputStream(file.parts().get(i))) {
                    in.skipNBytes(Math.max(start, partStart) - partStart);
                    copy(in, out, Math.min(end, partEnd) - Math.max(start, partStart) + 1);
                }
            }
            partStart = partEnd + 1;
        }
    }

    private static void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        while (count > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, count));
            if (read == -1) {
                throw new EOFException("Part is shorter than when the download started");
            }
            out.write(buffer, 0, read);
            count -= read;
        }
    }

    /** The paths of all the file's parts, in order. Throws 409 if any isn't recorded or isn't on disk. */
    private List<Path> completeParts(FileDetails file) {
        Set<Long> stored = new HashSet<>(fileStorageRepository.findPartNosByFileId(file.getId()));
        List<Path> parts = new ArrayList<>();
        for (long partNo = 0; partNo < file.getParts(); partNo++) {
            Path part = partPath(file.getId(), partNo);
            if (!stored.contains(partNo) || !Files.isRegularFile(part)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Upload incomplete: part " + partNo + " of " + file.getParts() + " is missing");
            }
            parts.add(part);
        }
        return parts;
    }
}
