import { useState, useEffect, useRef } from 'react'
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './App.css'

function App() {
  const [mensajes, setMensajes] = useState([]);
  const [input, setInput] = useState('');
  const [cargando, setCargando] = useState(false);
  const mensajesEndRef = useRef(null);

  const handleInput = (e) => {
  const element = e.target;
  setInput(element.value);
  
  // Lógica para que crezca
  element.style.height = "24px"; // Reseteamos para recalcular
if (element.scrollHeight > 24) {
    element.style.height = `${Math.min(element.scrollHeight, 150)}px`;
  }};

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

    try {
      const res = await fetch(`http://localhost:8080/api/chat?mensaje=${encodeURIComponent(input)}`);
      const data = await res.json();

      setMensajes([...nuevosMensajes, { 
        rol: 'ia', 
        texto: data.respuestaFinal,
        tokens: data.tokens,
        coste: data.coste
      }]);
    } catch (error) {
      setMensajes([...nuevosMensajes, { rol: 'ia', texto: "Error: No se pudo conectar con el servidor." }]);
    } finally {
      setCargando(false);
    }
  };

  return (
    <div className="main-layout">
      <header className="nav-bar">
        <div className="brand">
          <div className="logo-icon">M</div>
          <h1>Tartas Marco <span>Sistema de Gestión</span></h1>
        </div>
        <div className="status-badge">
          <span className="dot"></span> Online
        </div>
      </header>

      <div className="chat-container">
        <div className="messages-view">
          {mensajes.length === 0 && (
            <div className="welcome-screen">
              <h2>Panel de Control Inteligente</h2>
              <p>Consulta stock, recetas o realiza pedidos de forma automatizada.</p>
            </div>
          )}
          
          {mensajes.map((m, i) => (
            <div key={i} className={`msg-row ${m.rol}`}>
              <div className="msg-bubble">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {m.texto}
                </ReactMarkdown>
                {m.rol === 'ia' && m.tokens > 0 && (
                  <div className="token-meta">
                    ⚡ {m.tokens} tokens | {m.coste.toFixed(5)}€
                  </div>
                )}
              </div>
            </div>
          ))}
          <div ref={mensajesEndRef} />
        </div>

        <div className="input-box">
          <div className="input-wrapper">
          <textarea 
            placeholder="Escribe tu petición aquí..."
            value={input}
            onChange={handleInput} // Usamos nuestra nueva función
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) { // Enter envía, Shift+Enter salta línea
                e.preventDefault();
                enviarMensaje();
                e.target.style.height = "auto"; // Resetea altura al enviar
              }
            }}
            rows="1"
          />
          <button onClick={enviarMensaje} disabled={cargando}>
            {cargando ? <div className="spinner"></div> : "Enviar"}
          </button>
        </div>
        </div>
      </div>
    </div>
  )
}

export default App;