package br.com.greenvrp.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "blockchain.retry.enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainRetryJob {

	private static final Logger log = LoggerFactory.getLogger(BlockchainRetryJob.class);

	private final BlockchainService service;

	public BlockchainRetryJob(BlockchainService service) {
		this.service = service;
	}

	@Scheduled(
		initialDelayString = "${blockchain.retry.initial-delay:PT2M}",
		fixedDelayString = "${blockchain.retry.interval:PT5M}")
	public void retryFailedAnchors() {
		int retried = service.retryFailed();
		if (retried > 0) {
			log.info("Retried {} failed blockchain anchor(s)", retried);
		}
	}

}
