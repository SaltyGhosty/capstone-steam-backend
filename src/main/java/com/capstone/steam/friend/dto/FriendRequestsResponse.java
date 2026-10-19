package com.capstone.steam.friend.dto;

import java.util.List;

public record FriendRequestsResponse(List<FriendRequestResponse> incoming, List<FriendRequestResponse> outgoing) {
}
