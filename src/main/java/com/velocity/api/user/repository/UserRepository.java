package com.velocity.api.user.repository;

import com.velocity.api.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id <> :id")
    boolean isEmailTakenByAnotherUser(@Param("email") String email, @Param("id") UUID id);

    boolean existsByPhone(String phone); // register — no row to exclude

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phone = :phone AND u.id <> :id")
    boolean isPhoneTakenByAnotherUser(@Param("phone") String phone, @Param("id") UUID id); // update — exclude self
}
