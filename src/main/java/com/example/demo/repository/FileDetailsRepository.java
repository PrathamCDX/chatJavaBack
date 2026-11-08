package com.example.demo.repository;

import com.example.demo.model.FileDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FileDetailsRepository extends JpaRepository<FileDetails, Long> {

    Optional<FileDetails> findByMessageId(UUID messageId);

    // Atomic insert-if-missing on the unique message_id, like FileStorageRepository.insertIfAbsent.
    // Returns 1 if inserted, 0 if the message already has a file.
    @Modifying
    @Query(value = "insert into file_details (extension, size_kb, message_id, parts) "
            + "values (:extension, :sizeKb, :messageId, :parts) "
            + "on conflict (message_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("extension") String extension, @Param("sizeKb") Long sizeKb,
            @Param("messageId") UUID messageId, @Param("parts") Long parts);
}
