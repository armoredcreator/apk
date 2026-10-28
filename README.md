# FrameMatch Android — port nativo, versão 0.2.1-alpha (validação de compilação em GitHub Actions)

**Situação (28/10/2026): APK debug COMPILADO e testes Android PASSARAM no GitHub Actions. Ainda NÃO testado em um dispositivo Android real.** Não é uma cópia binária do programa Python/Tkinter para Windows; foi reimplementado em Java para Android.

## Revisão de qualidade após teste real `84.mp4`

- **Causa técnica identificada:** a prévia Java multiplicava ganhos em RGB, mas o shader da exportação usava uma ida e volta com `pow(...,1/2.2)` / `pow(...,2.2)`. Isso convertia cada ganho `g` em aproximadamente `g^2.2` (por exemplo, 1,20 → ~1,49). O shader SDR foi corrigido para usar o mesmo domínio RGB da prévia, sem elevar os ganhos.
- **Padrões conservadores:** intensidade 65%, reenquadramento automático desmarcado; o zoom só é considerado se ativado manualmente, com liberação antes dos três segundos.
- **Continuidade:** avalie principalmente os três primeiros segundos de cada próximo clipe. A cor deve permanecer consistente durante o clipe inteiro (não há desligamento abrupto da correção aos 3 segundos).
- **Validação:** compilação Android e teste estático de contrato RGB não garantem equivalência visual no aparelho; ainda é obrigatório exportar um par real, avaliar céu, pele, lábios e áudio e comparar com a prévia. Sem teste de dispositivo não classificar como estável.



## FrameMatch 0.7.0-alpha — corte temporal e cor com consistência entre quadros

- **Clip 1 segue sendo a paleta fixa** para todos os clipes seguintes. O ajuste de cor agora usa três amostras temporais por cena: instante inicial, +0,5 s e +1 s, comparados às amostras perto do fim do Clip 1. Ganhos que oscilam muito são amortecidos; mantém uma gradação estável durante a cena para evitar flicker de cor.
- **Busca opcional de entrada:** avalia os olhos em 0, 250, 500, 750, 1.000, 1.250 e 1.500 ms no novo clipe. Quando encontra posição/escala/rotação suficientemente próximas do último quadro anterior, seleciona um ponto de entrada e usa Media3 ClippingConfiguration para descartar o início, inclusive o áudio. **Está DESLIGADA por padrão**, porque fala, gesto e efeitos no início poderiam ser perdidos; o usuário deve conferir depois de ligar.
- O alinhamento facial e as seis alternativas de cor permanecem disponíveis. A comparação 50/50 é somente uma prévia estática; o aplicativo não sintetiza movimentos inexistentes.
- **Limitação importante:** a gradação permanece fixa em todo o clipe, mas usa evidência temporal para escolhê-la. Não é correção dinâmica por frame nem reconstrução de perspectiva. Não promete transições invisíveis; qualidade precisa ser avaliada no celular.
- O valor da busca é derivado exclusivamente dos clipes vizinhos. A referência **de cor**, em contraste, permanece exclusivamente o Clip 1.

## Versão 0.5.0-alpha — Qualidade e naturalidade

**Clip 1 = referência de cor fixa para todos os clipes**, e agora também pode definir o padrão de saída (resolução/proporção, bitrate solicitado e limite de FPS quando disponível). A resolução de referência respeita o teto conservador de 1920 pixels no maior lado, para evitar falhas em Android mais antigo; a codificação H.264/AAC e as limitações do hardware podem alterar os parâmetros solicitados. Uma exportação com os mesmos parâmetros não recupera nitidez de vídeos originalmente piores.

O motor **Auto Natural** escolhe por padrão uma dentre seis tentativas para cada clipe, comparando dois quadros próximos do começo do clipe com dois quadros do final do Clip 1. A decisão usa discrepâncias de cor por região e penaliza mudanças excessivas. É uma heurística conservadora de correspondência; não é um modelo de restauração com IA e não garante correspondência perfeita em qualquer conteúdo.

Controles adicionados:
- **AUTO NATURAL (todos):** recalcula e escolhe uma tentativa para cada cena independentemente, preservando o Clip 1 como paleta mestra;
- **AUTO NATURAL (por corte):** reavalia uma transição específica sem alterar outras escolhas de cor;
- **Ativar todas as correções compatíveis:** liga máscara regional, zoom experimental, normalização de resolução, solicitação de bitrate e limite de FPS. O zoom é opcional e pode provocar cortes indesejados; desligue-o se o quadro ficar apertado. Os seis looks de cor continuam mutuamente exclusivos e o ajuste fino permite regiões combinadas.
- **Aplicar a mesma tentativa a todos:** copia apenas o estilo/intensidade escolhido, mas calibra cada cena diretamente contra o Clip 1.
- **Ver qualidade exportada:** após a codificação, informa a resolução e a taxa de bits reportadas pelo arquivo produzido.

O critério principal de continuidade visual é o começo de cada clipe. Reenquadramento, quando ativado, termina antes de 3 segundos; correção de cor é constante no clipe inteiro para não criar uma mudança brusca aos 3 segundos. O áudio e os originais são preservados; a qualidade visual real exige teste e comparação no aparelho.

## Regra obrigatória: Clip 1 é a referência MESTRA de cor (v0.4.2-alpha)

- **Toda calibração de cor dos Clipes 2, 3, 4…16 é feita diretamente a partir de dois quadros próximos do final do Clip 1.** As cores do Clip 1 não são modificadas.
- **Não há cascata de cor.** Ajustar o Clip 2 não muda a paleta que determina a correção do Clip 3, e assim sucessivamente. Cada clipe tem sua seleção entre seis opções e intensidade independente; essas escolhas nunca alteram a referência-mestra.
- A prévia mostra três quadros antes das alternativas: **Clip 1 (cores)**, **fim do clipe anterior (continuidade de corte)** e **início original do clipe atual**. Enquadramento opcional usa apenas a cena anterior e fica restrito aos primeiros 2,8 segundos.
- A referência de cor é obtida dos quadros finais do Clip 1. Caso haja mudança significativa de iluminação dentro do próprio Clip 1, ainda é preciso futura referência temporal/por regiões mais sofisticada; não garantimos cor perfeita em qualquer situação.
- Os ganhos de cor são aplicados de forma estável ao clipe; não há desligamento repentino da correção após três segundos.
- O código Java puro tem teste de regressão verificando que o Clip 3 continua ancorado no Clip 1, independentemente da escolha do Clip 2.

## Tentativas por corte — versão 0.4.1-alpha

A correção não depende de vídeos específicos: cada corte entre clipes recebe seis alternativas (cinco estratégias mais ajuste fino) geradas automaticamente a partir dos quadros dos próprios arquivos.

- **Original:** mantém as cores sem alteração.
- **Suave:** reduz os ajustes globais e locais.
- **Equilibrada:** usa a calibração conservadora do motor.
- **Priorizar cenário:** privilegia céu azul e ambiente, sem aplicar correção local a pele/lábios.
- **Priorizar pele:** reduz a correção global, mantém pele/lábios e deixa céu sem ajuste local.
- **Ajuste fino:** controle independente de cenário, céu/azul, pele e lábios para aquele corte.

Após clicar **GERAR TENTATIVAS DE COR PARA CADA CORTE**, deslize horizontalmente pelas opções de cada transição. Ajuste também a **intensidade do corte (0–150%)** sem afetar as demais cenas. Toque na alternativa desejada: o app atualiza as prévias dos cortes, mas mantém a referência global fixa no Clip 1 sem precisar reler os vídeos ou repetir a detecção facial. O MP4 exportado usa **uma escolha independente por corte**, não apenas a intensidade global.

**Importante:** cada tentativa é uma heurística, não um resultado profissional garantido. As miniaturas usam o motor CPU e a exportação utiliza máscaras equivalentes no shader; pequenas diferenças de renderização do aparelho podem permanecer. A cor escolhida é aplicada de forma estável ao clipe inteiro; o **reenquadramento opcional** é limitado aos primeiros 2,8 segundos. A opção original evita o shader de correção daquele clipe. Os arquivos de entrada são preservados.


## Função e fluxo

1. Selecionar de 2 a 16 vídeos locais, reorganizar e remover clipes.
2. Analisar o último quadro de cada trecho e o primeiro do próximo, na ordem selecionada.
3. Calcular ajuste moderado **por região**: ambiente, céu azul, pele do rosto e lábios. Regiões não detectáveis são ignoradas. Não gera pixels nem reconstrói objetos.
4. Amostrar rostos com ML Kit **no aparelho**, sem identificar pessoas. Acompanhar tamanho/posição no tempo para reenquadramento progressivo **somente** se houver evidência de aproximação natural do personagem (desligável).
5. Ver primeiro/último quadro e a prévia da correção, exportar união MP4 H.264/AAC localmente usando Media3 Transformer e escolher o destino pelo seletor do Android.
6. Entradas via Storage Access Framework, originais em modo leitura. Exportação temporária no cache do app e cópia para documento escolhido.

## Diferenciais desde a primeira alpha

- `RegionMath.java`: correção específica de fundo, céu, pele e lábios, ganhos conservadores e análise independente em CPU. **Calibração por regiões de cor é heurística:** máscara de céu detecta cromaticidade azul, não a semântica do céu inteiro; boca é aproximada pela posição relativa do rosto.
- `FaceTimeline.java`: detecção facial local ML Kit 16.1.7, com amostragem temporal limitada e interpolação.
- `RegionEffect.java`: shader GLES2 aplica os ajustes de cor nos **frames exportados**, não só na prévia; sem processamento offline por servidor.
- `ContinuityZoom.java`: transformação geométrica temporizada até cerca de 2,8 segundos, com ativação conservadora.
- Preview de enquadramento via canvas e controles independentes na tela.
- Resolução de exportação normalizada em 540×960 com fit sem distorção, destinada a vídeos verticais; clipes horizontais terão barras pretas.

## Limitações explícitas

- **Não gera movimento novo**, não sincroniza poses incompatíveis, não corrige troca real de perspectiva/cenário e **não garante** cortes imperceptíveis. A sincronização implementada é de escala e deslocamento de enquadramento mediante rastreamento facial, não motion synthesis/optical-flow de corpos inteiros.
- Cor ajustada com heurísticas de cor: pele fora do rosto e céu cinzento podem não ser detectados. Lab/LUT 3D da versão Windows não foram reproduzidos fielmente; este é um motor Android novo, não paridade numérica.
- SDR por projeto. Vídeos HDR/mistura HDR+SDR exigem futura calibração e tratamento de cor adicional. Há vídeos com trilhas/codec/formato não suportados pelos codecs do dispositivo.
- Media3 não fornece transição de crossfade de vídeo/áudio para composição sequencial nesta API. Cortes são diretos; reenquadramento pode suavizar a percepção visual, mas não substitui interpolação de movimento.
- Até 16 vídeos, sem garantia para arquivos longos/4K: tempo de varredura e exportação depende do Android e da memória livre.
- O APK debug foi compilado com sucesso na [execução 40615353527 do GitHub Actions](https://github.com/armoredcreator/apk/actions/runs/40615353527). Ainda não há teste com o seu celular nem comparativo de cores do MP4 exportado pelo Android. Resultados Windows não são validação Android.

## Como compilar no GitHub (sem Android Studio local)

1. Crie repositório **novo** para este aplicativo, sem copiar para o projeto ArmoredCreator.
2. Envie **o conteúdo desta pasta** para a raiz do repositório (incluindo `.github/workflows/build-apk.yml`; uploads via interface web podem ocultar arquivos `.github`, confira).
3. `Actions` → `Compilar APK Android` → `Run workflow`.
4. O pipeline executará teste Java sem SDK, testes Gradle e `:app:assembleDebug`. Se ficar verde, baixe o artefato `FrameMatch-Android-debug-apk` e instale o `app-debug.apk` no Android.
5. O build do commit `f89c33f` passou. Em **Artifacts**, baixe `FrameMatch-Android-debug-apk`, extraia e instale `app-debug.apk`. É um APK debug para testes, não uma versão de produção.

Requisitos: Java 17; AGP 8.8.2; Gradle 8.10.2; Android SDK 35; Android 8+ (minSdk 26); Media3 1.9.0; ML Kit face detection 16.1.7 bundled.

## Teste disponível neste ambiente (sem SDK)

```sh
bash tests_jdk/run.sh
```

Executa sete verificações do motor de cor (`RegionMath`) com Java 17 puro.

## Segurança de arquivos e privacidade

- Leitura dos vídeos via SAF, sem acesso amplo ao armazenamento.
- Os arquivos de origem nunca são sobrescritos pelo aplicativo. Salve em nome/arquivo novo.
- Detecção facial no próprio dispositivo; não há identificação biométrica nem upload deliberado de vídeos.
- Para uma próxima versão, deve-se testar cancelamento, codecs, orientação, framerate, preservação e sincronismo de áudio e resiliência contra falta de espaço antes de chamar de lançamento estável.

## Próximas validações

1. Instalar o APK debug num Android compatível.
2. Exportar dois vídeos, checando orientação, áudio AAC, duração e continuidade do corte.
3. Comparar céu, pele e lábios com os vídeos de referência.
4. Corrigir qualquer problema do shader GLES, codecs do dispositivo ou reenquadramento antes da versão estável.
