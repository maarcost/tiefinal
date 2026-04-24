import { useState, useEffect, useRef } from 'react'
import './App.css'

function App() {
  const [mensajes, setMensajes] = useState([]);
  const [input, setInput] = useState('');
  const [cargando, setCargando] = useState(false);
  
  // Nuevo estado para simular que vemos los pasos del orquestador
  const [pasoActual, setPasoActual] = useState(0);

  const mensajesEndRef = useRef(null);

  const scrollToBottom = () => {
    mensajesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [mensajes]);

  const enviarMensaje = async () => {
    if (!input.trim()) return;

    const nuevosMensajes = [...mensajes, { rol: 'usuario', texto: input }];
    setMensajes(nuevosMensajes);
    setInput('');
    setCargando(true);
    setPasoActual(1); // Iniciamos el pipeline

    try {
      // Simulación de pasos para el monitor (esto da el toque Pro al TFG)
      setTimeout(() => setPasoActual(2), 500); // RAG
      setTimeout(() => setPasoActual(3), 1200); // Prompt Engineering

      const respuesta = await fetch(`http://localhost:8080/api/chat?mensaje=${encodeURIComponent(input)}`);
      const textoIA = await respuesta.text();
      
      setMensajes([...nuevosMensajes, { rol: 'ia', texto: textoIA }]);
      setPasoActual(4); // Finalizado
    } catch (error) {
      setMensajes([...nuevosMensajes, { rol: 'ia', texto: "Error: Backend desconectado" }]);
      setPasoActual(0);
    } finally {
      setCargando(false);
    }
  };

  return (
    <div className="dashboard-container">
      {/* PANEL LATERAL IZQUIERDO: MONITOR DE PIPELINE */}
      <aside className="pipeline-panel">
        <h3>Pipeline Orquestador</h3>
        <div className="steps-container">
          <div className={`step ${pasoActual >= 1 ? 'active' : ''}`}>
             <span className="step-num">1</span> Entrada Natural
          </div>
          <div className={`step ${pasoActual >= 2 ? 'active' : ''}`}>
             <span className="step-num">2</span> Recuperación RAG
          </div>
          <div className={`step ${pasoActual >= 3 ? 'active' : ''}`}>
             <span className="step-num">3</span> Prompt Optimization
          </div>
          <div className={`step ${pasoActual >= 4 ? 'active' : ''}`}>
             <span className="step-num">4</span> Inferencia LLM
          </div>
        </div>
        
        <div className="rag-info">
          <h4>Metadata RAG:</h4>
          <p>{cargando ? "Analizando documentos..." : "Consultando datos.txt"}</p>
        </div>
      </aside>

      {/* PANEL CENTRAL: EL CHAT */}
      <main className="chat-main">
        <header className="chat-header">
          <div className="status-container">
            <div className="status-dot"></div>
            <span>Intermediario Semántico Activo</span>
          </div>
          <h1>Optimizador</h1>
        </header>

        <div className="chat-window"> {/* ESTE es el que tiene el scroll */}
          {mensajes.map((m, i) => (
            <div key={i} className={`message-wrapper ${m.rol === 'usuario' ? 'user' : 'bot'}`}>
              <div className="message-bubble">
                {m.texto}
              </div>
            </div>
          ))}
          
          {cargando && <div className="loading-spinner">Ejecutando Pipeline...</div>}
          
          {/* EL ANCLA AQUÍ ABAJO, DENTRO DE chat-window */}
          <div ref={mensajesEndRef} /> 
        </div>

        <div className="input-area">
          <input 
            type="text" 
            placeholder="Introduce petición ambigua..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && enviarMensaje()}
          />
          <button onClick={enviarMensaje} disabled={cargando}>
            {cargando ? 'Procesando...' : 'Orquestar'}
          </button>
        </div>
      </main>
    </div>
  )
}

export default App;