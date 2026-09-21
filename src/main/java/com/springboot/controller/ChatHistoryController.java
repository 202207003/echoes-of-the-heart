package com.springboot.controller;

import com.springboot.entity.ChatHistory;
import com.springboot.entity.Member;
import com.springboot.service.ChatHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/history")
    public String history(
    		@RequestParam(name = "date", required = false) String date,
            HttpSession session,
            Model model
    ) {
        Member member = (Member) session.getAttribute("member");

        if (member == null) {
            return "redirect:/login";
        }

        LocalDate selectedDate = date != null
                ? LocalDate.parse(date)
                : LocalDate.now();

        List<ChatHistory> histories =
                chatHistoryService.findByDate(member, selectedDate);

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("histories", histories);

        return "history";
    }

    @GetMapping("/history/{id}")
    public String historyDetail(
    		@PathVariable("id") Long id,
            HttpSession session,
            Model model
    ) {
        Member member = (Member) session.getAttribute("member");

        if (member == null) {
            return "redirect:/login";
        }

        Optional<ChatHistory> history =
                chatHistoryService.findById(id, member);

        if (history.isEmpty()) {
            return "redirect:/history";
        }

        model.addAttribute("history", history.get());

        return "history-detail";
    }
}