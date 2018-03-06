package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.runner.Context;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import org.junit.Assert;
import org.junit.Test;

public class TransactionSignerTests {
    @Test
    public void should_create_hash_for_signing() {
        // Example taken from: https://bitcoin.stackexchange.com/questions/3374/how-to-redeem-a-basic-tx

        // Setup
        final String expectedHashToSign = "9302BDA273A887CB40C13E02A50B4071A31FD3AAE3AE04021B0B843DD61AD18E";

        final Script outputBeingSpentLockingScript = new ImmutableScript(BitcoinUtil.hexStringToByteArray("76A914010966776006953D5567439E5E39F86A0D273BEE88AC"));
        final Script newOutputLockingScript = new ImmutableScript(BitcoinUtil.hexStringToByteArray("76A914097072524438D003D23A2F23EDB65AAE1BB3E46988AC"));

        final MutableTransactionOutput transactionOutputBeingSpent = new MutableTransactionOutput();
        transactionOutputBeingSpent.setIndex(1);
        transactionOutputBeingSpent.setAmount(100000000L);
        transactionOutputBeingSpent.setLockingScript(outputBeingSpentLockingScript);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();
        transactionInput.setPreviousTransactionOutputHash(new ImmutableHash(BitcoinUtil.hexStringToByteArray("F2B3EB2DEB76566E7324307CD47C35EEB88413F971D88519859B1834307ECFEC")));
        transactionInput.setPreviousTransactionOutputIndex(1);
        transactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
        transactionInput.setUnlockingScript(Script.EMPTY_SCRIPT);
        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(1);
        transaction.addTransactionInput(transactionInput);
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setLockingScript(newOutputLockingScript);
        transactionOutput.setAmount(99900000L);
        transactionOutput.setIndex(0);
        transaction.addTransactionOutput(transactionOutput);
        transaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));

        final TransactionSigner transactionSigner = new TransactionSigner();
        final SignatureContext signatureContext = new SignatureContext(transaction, 0, transactionOutputBeingSpent);

        // Action
        final byte[] bytesForSigning = transactionSigner._getBytesForSigning(signatureContext, ScriptSignature.HashType.SIGNATURE_HASH_ALL);

        // Assert
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(expectedHashToSign), bytesForSigning);
    }

    @Test
    public void should_verify_signed_transaction() {
        // Example taken from: https://bitcoin.stackexchange.com/questions/32628/redeeming-a-raw-transaction-step-by-step-example-required

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transactionBeingSpent = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000000175DB462B20DD144DD143F5314270569C0A61191F1378C164CE4262E9BFF1B079000000008B4830450221008F906B9FE728CB17C81DECCD6704F664ED1AC920223BB2ECA918F066269C703302203B1C496FD4C3FA5071262B98447FBCA5E3ED7A52EFE3DA26AA58F738BD342D31014104BCA69C59DC7A6D8EF4D3043BDCB626E9E29837B9BEB143168938AE8165848BFC788D6FF4CDF1EF843E6A9CCDA988B323D12A367DD758261DD27A63F18F56CE77FFFFFFFF0133F50100000000001976A914DD6CCE9F255A8CC17BDA8BA0373DF8E861CB866E88AC00000000"));
        final Transaction transaction = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000001BE66E10DA854E7AEA9338C1F91CD489768D1D6D7189F586D7A3613F2A24D5396000000008B483045022100DA43201760BDA697222002F56266BF65023FEF2094519E13077F777BAED553B102205CE35D05EABDA58CD50A67977A65706347CC25EF43153E309FF210A134722E9E0141042DAA93315EEBBE2CB9B5C3505DF4C6FB6CACA8B756786098567550D4820C09DB988FE9997D049D687292F815CCD6E7FB5C1B1A91137999818D17C73D0F80AEF9FFFFFFFF0123CE0100000000001976A9142BC89C2702E0E618DB7D59EB5CE2F0F147B4075488AC00000000"));

        final ScriptRunner scriptRunner = new ScriptRunner();
        final Context context = new Context();
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int inputIndex=0; inputIndex<transactionInputs.getSize(); ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(inputIndex);

            context.setTransactionInputIndex(inputIndex);
            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutputBeingSpent);

            final Script lockingScript = transactionOutputBeingSpent.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();

            // Action
            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

            // Assert
            Assert.assertTrue(inputIsUnlocked);
        }
    }
}