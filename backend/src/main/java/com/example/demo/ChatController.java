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
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final Map<String, String> cache = new HashMap<>();

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
        if (m.contains("lo de siempre")) return "PEDIDO";
        if (m.matches(".*(pedido flash|flash|urgente).*")) return "FLASH";
        if (m.matches(".*(stock|tenemos|queda|inventario|cantidad|hay de|quedan).*")) return "STOCK";
        if (m.matches(".*(receta|hornear|temperatura|ingredientes|mezcla|elaboracion|elaboración|preparar).*")) return "RECETA";
        if (m.matches(".*(proveedor|contacto|teléfono|telefono|entrega|suministro|llamo|llamar).*")) return "PROVEEDOR";
        if (m.matches(".*(comprar|pedir|ordenar|quiero.*unidades|realizar.*compra).*")) return "PEDIDO";
        return "GENERAL";
    }

    private boolean estaEnContexto(String mensaje) {
    String m = mensaje.toLowerCase().trim();
    
    // Mensajes cortos siempre pasan — son respuestas conversacionales
    if (m.split(" ").length <= 4) return true;
    
    // Bloquear solo temas claramente ajenos al negocio
    boolean temaAjeno = m.matches(".*(capital|presidente|futbol|fútbol|pelicula|película|" +
        "cancion|canción|musica|música|politica|política|deporte|" +
        "tiempo.*mañana|historia.*mundo|guerra|pais|país|continente|" +
        "matematica|matemática|fisica|física|quimica|química).*");
    
    return !temaAjeno;
    }

    private String normalizarTexto(String texto) {
    return texto.toLowerCase()
        .replaceAll("[áà]", "a").replaceAll("[éè]", "e")
        .replaceAll("[íì]", "i").replaceAll("[óò]", "o")
        .replaceAll("[úù]", "u").replaceAll("ñ", "n")
        .replaceAll("[^a-z0-9 ]", "").trim();
    }

    private String buscarEnCache(String mensaje) {
        String normalizado = normalizarTexto(mensaje);
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry.getKey().equals(normalizado)) return entry.getValue();
        }
        return null;
    }
    
    /* 
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
                FLUJO OBLIGATORIO:
                - Si el usuario NO ha confirmado aún: resume el pedido y pregunta "¿Confirmas este pedido?"
                - Si el usuario dice "sí", "si", "confirmo", "acepto", "adelante" o similar: ejecuta INMEDIATAMENTE realizarCompra con los ingredientes y cantidades del pedido. No vuelvas a preguntar.
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
*/  
    private String construirPrompt(String intencion) {
    String base = "Eres el Agente Operativo de Tartas Marco. REGLA ABSOLUTA: NUNCA ejecutes realizarCompra sin confirmación explícita del usuario.\n\n";

    return switch (intencion) {
        case "STOCK" -> base + 
            "Usa consultarStock para obtener el dato real del ingrediente. Cuando tengas el resultado, responde en lenguaje natural en máximo 2 líneas indicando la cantidad disponible. No muestres código ni llamadas a funciones.";
        
        case "RECETA" -> base + 
            "Responde con los ingredientes en lista y tiempos/temperaturas en tabla. Incluye planes de contingencia si los hay.";
        
        case "PROVEEDOR" -> base + 
            "Responde únicamente con: nombre del proveedor, persona de contacto y teléfono. Sin información adicional.";
        
        case "PEDIDO" -> base + 
            "JERGA: 'lo de siempre' = pedido estándar de 10 sacos de harina y 20 bloques de mantequilla. " +
            "FLUJO OBLIGATORIO: Si no hay confirmación aún, consulta el stock de los ingredientes necesarios, resume el pedido y pregunta '¿Confirmas este pedido?'. Si el usuario confirma con sí/confirmo/acepto, ejecuta realizarCompra inmediatamente.";
        case "FLASH" -> base +
            "El usuario necesita un Pedido Flash — entrega en menos de 2 horas, solo disponible para Clientes VIP (Restaurante La Lonja y Hotel Ritz). " +
            "Pregunta qué producto necesita y confirma que el destino es un cliente VIP antes de proceder.";    
        
        default -> base + 
            "Usa títulos, listas y tablas para estructurar la respuesta. Sé conciso y directo.";
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

        // Bypass de ayuda
        if (input.trim().equals("ayuda") || input.trim().equals("help") || input.trim().equals("?")) {
            return new OrchestratorResponse(
                """
                👋 **Bienvenido al asistente de Tartas Artesanas Marco**
                
                Aquí tienes todo lo que puedo hacer por ti:
                
                📦 **Stock e inventario**
                - ¿Cuánta harina tenemos?
                - ¿Cuánto chocolate queda en stock?
                
                🛒 **Pedidos y compras**
                - Quiero comprar 10 sacos de harina
                - Quiero hacer lo de siempre
                
                🎂 **Recetas y elaboración**
                - ¿Cómo se prepara la tarta de Marco?
                - ¿Qué hago si no hay queso de cabra?
                
                🚚 **Proveedores y logística**
                - ¿Quién es nuestro proveedor de lácteos?
                - ¿Cuál es el horario de reparto?
                - ¿Quiénes son nuestros clientes VIP?
                
                ⚡ **Pedidos urgentes**
                - Necesito un pedido flash
                
                🔧 **Protocolos y contingencias**
                - ¿Qué hacemos si falla la electricidad?
                - ¿Cuándo es la próxima revisión del horno?
                """,
                "AYUDA - Bypass activado",
                0, 0.0
            );
        }

        // 2. Filtro de contexto empresarial
        if (!estaEnContexto(mensaje)) {
            return new OrchestratorResponse(
                "Lo siento, solo puedo ayudarte con consultas relacionadas con Tartas Artesanas Marco — stock, pedidos, recetas, proveedores y logística. ¿En qué puedo ayudarte?",
                "FUERA_DE_CONTEXTO - Bypass activado",
                0, 0.0
            );
        }

        // 3. Buscar en caché
        String cached = buscarEnCache(mensaje);
        if (cached != null) {
            return new OrchestratorResponse(cached, "CACHÉ - 0 tokens consumidos", 0, 0.0);
        }

        // 4. Clasificar intención y construir prompt dinámico
        String intencion = clasificarIntencion(mensaje);
        String systemPrompt = construirPrompt(intencion);

        try {
            var prompt = this.chatClient.prompt()
                    .system(systemPrompt)
                    .user(mensaje)
                    .functions("consultarStock", "realizarCompra");

            if (intencion.equals("GENERAL") || intencion.equals("RECETA") || intencion.equals("PROVEEDOR") || intencion.equals("PEDIDO") || intencion.equals("FLASH")) {
                prompt = prompt.advisors(new QuestionAnswerAdvisor(vectorStore,SearchRequest.defaults().withTopK(2)));
            }

            var response = prompt.call().chatResponse();

            long totalTokens = response.getMetadata().getUsage().getTotalTokens();
            double costeEstimado = (totalTokens / 1000.0) * 0.00015;

            String respuesta = response.getResult().getOutput().getContent();

            // Solo cachear preguntas que no dependen de stock en tiempo real
            if (!intencion.equals("STOCK") && !intencion.equals("PEDIDO") && mensaje.split(" ").length > 4) {
                cache.put(normalizarTexto(mensaje), respuesta);
            }

            return new OrchestratorResponse(respuesta, "Intención: " + intencion, totalTokens, costeEstimado);

        } catch (Exception e) {
            return new OrchestratorResponse("Error: " + e.getMessage(), "Fallo en inferencia", 0, 0.0);
        }
    }
}

@Configuration
class AiTools {

    private Map<String, Integer> stock = new HashMap<>(Map.of("Harina", 5, "Queso Crema", 10, "Chocolate", 2, "Mantequilla", 15));

    @Bean
    @Description("SIEMPRE usa esta función cuando el usuario pregunte por el stock, cantidad disponible o inventario de cualquier ingrediente. Devuelve el stock real en tiempo real.")
    public Function<StockRequest, String> consultarStock() {
        return request -> {
            String ingrediente = request.ingrediente().split(" ")[0];
            String key = ingrediente.substring(0, 1).toUpperCase() 
                    + ingrediente.substring(1).toLowerCase();
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