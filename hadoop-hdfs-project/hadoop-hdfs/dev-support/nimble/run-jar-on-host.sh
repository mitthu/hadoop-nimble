#!/bin/bash
# Run from top-level/root of the repo.
#
# Depends on jars created by full compilation of Hadoop.
# These are located at: hadoop-dist/target/hadoop-3.3.3

java -cp 'hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3/share/hadoop/hdfs/lib/*:hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' org.apache.hadoop.hdfs.server.nimble.NimbleTester
