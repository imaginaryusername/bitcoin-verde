package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeUnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.network.time.MutableNetworkTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockProcessorTests extends IntegrationTest {
    protected static Boolean utxoExistsInCommittedUtxoSet(final Transaction transaction, final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT 1 FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = 0 AND is_spent = 0 LIMIT 1")
                .setParameter(transaction.getHash())
        );
        return (! rows.isEmpty());
    }

    protected class TestHarness {
        public final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        public final MutableNetworkTime networkTime = new MutableNetworkTime();
        public final FakeUnspentTransactionOutputContext unspentTransactionOutputSet = new FakeUnspentTransactionOutputContext();

        public final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(_masterInflater, _masterInflater, _blockStore, _fullNodeDatabaseManagerFactory, this.networkTime, _synchronizationStatus, _transactionValidatorFactory) {
            @Override
            public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
                return new TransactionValidatorCore(blockOutputs, transactionValidatorContext) {
                    @Override
                    protected Long _getCoinbaseMaturity() {
                        return 0L;
                    }
                };
            }
        };
        public final BlockProcessor blockProcessor = new BlockProcessor(this.blockProcessorContext);

        public TestHarness() {
            blockProcessor.setMaxThreadCount(1);
            blockProcessor.setTrustedBlockHeight(BlockValidator.DO_NOT_TRUST_BLOCKS);
        }

        public Long processBlock(final Block block) {
            return this.blockProcessor.processBlock(block, this.unspentTransactionOutputSet).blockHeight;
        }

        public Long processBlock(final Block block, final Long blockHeight, final FullNodeDatabaseManager databaseManager) throws Exception {
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                blockHeaderDatabaseManager.storeBlockHeader(block);
            }

            final MutableUnspentTransactionOutputSet unspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
            unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight);
            return this.blockProcessor.processBlock(block, unspentTransactionOutputSet).blockHeight;
        }

        public Block inflateBlock(final String blockData) {
            return this.blockInflater.fromBytes(ByteArray.fromHexString(blockData));
        }

        public UnspentTransactionOutputManager newUnspentTransactionOutputManager(final FullNodeDatabaseManager databaseManager) {
            return new UnspentTransactionOutputManager(databaseManager, Long.MAX_VALUE);
        }
    }

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_process_genesis_block() throws Exception {
        // NOTE: Within the NodeModule, the blockProcessor doesn't process the genesis block, instead it is processed differently by the BlockchainBuilder...

        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);

            final TransactionOutputIdentifier transactionOutputIdentifier;
            {
                final List<Transaction> transactions = genesisBlock.getTransactions();
                final Transaction transaction = transactions.get(0);
                transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            // Action
            final Long blockHeight = harness.processBlock(genesisBlock);
            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);

            // Assert
            Assert.assertEquals(Long.valueOf(0L), blockHeight);
            Assert.assertNull(transactionOutput); // Outputs created by the genesis are not spendable...
        }
    }

    @Test
    public void should_process_blocks_with_utxos() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block block01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);

            final TransactionOutput expectedTransactionOutput;
            final TransactionOutputIdentifier transactionOutputIdentifier;
            {
                final List<Transaction> transactions = block01.getTransactions();
                final Transaction transaction = transactions.get(0);
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

                expectedTransactionOutput = transactionOutputs.get(0);
                transactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            // Action
            harness.processBlock(genesisBlock);
            final Long blockHeight = harness.processBlock(block01);
            final TransactionOutput transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);

            // Assert
            Assert.assertEquals(Long.valueOf(1L), blockHeight);
            Assert.assertEquals(expectedTransactionOutput, transactionOutput);
        }
    }

    @Test
    public void should_handle_reorg_fork() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            // Load Order: genesisBlock -> forkChainBlock01 -> mainChainBlock01 -> mainChainBlock02
            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK); // 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F
            final Block forkChainBlock01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1); // 0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2
            final Block mainChainBlock01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1); // 00000000839A8E6886AB5951D76F411475428AFC90947EE320161BBF18EB6048
            final Block mainChainBlock02 = harness.inflateBlock(BlockData.MainChain.BLOCK_2); // 000000006A625F06636B8BB6AC7B960A8D03705D1ACE08B1A19DA3FDCC99DDBD

            final TransactionOutputIdentifier invalidTransactionOutputIdentifier;
            {
                final List<Transaction> transactions = forkChainBlock01.getTransactions();
                final Transaction transaction = transactions.get(0);
                invalidTransactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            final Transaction transaction;
            { // Inflate transaction that spends the coinbase of the ForkChain2.BLOCK_1...
                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                transaction = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001F2857FE43B7FE710900C50F38DEFFDEF0304D05F0911BBA7CAC9859BD0797D0E000000008B483045022100AFC23C6CB284C4897BA7EFAF867B45D0A80D60EA13B4EE97394A66A9A9DCE863022049B91D579050DC5BB3CB5767A3D0A0AB2A8E7D77B0A7C4B96C7F2EDEAD0DDB78414104369319023063307A8209C518C0E07CC27AA2502113907BEECA66DEFA0669DEA00D995BEC5AB5964368769C4A772F3B04C9DFA002A14BE8B27BD0E3A57CEBFDA9FFFFFFFF0100F2052A010000001976A91410DB8BE45C9035835DD8B31E811143166D9907EA88AC00000000"));
            }

            // Action
            final Long blockHeightStep0 = harness.processBlock(genesisBlock);
            Assert.assertEquals(genesisBlock.getHash(), blockDatabaseManager.getHeadBlockHash());
            System.out.println();

            final Long blockHeightStep1 = harness.processBlock(forkChainBlock01);
            Assert.assertEquals(forkChainBlock01.getHash(), blockDatabaseManager.getHeadBlockHash());
            final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
            transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
            System.out.println();

            final Long blockHeightStep2 = harness.processBlock(mainChainBlock01);
            Assert.assertEquals(forkChainBlock01.getHash(), blockDatabaseManager.getHeadBlockHash());
            System.out.println();

            final Long blockHeightStep3 = harness.processBlock(mainChainBlock02);
            Assert.assertEquals(mainChainBlock02.getHash(), blockDatabaseManager.getHeadBlockHash());
            System.out.println();

            // Assert
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep3);

            // The output generated by the old chain should no longer be a UTXO...
            final TransactionOutput oldTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(invalidTransactionOutputIdentifier);
            Assert.assertNull(oldTransactionOutput);

            // The transaction spending the fork chain's UTXO should no longer be in the mempool...
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
            Assert.assertFalse(unconfirmedTransactionIds.contains(transactionId));
        }
    }

    @Test
    public void should_handle_reorg_fork_with_utxo_committed() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final TestHarness harness = new TestHarness();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block mainChainBlock01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);
            final Block mainChainBlock02 = harness.inflateBlock(BlockData.MainChain.BLOCK_2);
            final Block forkChainBlock01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1);

            final TransactionOutputIdentifier invalidTransactionOutputIdentifier;
            {
                final List<Transaction> transactions = forkChainBlock01.getTransactions();
                final Transaction transaction = transactions.get(0);
                invalidTransactionOutputIdentifier = new TransactionOutputIdentifier(transaction.getHash(), 0);
            }

            final Transaction forkChainCoinbaseTransaction = forkChainBlock01.getCoinbaseTransaction();

            final Transaction transaction;
            { // Inflate transaction that spends the coinbase of the ForkChain2.BLOCK_1...
                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                transaction = transactionInflater.fromBytes(ByteArray.fromHexString("0200000001F2857FE43B7FE710900C50F38DEFFDEF0304D05F0911BBA7CAC9859BD0797D0E000000008B483045022100AFC23C6CB284C4897BA7EFAF867B45D0A80D60EA13B4EE97394A66A9A9DCE863022049B91D579050DC5BB3CB5767A3D0A0AB2A8E7D77B0A7C4B96C7F2EDEAD0DDB78414104369319023063307A8209C518C0E07CC27AA2502113907BEECA66DEFA0669DEA00D995BEC5AB5964368769C4A772F3B04C9DFA002A14BE8B27BD0E3A57CEBFDA9FFFFFFFF0100F2052A010000001976A91410DB8BE45C9035835DD8B31E811143166D9907EA88AC00000000"));
            }

            // Action
            harness.processBlock(genesisBlock);

            final Long blockHeightStep1 = harness.processBlock(forkChainBlock01);
            final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
            transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, true); // Commit the UTXO set with outputs that will then be invalidated during a reorg...
            Assert.assertTrue(BlockProcessorTests.utxoExistsInCommittedUtxoSet(forkChainCoinbaseTransaction, databaseConnection)); // Ensure the UTXO was actually committed...

            final Long blockHeightStep2 = harness.processBlock(mainChainBlock01);
            final Long blockHeightStep3 = harness.processBlock(mainChainBlock02);

            // Assert
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep3);

            // The output generated by the old chain should no longer be a UTXO...
            final TransactionOutput oldTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(invalidTransactionOutputIdentifier);
            Assert.assertNull(oldTransactionOutput);

            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, true);

            // Ensure the invalid UTXO isn't left within the on-disk UTXO set...
            Assert.assertFalse(BlockProcessorTests.utxoExistsInCommittedUtxoSet(forkChainCoinbaseTransaction, databaseConnection));
            Assert.assertFalse(BlockProcessorTests.utxoExistsInCommittedUtxoSet(transaction, databaseConnection));

            // The transaction spending the fork chain's UTXO should no longer be in the mempool...
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
            Assert.assertFalse(unconfirmedTransactionIds.contains(transactionId));
        }
    }

    /**
     * This test creates a reorg where the contentious blocks (validly) include the same transaction.
     *  Block Height 3 is contentious between ForkChain2 and ForkChain4.
     *  Load Order: Genesis -> Fork2#1 -> Fork2#2 -> Fork4#3 -> Fork2#3 -> Fork2#4
     * A temporary UTXO set must be created to validate the block; if the new block is valid and surpasses the main
     *  chain its UTXO set becomes the master set.
     */
    @Test
    public void should_handle_contentious_reorg_fork_with_shared_utxos() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            // 0:   MainChain Blocks
            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK); // 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F
            // 1-2: ForkChain2 Blocks
            final Block forkChain2Block01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1); // 0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2
            final Block forkChain2Block02 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_2); // 00000000314E669144E0781C432EB33F2079834D406E46393291E94199F433EE
            // 3':  ForkChain4 Blocks
            final Block forkChain4Block03 = harness.inflateBlock(BlockData.ForkChain4.BLOCK_3); // 00000000C77EFC229BD4EF49BBC08C17AB26B7AC242C10B0105179EFA1A2D0D6
            // 3-4: ForkChain2 Blocks
            final Block forkChain2Block03 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_3); // 00000000EC006D368F4610AAEA50986B4E71450C81E8A2E1D947A2BF93F0BCB7
            final Block forkChain2Block04 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_4); // 000000009F1339E2BBC655346F47CC72075ECCE61E84DF3544A54F5623BED0FE

            // Action
            final Long blockHeightStep0 = harness.processBlock(genesisBlock, 0L, databaseManager);
            Assert.assertEquals(Long.valueOf(0L), blockHeightStep0);
            Assert.assertEquals(genesisBlock.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            // Load Order: Genesis -> Fork2#1 -> Fork2#2 -> Fork4#3 -> Fork2#3 -> Fork2#4

            final Long blockHeightStep1 = harness.processBlock(forkChain2Block01, 1L, databaseManager);
            Assert.assertEquals(Long.valueOf(1L), blockHeightStep1);
            Assert.assertEquals(forkChain2Block01.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            final Long blockHeightStep2 = harness.processBlock(forkChain2Block02, 2L, databaseManager);
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep2);
            Assert.assertEquals(forkChain2Block02.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            final Long blockHeightStep3 = harness.processBlock(forkChain4Block03, 3L, databaseManager);
            Assert.assertEquals(Long.valueOf(3L), blockHeightStep3);
            Assert.assertEquals(forkChain4Block03.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            final Long blockHeightStep4 = harness.processBlock(forkChain2Block03, 3L, databaseManager);
            Assert.assertEquals(Long.valueOf(3L), blockHeightStep4);
            Assert.assertEquals(forkChain4Block03.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            final Long blockHeightStep5 = harness.processBlock(forkChain2Block04, 4L, databaseManager);
            Assert.assertEquals(Long.valueOf(4L), blockHeightStep5);
            Assert.assertEquals(forkChain2Block04.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());
        }
    }

    @Test
    public void should_update_the_committed_utxo_set_after_reorg() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TestHarness harness = new TestHarness();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            // 0:   MainChain Blocks
            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK); // 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F
            // 1-2: ForkChain2 Blocks
            final Block forkChain2Block01 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_1); // 0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2
            final Block forkChain2Block02 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_2); // 00000000314E669144E0781C432EB33F2079834D406E46393291E94199F433EE
            // 3':  ForkChain4 Blocks
            final Block forkChain4Block03 = harness.inflateBlock(BlockData.ForkChain4.BLOCK_3); // 00000000C77EFC229BD4EF49BBC08C17AB26B7AC242C10B0105179EFA1A2D0D6
            // 3-4: ForkChain2 Blocks
            final Block forkChain2Block03 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_3); // 00000000EC006D368F4610AAEA50986B4E71450C81E8A2E1D947A2BF93F0BCB7
            final Block forkChain2Block04 = harness.inflateBlock(BlockData.ForkChain2.BLOCK_4); // 000000009F1339E2BBC655346F47CC72075ECCE61E84DF3544A54F5623BED0FE

            final TransactionOutputIdentifier culledTransactionOutputIdentifier;
            {
                final List<Transaction> transactions = forkChain4Block03.getTransactions();
                final Transaction transaction = transactions.get(1);
                final Sha256Hash transactionHash = transaction.getHash();
                culledTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, 0);
            }

            // Action
            harness.processBlock(genesisBlock, 0L, databaseManager);

            // Load Order: Genesis -> Fork2#1 -> Fork2#2 -> Fork4#3 -> Fork2#3 -> Fork2#4
            final Long blockHeightStep1 = harness.processBlock(forkChain2Block01, 1L, databaseManager);
            Assert.assertNotNull(blockHeightStep1);
            Assert.assertEquals(forkChain2Block01.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());
            final Long blockHeightStep2 = harness.processBlock(forkChain2Block02, 2L, databaseManager);
            Assert.assertNotNull(blockHeightStep2);
            Assert.assertEquals(forkChain2Block02.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());
            final Long blockHeightStep3 = harness.processBlock(forkChain4Block03, 3L, databaseManager);
            Assert.assertNotNull(blockHeightStep3);
            Assert.assertEquals(forkChain4Block03.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            { // Ensure the UTXO (that will later be removed) exists within the UTXO set...
                final TransactionOutput culledTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(culledTransactionOutputIdentifier);
                Assert.assertNotNull(culledTransactionOutput);
            }

            // Commit the UTXO Set
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, true);

            { // Ensure the UTXO (that will later be removed) exists within the UTXO set...
                final TransactionOutput culledTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(culledTransactionOutputIdentifier);
                Assert.assertNotNull(culledTransactionOutput);
            }

            final Long blockHeightStep4 = harness.processBlock(forkChain2Block03, 3L, databaseManager);
            Assert.assertNotNull(blockHeightStep4);
            Assert.assertEquals(forkChain4Block03.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash()); // NOTE: Should remain the original chain.
            final Long blockHeightStep5 = harness.processBlock(forkChain2Block04, 4L, databaseManager);
            Assert.assertNotNull(blockHeightStep5);
            Assert.assertEquals(forkChain2Block04.getHash(), blockHeaderDatabaseManager.getHeadBlockHeaderHash());

            // Re-Commit the UTXO set after the reorg
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, true);

            // Assert
            Assert.assertEquals(Long.valueOf(1L), blockHeightStep1);
            Assert.assertEquals(Long.valueOf(2L), blockHeightStep2);
            Assert.assertEquals(Long.valueOf(3L), blockHeightStep3);
            Assert.assertEquals(Long.valueOf(3L), blockHeightStep4);
            Assert.assertEquals(Long.valueOf(4L), blockHeightStep5);

            final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
            Assert.assertEquals(forkChain2Block04.getHash(), headBlockHash);

            // Ensure the UTXO from the abandoned block does not exist in the UTXO set...
            final TransactionOutput culledTransactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(culledTransactionOutputIdentifier);
            Assert.assertNull(culledTransactionOutput);
        }
    }

    /**
     * This test emulates a found error in production on 2019-11-15 shortly after the hard fork.  While the HF did not cause the bug, it did cause
     *  the bug to manifest when a 10+ old block was mined that was invalid with the new HF rules.
     *
     *  The cause of the bug involved a dirty read from the BlockchainSegment cache which was rolled back after the invalid block failed to process.
     *
     *  The test scenario executed in this test creates the following chain of blocks:
     *
     *                                       genesis (height=0, segment=1)
     *                                         /\
     *                                       /   \
     *          (segment=3) invalidBlock01Prime   block01 (height=1, segment=2)
     *                                             \
     *                                              block02 (height=2, segment=2)
     *                                              \
     *                                               block03 (height=3, segment=2)
     *
     *  Where the insert-order is genesis -> block01 -> block02 -> invalidBlock01Prime -> block03
     *
     *  2020-05 UPDATE: BlockchainSegments are no longer cached, so this bug is no longer possible in its previous form, however the same assertions hold true.
     */
    @Test
    public void should_maintain_correct_blockchain_segment_after_invalid_contentious_block() throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            // Setup
            final TestHarness harness = new TestHarness();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final Block genesisBlock = harness.inflateBlock(BlockData.MainChain.GENESIS_BLOCK);
            final Block block01 = harness.inflateBlock(BlockData.MainChain.BLOCK_1);
            final Block block02 = harness.inflateBlock(BlockData.MainChain.BLOCK_2);
            final Block block03 = harness.inflateBlock(BlockData.MainChain.BLOCK_3);
            final Block invalidBlock01Prime = harness.inflateBlock("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D619000000000073387C6C752B492D7D6DA0CA48715EE10394683D4421B602E80B754657B2E0A79130D05DFFFF001DE339AB7E0201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC0000000002000000013BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A0000000000FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000");

            // Action
            harness.processBlock(genesisBlock, 0L, databaseManager);
            final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(genesisBlock.getHash());

            harness.processBlock(block01, 1L, databaseManager);
            final BlockId block01BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block01.getHash());

            harness.processBlock(block02, 2L, databaseManager);
            final BlockId block02BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block02.getHash());

            harness.processBlock(invalidBlock01Prime, 1L, databaseManager);
            final BlockId invalidBlock01PrimeBlockId = blockHeaderDatabaseManager.getBlockHeaderId(invalidBlock01Prime.getHash());

            harness.processBlock(block03, 3L, databaseManager);
            final BlockId block03BlockId = blockHeaderDatabaseManager.getBlockHeaderId(block03.getHash());

            // Assert
            final BlockchainSegmentId genesisBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId);
            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
            Assert.assertEquals(block03.getHash(), headBlockHash);
            Assert.assertEquals(block03BlockId, headBlockId);

            final BlockchainSegmentId invalidBlockBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(invalidBlock01PrimeBlockId);
            final Boolean forkChainsIsConnectedToGenesis = blockchainDatabaseManager.areBlockchainSegmentsConnected(invalidBlockBlockchainSegmentId, genesisBlockchainSegmentId, BlockRelationship.ANY);
            final Boolean forkChainsAreConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(headBlockchainSegmentId, invalidBlockBlockchainSegmentId, BlockRelationship.ANY);
            Assert.assertTrue(forkChainsIsConnectedToGenesis);
            Assert.assertNotEquals(headBlockchainSegmentId, invalidBlockBlockchainSegmentId);
            Assert.assertFalse(forkChainsAreConnected);

            final BlockchainSegmentId block01BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block01BlockId);
            final BlockchainSegmentId block02BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block02BlockId);
            final BlockchainSegmentId block03BlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(block03BlockId);
            final Boolean genesisAndBlock01AreConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(genesisBlockchainSegmentId, block01BlockchainSegmentId, BlockRelationship.ANY);
            final Boolean block01AndBlock02AreConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(block01BlockchainSegmentId, block02BlockchainSegmentId, BlockRelationship.ANY);
            final Boolean block02AndBlock03AreConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(block02BlockchainSegmentId, block03BlockchainSegmentId, BlockRelationship.ANY);
            final Boolean block03AndHeadBlockAreConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(block03BlockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);
            final Boolean genesisAndHeadChainIsConnected = blockchainDatabaseManager.areBlockchainSegmentsConnected(genesisBlockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);
            Assert.assertTrue(genesisAndBlock01AreConnected);
            Assert.assertTrue(block01AndBlock02AreConnected);
            Assert.assertTrue(block02AndBlock03AreConnected);
            Assert.assertTrue(block03AndHeadBlockAreConnected);
            Assert.assertTrue(genesisAndHeadChainIsConnected);
        }
    }

    // TODO: Create a test that attempts to spend an output that does not exist, and ensure that output is not added to the mempool (i.e. that block was "unApplied" to the mempool).
}
