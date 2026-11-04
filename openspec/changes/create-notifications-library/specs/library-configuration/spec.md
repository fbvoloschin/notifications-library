# Library Configuration Specification

## Purpose

Define build, configuration, documentation, and demonstration requirements for a framework-independent library.

## ADDED Requirements

### Requirement: Java library and framework independence

The artifact MUST target Java 21 and be packaged as a Maven library dependency. It MUST work in plain Java or a framework-hosted app without framework runtime, annotations, dependencies, or bootstrap. Demos MUST NOT turn the library JAR into an executable application.

#### Scenario: Plain Java consumer

- **GIVEN** a Java 21+ Maven application with the library dependency
- **WHEN** it configures and sends a notification
- **THEN** it needs no framework or external provider account

### Requirement: Explicit Java-only configuration

The library MUST obtain registrations, credentials, senders, executor, retry policy, and listeners from Java configuration or documented Java defaults. It MUST NOT load environment variables, system properties, YAML, or properties files implicitly. The host MAY read its own environment and pass values explicitly.

#### Scenario: Explicit host configuration

- **GIVEN** the host has read a credential and supplied it through Java
- **WHEN** a provider is configured
- **THEN** the provider uses that value without reading any external configuration

### Requirement: Eager local configuration validation

Construction MUST validate required values and supported local formats for registered providers only, before sending. It MUST NOT make network or configuration-file calls or claim to verify ownership, authorization, or credential validity remotely.

#### Scenario: Missing local configuration

- **GIVEN** a registered provider has a missing required credential or malformed sender
- **WHEN** the client is built
- **THEN** construction fails with a safe configuration error and no provider invocation

#### Scenario: Unused providers need no configuration

- **GIVEN** only a valid simulated SMS provider is registered
- **WHEN** the client is built without network access
- **THEN** it builds successfully without Email or Push credentials

### Requirement: Consumer documentation and AI disclosure

Code and Javadoc MUST be in English; README and design guidance MUST be in Spanish. The README MUST cover local installation for Maven and Gradle consumers, quick start, provider setup and official references, the public API, extension, and credential handling and rotation. It MUST visibly and honestly describe actual AI tool use, workflow, and decisions retained by the author.

#### Scenario: A reviewer follows the README from a clean checkout

- **GIVEN** a clean checkout and no provider accounts
- **WHEN** a reviewer follows the README
- **THEN** they can install and use the JAR from a standalone Java consumer, run the examples, and read the AI-use note in the README
- **AND** the guide shows how to switch the default Email provider from SendGrid to Mailgun without changing the send call and how to register a custom channel without modifying the library

### Requirement: Meaningful tests and offline demo

Tests MUST exercise observable behavior with simulations or test doubles; constant-only and intentionally failing tests MUST NOT be included. A Docker image MUST run the same standalone Java demo as the documented local example without credentials or runtime network access.

#### Scenario: Run the demo without a network

- **GIVEN** the image is built
- **WHEN** the reviewer runs it with `--network none`
- **THEN** the demo shows simulated results for Email, SMS, and Push and exits successfully
