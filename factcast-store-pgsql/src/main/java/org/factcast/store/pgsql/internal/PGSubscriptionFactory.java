package org.factcast.store.pgsql.internal;

import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;

/**
 * Creates Subscription
 * 
 * @author usr
 *
 */
// TODO integrate with PGQuery
@RequiredArgsConstructor
class PGSubscriptionFactory {
	private final JdbcTemplate jdbcTemplate;

	private final EventBus eventBus;
	private final PGFactIdToSerMapper idToSerialMapper;

	public Subscription subscribe(SubscriptionRequestTO req, FactStoreObserver observer) {
		PGQuery q = new PGQuery(jdbcTemplate, eventBus, idToSerialMapper);
		return q.run(req, observer);
	}

}
