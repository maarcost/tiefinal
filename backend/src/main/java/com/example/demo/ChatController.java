package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor; // ¡IMPORTANTE!
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.VectorStore; // ¡IMPORTANTE!
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore; // Añadimos esto

    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore; // Lo guardamos
        
        ChatMemory memory = new InMemoryChatMemory();

        this.chatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(memory))
                .build();
    }

    @GetMapping("/api/chat")
    public String chat(@RequestParam(defaultValue = "Hola") String mensaje) {
        
        String systemPrompt = "Eres un experto en Ingeniería de Prompts. Responde basándote en la información proporcionada.";

        try {
            return this.chatClient.prompt()
                .system(systemPrompt)
                .user(mensaje)
                .advisors(
                    new MessageChatMemoryAdvisor(new InMemoryChatMemory()), // Memoria
                    new QuestionAnswerAdvisor(vectorStore)                 // RAG
                )
                .advisors(a -> a
                    .param(CHAT_MEMORY_CONVERSATION_ID_KEY, "usuario-123")
                    .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)
                )
                .call()
                .content();
        } catch (Exception e) {
            return "Error técnico: " + e.getMessage();
        }
    }
}