package br.com.greenvrp.blockchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tentativas_ancoragem")
public class AnchorAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "id_operacao", nullable = false)
	private Long operationId;

	@Column(name = "numero", nullable = false)
	private int number;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultado", nullable = false, length = 20)
	private AnchorAttemptResult result;

	@Column(name = "tx_hash", length = 66)
	private String txHash;

	@Column(name = "mensagem_erro", length = 500)
	private String message;

	@Column(name = "data_hora", nullable = false)
	private Instant at;

	protected AnchorAttempt() {
	}

	public AnchorAttempt(Long operationId, int number, AnchorAttemptResult result, String txHash, String message) {
		this.operationId = operationId;
		this.number = number;
		this.result = result;
		this.txHash = txHash;
		this.message = message;
		this.at = Instant.now();
	}

	public Long getOperationId() {
		return operationId;
	}

	public int getNumber() {
		return number;
	}

	public AnchorAttemptResult getResult() {
		return result;
	}

	public String getTxHash() {
		return txHash;
	}

	public String getMessage() {
		return message;
	}

	public Instant getAt() {
		return at;
	}

}
