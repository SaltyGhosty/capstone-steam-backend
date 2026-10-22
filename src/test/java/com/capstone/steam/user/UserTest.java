package com.capstone.steam.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private User user() {
        User u = new User("Juan", "Juan@Example.com", "hash-real");
        ReflectionTestUtils.setField(u, "id", 42L);
        u.setAvatarUrl("http://x/a.png");
        u.setProfilePublic(true);
        return u;
    }

    @Test
    void scheduleAndCancelDeletionToggleThePendingState() {
        User u = user();
        assertThat(u.isPendingDeletion()).isFalse();

        u.scheduleDeletion(NOW, DeletionRequester.ADMIN);
        assertThat(u.isPendingDeletion()).isTrue();
        assertThat(u.getDeletionScheduledAt()).isEqualTo(NOW);
        assertThat(u.getDeletionRequestedBy()).isEqualTo(DeletionRequester.ADMIN);

        u.cancelDeletion();
        assertThat(u.isPendingDeletion()).isFalse();
        assertThat(u.getDeletionScheduledAt()).isNull();
        assertThat(u.getDeletionRequestedBy()).isNull();
    }

    @Test
    void anonymizeWipesPersonalDataAndClearsTheScheduledDeletion() {
        User u = user();
        u.scheduleDeletion(NOW, DeletionRequester.SELF);

        u.anonymize("hash-inutilizable", NOW);

        assertThat(u.getUsername()).isEqualTo("deleted_42");
        assertThat(u.getEmail()).isEqualTo("deleted_42@deleted.invalid");
        assertThat(u.getPasswordHash()).isEqualTo("hash-inutilizable");
        assertThat(u.getDisplayName()).isNull();
        assertThat(u.getAvatarUrl()).isNull();
        assertThat(u.isEnabled()).isFalse();
        assertThat(u.isProfilePublic()).isFalse();
        assertThat(u.getDeletedAt()).isEqualTo(NOW);
        assertThat(u.isDeleted()).isTrue();
        assertThat(u.getDeletionScheduledAt()).isNull();
        assertThat(u.getDeletionRequestedBy()).isNull();
        assertThat(u.isPendingDeletion()).isFalse();
    }
}
