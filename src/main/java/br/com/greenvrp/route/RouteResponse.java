package br.com.greenvrp.route;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public record RouteResponse(

	Long id,
	Long vehicleId,
	OptimizationCriterion criterion,
	OperationStatus status,
	Instant createdAt,
	Double totalEmissionKg,
	Double totalDistanceMeters,
	Long executionMs,
	String errorMessage,
	List<PointItem> points,
	List<SegmentItem> segments,
	Double cargoKg,
	boolean returnsToBase,
	String baseName

) {

	public RouteResponse(
		Long id, Long vehicleId, OptimizationCriterion criterion, OperationStatus status, Instant createdAt,
		Double totalEmissionKg, Double totalDistanceMeters, Long executionMs, String errorMessage,
		List<PointItem> points, List<SegmentItem> segments) {
		this(id, vehicleId, criterion, status, createdAt, totalEmissionKg, totalDistanceMeters, executionMs,
			errorMessage, points, segments, null, false, null);
	}

	public record PointItem(
		Long pointId,
		String name,
		double latitude,
		double longitude,
		int visitOrder
	) {
	}

	public record SegmentItem(
		Long fromPointId,
		Long toPointId,
		double distanceMeters,
		double averageSpeedKmh,
		double slopePercent,
		double emissionKg,
		List<List<Double>> geometry
	) {

		public SegmentItem(
			Long fromPointId, Long toPointId, double distanceMeters, double averageSpeedKmh, double slopePercent,
			double emissionKg) {
			this(fromPointId, toPointId, distanceMeters, averageSpeedKmh, slopePercent, emissionKg, List.of());
		}

	}

	private static List<List<Double>> latLngOf(RouteSegment segment) {
		return Arrays.stream(segment.getGeom().getCoordinates())
			.map(coordinate -> List.of(coordinate.getY(), coordinate.getX()))
			.toList();
	}

	public static RouteResponse from(Operation operation) {
		List<PointItem> points = operation.getPoints().stream()
			.map(item -> new PointItem(
				item.getPoint().getId(),
				item.getPoint().getNome(),
				item.getPoint().getLatitude(),
				item.getPoint().getLongitude(),
				item.getOrdemVisita()))
			.sorted(Comparator.comparingInt(PointItem::visitOrder))
			.toList();
		List<SegmentItem> segments = operation.getSegments().stream()
			.map(segment -> new SegmentItem(
				segment.getOrigin().getId(),
				segment.getDestination().getId(),
				segment.getDistanciaMetros(),
				segment.getVelocidadeMedia(),
				segment.getDeclividade(),
				segment.getEmissaoKgCo2(),
				latLngOf(segment)))
			.toList();
		Double totalDistance = segments.isEmpty()
			? null
			: segments.stream().mapToDouble(SegmentItem::distanceMeters).sum();
		return new RouteResponse(
			operation.getId(),
			operation.getVehicle().getId(),
			operation.getCriterio(),
			operation.getStatus(),
			operation.getDataHora(),
			operation.getEmissaoTotalKg(),
			totalDistance,
			operation.getTempoExecucaoMs(),
			operation.getMensagemErro(),
			points,
			segments,
			operation.getCargaKg(),
			operation.isRetornaBase(),
			operation.getBase() == null ? null : operation.getBase().getNome());
	}

}
