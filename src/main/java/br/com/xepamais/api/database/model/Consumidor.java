package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_consumidor")
@Getter
@Setter
public class Consumidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consumidor")
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_conta", nullable = false, unique = true)
    private Conta conta;

    // Forma de receber escolhida por último no carrinho: RETIRADA ou ENTREGA
    @Column(name = "recebimento_preferido", nullable = false, length = 10)
    private String recebimentoPreferido = "RETIRADA";

    // Último endereço de entrega
    @Column(name = "cep_entrega", length = 8)
    private String cepEntrega;

    @Column(name = "logradouro_entrega", length = 150)
    private String logradouroEntrega;

    @Column(name = "numero_entrega", length = 10)
    private String numeroEntrega;

    @Column(name = "complemento_entrega", length = 60)
    private String complementoEntrega;

    @Column(name = "bairro_entrega", length = 80)
    private String bairroEntrega;

    @Column(name = "cidade_entrega", length = 80)
    private String cidadeEntrega;

    @Column(name = "uf_entrega", length = 2)
    private String ufEntrega;
}
