# CLC — Assistente visual para Android

CLC é um assistente visual com bolha flutuante. A captura é iniciada pelo usuário e a imagem só é enviada quando uma pergunta é enviada.

## Recursos

- Bolha flutuante móvel para iniciar captura.
- Uma sessão MediaProjection por autorização; o mesmo VirtualDisplay é reutilizado durante a sessão.
- Chat com várias mensagens e perguntas de acompanhamento sobre a mesma captura.
- Copiar resposta e limpar conversa.
- Provedor, URL base, caminho de endpoint, modelo e chave configuráveis.
- Cabeçalho de autenticação e prefixo de chave personalizáveis (Bearer, API-Key ou outros esquemas aceitos pelo serviço).
- Cabeçalhos HTTP adicionais configuráveis em JSON.
- Modo de resposta JSON opcional, quando suportado pelo provedor.
- Presets para OpenAI, OpenRouter, Groq e Together AI, além de modo personalizado.
- Teste de conexão com o provedor.
- Temperatura, limite de tokens, tamanho do contexto e prompt de sistema configuráveis.
- Resolução máxima, qualidade JPEG e timeout configuráveis.
- Tema que acompanha o modo claro/escuro do Android.
- Redimensionamento e compressão da imagem antes do envio.
- R8 e redução de recursos habilitados para controlar o tamanho do APK.

## Provedores de IA

O CLC envia requisições para o caminho configurado (por padrão `/chat/completions`) no formato OpenAI Chat Completions. O provedor precisa aceitar imagens no formato `image_url` para análise visual. Compatibilidade com texto, isoladamente, não garante suporte a visão.

Para configurar um provedor personalizado, informe a URL base, caminho do endpoint, ID exato do modelo e chave fornecidos pelo serviço. Não presuma que a URL de painel, uma chave ou uma URL de convite seja a URL da API.

**Nunca publique chaves reais no código, commits, README ou prints.** O CLC salva a chave nas preferências privadas do app, mas isso não é um cofre inviolável em aparelhos comprometidos ou com root. Para distribuir o aplicativo a outras pessoas, considere um backend próprio em vez de embutir uma chave compartilhada.

## Configurações da IA

- Temperatura: de 0,0 a 2,0.
- Tokens máximos: de 64 a 16.000.
- Contexto: de 1 a 20 turnos.
- Prompt de sistema personalizado.
- Caminho do endpoint personalizável.
- Timeout de 10 a 180 segundos.

## Configurações da captura

- Dimensão máxima: 768, 1024, 1280 ou 1600 pixels.
- Qualidade JPEG: 40% a 95%.
- Imagem reduzida antes da codificação Base64.

## Compilar

Requisitos: Android Studio recente, JDK 17 e Android SDK 35. Abra o projeto e use **Build > Build APK(s)**.

R8 e redução de recursos estão ativados. O tamanho final depende das ferramentas e da arquitetura; 50 MB é uma meta que deve ser medida no APK gerado.

## Privacidade e plataforma

- A captura exige autorização explícita do Android.
- A imagem é processada quando o usuário toca na bolha.
- A sessão usa um único VirtualDisplay por autorização, conforme as regras atuais de MediaProjection.
- O uso da API pode gerar custos conforme o provedor e o modelo.

## Coach de Volleyball Legends (modo contínuo)

- Com o CLC ativo e a bolha visível, **segure a bolha por cerca de 1 segundo** para ligar/desligar o Coach.
- Quando ligado, o CLC captura a tela aproximadamente a cada 12 segundos e envia a imagem ao modelo configurado para receber uma dica curta.
- As dicas tentam identificar a zona livre da quadra, risco de bloqueio e função provável do personagem. A base inicial de estilos/personagens fica em `VolleyballLegendsKnowledge.kt`.
- **Toque rápido** na bolha continua abrindo a análise manual.
- Use um modelo com suporte a imagens/visão. Modelos apenas de texto não conseguem analisar as capturas.
- O modo contínuo pode consumir bastante API e bateria/dados móveis. Desative segurando a bolha novamente ou parando o serviço.
- A captura só ocorre após a autorização do Android para MediaProjection. O Coach fornece dicas visuais; não toca, mira ou controla o jogo.
- A base de personagens é inicial e pode ficar desatualizada após patches do Volleyball Legends; recomendações dependem do que estiver visível na captura.
