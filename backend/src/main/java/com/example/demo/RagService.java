package com.example.demo;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    
    @Value("classpath:datos.txt") // Aquí pondremos tu información
    private Resource datosArchivo;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void cargarDatos() {
        // 1. Leer el archivo
        TextReader textReader = new TextReader(datosArchivo);
        List<Document> documentos = textReader.get();

        // 2. Trocear el texto (para que la IA no se colapse)
        //TokenTextSplitter splitter = new TokenTextSplitter();
        TokenTextSplitter splitter = new TokenTextSplitter(300, 50, 5, 10000, true);
        List<Document> trozos = splitter.apply(documentos);

        // 3. Guardar en la base de datos de vectores
        vectorStore.add(trozos);
        System.out.println("--- BASE DE DATOS VECTORIAL CARGADA ---");
    }
}