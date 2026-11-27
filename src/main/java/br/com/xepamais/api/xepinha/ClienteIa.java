package br.com.xepamais.api.xepinha;

import java.util.List;

// Quem gera a resposta da Xepinha (em produção: GeminiCliente; nos testes: uma resposta fixa).
public interface ClienteIa {

    // papel: "user" (feirante) ou "model" (Xepinha), na ordem da conversa.
    record Turno(String papel, String texto) {}

    // Lança IaIndisponivelException se a IA não responder (sem chave, fora do ar, sobrecarregada).
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
