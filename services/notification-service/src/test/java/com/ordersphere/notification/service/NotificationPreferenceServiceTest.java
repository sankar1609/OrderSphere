package com.ordersphere.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationPreference;
import com.ordersphere.notification.dto.CreateNotificationPreferenceRequest;
import com.ordersphere.notification.dto.NotificationPreferenceResponse;
import com.ordersphere.notification.exception.NotificationPreferenceNotFoundException;
import com.ordersphere.notification.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

  @Mock private NotificationPreferenceRepository preferenceRepository;

  private NotificationPreferenceService preferenceService;

  @BeforeEach
  void setUp() {
    preferenceService = new NotificationPreferenceService(preferenceRepository);
  }

  @Test
  void setPreferenceCreatesWhenNoneExists() {
    when(preferenceRepository.findByUsernameAndChannel("alice", NotificationChannel.SMS))
        .thenReturn(Optional.empty());
    when(preferenceRepository.save(any(NotificationPreference.class)))
        .thenAnswer(
            invocation -> {
              NotificationPreference preference = invocation.getArgument(0);
              preference.setId(1L);
              return preference;
            });

    NotificationPreferenceResponse response =
        preferenceService.setPreference(
            "alice", new CreateNotificationPreferenceRequest(NotificationChannel.SMS, false));

    assertThat(response.channel()).isEqualTo(NotificationChannel.SMS);
    assertThat(response.enabled()).isFalse();
  }

  @Test
  void setPreferenceUpdatesExisting() {
    NotificationPreference existing =
        new NotificationPreference("alice", NotificationChannel.SMS, true);
    existing.setId(1L);
    when(preferenceRepository.findByUsernameAndChannel("alice", NotificationChannel.SMS))
        .thenReturn(Optional.of(existing));
    when(preferenceRepository.save(existing)).thenReturn(existing);

    NotificationPreferenceResponse response =
        preferenceService.setPreference(
            "alice", new CreateNotificationPreferenceRequest(NotificationChannel.SMS, false));

    assertThat(response.enabled()).isFalse();
  }

  @Test
  void listPreferencesReturnsOnlyCallersPreferences() {
    NotificationPreference preference =
        new NotificationPreference("alice", NotificationChannel.EMAIL, true);
    when(preferenceRepository.findByUsername("alice")).thenReturn(List.of(preference));

    List<NotificationPreferenceResponse> responses = preferenceService.listPreferences("alice");

    assertThat(responses).hasSize(1);
  }

  @Test
  void deletePreferenceRemovesOwnedPreference() {
    NotificationPreference preference =
        new NotificationPreference("alice", NotificationChannel.EMAIL, true);
    when(preferenceRepository.findByIdAndUsername(1L, "alice")).thenReturn(Optional.of(preference));

    preferenceService.deletePreference("alice", 1L);

    verify(preferenceRepository).delete(preference);
  }

  @Test
  void deletePreferenceRejectsNonOwner() {
    when(preferenceRepository.findByIdAndUsername(1L, "bob")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> preferenceService.deletePreference("bob", 1L))
        .isInstanceOf(NotificationPreferenceNotFoundException.class);
  }
}
