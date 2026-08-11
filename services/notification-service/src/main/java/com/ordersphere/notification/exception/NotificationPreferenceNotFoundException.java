package com.ordersphere.notification.exception;

public class NotificationPreferenceNotFoundException extends RuntimeException {

  public NotificationPreferenceNotFoundException(Long id) {
    super("No notification preference found with id: " + id);
  }
}
