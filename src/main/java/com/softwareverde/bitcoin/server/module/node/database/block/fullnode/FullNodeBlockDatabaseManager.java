package com.softwareverde.bitcoin.server.module.node.database.block.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class FullNodeBlockDatabaseManager implements BlockDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    protected void _associateTransactionToBlock(final TransactionId transactionId, final Long diskOffset, final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final Integer currentTransactionCount = _getTransactionCount(blockId);
            databaseConnection.executeSql(
                new Query("INSERT INTO block_transactions (block_id, transaction_id, disk_offset, sort_order) VALUES (?, ?, ?, ?)")
                    .setParameter(blockId)
                    .setParameter(transactionId)
                    .setParameter(diskOffset)
                    .setParameter(currentTransactionCount)
            );

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET transaction_count = ? WHERE id = ?")
                    .setParameter(currentTransactionCount + 1L)
                    .setParameter(blockId)
            );
        }
    }


    protected void _associateTransactionsToBlock(final List<TransactionId> transactionIds, final List<Long> diskOffsets, final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO block_transactions (block_id, transaction_id, disk_offset, sort_order) VALUES (?, ?, ?, ?)");
            int sortOrder = 0;
            for (final TransactionId transactionId : transactionIds) {
                batchedInsertQuery.setParameter(blockId);
                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(diskOffsets.get(sortOrder));
                batchedInsertQuery.setParameter(sortOrder);
                sortOrder += 1;
            }

            databaseConnection.executeSql(batchedInsertQuery);

            final long transactionCount = transactionIds.getCount();
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET transaction_count = ? WHERE id = ?")
                    .setParameter(transactionCount)
                    .setParameter(blockId)
            );
        }
    }

    protected void _storeBlockTransactions(final BlockId blockId, final Block block) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<Transaction> transactions = block.getTransactions();

        long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
        diskOffset += ByteUtil.variableLengthIntegerToBytes(transactions.getCount()).length;

        final MutableList<Long> diskOffsets = new MutableList<Long>(transactions.getCount());
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        for (final Transaction transaction : transactions) {
            diskOffsets.add(diskOffset);
            diskOffset += transactionDeflater.getByteCount(transaction);
        }

        final MilliTimer storeBlockTimer = new MilliTimer();
        final MilliTimer associateTransactionsTimer = new MilliTimer();

        storeBlockTimer.start();
        {
            final List<TransactionId> transactionIds = transactionDatabaseManager.storeTransactions(transactions);
            if (transactionIds == null) { throw new DatabaseException("Unable to store block transactions."); }

            associateTransactionsTimer.start();
            _associateTransactionsToBlock(transactionIds, diskOffsets, blockId);
            associateTransactionsTimer.stop();
            Logger.info("AssociateTransactions: " + associateTransactionsTimer.getMillisecondsElapsed() + "ms");
        }
        storeBlockTimer.stop();
        Logger.info("StoreBlockDuration: " + storeBlockTimer.getMillisecondsElapsed() + "ms");
    }

    protected Integer _getTransactionCount(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            // new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
            new Query("SELECT id, transaction_count FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getInteger("transaction_count");
    }

    protected List<TransactionId> _getTransactionIds(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM block_transactions WHERE block_id = ? ORDER BY sort_order ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    protected List<Transaction> _getBlockTransactions(final BlockId blockId) throws DatabaseException {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<TransactionId> transactionIds = _getTransactionIds(blockId);

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(transactionIds.getCount());
        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            if (transaction == null) { return null; }

            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    protected BlockId _getHeadBlockId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getHeadBlockHash() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            // new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.chain_work DESC LIMIT 1")
            new Query("SELECT id, hash FROM blocks WHERE transaction_count > 0 ORDER BY chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    protected MutableBlock _getBlock(final BlockId blockId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);

        if (blockHeader == null) {
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            Logger.warn("Unable to inflate block. BlockId: " + blockId + " Hash: " + blockHash);
            return null;
        }

        final List<Transaction> transactions = _getBlockTransactions(blockId);
        if (transactions == null) {
            Logger.warn("Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        final MutableBlock block = new MutableBlock(blockHeader, transactions);

        if (! Util.areEqual(blockHeader.getHash(), block.getHash())) {
            Logger.warn("Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        return block;
    }

    public FullNodeBlockDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public MutableBlock getBlock(final BlockId blockId) throws DatabaseException {
        return _getBlock(blockId);
    }

    /**
     * Inserts the Block (and BlockHeader if it does not exist) (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this will update the existing BlockHeader.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain if the BlockHeader did not exist.
     */
    public BlockId storeBlock(final Block block) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to storeBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final BlockId blockId;
        if (existingBlockId == null) {
            blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
            blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);
        }
        else {
            blockId = existingBlockId;
        }

        _storeBlockTransactions(blockId, block);

        return blockId;
    }

    public Boolean storeBlockTransactions(final Block block) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.warn("Attempting to insert transactions without BlockHeader stored: "+ blockHash);
            return false;
        }

        _storeBlockTransactions(blockId, block);

        return true;
    }

    /**
     * Inserts the Block (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this function will throw a DatabaseException.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain.
     */
    public BlockId insertBlock(final Block block) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to insertBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
        if (blockId == null) { return null; }

        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        _storeBlockTransactions(blockId, block);
        return blockId;
    }

    /**
     * Returns the Sha256Hash of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    @Override
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        return _getHeadBlockHash();
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    @Override
    public BlockId getHeadBlockId() throws DatabaseException {
        return _getHeadBlockId();
    }

    /**
     * Returns true if the BlockHeader and its Transactions have been downloaded and verified.
     */
    @Override
    public Boolean hasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash, transaction_count FROM blocks WHERE hash = ? AND transaction_count > 0")
                .setParameter(blockHash)
        );
        return (! rows.isEmpty());
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE id = ? AND transaction_count > 0")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        return _getTransactionIds(blockId);
    }

    @Override
    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        return _getTransactionCount(blockId);
    }
}
