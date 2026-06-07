package com.springboot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LoginContorller {
	
	@GetMapping("/login")
	public String ShowLoginForm() {
		return "login_form";
	}
	
	//@PostMapping("/login")
}
