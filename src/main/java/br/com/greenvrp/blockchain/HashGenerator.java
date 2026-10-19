package br.com.greenvrp.blockchain;

import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationPoint;
import br.com.greenvrp.route.RouteSegment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HashGenerator {

	private static final int DECIMAL_PLACES = 6;

	public String hash(Operation operation) {
		return sha256Hex(canonicalJson(operation));
	}

	String canonicalJson(Operation operation) {
		List<OperationPoint> points = operation.getPoints().stream()
			.sorted(Comparator.comparingInt(OperationPoint::getOrdemVisita))
			.toList();
		List<RouteSegment> segments = operation.getSegments().stream()
			.sorted(Comparator.comparingInt(RouteSegment::getOrdem))
			.toList();

		StringBuilder json = new StringBuilder();
		json.append("{\"operationId\":").append(operation.getId())
			.append(",\"vehicleId\":").append(operation.getVehicle().getId())
			.append(",\"criterion\":\"").append(operation.getCriterio().name()).append('"')
			.append(",\"createdAt\":\"").append(operation.getDataHora().truncatedTo(ChronoUnit.MILLIS)).append('"')
			.append(cargo(operation))
			.append(",\"totalEmissionKg\":").append(number(operation.getEmissaoTotalKg()))
			.append(",\"points\":[");
		for (int index = 0; index < points.size(); index++) {
			OperationPoint point = points.get(index);
			json.append(index == 0 ? "" : ",")
				.append("{\"pointId\":").append(point.getPoint().getId())
				.append(",\"visitOrder\":").append(point.getOrdemVisita()).append('}');
		}
		json.append("],\"segments\":[");
		for (int index = 0; index < segments.size(); index++) {
			RouteSegment segment = segments.get(index);
			json.append(index == 0 ? "" : ",")
				.append("{\"order\":").append(segment.getOrdem())
				.append(",\"fromPointId\":").append(segment.getOrigin().getId())
				.append(",\"toPointId\":").append(segment.getDestination().getId())
				.append(",\"distanceMeters\":").append(number(segment.getDistanciaMetros()))
				.append(",\"averageSpeedKmh\":").append(number(segment.getVelocidadeMedia()))
				.append(",\"slopePercent\":").append(number(segment.getDeclividade()))
				.append(",\"emissionKg\":").append(number(segment.getEmissaoKgCo2())).append('}');
		}
		return json.append("]}").toString();
	}

	private String cargo(Operation operation) {
		return operation.getCargaKg() == null ? "" : ",\"cargoKg\":" + number(operation.getCargaKg());
	}

	private String number(Double value) {
		if (value == null) {
			return "null";
		}
		return new BigDecimal(value).setScale(DECIMAL_PLACES, RoundingMode.HALF_UP).toPlainString();
	}

	private String sha256Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is not available", impossible);
		}
	}

}
