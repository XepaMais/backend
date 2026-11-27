package br.com.xepamais.api.xepinha;

import java.util.List;

// Quem gera a resposta da Xepinha
public interface ClienteIa {

    // papel: "user" ou "model", na ordem da conversa
    record Turno(String papel, String texto) {}

    // Lança IaIndisponivelException se a IA não responder
    String responder(String instrucoes, List<Turno> historico, String pergunta);

    class IaIndisponivelException extends RuntimeException {
        private final boolean sobrecarga;

        public IaIndisponivelException(String mensagem, boolean sobrecarga, Throwable causa) {
            super(mensagem, causa);
            this.sobrecarga = sobrecarga;
        }

        public boolean isSobrecarga() {
            return sobrecarga;
        }
    }
}
