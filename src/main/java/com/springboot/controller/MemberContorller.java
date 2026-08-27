package com.springboot.controller;

import com.springboot.repository.MemberRepository;


import java.util.Optional;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.springboot.entity.Member;

@Controller
public class MemberContorller {
	
	private final MemberRepository memberRepository;

	public MemberContorller(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@GetMapping("/login")
	public String ShowLoginForm() {
		return "login_form";
	}
	
	@GetMapping("/sign")
	public String ShowSignForm() {
		return "sign_form";
	}
	
	@PostMapping("/login")
	public String login(@RequestParam("username") String username, @RequestParam("password") String password, HttpSession session) {
	    Optional<Member> member = memberRepository.findByUsername(username);

	    if(member.isPresent() && member.get().getPassword().equals(password)) {
        	session.setAttribute("username", member.get().getUsername());
        	session.setAttribute("mbti", member.get().getMbti());
            return "redirect:/chat";
	    }
	    return "redirect:/login";
	}
	
	@GetMapping("/logout")
	public String logout(HttpSession session) {
	    session.invalidate();
	    return "redirect:/chat";
	}
	
	@PostMapping("/sign")
	public String signup(Member member) {
		memberRepository.save(member);
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
