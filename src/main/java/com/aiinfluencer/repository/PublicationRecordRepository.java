package com.aiinfluencer.repository;

import com.aiinfluencer.domain.entity.PublicationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicationRecordRepository extends JpaRepository<PublicationRecord, Long> {
    List<PublicationRecord> findByVideoProjectId(Long videoProjectId);
    List<PublicationRecord> findByStatus(PublicationRecord.Status status);
}
