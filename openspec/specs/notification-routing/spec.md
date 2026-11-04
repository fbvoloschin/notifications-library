# Notification Routing Specification

## Purpose

Define a common send contract, provider selection, and extension without changes to the core.

## Requirements

### Requirement: One synchronous contract for open channels

The client MUST accept typed notifications with an open channel identifier and return a provider acceptance or throw a typed send exception. Acceptance MUST expose the channel, provider, actual attempts, and provider message ID only when supplied. The core MUST NOT branch on the set of built-in channels or depend on concrete provider adapters.

#### Scenario: Built-in providers use the common contract

- **GIVEN** configured Email, SMS, and Push providers
- **WHEN** a valid notification of each type is sent
- **THEN** the same send API returns each selected provider's acceptance

### Requirement: Explicit names and optional defaults

Provider names MUST be unique per channel and case-sensitive. A channel MAY have zero or one default. Omitted selection uses only the default; explicit selection works even without one and never changes it. Duplicate names or a second default MUST be rejected without replacing an existing registration. A built client MUST retain its original routing after builder changes.

#### Scenario: Change providers only through configuration

- **GIVEN** two clients configured identically except for their Email default
- **WHEN** the same consumer code sends the same email without naming a provider
- **THEN** one routes to SendGrid and the other to Mailgun

#### Scenario: Explicit selection without a default

- **GIVEN** a channel has a named provider and no default
- **WHEN** a send explicitly selects it
- **THEN** it is used without installing a default

#### Scenario: Registration failure or builder changes do not alter an existing client

- **GIVEN** a client built with a registered default
- **WHEN** the builder rejects a duplicate or is later changed
- **THEN** the built client retains the original route

#### Scenario: Explicit selection does not replace the default

- **GIVEN** two providers registered for the same channel and one default
- **WHEN** a send explicitly selects the other provider and a later send omits the name
- **THEN** each send uses its respective provider without changing the default

### Requirement: Distinct route failures

The client MUST distinguish unknown channel, unknown provider on a known channel, and absent default. Routing failure MUST invoke no provider; neither a bad explicit selection nor a provider failure may trigger automatic fallback.

#### Scenario: An explicit selection is unknown

- **GIVEN** a known channel has a default
- **WHEN** a valid notification names an unregistered provider
- **THEN** it fails as unknown provider without invoking the default

#### Scenario: An unregistered channel or absent default fails distinctly

- **GIVEN** one unregistered channel and one registered channel without a default
- **WHEN** a valid notification for each is sent without an explicit provider
- **THEN** the first fails as unknown channel and the second as absent default, with no provider calls

### Requirement: Consumer-defined channels and providers

The public registration API MUST support a channel's notification type, validator, and compatible providers. Adding a channel or a provider MUST NOT require edits to the core or to any built-in provider. A mismatch between a notification and its registered channel type MUST fail before provider invocation.

#### Scenario: A plain Java consumer adds a channel and a provider

- **GIVEN** a consumer-defined channel, notification, validator, and provider plus an alternate provider for an existing channel
- **WHEN** the consumer registers and sends through them using the public API
- **THEN** both routes work without changing library code or affecting built-ins

#### Scenario: A custom validator fails unexpectedly

- **GIVEN** a registered validator that throws a `RuntimeException` with an unsafe message
- **WHEN** its notification is sent synchronously or within an async batch
- **THEN** the send fails with a typed `SendException` identifying an unexpected validation failure, not ordinary input violations, with zero attempts and no provider selection
- **AND** one safe `FAILED` event is emitted, the original message and cause are not exposed, and other batch items continue
