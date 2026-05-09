package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :id")
    void resetLoginAttempts(@Param("id") Long id);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime, u.lastLoginIp = :ip WHERE u.id = :id")
    void updateLastLogin(@Param("id") Long id,
                         @Param("loginTime") LocalDateTime loginTime,
                         @Param("ip") String ip);
}

