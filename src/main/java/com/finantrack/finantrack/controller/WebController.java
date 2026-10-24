
package com.finantrack.finantrack.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/api")
public class WebController {

   @GetMapping("/")
   public String rootRedirect() {
      return "redirect:/api/index";
   }

   @GetMapping("/index")
   public String indexPage() {
      return "index";
   }

   @GetMapping("/login")
   public String loginPage() {
      return "login";
   }

   @GetMapping("/dashboard")
   public String dashboard(Authentication authentication, Model model) {
      model.addAttribute("email", authentication.getName());
      return "dashboard";
   }
}

