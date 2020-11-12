package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.network.ip.Ip;

public class NodeHandler implements NodeRpcHandler.NodeHandler {
    protected final BitcoinNodeManager _nodeManager;
    protected final BitcoinNodeFactory _bitcoinNodeFactory;

    public NodeHandler(final BitcoinNodeManager nodeNodeManager, final BitcoinNodeFactory bitcoinNodeFactory) {
        _nodeManager = nodeNodeManager;
        _bitcoinNodeFactory = bitcoinNodeFactory;
    }

    @Override
    public void addNode(final Ip ip, final Integer port) {
        if ( (ip == null) || (port == null) ) { return; }
        if ( (port <= 0) || (port > 65535) ) { return; }

        final String ipString = ip.toString();

        final BitcoinNode bitcoinNode = _bitcoinNodeFactory.newNode(ipString, port);
        _nodeManager.addNode(bitcoinNode);
    }

    @Override
    public List<BitcoinNode> getNodes() {
        return _nodeManager.getNodes();
    }

    @Override
    public void banNode(final Ip ip) {
        _nodeManager.banNode(ip);
    }

    @Override
    public void unbanNode(final Ip ip) {
        _nodeManager.unbanNode(ip);
    }

    @Override
    public void addIpToWhitelist(final Ip ip) {
        _nodeManager.addToWhitelist(ip);
    }

    @Override
    public void removeIpFromWhitelist(final Ip ip) {
        _nodeManager.removeIpFromWhitelist(ip);
    }
}
