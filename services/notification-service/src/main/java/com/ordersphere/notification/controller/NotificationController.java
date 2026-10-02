package com.ordersphere.notification.controller;

import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.dto.NotificationResponse;
import com.ordersphere.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
  public ResponseEntity<NotificationResponse> createNotification(
      @Valid @RequestBody CreateNotificationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(notificationService.createNotification(request));
  }

  @GetMapping
  public List<NotificationResponse> listOwn(Authentication authentication) {
    return notificationService.listOwn(authentication.getName());
  }

  @GetMapping("/{id}")
  public NotificationResponse getNotification(
      @PathVariable Long id, Authentication authentication) {
    return notificationService.getNotification(
        authentication.getName(), isAdmin(authentication), id);
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
