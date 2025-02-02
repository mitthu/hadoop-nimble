/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.datanode.extdataset;

import org.apache.hadoop.hdfs.server.common.HdfsServerConstants.ReplicaState;
import org.apache.hadoop.hdfs.server.datanode.Replica;

public class ExternalReplica implements Replica {

  @Override
  public long getBlockId() {
    return 0;
  }

  @Override
  public long getGenerationStamp() {
    return 0;
  }

  @Override
  public byte[] getChecksum() {
    return null;
  }

  @Override
  public void setChecksum(byte[] ck) { }

  @Override
  public ReplicaState getState() {
    return ReplicaState.FINALIZED;
  }

  @Override
  public long getNumBytes() {
    return 0;
  }

  @Override
  public long getBytesOnDisk() {
    return 0;
  }

  @Override
  public long getVisibleLength() {
    return 0;
  }

  @Override
  public String getStorageUuid() {
    return null;
  }

  @Override
  public boolean isOnTransientStorage() {
    return false;
  }
}
