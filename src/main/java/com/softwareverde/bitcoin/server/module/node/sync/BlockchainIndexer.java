package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;

public class BlockchainIndexer extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected static class OutputIndexData {
        TransactionId transactionId;
        Integer outputIndex;
        Long amount;
        ScriptType scriptType;
        AddressId addressId;
        TransactionId slpTransactionId;
    }

    protected static class InputIndexData {
        TransactionId transactionId;
        Integer inputIndex;
        AddressId addressId;
    }

    protected final TransactionOutputIndexerContext _context;
    protected final ScriptPatternMatcher _scriptPatternMatcher = new ScriptPatternMatcher();
    protected final SlpScriptInflater _slpScriptInflater = new SlpScriptInflater();

    protected Runnable _onSleepCallback;

    protected AddressId _getAddressId(final AtomicTransactionOutputIndexerContext context, final LockingScript lockingScript) throws ContextException {
        final ScriptType scriptType = _scriptPatternMatcher.getScriptType(lockingScript);
        final Address address = _scriptPatternMatcher.extractAddress(scriptType, lockingScript);
        if (address == null) { return null; }

        final AddressId addressId = context.getAddressId(address);
        if (addressId != null) { return addressId; }

        return context.storeAddress(address);
    }

    protected TransactionId _getSlpTokenTransactionId(final TransactionId transactionId, final SlpScript slpScript) throws ContextException {
        final SlpTokenId slpTokenId;
        switch (slpScript.getType()) {
            case GENESIS: {
                return transactionId;
            }

            case SEND: {
                final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                slpTokenId = slpSendScript.getTokenId();
            } break;

            case MINT: {
                final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                slpTokenId = slpMintScript.getTokenId();
            } break;

            case COMMIT: {
                final SlpCommitScript slpCommitScript = (SlpCommitScript) slpScript;
                slpTokenId = slpCommitScript.getTokenId();
            } break;

            default: {
                slpTokenId = null;
            } break;
        }

        if (slpTokenId == null) { return null; }

        try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
            return context.getTransactionId(slpTokenId);
        }
    }

    protected List<InputIndexData> _indexTransactionInputs(final AtomicTransactionOutputIndexerContext context, final TransactionId transactionId, final Transaction transaction) throws ContextException {
        final MutableList<InputIndexData> inputIndexDataList = new MutableList<InputIndexData>();

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final int transactionInputCount = transactionInputs.getCount();
        for (int inputIndex = 0; inputIndex < transactionInputCount; ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            { // Avoid indexing Coinbase Inputs...
                final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                if (Util.areEqual(TransactionOutputIdentifier.COINBASE, previousTransactionOutputIdentifier)) {
                    continue;
                }
            }

            final TransactionId previousTransactionId = context.getTransactionId(previousTransactionHash);
            final Transaction previousTransaction = context.getTransaction(previousTransactionId);
            if (previousTransaction == null) {
                Logger.debug("Cannot index input; Transaction does not exist: " + previousTransactionHash);
                continue;
            }

            final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
            final TransactionOutput previousTransactionOutput = previousTransactionOutputs.get(previousTransactionOutputIndex);
            final LockingScript lockingScript = previousTransactionOutput.getLockingScript();

            final AddressId addressId = _getAddressId(context, lockingScript);

            final InputIndexData inputIndexData = new InputIndexData();
            inputIndexData.transactionId = transactionId;
            inputIndexData.inputIndex = inputIndex;
            inputIndexData.addressId = addressId;

            inputIndexDataList.add(inputIndexData);
        }

        return inputIndexDataList;
    }

    protected Map<TransactionOutputIdentifier, OutputIndexData> _indexTransactionOutputs(final AtomicTransactionOutputIndexerContext context, final TransactionId transactionId, final Transaction transaction) throws ContextException {
        final HashMap<TransactionOutputIdentifier, OutputIndexData> outputIndexData = new HashMap<TransactionOutputIdentifier, OutputIndexData>();

        final Sha256Hash transactionHash = transaction.getHash();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final int transactionOutputCount = transactionOutputs.getCount();

        final LockingScript slpLockingScript;
        {
            final TransactionOutput transactionOutput = transactionOutputs.get(0);
            slpLockingScript = transactionOutput.getLockingScript();
        }

        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            if (! outputIndexData.containsKey(transactionOutputIdentifier)) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                final OutputIndexData indexData = new OutputIndexData();
                indexData.transactionId = transactionId;
                indexData.outputIndex = outputIndex;
                indexData.amount = transactionOutput.getAmount();
                indexData.scriptType = ScriptType.UNKNOWN;
                indexData.addressId = _getAddressId(context, lockingScript);
                indexData.slpTransactionId = null;

                outputIndexData.put(transactionOutputIdentifier, indexData);
            }
        }

        final ScriptType scriptType = _scriptPatternMatcher.getScriptType(slpLockingScript);
        if (! ScriptType.isSlpScriptType(scriptType)) {
            return outputIndexData;
        }

        boolean slpTransactionIsValid;
        { // Validate SLP Transaction...
            // NOTE: Inflating the whole transaction is mildly costly, but typically this only happens once per SLP transaction, which is required anyway.
            final SlpScript slpScript = _slpScriptInflater.fromLockingScript(slpLockingScript);

            slpTransactionIsValid = ( (slpScript != null) && (transactionOutputCount >= slpScript.getMinimumTransactionOutputCount()) );

            if (slpTransactionIsValid) {
                ScriptType outputScriptType = ScriptType.CUSTOM_SCRIPT;
                final TransactionId slpTokenTransactionId = _getSlpTokenTransactionId(transactionId, slpScript);

                switch (slpScript.getType()) {
                    case GENESIS: {
                        final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) slpScript;
                        final Integer generatorOutputIndex = slpGenesisScript.getBatonOutputIndex();

                        if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputCount)) {
                            slpTransactionIsValid = false;
                        }
                        else {
                            outputScriptType = ScriptType.SLP_GENESIS_SCRIPT;

                            { // Mark the Receiving Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }

                            if (generatorOutputIndex != null) {
                                // Mark the Mint Baton Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, generatorOutputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case MINT: {
                        final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                        final Integer generatorOutputIndex = slpMintScript.getBatonOutputIndex();

                        if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputCount)) {
                            slpTransactionIsValid = false;
                        }
                        else {
                            outputScriptType = ScriptType.SLP_MINT_SCRIPT;

                            { // Mark the Receiving Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }

                            if (generatorOutputIndex != null) {
                                // Mark the Mint Baton Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, generatorOutputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case SEND: {
                        final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
                            final Long slpAmount = Util.coalesce(slpSendScript.getAmount(outputIndex));

                            if (slpAmount > 0L) {
                                outputScriptType = ScriptType.SLP_SEND_SCRIPT;

                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case COMMIT: {
                        outputScriptType = ScriptType.SLP_COMMIT_SCRIPT;
                    } break;
                }

                { // Update the OutputIndexData for the first TransactionOutput...
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, 0);
                    final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                    indexData.scriptType = outputScriptType;

                    if (slpTransactionIsValid) {
                        indexData.slpTransactionId = slpTokenTransactionId;
                    }
                }
            }
        }

        return outputIndexData;
    }

    public BlockchainIndexer(final TransactionOutputIndexerContext context) {
        _context = context;
    }

    @Override
    protected void _onStart() {
        Logger.trace("AddressProcessor Starting.");
    }

    @Override
    protected Boolean _run() {
        Logger.trace("AddressProcessor Running.");

        try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
            final MilliTimer processTimer = new MilliTimer();

            int inputCount = 0;
            int outputCount = 0;
            processTimer.start();
            final List<TransactionId> queuedTransactionIds = context.getUnprocessedTransactions(BATCH_SIZE);
            if (queuedTransactionIds.isEmpty()) { return false; }

            for (final TransactionId transactionId : queuedTransactionIds) {
                final Transaction transaction = context.getTransaction(transactionId);
                if (transaction == null) {
                    Logger.debug("Unable to inflate Transaction for address processing: " + transactionId);
                    return false;
                }

                final Map<TransactionOutputIdentifier, OutputIndexData> outputIndexData = _indexTransactionOutputs(context, transactionId, transaction);
                for (final OutputIndexData indexData : outputIndexData.values()) {
                    context.indexTransactionOutput(indexData.transactionId, indexData.outputIndex, indexData.amount, indexData.scriptType, indexData.addressId, indexData.slpTransactionId);
                    outputCount += 1;
                }

                final List<InputIndexData> inputIndexDataList = _indexTransactionInputs(context, transactionId, transaction);
                for (final InputIndexData inputIndexData : inputIndexDataList) {
                    context.indexTransactionInput(inputIndexData.transactionId, inputIndexData.inputIndex, inputIndexData.addressId);
                    inputCount += 1;
                }
            }

            context.dequeueTransactionsForProcessing(queuedTransactionIds);
            context.commitDatabaseTransaction();
            processTimer.stop();

            Logger.info("Indexed " + inputCount + " Inputs, " + outputCount + " Outputs in " + processTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }

        Logger.trace("AddressProcessor Stopping.");
        return true;
    }

    @Override
    protected void _onSleep() {
        Logger.trace("AddressProcessor Sleeping.");
        final Runnable onSleepCallback = _onSleepCallback;
        if (onSleepCallback != null) {
            onSleepCallback.run();
        }
    }

    public void setOnSleepCallback(final Runnable onSleepCallback) {
        _onSleepCallback = onSleepCallback;
    }
}