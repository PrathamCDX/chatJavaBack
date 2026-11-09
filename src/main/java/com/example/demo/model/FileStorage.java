package com.example.demo.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Unique on (file_id, part_no): a part is stored once, and insertIfAbsent relies on it
@Entity
@Table(name = "file_storage",
        uniqueConstraints = @UniqueConstraint(name = "ux_file_storage_file_part", columnNames = {"file_id", "part_no"}))
public class FileStorage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ON DELETE CASCADE: deleting a file removes its parts in the database
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FileDetails file;

    @Column(nullable = false)
    private Long partNo;

    protected FileStorage() {
    }

    public FileStorage(FileDetails file, Long partNo) {
        this.file = file;
        this.partNo = partNo;
    }

    public Long getId() {
        return id;
    }

    public FileDetails getFile() {
        return file;
    }

    public Long getPartNo() {
        return partNo;
    }
}
