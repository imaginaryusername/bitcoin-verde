package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;

public class ScriptDeflater {
    public MutableByteArray toBytes(final Script script) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        for (final Operation operation : script.getOperations()) {
            byteArrayBuilder.appendBytes(operation.getBytes());
        }
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public String toString(final Script script) {
        final List<Operation> scriptOperations = script.getOperations();
        if (scriptOperations == null) { return null; }

        final StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<scriptOperations.getSize(); ++i) {
            final Operation operation = scriptOperations.get(i);
            stringBuilder.append("(");
            stringBuilder.append(operation.toString());
            stringBuilder.append(")");

            if (i + 1 < scriptOperations.getSize()) {
                stringBuilder.append(" ");
            }
        }
        return stringBuilder.toString();
    }

    public Json toJson(final Script script) {
        final ByteArray scriptByteArray = script.getBytes();

        final Json json = new Json();
        json.put("bytes", scriptByteArray);

        final Json operationsJson = new Json();
        final List<Operation> operations = script.getOperations();
        for (final Operation operation : operations) {
            operationsJson.add(operation);
        }
        json.put("operations", operationsJson);

        return json;
    }
}