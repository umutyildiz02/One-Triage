import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** No key, no generation: reproduces the gateway's body handling across an idle interval. */
class ProbeOpenAiIdleTransport {
    public static void main(String[] args) throws Exception {
        int idleSeconds = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        if (idleSeconds < 30 || idleSeconds > 360) throw new IllegalArgumentException("Idle interval must be 30..360 seconds");
        System.setProperty("jdk.httpclient.disableRetryConnect", "true");
        System.setProperty("jdk.httpclient.keepalive.timeout", "5");
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                for (int elapsed = 0; elapsed < idleSeconds; elapsed += 30) {
                    Thread.sleep(Math.min(30, idleSeconds - elapsed) * 1000L);
                    System.out.println("Idle seconds=" + Math.min(elapsed + 30, idleSeconds));
                }
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int bytes;
                try (var body = response.body()) { bytes = body.readNBytes(128001).length; }
                System.out.println("probe=" + attempt + " status=" + response.statusCode() + " bytes=" + bytes + " protocol=" + response.version());
                if (response.statusCode() != 401) throw new IllegalStateException("Expected unauthenticated response");
            } catch (Exception failure) {
                System.out.println("probe=" + attempt + " failure=" + failure.getClass().getSimpleName());
                Throwable cause = failure.getCause();
                if (cause != null) System.out.println("cause=" + cause.getClass().getSimpleName());
                throw new IllegalStateException("Unauthenticated transport check failed");
            }
        }
    }
}
