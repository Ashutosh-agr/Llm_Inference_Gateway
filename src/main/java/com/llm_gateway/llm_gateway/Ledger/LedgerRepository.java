package com.llm_gateway.llm_gateway.Ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerRepository  extends JpaRepository<LedgerEntity, UUID> {
}
