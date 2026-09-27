package br.com.greenvrp.vehicle;

public record VehicleResponse(

	Long id,
	String placa,
	Double massaKg,
	String tipoCombustivel,
	boolean ativo

) {

	public static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(
			vehicle.getId(),
			vehicle.getPlaca(),
			vehicle.getMassaKg(),
			vehicle.getTipoCombustivel(),
			vehicle.isAtivo()
		);
	}

}
