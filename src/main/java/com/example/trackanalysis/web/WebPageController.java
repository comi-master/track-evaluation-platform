package com.example.trackanalysis.web;

import com.example.trackanalysis.auth.application.WebIdentityService;
import com.example.trackanalysis.auth.application.AuthApplicationService;
import com.example.trackanalysis.auth.api.RegisterRequest;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.user.application.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebPageController {

  private final WebIdentityService identityService;
  private final UserProfileService profiles;
  private final AuthApplicationService authService;

  public WebPageController(
      WebIdentityService identityService,
      UserProfileService profiles,
      AuthApplicationService authService) {
    this.identityService = identityService;
    this.profiles = profiles;
    this.authService = authService;
  }

  @GetMapping("/app/profile")
  public String profile(Principal principal, Model model) {
    model.addAttribute("identity", identityService.requireActive(principal.getName()));
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", new ChangePasswordForm("", "", ""));
    }
    return "app/profile";
  }

  @PostMapping("/app/profile")
  public String updateProfile(
      @RequestParam(required = false) String displayName,
      @RequestParam(required = false) String email,
      Principal principal,
      HttpServletRequest request,
      RedirectAttributes flash) {
    var identity = identityService.requireActive(principal.getName());
    profiles.updateProfile(identity.id(), displayName, email, RequestIdFilter.requestId(request), request.getRemoteAddr());
    flash.addFlashAttribute("success", "用户信息已更新");
    return "redirect:/app/profile";
  }

  @PostMapping("/app/change-password")
  public String changePassword(
      @Valid ChangePasswordForm form,
      BindingResult binding,
      Principal principal,
      HttpServletRequest request,
      Model model,
      RedirectAttributes flash) {
    var identity = identityService.requireActive(principal.getName());
    if (binding.hasErrors()) {
      model.addAttribute("identity", identity);
      return "app/profile";
    }
    try {
      profiles.changePassword(
          identity.id(),
          form.currentPassword(),
          form.newPassword(),
          form.confirmPassword(),
          RequestIdFilter.requestId(request),
          request.getRemoteAddr());
    } catch (BusinessException exception) {
      model.addAttribute("identity", identity);
      model.addAttribute("error", exception.getMessage());
      return "app/profile";
    }
    flash.addFlashAttribute("success", "密码已修改，请重新登录");
    return "redirect:/login?passwordChanged";
  }

  @GetMapping("/")
  public String root(Principal principal) {
    return principal == null ? "redirect:/login" : "redirect:/app/simulator";
  }

  @GetMapping("/login")
  public String login(Model model) {
    model.addAttribute("registrationForm", new RegistrationForm("", "", ""));
    return "login";
  }

  @GetMapping("/register")
  public String register(Model model) {
    if (!model.containsAttribute("registrationForm")) {
      model.addAttribute("registrationForm", new RegistrationForm("", "", ""));
    }
    return "register";
  }

  @PostMapping("/register")
  public String register(@Valid RegistrationForm form, BindingResult binding, Model model) {
    if (!form.password().equals(form.confirmPassword())) {
      binding.rejectValue("confirmPassword", "password.mismatch", "两次输入的密码不一致");
    }
    if (binding.hasErrors()) {
      model.addAttribute("registrationForm", form);
      return "register";
    }
    try {
      authService.register(new RegisterRequest(form.username(), form.password()));
    } catch (BusinessException exception) {
      model.addAttribute("registrationForm", form);
      model.addAttribute("error", exception.getMessage());
      return "register";
    }
    return "redirect:/login?registered";
  }

  @GetMapping("/app/dashboard")
  public String dashboard() {
    return "redirect:/app/simulator";
  }

  @org.springframework.web.bind.annotation.RequestMapping("/403")
  public String forbidden() {
    return "error/403";
  }

  public record ChangePasswordForm(
      @Size(min = 1, max = 128) String currentPassword,
      @Size(min = 12, max = 128) String newPassword,
      @Size(min = 12, max = 128) String confirmPassword) {}

  public record RegistrationForm(
      @jakarta.validation.constraints.NotBlank(message = "请输入用户名")
          @Size(min = 3, max = 64, message = "用户名长度为 3-64 个字符") String username,
      @jakarta.validation.constraints.NotBlank(message = "请输入密码")
          @Size(min = 8, max = 64, message = "密码长度为 8-64 个字符") String password,
      @jakarta.validation.constraints.NotBlank(message = "请再次输入密码") String confirmPassword) {}
}
