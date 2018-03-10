package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class SignatureContextGenerator {
    // Reference: https://en.bitcoin.it/wiki/OP_CHECKSIG

    private final TransactionDatabaseManager _transactionDatabaseManager;
    private final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;

    public SignatureContextGenerator(final MysqlDatabaseConnection databaseConnection) {
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
    }

    public SignatureContextGenerator(final TransactionDatabaseManager transactionDatabaseManager, final TransactionOutputDatabaseManager transactionOutputDatabaseManager) {
        _transactionDatabaseManager = transactionDatabaseManager;
        _transactionOutputDatabaseManager = transactionOutputDatabaseManager;
    }

    public SignatureContext createContextForEntireTransaction(final Transaction transaction) throws DatabaseException {
        final SignatureContext signatureContext = new SignatureContext(transaction, ScriptSignature.HashType.SIGNATURE_HASH_ALL);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final Long transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionInput.getPreviousTransactionOutputHash());
            final Long transactionOutputId = _transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionInput.getPreviousTransactionOutputIndex());
            final TransactionOutput transactionOutput = _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);

            signatureContext.setShouldSignInput(i, true, transactionOutput);
        }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        for (int i=0; i<transactionOutputs.getSize(); ++i) {
            signatureContext.setShouldSignOutput(i, true);
        }

        return signatureContext;
    }

    public SignatureContext createContextForSingleOutputAndAllInputs(final Transaction transaction, final Integer outputIndex) throws DatabaseException {
        return null; // TODO...
    }

    public SignatureContext createContextForAllInputsAndNoOutputs(final Transaction transaction) throws DatabaseException {
        return null; // TODO...
    }
}
