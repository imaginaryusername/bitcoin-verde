package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.NanoTimer;

public class CacheWarmer {
    public void warmUpCache(final MasterDatabaseManagerCache masterDatabaseManagerCache, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        try (final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);
                final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {

            { // Warm Up UTXO Cache...
                final Integer batchSize = (512 * 1024);
                Long lastRowId = Long.MAX_VALUE;

                while (lastRowId > 0) {
                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();

                    Long batchFirstRowId = null;
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT id, transaction_output_id, transaction_hash, `index` FROM unspent_transaction_outputs WHERE id < ? ORDER BY id DESC LIMIT " + batchSize)
                            .setParameter(lastRowId)
                    );
                    if (rows.isEmpty()) { break; }

                    for (final Row row : rows) {
                        final Long rowId = row.getLong("id");
                        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
                        final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("transaction_hash"));
                        final Integer transactionOutputIndex = row.getInteger("index");

                        localDatabaseManagerCache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
                        lastRowId = rowId;

                        if (batchFirstRowId == null) {
                            batchFirstRowId = rowId;
                        }
                    }

                    nanoTimer.stop();
                    Logger.log("Cached: " + batchFirstRowId + " - " + lastRowId + " (" + nanoTimer.getMillisecondsElapsed() + "ms)");
                }
            }

            masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseManagerCache);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            BitcoinUtil.exitFailure();
        }
    }
}
