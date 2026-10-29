package com.hotel.booking.repository;

import com.hotel.booking.entity.SeasonalRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonalRateRepository extends JpaRepository<SeasonalRate, Long> {

    List<SeasonalRate> findByActiveTrue();
}
