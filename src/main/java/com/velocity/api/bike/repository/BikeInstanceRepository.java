package com.velocity.api.bike.repository;

import com.velocity.api.bike.BikeInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BikeInstanceRepository extends JpaRepository<BikeInstance, UUID> {

}
