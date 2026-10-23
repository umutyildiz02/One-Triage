import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Unauthenticated, non-generative connectivity diagnostic. Never reads credentials. */
class ProbeOpenAiTransport {
    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.httpclient.disableRetryConnect", "true");
        for (HttpClient.Version version : HttpClient.Version.values()) {
            HttpClient client = HttpClient.newBuilder().version(version)
                .connectTimeout(Duration.ofSeconds(10)).build();
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) Thread.sleep(30000);
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
                        .timeout(Duration.ofSeconds(15)).GET().build();
                    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    System.out.println(version + " probe=" + attempt + " status=" + response.statusCode() + " negotiated=" + response.version());
                } catch (Exception failure) {
                    System.out.println(version + " probe=" + attempt + " failure=" + failure.getClass().getSimpleName());
                    Throwable cause = failure.getCause();
                    if (cause != null) System.out.println("cause=" + cause.getClass().getSimpleName());
                }
            }
        }
    }
}
