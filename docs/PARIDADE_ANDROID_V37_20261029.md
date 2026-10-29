# FrameMatch Android — Paridade com V37 (auditoria 2026-10-29)

**Status: arquitetura identificada; paridade visual ainda NÃO comprovada.** Não publicar APK como equivalente ao Windows sem teste real.

## Referência protegida
- Entradas: `94.mp4` e `95.mp4` (8s cada; 480x864; 24 fps; H.264/AAC).
- Resultado aprovado: `FrameMatch_V37_RECUO_CONTINUO_COR_PERSISTENTE.mp4` (16s, 384 frames).
- SHA-256 V37: `25f680fe06ea7630a2110bb6371d6a988ddbbdb2ac3bea4c5f5948687c7329d2`.
- SHA-256 94: `91250d98002e01b958357113ea1c9fc19950ab554e4c07359650786c7f2a2531`.
- SHA-256 95: `298f38b28ad1b3072d5e0e5f025122e909eae21c7441922344ea8fa8a2d0098d`.
- Para **esse par somente**: zoom 33% começando três segundos antes do corte dos 8s; alinhamento no próprio corte; recuo integral sem degraus; cor coerente do corte ao último frame. A V28-B é referência para o movimento na transição; a V37 é a referência completa aprovada. NÃO generalizar zoom de 33%.

## Auditoria do código atual (branch `feature/framematch-android`)
- `app/build.gradle`: `versionName 0.9.5-alpha`; Media3 1.9.0, ML Kit 16.1.7; **sem OpenCV/FFmpeg**.
- `MainActivity.java`: já seleciona múltiplos clipes, exporta usando Transformer, oferece modo automático/manual.
- `AdaptiveCutPlanner.java`: heurística de diferença estrutural e caixa de rosto, **sem fluxo óptico de cenário/personagem**.
- `ContinuityZoom.java`: transforma frames existentes; não implementa os mesmos quadros intermediários da sequência experimental V20–V37.
- `RegionMath.java` + `RegionEffect.java`: ganhos RGB e máscaras de cor, não port de processamento espacial/temporal em LAB da referência Windows. Exige comparação de preview CPU e saída GLES.
- `FaceTimeline.java`: ML Kit é útil para pontos faciais, mas não mede movimento de câmera do fundo.
- Código Windows v1.4 fornecido pela usuária utiliza `cv2`, `numpy`, LAB, ORB e alinhamento de sujeito. Ele não prova reprodução automática da V37; a V37 é produto de experimentação adicional.

## Dependências: decisão
1. **OpenCV Android AAR `org.opencv:opencv:4.14.0` — integrar em branch de prova**. OpenCV oficial, Apache 2.0, fornece ORB, RANSAC, ECC, optical flow Farnebäck, remap, Lab, máscaras e estatísticas. Usar análise em menor resolução e processamento por quadros para evitar memória excessiva. Validar tamanho APK, compatibilidade e custo de CPU.
2. **Media3 Transformer + efeitos OpenGL — manter e aperfeiçoar.** Codificação/decodificação/áudio, shaders de geometria e cor. Fazer curvas de zoom com continuidade de posição **e velocidade**, e aplicar tratamento de cor durante **toda a cena**. Padronizar domínio RGB/transferência/faixa de cor antes de comparar com OpenCV e render GPU. GPU pode produzir diferença do PC, por isso validar output.
3. **ML Kit — manter** para rosto/landmarks; medir separadamente fluxo do cenário e fluxo do rosto.
4. **FFmpegKit original — NÃO usar**, pois está aposentado. Investigar `FFmpegKitNext` (fonte, autor original) ou fork mantido **somente** se Media3 + OpenCV não oferecerem controle suficiente de interpolação, PTS e áudio. Verificar licenças LGPL/GPL e ABI antes de adotar.
5. **Modelos que geram/deformam rosto — não adotar por padrão**, para preservar a identidade e evitar frames artificiais.

Referências:
- https://opencv.org/opencv4android-usage-models/
- https://central.sonatype.com/artifact/org.opencv/opencv/4.14.0
- https://developer.android.com/media/media3/transformer
- https://developer.android.com/media/media3/transformer/transformations
- https://arthenica.github.io/ffmpeg-kit/
- https://ffmpeg.org/legal.html

## Arquitetura de implementação — por cada corte
1. Ler metadados e decodificar sequencialmente por **PTS real**; preservar os originais.
2. Comparar janelas de vários quadros antes e depois de cada corte, não só duas miniaturas. Identificar rosto, fundo, iluminação por região, diferenças de posição e fluxo.
3. Gerar candidatos independentes: sem efeito, apenas cor, alinhamento rígido, zoom/recuo, e eventual interpolação controlada no corte. **Não aplicar o mesmo zoom em todas as cenas.**
4. Testar alinhamento no **primeiro frame** da cena seguinte e a continuidade temporal dos frames seguintes.
5. Zoom começa antes do corte quando necessário, não dá degrau no corte, recua **continuamente até 1.00x**, sem perder o conteúdo do segundo vídeo.
6. O *tratamento* de cor decidido para a cena 2 é válido até o **último frame**. Não fazer fade para a cor original e não forçar médias iguais quando a composição/iluminação natural mudam. Testar pele, ombros e cenário.
7. Verificar todos os frames no corte e recuo, detectar flashes, mudança abrupta de escala/translação e artefatos; se inseguro, preferir saída sem efeito ou solicitar revisão.
8. Manter dois áudios e dois vídeos completos por padrão; nunca alterar arquivos originais. Para N clipes, diagnosticar cada fronteira separadamente.

## Testes obrigatórios antes de afirmar paridade
- **94 + 95:** 16s, 384 frames, 24 FPS, 480x864, H.264/AAC, zoom de 33% e recuo integral, sem flash/corte seco; comparar MP4 Android frame a frame à V37 e ouvir áudio.
- **Novos pares:** (a) corte já natural, (b) diferença só de cor, (c) diferença de escala, (d) caso incompatível que deve recusar; testar também três clipes consecutivos.
- Testar Android real, **não apenas CI**. Comparar regiões correspondentes, métrica de imagem, timestamps, artefatos e prévia vs exportação. Um score numérico isolado não prova qualidade percebida.

**Próxima etapa concreta:** integrar OpenCV em branch de prova **com uso real de diagnóstico**, acrescentar testes offline e exportar 94+95 no Android para comparação contra V37. Só então liberar APK como equivalente.
