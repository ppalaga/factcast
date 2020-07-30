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
package org.factcast.highlevel.snapshot;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.snap.SnapshotId;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.highlevel.projection.Aggregate;
import org.jetbrains.annotations.NotNull;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class AggregateSnapshotRepositoryImpl implements AggregateSnapshotRepository {

    private final SnapshotRepository snap;

    private static final String KEY_DELIMITER = ":";

    private static final String KEY_PREFIX = "AggregateSnapshotRepository" + KEY_DELIMITER;

    @Override
    public <A extends Aggregate> Optional<AggregateSnapshot<A>> findLatest(@NonNull Class<A> type,
            @NonNull UUID aggregateId) {
        SnapshotId snapshotId = new SnapshotId(createKeyForType(type), aggregateId);
        return snap.getSnapshot(snapshotId)
                .map(s -> new AggregateSnapshot<A>(type, s.lastFact(), s.bytes()));

    }

    @NotNull
    @VisibleForTesting
    protected <A extends Aggregate> String createKeyForType(@NonNull Class<A> type) {
        return KEY_PREFIX + type.getCanonicalName() + KEY_DELIMITER + getSerialVersionUid(type);
    }

    @VisibleForTesting
    protected <A extends Aggregate> Long getSerialVersionUid(Class<A> type) {
        // TODO add loadingcache
        try {
            Field field = type.getDeclaredField("serialVersionUID");
            field.setAccessible(true);
            return field.getLong(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return 0L;
        }
    }

    @Override
    public <A extends Aggregate> void putBlocking(@NonNull UUID aggregateId,
            @NonNull AggregateSnapshot<A> snapshot) {
        val snapId = new SnapshotId(createKeyForType(snapshot.type()), aggregateId);
        snap.setSnapshot(snapId, snapshot.factId(), snapshot.serializedAggregate());
    }

    @Override
    public <A extends Aggregate> CompletableFuture<Void> put(@NonNull UUID aggregateId,
            @NonNull AggregateSnapshot<A> snapshot) {
        return CompletableFuture.runAsync(() -> {
            putBlocking(aggregateId, snapshot);
        });
    }
}
