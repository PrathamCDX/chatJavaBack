package com.example.demo.repository;

import com.example.demo.model.FileStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FileStorageRepository extends JpaRepository<FileStorage, Long> {

    Optional<FileStorage> findByFileIdAndPartNo(Long fileId, Long partNo);

    @Query("select p.partNo from FileStorage p where p.file.id = :fileId order by p.partNo")
    List<Long> findPartNosByFileId(@Param("fileId") Long fileId);

    // Atomic insert-if-missing: two requests racing on the same part can't both insert, and the
    // loser doesn't fail on the unique constraint. Returns 1 if inserted, 0 if it already existed.
    @Transactional
    @Modifying
    @Query(value = "insert into file_storage (file_id, part_no) values (:fileId, :partNo) "
            + "on conflict (file_id, part_no) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("fileId") Long fileId, @Param("partNo") Long partNo);
}
