package br.com.greenvrp.deliverypoint;

public record DeliveryPointResponse(

	Long id,
	String nome,
	String endereco,
	double latitude,
	double longitude,
	boolean ativo

) {

	public static DeliveryPointResponse from(DeliveryPoint point) {
		return new DeliveryPointResponse(
			point.getId(),
			point.getNome(),
			point.getEndereco(),
			point.getLatitude(),
			point.getLongitude(),
			point.isAtivo()
		);
	}

}
