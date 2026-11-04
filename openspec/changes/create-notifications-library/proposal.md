# Proposal: Create Notifications Library

## Why

Give Java consumers one way to send typed notifications and switch providers through configuration. The submission should demonstrate extensibility, realistic provider contracts, and reliable failure handling without depending on a framework or sending real notifications.

## What Changes

- Build a Java 21+ Maven library with Java-only configuration and simulated SendGrid/Mailgun (Email), Twilio (SMS), and FCM (Push).
- Support local validation, named/default routing, a public extension API, typed errors, and safe diagnostics.
- Ship async sends, isolated batches, bounded retries, and status listeners on top of the same send flow. These are optional in the challenge, but included in this submission.
- Ship runnable consumer examples, a Docker demo, and Spanish README/design guidance with visible AI-use disclosure.
- Exclude templates, a built-in Slack adapter, provider SDKs and networking, durable queues, idempotency, and artifact publication to Maven Central.

## Capabilities

### New Capabilities

- `library-configuration`: library packaging, explicit configuration, documentation and runnable demos.
- `notification-routing`: common sync contract, provider selection and consumer-defined channels.
- `notification-providers`: channel validation, offline provider contracts, classification and credential safety.
- `notification-delivery`: async and batch semantics, retries, interruption and events.

### Modified Capabilities

None.

## Impact

Implement and verify the synchronous library and provider adapters first. Then add async/batches, retry and event behavior, and the demo. No provider accounts or external services are needed at runtime; first-time builds may download build dependencies.

## Acceptance

- `mvn -B clean verify` succeeds on JDK 21 from a clean checkout, with meaningful unit tests and a plain Java consumer example.
- Tests prove provider switching via configuration only, consumer-owned extension, each adapter contract, typed failures and safe diagnostics.
- Deterministic tests prove out-of-order batch completion, failure isolation, retry limits and event sequencing.
- The documented Docker build succeeds and its demo runs with `--network none`. The reviewer can follow the README from a clean clone. Publish the source to GitHub or GitLab and share the repository link for submission.
