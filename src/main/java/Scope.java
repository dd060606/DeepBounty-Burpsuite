import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import json.JSONBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Manages scope synchronization with the DeepBounty server
 */
public class Scope {

    private final MontoyaApi api;
    private final Settings settings;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private final Set<String> currentScopeSubdomains = new HashSet<>();
    private int currentScopeVersion = -1;

    public Scope(MontoyaApi api, Settings settings) {
        this.api = api;
        this.settings = settings;
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.gson = new Gson();
    }

    /**
     * Start periodic scope version checking (every 10 seconds)
     */
    public void startScopeVersionCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndUpdateScope();
            } catch (Exception e) {
                api.logging().logToError("Error checking scope version: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);

        api.logging().logToOutput("Scope version check started - checking every 10 seconds");
    }

    /**
     * Check if the remote scope version is newer and update if needed
     */
    private void checkAndUpdateScope() {
        String serverUrl = settings.getServerUrl();
        String burpsuiteKey = settings.getBurpsuiteKey();

        // Verify configuration
        if (serverUrl.isEmpty()) {
            api.logging().logToError("Server URL is not configured. Please configure it in settings.");
            return;
        }
        if (burpsuiteKey.isEmpty()) {
            api.logging().logToError("Burpsuite key is not configured. Please configure it in settings.");
            return;
        }

        try {
            // Check version from server
            int remoteVersion = getRemoteScopeVersion(serverUrl, burpsuiteKey);

            // If remote version is newer, fetch and update scope
            if (remoteVersion > currentScopeVersion) {
                api.logging().logToOutput("Scope version mismatch detected. Remote: " + remoteVersion + ", Local: " + currentScopeVersion);
                api.logging().logToOutput("Fetching new scope from server...");

                // Fetch and apply new scope
                fetchAndUpdateScope(serverUrl, burpsuiteKey);
            }
        } catch (IOException | InterruptedException e) {
            api.logging().logToError("Error during scope check: " + e.getMessage());
        }
    }

    /**
     * Get the current scope version from the server
     */
    private int getRemoteScopeVersion(String serverUrl, String burpsuiteKey) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/scope/version"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10));

        requestBuilder.header("Authorization", "Bearer " + burpsuiteKey);

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            try {
                JSONBody.ScopeVersionResponse versionResponse = gson.fromJson(response.body(), JSONBody.ScopeVersionResponse.class);
                return versionResponse.getVersion();
            } catch (JsonSyntaxException e) {
                api.logging().logToError("Failed to parse scope version JSON: " + e.getMessage());
                return currentScopeVersion;
            }
        } else {
            api.logging().logToError("Failed to get scope version. Status: " + response.statusCode());
            return currentScopeVersion;
        }
    }

    /**
     * Fetch the full scope from the server and update Burp scope
     */
    private void fetchAndUpdateScope(String serverUrl, String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/scope"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10));

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            updateBurpScope(response.body());
        } else {
            api.logging().logToError("Failed to fetch scope. Status: " + response.statusCode());
        }
    }

    /**
     * Parse the scope JSON and update Burp's scope
     */
    private void updateBurpScope(String scopeJson) {
        try {
            JSONBody.ScopeResponse scopeResponse = gson.fromJson(scopeJson, JSONBody.ScopeResponse.class);

            if (scopeResponse == null || scopeResponse.getSubdomains() == null || scopeResponse.getSubdomains().isEmpty()) {
                api.logging().logToOutput("No subdomains found in scope response");
                return;
            }

            // Add each subdomain to local scope
            for (String subdomain : scopeResponse.getSubdomains()) {
                if (subdomain != null && !subdomain.isEmpty()) {
                    currentScopeSubdomains.add(subdomain);
                }
            }

            // Update local version
            currentScopeVersion = scopeResponse.getVersion();
            api.logging().logToOutput("Scope updated successfully! Version: " + currentScopeVersion + ", Subdomains: " + scopeResponse.getSubdomains().size());

        } catch (JsonSyntaxException e) {
            api.logging().logToError("Error parsing scope JSON: " + e.getMessage());
            api.logging().logToError("Response was: " + scopeJson);
        }
    }


    /**
     * Get the current scope subdomains
     */
    public Set<String> getScope() {
        return currentScopeSubdomains;
    }
}
