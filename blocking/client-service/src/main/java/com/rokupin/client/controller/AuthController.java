package com.rokupin.client.controller;

import com.rokupin.client.exceptions.ClientExistsException;
import com.rokupin.client.exceptions.PasswordsDoNotMatch;
import com.rokupin.client.exceptions.RegistrationException;
import com.rokupin.client.model.form.SignupForm;
import com.rokupin.client.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {
    private final ClientService service;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

   @GetMapping("/signup")
    public String showSignupPage(Model model) {
        model.addAttribute("signupForm", new SignupForm("", "", ""));
        return "signup";
    }

    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute("signupForm") SignupForm signupForm,
                                BindingResult result,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors())
            return "signup";
        try {
            service.registerUser(signupForm);
            redirectAttributes.addFlashAttribute("register", "success");
            return "redirect:/login";
        } catch (ClientExistsException e) {
            result.rejectValue(
                    "username", "error.signupForm", "Username already exists");
        } catch (PasswordsDoNotMatch e) {
            result.rejectValue(
                    "password_repeat", "error.signupForm", "Passwords do not match");
        } catch (RegistrationException e) {
            result.reject("error.signupForm");
        }
        return "signup";
    }

    @GetMapping("/home")
    public String showHomePage(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        if (userDetails != null) {
            model.addAttribute("username", userDetails.getUsername());
        }
        return "home";
    }
}
