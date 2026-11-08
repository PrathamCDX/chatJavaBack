package com.example.demo.model;

import jakarta.persistence.*;

import java.util.UUID;

/** A file attached to a chat message. Its content is stored in parts, in file_storage. */
@Entity
@Table(name = "file_details")
public class FileDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String extension;

    @Column(nullable = false)
    private Long sizeKb;

    // One file per message, so parts can be found by the message's id
    @Column(nullable = false, unique = true)
    private UUID messageId;

    // How many parts the file is split into
    @Column(nullable = false)
    private Long parts;

    protected FileDetails() {
    }

    public FileDetails(String extension, Long sizeKb, UUID messageId, Long parts) {
        this.extension = extension;
        this.sizeKb = sizeKb;
        this.messageId = messageId;
        this.parts = parts;
    }

    public Long getId() {
        return id;
    }

    public String getExtension() {
        return extension;
    }

    public Long getSizeKb() {
        return sizeKb;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public Long getParts() {
        return parts;
    }
}
