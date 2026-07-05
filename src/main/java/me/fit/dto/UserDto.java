package me.fit.dto;

import me.fit.model.Role;
import me.fit.model.User;

import java.time.Instant;

public record UserDto(Long id, String name, String email, Role role, Instant createdAt, ProfileDto profile) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getCreatedAt(), ProfileDto.from(user.getProfile()));
    }
}
