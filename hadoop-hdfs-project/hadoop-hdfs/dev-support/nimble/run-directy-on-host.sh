#!/bin/bash
# Run script from top-level/root of the repo.
#
# Compiles only Nimble class and runs it.
#
# Depends on jars created by full compilation of Hadoop.
# These are located at: hadoop-dist/target/hadoop-3.3.3

set -e
SAVED_PWD=`pwd`

# Compile
rm -rf /tmp/hadoop
mkdir /tmp/hadoop
javac -d /tmp/hadoop \
  -cp 'hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3/share/hadoop/hdfs/lib/*:hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' \
  hadoop-hdfs-project/hadoop-hdfs/src/main/java/org/apache/hadoop/hdfs/Nimble.java

# Run
cd /tmp/hadoop
ln -s "$SAVED_PWD/hadoop-hdfs-project" hadoop-hdfs-project
ln -s "$SAVED_PWD/hadoop-dist" hadoop-dist
java -cp '/tmp/hadoop:hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3/share/hadoop/hdfs/lib/*:hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' \
  org.apache.hadoop.hdfs.Nimble
