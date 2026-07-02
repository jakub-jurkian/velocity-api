package com.velocity.api.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserDto {
    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private String role; // String for API flexibility
    private String status;
    private String city;
}
