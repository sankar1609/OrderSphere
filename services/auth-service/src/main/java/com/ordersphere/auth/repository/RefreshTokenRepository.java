package com.ordersphere.auth.repository;

import com.ordersphere.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /** Row lock: two concurrent refreshes with the same token must not both succeed. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.user.id = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
