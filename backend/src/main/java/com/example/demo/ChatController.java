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

    // ─── CLASIFICADOR DE INTENCIÓN ───
    private String clasificarIntencion(String mensaje) {
        String m = mensaje.toLowerCase();
        if (m.matches(".*(stock|tenemos|queda|inventario|cantidad|hay de|quedan).*")) return "STOCK";
        if (m.matches(".*(receta|hornear|temperatura|ingredientes|mezcla|elaboracion|elaboración|preparar).*")) return "RECETA";
        if (m.matches(".*(proveedor|contacto|teléfono|telefono|entrega|suministro|llamo|llamar).*")) return "PROVEEDOR";
        if (m.matches(".*(comprar|pedir|ordenar|quiero.*unidades|realizar.*compra|pedido flash|flash).*")) return "PEDIDO";
        return "GENERAL";
    }

    // ─── PROMPT DINÁMICO ───
    private String construirPrompt(String intencion) {
        String base = "Eres el Agente Operativo de Tartas Marco. REGLA ABSOLUTA: NUNCA ejecutes realizarCompra sin que el usuario lo pida explícitamente con palabras como comprar, pedir u ordenar.\n\n";

        return switch (intencion) {
            case "STOCK" -> base + """
                El usuario pregunta por inventario. 
                USA SIEMPRE la función consultarStock para obtener el dato real.
                Responde de forma breve: indica el stock actual y si es bajo sugiere reponer, pero NO compres sin confirmación.
                """;
            case "RECETA" -> base + """
                El usuario pregunta por recetas o procesos de elaboración.
                Usa tablas para tiempos y temperaturas. Usa listas para ingredientes.
                Incluye notas importantes con '>' si hay planes de contingencia relevantes.
                """;
            case "PROVEEDOR" -> base + """
                El usuario pregunta por proveedores o contactos.
                Responde con nombre del proveedor, persona de contacto y teléfono si está disponible.
                Sé directo y conciso, sin información adicional innecesaria.
                """;
            case "PEDIDO" -> base + """
                El usuario quiere realizar un pedido o compra.
                OBLIGATORIO: Antes de ejecutar realizarCompra, resume el pedido y pregunta explícitamente "¿Confirmas este pedido?" y espera respuesta del usuario.
                NUNCA ejecutes realizarCompra en el mismo mensaje en que el usuario hace la petición.
                Recuerda las condiciones de Pedido Flash si aplica: solo clientes VIP, menos de 2 horas.
                """;
            default -> base + """
                FORMATO DE SALIDA:
                1. Usa títulos con '#' para secciones importantes.
                2. Usa tablas para comparar datos.
                3. Usa listas con viñetas para pasos o ingredientes.
                4. Usa bloques '>' para advertencias o notas importantes.
                5. EVITA las negritas en frases largas, úsalas solo para términos clave.
                6. NUNCA realices una compra sin que el usuario lo haya solicitado explícitamente.
                7. Si el stock es bajo, informa pero espera confirmación antes de actuar.
                """;
        };
    }

    @GetMapping("/api/chat")
    public OrchestratorResponse chat(@RequestParam(defaultValue = "Hola") String mensaje) {

        // 1. Bypass de saludos
        String input = mensaje.toLowerCase();
        if (input.matches(".*(hola|buenos dias|que tal|saludos).*") && mensaje.length() < 15) {
            return new OrchestratorResponse(
                "¡Hola! Soy tu asistente de Tartas Marco. ¿En qué puedo ayudarte?",
                "SALUDO - Bypass activado",
                0, 0.0
            );
        }

        // 2. Clasificar intención y construir prompt dinámico
        String intencion = clasificarIntencion(mensaje);
        String systemPrompt = construirPrompt(intencion);

        try {
            var response = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user(mensaje)
                    .functions("consultarStock", "realizarCompra")
                    .advisors(new QuestionAnswerAdvisor(vectorStore))
                    .call()
                    .chatResponse();

            long totalTokens = response.getMetadata().getUsage().getTotalTokens();
            double costeEstimado = (totalTokens / 1000.0) * 0.00015;

            return new OrchestratorResponse(
                response.getResult().getOutput().getContent(),
                "Intención: " + intencion,
                totalTokens,
                costeEstimado
            );

        } catch (Exception e) {
            return new OrchestratorResponse("Error: " + e.getMessage(), "Fallo en inferencia", 0, 0.0);
        }
    }
}

@Configuration
class AiTools {
    private Map<String, Integer> stock = new HashMap<>(Map.of("Harina", 5, "Queso Crema", 10, "Chocolate", 2));

    @Bean
    @Description("SIEMPRE usa esta función cuando el usuario pregunte por el stock, cantidad disponible o inventario de cualquier ingrediente. Devuelve el stock real en tiempo real.")
    public Function<StockRequest, String> consultarStock() {
        return request -> {
            String key = request.ingrediente().substring(0, 1).toUpperCase() 
                       + request.ingrediente().substring(1).toLowerCase();
            Integer cantidad = stock.getOrDefault(key, 0);
            return "Stock de " + key + ": " + cantidad + " unidades.";
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