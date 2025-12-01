# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is a Burp Suite Extension project using the Montoya API for the DeepBounty platform.

## Architecture

- **Main Entry Point**: `src/main/java/Extension.java` - implements `BurpExtension` interface
- **Settings Management**: `src/main/java/Settings.java` - manages extension settings and UI panel
- **Scope Management**: `src/main/java/Scope.java` - handles periodic scope synchronization with DeepBounty server
- **Build System**: Gradle with Kotlin DSL, Java 21 compatibility
- **Dependencies**: Montoya API 2025.10 (compile-only), no runtime dependencies

## Key Development Commands

```bash
./gradlew build    # Build and test the extension
./gradlew jar      # Create the extension JAR file
./gradlew clean    # Clean build artifacts
```

The built JAR file will be in `build/libs/` and can be loaded directly into Burp Suite.

## Extension Loading in Burp

1. Build the JAR using `./gradlew jar`
2. In Burp: Extensions > Installed > Add > Select the JAR file
3. For quick reloading during development: Ctrl/⌘ + click the Loaded checkbox

## Documentation Structure

- See @docs/bapp-store-requirements.md for BApp Store submission requirements
- See @docs/montoya-api-examples.md for code patterns and extension structure
- See @docs/development-best-practices.md for development guidelines
- See @docs/resources.md for external documentation and links

## Current State

This extension periodically checks a DeepBounty server for scope updates (every 10 seconds) and automatically adds
subdomains to Burp's scope when a new version is detected.

### Main Components

1. **Extension.java**: Minimal entry point that orchestrates initialization
2. **Settings.java**: Manages server URL and API key configuration
3. **Scope.java**: Handles HTTP communication, JSON parsing, and Burp scope updates

### Server Communication

- `GET /scope/version` - Returns `{"version": <number>}`
- `GET /scope` - Returns `{"version": <number>, "subdomains": ["domain1", "domain2", ...]}`
- Bearer token authentication via API key

