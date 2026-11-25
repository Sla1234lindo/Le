# ScreenAI — projeto Android

Assistente Android com bolha flutuante. Ao tocar na bolha, captura a tela atual com a permissão do Android, abre uma tela de pergunta e envia a imagem para a API OpenAI apenas quando você toca em **Analisar tela**.

## Requisitos
- Android Studio recente
- JDK 17
- Android SDK Platform 35
- Uma chave de API da OpenAI com acesso e saldo/créditos disponíveis

## Como gerar o APK
1. Abra esta pasta no Android Studio (Open / Open Project).
2. Aguarde o Gradle sincronizar e instalar os componentes necessários.
3. Pressione **Build > Build APK(s)**.
4. O APK de debug costuma ficar em `app/build/outputs/apk/debug/app-debug.apk`.
5. Instale o APK no celular. Na primeira ativação, permita aparecer sobre outros apps e autorize a captura de tela.

## Uso
1. Abra ScreenAI e insira sua chave de API.
2. Toque em **Ativar assistente flutuante**.
3. Aceite a captura de tela e toque na bolinha **AI** quando quiser analisar.
4. Escreva uma pergunta e toque em **Analisar tela**.
5. Para encerrar a captura e a bolinha, abra o app e toque em **Parar assistente**.

## Privacidade e limitações
- A captura é iniciada pelo usuário através do diálogo oficial do Android.
- O app não faz monitoramento contínuo: só captura após tocar na bolinha.
- A imagem é enviada à API quando a pergunta é enviada; use apenas em telas que você pode compartilhar.
- A chave é armazenada nas preferências privadas do app, não em armazenamento seguro dedicado. Não compartilhe o aparelho/app com pessoas não confiáveis.
- A API pode gerar custos. Este projeto é um protótipo e não inclui faturamento, login, nem proteção avançada da chave.
- Não captura conteúdo protegido por DRM ou telas que o Android bloqueia.
