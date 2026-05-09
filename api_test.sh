# 1. Verificar se está UP
curl http://localhost:8080/actuator/health

# 2. Criar portfólio
curl -X POST http://localhost:8080/api/portfolios \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Teste",
    "currency": "EUR",
    "initialCapital": 10000
  }'

# 3. Ver portfólios
curl http://localhost:8080/api/portfolios
```

## 🌐 Ou use o Swagger (MAIS VISUAL)

Abra no navegador:
```
http://localhost:8080/swagger-ui.html
```

- Interface gráfica
- Testar todos os endpoints
- Upload de ficheiros
- Ver respostas formatadas

## 📊 Verificar os Dados no H2
```
http://localhost:8080/h2-console