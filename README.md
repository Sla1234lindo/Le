# CLC — Assistente visual para Android

Assistente visual com bolha flutuante. A captura só acontece quando o usuário toca na bolha; depois, é possível conversar com uma IA sobre a mesma imagem.

## Recursos

- Captura de tela iniciada pelo usuário usando a permissão oficial do Android.
- Perguntas de acompanhamento sobre a captura atual.
- Provedor configurável por URL base, chave de API e nome do modelo.
- Compatibilidade com APIs OpenAI Chat Completions que aceitem entrada de imagem (vision).
- Atalhos para resumir, explicar e sugerir ações.
- Redimensionamento e compressão JPEG antes do envio.
- Interface leve, sem bibliotecas visuais externas.

## Configurar a IA

Na tela inicial, informe a URL base, o modelo e sua chave. Exemplo de URL base OpenAI: `https://api.openai.com/v1`. Use a URL base documentada pelo provedor que escolher.

O CLC acrescenta `/chat/completions` automaticamente, a menos que a URL já termine nesse caminho. O provedor precisa suportar o formato OpenAI Chat Completions e imagens; compatibilidade apenas com texto não é suficiente para analisar capturas.

**Nunca coloque chaves reais no código, README, commits ou prints.** A chave fica nas preferências privadas do app. Para distribuição pública, prefira um backend próprio ou armazenamento seguro.

## Compilar

Requisitos: Android Studio recente, JDK 17 e Android SDK 35. Abra o projeto e use **Build > Build APK(s)**.

R8 e a redução de recursos estão ativados nos builds debug e release. O tamanho final depende das ferramentas e da arquitetura; 50 MB é uma meta a medir no APK gerado, não uma garantia.

## Privacidade

- Não existe captura contínua: é necessária ação explícita do usuário.
- A imagem só é enviada quando uma pergunta é enviada.
- Conteúdo protegido por DRM ou bloqueado pelo Android não pode ser capturado.
- O uso da API pode gerar custos conforme o provedor e o modelo.
