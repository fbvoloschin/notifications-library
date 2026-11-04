package dev.notifications;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AsyncSendTest {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void sendsOnlyWhenTheHostRunsTheQueuedTask() throws InterruptedException {
        var worker = new AtomicReference<Thread>();
        var executor = new ControlledExecutor();
        var client = client(executor, email -> {
            worker.set(Thread.currentThread());
            return Optional.of(email.subject());
        });

        var result = client.sendAsync(request("queued"));

        assertFalse(result.isDone());
        assertNull(worker.get());
        executor.run(0);
        assertNotEquals(Thread.currentThread(), worker.get());
        assertEquals(Optional.of("queued"), result.join().providerMessageId());
    }

    @Test
    void missingAndRejectingExecutorsReturnTypedAdmissionFailuresWithNoProvider() {
        NotificationProvider<EmailNotification> forbidden = email -> fail("No inline or provider invocation");
        var missing = client(null, forbidden);
        var rejecting = client(command -> { throw new RejectedExecutionException("private executor details"); }, forbidden);

        var absentFailure = failure(missing.sendAsync(request("no executor")), SendException.Reason.EXECUTION_REJECTED);
        var rejectFailure = failure(rejecting.sendAsync(request("rejected")), SendException.Reason.EXECUTION_REJECTED);

        for (var failure : List.of(absentFailure, rejectFailure)) {
            assertEquals("email", failure.channel());
            assertEquals(0, failure.attempts());
            assertEquals(Optional.empty(), failure.provider());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private executor details"));
        }
        assertNotEquals(absentFailure.correlationId(), rejectFailure.correlationId());
    }

    @Test
    void joinAndGetExposeTheSameTypedFailureFromTheSyncFlow() throws InterruptedException {
        var executor = new ControlledExecutor();
        var client = NotificationClient.builder().executor(executor)
                .registerChannel(EmailNotification.CHANNEL, email -> {
                    throw new IllegalArgumentException("private validator details");
                })
                .registerProvider(EmailNotification.CHANNEL, "provider", email -> fail("Validation must run first"), true)
                .build();
        var result = client.sendAsync(request("unexpected"));
        executor.run(0);

        var failure = failure(result, SendException.Reason.UNEXPECTED_VALIDATION);

        assertSame(failure, assertThrows(ExecutionException.class, result::get).getCause());
        assertNull(failure.getCause());
        assertEquals(0, failure.attempts());
        assertEquals(Optional.empty(), failure.provider());
    }

    @Test
    void mixedBatchRetainsInputOrderWhenTheHostRunsTasksOutOfOrder() throws InterruptedException {
        var invocations = new ArrayList<String>();
        var executor = new ControlledExecutor(submission -> submission == 4);
        var client = client(executor, email -> {
            invocations.add(email.subject());
            if (email.subject().equals("provider rejection")) {
                throw new ProviderFailure(ProviderFailure.Kind.REJECTED);
            }
            return Optional.of(email.subject());
        });

        var results = client.sendAll(List.of(request("accepted"), request(" "),
                request("provider rejection"), request("admission rejection")));

        assertEquals(4, results.size());
        failure(results.get(3), SendException.Reason.EXECUTION_REJECTED);
        executor.run(2);
        failure(results.get(2), SendException.Reason.PROVIDER_REJECTED);
        assertFalse(results.get(0).isDone());
        executor.run(1);
        failure(results.get(1), SendException.Reason.INVALID_NOTIFICATION);
        executor.run(0);
        assertEquals(Optional.of("accepted"), results.get(0).join().providerMessageId());
        assertEquals(List.of("provider rejection", "accepted"), invocations);
    }

    @Test
    void emptyBatchDoesNotSubmitWithOrWithoutAnExecutor() {
        NotificationProvider<EmailNotification> forbidden = email -> fail("No sends in an empty batch");
        Executor noSubmissions = runnable -> fail("No submissions in an empty batch");

        assertEquals(List.of(), client(noSubmissions, forbidden).sendAll(List.of()));
        assertEquals(List.of(), client(null, forbidden).sendAll(List.of()));
    }

    @Test
    void cancellingAPausedSendDoesNotInterruptWorkOrCloseTheExecutor() throws InterruptedException {
        var started = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        var executor = new ControlledExecutor();
        var client = client(executor, email -> {
            if (email.subject().equals("paused")) {
                started.countDown();
                await(resume);
                completed.set(true);
            }
            return Optional.of(email.subject());
        });
        var results = client.sendAll(List.of(request("paused"), request("independent")));
        var paused = executor.start(0);
        try {
            await(started);
            assertTrue(results.get(0).cancel(true));
            executor.run(0);
            assertEquals(Optional.of("independent"), results.get(1).join().providerMessageId());
        } finally {
            resume.countDown();
            assertTrue(paused.join(TEST_TIMEOUT));
        }

        assertTrue(completed.get());
        assertThrows(CancellationException.class, results.get(0)::join);
        assertFalse(executor.closed);
    }

    @Test
    void cancellationBeforeDispatchStillRunsTheSend() throws InterruptedException {
        var seen = new AtomicReference<String>();
        var executor = new ControlledExecutor();
        var client = client(executor, email -> {
            seen.set(email.subject());
            return Optional.of(email.subject());
        });
        var result = client.sendAsync(request("queued cancellation"));

        assertTrue(result.cancel(false));
        executor.run(0);

        assertEquals("queued cancellation", seen.get());
        assertThrows(CancellationException.class, result::join);
    }

    @Test
    void missingRequestsAndBrokenCustomChannelsFailIndependently() throws InterruptedException {
        var executor = new ControlledExecutor();
        var client = client(executor, email -> Optional.of(email.subject()));
        var broken = new SendRequest(() -> { throw new IllegalStateException("private channel details"); });
        var results = client.sendAll(Arrays.asList(null, broken, request("valid")));
        for (int index = 0; index < results.size(); index++) {
            executor.run(0);
        }

        failure(results.get(0), SendException.Reason.INVALID_NOTIFICATION);
        var invalidChannel = failure(results.get(1), SendException.Reason.UNKNOWN_CHANNEL);
        assertNull(invalidChannel.getCause());
        assertEquals(Optional.of("valid"), results.get(2).join().providerMessageId());
    }

    private static NotificationClient client(Executor executor, NotificationProvider<EmailNotification> provider) {
        var builder = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "provider", provider, true);
        return (executor == null ? builder : builder.executor(executor)).build();
    }

    private static SendRequest request(String subject) {
        return new SendRequest(new EmailNotification("dev@example.com", subject, "Content"));
    }

    private static SendException failure(CompletableFuture<ProviderAcceptance> result, SendException.Reason reason) {
        assertTrue(result.isCompletedExceptionally());
        var failure = assertInstanceOf(SendException.class, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(reason, failure.reason());
        return failure;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("The underlying send should not be interrupted", interrupted);
        }
    }

    private static final class ControlledExecutor implements Executor, AutoCloseable {
        private final List<Runnable> pending = new ArrayList<>();
        private final IntPredicate reject;
        private int submissions;
        private boolean closed;

        ControlledExecutor() {
            this(submission -> false);
        }

        ControlledExecutor(IntPredicate reject) {
            this.reject = reject;
        }

        @Override
        public void execute(Runnable command) {
            if (reject.test(++submissions)) {
                throw new RejectedExecutionException();
            }
            pending.add(command);
        }

        Thread start(int index) {
            return Thread.ofVirtual().start(pending.remove(index));
        }

        void run(int index) throws InterruptedException {
            assertTrue(start(index).join(TEST_TIMEOUT));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
