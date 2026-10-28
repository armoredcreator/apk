# FrameMatch — V39 é a referência APROVADA (28/10/2026)

## Identidade da referência
- **Aprovada pela usuária como impecável:** `FrameMatch_V39_V37_BRILHO_ESTAVEL.mp4`.
- Arquivo local conferido: 16 segundos, produzido a partir das cenas 94 e 95.
- **V37 é somente antecedente**, não o padrão final. NÃO regressar para V37 como master.
- Este documento estabelece o contrato; o aplicativo Android existente **ainda não implementa nem reproduz a V39**. Não confundir especificação e exportação comprovada.

## Invariantes inegociáveis
1. Cena anterior é a referência no próprio instante do corte; primeiro quadro seguinte já encaixado. Sem «chegar ao alinhamento» após o corte.
2. Preservar os originais, áudio, duração e conteúdo da cena 2; exportar novo arquivo.
3. A correção de cor da cena seguinte **não desaparece** durante o recuo, nem causa mudança abrupta em frame isolado. A transformação pode acompanhar a iluminação natural, mas nunca ter reinicialização/salto.
4. Quando zoom for necessário: aproximação começa **antes** do corte; zoom completo no corte; recuo **lento, contínuo, sem mudança de escala abrupta**, termina no enquadramento integral para mostrar o conteúdo restante.
5. Exemplo de referência V39: aproximação 33%, iniciada 3 segundos antes do corte. São **parâmetros de caso validado**, NÃO valores fixos para qualquer par de vídeos.
6. Nunca introduzir cortes secos secundários, flashes, achatamento facial ou zoom descontínuo durante a retirada de efeitos.
7. Múltiplos clipes: decidir e validar cada fronteira de maneira independente, preservando decisões já aprovadas. Se um efeito não for necessário, não aplicar.
8. «Não demonstrado» é estado de análise válido. Não alegar qualidade perfeita sem assistir e medir **o vídeo completo**.

## Estado da implementação Android V39

A implementação está disponível na branch `feature/v39-implementation-20261028`, na PR #4.

- Cada fronteira gera um plano independente: aproximação antecipada no fim do clipe anterior e recuo suave desde o primeiro quadro do próximo.
- O mesmo plano é usado na prévia da transição e na exportação Media3.
- O exemplo de 33% continua sendo apenas um caso de referência; os parâmetros são medidos por corte.
- A versão compilada é `0.9.0-v39-alpha`; o CI passou em testes Java, testes Android e `:app:assembleDebug`.
- Ainda falta a validação visual no aparelho real comparando a exportação Android com `FrameMatch_V39_V37_BRILHO_ESTAVEL.mp4`.

## Sequência de implementação (ainda pendente)
1. Extrair métricas robustas de frames de corte, rosto, fundo, cor e movimento, mais janela de 3+ segundos antes/depois de cada corte.
2. Elaborar candidatos: sem efeito, cor, enquadramento, zoom antecipado + retorno. Estimar parâmetros por corte, não usar constantes V39 como universal.
3. Renderizar preview com a MESMA cadeia de efeitos utilizada na exportação.
4. Avaliar todos os quadros da união e do recuo contra descontinuidades de exposição, tonalidade, escala e velocidade. Rejeitar candidatos que introduzam um salto fora da união.
5. Aprovação visual de pelo menos três transições com comportamentos diferentes, incluindo uma que não precise de zoom; validação no Android real/CI antes de declarar o APK concluído.

## Código Android visto na auditoria inicial
- `MainActivity.java`: composição por Media3; atualmente adiciona `ContinuityZoom` só ao clipe de entrada.
- `ContinuityZoom.java`: desvanecimento no começo do novo clipe, não implementa uma aproximação antecipada no clipe anterior.
- `RegionMath.java` / `TemporalColor.java`: correção por planos de cor, ainda sem prova de continuidade V39 durante todo o recuo.
- Portanto, **o comportamento exato da V39 não está entregue no Android**.
