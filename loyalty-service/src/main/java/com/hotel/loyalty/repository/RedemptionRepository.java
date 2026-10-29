package com.hotel.loyalty.repository;

import com.hotel.loyalty.entity.Redemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    List<Redemption> findByUserId(Long userId);
}
