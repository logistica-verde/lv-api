package br.com.greenvrp.geocode;

import br.com.greenvrp.api.InvalidRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GeocodeService {

	static final int MIN_QUERY_LENGTH = 3;
	static final int MAX_QUERY_LENGTH = 200;

	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

	private record Cached(List<GeocodeMatch> matches, Instant expiresAt) {
	}

	private final GeocoderClient client;
	private final GeocodeProperties properties;
	private final Clock clock;
	private final Sleeper sleeper;
	private final Map<String, Cached> cache;
	private final Object upstreamLock = new Object();
	private Instant lastUpstreamCall = Instant.MIN;

	@Autowired
	public GeocodeService(GeocoderClient client, GeocodeProperties properties, Clock geocodeClock) {
		this(client, properties, geocodeClock, duration -> Thread.sleep(duration.toMillis()));
	}

	GeocodeService(GeocoderClient client, GeocodeProperties properties, Clock clock, Sleeper sleeper) {
		this.client = client;
		this.properties = properties;
		this.clock = clock;
		this.sleeper = sleeper;
		int capacity = properties.cacheSize();
		this.cache = new LinkedHashMap<>(16, 0.75f, true) {

			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
				return size() > capacity;
			}

		};
	}

	public List<GeocodeMatch> search(String rawQuery) {
		String query = validQuery(rawQuery);
		String key = query.toLowerCase(Locale.ROOT);

		List<GeocodeMatch> cached = fromCache(key);
		if (cached != null) {
			return cached;
		}
		synchronized (upstreamLock) {
			cached = fromCache(key);
			if (cached != null) {
				return cached;
			}
			waitForTurn();
			List<GeocodeMatch> matches;
			try {
				matches = List.copyOf(client.search(query));
			}
			finally {
				lastUpstreamCall = clock.instant();
			}
			synchronized (cache) {
				cache.put(key, new Cached(matches, clock.instant().plus(properties.cacheTtl())));
			}
			return matches;
		}
	}

	private static String validQuery(String rawQuery) {
		String query = rawQuery == null ? "" : rawQuery.trim().replaceAll("\\s+", " ");
		if (query.length() < MIN_QUERY_LENGTH) {
			throw new InvalidRequestException("q must have at least %d characters".formatted(MIN_QUERY_LENGTH));
		}
		if (query.length() > MAX_QUERY_LENGTH) {
			throw new InvalidRequestException("q must have at most %d characters".formatted(MAX_QUERY_LENGTH));
		}
		return query;
	}

	private List<GeocodeMatch> fromCache(String key) {
		synchronized (cache) {
			Cached entry = cache.get(key);
			if (entry == null) {
				return null;
			}
			if (!entry.expiresAt().isAfter(clock.instant())) {
				cache.remove(key);
				return null;
			}
			return entry.matches();
		}
	}

	private void waitForTurn() {
		Duration sinceLast = Duration.between(lastUpstreamCall, clock.instant());
		Duration remaining = properties.minInterval().minus(sinceLast);
		if (!remaining.isNegative() && !remaining.isZero()) {
			try {
				sleeper.sleep(remaining);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new GeocoderUnavailableException("Interrupted while waiting to search", interrupted);
			}
		}
	}

}
