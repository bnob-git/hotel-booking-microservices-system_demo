package com.hotel.loyalty.repository;

import com.hotel.loyalty.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByUserId(Long userId);

    // Serialises balance changes from the Kafka consumer and REST redemptions for the same user.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LoyaltyAccount a WHERE a.userId = :userId")
    Optional<LoyaltyAccount> findByUserIdForUpdate(@Param("userId") Long userId);
}
