# Nimble-aware Hadoop

Compiling and installing Nimble-aware Hadoop (referred to as Hadoop from now on) is exactly the same as vanilla Hadoop. The primary difference is the extra configuration for Nimble. This guide is structured as:

* Compile
* Deploy
* Benchmark
* Troubleshooting

Hadoop has two kinds of nodes: Namenode and Datanode. The Namenode maintains the structure of the file system and all the metadata. The Datanode(s) hold the actual block that make up the files. For our setup, we will run two nodes: one Namenode and one Datanode.

The compilation can be done on any machine. It creates a single tar.gz archive containing both the Namenode and Datanode binaries. The target machine on which Hadoop will be run only requires the Java runtime and the tar.gz archive. We use Ubuntu 22.04 for our tasks.

`Note`: To benchmark, you need to also compile the upstream (or vanilla) Hadoop version 3.3.3. The compilation steps are identical to that of Nimble-aware Hadoop.


## Compile

### Pre-requisites
Install Java and Apache Maven:
```bash
sudo apt update
sudo apt install openjdk-8-jdk maven
```

Hadoop only supports compilation using JDK 8. Ensure that you're using OpenJDK version 1.8.x using:

```bash
java -version
```


### Compile Hadoop
We want to compile the entire Hadoop distribution.

```bash
# cd <repo-top-level>
mvn package -Pdist -DskipTests -Dtar -Dmaven.javadoc.skip=true
```

The compilation will generate the final archive at `hadoop-dist/target/hadoop-3.3.3.tar.gz`.

## Deploy

You need to repeat the below steps for each machine that will run Hadoop. In our case, we need two machines.

### Install

```bash
# Pre-requisites
sudo apt update
sudo apt install -y default-jre

# Setup environment variables
export JAVA_HOME=/usr/lib/jvm/default-java
export PATH=/opt/hadoop-3.3.3/bin:$PATH
```

Copy the archive located at  `hadoop-dist/target/hadoop-3.3.3.tar.gz` to the  machine where you want to install Hadoop. Then do the following:

```bash
# Unpack archive
sudo tar -xvf hadoosudo mv /opt/hadoop-3.3.3/
sudo /opt/hadoop-nimblep-project-dist-3.3.3.tar.gz -C /opt/
```

### Configure

Add necessary configuration to run Hadoop on both the Namenode and Datanode. Use:

```bash
echo "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>
<configuration>
	<property>
		<name>fs.defaultFS</name>
		<value>hdfs://<namenodeip>:9000</value>
	</property>
	<property>
		<name>fs.nimbleURI</name>
		<value>http://<nimbleip>:8082/</value>
	</property>
	<property>
		<name>fs.nimble.batchSize</name>
		<value>10</value>
	</property>
</configuration>
" | sudo tee /opt/hadoop-nimble/etc/hadoop/core-site.xml
```

Replace `namenodeip` with network accessible IP address of the machine that will run the Namenode. Replace `nimbleURI` with the IP address running the Nimble REST endpoint.

You can access Namenode's web UI from `http://namenodeip:9870`.

Configuration options:

fs.defaultFS
: Endpoint for this HDFS file system. All 

fs.nimbleURI
: URL of NimbleLedger's REST endpoint

fs.nimble.batchSize
: Number of operations to batch before incrementing the counter


**Optional**:

fs.nimble.service.id
: Expect this identity for NimbleLedger (against "/serviceid"). It is base64url encoded.

fs.nimble.service.publicKey
: Expected this public Key for NimbleLedger. It is base64url encoded.

fs.nimble.service.handle
: Ledger handle (or name) to use for formatting and reporting. It is base64url encoded.


### Run

On Namenode:
```bash
# Format namenode (needed once)
hdfs namenode -format

# Start Namenode
hdfs --daemon start namenode
```


On Datanode:
```
Start Datanode
hdfs --daemon start datanode
```

Logs are inside `/opt/hadoop-namenode/logs`. To quickly wipe the existing installtion and start over again, see Troublesooting -- "Reset installation".


### Verify
Formatting the file system, produces for a message like:

```
2022-10-28 02:27:17,586 INFO nimble.TMCS: Formatted TMCS: NimbleServiceID{identity=C9JtOpmXyBd-anyeBbhr5RZ0ac2urm5Nt-z_C88wfvU, publicKey=A8ABjZekZrccR7eq7ASkDNt0689wd3klvWUW6wIzYmJz, handle=yfQi-y8v4k1GIfgjaKon7w, signPublicKey=MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEXNoPgT2QAqTPaTJk0wowGyl4fQx7UywYJoqaR8UyARHgJGld6QOaH3mv1OQYIKwZNb3fBr7gPMM7LypIWbNNbQ, signPrivateKey=MD4CAQAwEAYHKoZIzj0CAQYFK4EEAAoEJzAlAgEBBCBzv_4v64jTXxngYxfDnFQAG-3M8rhc5heXrB9sSnGcmw}
```


We will test our instalation using Hadoop WebHDFS which exposes a RESTful HTTP API.

```bash
# Set the HDFS user
USER=root

# Change perms
curl -i -X PUT "http://namenode.lxd:9870/webhdfs/v1/?op=SETPERMISSION&permission=777&user.name=$USER"

# CREATE file (single block)
curl -i -X PUT -T /opt/hadoop-3.3.3/README.txt "http://datanode.lxd:9864/webhdfs/v1/foo?op=CREATE&user.name=$USER&namenoderpcaddress=localhost:9000&createflag=&createparent=true&overwrite=false&permission=777"

# CREATE file (multi block)
curl -i -X PUT -T /opt/hadoop-3.3.3/share/hadoop/yarn/hadoop-yarn-applications-catalog-webapp-3.3.3.war "http://datanode.lxd:9864/webhdfs/v1/yarn.war?op=CREATE&user.name=$USER&namenoderpcaddress=localhost:9000&createflag=&createparent=true&overwrite=false&permission=777"

# APPEND file
curl -i -X POST -T /opt/hadoop-3.3.3/README.txt "http://datanode.lxd:9864/webhdfs/v1/foo?op=APPEND&permission=777&user.name=$USER&namenoderpcaddress=namenode.lxd:9000"

# READ file
curl -i "http://datanode.lxd:9864/webhdfs/v1/foo?op=OPEN&user.name=$USER&namenoderpcaddress=namenode.lxd:9000"

# STAT file
curl -i  "http://namenode.lxd:9870/webhdfs/v1/foo?op=LISTSTATUS"
curl -i  "http://namenode.lxd:9870/webhdfs/v1/foo?op=GETFILESTATUS"

# DELETE file
curl -i -X DELETE "http://namenode.lxd:9870/webhdfs/v1/foo?op=DELETE&recursive=true&user.name=$USER"

# MKDIR
curl -i -X PUT "http://namenode.lxd:9870/webhdfs/v1/thedir?op=MKDIRS&permission=777&user.name=$USER"
```

Note: The default port for WebHDFS is 9870.


## Troubleshoot

### View logs

```bash
# Namenode
tail -f /opt/hadoop-3.3.3/logs/hadoop-root-namenode-namenode.log

# Datanode
tail -f /opt/hadoop-3.3.3/logs/hadoop-root-datanode-datanode.log
```

### Reset installation
In case of unrecoverable errors, you can reset the entire installation using below steps.

On Namenode:
```bash
# Cleanup
/opt/hadoop-nimble/bin/hdfs --daemon stop namenode

# Format namenode
/opt/hadoop-nimble/bin/hdfs namenode -format

# Start
/opt/hadoop-nimble/bin/hdfs --daemon start namenode
```

On Datanode:

``` bash
# Cleanup
/opt/hadoop-nimble/bin/hdfs --daemon stop datanode

# Start
/opt/hadoop-nimble/bin/hdfs --daemon start datanode
```


### Debug logging

On Namenode or Datanode you can enable debug logging for Nimble module using:

```bash
# Edit file: /opt/hadoop-3.3.3/etc/hadoop/log4j.properties
# and add the following line.
log4j.logger.org.apache.hadoop.hdfs.server.nimble.NimbleUtils=DEBUG

# Then restart Namenode/Datanode
/opt/hadoop-nimble/bin/hdfs --daemon start namenode
/opt/hadoop-nimble/bin/hdfs --daemon stop namenode
```

