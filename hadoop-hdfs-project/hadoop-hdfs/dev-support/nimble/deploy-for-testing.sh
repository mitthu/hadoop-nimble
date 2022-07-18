#!/bin/bash
VERSION=3.3.3
JARFILE=hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-${VERSION}.jar
JARFILE_CLIENT=hadoop-hdfs-project/hadoop-hdfs-client/target/hadoop-hdfs-client-${VERSION}.jar
JARFILE_NATIVE_CLIENT=hadoop-hdfs-project/hadoop-hdfs-native-client/target/hadoop-hdfs-native-client-${VERSION}.jar
JARFILE_HTTPFS=hadoop-hdfs-project/hadoop-hdfs-httpfs/target/hadoop-hdfs-httpfs-${VERSION}.jar
JARFILE_NFS=hadoop-hdfs-project/hadoop-hdfs-nfs/target/hadoop-hdfs-nfs-${VERSION}.jar
JARFILE_RBF=hadoop-hdfs-project/hadoop-hdfs-rbf/target/hadoop-hdfs-rbf-${VERSION}.jar
TARGET=opt/hadoop-${VERSION}/share/hadoop/hdfs/

function containers() {
  # Push files to lxc containers
  lxc file push $JARFILE ${JARFILE_CLIENT} ${JARFILE_NATIVE_CLIENT} ${JARFILE_HTTPFS} ${JARFILE_NFS} ${JARFILE_RBF} namenode/${TARGET}
  lxc file push $JARFILE ${JARFILE_CLIENT} ${JARFILE_NATIVE_CLIENT} ${JARFILE_HTTPFS} ${JARFILE_NFS} ${JARFILE_RBF} datanode/${TARGET}

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
}

function standalone() {
  # Update JAR
  cp $JARFILE ${JARFILE_CLIENT} ${JARFILE_NATIVE_CLIENT} ${JARFILE_HTTPFS} ${JARFILE_NFS} ${JARFILE_RBF} /${TARGET}

  # Stop services
  hdfs --daemon stop namenode
  hdfs --daemon stop datanode

  # Start services
  hdfs --daemon start namenode
  hdfs --daemon start datanode

  # Troubleshooting: Namenode does not start
  # Why? Due to incorrect shutdown
  # hdfs namenode -format
  # rm -rf /tmp/hadoop-root/dfs/data
}

# Run containers or standalone?
case $1 in
containers)
  containers
  ;;

standalone)
  standalone
  ;;

*)
  containers
  ;;
esac
