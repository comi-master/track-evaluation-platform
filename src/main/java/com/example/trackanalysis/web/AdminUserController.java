package com.example.trackanalysis.web;

import com.example.trackanalysis.auth.application.WebIdentityService;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.user.application.UserAdministrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
  private final UserAdministrationService users;
  private final WebIdentityService identities;

  public AdminUserController(UserAdministrationService users, WebIdentityService identities) {
    this.users = users;
    this.identities = identities;
  }

  @GetMapping
  public String list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "") String keyword,
      Model model) {
    model.addAttribute("page", users.list(page, keyword));
    model.addAttribute("keyword", keyword);
    return "admin/users";
  }

  @GetMapping("/new")
  public String createForm(Model model) {
    if (!model.containsAttribute("form"))
      model.addAttribute("form", new CreateUserForm("", "", "", "", "RESEARCHER"));
    return "admin/user-new";
  }

  @GetMapping("/{id}")
  public String details(@PathVariable long id, Model model) {
    var details = users.details(id);
    model.addAttribute("details", details);
    model.addAttribute(
        "form",
        new UpdateUserForm(
            details.user().getDisplayName(),
            details.user().getEmail(),
            details.roles().isEmpty() ? "RESEARCHER" : details.roles().get(0)));
    return "admin/user-detail";
  }

  @PostMapping("/{id}")
  public String update(
      @PathVariable long id,
      @Valid UpdateUserForm form,
      BindingResult binding,
      Principal principal,
      HttpServletRequest request,
      Model model,
      RedirectAttributes flash) {
    if (binding.hasErrors()) {
      model.addAttribute("details", users.details(id));
      return "admin/user-detail";
    }
    var actor = identities.requireActive(principal.getName());
    users.update(
        id,
        form.displayName(),
        form.email(),
        form.role(),
        actor.id(),
        actor.username(),
        RequestIdFilter.requestId(request),
        request.getRemoteAddr());
    flash.addFlashAttribute("success", "用户资料和角色已更新");
    return "redirect:/admin/users/" + id;
  }

  @PostMapping
  public String create(
      @Valid CreateUserForm form,
      BindingResult binding,
      Principal principal,
      HttpServletRequest request,
      RedirectAttributes flash) {
    if (binding.hasErrors()) return "admin/user-new";
    var actor = identities.requireActive(principal.getName());
    users.create(
        form.username(),
        form.displayName(),
        form.email(),
        form.password(),
        form.role(),
        actor.id(),
        actor.username(),
        RequestIdFilter.requestId(request),
        request.getRemoteAddr());
    flash.addFlashAttribute("success", "用户已创建");
    return "redirect:/admin/users";
  }

  @PostMapping("/{id}/enabled")
  public String enabled(
      @PathVariable long id,
      @RequestParam boolean enabled,
      Principal principal,
      HttpServletRequest request) {
    var actor = identities.requireActive(principal.getName());
    users.setEnabled(
        id,
        enabled,
        actor.id(),
        actor.username(),
        RequestIdFilter.requestId(request),
        request.getRemoteAddr());
    return "redirect:/admin/users";
  }

  @PostMapping("/{id}/password")
  public String password(
      @PathVariable long id,
      @RequestParam @Size(min = 12, max = 128) String password,
      Principal principal,
      HttpServletRequest request) {
    var actor = identities.requireActive(principal.getName());
    users.resetPassword(
        id,
        password,
        actor.id(),
        actor.username(),
        RequestIdFilter.requestId(request),
        request.getRemoteAddr());
    return "redirect:/admin/users";
  }

  public record CreateUserForm(
      @NotBlank @Size(max = 64) String username,
      @Size(max = 100) String displayName,
      @Size(max = 254) String email,
      @Size(min = 12, max = 128) String password,
      @NotBlank String role) {}

  public record UpdateUserForm(
      @Size(max = 100) String displayName,
      @jakarta.validation.constraints.Email @Size(max = 254) String email,
      @NotBlank String role) {}
}
