package br.com.greenvrp.base;

public record BaseResponse(

	Long id,
	String nome,
	String endereco,
	double latitude,
	double longitude,
	boolean ativo

) {

	public static BaseResponse from(Base base) {
		return new BaseResponse(
			base.getId(), base.getNome(), base.getEndereco(), base.getLatitude(), base.getLongitude(), base.isAtivo());
	}

}
