package br.com.greenvrp.blockchain;

import br.com.greenvrp.route.Operation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "registros_blockchain")
public class BlockchainRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operacao", unique = true)
	private Operation operation;

	@Column(name = "hash_sha256", nullable = false, length = 64)
	private String hashSha256;

	@Column(name = "tx_hash", length = 66)
	private String txHash;

	@Column(name = "bloco_ledger")
	private Long blocoLedger;

	@Column(name = "timestamp_registro", nullable = false)
	private Instant timestampRegistro;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_integridade", nullable = false, length = 20)
	private AnchorStatus status;

	@Column(name = "mensagem_erro", length = 500)
	private String mensagemErro;

	protected BlockchainRecord() {
	}

	private BlockchainRecord(Operation operation, String hashSha256, AnchorStatus status) {
		this.operation = operation;
		this.hashSha256 = hashSha256;
		this.status = status;
		this.timestampRegistro = Instant.now();
	}

	public static BlockchainRecord anchored(Operation operation, String hashSha256, AnchorReceipt receipt) {
		BlockchainRecord record = new BlockchainRecord(operation, hashSha256, AnchorStatus.ANCHORED);
		record.txHash = receipt.txHash();
		record.blocoLedger = receipt.blockNumber();
		return record;
	}

	public static BlockchainRecord failed(Operation operation, String hashSha256, String message) {
		BlockchainRecord record = new BlockchainRecord(operation, hashSha256, AnchorStatus.FAILED);
		record.mensagemErro = message;
		return record;
	}

	public Long getId() {
		return id;
	}

	public String getHashSha256() {
		return hashSha256;
	}

	public String getTxHash() {
		return txHash;
	}

	public Long getBlocoLedger() {
		return blocoLedger;
	}

	public Instant getTimestampRegistro() {
		return timestampRegistro;
	}

	public AnchorStatus getStatus() {
		return status;
	}

	public String getMensagemErro() {
		return mensagemErro;
	}

}
