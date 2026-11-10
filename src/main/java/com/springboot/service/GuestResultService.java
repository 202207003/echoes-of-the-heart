package com.springboot.service;

import com.springboot.entity.Member;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class GuestResultService {
    private static final String SESSION_KEY = "guestMusicResults";
    private final ChatHistoryService historyService;

    public GuestResultService(ChatHistoryService historyService) {
        this.historyService = historyService;
    }

    private record GuestResult(String id, String input, String poem, String musicTitle, String audioUrl) {}
    private static class PendingResults {
        private final LinkedHashMap<String, GuestResult> results = new LinkedHashMap<>();
    }

    public void remember(String id, String input, String poem, String musicTitle, String audioUrl,
                         HttpSession session) {
        synchronized (session) {
            if (session.getAttribute("username") != null) return;
            PendingResults pending;
            if (session.getAttribute(SESSION_KEY) instanceof PendingResults saved) {
                pending = saved;
            } else {
                pending = new PendingResults();
                session.setAttribute(SESSION_KEY, pending);
            }
            pending.results.put(id, new GuestResult(id, input, poem, musicTitle, audioUrl));
            while (pending.results.size() > 5) {
                pending.results.remove(pending.results.keySet().iterator().next());
            }
            session.setAttribute("latestGuestResultId", id);
        }
    }

    public String selectResult(String requestedId, HttpSession session) {
        synchronized (session) {
            if (!(session.getAttribute(SESSION_KEY) instanceof PendingResults pending)) return null;
            if (requestedId != null && pending.results.containsKey(requestedId)) return requestedId;
            Object latest = session.getAttribute("latestGuestResultId");
            return latest instanceof String id && pending.results.containsKey(id) ? id : null;
        }
    }

    public void saveForMember(Member member, HttpSession session) {
        synchronized (session) {
            if (!(session.getAttribute(SESSION_KEY) instanceof PendingResults pending)) return;
            for (GuestResult result : new ArrayList<>(pending.results.values())) {
                historyService.save(member, result.input(), result.poem(), result.musicTitle(), result.audioUrl());
                // Remove each saved item immediately so a retry does not save it twice.
                pending.results.remove(result.id());
            }
            session.removeAttribute(SESSION_KEY);
            session.removeAttribute("latestGuestResultId");
        }
    }
}
