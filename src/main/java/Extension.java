import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class Extension implements BurpExtension {

    private Settings settings;
    private Scope scope;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("DeepBounty");
        // Initialize settings
        settings = new Settings(api);

        // Initialize scope manager
        scope = new Scope(api, settings);

        // Start periodic scope version check (every 10 seconds)
        scope.startScopeVersionCheck();

        api.logging().logToOutput("DeepBounty extension loaded successfully");
    }
}

