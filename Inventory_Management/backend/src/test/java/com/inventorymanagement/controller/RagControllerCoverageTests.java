package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.ChatRequest;
import com.inventorymanagement.dto.response.ChatResponse;
import com.inventorymanagement.service.RagChatService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagControllerCoverageTests {

    @Test
    void asksLiveChatAndReturnsJsonShape() {
        RagChatService ragChatService = mock(RagChatService.class);
        RagController controller = new RagController(ragChatService);

        when(ragChatService.ask("What is the dashboard status?")).thenReturn(
                ChatResponse.builder()
                        .question("What is the dashboard status?")
                        .answer("Total products: 100")
                        .sourceCount(1)
                        .build()
        );

        ChatRequest request = new ChatRequest();
        request.setQuestion("What is the dashboard status?");

        ChatResponse response = controller.ask(request).getBody();
        assertEquals("What is the dashboard status?", response.getQuestion());
        assertEquals("Total products: 100", response.getAnswer());
        assertEquals(1, response.getSourceCount());
    }
}

