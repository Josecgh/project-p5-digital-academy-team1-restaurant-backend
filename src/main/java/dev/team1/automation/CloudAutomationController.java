package dev.team1.automation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "${api-endpoint}")
public class CloudAutomationController {
  private final CloudAutomationService automationService;

  public CloudAutomationController(CloudAutomationService automationService) {
    this.automationService = automationService;
  }

  @GetMapping({"/sistema/cron-status", "/cloud-automation/status"})
  public CloudAutomationStatus status() {
    return automationService.status();
  }
}
