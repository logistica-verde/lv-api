// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract AuditRegistry {

    mapping(uint256 => bytes32) private hashes;

    event HashAnchored(uint256 indexed operationId, bytes32 hash);

    function anchor(uint256 operationId, bytes32 hash) external {
        require(hash != bytes32(0), "empty hash");
        require(hashes[operationId] == bytes32(0), "already anchored");
        hashes[operationId] = hash;
        emit HashAnchored(operationId, hash);
    }

    function getHash(uint256 operationId) external view returns (bytes32) {
        return hashes[operationId];
    }

}
