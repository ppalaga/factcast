/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.applier.EventApplier;
import org.factcast.factus.applier.EventApplierFactory;
import org.factcast.factus.batch.DefaultPublishBatch;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.EventSerializer;
import org.factcast.factus.snapshot.*;
import org.jetbrains.annotations.NotNull;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Single entry point to the factus API.
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultFactus implements Factus {
    final FactCast fc;

    final EventApplierFactory ehFactory;

    final EventSerializer serializer;

    final AggregateSnapshotRepository aggregateSnapshotRepository;

    final ProjectionSnapshotRepository projectionSnapshotRepository;

    final SnapshotFactory snapFactory;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Set<AutoCloseable> managedObjects = new HashSet<>();

    @Override
    public PublishBatch batch() {
        return new DefaultPublishBatch(fc, serializer);
    }

    @Override
    public <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn) {

        assertUnclosed();

        Fact factToPublish = e.toFact(serializer);
        fc.publish(factToPublish);
        return resultFn.apply(factToPublish);
    }

    private void assertUnclosed() {
        if (closed.get()) {
            throw new IllegalStateException("Already closed.");
        }
    }

    @Override
    public void publish(@NonNull List<EventPojo> e) {
        publish(e, f -> null);
    }

    @Override
    public <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn) {

        assertUnclosed();

        List<Fact> facts = StreamSupport.stream(e.spliterator(), false)
                .map(p -> p.toFact(serializer))
                .collect(Collectors.toList());
        fc.publish(facts);
        return resultFn.apply(facts);
    }

    @Override
    public <P extends ManagedProjection> void update(
            @NonNull P managedProjection,
            @NonNull Duration maxWaitTime) throws TimeoutException {

        assertUnclosed();

        log.trace("updating local projection {}", managedProjection.getClass());
        managedProjection.withLock(() -> {
            catchupProjection(managedProjection, managedProjection.state(), maxWaitTime);
        });
    }

    @Override
    public <P extends SubscribedProjection> void subscribe(@NonNull P subscribedProjection) {

        assertUnclosed();

        CompletableFuture.runAsync(() -> {
            // TODO how to exit this loop?
            Duration INTERVAL = Duration.ofMinutes(5); // TODO needed?
            while (!closed.get()) {
                if (subscribedProjection.aquireWriteToken(INTERVAL) != null) {
                    managedObjects.add(doSubscribe(subscribedProjection));
                }
            }
        });
    }

    @SneakyThrows
    private <P extends SubscribedProjection> AutoCloseable doSubscribe(P subscribedProjection) {
        EventApplier<P> handler = ehFactory.create(subscribedProjection);
        FactObserver fo = new FactObserver() {
            // TODO what about error control?
            @Override
            public void onNext(@NonNull Fact element) {
                handler.apply(element);
                subscribedProjection.state(element.id());
            }
        };

        return fc.subscribe(
                SubscriptionRequest
                        .catchup(handler.createFactSpecs())
                        .fromNullable(subscribedProjection.state()), fo)
                .awaitComplete(FOREVER.toMillis());

    }

    @Override
    @SneakyThrows
    public <P extends SnapshotProjection> P fetch(Class<P> projectionClass) {
        assertUnclosed();

        // TODO ugly, fix hierarchy?
        if (Aggregate.class.isAssignableFrom(projectionClass)) {
            throw new IllegalArgumentException(
                    "Method confusion: UUID aggregateId is missing as a second parameter for aggregates");
        }

        val ser = snapFactory.retrieveSerializer(projectionClass);

        Optional<ProjectionSnapshot> latest = projectionSnapshotRepository.findLatest(
                projectionClass);

        P projection;
        if (latest.isPresent()) {
            ProjectionSnapshot snap = latest.get();

            projection = ser.deserialize(projectionClass, snap.bytes());
        } else {
            log.trace("Creating initial projection version for {}", projectionClass);
            projection = initialProjection(projectionClass);
        }

        // catchup
        UUID factUuid = null;
        factUuid = catchupProjection(projection, latest.map(ProjectionSnapshot::factId)
                .orElse(null), FOREVER);
        if (factUuid != null) {
            ProjectionSnapshot currentSnap = new ProjectionSnapshot(projectionClass,
                    factUuid, ser.serialize(projection));
            // TODO concurrency control
            projectionSnapshotRepository.putBlocking(currentSnap);
        }
        return projection;
    }

    @Override
    @SneakyThrows
    public <A extends Aggregate> Optional<A> fetch(Class<A> aggregateClass, UUID aggregateId) {
        assertUnclosed();

        val ser = snapFactory.retrieveSerializer(aggregateClass);

        Optional<AggregateSnapshot<A>> latest = aggregateSnapshotRepository.findLatest(
                aggregateClass, aggregateId);
        Optional<A> optionalA = latest
                .map(as -> ser.deserialize(aggregateClass, as.serializedAggregate()));
        A aggregate = optionalA
                .orElseGet(() -> (A) initial(aggregateClass, aggregateId));

        UUID factUuid = catchupProjection(aggregate, latest.map(AggregateSnapshot::factId)
                .orElse(null), FOREVER);
        if (factUuid == null) {
            // nothing new

            if (!latest.isPresent()) {
                // nothing before
                return Optional.empty();
            } else {
                // just return what we got
                return Optional.of(aggregate);
            }
        } else {
            AggregateSnapshot<A> currentSnap = new AggregateSnapshot<A>(aggregateClass,
                    factUuid, ser.serialize(aggregate));
            // TODO concurrency control
            aggregateSnapshotRepository.putBlocking(aggregateId, currentSnap);
            return Optional.of(aggregate);
        }
    }

    @SneakyThrows
    private <P extends Projection> UUID catchupProjection(
            @NonNull P projection, UUID stateOrNull,
            Duration maxWait) {
        EventApplier<P> handler = ehFactory.create(projection);
        AtomicReference<UUID> factId = new AtomicReference<>();
        FactObserver fo = new FactObserver() {
            // TODO what about error control?
            @Override
            public void onNext(@NonNull Fact element) {
                handler.apply(element);
                factId.set(element.id());
            }
        };

        Subscription s = fc.subscribe(
                SubscriptionRequest
                        .catchup(handler.createFactSpecs())
                        .fromNullable(stateOrNull), fo)
                .awaitComplete(maxWait.toMillis());
        return factId.get();
    }

    @VisibleForTesting
    protected List<Field> getAllFields(Class clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }

        val result = new LinkedList<Field>();
        result.addAll(Arrays.asList(clazz.getDeclaredFields()));
        result.addAll(getAllFields(clazz.getSuperclass()));
        return result;
    }

    @VisibleForTesting
    @SneakyThrows
    protected <A extends Aggregate> A initial(Class<A> aggregateClass, UUID aggregateId) {
        log.trace("Creating initial aggregate version for {} with id {}", aggregateClass
                .getSimpleName(), aggregateId);
        Constructor<A> con = aggregateClass.getDeclaredConstructor();
        con.setAccessible(true);
        A a = con.newInstance();

        Optional<Field> first = getAllFields(aggregateClass).stream()
                .filter(f -> "id".equals(f.getName()))
                .findFirst();
        if (first.isPresent()) {
            Field field = first.get();
            field.setAccessible(true);
            field.set(a, aggregateId);
        } else {
            throw new IllegalArgumentException("Aggregate " + aggregateClass
                    + " needs a field named 'id'");
        }

        return a;
    }

    @NotNull
    @SneakyThrows
    // TODO extract?
    private <P extends SnapshotProjection> P initialProjection(Class<P> projectionClass) {
        Constructor<P> con = projectionClass.getDeclaredConstructor();
        con.setAccessible(true);
        return con.newInstance();
    }

    @Override
    public void close() throws IOException {
        if (this.closed.getAndSet(true)) {
            log.warn("close is being called more than once!?");
        } else {
            ArrayList<AutoCloseable> closeables = new ArrayList<>(managedObjects);
            for (AutoCloseable c : closeables) {
                try {
                    c.close();
                } catch (Exception e) {
                    // needs to be swallowed
                    log.warn("While closing {} of type {}:", c, c.getClass().getCanonicalName(), e);
                }
            }
        }
    }

    @Override
    public Fact toFact(@NonNull EventPojo e) {
        return e.toFact(serializer);
    }

    @Override
    public Locked lock(ManagedProjection projectionClass) {
        return null;
    }

    @Override
    public <A extends Aggregate> Locked lock(Class<A> aggregateClass, UUID id) {
        Aggregate fresh = fetch(aggregateClass, id).orElse(initialProjection(aggregateClass));
        EventApplier<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
        List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
        return new Locked(fc, this, specs);
    }

    @Override
    public <P extends SnapshotProjection> Locked lock(@NonNull Class<P> projectionClass) {
        SnapshotProjection fresh = fetch(projectionClass);
        EventApplier<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
        List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
        return new Locked(fc, this, specs);
    }

    @Override
    public Locked lockAggregateById(UUID first, UUID... others) {
        val specs = new LinkedList<FactSpec>();
        specs.add(FactSpec.anyNs().aggId(first));
        if (others != null && others.length > 0) {
            Arrays.stream(others).map(i -> FactSpec.anyNs().aggId(first)).forEach(specs::add);
        }

        return new Locked(fc, this, specs);
    }
}