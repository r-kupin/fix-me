package com.rokupin.client.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {
    //    private final UserService userService;
//    private final JwtService jwtService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

//   @GetMapping("/signup")
//    public String showSignupPage(Model model) {
//        model.addAttribute("userForm", new SignupForm());
//        return "signup";
//    }
//
//    @ResponseStatus(HttpStatus.CREATED)
//    @PostMapping("/signup")
//    public String processSignup(@ModelAttribute("userForm")
//                                @Valid
//                                SignupForm form,
//                                BindingResult result) {
//        if (result.hasErrors())
//            return "signup";
//        userService.registerUser(form.username(), form.password());
//        return "redirect:/login";
//    }

    @GetMapping("/home")
    public String showHomePage(Model model) {
        model.addAttribute("username", "gggggg");
        return "home";
    }
}
