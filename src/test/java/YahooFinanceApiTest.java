import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Teste rápido da Yahoo Finance API
 * Execute: mvn exec:java -Dexec.mainClass="com.analytics.portfolio.YahooFinanceApiTest"
 */
public class YahooFinanceApiTest {

    public static void main(String[] args) {
        try {
            // Sua API key
            String apiKey = "ce43547f03msh8d1cad3713cffd8p1cca0bjsn9a33561ca315";
            String apiHost = "yahoo-finance15.p.rapidapi.com";

            // Teste 1: Cotação de uma ação (AAPL)
            System.out.println("=== TESTE 1: Cotação SONAE ===");
            testQuote("SON.LS", apiKey, apiHost);

            Thread.sleep(2000); // Aguardar entre chamadas

            // Teste 2: Cotação de outra ação (MSFT)
            System.out.println("\n=== TESTE 2: Cotação NOS ===");
            testQuote("NOS.LS", apiKey, apiHost);

            Thread.sleep(2000);

            // Teste 3: Batch - Múltiplas ações
            System.out.println("\n=== TESTE 3: BATCH (AAPL, MSFT, GOOGL) ===");
            testBatchQuotes("AAPL,MSFT,GOOGL", apiKey, apiHost);

            System.out.println("\n✅ Todos os testes concluídos!");

        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testQuote(String ticker, String apiKey, String apiHost) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://yahoo-finance15.p.rapidapi.com/api/v1/markets/quote?ticker=" + ticker + "&type=STOCKS"))
                .header("x-rapidapi-key", apiKey)
                .header("x-rapidapi-host", apiHost)
                .header("Content-Type", "application/json")
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Resposta: " + response.body());
    }

    private static void testBatchQuotes(String tickers, String apiKey, String apiHost) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://yahoo-finance15.p.rapidapi.com/api/v1/markets/quote?ticker=" + tickers + "&type=STOCKS"))
                .header("x-rapidapi-key", apiKey)
                .header("x-rapidapi-host", apiHost)
                .header("Content-Type", "application/json")
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Resposta (primeiros 500 chars): " +
                response.body().substring(0, Math.min(500, response.body().length())) + "...");
    }
}
