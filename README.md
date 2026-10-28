# FrameMatch Android — port nativo, versão 0.2-dev (validação de compilação em GitHub Actions)

**Situação: código-fonte implementado; APK AINDA NÃO compilado nem testado num Android real.** Não é uma cópia binária do programa Python/Tkinter para Windows; foi reimplementado em Java para Android.

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
- Ainda não há **APK compilado**, teste com o seu celular, comparativo de cor no MP4 gerado pelo Android ou evidência de CI verde. Nenhum resultado no Windows deve ser interpretado como validação Android.

## Como compilar no GitHub (sem Android Studio local)

1. Crie repositório **novo** para este aplicativo, sem copiar para o projeto ArmoredCreator.
2. Envie **o conteúdo desta pasta** para a raiz do repositório (incluindo `.github/workflows/build-apk.yml`; uploads via interface web podem ocultar arquivos `.github`, confira).
3. `Actions` → `Compilar APK Android` → `Run workflow`.
4. O pipeline executará teste Java sem SDK, testes Gradle e `:app:assembleDebug`. Se ficar verde, baixe o artefato `FrameMatch-Android-debug-apk` e instale o `app-debug.apk` no Android.
5. Se a compilação falhar, envie logs do job para correção: **o pacote neste estágio é código pronto para CI, e não certificação de funcionamento**.

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

## Próxima etapa objetiva depois de enviar o endereço do repositório

Subir/atualizar o código numa branch de trabalho; executar CI; corrigir erros reais de compilação; testar exportação H.264/AAC em aparelho Android; comparar corte, céu, pele e lábios nos arquivos de exemplo; publicar APK de teste somente após build verde.
