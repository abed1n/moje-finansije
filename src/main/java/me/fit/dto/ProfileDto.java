package me.fit.dto;

import me.fit.model.Profile;

import java.time.LocalDate;

public record ProfileDto(String address, String phone, LocalDate dateOfBirth) {

    public static ProfileDto from(Profile profile) {
        if (profile == null) {
            return null;
        }
        return new ProfileDto(profile.getAddress(), profile.getPhone(), profile.getDateOfBirth());
    }
}
