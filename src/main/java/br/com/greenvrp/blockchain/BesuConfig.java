package br.com.greenvrp.blockchain;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties(BesuProperties.class)
public class BesuConfig {

	@Bean(destroyMethod = "shutdown")
	public Web3j web3j(BesuProperties properties) {
		return Web3j.build(new HttpService(properties.rpc().url()));
	}

	@Bean
	public Ledger besuLedger(Web3j web3j, BesuProperties properties) {
		return new BesuLedger(web3j, properties);
	}

}
