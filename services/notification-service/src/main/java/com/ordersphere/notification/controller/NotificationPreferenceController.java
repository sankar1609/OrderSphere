package com.ordersphere.notification.controller;

import com.ordersphere.notification.dto.CreateNotificationPreferenceRequest;
import com.ordersphere.notification.dto.NotificationPreferenceResponse;
import com.ordersphere.notification.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-preferences")
public class NotificationPreferenceController {

  private final NotificationPreferenceService preferenceService;

  public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
    this.preferenceService = preferenceService;
  }

  @PostMapping
  public NotificationPreferenceResponse setPreference(
      @Valid @RequestBody CreateNotificationPreferenceRequest request, Principal principal) {
    return preferenceService.setPreference(principal.getName(), request);
  }

  @GetMapping
  public List<NotificationPreferenceResponse> listPreferences(Principal principal) {
    return preferenceService.listPreferences(principal.getName());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePreference(@PathVariable Long id, Principal principal) {
    preferenceService.deletePreference(principal.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
