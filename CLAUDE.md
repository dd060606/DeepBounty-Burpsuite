# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is a Burp Suite Extension project using the Montoya API for the DeepBounty platform.

## Architecture

- **Main Entry Point**: `src/main/java/Extension.java` - implements `BurpExtension` interface
- **Settings Management**: `src/main/java/Settings.java` - manages extension settings and UI panel
- **Scope Management**: `src/main/java/Scope.java` - handles periodic scope synchronization with DeepBounty server
- **Traffic Handler**: `src/main/java/Handler.java` - intercepts and forwards HTTP traffic to DeepBounty server
- **JSON Models**: `src/main/java/json/JSONBody.java` - JSON request/response models with Gson
- **Build System**: Gradle with Kotlin DSL, Java 21 compatibility
- **Dependencies**:
    - Montoya API 2025.10 (compile-only)
    - Gson 2.13.2 (implementation - included in JAR)

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

This extension provides two main features:

### 1. Scope Synchronization

Periodically checks a DeepBounty server for scope updates (every 10 seconds) and automatically adds
subdomains to Burp's scope when a new version is detected.

### 2. Traffic Interception

Intercepts all HTTP traffic, filters by MIME type, and asynchronously forwards relevant requests/responses
to the DeepBounty server for analysis.

### Main Components

1. **Extension.java**: Minimal entry point that orchestrates initialization
2. **Settings.java**: Manages server URL and API key configuration with UI panel
3. **Scope.java**: Handles HTTP communication, JSON parsing, and Burp scope updates
4. **Handler.java**: HTTP handler for traffic interception and forwarding
5. **JSONBody.java**: Type-safe JSON models for API communication

### Server Communication

#### Scope Endpoints

- `GET /scope/version` - Returns `{"version": <number>}`
- `GET /scope` - Returns `{"version": <number>, "subdomains": ["domain1", "domain2", ...]}`

#### Ingestion Endpoint

- `POST /ingest/burp` - Receives traffic data with full request/response details

All endpoints use Bearer token authentication via API key.

