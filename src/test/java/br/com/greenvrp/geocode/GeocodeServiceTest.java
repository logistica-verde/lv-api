package br.com.greenvrp.geocode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.greenvrp.api.InvalidRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeocodeServiceTest {

	private static final GeocodeMatch SE = new GeocodeMatch("Praça da Sé, São Paulo", "-23.5505", "-46.6333");

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-12T12:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	private final GeocoderClient client = mock(GeocoderClient.class);
	private final MutableClock clock = new MutableClock();
	private final List<Duration> sleeps = new ArrayList<>();

	private GeocodeService service(int cacheSize) {
		GeocodeProperties properties = new GeocodeProperties(
			"unused", "lv-test/1.0", Duration.ofSeconds(5), Duration.ofMillis(1100), Duration.ofHours(24),
			cacheSize, 5, "br");
		return new GeocodeService(client, properties, clock, sleeps::add);
	}

	@Test
	void returnsWhatTheProviderFound() {
		given(client.search("Praça da Sé")).willReturn(List.of(SE));

		assertThat(service(10).search("Praça da Sé")).containsExactly(SE);
	}

	@Test
	void answersARepeatedSearchFromTheCache() {
		given(client.search("Praça da Sé")).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("Praça da Sé");
		service.search("Praça da Sé");

		verify(client, times(1)).search("Praça da Sé");
	}

	@Test
	void ignoresCaseAndExtraSpacesWhenCaching() {
		given(client.search("Praça da Sé")).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("Praça da Sé");
		List<GeocodeMatch> again = service.search("  PRAÇA   da  sé ");

		assertThat(again).containsExactly(SE);
		verify(client, times(1)).search(anyString());
	}

	@Test
	void cachesAnEmptyAnswerToo() {
		given(client.search("Rua Inexistente")).willReturn(List.of());
		GeocodeService service = service(10);

		service.search("Rua Inexistente");
		service.search("Rua Inexistente");

		verify(client, times(1)).search("Rua Inexistente");
	}

	@Test
	void asksTheProviderAgainOnceTheCacheEntryExpires() {
		given(client.search("Praça da Sé")).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("Praça da Sé");
		clock.advance(Duration.ofHours(23));
		service.search("Praça da Sé");
		verify(client, times(1)).search("Praça da Sé");
		clock.advance(Duration.ofHours(2));
		service.search("Praça da Sé");

		verify(client, times(2)).search("Praça da Sé");
	}

	@Test
	void forgetsTheLeastRecentlyUsedSearchWhenTheCacheIsFull() {
		given(client.search(anyString())).willReturn(List.of(SE));
		GeocodeService service = service(2);

		service.search("primeira rua");
		service.search("segunda rua");
		service.search("primeira rua");
		service.search("terceira rua");
		service.search("primeira rua");
		service.search("segunda rua");

		verify(client, times(1)).search("primeira rua");
		verify(client, times(2)).search("segunda rua");
		verify(client, times(1)).search("terceira rua");
	}

	@Test
	void waitsToRespectTheProviderRateLimit() {
		given(client.search(anyString())).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("primeira rua");
		clock.advance(Duration.ofMillis(300));
		service.search("segunda rua");

		assertThat(sleeps).containsExactly(Duration.ofMillis(800));
	}

	@Test
	void doesNotWaitWhenEnoughTimeHasPassed() {
		given(client.search(anyString())).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("primeira rua");
		clock.advance(Duration.ofSeconds(2));
		service.search("segunda rua");

		assertThat(sleeps).isEmpty();
	}

	@Test
	void doesNotWaitForTheCache() {
		given(client.search(anyString())).willReturn(List.of(SE));
		GeocodeService service = service(10);

		service.search("primeira rua");
		service.search("primeira rua");
		service.search("primeira rua");

		assertThat(sleeps).isEmpty();
	}

	@Test
	void doesNotCacheFailuresAndStillCountsThemForTheRateLimit() {
		given(client.search("Praça da Sé"))
			.willThrow(new GeocoderUnavailableException("down", new RuntimeException()))
			.willReturn(List.of(SE));
		GeocodeService service = service(10);

		assertThatThrownBy(() -> service.search("Praça da Sé")).isInstanceOf(GeocoderUnavailableException.class);
		assertThat(service.search("Praça da Sé")).containsExactly(SE);

		verify(client, times(2)).search("Praça da Sé");
		assertThat(sleeps).containsExactly(Duration.ofMillis(1100));
	}

	@Test
	void refusesAQueryThatIsTooShortOrBlank() {
		GeocodeService service = service(10);

		for (String query : new String[] {null, "", "   ", "ab", " a  "}) {
			assertThatThrownBy(() -> service.search(query))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("at least 3");
		}
		verify(client, never()).search(anyString());
	}

	@Test
	void refusesAQueryThatIsTooLong() {
		assertThatThrownBy(() -> service(10).search("a".repeat(201)))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("at most 200");
		verify(client, never()).search(anyString());
	}

	@Test
	void treatsTabsAndLineBreaksAsSpaces() {
		given(client.search("Rua Augusta 100")).willReturn(List.of(SE));

		service(10).search("Rua\tAugusta\n 100");

		verify(client).search("Rua Augusta 100");
	}

	@Test
	void sendsTheTrimmedQueryToTheProvider() {
		given(client.search("Rua Augusta, 100")).willReturn(List.of(SE));

		service(10).search("  Rua   Augusta,  100 ");

		verify(client).search("Rua Augusta, 100");
	}

}
