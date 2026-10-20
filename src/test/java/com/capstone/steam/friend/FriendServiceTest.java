package com.capstone.steam.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.friend.dto.FriendRequestResponse;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendServiceTest {

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private LibraryRepository libraryRepository;
    @InjectMocks private FriendService service;

    private User nine;
    private User three;

    @BeforeEach
    void setUp() {
        nine = player(9L, "nine");
        three = player(3L, "three");
        when(friendshipRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void friendshipStoresTheSmallerIdAsLowWhateverTheDirection() {
        Friendship nineAsksThree = new Friendship(nine, three);
        Friendship threeAsksNine = new Friendship(three, nine);

        assertThat(nineAsksThree.getUserLowId()).isEqualTo(3L);
        assertThat(nineAsksThree.getUserHighId()).isEqualTo(9L);
        assertThat(threeAsksNine.getUserLowId()).isEqualTo(3L);
        assertThat(threeAsksNine.getUserHighId()).isEqualTo(9L);
        assertThat(nineAsksThree.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(nineAsksThree.involves(9L)).isTrue();
        assertThat(nineAsksThree.involves(4L)).isFalse();
        assertThat(nineAsksThree.other(9L)).isSameAs(three);
        assertThat(nineAsksThree.other(3L)).isSameAs(nine);
    }

    @Test
    void sendLooksUpThePairAsLowHighAndSavesAPendingRequest() {
        when(friendshipRepository.findByPair(3L, 9L)).thenReturn(Optional.empty());

        FriendRequestResponse response = service.send(9L, "three");

        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(response.user().username()).isEqualTo("three");
        ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserLowId()).isEqualTo(3L);
        assertThat(saved.getValue().getUserHighId()).isEqualTo(9L);
        assertThat(saved.getValue().getRequester()).isSameAs(nine);
    }

    @Test
    void crossedRequestAcceptsTheExistingOneInsteadOfInsertingAnother() {
        Friendship existing = new Friendship(three, nine); // three ya había pedido a nine
        when(friendshipRepository.findByPair(3L, 9L)).thenReturn(Optional.of(existing));

        FriendRequestResponse response = service.send(9L, "three");

        assertThat(response.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(existing.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(existing.getRespondedAt()).isNotNull();
        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatingYourOwnRequestIsAConflict() {
        Friendship mine = new Friendship(nine, three);
        when(friendshipRepository.findByPair(3L, 9L)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.send(9L, "three")).isInstanceOf(ConflictException.class);
        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void requestingAnExistingFriendIsAConflict() {
        Friendship friends = new Friendship(three, nine);
        friends.accept();
        when(friendshipRepository.findByPair(3L, 9L)).thenReturn(Optional.of(friends));

        assertThatThrownBy(() -> service.send(9L, "three")).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotRequestYourself() {
        assertThatThrownBy(() -> service.send(9L, "nine")).isInstanceOf(BadRequestException.class);
        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownDevelopersAndDisabledTargetsAreNotFound() {
        User studio = player(5L, "studio");
        when(studio.hasRole(RoleName.ROLE_DEVELOPER)).thenReturn(true);
        User off = player(6L, "off");
        when(off.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.send(9L, "studio")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.send(9L, "off")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.send(9L, "ghost")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyPlayersCanSendRequests() {
        User admin = player(1L, "boss");
        when(admin.hasRole(RoleName.ROLE_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> service.send(1L, "three")).isInstanceOf(ForbiddenException.class);
        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelDeletesOnlyIfStillPendingAndIsNotFoundOtherwise() {
        Friendship pending = new Friendship(nine, three);
        when(friendshipRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(friendshipRepository.deletePending(5L)).thenReturn(0); // otro la aceptó entre medias

        assertThatThrownBy(() -> service.cancel(9L, 5L)).isInstanceOf(NotFoundException.class);
        verify(friendshipRepository, never()).delete(any());

        when(friendshipRepository.deletePending(5L)).thenReturn(1);
        service.cancel(9L, 5L);
        verify(friendshipRepository, org.mockito.Mockito.times(2)).deletePending(5L);
    }

    @Test
    void searchRejectsShortQueriesWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.search(9L, " a ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.search(9L, null)).isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).searchPlayers(any(), anyLong(), any());
    }

    @Test
    void searchEscapesLikeWildcards() {
        when(userRepository.searchPlayers(any(), anyLong(), any())).thenReturn(List.of());

        service.search(9L, "50%_x");

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(userRepository).searchPlayers(pattern.capture(), anyLong(), any());
        assertThat(pattern.getValue()).isEqualTo("%50!%!_x%");
    }

    private User player(long id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn(username);
        when(user.isEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(user.isEmailVerified()).thenReturn(true);
        when(userRepository.findWithRolesById(id)).thenReturn(Optional.of(user));
        when(userRepository.findWithRolesByUsernameIgnoreCase(username)).thenReturn(Optional.of(user));
        return user;
    }
}
