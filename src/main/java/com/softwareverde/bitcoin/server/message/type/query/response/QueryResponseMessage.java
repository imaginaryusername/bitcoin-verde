package com.softwareverde.bitcoin.server.message.type.query.response;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class QueryResponseMessage extends ProtocolMessage {

    private final List<DataHash> _dataHashes = new ArrayList<DataHash>();

    public QueryResponseMessage() {
        super(MessageType.QUERY_RESPONSE);
    }

    public List<DataHash> getDataHashes() {
        return Util.copyList(_dataHashes);
    }

    public void addInventoryItem(final DataHash dataHash) {
        _dataHashes.add(dataHash);
    }

    public void clearInventoryItems() {
        _dataHashes.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_dataHashes.size()), Endian.LITTLE);
        for (final DataHash dataHash : _dataHashes) {
            byteArrayBuilder.appendBytes(dataHash.getBytes(), Endian.BIG);
        }
        return new MutableByteArray(byteArrayBuilder.build());
    }
}
