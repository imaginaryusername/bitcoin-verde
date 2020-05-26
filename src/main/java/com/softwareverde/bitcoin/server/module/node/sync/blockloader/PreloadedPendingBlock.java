package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;

public interface PreloadedPendingBlock {
    PendingBlock getPendingBlock();
    MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet();
}
