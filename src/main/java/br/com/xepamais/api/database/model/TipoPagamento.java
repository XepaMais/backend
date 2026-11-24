package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_tipo_pagamento")
@Getter
@Setter
public class TipoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_pagamento")
    private Integer id;

    @Column(name = "metodo_pagamento", nullable = false, length = 40, unique = true)
    private String metodoPagamento;
}
