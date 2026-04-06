package ca.inspection.home.inspection.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@CrossOrigin(origins = "*")
public class MainController {
    @GetMapping("/")
    public String index() {
        return "redirect:/html/index.html";
    }
}
