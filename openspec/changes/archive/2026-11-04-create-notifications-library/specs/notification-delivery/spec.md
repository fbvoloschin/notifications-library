# Notification Delivery Specification

## Purpose

Define asynchronous execution, retry rules, and events for the same send flow as the synchronous API.

## ADDED Requirements

### Requirement: Async sends share the sync contract

`sendAsync(SendRequest)` MUST submit the sync flow and return a `CompletableFuture<ProviderAcceptance>` without waiting. The future MUST complete with the sync result or typed failure. For async and batch, the host executor MUST dispatch off-thread without waiting and throw `RejectedExecutionException` on rejection (no caller-runs or silent discard). Missing executor or rejection MUST yield a typed execution error with zero attempts. The client MUST NOT run sends inline or close the executor.

#### Scenario: An accepted task has not run yet

- **GIVEN** a controlled executor that queues tasks
- **WHEN** `sendAsync` is called
- **THEN** an incomplete future returns before any provider call
- **AND** running the task completes it with the synchronous outcome

#### Scenario: Executor rejection does not escape as a submission exception

- **GIVEN** an executor that rejects a task
- **WHEN** `sendAsync` is called
- **THEN** it returns an exceptionally completed future with a typed cause and no provider call

#### Scenario: Async failures retain their typed cause

- **GIVEN** a send that fails with a typed `SendException`
- **WHEN** the completed future is observed through `join()` or `get()`
- **THEN** `CompletionException` or `ExecutionException`, respectively, exposes that `SendException` as its cause

### Requirement: Batch order and isolation

`sendAll(List<SendRequest>)` MUST return `List<CompletableFuture<ProviderAcceptance>>`, with one future per input in input order, regardless of completion order. Each task MUST preserve its result or typed failure without cancelling or skipping other tasks. Empty input MUST return an empty list without submitting tasks or emitting send events.

#### Scenario: Mixed batch completes out of order

- **GIVEN** accepted, invalid, provider-rejected, and executor-rejected items
- **WHEN** the queued items are executed out of order
- **THEN** the futures stay in input order and each item retains its independent outcome

#### Scenario: An empty batch has no side effects

- **GIVEN** an empty list, including when no executor is configured
- **WHEN** `sendAll` is called
- **THEN** it returns an empty list without executor submissions, provider calls, or events

### Requirement: Cancelling a future does not cancel the send

Cancellation of a returned future MUST affect only observation of that result, not interrupt or cancel its underlying send or other sends. The send MUST still publish exactly one terminal event for its actual outcome. Cancellation MUST NOT create a synthetic `FAILED` event or be represented as provider cancellation.

#### Scenario: A cancelled future and the actual send outcome differ

- **GIVEN** a send paused inside a controlled provider and an independent batch item
- **WHEN** its future is cancelled with `cancel(true)` and the provider later accepts
- **THEN** that future remains cancelled, the independent item is unaffected, and the send emits exactly one `ACCEPTED` and no cancellation-generated `FAILED`

### Requirement: Retry is bounded and opt-in

The client MUST retry only adapter-classified transient failures and MUST retry the same provider. `maxAttempts` MUST be positive, include the first call, and default to one. Attempts MUST count actual invocations. Tests MUST control waiting without real sleeps.

#### Scenario: A transient failure recovers

- **GIVEN** a transient failure with a 10-second provider delay, a 4-second backoff cap, and a two-attempt limit
- **WHEN** the next attempt succeeds
- **THEN** the requested wait is 10 seconds and acceptance reports two calls

#### Scenario: The retry limit or failure category prevents more attempts

- **GIVEN** no attempts remain, or the failure is not classified as transient
- **WHEN** the attempt ends
- **THEN** the client fails without another call or provider fallback

### Requirement: Backoff grows exponentially and respects provider delays

For retry number `n` starting at one, backoff MUST be `min(maxBackoff, initialDelay * 2^(n-1))`, saturating safely at the cap. The requested wait MUST be `max(backoff, providerDelay)`, with zero for an absent provider delay. `initialDelay` MUST be positive and `maxBackoff >= initialDelay`; invalid values MUST fail construction. Defaults MUST be 1 second and 30 seconds, respectively.

#### Scenario: The exponential backoff reaches its cap

- **GIVEN** four transient responses then acceptance, five allowed attempts, a 1-second initial delay, a 4-second cap, and no provider delays
- **WHEN** the send runs with a recording fake wait
- **THEN** requested waits are 1, 2, 4, and 4 seconds, without real sleeps

#### Scenario: Invalid backoff configuration is rejected locally

- **GIVEN** a nonpositive initial delay or a cap below the initial delay
- **WHEN** the client is built
- **THEN** construction fails with a safe configuration error before any provider call

### Requirement: Interruption stops backoff

An interruption during backoff MUST restore the executing thread's interrupt flag and finish with a typed interruption error and the actual attempt count. No retry event or attempt may be added for the interrupted wait.

#### Scenario: Interrupted waiting prevents another call

- **GIVEN** one transient call and pending backoff
- **WHEN** the wait is interrupted
- **THEN** the send fails as interrupted with one attempt, one terminal `FAILED`, and no `RETRYING` for the interrupted wait

### Requirement: Events describe the send outcome

Each send MUST publish exactly one terminal `ACCEPTED` or `FAILED` with a per-client correlation ID, timestamp, actual attempts, and selected provider if any. `RETRYING` MUST occur after backoff and immediately before the next attempt, reporting completed attempts. `ACCEPTED` means provider acceptance, not delivery. Timestamps MUST be controllable in tests.

#### Scenario: A successful retry emits an ordered sequence

- **GIVEN** one transient response, a second successful call, and a fixed clock
- **WHEN** the send completes
- **THEN** it emits `RETRYING` with one completed attempt and `ACCEPTED` with two, both with the same correlation ID

#### Scenario: Failure before selecting a provider

- **GIVEN** an invalid request, unresolved route, or rejected submission
- **WHEN** the send fails before provider selection
- **THEN** it emits one `FAILED` with zero attempts and no provider

### Requirement: Listener failures are isolated

For each event, listeners MUST run in registration order. A listener's runtime exception MUST NOT alter the send result, cause additional retries, or prevent other listeners from receiving the event. Events from different sends MAY interleave.

#### Scenario: A throwing listener does not block later listeners

- **GIVEN** a first listener that throws and a later one that records events
- **WHEN** a send emits an event
- **THEN** the recorder receives it and the send outcome remains unchanged
