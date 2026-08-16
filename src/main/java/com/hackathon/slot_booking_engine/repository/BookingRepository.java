package com.hackathon.slot_booking_engine.repository;

import com.hackathon.slot_booking_engine.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findBySlotId(Long slotId);

    List<Booking> findByClientId(Long clientId);
}
