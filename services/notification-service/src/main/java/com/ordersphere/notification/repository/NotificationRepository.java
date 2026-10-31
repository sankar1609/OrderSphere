package com.ordersphere.notification.repository;

import com.ordersphere.notification.domain.Notification;
import com.ordersphere.notification.domain.NotificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Page<Notification> findByRecipientUsername(String recipientUsername, Pageable pageable);

  Optional<Notification> findByIdAndRecipientUsername(Long id, String recipientUsername);

  List<Notification> findByStatus(NotificationStatus status);
}
