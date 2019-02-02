package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.stratum.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.ReflectionUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class StratumModuleTests {
    @Test
    public void should_mine_valid_prototype_block() {
        // Setup
        final StratumModulePartialMock stratumModule = new StratumModulePartialMock();

        stratumModule.queueFakeJsonResponse(Json.parse("{\"blockHeaders\":[\"00000020491C708BADFD38F0B38A6EDD6FDD949C9A4D109C037EAB010000000000000000093CC8AEF901A2BF304DC6949439AAC6942F75BE376E14E7C38EEA8F0D2B696360C6555C3C9B051848B94556\"],\"method\":\"RESPONSE\",\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumModule.queueFakeJsonResponse(Json.parse("{\"method\":\"RESPONSE\",\"blockHeight\":568009,\"blockHeaderHeight\":568009,\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumModule.queueFakeJsonResponse(Json.parse("{\"difficulty\":\"180597F0\",\"method\":\"RESPONSE\",\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumModule.queueFakeJsonResponse(Json.parse("{\"method\":\"RESPONSE\",\"errorMessage\":null,\"blockReward\":1250000000,\"wasSuccess\":1}"));
        stratumModule.queueFakeJsonResponse(Json.parse("{\"unconfirmedTransactions\":[{\"transactionFee\":243,\"transaction\":\"0100000001D0ADDDF36837842BF09B8C699DABB01923D6CA5224975863880637E4BFA355ED010000006A47304402203AF85025DBC1EC2318C9C463041502F61115B7BB002373DD6A88A2E3D7ED2D01022001C6E527F76508CA9C76ACF7EDBF5B00049BA1A96CD77A20EEBAB41D35AC4CF1412102F7A672CD7516D4E76D34D68DB1492DD5DD7121DDBD6FAE6FAC49F431BF36E26AFFFFFFFF0289CE0200000000001976A9147A12CF7834A18154377FD50A38C78E7725C0486F88ACAA3B0300000000001976A9148B80501D56D20C1BF8AA94F25CE9EAFADE545E0E88AC00000000\"},{\"transactionFee\":390,\"transaction\":\"010000000226D4509F9709D74EE7CB4EEE608280CCAC94CB5B259B2DC9C8083A81A42189CD000000006A47304402207FBB1AA623F2F1DB9EE78666BF8150C74883494E5F60AA88E88162AED3D5AAD002204C4E933B791E1332A4518FA2B19B1FC941E6A7C761C97A0296BB013A72A0D314412102353C7984935825F70C60ECF0543892CEFED310B55629E1E5C9A4D23685DD7DE5FFFFFFFF6DEC6DB7021E83B5470D73263854F5B7A609AFB21EF05F2498A418C98F18226B010000006B483045022100DB11AF4F4731D8B453E87BCEED56A28DBFE35FBFE99666B2B8FE546BED32828802207F7A89655E24D60FA8189B4D9EE049590616C3AB8B5D0E588BB272C03D543D8C412103788A855A936EA41414713D0D4F83B20DE49B9D892D4BB3AB52AED8EA2A9E32A1FFFFFFFF021DB90900000000001976A9148B80506A2D60464710BD359AC82400C2BF23E5FE88AC260B0600000000001976A914DB77613B90CA4FFC8DF46422B0021854B9B4C9AE88AC00000000\"},{\"transactionFee\":280,\"transaction\":\"0100000001970E0DEF3A463DB2CD124FCE1CEC2B0A09129B4D7DC50F1C51A3BB9EB2B4A410000000006B483045022100883CBCBC0781CCF1F4DA8C40E7887B4E863661CC11A83C9C34C0F028AA2150E0022053B2F7DB5362D22BBC7857600B594834F6D8440081784481FE87993A4566772741210337433E3CD5B7B46006D7DF31261FB1EC8679E116072357FDDC05D491E05E7ADFFFFFFFFF020000000000000000216A040101010104343339301501DB673B6C83CF1BEB747A8F5C2AE7FBC50F6A0D001A290200000000001976A9149A43A4319077B6DB6B3F8EC27E756A75F41672CC88AC00000000\"}],\"method\":\"RESPONSE\",\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumModule.queueFakeJsonResponse(Json.parse("{\"method\":\"RESPONSE\",\"errorMessage\":\"\",\"wasSuccess\":0}")); // Fail the ADD_HOOK upgrade since the socket is not real...

        final StratumMineBlockTask stratumMineBlockTask = stratumModule.createStratumMineBlockTask();

        // Action
        final Block block = stratumMineBlockTask.assembleBlock("00000000", "00000000", "00000000");

        // Assert
        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(4, transactions.getSize());

        // Enforce LTOR...
        Assert.assertEquals(Sha256Hash.fromHexString("09DB13063DF69B27786D01A7397A46D46FE3D780A0BDDE433CDC89DA7DBFFAF8"), transactions.get(1).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("C44A58ECAC5A526218BE2AC26F246C0880E7C14C3FD82DB4ED074B63D948F83F"), transactions.get(2).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("ED55A3BFE43706886358972452CAD62319B0AB9D698C9BF02B843768F3DDADD0"), transactions.get(3).getHash());

        Assert.assertEquals(Sha256Hash.fromHexString("0000000000000000031D4DC02DF126D9C1130EAC699BC4C8E3F70767042FE72D"), block.getPreviousBlockHash());
        Assert.assertEquals(0L, block.getTimestamp().longValue());
        Assert.assertEquals(Difficulty.decode(HexUtil.hexStringToByteArray("180597F0")), block.getDifficulty());
        Assert.assertEquals(0L, block.getNonce().longValue());
        Assert.assertEquals((1250000000L + 243L + 390L + 280L), block.getCoinbaseTransaction().getTransactionOutputs().get(0).getAmount().longValue());

        final UnlockingScript unlockingScript = block.getCoinbaseTransaction().getCoinbaseScript();
        final List<Operation> operations = unlockingScript.getOperations();
        Assert.assertEquals(Long.valueOf(568010L), ((PushOperation) operations.get(0)).getValue().asLong()); // Enforce BlockHeight within coinbase...
        Assert.assertEquals(Constants.COINBASE_MESSAGE, ((PushOperation) operations.get(1)).getValue().asString()); // Enforce coinbase message...
        Assert.assertEquals(stratumMineBlockTask.getExtraNonce(), HexUtil.toHexString(((PushOperation) operations.get(2)).getValue().getBytes(0, 4))); // Enforce extraNonce...
        Assert.assertEquals("00000000", HexUtil.toHexString(((PushOperation) operations.get(2)).getValue().getBytes(4, 4))); // Enforce extraNonce2...
    }
}

class FakeStratumServerSocket extends StratumServerSocket {

    public FakeStratumServerSocket() {
        super(null, null);
    }

    @Override
    public void start() {
        // Nothing.
    }
}

class StratumModulePartialMock extends StratumModule {
    protected static File TEMP_FILE;
    static {
        try {
            TEMP_FILE = File.createTempFile("tmp", ".dat");
            TEMP_FILE.deleteOnExit();
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }
    }

    protected final MutableList<Json> _fakeJsonResponses = new MutableList<Json>();

    public StratumModulePartialMock() {
        super(TEMP_FILE.getPath());

        ReflectionUtil.setValue(this, "_stratumServerSocket", new FakeStratumServerSocket());
    }

    @Override
    protected NodeJsonRpcConnection _getNodeJsonRpcConnection() {
        return new NodeJsonRpcConnection(null, null) {
            @Override
            protected Json _executeJsonRequest(final Json rpcRequestJson) {
                System.out.println("Stratum Sent: " + rpcRequestJson.toString());

                final Json jsonResponse = _fakeJsonResponses.remove(0);
                System.out.println("Stratum Received: " + jsonResponse.toString());
                return jsonResponse;
            }
        };
    }

    public void queueFakeJsonResponse(final Json json) {
        _fakeJsonResponses.add(json);
    }

    public StratumMineBlockTask createStratumMineBlockTask() {
        return _createNewMiningTask(_privateKey);
    }
}