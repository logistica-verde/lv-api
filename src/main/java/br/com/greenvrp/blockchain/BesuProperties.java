package br.com.greenvrp.blockchain;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "besu")
public record BesuProperties(
	@DefaultValue Rpc rpc,
	@DefaultValue("1337") long chainId,
	@DefaultValue("") String contractAddress,
	@DefaultValue("") String privateKey,
	@DefaultValue("0") BigInteger gasPrice,
	@DefaultValue("300000") BigInteger gasLimit
) {

	public record Rpc(@DefaultValue("http://localhost:8545") String url) {
	}

}
