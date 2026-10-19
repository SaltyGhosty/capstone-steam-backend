package com.capstone.steam.recommendation;

import java.util.List;

import com.capstone.steam.recommendation.dto.ChatMessage;

/** El modelo de lenguaje detrás del chat; detrás de una interfaz para simularlo en los tests. */
public interface ChatModel {

    /**
     * Pide una respuesta al modelo.
     *
     * @return el texto del primer {@code choice}
     * @throws AssistantUnavailableException si no hay clave, hay error de red o de cupo, o la respuesta no sirve
     */
    String complete(String system, List<ChatMessage> messages);
}
