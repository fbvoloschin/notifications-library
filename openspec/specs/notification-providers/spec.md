# Notification Providers Specification

## Purpose

Define offline provider contracts, notification validation, failure classification, and safe diagnostics.

## Requirements

### Requirement: Notification validation is channel-specific

Validation MUST aggregate violations before any provider invocation. Email needs a syntactically valid recipient and nonblank subject and body. SMS needs a `+` followed by 8–15 digits starting with a nonzero digit and a nonblank body of at most 1600 Unicode code points. Push needs a nonblank token, title and body; optional data contains only non-null string keys and values.

#### Scenario: An invalid notification is not submitted

- **GIVEN** a notification with multiple invalid fields
- **WHEN** it is sent through a registered channel
- **THEN** the validation error identifies every violation without a provider call

### Requirement: The selected provider owns sender configuration

SendGrid and Mailgun email senders and Twilio's SMS sender MUST come from the selected provider's explicit configuration and be validated locally at client construction. The same email can be sent through providers with different senders. A real account's authorization is a deployment prerequisite, not part of the offline validation.

#### Scenario: Provider switching does not modify the notification

- **GIVEN** SendGrid and Mailgun are configured with different senders
- **WHEN** the same email notification is sent through each
- **THEN** each simulated request uses its own configured sender

### Requirement: Offline request contracts

Adapters MUST model the provider's endpoint, authorization scheme and structured payload in memory. They MUST NOT use network calls or provider SDKs.

#### Scenario: Contract fixtures match the four APIs

- **GIVEN** valid notifications and explicit simulated credentials
- **WHEN** each adapter prepares a request
- **THEN** SendGrid models `POST /v3/mail/send` with Bearer auth and JSON `personalizations[].to[]`, `from`, `subject`, and `content[]`
- **AND** Mailgun models `POST /v3/{domain}/messages` with Basic `api:<key>` and multipart `from`, `to`, `subject`, and `text`
- **AND** Twilio models `POST /2010-04-01/Accounts/{AccountSid}/Messages.json` with Basic auth and form-encoded `To`, `From`, and `Body`
- **AND** FCM models `POST /v1/projects/{projectId}/messages:send` with OAuth Bearer and JSON `message.token`, `message.notification`, and optional `message.data`

### Requirement: Success follows the adapter contract

An adapter MUST accept only its modeled success status and required fields. Unexpected 2xx statuses or malformed success bodies MUST produce a distinct non-retryable unexpected-response error. Repeated simulations with identical inputs and fixtures MUST produce equivalent provider outcomes without side effects.

#### Scenario: Valid success fixtures return an acceptance

- **GIVEN** valid requests
- **WHEN** SendGrid returns `202` with empty body, Mailgun `200` with `id` and `message`, Twilio `201` with `sid` and `status`, or FCM `200` with `name`
- **THEN** the adapter returns acceptance and the supplied ID, using SendGrid's `X-Message-ID` only if present

#### Scenario: A successful status is insufficient

- **GIVEN** a Mailgun `200` fixture with no `id`, or an unexpected `204` fixture
- **WHEN** the response is interpreted
- **THEN** it fails as unexpected response, without retrying or inventing an identifier

### Requirement: Failures are classified by the adapter

The adapter MUST distinguish retryable transient failure, rejection, unknown acceptance, and unexpected failure. The simulation MUST cover Twilio `429`, FCM `503`, and a known pre-acceptance connection failure as transient; it MUST carry a supplied retry delay. Ambiguous timeouts and ambiguous server failures are unknown acceptance, not generally retryable. Undocumented failures MUST NOT be retried by default.

#### Scenario: Different failures produce different classifications

- **GIVEN** fixtures for Twilio `429`, FCM `503` with a retry delay, an auth rejection, an ambiguous timeout, and an unexpected exception
- **WHEN** the respective adapters interpret them
- **THEN** the first two are transient, the delay is preserved, and the others remain distinct non-retryable failures

### Requirement: Credential non-disclosure

The library MUST NOT expose credentials, derived authorization values, raw provider bodies, or untrusted cause messages in logs, errors, results, events, or string representations. Safe diagnostics MAY include the channel, provider, attempt count, and normalized failure reason.

#### Scenario: A rejection echoes a secret in upstream diagnostics

- **GIVEN** fake credentials and a fixture containing them in a raw body or exception
- **WHEN** the send fails
- **THEN** public diagnostics and exception causes contain neither the credentials nor derived authorization values
