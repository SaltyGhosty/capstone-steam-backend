package com.capstone.steam.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.capstone.steam.recommendation.dto.ChatMessage;
import com.capstone.steam.recommendation.dto.ChatMessage.Role;

/**
 * Sin {@code @Transactional} en la clase: así se ve si el servicio abre una transacción
 * (y retiene una conexión) mientras espera al modelo.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendationTransactionTest {

    @Autowired private RecommendationService service;
    @MockitoBean private ChatModel model;

    @Test
    void theModelIsCalledWithoutAnActiveTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        when(model.complete(anyString(), any())).thenAnswer(call -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return "{\"reply\":\"ok\",\"gameIds\":[]}";
        });

        service.chat(9_001L, List.of(new ChatMessage(Role.user, "algo oscuro")));

        assertThat(inTransaction).isFalse();
    }
}
