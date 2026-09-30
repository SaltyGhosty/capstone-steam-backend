package com.capstone.steam.user.dto;

import java.util.Set;

import com.capstone.steam.user.RoleName;

import jakarta.validation.constraints.NotEmpty;

public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
}
