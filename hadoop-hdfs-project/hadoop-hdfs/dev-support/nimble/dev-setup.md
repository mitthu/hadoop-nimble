# Setup guide for Nimble Integration

### Installation
Install Java and Apache Maven:
```bash
sudo apt update
sudo apt install default-jdk maven
```

Install LXC containers:
```bash
sudo snap install lxd # config is in /var/snap/lxd/common
sudo usermod -a -G lxd <username> # then re-login
sudo lxd init
```

To resolve DNS from host (optional):
```bash
lxc network get lxdbr0 ipv4.address # for DNS address; ex. 10.88.138.1/24

sudo resolvectl dns lxdbr0 <address_from_above>  # remove the subset: /24
sudo resolvectl domain lxdbr0 '~lxd'
```

### Compilation
We want to compile the entire Hadoop distribution and deploy it inside containers.

```bash
# cd <repo-top-level>
mvn package -Pdist -DskipTests -Dtar -Dmaven.javadoc.skip=true
```

### Setup Local cluster

```bash
# Setup java
sudo apt update
sudo apt install -y bash-completion default-jre

# Setup environment variables (for bash)
export JAVA_HOME=/usr/lib/jvm/default-java
export PATH=/opt/hadoop-3.3.3/bin:$PATH

# (for fish)
set -x JAVA_HOME /usr/lib/jvm/default-java
set -x PATH /opt/hadoop-3.3.3/bin $PATH
```

Copy Hadoop installation:

```bash
sudo cp -ar ./hadoop-dist/target/hadoop-3.3.3 /opt/hadoop-3.3.3

# Replace localhost with IP address. Then you can access hdfs namenode webUI from ip:9870.
echo "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>
<configuration>
	<property>
		<name>fs.defaultFS</name>
		<value>hdfs://localhost:9000</value>
	</property>
</configuration>
" >/opt/hadoop-3.3.3/etc/hadoop/core-site.xml
```

Start Hadoop:
```bash
# Format namenode
hdfs namenode -format

# Start
hdfs --daemon start namenode
hdfs --daemon start datanode

# Logs are inside /opt/hadoop-3.3.3/logs
```

### Setup Containers

```bash
lxc launch images:ubuntu/20.04 namenode
lxc launch images:ubuntu/20.04 datanode

lxc exec namenode -- apt update
lxc exec namenode -- apt install -y bash-completion default-jre
lxc exec datanode -- apt update
lxc exec datanode -- apt install -y bash-completion default-jre
```

Deploy entire hadoop distribution inside containers:
```bash
# cd <repo-top-level>
lxc file push ./hadoop-dist/target/hadoop-3.3.3.tar.gz namenode/opt/hadoop-3.3.3.tar.gz
lxc file push ./hadoop-dist/target/hadoop-3.3.3.tar.gz datanode/opt/hadoop-3.3.3.tar.gz
lxc exec namenode -- tar -xzf /opt/hadoop-3.3.3.tar.gz -C /opt
lxc exec datanode -- tar -xzf /opt/hadoop-3.3.3.tar.gz -C /opt

# Deploy Hadoop config
echo "\
<configuration>
	<property>
		<name>fs.defaultFS</name>
		<value>hdfs://namenode.lxd:9000</value>
	</property>
</configuration>
" >/tmp/core-site.xml

lxc file push /tmp/core-site.xml namenode/opt/hadoop-3.3.3/etc/hadoop/core-site.xml
lxc file push /tmp/core-site.xml datanode/opt/hadoop-3.3.3/etc/hadoop/core-site.xml

# Setup environment variable (for root user)
lxc exec namenode -- bash -c "echo 'export JAVA_HOME=/usr/lib/jvm/default-java' >>/root/.bashrc"
lxc exec namenode -- bash -c "echo 'export PATH=/opt/hadoop-3.3.3/bin:\$PATH' >>/root/.bashrc"
lxc exec datanode -- bash -c "echo 'export JAVA_HOME=/usr/lib/jvm/default-java' >>/root/.bashrc"
lxc exec datanode -- bash -c "echo 'export PATH=/opt/hadoop-3.3.3/bin:\$PATH' >>/root/.bashrc"
```

Start Hadoop:
```bash
# Format namenode
lxc exec namenode --env JAVA_HOME=/usr/lib/jvm/default-java -- /opt/hadoop-3.3.3/bin/hdfs namenode -format

# Start
lxc exec namenode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-3.3.3/bin/hdfs --daemon start namenode
lxc exec datanode --env JAVA_HOME=/usr/lib/jvm/default-java -T -- /opt/hadoop-3.3.3/bin/hdfs --daemon start datanode
```

### Others
View logs:
```bash
# Namenode
lxc exec namenode -- tail -f /opt/hadoop-3.3.3/logs/hadoop-root-namenode-namenode.log

# Datanode
lxc exec datanode -- tail -f /opt/hadoop-3.3.3/logs/hadoop-root-datanode-datanode.log
```

Interactive shell:
```bash
lxc exec namenode -- bash
```

Testing Nimble:
```bash
# From host
java -cp 'hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' org.apache.hadoop.hdfs.server.nimble.NimbleTester

# From container
export CLASSPATH=/opt/hadoop-3.3.3/etc/hadoop:/opt/hadoop-3.3.3/share/hadoop/common/lib/*:/opt/hadoop-3.3.3/share/hadoop/common/*:/opt/hadoop-3.3.3/share/hadoop/hdfs:/opt/hadoop-3.3.3/share/hadoop/hdfs/lib/*:/opt/hadoop-3.3.3/share/hadoop/hdfs/*:/opt/hadoop-3.3.3/share/hadoop/mapreduce/*:/opt/hadoop-3.3.3/share/hadoop/yarn/lib/*:/opt/hadoop-3.3.3/share/hadoop/yarn/*
java -cp hadoop-hdfs-3.3.3.jar:$CLASSPATH org.apache.hadoop.hdfs.server.nimble.NimbleTester
```

Compile and run Nimble only (for development):
```bash
# In host
mkdir tmp/
javac -d tmp/ -cp 'hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' \
  hadoop-hdfs-project/hadoop-hdfs/src/main/java/org/apache/hadoop/hdfs/Nimble.java
java -cp 'tmp/:hadoop-hdfs-project/hadoop-hdfs/target/hadoop-hdfs-3.3.3.jar:hadoop-dist/target/hadoop-3.3.3/etc/hadoop:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/common/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/lib/*:hadoop-dist/target/hadoop-3.3.3/share/hadoop/hdfs/*' org.apache.hadoop.hdfs.server.nimble.NimbleTester

# In container (after doing above)
lxc file push -r tmp/org namenode/root/
lxc exec namenode -- bash
export CLASSPATH=/root:/opt/hadoop-3.3.3/etc/hadoop:/opt/hadoop-3.3.3/share/hadoop/common/lib/*:/opt/hadoop-3.3.3/share/hadoop/common/*:/opt/hadoop-3.3.3/share/hadoop/hdfs:/opt/hadoop-3.3.3/share/hadoop/hdfs/lib/*:/opt/hadoop-3.3.3/share/hadoop/hdfs/*:/opt/hadoop-3.3.3/share/hadoop/mapreduce/*:/opt/hadoop-3.3.3/share/hadoop/yarn/lib/*:/opt/hadoop-3.3.3/share/hadoop/yarn/*
java org/apache/hadoop/hdfs/Nimble
```

### References
* [LXC Getting Started](https://linuxcontainers.org/lxd/getting-started-cli/)
* [Verify signatures in Java](https://etzold.medium.com/elliptic-curve-signatures-and-how-to-use-them-in-your-java-application-b88825f8e926)
* [Signature Algorithms in Java](https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature)
