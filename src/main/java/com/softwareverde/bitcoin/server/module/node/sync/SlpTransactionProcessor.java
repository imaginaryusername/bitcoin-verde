package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;

import java.util.HashMap;
import java.util.Map;

public class SlpTransactionProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected static Boolean _isConnected(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        final List<BlockId> blockIds = transactionDatabaseManager.getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANCESTOR);
            if (isConnected) {
                return true;
            }
        }

        return false;
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    @Override
    protected void _onStart() {
        Logger.trace("SlpTransactionProcessor Starting.");
    }

    @Override
    protected Boolean _run() {
        Logger.trace("SlpTransactionProcessor Running.");
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

            final Container<BlockchainSegmentId> blockchainSegmentId = new Container<BlockchainSegmentId>();

            final SlpTransactionValidator.TransactionAccumulator transactionAccumulator = new SlpTransactionValidator.TransactionAccumulator() {
                @Override
                public Map<Sha256Hash, Transaction> getTransactions(final List<Sha256Hash> transactionHashes) {
                    try {
                        final HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(transactionHashes.getSize());
                        for (final Sha256Hash transactionHash : transactionHashes) {
                            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                            if (transactionId == null) { continue; }

                            final Boolean transactionIsConnectedToBlockchainSegment = _isConnected(blockchainSegmentId.value, transactionId, databaseManager);
                            if (! transactionIsConnectedToBlockchainSegment) { continue; }

                            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                            transactions.put(transactionHash, transaction);
                        }
                        return transactions;
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
                        return null;
                    }
                }
            };

            final SlpTransactionValidator.SlpTransactionValidationCache slpTransactionValidationCache = new SlpTransactionValidator.SlpTransactionValidationCache() {
                @Override
                public Boolean isValid(final Sha256Hash transactionHash) {
                    try {
                        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                        if (transactionId == null) { return null; }

                        return slpTransactionDatabaseManager.getSlpTransactionValidationResult(blockchainSegmentId.value, transactionId);
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
                        return null;
                    }
                }
            };

            // 1. Iterate through blocks for SLP transactions.
            // 2. Validate any SLP transactions for that block's blockchain segment.
            // 3. Update those SLP transactions validation statuses via the slpTransactionDatabaseManager.
            // 4. Move on to the next block.

            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);
            final Map<BlockId, List<TransactionId>> pendingSlpTransactionIds = slpTransactionDatabaseManager.getPendingValidationSlpTransactions(BATCH_SIZE);

            for (final BlockId blockId : pendingSlpTransactionIds.keySet()) {
                blockchainSegmentId.value = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);

                final List<TransactionId> transactionIds = pendingSlpTransactionIds.get(blockId);
                for (final TransactionId transactionId : transactionIds) {
                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    final Boolean isValid = slpTransactionValidator.validateTransaction(transaction);

                    slpTransactionDatabaseManager.setSlpTransactionValidationResult(blockchainSegmentId.value, transactionId, isValid);
                    Logger.trace("Slp Tx " + transaction.getHash() + " IsValid: " + isValid);
                }
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }

        Logger.trace("SlpTransactionProcessor Stopping.");
        return false;
    }

    @Override
    protected void _onSleep() {
        Logger.trace("SlpTransactionProcessor Sleeping.");
    }

    public SlpTransactionProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }
}
