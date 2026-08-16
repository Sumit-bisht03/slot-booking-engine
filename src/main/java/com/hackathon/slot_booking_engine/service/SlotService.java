package com.hackathon.slot_booking_engine.service;

import com.hackathon.slot_booking_engine.dto.SlotCreateRequestDto;
import com.hackathon.slot_booking_engine.dto.SlotResponseDto;
import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.User;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import com.hackathon.slot_booking_engine.entity.enums.UserRole;
import com.hackathon.slot_booking_engine.exception.InvalidSlotRequestException;
import com.hackathon.slot_booking_engine.exception.ResourceNotFoundException;
import com.hackathon.slot_booking_engine.repository.SlotRepository;
import com.hackathon.slot_booking_engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    @CacheEvict(value = {"availableSlots", "slotDetails"}, allEntries = true)
    @Transactional
    public SlotResponseDto createSlot(SlotCreateRequestDto request) {
        User host = userRepository.findById(request.getHostId())
                .orElseThrow(() -> new ResourceNotFoundException("Host not found with ID: " + request.getHostId()));

        if (host.getRole() != UserRole.HOST) {
            throw new InvalidSlotRequestException("User with ID " + request.getHostId() + " is not a valid Host");
        }

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new InvalidSlotRequestException("Start time must be before end time");
        }

        if (slotRepository.existsOverlappingSlot(host.getId(), request.getStartTime(), request.getEndTime())) {
            throw new InvalidSlotRequestException("This host already has a slot that overlaps with the requested time range");
        }

        Slot slot = Slot.builder()
                .host(host)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SlotStatus.AVAILABLE)
                .build();

        Slot savedSlot = slotRepository.save(slot);
        log.info("Slot created: id={}, hostId={}, startTime={}, endTime={}",
                savedSlot.getId(), host.getId(), savedSlot.getStartTime(), savedSlot.getEndTime());
        return mapToDto(savedSlot);
    }

    @Cacheable(value = "availableSlots", key = "'all'")
    @Transactional(readOnly = true)
    public List<SlotResponseDto> getAllAvailableSlots(){
        List<Slot> slot = slotRepository.findByStatus(SlotStatus.AVAILABLE);
        return slot.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "slotDetails", key = "#hostId")
    @Transactional(readOnly = true)
    public List<SlotResponseDto> getAvailableSlotsForHost(Long hostId){
        if(!userRepository.existsById(hostId)){
            throw new ResourceNotFoundException("Host not found with ID: " + hostId);
        }
        List<Slot> slot = slotRepository.findByHostIdAndStatus(hostId, SlotStatus.AVAILABLE);
        return slot.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Slot getSlotEntityByID(Long slotId){
        Slot slot = slotRepository.findByIdWithHost(slotId).orElseThrow(() ->
                new ResourceNotFoundException("Slot not found with ID: " + slotId));
        return slot;
    }

    public SlotResponseDto mapToDto(Slot slot){
        return SlotResponseDto.builder()
                .slotId(slot.getId())
                .hostId(slot.getHost().getId())
                .hostName(slot.getHost().getName())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .build();
    }
}
