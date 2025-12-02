package json;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Container class for JSON body objects used by the DeepBounty API
 */
public final class JSONBody {

    // Private constructor to prevent instantiation
    private JSONBody() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Response object for scope version endpoint
     * JSON format: {"version": 123}
     */
    public static class ScopeVersionResponse {
        @SuppressWarnings("unused") // Assigned by Gson via reflection
        @SerializedName("version")
        private int version;

        public int getVersion() {
            return version;
        }
    }

    /**
     * Response object for full scope endpoint
     * JSON format: {"version": 123, "subdomains": ["sub1.example.com", "sub2.example.com"]}
     */
    public static class ScopeResponse {
        @SuppressWarnings("unused") // Assigned by Gson via reflection
        @SerializedName("version")
        private int version;

        @SuppressWarnings("unused") // Assigned by Gson via reflection
        @SerializedName("subdomains")
        private List<String> subdomains;

        public int getVersion() {
            return version;
        }

        public List<String> getSubdomains() {
            return subdomains != null ? subdomains : Collections.emptyList();
        }
    }

    /**
     * Request object for sending traffic data to the server
     *
     * @param url             The request URL
     * @param method          The HTTP method
     * @param statusCode      The HTTP status code
     * @param requestHeaders  The request headers
     * @param responseHeaders The response headers
     * @param requestBody     The request body
     * @param responseBody    The response body
     * @param mimeType        The MIME type
     */
    public record Traffic(
            @SerializedName("url") String url,
            @SerializedName("method") String method,
            @SerializedName("statusCode") int statusCode,
            @SerializedName("requestHeaders") Map<String, String> requestHeaders,
            @SerializedName("responseHeaders") Map<String, String> responseHeaders,
            @SerializedName("requestBody") String requestBody,
            @SerializedName("responseBody") String responseBody,
            @SerializedName("mimeType") String mimeType
    ) {
        /**
         * Compact constructor with null-safe defaults
         */
        public Traffic {
            requestHeaders = requestHeaders != null ? requestHeaders : Collections.emptyMap();
            responseHeaders = responseHeaders != null ? responseHeaders : Collections.emptyMap();
            requestBody = requestBody != null ? requestBody : "";
            responseBody = responseBody != null ? responseBody : "";
        }
    }
}


