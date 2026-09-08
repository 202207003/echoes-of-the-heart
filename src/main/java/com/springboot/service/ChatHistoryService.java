package com.springboot.service;

import com.springboot.entity.ChatHistory;
import com.springboot.entity.Member;
import com.springboot.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;

    public void save(Member member, String question, String poem, String musicTitle, String musicUrl) {

        ChatHistory history = new ChatHistory();

        history.setMember(member);
        history.setQuestion(question);
        history.setPoem(poem);
        history.setMusicTitle(musicTitle);
        history.setMusicUrl(musicUrl);

        chatHistoryRepository.save(history);
    }
}
