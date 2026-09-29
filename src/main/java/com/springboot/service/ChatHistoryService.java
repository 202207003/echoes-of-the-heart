package com.springboot.service;

import com.springboot.entity.ChatHistory;
import com.springboot.entity.Member;
import com.springboot.repository.ChatHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;

    public ChatHistoryService(ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public void save(Member member, String question, String poem, String musicTitle, String musicUrl) {
        ChatHistory history = new ChatHistory();

        history.setMember(member);
        history.setQuestion(question);
        history.setPoem(poem);
        history.setMusicTitle(musicTitle);
        history.setMusicUrl(musicUrl);

        chatHistoryRepository.save(history);
    }

    public List<ChatHistory> findByDate(Member member, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        return chatHistoryRepository
                .findByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        member, start, end
                );
    }

    public List<ChatHistory> findByMember(Member member) {
        return chatHistoryRepository.findByMember(member);
    }

    public Optional<ChatHistory> findById(Long id, Member member) {
        return chatHistoryRepository.findByIdAndMember(id, member);
    }
}