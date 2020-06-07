package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class BlockHeaderValidatorContext implements BlockHeaderValidator.Context {
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;
    protected final VolatileNetworkTime _networkTime;

    protected final HashMap<Long, BlockId> _blockIds = new HashMap<Long, BlockId>();
    protected final HashMap<Long, BlockHeader> _blockHeaders = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();

    protected BlockId _getBlockId(final Long blockHeight) throws DatabaseException {
        { // Check for a cached BlockId...
            final BlockId blockId = _blockIds.get(blockHeight);
            if (blockId != null) {
                return blockId;
            }
        }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
        if (blockId != null) {
            _blockIds.put(blockHeight, blockId);
        }

        return blockId;
    }

    public BlockHeaderValidatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager, final VolatileNetworkTime networkTime) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;
        _networkTime = networkTime;
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        { // Check for a cached value...
            final BlockHeader blockHeader = _blockHeaders.get(blockHeight);
            if (blockHeader != null) {
                return blockHeader;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
            _blockHeaders.put(blockHeight, blockHeader);
            return blockHeader;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public ChainWork getChainWork(final Long blockHeight) {
        { // Check for a cached value...
            final ChainWork chainWork = _chainWorks.get(blockHeight);
            if (chainWork != null) {
                return chainWork;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(blockId);
            _chainWorks.put(blockHeight, chainWork);
            return chainWork;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        { // Check for a cached value...
            final MedianBlockTime medianBlockTime = _medianBlockTimes.get(blockHeight);
            if (medianBlockTime != null) {
                return medianBlockTime;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);
            _medianBlockTimes.put(blockHeight, medianBlockTime);
            return medianBlockTime;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
    }

    public void loadBlock(final Long blockHeight, final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);
        final ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(blockId);

        _blockIds.put(blockHeight, blockId);
        _blockHeaders.put(blockHeight, blockHeader);
        _medianBlockTimes.put(blockHeight, medianBlockTime.asConst());
        _chainWorks.put(blockHeight, chainWork);
    }

    public void loadBlocks(final Long firstBlockHeight, final List<BlockId> blockIds, final List<BlockHeader> blockHeaders) throws DatabaseException {
        if (blockIds.isEmpty()) { return; }
        if (! Util.areEqual(blockIds.getCount(), blockHeaders.getCount())) {
            Logger.debug("BlockValidatorContext::loadBlocks parameter mismatch.");
            return;
        }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId firstBlockId = blockIds.get(0);
        final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(firstBlockId);

        ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(firstBlockId);
        for (int i = 0; i < blockIds.getCount(); ++i) {
            final BlockId blockId = blockIds.get(i);
            final BlockHeader blockHeader = blockHeaders.get(i);
            final Long blockHeight = (firstBlockHeight + i);


            if (i > 0) {
                medianBlockTime.addBlock(blockHeader);

                final Difficulty difficulty = blockHeader.getDifficulty();
                final BlockWork blockWork = difficulty.calculateWork();
                chainWork = ChainWork.add(chainWork, blockWork);
            }

            _blockIds.put(blockHeight, blockId);
            _blockHeaders.put(blockHeight, blockHeader);
            _medianBlockTimes.put(blockHeight, medianBlockTime.asConst());
            _chainWorks.put(blockHeight, chainWork);
        }
    }
}