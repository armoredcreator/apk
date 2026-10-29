# FrameMatch — Referência V39 (28/10/2026)

## Fonte de verdade
Vídeo local aprovado pela usuária como versão de referência: `FrameMatch_V39_V37_BRILHO_ESTAVEL.mp4`.
SHA-256: `1253fcc50f83d3238f8faddb9f71ca5399c59a7ab463e8d0fcce6787f0487b85`.
16,000 s, 384 quadros, 24 FPS, 480×864, H.264. Conjunto de origem: cenas 94.mp4 e 95.mp4.

**V39, não V37, é o alvo de equivalência visual.** O nome do arquivo contém V37 porque essa foi a base histórica; isso não autoriza rebaixar a versão aprovada.

## Comportamentos obrigatórios
1. A cena 1 define a referência visual e temporal. No caso validado, aproximação de 33% iniciada três segundos antes da união.
2. No corte, a cena 2 precisa estar geometricamente alinhada **desde seu primeiro quadro**; não entrar fina e corrigir depois.
3. O recuo deve ser lento, contínuo e chegar até a composição original da cena 2; nenhum salto de enquadramento durante o recuo.
4. A correção de cor da união deve acompanhar toda a cena 2; não pode terminar após 2–3s nem produzir flash de exposição.
5. Cada par futuro precisa ser diagnosticado independentemente; 33% não é padrão universal.
6. Preservar áudio, sequência integral quando solicitado, e os arquivos originais.
7. Gerar uma prévia real exportada antes de considerar qualquer resultado aprovado.
8. Uma melhoria na cor não pode alterar o movimento aprovado; uma melhoria no movimento não pode criar flashes de brilho.

## Estado real de implementação
O código Android atual contém a opção `ZOOM V37` e o motor `CinematicCutZoom`, mas **isso não comprova paridade com a V39**. A renderização experimental Python/OpenCV de V39 ainda não foi portada, calibrada e testada no Media3/GLES Android. Não rotular um APK como V39 até exportar no celular as cenas 94/95 e comparar o MP4 a esta referência.

## Critérios de aceite
- Exportar clipes 94 e 95 completos sem flash, salto seco ou deformação facial.
- Comparar frames de ambos os lados do corte e pelo menos 10 amostras distribuídas pela cena 2 contra V39.
- Conferir curva de zoom monotônica no recuo e retorno ao enquadramento normal no último trecho.
- Conferir cor ao longo de toda cena 2 e ausência de mudança abrupta.
- Repetir em pelo menos 3 outros pares para validar detecção automática (inclusive um par que não precise de zoom).
- Registrar evidência audiovisual exportada **pelo app Android**, não apenas compilação do APK.
