package com.rokupin.client.controller;

import com.rokupin.client.model.form.LoginForm;
import com.rokupin.client.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {
    //    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/perform-login")
    public String performLogin(@ModelAttribute LoginForm loginForm, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof UsernamePasswordAuthenticationToken tokenAuth) {
            String jwt = tokenAuth.getCredentials().toString();
            model.addAttribute("token", jwt);
            return "redirect:/home";  // Redirect with token stored for frontend use
        }

        return "redirect:/login?login=failed";
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
    public String showHomePage(UserDetails user,
                               Model model) {
        model.addAttribute("username", user.getUsername());
        return "home";
    }
}
