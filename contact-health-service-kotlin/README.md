# QLO-FEAT-003: Avaliador de Saúde e Higiene de Contatos de Hóspedes

Microsserviço local autônomo desenvolvido em **Kotlin/Ktor** para cálculo de métricas de higiene cadastral, staleness e conformidade regulatória (LGPD) de contatos de hóspedes do QloApps, conforme a especificação técnica da **RFC-003**.

---

## Estrutura do Projeto

```text
contact-health-service-kotlin/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/
│   │           └── hotel/
│   │               └── contacthealth/
│   │                   └── Application.kt       # Ponto de entrada e rotas Ktor
│   └── test/
│       └── kotlin/
│           └── com/
│               └── hotel/
│                   └── contacthealth/
│                       └── ApplicationTest.kt   # Testes automatizados (Ktor Test Host)
├── build.gradle.kts                             # Configurações de dependências e JVM
├── gradle.properties                            # Propriedades de JVM e cache Gradle
├── gradlew                                      # Executável do Gradle Wrapper (Linux/macOS)
├── gradlew.bat                                  # Executável do Gradle Wrapper (Windows)
├── settings.gradle.kts                          # Definições do projeto Gradle
└── README.md                                    # Esta documentação
```


## Pré-requisitos e Ambiente

* JDK: Versão 17 ou 21 instalada e configurada (JAVA_HOME)

* Gradle: 8.7+ (gerenciado via Gradle Wrapper incluso ./gradlew)

* Porta Local: 8103 livre na interface 127.0.0.1

--- 

## Como Executar
1. Inicializar o Microsserviço
Navegue até a pasta do serviço e execute:


* cd contact-health-service-kotlin
./gradlew run


O servidor Ktor inicializará utilizando a engine Netty escutando em:
http://127.0.0.1:8103

2. Executar os Testes Automatizados
Para rodar a suíte de testes de integração da API:


* ./gradlew test

Para inspecionar o log detalhado dos testes:


* ./gradlew test --info

## Exemplos de Teste e Chamadas (cURL)
1. Verificação de Saúde do Serviço (GET /healthz)
Requisição:


* curl -i -X GET http://127.0.0.1:8103/healthz

Resposta esperada (HTTP 200 OK):

* http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 15
{"status":"UP"}


## Tecnologias Utilizadas
Linguagem: Kotlin 1.9.23

Framework: Ktor 2.3.9 (ktor-server-core, ktor-server-netty)

Serialização: Kotlinx Serialization JSON

Logging: Logback Classic

Testes: JUnit 5 (Platform Engine) + Ktor Server Test Host