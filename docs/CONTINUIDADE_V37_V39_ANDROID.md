# FrameMatch — contrato executável V37/V39 para Android

## Referências imutáveis
- Par de referência da validação: 94.mp4 (cena 1) + 95.mp4 (cena 2);
  equivalência das fontes 183/184 com a referência V39 NÃO presumida.
- Vídeo de movimento aprovado: FrameMatch_V37_RECUO_CONTINUO_COR_PERSISTENTE.mp4.
- V39 / 181.mp4 permanece a REFERÊNCIA visual refinada, mas o arquivo
  precisa estar disponível para comparação do MP4 exportado no Android.
- 230.mp4: exportação Android 16,014 s / 384 quadros, orientação gravada
  864x480 com rotação -90; conteúdo exibido em 480x864.
- V37: 16,000 s / 384 quadros / 24 fps, 480x864, sem rotação metadata.

## Causa medida no Android (230 vs V37)
O aplicativo aplicava `Matrix.setScale(z,z)` em torno do CENTRO da imagem.
Na saída vertical de 480x864, esse centro fica em (240,432).
A V37 aprovada aproximava em torno de aproximadamente (240,320).
Com z=1,33 isso causa desvio de ~37px em Y na união — medido por
SIFT/RANSAC comparando ambos os vídeos com os originais.
A curva de aproximação V37 seguia smoothstep cúbico, NÃO quintico.

## Anti-regressão 0.9.4-alpha
- O pivot precisa ser IGUAL nos dois lados de cada corte.
- O modo manual de referência usa (0.5, 320/864); o AUTO por corte
  deriva o ponto focal dos dados de detecção facial e pode rejeitar o zoom.
- Aproximação nos últimos 3 segundos da primeira cena;
  1.000x no início, 1.330x na união, sem salto de escala no corte.
- Recuo monotônico C1 ao longo de toda a segunda cena, e 1.000x no fim;
  interpolado sobre as amostras medidas da V37 sem hard release.
- Prévia exportada e vídeo completo usam o MESMO efeito e o MESMO pivot.
- A cor deve permanecer aplicada durante todo o clipe, não sumir quando
  o zoom abre. Isso só será CERTIFICADO ao comparar o Android com a V39.
- Nunca cortar 750ms automaticamente para o par de teste; deixar
  a opção de buscar entrada desligada (entrada padrão = 0ms).
- Nada de cortes secos introduzidos pelo programa; manter áudio e
  todos os quadros originais salvo trim explicitamente selecionado.
- Para pares futuros, 33% não é regra universal; diagnóstico e veto
  de correções inseguras são obrigatórios.

## Auditoria quantitativa de movimento
RANSAC mediu no 94->V37:
- 5s 1,000x; 6s 1,0857x; 7s 1,2449x; 7,95s 1,3294x.
Na 95->V37:
- 10s global 1,2514x; 12s 1,1799x; 14s 1,0595x; 15,5s 1,0036x.
Teste offline de simulação do novo zoom contra V37 reduziu a diferença
estrutural (com remoção da mediana RGB) em pontos entre 6s e 14s
de 40% a 72% comparado a 230.mp4. Essa comparação NÃO é uma
validação de cor, áudio, rosto, transição perceptiva ou APK no aparelho.

## Estado verificável
- Geometria e curva V37: implementadas em Java, com autotestes JDK.
- Compilação Android: confirmar status do CI, não inferir de commit.
- Reprodução exata da V39/181 e correção total de iluminação: PENDENTES
  até avaliação do MP4 exportado no aparelho, quadro a quadro.
- Não declarar "perfeito", nem instalar automaticamente, sem essas provas.
