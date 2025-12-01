import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

/**
 * Manages all settings for the DeepBounty extension
 */
public class Settings {

    private final SettingsPanelWithData settingsPanel;

    // Settings keys
    private static final String SERVER_URL_KEY = "DeepBounty Server URL";
    private static final String BURPSUITE_KEY = "Burpsuite Key";

    // Default values
    private static final String DEFAULT_SERVER_URL = "http://localhost:3000";
    private static final String DEFAULT_BURPSUITE_KEY = "";

    public Settings(MontoyaApi api) {
        // Create settings panel
        settingsPanel = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle("DeepBounty Settings")
                .withDescription("Configure DeepBounty extension to sync scope from your server.")
                .withSettings(
                        SettingsPanelSetting.stringSetting(SERVER_URL_KEY, DEFAULT_SERVER_URL),
                        SettingsPanelSetting.stringSetting(BURPSUITE_KEY, DEFAULT_BURPSUITE_KEY)
                )
                .withKeywords("DeepBounty", "Settings")
                .build();

        // Register the settings panel
        api.userInterface().registerSettingsPanel(settingsPanel);
    }

    /**
     * Get the configured server URL
     */
    public String getServerUrl() {
        return settingsPanel.getString(SERVER_URL_KEY);
    }

    /**
     * Get the configured Burpsuite key
     */
    public String getBurpsuiteKey() {
        return settingsPanel.getString(BURPSUITE_KEY);
    }
}

