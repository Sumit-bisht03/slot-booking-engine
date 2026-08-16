package com.hackathon.slot_booking_engine.repository;

import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

   List<Slot> findByHostIdAndStatus(Long hostId, SlotStatus status);
   List<Slot> findByStatus(SlotStatus status);

   @Query("SELECT s FROM Slot s JOIN FETCH s.host WHERE s.id = :slotId")
   Optional<Slot> findByIdWithHost(@Param("slotId") Long slotId);

   @Query("SELECT S FROM Slot S WHERE " +
           "S.host.id = :hostId " +
           "AND S.status = :status " +
           "AND S.startTime >= :startTime" +
           " AND S.endTime <= :endTime")
   List<Slot> findAvailableSlotInRange(
           @Param("hostId") Long hostId,
           @Param("status") SlotStatus status,
           @Param("startTime")Instant startTime,
           @Param("endTime")Instant endTime);

   // True overlap check (as opposed to findAvailableSlotInRange, which only
   // matches slots fully contained within the range): an existing slot
   // conflicts with a proposed [startTime, endTime) window whenever it starts
   // before the new slot ends AND ends after the new slot starts.
   @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Slot s " +
           "WHERE s.host.id = :hostId " +
           "AND s.startTime < :endTime " +
           "AND s.endTime > :startTime")
   boolean existsOverlappingSlot(@Param("hostId") Long hostId,
                                  @Param("startTime") Instant startTime,
                                  @Param("endTime") Instant endTime);
}
