package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CoverageAreaTest {

	private final CoverageArea saoPaulo = new CoverageArea("-24.10,-46.95,-23.25,-46.25");

	@Test
	void acceptsEverywhereWhenNoAreaIsConfigured() {
		assertThat(new CoverageArea("").isDefined()).isFalse();
		assertThat(new CoverageArea("").contains(-22.9, -43.2)).isTrue();
		assertThat(new CoverageArea(null).contains(0, 0)).isTrue();
	}

	@Test
	void acceptsThePointsInsideTheArea() {
		assertThat(saoPaulo.isDefined()).isTrue();
		assertThat(saoPaulo.contains(-23.5505, -46.6333)).isTrue();
	}

	@Test
	void acceptsThePointsOnTheEdge() {
		assertThat(saoPaulo.contains(-24.10, -46.95)).isTrue();
		assertThat(saoPaulo.contains(-23.25, -46.25)).isTrue();
	}

	@Test
	void refusesThePointsOutsideTheArea() {
		assertThat(saoPaulo.contains(-22.9068, -43.1729)).isFalse();
		assertThat(saoPaulo.contains(-23.5505, -45.0)).isFalse();
		assertThat(saoPaulo.contains(-25.0, -46.6)).isFalse();
	}

	@Test
	void failsAtStartWhenTheConfigurationIsMalformed() {
		assertThatThrownBy(() -> new CoverageArea("-24.1,-46.9,-23.2"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CoverageArea("-23.2,-46.25,-24.1,-46.95"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CoverageArea("a,b,c,d")).isInstanceOf(NumberFormatException.class);
	}

}
