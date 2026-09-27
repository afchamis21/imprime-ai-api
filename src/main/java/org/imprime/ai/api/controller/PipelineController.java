package org.imprime.ai.api.controller;

public class PipelineController {
    // TODO
    //  1. - Receber modelo (metadados + arquivo)
    //     - Criar um pré-pedido e salvar o arquivo no Object Storage
    //     - Lista de vendedores na mesma Cidade -> Estado -> País do cliente.
    //       - Eventualmente podemos integrar com uma API do google maps ou algo assim para calcular as distâncias
    //     - Calcular a estimativa com base nas configurações de cada vendedor
    //       - Depois
    //     - Retornar lista à UI
    //  2. - Com o pré-pedido, podemos escolher X vendedores para abrir Chat
    //     - Precisamos fazer algo com WebSocket para termos live chats (linkados ao pedido).
    //       - Quando fizermos o fetch do chat, podemos ter linkado chats antigos com o mesmo vendedor/cliente que já foram fechados/estão abertos
    //  3. - Preço aceito/negado
    //       - Aceito -> Fechar todos os outros chats do pedido em aberto, movemos o status do pedido de pré para aprovado. Pedidos em aprovado não devem poder abrir novos chats.
    //          Mantemos o chat com o vendedor em aberto para quaisquer comunicações
    //         - Pagamento via Stripe -> Retemos o dinheiro na nossa conta. Nota Fiscal? Stripe faz?
    //       - Negado -> Chat segue
    //  4. - Pedido pronto
    //     - Precisamos integrar com alguma API dos correios, DHL Express, UPS, eu sei lá. Mas precisamos emitir um código de envio, o vendedor leva aos correios, envia o produto com o código.
    //     - Antes de gerar o código de envio, o Maker precisa fazer upload de uma foto comprovando a qualidade do produto
    //       - [?] A API que integrarmos tem webhook para atualizar sobre o pedido? Tem um endpoint que podemos chamar?
    //              Se tiver podemos fazer uma cron a cada 5 minutos para atualizar os pedidos em aberto.
    //              Temos que disponibilizar um código de rastreio, mas também temos que saber quando o produto foi entregue para liberar o valor ao maker.
    //              Também podemos ter um botão de confirmar recebimento. Mas basicamente, quando o status mover para Completo, liberamos o valor ao maker.
    //              Maker precisa poder ver na página dele o valor a receber (com o desconto da nossa plataforma)
    //  5. - Ideia: No front (ou no back) ao invéz do viacep, devemos usar uma integração com o google maps.
    //      Desse forma podemos facilmente calcular quais makers estão mais próximos do cliente, e até mesmo a distância
}
