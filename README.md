# Portfolio Analytics - Sistema de Análise de Portfólios

Sistema completo de análise e gestão de portfólios de investimentos com suporte a importação de dados da **XTB** e **Binance**, cálculo de métricas avançadas e visualização de performance.

## 🚀 Funcionalidades Principais

### Gestão de Portfólios
- Criar e gerir múltiplos portfólios
- Acompanhamento de posições em tempo real
- Histórico completo de transações
- Suporte para múltiplas moedas (EUR, USD, etc.)

### Importação de Dados
- **XTB**: Importação de transações e dividendos via CSV/Excel
- **Binance**: Importação de trades via CSV ou API direta
- Suporte para staking rewards da Binance
- Detecção automática de tipo de ativo

### Métricas e Análises

#### Métricas do Portfólio
- **Retornos**:
  - Retorno total (absoluto e percentual)
  - Retorno anualizado (CAGR)
  - Retorno mensal e YTD
  - Time-weighted return (TWR)
  - Money-weighted return (IRR)

- **Dividendos**:
  - Total de dividendos recebidos
  - Dividend Yield
  - Dividend Yield anualizado
  - Histórico de pagamentos

- **Risco e Performance**:
  - Volatilidade (desvio padrão anualizado)
  - Sharpe Ratio
  - Sortino Ratio
  - Maximum Drawdown
  - Beta (em relação ao mercado)

#### Métricas por Ativo
- Retorno realizado e não realizado
- Retorno total com dividendos
- Retorno anualizado por ativo
- Peso no portfólio
- Dias mantidos
- Total de taxas

### Cálculos Avançados
- **Anualização de retornos**: Usa fórmula composta `((1 + r)^(365.25/days) - 1) * 100`
- **Dividend Yield anualizado**: Projeção baseada em dividendos históricos
- **Volatilidade anualizada**: Desvio padrão * √252
- **Drawdown**: Maior queda desde o pico histórico

## 📋 Requisitos

- Java 17+
- Maven 3.6+
- PostgreSQL 12+ (produção) ou H2 (desenvolvimento)

## 🛠️ Instalação

### 1. Clone o repositório

```bash
git clone <repository-url>
cd portfolio-analytics
```

### 2. Configure o banco de dados

O projeto vem configurado com H2 para desenvolvimento. Para produção, edite `application.properties`:

```properties
# PostgreSQL Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/portfoliodb
spring.datasource.username=seu_usuario
spring.datasource.password=sua_senha
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### 3. Compile e execute

```bash
mvn clean install
mvn spring-boot:run
```

A aplicação estará disponível em `http://localhost:8080`

## 📚 API Documentation

Após iniciar a aplicação, aceda à documentação Swagger em:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## 🔌 Endpoints Principais

### Portfolios

```
GET    /api/portfolios              - Lista todos os portfólios
POST   /api/portfolios              - Cria novo portfólio
GET    /api/portfolios/{id}         - Detalhes do portfólio
PUT    /api/portfolios/{id}         - Atualiza portfólio
DELETE /api/portfolios/{id}         - Remove portfólio
```

### Métricas

```
GET /api/portfolios/{id}/metrics              - Métricas gerais do portfólio
GET /api/portfolios/{id}/positions/metrics    - Métricas por ativo
```

### Transações e Dividendos

```
GET /api/portfolios/{id}/transactions  - Lista transações
GET /api/portfolios/{id}/dividends     - Lista dividendos
```

### Importação

```
POST /api/portfolios/{id}/import/xtb              - Importa transações XTB
POST /api/portfolios/{id}/import/xtb/dividends    - Importa dividendos XTB
POST /api/portfolios/{id}/import/binance          - Importa trades Binance
```

## 📊 Exemplos de Uso

### Criar um Portfólio

```json
POST /api/portfolios
{
  "name": "Meu Portfólio Principal",
  "description": "Investimentos de longo prazo",
  "currency": "EUR",
  "initialCapital": 10000.00
}
```

### Importar Transações da XTB

```bash
curl -X POST http://localhost:8080/api/portfolios/1/import/xtb \
  -F "file=@xtb_transactions.csv"
```

### Obter Métricas do Portfólio

```bash
curl http://localhost:8080/api/portfolios/1/metrics
```

Resposta:
```json
{
  "portfolioId": 1,
  "portfolioName": "Meu Portfólio",
  "totalInvested": 10000.00,
  "currentValue": 12500.00,
  "totalReturn": 2500.00,
  "totalReturnPercentage": 25.00,
  "annualizedReturn": 18.92,
  "totalDividendsReceived": 350.00,
  "dividendYield": 3.50,
  "annualizedDividendYield": 3.45,
  "volatility": 15.30,
  "sharpeRatio": 1.24,
  "maxDrawdown": -12.50
}
```

## 📁 Estrutura do Projeto

```
├───main
│   ├───java
│   │   └───com
│   │       └───analytics
│   │           └───portfolio
│   │               │   PortfolioAnalyticsApplication.java
│   │               │
│   │               ├───clients
│   │               │       YahooFinanceClient.java
│   │               │
│   │               ├───config
│   │               │       DataInitializer.java
│   │               │       JacksonConfig.java
│   │               │       JwtProperties.java
│   │               │       OpenApiConfig.java
│   │               │       YahooFinanceConfig.java
│   │               │
│   │               ├───controller
│   │               │       AssetController.java
│   │               │       AuthController.java
│   │               │       ClosedPositionController.java
│   │               │       HoldingsController.java
│   │               │       MarketDataController.java
│   │               │       PortfolioController.java
│   │               │
│   │               ├───dto
│   │               │   │   AssetMetrics.java
│   │               │   │   ClosedPositionStats.java
│   │               │   │   MarketQuoteDto.java
│   │               │   │   PortfolioMetrics.java
│   │               │   │   YahooFinanceDTO.java
│   │               │   │
│   │               │   └───auth
│   │               │           AuthResponse.java
│   │               │           ChangePasswordRequest.java
│   │               │           LoginRequest.java
│   │               │           PasswordResetRequest.java
│   │               │           RefreshTokenRequest.java
│   │               │           RegisterRequest.java
│   │               │
│   │               ├───enums
│   │               │       AssetSource.java
│   │               │       AssetType.java
│   │               │       TransactionType.java
│   │               │
│   │               ├───exceptions
│   │               │       GlobalExceptionHandler.java
│   │               │
│   │               ├───integration
│   │               │       BinanceImportService.java
│   │               │       XTBImportService.java
│   │               │
│   │               ├───model
│   │               │       Asset.java
│   │               │       CashFlow.java
│   │               │       ClosedPosition.java
│   │               │       Dividend.java
│   │               │       Fingerprintable.java
│   │               │       Portfolio.java
│   │               │       Position.java
│   │               │       PriceHistory.java
│   │               │       RefreshToken.java
│   │               │       Role.java
│   │               │       Transaction.java
│   │               │       User.java
│   │               │
│   │               ├───repository
│   │               │       AssetRepository.java
│   │               │       CashFlowRepository.java
│   │               │       ClosedPositionRepository.java
│   │               │       DividendRepository.java
│   │               │       PortfolioRepository.java
│   │               │       PositionRepository.java
│   │               │       PriceHistoryRepository.java
│   │               │       RefreshTokenRepository.java
│   │               │       RoleRepository.java
│   │               │       TransactionRepository.java
│   │               │       UserRepository.java
│   │               │
│   │               ├───schedule
│   │               │       PriceUpdateScheduler.java
│   │               │
│   │               ├───security
│   │               │       CustomUserDetailsService.java
│   │               │       JwtAuthenticationEntryPoint.java
│   │               │       JwtAuthenticationFilter.java
│   │               │       JwtTokenProvider.java
│   │               │       SecurityConfig.java
│   │               │
│   │               ├───service
│   │               │       AuthService.java
│   │               │       ClosedPositionService.java
│   │               │       DuplicateDetectionService.java
│   │               │       EmailService.java
│   │               │       HoldingsCalculationService.java
│   │               │       MetricsCalculationService.java
│   │               │       PositionService.java
│   │               │       RefreshTokenService.java
│   │               │       YahooFinanceService.java
│   │               │
│   │               ├───utils
│   │               │       PortfolioUtils.java
│   │               │
│   │               └───{model,repository,service,controller,dto,config,integration,util}
│   └───resources
│       │   application.properties
│       │
│       └───static
│               index.html
│
└───test
    └───java
        │   YahooFinanceApiTest.java
        │
        └───com
            └───analytics
                └───portfolio
```

## 🔧 Configuração Avançada

### Integração com Binance API

Para usar a API da Binance diretamente (em vez de importação de CSV):

```properties
binance.api.url=https://api.binance.com
```

No código:
```java
List<Transaction> trades = binanceImportService.importTradesFromAPI(
    "your_api_key", 
    "your_api_secret", 
    "BTCUSDT", 
    portfolioId
);
```

### Agendamento de Atualizações

O sistema pode atualizar preços automaticamente:

```properties
portfolio.price.update.cron=0 0 * * * ?  # A cada hora
portfolio.metrics.calculation.cron=0 30 * * * ?  # Cada 30 minutos
```

## 🧮 Fórmulas Utilizadas

### Retorno Anualizado (CAGR)
```
Retorno Anualizado = ((1 + Retorno Total)^(365.25/dias) - 1) × 100
```

### Volatilidade Anualizada
```
Volatilidade = Desvio Padrão dos Retornos Diários × √252
```

### Sharpe Ratio
```
Sharpe = (Retorno do Portfolio - Taxa Livre de Risco) / Volatilidade
```

### Dividend Yield Anualizado
```
Dividend Yield Anualizado = (Total Dividendos / Investimento) × (365.25 / dias)
```

## 🎯 Próximos Passos

- [ ] Frontend em React/Vue.js
- [ ] Gráficos interativos com Chart.js/D3.js
- [ ] Otimização de portfólio (Markowitz)
- [ ] Backtesting de estratégias
- [ ] Alertas e notificações
- [ ] Relatórios em PDF
- [ ] Dashboard em tempo real
- [ ] Suporte para mais brokers (Interactive Brokers, Trading212, etc.)

## 📝 Formatos de Importação

### XTB CSV Format
```csv
Date,Symbol,Type,Quantity,Price,Commission,Amount
2024-01-15 10:30:00,AAPL.US,BUY,10,150.50,5.00,1505.00
```

### Binance CSV Format
```csv
Date(UTC),Pair,Type,Order Price,Order Amount,AvgTrading Price,Filled,Total,Fee
2024-01-15 10:30:00,BTCUSDT,BUY,45000.00,0.001,45000.00,0.001,45.00,0.045 USDT
```

## 🤝 Contribuições

Contribuições são bem-vindas! Por favor:
1. Fork o projeto
2. Crie uma branch para sua feature
3. Commit suas mudanças
4. Push para a branch
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT.

## 💬 Suporte

Para questões e suporte, abra uma issue no GitHub.

---

Desenvolvido com ☕ e Java
