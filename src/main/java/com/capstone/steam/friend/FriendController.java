package com.capstone.steam.friend;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.friend.dto.ConversationResponse;
import com.capstone.steam.friend.dto.FriendActivityResponse;
import com.capstone.steam.friend.dto.FriendRequestResponse;
import com.capstone.steam.friend.dto.FriendRequestsResponse;
import com.capstone.steam.friend.dto.FriendResponse;
import com.capstone.steam.friend.dto.SendFriendRequest;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final ChatService chatService;

    public FriendController(FriendService friendService, ChatService chatService) {
        this.friendService = friendService;
        this.chatService = chatService;
    }

    @GetMapping
    public List<FriendResponse> friends(@AuthenticationPrincipal AppUserPrincipal principal) {
        return friendService.friends(principal.getId());
    }

    /** Lo pendiente (solicitudes y mensajes sin leer). */
    @GetMapping("/activity")
    public FriendActivityResponse activity(@AuthenticationPrincipal AppUserPrincipal principal) {
        return chatService.activity(principal.getId());
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> conversations(@AuthenticationPrincipal AppUserPrincipal principal) {
        return chatService.conversations(principal.getId());
    }

    @GetMapping("/requests")
    public FriendRequestsResponse requests(@AuthenticationPrincipal AppUserPrincipal principal) {
        return friendService.requests(principal.getId());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse send(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody SendFriendRequest request) {
        return friendService.send(principal.getId(), request.username());
    }

    @PostMapping("/requests/{id}/accept")
    public FriendRequestResponse accept(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return friendService.accept(principal.getId(), id);
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        friendService.reject(principal.getId(), id);
    }

    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        friendService.cancel(principal.getId(), id);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String username) {
        friendService.remove(principal.getId(), username);
    }
}
