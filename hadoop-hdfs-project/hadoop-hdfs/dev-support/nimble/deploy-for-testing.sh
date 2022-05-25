#!/bin/bash
VERSION=3.3.3
JARFILE=hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-${VERSION}.jar
TARGET=opt/hadoop-${VERSION}/share/hadoop/hdfs/hadoop-hdfs-${VERSION}.jar

# Push files to lxc containers
lxc file push $JARFILE namenode/${TARGET}
lxc file push $JARFILE datanode/${TARGET}

# Stop services
lxc exec namenode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-$VERSION/bin/hdfs --daemon stop namenode
lxc exec datanode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-$VERSION/bin/hdfs --daemon stop datanode

# Start services
lxc exec namenode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-$VERSION/bin/hdfs --daemon start namenode
lxc exec datanode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-$VERSION/bin/hdfs --daemon start datanode

# Troubleshooting: Namenode does not start
# Why? Due to incorrect shutdown
# lxc exec namenode --env JAVA_HOME=/usr/lib/jvm/default-java -- /opt/hadoop-3.3.3/bin/hdfs namenode -format
# lxc exec datanode -- rm -rf /tmp/hadoop-root/dfs/data
