package ru.rpovetkin.front_ui.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {
    private String login;
    private String password;
    private String confirmPassword;
    private String name;
    private String birthdate;
}
