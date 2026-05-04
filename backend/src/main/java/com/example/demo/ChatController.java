package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // Record optimizado para el Frontend
    public record OrchestratorResponse(
        String respuestaFinal,
        String razonamiento,
        long tokens,
        double coste
    ) {}

    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    @GetMapping("/api/chat")
    public OrchestratorResponse chat(@RequestParam(defaultValue = "Hola") String mensaje) {
        // 1. Valla de Seguridad & Ahorro de Tokens (Bypass manual)
        String input = mensaje.toLowerCase();
        if (input.matches(".*(hola|buenos dias|que tal|saludos).*") && mensaje.length() < 15) {
            return new OrchestratorResponse("¡Hola! Soy tu asistente de Tartas Marco. ¿En qué puedo ayudarte?", "Cortesía", 0, 0.0);
        }

        // 2. System Prompt: Vallas de seguridad e instrucciones de comportamiento
        String systemPrompt = """
            Eres el Agente Operativo de Tartas Marco.
            REGLA ABSOLUTA: NUNCA ejecutes la función realizarCompra sin que el usuario haya usado explícitamente palabras como "comprar", "pedir", "ordenar" o "quiero X unidades". Informar sobre stock bajo NO es autorización para comprar. Ante cualquier duda, pregunta antes de actuar. 
            FORMATO DE SALIDA:
            1. Usa títulos con '#' para secciones importantes.
            2. Usa tablas para comparar datos (como tiempos de horneado o stock).
            3. Usa listas con viñetas para pasos o ingredientes.
            4. Usa bloques de 'quote' (>) para advertencias o notas importantes.
            5. EVITA las negritas (**) en frases largas; úsalas solo para términos clave.
            6. NUNCA realices una compra o pedido sin que el usuario lo haya solicitado explícitamente.
            7. Si el stock es bajo, informa al usuario pero espera su confirmación antes de actuar.
            """;

        try {
            var response = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user(mensaje)
                    .functions("consultarStock", "realizarCompra") // Llamada a las herramientas
                    .advisors(new QuestionAnswerAdvisor(vectorStore))
                    .call()
                    .chatResponse();

            // 3. Métricas de Uso
            long totalTokens = response.getMetadata().getUsage().getTotalTokens();
            double costeEstimado = (totalTokens / 1000.0) * 0.00015; // Estimación coste modelo mini

            return new OrchestratorResponse(
                response.getResult().getOutput().getContent(),
                "Procesado con RAG y Function Calling",
                totalTokens,
                costeEstimado
            );

        } catch (Exception e) {
            return new OrchestratorResponse("Error: " + e.getMessage(), "Fallo en inferencia", 0, 0.0);
        }
    }
}

/** * CONFIGURACIÓN DE HERRAMIENTAS (Tools)
 * Estas funciones permiten que la IA interactúe con el sistema.
 */
@Configuration
class AiTools {
    private Map<String, Integer> stock = new HashMap<>(Map.of("Harina", 5, "Queso Crema", 10, "Chocolate", 2));

    @Bean
    @Description("SIEMPRE usa esta función cuando el usuario pregunte por el stock, cantidad disponible o inventario de cualquier ingrediente. Devuelve el stock real en tiempo real.")
    public Function<StockRequest, String> consultarStock() {
        return request -> {
            String key = request.ingrediente().substring(0, 1).toUpperCase() + request.ingrediente().substring(1).toLowerCase();
            Integer cantidad = stock.getOrDefault(key, 0);
            return "Stock de " + request.ingrediente() + ": " + cantidad + " unidades.";
        };
    }

    @Bean
    @Description("Realiza una compra de un ingrediente SOLO cuando el usuario lo pide explícitamente con verbos como comprar, pedir, ordenar o similar. NUNCA llamar a esta función solo porque el stock sea bajo.")
    public Function<OrderRequest, String> realizarCompra() {
        return request -> {
            if (request.cantidad() > 50) return "Error: Capacidad de almacén excedida.";
            return "ORDEN PROCESADA: " + request.cantidad() + " unidades de " + request.ingrediente();
        };
    }
}

record StockRequest(String ingrediente) {}
record OrderRequest(String ingrediente, int cantidad) {}