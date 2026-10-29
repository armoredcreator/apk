# FrameMatch Android — contrato de entrega AUTO CINEMA 0.9.3-alpha

**Referência visual final: V39** (`FrameMatch_V39_V37_BRILHO_ESTAVEL.mp4`, SHA-256
`1253fcc50f83d3238f8faddb9f71ca5399c59a7ab463e8d0fcce6787f0487b85`).
A V37 foi um marco aprovado de movimento; a V39 é a referência posterior de acabamento.

## O que a alpha 0.9.3 de fato implementa

- Avalia **cada corte adjacente independentemente**, usando quadros originais já decodificados e localização aproximada do rosto (ML Kit).
- O modo **AUTO CINEMA** está ativo por padrão. A heurística classifica `KEEP`, `ZOOM` (aproximação até 33%, baseada nos dados deste corte) ou `REVIEW`.
- Ausência de rosto confiável, face deslocada demais, geometria suspeita e duração insuficiente NÃO disparam zoom automático. `REVIEW` aparece na análise para inspeção.
- A exportação usa a mesma decisão que a prévia renderizada do corte, incluindo pares intermediários de sequências com 3–16 clipes.
- Para zoom aplicado: aproximação ao longo dos últimos 3 s da cena anterior, mesmo valor no corte, e recuo quintic contínuo ao longo do clipe seguinte, retornando a 1x no fim.
- O zoom manual de **33%** continua disponível como experimento, exclusivamente para uma união de dois clipes, em alternativa ao AUTO CINEMA.
- Paleta do **Clip 1** continua mestre para os seguintes; o shader de cor não se desliga aos 3 segundos. Os originais não são sobrescritos.

## O que a alpha ainda NÃO implementa/comprova

- Não há reconstrução de rosto, síntese de movimento, fluxo óptico temporal bidirecional, correção de perspectiva nem comparação automática pós-exportação de flashes em cada frame.
- Não há prova de paridade **pixel a pixel** entre Media3/GLES Android e a renderização desktop V39.
- A métrica entre quadros não prevê sozinha se o resultado parecerá natural. O modo REVIEW é conservador, e o usuário precisa inspecionar o MP4 exportado.
- A correção de cor existente é estável por clipe; não é estabilização semântica de iluminação por mapa 3D de cena.
- 33% não é padrão para todos os cortes. Uma cena sem face ou com enquadramentos incompatíveis pode exigir edição manual.

## Qualificação antes de chamar de versão final

1. Em **Android físico**, exportar `94.mp4` + `95.mp4` completos, comparar com V39 no corte e 10 pontos da cena 2; validar cor, áudio, formato e recuo.
2. Repetir com 3 pares diferentes: um já alinhado (sem zoom), um com diferença visual moderada (zoom conservador) e um incompatível (REVIEW).
3. Verificar todos os quadros do recuo: escala monotônica para cada corte, nenhuma liberação abrupta, nem flash extra causado por GPU, máscaras ou correção de cor.
4. Testar múltiplos clipes onde um clipe intermediário tem recuo de um corte e aproximação ao próximo.
5. Só publicar como estável depois desses testes reais. Compilação CI ≠ comprovação visual.

**Política:** preservar V39 como benchmark, não alterar outros elementos quando uma correção isolada não foi comprovada.
