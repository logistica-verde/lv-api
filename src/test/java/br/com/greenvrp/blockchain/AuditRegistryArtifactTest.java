package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

class AuditRegistryArtifactTest {

	private static String resource(String name) throws IOException {
		try (InputStream stream = AuditRegistryArtifactTest.class.getResourceAsStream(name)) {
			assertThat(stream).as("resource " + name).isNotNull();
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
	}

	private static String selectorOf(Function function) {
		return FunctionEncoder.encode(function).substring(2, 10);
	}

	private final Function anchor = new Function(
		"anchor",
		List.of(new Uint256(BigInteger.ONE), new Bytes32(new byte[32])),
		Collections.emptyList());

	private final Function getHash = new Function(
		"getHash",
		List.of(new Uint256(BigInteger.ONE)),
		List.of(new TypeReference<Bytes32>() {
		}));

	private final Function owner = new Function(
		"owner",
		Collections.emptyList(),
		List.of(new TypeReference<Address>() {
		}));

	@Test
	void theCompiledBytecodeIsAValidHexString() throws IOException {
		String bytecode = resource("/contracts/AuditRegistry.bin");

		assertThat(bytecode).matches("[0-9a-f]+");
		assertThat(bytecode.length() % 2).isZero();
		assertThat(bytecode).startsWith("60a0604052");
	}

	@Test
	void theBytecodeAnswersTheFunctionsTheLedgerClientCalls() throws IOException {
		String bytecode = resource("/contracts/AuditRegistry.bin");

		assertThat(bytecode).contains(selectorOf(anchor));
		assertThat(bytecode).contains(selectorOf(getHash));
		assertThat(bytecode).contains(selectorOf(owner));
	}

	@Test
	void onlyTheAccountThatDeployedTheContractCanAnchor() throws IOException {
		String source = Files.readString(Path.of("contracts", "AuditRegistry.sol"));

		assertThat(source)
			.contains("owner = msg.sender;")
			.contains("require(msg.sender == owner, \"not the owner\");")
			.contains("require(hashes[operationId] == bytes32(0), \"already anchored\");");
	}

	@Test
	void theAbiDescribesTheSameFunctions() throws IOException {
		String abi = resource("/contracts/AuditRegistry.abi");

		assertThat(abi)
			.contains("\"name\":\"anchor\"")
			.contains("\"name\":\"getHash\"")
			.contains("\"name\":\"HashAnchored\"");
		assertThat(abi.indexOf("\"name\":\"anchor\"")).isGreaterThan(0);
		assertThat(Numeric.cleanHexPrefix(FunctionEncoder.encode(anchor)).length()).isEqualTo(8 + 64 + 64);
	}

	@Test
	void theSourceInTheRepositoryDeclaresTheSameInterface() throws IOException {
		String source = Files.readString(Path.of("contracts", "AuditRegistry.sol"));

		assertThat(source)
			.contains("function anchor(uint256 operationId, bytes32 hash) external")
			.contains("function getHash(uint256 operationId) external view returns (bytes32)")
			.contains("event HashAnchored(uint256 indexed operationId, bytes32 hash)");
	}

}
