package com.springboot.repository;

import com.springboot.entity.ChatHistory;
import com.springboot.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Member member,
            LocalDateTime start,
            LocalDateTime end
    );

    List<ChatHistory> findByMemberOrderByCreatedAtDesc(Member member);

    Optional<ChatHistory> findByIdAndMember(Long id, Member member);
}