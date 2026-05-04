# 🎂 Asistente IA — Tartas Artesanas Marco

<p align="center">
  <img src="assets/demo.png" alt="Demo del asistente" width="800"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_AI-1.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black"/>
  <img src="https://img.shields.io/badge/LLaMA_3.3_70B-Groq-F55036?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
</p>

---

## 📌 ¿Qué es este proyecto?

Asistente conversacional empresarial desarrollado como Trabajo Final de la asignatura **Tecnologías de Información Emergentes** (4º curso, USAL). El sistema combina técnicas avanzadas de IA generativa para ofrecer un chatbot optimizado en consumo de tokens, contextualizado con el conocimiento interno de una empresa real.

El caso de uso es **Tartas Artesanas Marco**, una pastelería artesanal con operativa compleja — proveedores, recetas exclusivas, gestión de stock y clientes VIP — que sirve de entorno de demostración realista.

---

## 🧠 Tecnologías principales

| Capa | Tecnología | Origen |
|------|-----------|--------|
| Frontend | React + Vite | Seminario HP |
| Backend | Spring Boot 3.4.1 | Seminario Viewnext |
| IA | Spring AI | Seminario Viewnext |
| LLM | LLaMA 3.3 70B vía Groq | Decisión técnica propia |
| Vector Store | SimpleVectorStore | Spring AI |

---

## ⚙️ Funcionalidades implementadas

### 🔍 RAG — Retrieval-Augmented Generation
El sistema vectoriza el conocimiento interno de la empresa (`datos.txt`) al arrancar. Cada consulta recupera automáticamente los fragmentos más relevantes e inyecta ese contexto en el prompt antes de llamar al LLM. El asistente responde como si conociera la empresa porque en cada llamada recibe la información pertinente.

### 🛠️ Function Calling
El LLM puede invocar herramientas reales de forma autónoma cuando la situación lo requiere:
- `consultarStock` — devuelve el inventario actual de un ingrediente en tiempo real
- `realizarCompra` — procesa una orden de compra, solo con confirmación explícita del usuario

### 🧠 Memoria de conversación
`InMemoryChatMemory` mantiene el historial de la conversación e inyecta el contexto en cada nueva petición, permitiendo diálogos coherentes y encadenados sin que el usuario repita información.

### ⚡ Optimización de tokens

El objetivo central del proyecto es minimizar el coste por interacción. Se implementan tres técnicas:

**1. Bypass de saludos**
Los saludos simples se interceptan antes de llegar al LLM y reciben una respuesta fija preprogramada. Coste: 0 tokens.

**2. Clasificador de intención + Prompt dinámico**
Cada mensaje se clasifica antes de llamar al LLM en una de estas categorías:

```
SALUDO → bypass directo (0 tokens)
STOCK → prompt corto + consultarStock
RECETA → prompt técnico con formato de tabla
PROVEEDOR → prompt de datos de contacto
PEDIDO → prompt con confirmación obligatoria
GENERAL → prompt completo (fallback)
```

El prompt se construye dinámicamente según la intención detectada, enviando solo las instrucciones necesarias para ese tipo de consulta.

**3. RAG selectivo**
El `QuestionAnswerAdvisor` solo se activa para intenciones que realmente necesitan contexto documental (GENERAL, RECETA, PROVEEDOR). Las consultas de STOCK y PEDIDO no cargan fragmentos del vector store innecesariamente.

**4. Caché de respuestas frecuentes**
Las respuestas a preguntas no volátiles (recetas, proveedores, protocolos) se cachean en memoria. La segunda vez que se formula la misma pregunta, el sistema devuelve la respuesta cacheada sin llamar al LLM.

### 📊 Impacto medido

| Tipo de consulta | Sin optimización | Con optimización | Ahorro |
|-----------------|-----------------|-----------------|--------|
| Consulta de stock | ~1.500 tokens | ~635 tokens | **-58%** |
| Respuesta cacheada | ~1.700 tokens | 0 tokens | **-100%** |
| Consulta de proveedor | ~1.935 tokens | ~1.487 tokens | **-23%** |
| Saludo simple | ~800 tokens | 0 tokens | **-100%** |

---

## 🏗️ Arquitectura

```
Usuario
  │
  ▼
React (puerto 5173)
  │ HTTP GET /api/chat?mensaje=...
  ▼
ChatController (Spring Boot — puerto 8081)
  │
  ├── Bypass saludos ──────────────────→ Respuesta directa (0 tokens)
  │
  ├── Clasificador de intención
  │
  ├── Prompt dinámico según intención
  │
  ├── RAG (solo si aplica) ────────────→ SimpleVectorStore ← datos.txt
  │
  ├── Memoria ─────────────────────────→ InMemoryChatMemory
  │
  └── LLM ─────────────────────────────→ Groq API / LLaMA 3.3 70B
        │
        └── Function Calling ──────────→ consultarStock / realizarCompra
```

---

## 🚀 Cómo ejecutar el proyecto

### Requisitos
- Java 21
- Node.js 18+
- Maven 3.9+
- API Key de Groq (gratuita en [console.groq.com](https://console.groq.com))

### Backend

1. Crea el fichero `backend/src/main/resources/application.properties` a partir del ejemplo:

```properties
spring.application.name=backend
spring.ai.openai.api-key=TU_API_KEY_DE_GROQ
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
server.port=8081
```

2. Arranca el backend:

```bash
cd backend
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

3. Abre el navegador en `http://localhost:5173`

---

## 📁 Estructura del proyecto

```
tiefinal/
├── backend/
│   └── src/main/
│       ├── java/com/example/demo/
│       │   ├── ChatController.java   # Orquestador principal + AiTools
│       │   ├── RagService.java       # Carga y vectorización de datos.txt
│       │   └── DemoApplication.java
│       └── resources/
│           ├── application.properties.example
│           └── datos.txt             # Base de conocimiento de la empresa
├── frontend/
│   └── src/
│       └── App.jsx                   # Interfaz de chat
└── assets/
    └── demo.png
```

---

## 👥 Autores

Proyecto desarrollado en pareja para la asignatura **Tecnologías de Información Emergentes**, 4º curso del Grado en Ingeniería Informática — Universidad de Salamanca, curso 2025/2026.

- **Marco** — [@maarcost](https://github.com/maarcost)
- **Sergio** — [@sergiodpgz](https://github.com/sergiodpgz)
