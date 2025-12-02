import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.MimeType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import json.JSONBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * HTTP Handler for intercepting and forwarding traffic to the server
 */
public class Handler implements HttpHandler {

    private final MontoyaApi api;
    private final Settings settings;
    private final Gson gson;
    private final HttpClient httpClient;

    // Allowed Mime types for processing
    private static final Set<MimeType> ALLOWED_MIMES = Set.of(
            MimeType.JSON, MimeType.XML, MimeType.HTML,
            MimeType.SCRIPT, MimeType.PLAIN_TEXT
    );

    public Handler(MontoyaApi api, Settings settings) {
        // Configure HttpClient with virtual threads for async operations
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new GsonBuilder()
                .disableHtmlEscaping()
                .create();
        this.api = api;
        this.settings = settings;
        api.logging().logToOutput("Handler initialized successfully");
    }

    /**
     * Send traffic data to DeepBounty server asynchronously
     */
    private void sendToServer(JSONBody.Traffic traffic) {
        String serverUrl = settings.getServerUrl();
        String apiKey = settings.getBurpsuiteKey();

        // Validate settings
        if (serverUrl == null || serverUrl.isEmpty()) {
            return;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        try {
            String jsonPayload = gson.toJson(traffic);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/ingest/burp"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            // Async send (Fire and Forget)
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            api.logging().logToError("Error creating request: " + e.getMessage());
        }
    }

    /**
     * Convert Burp HttpHeaders to Map<String, String>
     */
    private Map<String, String> mapHeaders(List<HttpHeader> headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (headers != null) {
            for (HttpHeader header : headers) {
                // Concatenate multiple values for same header name
                String existingValue = headerMap.get(header.name());
                if (existingValue != null) {
                    headerMap.put(header.name(), existingValue + "; " + header.value());
                } else {
                    headerMap.put(header.name(), header.value());
                }
            }
        }
        return headerMap;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Simply forward the request without modification
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        try {
            // Filter mime types
            MimeType mimeType = responseReceived.inferredMimeType();
            if (!ALLOWED_MIMES.contains(mimeType)) {
                return ResponseReceivedAction.continueWith(responseReceived);
            }

            var initiatingRequest = responseReceived.initiatingRequest();

            JSONBody.Traffic traffic = new JSONBody.Traffic(
                    initiatingRequest.url(),
                    initiatingRequest.method(),
                    responseReceived.statusCode(),
                    mapHeaders(initiatingRequest.headers()),
                    mapHeaders(responseReceived.headers()),
                    initiatingRequest.bodyToString(),
                    responseReceived.bodyToString(),
                    mimeType.name()
            );

            // Send traffic data to server asynchronously
            sendToServer(traffic);

        } catch (Exception e) {
            api.logging().logToError("Error handling response: " + e.getMessage());
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }
}