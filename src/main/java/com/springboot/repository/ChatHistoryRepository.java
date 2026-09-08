package com.springboot.repository;

import com.springboot.entity.ChatHistory;
import com.springboot.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    // 특정 회원의 기록 조회
    List<ChatHistory> findByMember(Member member);
}
