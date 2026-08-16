package com.hackathon.slot_booking_engine.repository;

import com.hackathon.slot_booking_engine.entity.User;
import com.hackathon.slot_booking_engine.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
}
