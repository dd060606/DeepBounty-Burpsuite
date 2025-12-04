import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class Extension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("DeepBounty");

        // Initialize settings
        Settings settings = new Settings(api);

        // Initialize scope manager
        Scope scope = new Scope(api, settings);

        // Start periodic scope version check (every 10 seconds)
        scope.startScopeVersionCheck();

        // Initialize and register HTTP handler for traffic interception
        Handler handler = new Handler(api, settings, scope);
        api.http().registerHttpHandler(handler);

        api.logging().logToOutput("DeepBounty extension loaded successfully");
    }
}

