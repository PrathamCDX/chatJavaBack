package com.example.demo.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Unique on (file_id, part_no): created in schema.sql, because Hibernate skips multi-column unique
// constraints and indexes on SQLite
@Entity
@Table(name = "file_storage")
public class FileStorage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hibernate leaves foreign keys out on SQLite, so the column definition declares it instead.
    // NO_CONSTRAINT stops Hibernate adding a second, identical one on databases where it does create them.
    // ON DELETE CASCADE: deleting a file removes its parts in the database
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false,
            columnDefinition = "bigint references file_details (id) on delete cascade",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
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
