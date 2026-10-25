package com.capstone.steam.recommendation;

import java.util.List;

import com.capstone.steam.recommendation.dto.ChatMessage;

/** Il modello linguistico dietro la chat; dietro un'interfaccia per simularlo nei test. */
public interface ChatModel {

    /**
     * Chiede una risposta al modello.
     *
     * @return il testo del primo {@code choice}
     * @throws AssistantUnavailableException se non c'è la chiave, c'è un errore di rete o di quota, o la risposta non serve
     */
    String complete(String system, List<ChatMessage> messages);
}
