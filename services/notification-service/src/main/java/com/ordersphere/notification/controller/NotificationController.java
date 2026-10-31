package com.ordersphere.notification.controller;

import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.dto.NotificationResponse;
import com.ordersphere.notification.dto.PageResponse;
import com.ordersphere.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "The caller's notifications")
@Validated
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

  @Operation(summary = "The caller's notifications, newest first, a page at a time")
  @GetMapping
  public PageResponse<NotificationResponse> listOwn(
      Authentication authentication,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
    return notificationService.listOwn(authentication.getName(), page, size);
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
