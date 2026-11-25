# Potato AI — Android

Potato AI é um assistente Android experimental com uma bolha flutuante. O usuário inicia a captura pelo diálogo oficial do Android; ao tocar na bolha, o app captura a tela, permite escrever uma pergunta e envia a imagem à API OpenAI somente quando o usuário toca em **Analisar tela**.

## Recursos nesta versão alpha
- Bolha flutuante que pode ser movida.
- Captura de tela iniciada e controlada pelo usuário.
- Perguntas sobre a captura usando um modelo de visão da OpenAI.
- Resposta exibida dentro do app.

## Requisitos
- Android Studio recente, JDK 17 e Android SDK Platform 35.
- Chave de API OpenAI com acesso e créditos disponíveis. O uso da API pode gerar custos.

## Compilar
O workflow **Build Potato AI APK** compila automaticamente no GitHub Actions em cada push para `main`. O APK de debug é publicado como artefato do workflow e também na branch `dist-apk`.

## Usar
1. Instale o APK e abra Potato AI.
2. Insira sua própria chave da API OpenAI.
3. Permita a exibição sobre outros apps.
4. Toque em **Ativar assistente flutuante** e aceite a captura de tela do Android.
5. Toque na bolha para capturar a tela, escreva uma pergunta e escolha **Analisar tela**.
6. Use **Parar assistente** para encerrar.

## Privacidade e limitações
- Não há captura contínua: a captura ocorre após o toque do usuário na bolha.
- A imagem só é enviada à API quando o usuário solicita a análise.
- Não use em telas com dados privados ou de terceiros sem autorização.
- A chave fica nas preferências privadas do app, mas não usa armazenamento seguro dedicado. Não compartilhe o app com pessoas não confiáveis.
- Não captura conteúdo protegido por DRM nem telas bloqueadas pelo Android.
- Esta é uma versão alpha: ainda precisa de testes reais em diferentes aparelhos e versões do Android.
