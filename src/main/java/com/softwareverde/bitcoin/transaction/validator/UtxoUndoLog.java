package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;

public class UtxoUndoLog {
    protected final FullNodeDatabaseManager _databaseManager;
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _reAvailableOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
    protected final HashSet<TransactionOutputIdentifier> _uncreatedOutputs = new HashSet<TransactionOutputIdentifier>();

    public UtxoUndoLog(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public void undoBlock(final Block block) throws DatabaseException {
        Logger.debug("Undoing Block: " + block.getHash());
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();

            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final TransactionOutput transactionOutput = transactionDatabaseManager.getTransactionOutput(transactionOutputIdentifier);
                    _reAvailableOutputs.put(transactionOutputIdentifier, transactionOutput);
                }
            }

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                _uncreatedOutputs.add(transactionOutputIdentifier);
                outputIndex += 1;
            }

            isCoinbase = false;
        }
    }

    public void redoBlock(final Block block) {
        Logger.debug("Redoing Block: " + block.getHash());
        final List<Transaction> transactions = block.getTransactions();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();

            if (! isCoinbase) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    _reAvailableOutputs.remove(transactionOutputIdentifier);
                }
            }

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            int outputIndex = 0;
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                _uncreatedOutputs.remove(transactionOutputIdentifier);
                outputIndex += 1;
            }

            isCoinbase = false;
        }
    }

    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        if (_uncreatedOutputs.contains(transactionOutputIdentifier)) { return null; }

        return _reAvailableOutputs.get(transactionOutputIdentifier);
    }
}
