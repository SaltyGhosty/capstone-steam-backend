package com.capstone.steam.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.capstone.steam.notification.MailListener;
import com.capstone.steam.notification.event.UserRegisteredEvent;

class AsyncConfigTest {

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void asyncErrorLogNamesTheEventButNotItsData(CapturedOutput output) throws Exception {
        Method method = MailListener.class.getMethod("onUserRegistered", UserRegisteredEvent.class);

        new AsyncConfig().getAsyncUncaughtExceptionHandler().handleUncaughtException(
                new IllegalStateException("plantilla rota"), method,
                new UserRegisteredEvent(7L, "ana@test.com", "Ana", "es", null, null));

        assertThat(output.getAll())
                .contains("onUserRegistered", "UserRegisteredEvent", "plantilla rota")
                .doesNotContain("ana@test.com");
    }
}
