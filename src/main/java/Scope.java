import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import json.JSONBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
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
    private volatile Set<String> currentScopeSubdomains = Collections.emptySet();
    private volatile Set<String> exactScopes = Collections.emptySet();
    private volatile List<String> wildcardSuffixes = Collections.emptyList();
    private int currentScopeVersion = -1;

    public Scope(MontoyaApi api, Settings settings) {
        this.api = api;
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
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
                safeLogToError("Error checking scope version: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);

        safeLogToOutput("Scope version check started - checking every 10 seconds");
    }

    /**
     * Check if the remote scope version is newer and update if needed
     */
    private void checkAndUpdateScope() {
        String serverUrl = settings.getServerUrl();
        String burpsuiteKey = settings.getBurpsuiteKey();

        // Verify configuration
        if (serverUrl.isEmpty()) {
            safeLogToError("Server URL is not configured. Please configure it in settings.");
            return;
        }
        if (burpsuiteKey.isEmpty()) {
            safeLogToError("Burpsuite key is not configured. Please configure it in settings.");
            return;
        }

        try {
            // Check version from server
            int remoteVersion = getRemoteScopeVersion(serverUrl, burpsuiteKey);

            // If remote version is newer, fetch and update scope
            if (remoteVersion > currentScopeVersion) {
                safeLogToOutput("Scope version mismatch detected. Remote: " + remoteVersion + ", Local: " + currentScopeVersion);

                // Fetch and apply new scope
                fetchAndUpdateScope(serverUrl, burpsuiteKey);
            }
        } catch (IOException | InterruptedException e) {
            safeLogToError("Error during scope check: " + e.getMessage());
        }
    }

    /**
     * Get the current scope version from the server
     */
    private int getRemoteScopeVersion(String serverUrl, String burpsuiteKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/scope/version"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + burpsuiteKey)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            try {
                JSONBody.ScopeVersionResponse versionResponse = gson.fromJson(response.body(), JSONBody.ScopeVersionResponse.class);
                return versionResponse.getVersion();
            } catch (JsonSyntaxException e) {
                safeLogToError("Failed to parse scope version JSON: " + e.getMessage());
                return currentScopeVersion;
            }
        } else {
            safeLogToError("Failed to get scope version. Status: " + response.statusCode());
            safeLogToError("Response body: " + response.body());
            return currentScopeVersion;
        }
    }

    /**
     * Fetch the full scope from the server and update Burp scope
     */
    private void fetchAndUpdateScope(String serverUrl, String apiKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/scope"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            updateBurpScope(response.body());
        } else {
            safeLogToError("Failed to fetch scope. Status: " + response.statusCode());
        }
    }

    /**
     * Parse the scope JSON and update Burp's scope
     * Completely replaces the current scope to handle removed domains
     */
    private void updateBurpScope(String scopeJson) {
        try {
            JSONBody.ScopeResponse scopeResponse = gson.fromJson(scopeJson, JSONBody.ScopeResponse.class);

            if (scopeResponse == null || scopeResponse.getSubdomains() == null || scopeResponse.getSubdomains().isEmpty()) {
                safeLogToOutput("No subdomains found in scope response");
                // Clear the scope if the server returns empty
                currentScopeSubdomains = Collections.emptySet();
                exactScopes = Collections.emptySet();
                wildcardSuffixes = Collections.emptyList();
                currentScopeVersion = scopeResponse != null ? scopeResponse.getVersion() : currentScopeVersion;
                return;
            }

            // Create new collections
            Set<String> newScopeSubdomains = new HashSet<>();
            Set<String> newExactScopes = new HashSet<>();
            List<String> newWildcardSuffixes = new ArrayList<>();

            // Add all subdomains from server response
            for (String subdomain : scopeResponse.getSubdomains()) {
                if (subdomain != null && !subdomain.isEmpty()) {
                    newScopeSubdomains.add(subdomain);

                    if (subdomain.startsWith("*.")) {
                        newWildcardSuffixes.add(subdomain.substring(2));
                    } else {
                        newExactScopes.add(subdomain);
                    }
                }
            }

            // Update references atomically
            currentScopeSubdomains = newScopeSubdomains;
            exactScopes = newExactScopes;
            wildcardSuffixes = newWildcardSuffixes;

            // Update local version
            currentScopeVersion = scopeResponse.getVersion();
            safeLogToOutput("Scope updated successfully! Version: " + currentScopeVersion + ", Subdomains: " + scopeResponse.getSubdomains().size());

        } catch (JsonSyntaxException e) {
            safeLogToError("Error parsing scope JSON: " + e.getMessage());
            safeLogToError("Response was: " + scopeJson);
        }
    }

    /**
     * Check if a full URL is in scope
     */
    public boolean isInScope(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return isHostInScope(host);
        } catch (Exception e) {
            safeLogToError("Error checking scope for URL: " + url + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a host is in scope efficiently
     */
    public boolean isHostInScope(String host) {
        if (host == null || host.isEmpty()) return false;

        // Check exact matches first (O(1))
        if (exactScopes.contains(host)) {
            return true;
        }

        // Check wildcards
        for (String suffix : wildcardSuffixes) {
            if (host.endsWith("." + suffix) || host.equals(suffix)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Get the current scope subdomains
     */
    public Set<String> getScope() {
        return currentScopeSubdomains;
    }

    /**
     * Safely log to output, checking if API is available
     */
    private void safeLogToOutput(String message) {
        try {
            if (api != null && api.logging() != null) {
                api.logging().logToOutput(message);
            }
        } catch (Exception ignored) {
            // Silently ignore if logging fails
        }
    }

    /**
     * Safely log to error, checking if API is available
     */
    private void safeLogToError(String message) {
        try {
            if (api != null && api.logging() != null) {
                api.logging().logToError(message);
            }
        } catch (Exception ignored) {
            // Silently ignore if logging fails
        }
    }
}
