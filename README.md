# Nimble-aware Hadoop

Compiling and installing Nimble-aware Hadoop (referred to as Hadoop from now on) is exactly the same as vanilla Hadoop. The primary difference is the extra configuration for Nimble. This guide is structured as:

* Compile
* Deploy
* Benchmark
* Troubleshoot

Hadoop has two kinds of nodes: Namenode and Datanode. The Namenode maintains the structure of the file system and all the metadata. The Datanode(s) hold the actual block that make up the files. For our setup, we will run two nodes: one Namenode and one Datanode.

The compilation can be done on any machine. It creates a single tar.gz archive containing both the Namenode and Datanode binaries. The target machine on which Hadoop will be run only requires the Java runtime and the tar.gz archive. These instructions are tested on Ubuntu 18.04 (Bionic). The primary requirement from the Linux ditribution is OpenJDK v8.

**Note**: To benchmark, you need to also compile the upstream (or vanilla) Hadoop version 3.3.3. The compilation steps are identical to that of Nimble-aware Hadoop.


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
sudo apt install -y openjdk-8-jdk

# Setup JAVA_HOME for current user & root
echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' |
  tee -a ~/.bashrc |
  sudo tee -a /root/.bashrc
source ~/.bashrc
```

Copy the archive located at  `hadoop-dist/target/hadoop-3.3.3.tar.gz` to the  machine where you want to install Hadoop. Then do the following:

```bash
# Unpack archive
sudo tar -xvf hadoop-3.3.3.tar.gz -C /opt
sudo mv /opt/hadoop-3.3.3 /opt/hadoop-nimble

# Make yourself the owner the Hadoop installation
sudo chown -R `whoami` /opt/hadoop-nimble

# Add Hadoop binaries to your PATH
echo 'export PATH=$PATH:/opt/hadoop-nimble/bin' |
  tee -a ~/.bashrc |
  sudo tee -a /root/.bashrc
source ~/.bashrc
```

Create directory to store the Hadoop file system data. You can also mount another disk to store this data.

``` bash
sudo mkdir /mnt/store

# Make yourself the owner
sudo chown -R `whoami` /mnt/store
```


### Configure

This step is identical for both the Namenode and the Datanode. First, we configure Hadoop to use the correct storage directories.

```bash
echo "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>
<configuration>
	<property>
		<name>dfs.name.dir</name>
		<value>/mnt/store/namenode</value>
	</property>
	<property>
		<name>dfs.data.dir</name>
		<value>/mnt/store/datanode</value>
	</property>
</configuration>
" | sudo tee /opt/hadoop-nimble/etc/hadoop/hdfs-site.xml
```

Then, we add the necessary configuration to run Hadoop.

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
		<value>100</value>
	</property>
</configuration>
" | sudo tee /opt/hadoop-nimble/etc/hadoop/core-site.xml
```

Edit the newly created file `/opt/hadoop-nimble/etc/hadoop/core-site.xml` and replace `namenodeip` with network accessible IP address of the machine that will run the Namenode. Alsp replace `nimbleURI` with the IP address running the Nimble REST endpoint.

You can access Namenode's web UI from `http://namenodeip:9870`.

Configuration options:

- **fs.defaultFS**: Endpoint for this HDFS file system (points to the Namenode)
- **fs.nimbleURI**: URL of NimbleLedger's REST endpoint
- **fs.nimble.batchSize**: Number of operations to batch before incrementing the counter
- **fs.nimble.service.id** (optional): Expect this identity for NimbleLedger (against "/serviceid"). It is base64url encoded.
- **fs.nimble.service.publicKey** (optional): Expected this public Key for NimbleLedger. It is base64url encoded.
- **fs.nimble.service.handle** (optional): Ledger handle (or name) to use for formatting and reporting. It is base64url encoded.


**Important!**
Ensure that the Namenode and the Datanode can reach eachother via their hostnames. The commands `$ hostname` and `$ hostname -f` will give you the hostname and fully qualified hostname (or domain name) of a given machine. You can modify `/etc/hosts` to map the hostnames to the corresponding network-reachable IP addresses (don't use 127.0.0.1, etc.). The Namenode should have a mapping for the Datanode and the Datanode should have a mapping for the Namenode.

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

Logs are inside `/opt/hadoop-nimble/logs`. After starting the daemons, look at the logs for potential problems.

To quickly wipe the existing installtion and start over again, see Troublesooting -- "Reset installation".


### Verify
Formatting the file system, produces for a message like:

```
2022-10-28 02:27:17,586 INFO nimble.TMCS: Formatted TMCS: NimbleServiceID{identity=C9JtOpmXyBd-anyeBbhr5RZ0ac2urm5Nt-z_C88wfvU, publicKey=A8ABjZekZrccR7eq7ASkDNt0689wd3klvWUW6wIzYmJz, handle=yfQi-y8v4k1GIfgjaKon7w, signPublicKey=MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEXNoPgT2QAqTPaTJk0wowGyl4fQx7UywYJoqaR8UyARHgJGld6QOaH3mv1OQYIKwZNb3fBr7gPMM7LypIWbNNbQ, signPrivateKey=MD4CAQAwEAYHKoZIzj0CAQYFK4EEAAoEJzAlAgEBBCBzv_4v64jTXxngYxfDnFQAG-3M8rhc5heXrB9sSnGcmw}
```


You can test the instalation using Hadoop's WebHDFS API. Run the below commands either on the Namenode or the Datanode. Feel free to run only a few or all of the commands.

```bash
# Set the HDFS user
USER=root
API=http://<namenodeip>:9870/webhdfs/v1
DNAPI=http://<datanodeip>:9864/webhdfs/v1

# Change perms of /
curl -i -X PUT "$API/?op=SETPERMISSION&permission=777&user.name=$USER"

# CREATE file (single block)
curl -i -X PUT -T /opt/hadoop-nimble/README.txt "$DNAPI/foo?op=CREATE&user.name=$USER&namenoderpcaddress=localhost:9000&createflag=&createparent=true&overwrite=false&permission=777"

# CREATE file (multi block)
curl -i -X PUT -T /opt/hadoop-nimble/share/hadoop/yarn/hadoop-yarn-applications-catalog-webapp-3.3.3.war "$DNAPI/yarn.war?op=CREATE&user.name=$USER&namenoderpcaddress=localhost:9000&createflag=&createparent=true&overwrite=false&permission=777"

# APPEND file
curl -i -X POST -T /opt/hadoop-3.3.3/README.txt "$DNAPI/foo?op=APPEND&permission=777&user.name=$USER&namenoderpcaddress=namenode.lxd:9000"

# READ file
curl -i "$DNAPI/foo?op=OPEN&user.name=$USER&namenoderpcaddress=namenode.lxd:9000"

# STAT file
curl -i  "$API/foo?op=LISTSTATUS"
curl -i  "$API/foo?op=GETFILESTATUS"

# DELETE file
curl -i -X DELETE "$API/foo?op=DELETE&recursive=true&user.name=$USER"

# MKDIR
curl -i -X PUT "$API/thedir?op=MKDIRS&permission=777&user.name=$USER"
```

All commands in the WebHDFS API can be found [here](https://hadoop.apache.org/docs/r3.3.3/hadoop-project-dist/hadoop-hdfs/WebHDFS.html).


## Benchmark

We will run the following two benchmarks:

- NNThroughputBenchmark
- HiBench

You can run the benchmarks from any node that have the Hadoop binaires. For our benchmarking, we will use the Namenode.

### NNThroughputBenchmark

The basic structure is as follows:

```bash
hadoop org.apache.hadoop.hdfs.server.namenode.NNThroughputBenchmark \
    -op create -threads 10 -files 10
```

[Here](https://hadoop.apache.org/docs/r3.3.3/hadoop-project-dist/hadoop-common/Benchmarking.html) is the documentation of NNThroughputBenchmark.


For our purposes, use the following script to run all the benchmarks in the paper:

```bash
#!/bin/bash -e
THREADS=64
FILES=500000
DIRS=500000

function bench {
        op=$1
        echo "Running $op:"
        hadoop org.apache.hadoop.hdfs.server.namenode.NNThroughputBenchmark -op $*
}

bench create      -threads $THREADS -files $FILES
bench mkdirs      -threads $THREADS -dirs $DIRS
bench open        -threads $THREADS -files $FILES
bench delete      -threads $THREADS -files $FILES
bench fileStatus  -threads $THREADS -files $FILES
bench rename      -threads $THREADS -files $FILES
bench clean
```

### HiBench

We will first get HiBench and compile it. We run the following commands on the Namenode.
It requires OpenJDK 8 which should already be installed.

```bash
# Install pre-requisites
sudo apt-get install -y git maven python2.7
sudo ln -s /usr/bin/python2.7 /usr/bin/python2

# Get HiBench
cd ~
git clone https://github.com/Intel-bigdata/HiBench.git
cd HiBench/
git checkout 00aa105

# Compile (re-run if compilation fails)
mvn -Phadoopbench -Dhadoop=3.2 -DskipTests package
```

You will likely need to re-run the build once again when it fails. There seems to be a bug in their build script. 
For detailed instructions on building HiBench, you can refer to [this page](https://github.com/Intel-bigdata/HiBench/blob/master/docs/build-hibench.md).

Next you will need to configure HiBench.

```bash
# Inside HiBench directory
echo -n '# Configure
hibench.hadoop.home           /opt/hadoop-nimble
hibench.hadoop.executable     ${hibench.hadoop.home}/bin/hadoop
hibench.hadoop.configure.dir  ${hibench.hadoop.home}/etc/hadoop
hibench.hdfs.master           hdfs://<namenodeip>:9000
hibench.hadoop.release        apache
' >conf/hadoop.conf

# Configure YARN (on Namenode)
echo "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>
<configuration>
	<property>
		<name>yarn.resourcemanager.hostname</name>
		<value><namenodeip></value>
	</property>
</configuration>
" | sudo tee /opt/hadoop-nimble/etc/hadoop/yarn-site.xml


# Start Hadoop's Resource Manager (on Namenode)
yarn --daemon start resourcemanager

# Start Hadoop's Node Manager (on Datanode)
yarn --daemon start nodemanager
```

Replace `<namenodeip>` with the Namenode's IP address (at both places in the above code snippet). The Yarn logs are located inside `/opt/hadoop-nimble/logs/`.

The basic structure to run a benchmark is:

```bash
bin/workloads/micro/wordcount/prepare/prepare.sh
bin/workloads/micro/wordcount/hadoop/run.sh

# To view the thoughput
cat report/hibench.report
```

For our purposes, run the following script:

```bash
#!/bin/bash

size=large
sed -ie "s/hibench.scale.profile .*/hibench.scale.profile $size/g" conf/hibench.conf

function bench {
        kind=$1
        name=$2
        bin/workloads/$kind/$name/prepare/prepare.sh
        bin/workloads/$kind/$name/hadoop/run.sh
}

bench micro     wordcount
bench micro     sort
bench micro     terasort
bench micro     dfsioe
bench websearch pagerank
```

To get the throughputs, see the file **report/hibench.report**.

## Troubleshoot

### View logs

```bash
# Namenode
tail -f /opt/hadoop-nimble/logs/hadoop-<user>-namenode-<hostname>.log

# Datanode
tail -f /opt/hadoop-nimble/logs/hadoop-<user>-datanode-<hostname>.log
```

Where `<user>` refers to your UNIX username (i.e., output of **whoami**) and `<hostname>` refers to the hostname of the current machine.

### Reset installation
In case of unrecoverable errors, you can reset the entire installation using below steps.

On Namenode:
```bash
# Cleanup
/opt/hadoop-nimble/bin/hdfs --daemon stop namenode
/opt/hadoop-nimble/bin/yarn --daemon stop resourcemanager

rm -rf /mnt/store/*

# Format namenode
/opt/hadoop-nimble/bin/hdfs namenode -format

# Start
/opt/hadoop-nimble/bin/hdfs --daemon start namenode
/opt/hadoop-nimble/bin/yarn --daemon start resourcemanager
```

On Datanode:

```bash
# Cleanup
/opt/hadoop-nimble/bin/hdfs --daemon stop datanode
rm -rf /mnt/store/*

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

