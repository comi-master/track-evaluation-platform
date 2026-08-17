package com.example.trackanalysis.web;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditPageController {
  private final AuditApplicationService audit;

  public AuditPageController(AuditApplicationService audit) {
    this.audit = audit;
  }

  @GetMapping("/admin/audit-logs")
  public String list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "") String username,
      @RequestParam(defaultValue = "") String action,
      Model model) {
    model.addAttribute("page", audit.list(page, 50, username, action));
    model.addAttribute("username", username);
    model.addAttribute("action", action);
    return "admin/audit-logs";
  }
}
