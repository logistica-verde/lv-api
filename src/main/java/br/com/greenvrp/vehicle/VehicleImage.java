package br.com.greenvrp.vehicle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

public enum VehicleImage {
	VAN,
	PICKUP,
	VUC,
	TRUCK,
	SEMI,
	MOTORCYCLE;

	public static final VehicleImage DEFAULT = TRUCK;

	@JsonValue
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	@JsonCreator
	public static VehicleImage fromKey(String key) {
		return Arrays.stream(values())
			.filter(image -> image.key().equals(key))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown vehicle image: " + key));
	}

}
