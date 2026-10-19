package com.capstone.steam.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFriendRequest(@NotBlank @Size(max = 50) String username) {
}
