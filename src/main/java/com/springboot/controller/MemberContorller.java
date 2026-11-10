package com.springboot.controller;

import com.springboot.repository.MemberRepository;
import com.springboot.service.GuestResultService;
import lombok.extern.slf4j.Slf4j;


import java.util.Optional;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.springboot.entity.Member;

@Slf4j
@Controller
public class MemberContorller {
	
	private final MemberRepository memberRepository;
	private final GuestResultService guestResultService;

	public MemberContorller(MemberRepository memberRepository, GuestResultService guestResultService) {
		this.memberRepository = memberRepository;
		this.guestResultService = guestResultService;
	}

	@GetMapping("/login")
	public String ShowLoginForm(@RequestParam(value = "resultId", required = false) String resultId,
            HttpSession session, Model model) {
        model.addAttribute("resultId", guestResultService.selectResult(resultId, session));
		return "login_form";
	}
	
	@GetMapping("/sign")
	public String ShowSignForm(@RequestParam(value = "resultId", required = false) String resultId,
            HttpSession session, Model model) {
        model.addAttribute("resultId", guestResultService.selectResult(resultId, session));
		return "sign_form";
	}
	
	@PostMapping("/login")
	public String login(@RequestParam("username") String username, @RequestParam("password") String password,
            @RequestParam(value = "resultId", required = false) String resultId,
            HttpSession session, RedirectAttributes redirectAttributes) {
	    Optional<Member> member = memberRepository.findByUsername(username);

	    if(member.isPresent() && member.get().getPassword().equals(password)) {
            String selectedResultId = guestResultService.selectResult(resultId, session);
	    	session.setAttribute("member", member.get());
        	session.setAttribute("username", member.get().getUsername());
        	session.setAttribute("mbti", member.get().getMbti());
            try {
                guestResultService.saveForMember(member.get(), session);
                if (selectedResultId != null) {
                    redirectAttributes.addFlashAttribute("resultNotice", "로그인했어요. 비로그인 상태에서 만든 결과를 내 기록에 저장했어요.");
                }
            } catch (RuntimeException e) {
                log.warn("비로그인 결과를 회원 기록에 저장하지 못했습니다.");
                redirectAttributes.addFlashAttribute("resultError", "로그인은 완료했지만 기록을 저장하지 못했어요. 현재 결과는 그대로 볼 수 있어요.");
            }
            if (selectedResultId != null) {
                redirectAttributes.addAttribute("resultId", selectedResultId);
                return "redirect:/music-result";
            }
            return "redirect:/chat";
	    }
        String selectedResultId = guestResultService.selectResult(resultId, session);
        if (selectedResultId != null) redirectAttributes.addAttribute("resultId", selectedResultId);
        redirectAttributes.addFlashAttribute("loginError", "아이디 또는 비밀번호를 확인해주세요.");
        redirectAttributes.addFlashAttribute("loginUsername", username);
	    return "redirect:/login";
	}
	
	@GetMapping("/logout")
	public String logout(HttpSession session) {
	    session.invalidate();
	    return "redirect:/chat";
	}
	
	@PostMapping("/sign")
	public String signup(Member member, @RequestParam(value = "resultId", required = false) String resultId,
            HttpSession session, RedirectAttributes redirectAttributes) {
		memberRepository.save(member);
        String selectedResultId = guestResultService.selectResult(resultId, session);
        if (selectedResultId != null) {
            redirectAttributes.addAttribute("resultId", selectedResultId);
            redirectAttributes.addFlashAttribute("loginNotice", "회원가입이 완료됐어요. 로그인하면 이전 결과가 그대로 이어져요.");
            return "redirect:/login";
        }
		return "redirect:/chat";
	}
	
	@GetMapping("/checkUsername")
	@ResponseBody
	public String checkUsername(@RequestParam("username") String username) {

	    if(memberRepository.findByUsername(username).isPresent()) {
	        return "이미 사용중인 아이디입니다";
	    }

	    return "사용 가능한 아이디입니다!";
	}

}
