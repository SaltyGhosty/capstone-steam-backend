package com.capstone.steam.friend;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.friend.dto.MessageResponse;
import com.capstone.steam.friend.dto.SendMessageRequest;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/friends/{username}/messages")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<MessageResponse> messages(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String username, @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {
        return chatService.messages(principal.getId(), username, before, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String username,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(principal.getId(), username, request.body());
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String username) {
        chatService.markRead(principal.getId(), username);
    }
}
