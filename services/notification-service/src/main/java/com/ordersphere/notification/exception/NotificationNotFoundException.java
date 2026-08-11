package com.ordersphere.notification.exception;

public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(Long id) {
    super("No notification found with id: " + id);
  }
}
