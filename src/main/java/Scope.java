import burp.api.montoya.MontoyaApi;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Manages scope synchronization with the DeepBounty server
 */
public class Scope {

    private final MontoyaApi api;
    private final Settings settings;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Set<String> currentScopeSubdomains = new HashSet<String>();
    private int currentScopeVersion = -1;

    public Scope(MontoyaApi api, Settings settings) {
        this.api = api;
        this.settings = settings;
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(1);
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
            return parseVersionFromJson(response.body());
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
            // Parse JSON: {"version": versionNum, "subdomains": ["subdomain1", "subdomain2"]}
            int newVersion = parseVersionFromJson(scopeJson);
            String[] subdomains = parseSubdomainsFromJson(scopeJson);

            if (subdomains.length == 0) {
                return;
            }

            // Add each subdomain to local scope
            for (String subdomain : subdomains) {
                if (subdomain != null && !subdomain.isEmpty()) {
                    currentScopeSubdomains.add(subdomain);
                }
            }

            // Update local version
            currentScopeVersion = newVersion;
            api.logging().logToOutput("Scope updated successfully! Version: " + newVersion);

        } catch (Exception e) {
            api.logging().logToError("Error parsing scope JSON: " + e.getMessage());
            api.logging().logToError("Response was: " + scopeJson);
        }
    }

    /**
     * Parse version number from JSON response
     */
    private int parseVersionFromJson(String json) {
        // Pattern to match "version": number or "version":number
        Pattern pattern = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return -1;
    }

    /**
     * Parse subdomains array from JSON response
     */
    private String[] parseSubdomainsFromJson(String json) {
        // Pattern to match "subdomains": ["item1", "item2", ...]
        Pattern pattern = Pattern.compile("\"subdomains\"\\s*:\\s*\\[([^]]*)]");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String arrayContent = matcher.group(1);

            // Extract quoted strings
            Pattern itemPattern = Pattern.compile("\"([^\"]+)\"");
            Matcher itemMatcher = itemPattern.matcher(arrayContent);

            java.util.List<String> subdomains = new java.util.ArrayList<>();
            while (itemMatcher.find()) {
                subdomains.add(itemMatcher.group(1));
            }

            return subdomains.toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * Get the current scope subdomains
     */
    public Set<String> getScope() {
        return currentScopeSubdomains;
    }
}
