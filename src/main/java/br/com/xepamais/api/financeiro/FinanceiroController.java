package br.com.xepamais.api.financeiro;

import br.com.xepamais.api.financeiro.dto.FinanceiroDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Financeiro do feirante: lucros (rendas), despesas, saques via Pix, resumo e planilha.
// {idConta} é o "id" devolvido pelo login.
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Financeiro", description = "Lucros, despesas, saques, resumo e planilha do feirante")
@RequestMapping("/api/financeiro")
public class FinanceiroController {
    private final FinanceiroService service;

    public FinanceiroController(FinanceiroService service) {
        this.service = service;
    }

    @GetMapping("/categorias")
    public Map<String, List<String>> categorias() {
        return Map.of("origensRenda", service.origensRenda(), "categoriasDespesa", service.categoriasDespesa());
    }

    // ---------- Rendas ----------

    @GetMapping("/{idConta}/rendas")
    public List<RendaResponse> listarRendas(@PathVariable Integer idConta,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.listarRendas(idConta, inicio, fim);
    }

    @PostMapping("/{idConta}/rendas")
    @ResponseStatus(HttpStatus.CREATED)
    public RendaResponse criarRenda(@PathVariable Integer idConta, @RequestBody RendaRequest request) {
        return service.criarRenda(idConta, request);
    }

    @PutMapping("/{idConta}/rendas/{id}")
    public RendaResponse atualizarRenda(@PathVariable Integer idConta, @PathVariable Integer id,
                                        @RequestBody RendaRequest request) {
        return service.atualizarRenda(idConta, id, request);
    }

    @DeleteMapping("/{idConta}/rendas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRenda(@PathVariable Integer idConta, @PathVariable Integer id) {
        service.excluirRenda(idConta, id);
    }

    // ---------- Despesas ----------

    @GetMapping("/{idConta}/despesas")
    public List<DespesaResponse> listarDespesas(@PathVariable Integer idConta,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.listarDespesas(idConta, inicio, fim);
    }

    @PostMapping("/{idConta}/despesas")
    @ResponseStatus(HttpStatus.CREATED)
    public DespesaResponse criarDespesa(@PathVariable Integer idConta, @RequestBody DespesaRequest request) {
        return service.criarDespesa(idConta, request);
    }

    @PutMapping("/{idConta}/despesas/{id}")
    public DespesaResponse atualizarDespesa(@PathVariable Integer idConta, @PathVariable Integer id,
                                            @RequestBody DespesaRequest request) {
        return service.atualizarDespesa(idConta, id, request);
    }

    @DeleteMapping("/{idConta}/despesas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirDespesa(@PathVariable Integer idConta, @PathVariable Integer id) {
        service.excluirDespesa(idConta, id);
    }

    // ---------- Saques ----------

    @GetMapping("/{idConta}/saldo")
    public SaldoResponse saldo(@PathVariable Integer idConta) {
        return service.saldo(idConta);
    }

    @GetMapping("/{idConta}/saques")
    public List<SaqueResponse> listarSaques(@PathVariable Integer idConta) {
        return service.listarSaques(idConta);
    }

    @PostMapping("/{idConta}/saques")
    @ResponseStatus(HttpStatus.CREATED)
    public SaqueResponse sacar(@PathVariable Integer idConta, @RequestBody SaqueRequest request) {
        return service.sacar(idConta, request);
    }

    // ---------- Resumo e planilha ----------

    @GetMapping("/{idConta}/resumo")
    public ResumoResponse resumo(@PathVariable Integer idConta) {
        return service.resumo(idConta);
    }

    @GetMapping("/{idConta}/historico")
    public HistoricoResponse historico(@PathVariable Integer idConta,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                       @RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) String categoria,
                                       @RequestParam(required = false) String busca) {
        return service.historico(idConta, inicio, fim, tipo, categoria, busca);
    }

    // Mesmos filtros do histórico, em CSV que abre direto no Excel / Google Planilhas.
    @GetMapping("/{idConta}/planilha")
    public ResponseEntity<byte[]> planilha(@PathVariable Integer idConta,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                           @RequestParam(required = false) String tipo,
                                           @RequestParam(required = false) String categoria,
                                           @RequestParam(required = false) String busca) {
        HistoricoResponse historico = service.historico(idConta, inicio, fim, tipo, categoria, busca);
        byte[] arquivo = PlanilhaCsv.gerar(service.nomeDoFeirante(idConta), historico).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("historico-financeiro.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(arquivo);
    }
}
