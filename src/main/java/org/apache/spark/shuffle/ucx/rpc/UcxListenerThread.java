/*
 * Copyright (C) Mellanox Technologies Ltd. 2019. ALL RIGHTS RESERVED.
 * See file LICENSE for terms.
 */
package org.apache.spark.shuffle.ucx.rpc;

import org.apache.spark.shuffle.ucx.UcxNode;
import org.apache.spark.unsafe.Platform;
import org.openucx.jucx.ucp.UcpRequest;
import org.openucx.jucx.ucp.UcpWorker;

import java.nio.ByteBuffer;

/**
 * Thread for progressing global worker for connection establishment and RPC exchange.
 */
public class UcxListenerThread extends Thread implements Runnable {
  private UcxNode ucxNode;
  private boolean isDriver;
  private final UcpWorker globalWorker;

  public UcxListenerThread(UcxNode ucxNode, boolean isDriver) {
    this.ucxNode = ucxNode;
    this.isDriver = isDriver;
    this.globalWorker = ucxNode.getGlobalWorker();
    setDaemon(true);
    setName("UcxListenerThread");
  }

  /**
   * 2. Both Driver and Executor. Accept Recv request.
   * If on driver broadcast it to other executors. On executor just save worker addresses.
   */
  private UcpRequest recvRequest() {
    ByteBuffer metadataBuffer = Platform.allocateDirectBuffer(
      ucxNode.getConf().metadataRPCBufferSize());
    RpcConnectionCallback callback = new RpcConnectionCallback(metadataBuffer, isDriver, ucxNode);
    return globalWorker.recvTaggedNonBlocking(metadataBuffer, callback);
  }

  @Override
  public void run() {
    UcpRequest recv = recvRequest();
    while (!isInterrupted()) {
      if (recv.isCompleted()) {
        // Process 1 recv request at a time.
        recv = recvRequest();
      }
      if (globalWorker.progress() == 0) {
        globalWorker.waitForEvents();
      }
    }
    globalWorker.cancelRequest(recv);
    recv.close();
  }
}
