# FrameMatch Android — port nativo, versão 0.2-dev (validação de compilação em GitHub Actions)

**Situação (28/10/2026): APK debug COMPILADO e testes Android PASSARAM no GitHub Actions. Ainda NÃO testado em um dispositivo Android real.** Não é uma cópia binária do programa Python/Tkinter para Windows; foi reimplementado em Java para Android.

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
