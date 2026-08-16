package com.hackathon.slot_booking_engine.config;

import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.User;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import com.hackathon.slot_booking_engine.entity.enums.UserRole;
import com.hackathon.slot_booking_engine.repository.SlotRepository;
import com.hackathon.slot_booking_engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SlotRepository slotRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if( userRepository.count() > 0) {
            log.info("Data Seeder: Database already contains data. Skipping seeding.");
            return;
        }

        log.info("Data Seeder: Initializing test data...");

        // 1. Create Hosts
        User host1 = userRepository.save(User.builder()
                .name("Alice Host")
                .email("alice.host@slotify.com")
                .role(UserRole.HOST).build());

        User host2 = userRepository.save(User.builder()
                .name("Bob Host")
                .email("bob.host@slotify.com")
                .role(UserRole.HOST)
                .build());

        // 2. Create Clients
        User client1 = userRepository.save(User.builder()
                .name("Charlie Client")
                .email("charlie.client@gmail.com")
                .role(UserRole.CLIENT)
                .build());

        User client2 = userRepository.save(User.builder()
                .name("Diana Client")
                .email("diana.client@gmail.com")
                .role(UserRole.CLIENT)
                .build());

        // 3. Create Available Time Slots for Host 1 (Tomorrow)
        Instant baseTime = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        Slot slot1 = Slot.builder()
                .host(host1)
                .startTime(baseTime.plus(9, ChronoUnit.HOURS))   // 09:00 AM tomorrow
                .endTime(baseTime.plus(10, ChronoUnit.HOURS))   // 10:00 AM tomorrow
                .status(SlotStatus.AVAILABLE)
                .build();

        Slot slot2 = Slot.builder()
                .host(host1)
                .startTime(baseTime.plus(11, ChronoUnit.HOURS))  // 11:00 AM tomorrow
                .endTime(baseTime.plus(12, ChronoUnit.HOURS))  // 12:00 PM tomorrow
                .status(SlotStatus.AVAILABLE)
                .build();

        Slot slot3 = Slot.builder()
                .host(host1)
                .startTime(baseTime.plus(14, ChronoUnit.HOURS))  // 02:00 PM tomorrow
                .endTime(baseTime.plus(15, ChronoUnit.HOURS))  // 03:00 PM tomorrow
                .status(SlotStatus.AVAILABLE)
                .build();

        // 4. Create Available Time Slots for Host 2 (Day after tomorrow)
        Instant host2Base = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        Slot slot4 = Slot.builder()
                .host(host2)
                .startTime(host2Base.plus(10, ChronoUnit.HOURS))
                .endTime(host2Base.plus(11, ChronoUnit.HOURS))
                .status(SlotStatus.AVAILABLE)
                .build();

        Slot slot5 = Slot.builder()
                .host(host2)
                .startTime(host2Base.plus(13, ChronoUnit.HOURS))
                .endTime(host2Base.plus(14, ChronoUnit.HOURS))
                .status(SlotStatus.AVAILABLE)
                .build();

        slotRepository.saveAll(List.of(slot1, slot2, slot3, slot4, slot5));
        log.info("Data Seeder: Initialized {} users and {} slots successfully!", userRepository.count(), slotRepository.count());
    }
}
