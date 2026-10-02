package com.ordersphere.notification.service;

import com.ordersphere.notification.domain.NotificationPreference;
import com.ordersphere.notification.dto.CreateNotificationPreferenceRequest;
import com.ordersphere.notification.dto.NotificationPreferenceResponse;
import com.ordersphere.notification.exception.NotificationPreferenceNotFoundException;
import com.ordersphere.notification.repository.NotificationPreferenceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository preferenceRepository;

  public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
    this.preferenceRepository = preferenceRepository;
  }

  /**
   * Upsert per (user, channel). Two saves of a new channel at the same moment both find nothing and
   * both insert; the unique constraint rejects one, which then updates the row the other created.
   */
  public NotificationPreferenceResponse setPreference(
      String username, CreateNotificationPreferenceRequest request) {
    try {
      return upsert(username, request);
    } catch (DataIntegrityViolationException createdConcurrently) {
      return upsert(username, request);
    }
  }

  private NotificationPreferenceResponse upsert(
      String username, CreateNotificationPreferenceRequest request) {
    NotificationPreference preference =
        preferenceRepository
            .findByUsernameAndChannel(username, request.channel())
            .orElseGet(
                () -> new NotificationPreference(username, request.channel(), request.enabled()));
    preference.setEnabled(request.enabled());
    preference.setUpdatedAt(Instant.now());
    return NotificationPreferenceResponse.from(preferenceRepository.save(preference));
  }

  @Transactional(readOnly = true)
  public List<NotificationPreferenceResponse> listPreferences(String username) {
    return preferenceRepository.findByUsername(username).stream()
        .map(NotificationPreferenceResponse::from)
        .toList();
  }

  public void deletePreference(String username, Long id) {
    NotificationPreference preference =
        preferenceRepository
            .findByIdAndUsername(id, username)
            .orElseThrow(() -> new NotificationPreferenceNotFoundException(id));
    preferenceRepository.delete(preference);
  }
}
