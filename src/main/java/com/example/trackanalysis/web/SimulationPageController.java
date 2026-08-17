package com.example.trackanalysis.web;

import com.example.trackanalysis.auth.application.WebIdentityService;
import com.example.trackanalysis.web.application.SimulationGeneratorService;
import com.example.trackanalysis.web.application.SimulationGeneratorService.SimulationRequest;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Profile("!no-persistence")
public class SimulationPageController {
  private final WebIdentityService identities;
  private final SimulationGeneratorService generator;

  public SimulationPageController(WebIdentityService identities, SimulationGeneratorService generator) {
    this.identities = identities;
    this.generator = generator;
  }

  @GetMapping("/app/simulator")
  public String page() { return "app/simulator"; }

  @PostMapping("/app/simulator/generate")
  public String generate(Principal principal, @RequestParam String motionModel, @RequestParam int dimensions,
      @RequestParam double noise, @RequestParam int targetCount, @RequestParam int points,
      @RequestParam double timeStep, @RequestParam(defaultValue = "20260817") long seed,
      @RequestParam(defaultValue = "1.0") double abnormalThreshold,
      @RequestParam(required = false) String description, RedirectAttributes flash) {
    long id = generator.generate(identities.requireActive(principal.getName()).id(),
        new SimulationRequest(motionModel, dimensions, noise, targetCount, points, timeStep, seed, abnormalThreshold, description));
    flash.addFlashAttribute("success", "仿真场景已生成并解析完成，可以上传算法结果");
    return "redirect:/app/datasets/" + id;
  }
}
