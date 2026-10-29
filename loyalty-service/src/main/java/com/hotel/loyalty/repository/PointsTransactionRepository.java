package com.hotel.loyalty.repository;

import com.hotel.loyalty.entity.PointsTransaction;
import com.hotel.loyalty.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    boolean existsBySourceEventId(UUID sourceEventId);

    boolean existsByBookingIdAndType(Long bookingId, TransactionType type);

    List<PointsTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointsTransaction t "
            + "WHERE t.bookingId = :bookingId AND t.type = :type")
    long sumPointsByBookingIdAndType(@Param("bookingId") Long bookingId, @Param("type") TransactionType type);
}
