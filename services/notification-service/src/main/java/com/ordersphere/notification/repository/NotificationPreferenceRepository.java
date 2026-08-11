package com.ordersphere.notification.repository;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, Long> {

  List<NotificationPreference> findByUsername(String username);

  Optional<NotificationPreference> findByUsernameAndChannel(
      String username, NotificationChannel channel);

  Optional<NotificationPreference> findByIdAndUsername(Long id, String username);
}
